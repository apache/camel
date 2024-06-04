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
package org.apache.camel.dsl.jbang.core.commands.k;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.component.kamelet.KameletEndpoint;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.k.support.Capability;
import org.apache.camel.dsl.jbang.core.commands.k.support.RuntimeType;
import org.apache.camel.dsl.jbang.core.commands.k.support.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.core.commands.k.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.commands.k.support.StubComponentResolver;
import org.apache.camel.dsl.jbang.core.commands.k.support.StubDataFormatResolver;
import org.apache.camel.dsl.jbang.core.commands.k.support.StubLanguageResolver;
import org.apache.camel.dsl.jbang.core.commands.k.support.StubTransformerResolver;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultModelReifierFactory;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.DisabledReifier;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.TransformerResolver;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EntityRef;
import org.apache.camel.tooling.model.Kind;
import org.apache.camel.tooling.model.LanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = Agent.ID,
                     description = "Start a Camel K agent service that exposes functionalities to inspect routes and interact with a Camel Catalog",
                     sortOptions = false)
public class Agent extends CamelCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(Agent.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private static final Map<String, BiConsumer<CamelCatalog, SourceMetadata>> COMPONENT_CUSTOMIZERS;

    public static final String ID = "agent";
    public static final String STUB_PATTERN = "*";
    public static final String MIME_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";

    static {
        // the core CircuitBreaker reifier fails as it depends on a specific implementation provided by
        // a dedicated component/module, but as we are orly inspecting the route to determine its capabilities,
        // we can safely disable the EIP with a disabled/stub reifier.
        ProcessorReifier.registerReifier(CircuitBreakerDefinition.class, DisabledReifier::new);

        COMPONENT_CUSTOMIZERS = new HashMap<>();
        COMPONENT_CUSTOMIZERS.put("platform-http", (catalog, meta) -> {
            meta.capabilities.put(
                    Capability.PlatformHttp,
                    catalog
                            .findCapabilityRef(Capability.PlatformHttp.getValue())
                            .orElseGet(() -> new EntityRef(null, null)));
        });

    }

    @CommandLine.Option(names = { "--listen-host" },
                        description = "The host to listen on")
    String host = "localhost";

    @CommandLine.Option(names = { "--listen-port" },
                        description = "The port to listen on")
    int port = 8081;

    @CommandLine.Option(names = { "--runtime-version" },
                        description = "To use a different runtime version than the default version")
    String runtimeVersion;

    @CommandLine.Option(names = { "--runtime" },
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (spring-boot, quarkus, camel-main)")
    RuntimeType runtimeType = RuntimeType.camelMain;

    @CommandLine.Option(names = { "--repos" },
                        description = "Comma separated list of additional maven repositories")
    String repos;

    @CommandLine.Option(names = { "--quarkus-group-id" }, description = "Quarkus Platform Maven groupId",
            defaultValue = "io.quarkus.platform")
    String quarkusGroupId = "io.quarkus.platform";

    public Agent(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Vertx vertx = null;
        HttpServer server = null;

        try {
            CountDownLatch latch = new CountDownLatch(1);

            vertx = Vertx.vertx();
            server = serve(vertx).toCompletableFuture().get();

            latch.await();
        } finally {
            if (server != null) {
                server.close();
            }
            if (vertx != null) {
                vertx.close();
            }
        }

        return 0;
    }

    // Visible for testing
    CompletionStage<HttpServer> serve(Vertx vertx) {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route()
                .handler(BodyHandler.create())
                .failureHandler(this::handleFailure);

        router.route(HttpMethod.GET, "/catalog/model/:kind/:name")
                .produces(MIME_TYPE_JSON)
                .blockingHandler(this::handleCatalogModel);

        router.route(HttpMethod.GET, "/catalog/capability/:name")
                .produces(MIME_TYPE_JSON)
                .blockingHandler(this::handleCatalogCapability);

        router.route(HttpMethod.POST, "/inspect/:location")
                .produces(MIME_TYPE_JSON)
                .blockingHandler(this::handleInspect);

        return server.requestHandler(router).listen(port, host).toCompletionStage();
    }

    private void handleFailure(RoutingContext ctx) {
        LOGGER.warn("", ctx.failure());

        ctx.response()
                .setStatusCode(500)
                .setStatusMessage(
                        ctx.failure().getCause() != null
                                ? ctx.failure().getCause().getMessage()
                                : ctx.failure().getMessage())
                .end();
    }

    private void handleCatalogCapability(RoutingContext ctx) {
        try {
            final CamelCatalog catalog = loadCatalog(runtimeType, runtimeVersion);
            final String name = ctx.pathParam("name");
            final Optional<EntityRef> ref = catalog.findCapabilityRef(name);

            if (ref.isPresent()) {
                ctx.response()
                        .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
                        .end(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ref.get()));
            } else {
                ctx.response()
                        .setStatusCode(204)
                        .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
                        .end(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleCatalogModel(RoutingContext ctx) {
        try {
            final CamelCatalog catalog = loadCatalog(runtimeType, runtimeVersion);
            final String kind = ctx.pathParam("kind");
            final String name = ctx.pathParam("name");
            final BaseModel<?> model = catalog.model(Kind.valueOf(kind), name);

            if (model != null) {
                ctx.response()
                        .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
                        .end(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(model));
            } else {
                ctx.response()
                        .setStatusCode(204)
                        .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
                        .end(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleInspect(RoutingContext ctx) {
        final String content = ctx.body().asString();
        List<String> rt = ctx.queryParam("runtimeType");
        List<String> rv = ctx.queryParam("runtimeVersion");
        List<String> capabilities = ctx.queryParam("capabilities");

        try (CamelContext context = createCamelContext()) {
            final Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
            final String name = ctx.pathParam("location");
            final CamelCatalog catalog = loadCatalog(runtimeType, runtimeVersion);

            PluginHelper.getRoutesLoader(context).loadRoutes(
                    ResourceHelper.fromString(name, content));

            context.start();

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

                                meta.capabilities.put(
                                        Capability.CircuitBreaker,
                                        catalog
                                                .findCapabilityRef(Capability.CircuitBreaker.getValue())
                                                .orElseGet(() -> new EntityRef(null, null)));
                            }
                        });
            }

            if (capabilities.size() == 1) {
                for (String item : capabilities.get(0).split(",")) {
                    meta.capabilities.put(
                            Capability.fromValue(item),
                            catalog
                                    .findCapabilityRef(item)
                                    .orElseGet(() -> new EntityRef(null, null)));
                }
            }

            meta.dependencies.addAll(deps(
                    context,
                    catalog,
                    rt.size() == 1 ? RuntimeType.fromValue(rt.get(0)) : runtimeType,
                    rv.size() == 1 ? rv.get(0) : runtimeVersion));

            ctx.response()
                    .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
                    .end(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meta));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void navigateRoute(ProcessorDefinition<?> root, Consumer<ProcessorDefinition<?>> consumer) {
        consumer.accept(root);

        for (ProcessorDefinition<?> def : root.getOutputs()) {
            navigateRoute(def, consumer);
        }
    }

    private Collection<String> deps(CamelContext context, CamelCatalog catalog, RuntimeType runtimeType, String runtimeVersion)
            throws Exception {
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

    private CamelCatalog loadCatalog(RuntimeType runtime, String runtimeVersion) throws Exception {
        switch (runtime) {
            case springBoot:
                return CatalogLoader.loadSpringBootCatalog(repos, runtimeVersion);
            case quarkus:
                return CatalogLoader.loadQuarkusCatalog(repos, runtimeVersion, quarkusGroupId);
            case camelMain:
                return CatalogLoader.loadCatalog(repos, runtimeVersion);
            default:
                throw new IllegalArgumentException("Unsupported runtime: " + runtime);
        }
    }

    private CamelContext createCamelContext() {
        final StubComponentResolver componentResolver = new StubComponentResolver(STUB_PATTERN, true);
        final StubDataFormatResolver dataFormatResolver = new StubDataFormatResolver(STUB_PATTERN, true);
        final StubLanguageResolver languageResolver = new StubLanguageResolver(STUB_PATTERN, true);
        final StubTransformerResolver transformerResolver = new StubTransformerResolver(STUB_PATTERN, true);

        CamelContext context = new DefaultCamelContext() {
            @Override
            public Set<String> getDataFormatNames() {
                // data formats names are no necessary stored in the context as they
                // are created on demand
                //
                // TODO: maybe the camel context should keep track of those ?
                return dataFormatResolver.getNames();
            }
        };

        final ExtendedCamelContext ec = context.getCamelContextExtension();
        final Model model = ec.getContextPlugin(Model.class);

        model.setModelReifierFactory(new AgentModelReifierFactory());

        ec.addContextPlugin(ComponentResolver.class, componentResolver);
        ec.addContextPlugin(DataFormatResolver.class, dataFormatResolver);
        ec.addContextPlugin(LanguageResolver.class, languageResolver);
        ec.addContextPlugin(TransformerResolver.class, transformerResolver);

        return context;
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
