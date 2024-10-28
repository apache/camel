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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.CatalogHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.MetadataHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.Capability;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.Kind;
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

    public static final String ID = "agent";
    public static final String MIME_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";

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
                        completionCandidates = RuntimeCompletionCandidates.class,
                        converter = RuntimeTypeConverter.class,
                        defaultValue = "camel-main",
                        description = "Runtime (${COMPLETION-CANDIDATES})")
    RuntimeType runtimeType = RuntimeType.main;

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

    private void handleCatalogModel(RoutingContext ctx) {
        try {
            final CamelCatalog catalog = CatalogHelper.loadCatalog(runtimeType, runtimeVersion, repos, quarkusGroupId);
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
        final String name = ctx.pathParam("location");

        try {
            RuntimeType resolvedRuntimeType = rt.size() == 1 ? RuntimeType.fromValue(rt.get(0)) : runtimeType;
            String resolvedRuntimeVersion = rv.size() == 1 ? rv.get(0) : runtimeVersion;
            CamelCatalog catalog
                    = CatalogHelper.loadCatalog(resolvedRuntimeType, resolvedRuntimeVersion, repos, quarkusGroupId);
            SourceMetadata meta = MetadataHelper.readFromSource(catalog, name, content);

            if (capabilities.size() == 1) {
                for (String item : capabilities.get(0).split(",")) {
                    meta.capabilities.add(Capability.fromValue(item));
                }
            }

            ctx.response()
                    .putHeader(CONTENT_TYPE_HEADER, MIME_TYPE_JSON)
                    .end(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
