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
package org.apache.camel.component.platform.http.vertx;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerSupport.configureSSL;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerSupport.createBodyHandler;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerSupport.createCorsHandler;

/**
 * This class implement a basic Vert.x Web based server that can be used by the {@link VertxPlatformHttpEngine} on
 * platforms that do not provide Vert.x based http services.
 */
final class VertxPlatformHttpServer extends ServiceSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPlatformHttpServer.class);

    private final CamelContext context;
    private final VertxPlatformHttpServerConfiguration configuration;

    private ExecutorService executor;
    private Vertx vertx;
    private boolean localVertx;

    private HttpServer server;

    public VertxPlatformHttpServer(CamelContext context, VertxPlatformHttpServerConfiguration configuration) {
        this(context, configuration, null);
    }

    public VertxPlatformHttpServer(CamelContext context, VertxPlatformHttpServerConfiguration configuration, Vertx vertx) {
        this.context = context;
        this.configuration = configuration;
        this.vertx = vertx;
    }

    @Override
    protected void doStart() throws Exception {
        vertx = CamelContextHelper.findByType(context, Vertx.class);
        executor = context.getExecutorServiceManager().newSingleThreadExecutor(this, "platform-http-service");

        if (vertx != null) {
            LOGGER.info("Found Vert.x instance in registry: {}", vertx);
        } else {
            VertxOptions options = CamelContextHelper.findByType(context, VertxOptions.class);
            if (options == null) {
                options = new VertxOptions();
            }

            LOGGER.info("Creating new Vert.x instance");

            vertx = Vertx.vertx(options);
            localVertx = true;
        }

        startAsync().toCompletableFuture().join();
    }

    @Override
    protected void doStop() throws Exception {
        try {
            if (server != null) {
                stopAsync().toCompletableFuture().join();
            }
        } finally {
            this.server = null;
        }

        if (vertx != null && localVertx) {
            Future<?> future = executor.submit(
                () -> {
                    CountDownLatch latch = new CountDownLatch(1);

                    vertx.close(result -> {
                        try {
                            if (result.failed()) {
                                LOGGER.warn("Failed to close Vert.x reason: {}",
                                    result.cause().getMessage()
                                );

                                throw new RuntimeException(result.cause());
                            }

                            LOGGER.info("Vert.x stopped");
                        } finally {
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            );

            try {
                future.get();
            } finally {
                vertx = null;
                localVertx = false;
            }
        }

        if (executor != null) {
            context.getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    private CompletionStage<Void> startAsync() {
        final Router router = Router.router(vertx);
        final Router subRouter = Router.router(vertx);

        if (configuration.getCors().isEnabled()) {
            subRouter.route().handler(createCorsHandler(configuration));
        }

        router.mountSubRouter(configuration.getPath(), subRouter);

        context.getRegistry().bind(
            VertxPlatformHttp.PLATFORM_HTTP_ROUTER_NAME,
            new VertxPlatformHttp(vertx, subRouter, Collections.singletonList(createBodyHandler(configuration)))
        );

        HttpServerOptions options = new HttpServerOptions();

        configureSSL(options, configuration, context);

        server = vertx.createHttpServer(options);

        return CompletableFuture.runAsync(
            () -> {
                CountDownLatch latch = new CountDownLatch(1);
                server.requestHandler(router).listen(configuration.getBindPort(), configuration.getBindHost(), result -> {
                    try {
                        if (result.failed()) {
                            LOGGER.warn("Failed to start Vert.x HttpServer on {}:{}, reason: {}",
                                configuration.getBindHost(),
                                configuration.getBindPort(),
                                result.cause().getMessage()
                            );

                            throw new RuntimeException(result.cause());
                        }

                        LOGGER.info("Vert.x HttpServer started on {}:{}", configuration.getBindHost(), configuration.getBindPort());
                    } finally {
                        latch.countDown();
                    }
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            executor
        );
    }

    protected CompletionStage<Void> stopAsync() {
        return CompletableFuture.runAsync(
            () -> {
                CountDownLatch latch = new CountDownLatch(1);

                // remove the platform-http component
                context.removeComponent(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME);

                server.close(result -> {
                    try {
                        if (result.failed()) {
                            LOGGER.warn("Failed to close Vert.x HttpServer reason: {}",
                                result.cause().getMessage()
                            );

                            throw new RuntimeException(result.cause());
                        }

                        LOGGER.info("Vert.x HttpServer stopped");
                    } finally {
                        latch.countDown();
                    }
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            executor
        );
    }
}
