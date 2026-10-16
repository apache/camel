/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteModel;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteNodes;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubComponentResolver;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubDataFormatResolver;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubLanguageResolver;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubTransformerResolver;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultModelReifierFactory;
import org.apache.camel.java.in.LwJavaParser;
import org.apache.camel.main.download.CamelCustomClassLoader;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.DependencyDownloaderClassResolver;
import org.apache.camel.main.download.DependencyDownloaderRoutesLoader;
import org.apache.camel.main.download.DependencyDownloaderStrategy;
import org.apache.camel.main.download.KnownDependenciesResolver;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.DisabledReifier;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.TransformerResolver;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.util.URISupport;

/**
 * Reads what the traits need to know of a route source: the endpoints its routes consume from and send to. The service
 * trait creates a Kubernetes Service when a route consumes from an http component or knative, and the knative trait
 * configures the knative: channels, endpoints and events.
 * <p/>
 * Java and XML routes are read into the route model without starting anything (CAMEL-25592): Java with the Java DSL
 * parser of camel-java-io, which neither compiles nor runs the source. YAML routes, Pipes and Kamelets are loaded in a
 * stubbed CamelContext, as the YAML DSL builds the uri of an endpoint written with parameters with the component.
 */
public class MetadataHelper {

    public static final String STUB_PATTERN = "*";

    /**
     * The steps that send to a fixed endpoint, which the routes create when they start. enrich, pollEnrich, poll and
     * toD resolve their endpoint for each exchange, so a started route has not created it, and they are left out as the
     * stubbed CamelContext did.
     */
    private static final Set<String> PRODUCERS = Set.of("to", "wireTap");

    /** The Quarkus platform version of the stubbed context, looked up once. */
    private static volatile String quarkusVersion;

    static {
        // the core CircuitBreaker reifier fails as it depends on a specific implementation provided by
        // a dedicated component/module, but as we are only inspecting the route to determine its capabilities,
        // we can safely disable the EIP with a disabled/stub reifier.
        ProcessorReifier.registerReifier(CircuitBreakerDefinition.class, DisabledReifier::new);
    }

    private MetadataHelper() {
        // prevent instantiation of utility class
    }

    public static SourceMetadata readFromSource(CamelCatalog catalog, Source source) throws Exception {
        return readFromSource(catalog, source.name(), source.content());
    }

