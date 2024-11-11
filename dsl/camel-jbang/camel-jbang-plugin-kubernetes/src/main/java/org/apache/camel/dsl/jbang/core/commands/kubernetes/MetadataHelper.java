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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.component.kamelet.KameletEndpoint;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.Capability;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubComponentResolver;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubDataFormatResolver;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubLanguageResolver;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.StubTransformerResolver;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultModelReifierFactory;
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
import org.apache.camel.model.ProcessorDefinition;
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
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.util.URISupport;

public class MetadataHelper {

    public static final String STUB_PATTERN = "*";

    private static final Map<String, BiConsumer<CamelCatalog, SourceMetadata>> COMPONENT_CUSTOMIZERS;

    static {
        // the core CircuitBreaker reifier fails as it depends on a specific implementation provided by
        // a dedicated component/module, but as we are only inspecting the route to determine its capabilities,
        // we can safely disable the EIP with a disabled/stub reifier.
        ProcessorReifier.registerReifier(CircuitBreakerDefinition.class, DisabledReifier::new);

        COMPONENT_CUSTOMIZERS = new HashMap<>();
        COMPONENT_CUSTOMIZERS.put(Capability.PlatformHttp.getValue(),
                (catalog, meta) -> meta.capabilities.add(Capability.PlatformHttp));
    }

    private MetadataHelper() {
        // prevent instantiation of utility class
    }

    public static SourceMetadata readFromSource(CamelCatalog catalog, Source source) throws Exception {
        return readFromSource(catalog, source.name(), source.content());
    }

    public static SourceMetadata readFromSource(CamelCatalog catalog, String location, String source) throws Exception {
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

            final Set<String> kamelets = context.getEndpoints().stream()
                    .filter(KameletEndpoint.class::isInstance)
                    .map(KameletEndpoint.class::cast)
                    .map(KameletEndpoint::getTemplateId)
                    .collect(Collectors.toSet());

            SourceMetadata meta = new SourceMetadata();
            meta.resources.components.addAll(context.getComponentNames());
            meta.resources.languages.addAll(context.getLanguageNames());
            meta.resources.dataformats.addAll(context.getDataFormatNames());
            meta.resources.kamelets.addAll(kamelets);
            meta.endpoints.from.addAll(fromEndpoints);
            meta.endpoints.to.addAll(toEndpoints);

            // determine capabilities based on components
            for (String component : meta.resources.components) {
                // TODO: add this information to the model so we can retrieve them automatically
                BiConsumer<CamelCatalog, SourceMetadata> consumer = COMPONENT_CUSTOMIZERS.get(component);
                if (consumer != null) {
                    consumer.accept(catalog, meta);
                }
            }

            // determine capabilities based on EIP
            for (RouteDefinition definition : model.getRouteDefinitions()) {
                navigateRoute(
                        definition,
                        d -> {
                            if (d instanceof CircuitBreakerDefinition) {
                                meta.capabilities.add(Capability.CircuitBreaker);
                            }
                        });
            }

            meta.dependencies.addAll(deps(context, catalog));

            return meta;
        }
    }

    private static Collection<String> deps(CamelContext context, CamelCatalog catalog) {
        final Set<String> answer = new TreeSet<>();

        for (String name : context.getComponentNames()) {
            ComponentModel model = catalog.componentModel(name);
            if (model != null) {
                answer.add(String.format("mvn:%s/%s/%s", model.getGroupId(), model.getArtifactId(), model.getVersion()));
            }
        }
        for (String name : context.getLanguageNames()) {
            LanguageModel model = catalog.languageModel(name);
            if (model != null) {
                answer.add(String.format("mvn:%s/%s/%s", model.getGroupId(), model.getArtifactId(), model.getVersion()));
            }
        }
        for (String name : context.getDataFormatNames()) {
            DataFormatModel model = catalog.dataFormatModel(name);
            if (model != null) {
                answer.add(String.format("mvn:%s/%s/%s", model.getGroupId(), model.getArtifactId(), model.getVersion()));
            }
        }

        return answer;
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
                context, new KnownDependenciesResolver(context, RuntimeType.SPRING_BOOT_VERSION, RuntimeType.QUARKUS_VERSION),
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

    private static void navigateRoute(ProcessorDefinition<?> root, Consumer<ProcessorDefinition<?>> consumer) {
        consumer.accept(root);

        for (ProcessorDefinition<?> def : root.getOutputs()) {
            navigateRoute(def, consumer);
        }
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