    public static SourceMetadata readFromSource(CamelCatalog catalog, String location, String source) throws Exception {
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java") || lower.endsWith(".xml")) {
            RouteModel model = RouteModel.read(location, source, catalog, Map.of());
            // a Java or XML file that is not a route source has no endpoints
            return model != null ? fromModel(model, catalog) : new SourceMetadata();
        }
        return fromContext(location, source);
    }

    /**
     * The endpoints of the routes read into the model: the route inputs, and the fixed endpoints the routes send to. A
     * value only known at runtime is left out.
     */
    private static SourceMetadata fromModel(RouteModel model, CamelCatalog catalog) {
        SourceMetadata meta = new SourceMetadata();
        Set<String> to = new TreeSet<>();
        for (RouteNodes.Node n : RouteNodes.of(model, catalog)) {
            String uri = n.uri();
            if (n.kind() != RouteNodes.Kind.ENDPOINT || uri == null || uri.contains(LwJavaParser.UNRESOLVED_PREFIX)) {
                continue;
            }
            if ("from".equals(n.eip()) && n.parents().isEmpty()) {
                meta.endpoints.from.add(uri);
            } else if (PRODUCERS.contains(n.eip()) && !uri.contains("${")) {
                to.add(uri);
            }
        }
        to.removeAll(meta.endpoints.from);
        meta.endpoints.to.addAll(to);
        return meta;
    }

    private static SourceMetadata fromContext(String location, String source) throws Exception {
        try (CamelContext context = createCamelContext()) {
            Resource resource = ResourceHelper.fromString(location, source);

            try {
                PluginHelper.getRoutesLoader(context).loadRoutes(resource);
            } catch (IllegalArgumentException e) {
                // obviously no matching routes loader is available, return empty metadata
                return new SourceMetadata();
            }

            context.start();

            final Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
            final Set<String> fromEndpoints = model.getRouteDefinitions().stream()
                    .map(RouteDefinition::getInput)
                    .map(FromDefinition::getEndpointUri)
                    .map(context::getEndpoint)
                    .map(Endpoint::getEndpointUri)
                    .collect(Collectors.toSet());

            final Set<String> toEndpoints = context.getEndpoints().stream()
                    .map(Endpoint::getEndpointUri)
                    .filter(Predicate.not(fromEndpoints::contains))
                    .collect(Collectors.toSet());

            SourceMetadata meta = new SourceMetadata();
            meta.endpoints.from.addAll(fromEndpoints);
            meta.endpoints.to.addAll(toEndpoints);
            return meta;
        }
    }

    private static CamelContext createCamelContext() throws Exception {
        final StubComponentResolver componentResolver = new StubComponentResolver(STUB_PATTERN, true);
        final StubDataFormatResolver dataFormatResolver = new StubDataFormatResolver(STUB_PATTERN, true);
        final StubLanguageResolver languageResolver = new StubLanguageResolver(STUB_PATTERN, true);
        final StubTransformerResolver transformerResolver = new StubTransformerResolver(STUB_PATTERN, true);

        CamelContext context = new DefaultCamelContext(false) {
            @Override
            public Set<String> getDataFormatNames() {
                // data formats names are no necessary stored in the context as they
                // are created on demand
                //
                // TODO: maybe the camel context should keep track of those ?
                return dataFormatResolver.getNames();
            }

            @Override
            public PropertiesComponent getPropertiesComponent() {
                return new org.apache.camel.component.properties.PropertiesComponent() {
                    @Override
                    public Optional<String> resolveProperty(String key) {
                        // properties may not be resolvable in this stubbed context, just use the key as a value
                        return Optional.of(key);
                    }

                    @Override
                    public String parseUri(String uri, boolean keepUnresolvedOptional) {
                        // we are not interested in the query part of endpoint uris, remove it to avoid unresolvable properties
                        return URISupport.stripQuery(uri);
                    }
                };
            }
        };

        final ExtendedCamelContext ec = context.getCamelContextExtension();
        final Model model = ec.getContextPlugin(Model.class);

        model.setModelReifierFactory(new AgentModelReifierFactory());

        ec.addContextPlugin(ComponentResolver.class, componentResolver);
        ec.addContextPlugin(DataFormatResolver.class, dataFormatResolver);
        ec.addContextPlugin(LanguageResolver.class, languageResolver);
        ec.addContextPlugin(TransformerResolver.class, transformerResolver);

        ec.getRegistry().bind(DependencyDownloaderStrategy.class.getSimpleName(),
                new DependencyDownloaderStrategy(context));

        ClassLoader cl = createClassLoader(context);
        context.setApplicationContextClassLoader(cl);
        PluginHelper.getPackageScanClassResolver(context).addClassLoader(cl);
        PluginHelper.getPackageScanResourceResolver(context).addClassLoader(cl);

        ClassResolver classResolver = new DependencyDownloaderClassResolver(
                context, new KnownDependenciesResolver(context, RuntimeType.SPRING_BOOT_VERSION, quarkusVersion()),
                true);
        context.setClassResolver(classResolver);
        // re-create factory finder with download class-resolver
        FactoryFinderResolver ffr = PluginHelper.getFactoryFinderResolver(context);
        FactoryFinder ff = ffr.resolveBootstrapFactoryFinder(classResolver);
        context.getCamelContextExtension().setBootstrapFactoryFinder(ff);
        ff = ffr.resolveDefaultFactoryFinder(classResolver);
        context.getCamelContextExtension().setDefaultFactoryFinder(ff);

        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(cl);
        downloader.setCamelContext(context);
        downloader.setVerbose(false);

        context.addService(downloader);

        DependencyDownloaderRoutesLoader routesLoader = new DependencyDownloaderRoutesLoader(
                context, VersionHelper.extractCamelVersion(), VersionHelper.extractKameletsVersion());

        ec.addContextPlugin(RoutesLoader.class, routesLoader);

        // ensure camel context is build
        context.build();

        return context;
    }

    /** The Quarkus platform version for the known dependencies of the stubbed context: a lookup, done once. */
    private static String quarkusVersion() throws Exception {
        String answer = quarkusVersion;
        if (answer == null) {
            synchronized (MetadataHelper.class) {
                answer = quarkusVersion;
                if (answer == null) {
                    try (MavenDownloaderImpl d = new MavenDownloaderImpl()) {
                        d.build();
                        answer = QuarkusHelper
                                .findQuarkusPlatformBom(
                                        RuntimeType.main.version(),
                                        d::resolveArtifact,
                                        true,
                                        RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL,
                                        false)
                                .version();
                    }
                    quarkusVersion = answer;
                }
            }
        }
        return answer;
    }

    public static boolean exposesHttpServices(CamelCatalog catalog, SourceMetadata sourceMetadata) {
        for (String uri : sourceMetadata.endpoints.from) {
            String componentName = catalog.endpointComponentName(uri);
            if (componentName != null) {
                // TODO: improve retrieval of Http component nature
                if (catalog.componentModel(componentName).isConsumerOnly() && componentName.contains("http")) {
                    return true;
                }

                if (componentName.equals("knative")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ClassLoader createClassLoader(CamelContext context) {
        return new DependencyDownloaderClassLoader(new CamelCustomClassLoader(MetadataHelper.class.getClassLoader(), context));
    }

    private static final class AgentModelReifierFactory extends DefaultModelReifierFactory {
        @Override
        public Route createRoute(CamelContext camelContext, Object routeDefinition) {
            if (routeDefinition instanceof RouteDefinition) {
                ((RouteDefinition) routeDefinition).autoStartup(false);
            }

            return super.createRoute(camelContext, routeDefinition);
        }
    }
}
