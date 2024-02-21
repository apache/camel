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
package org.apache.camel.component.knative.http;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KnativeHttpServer extends ServiceSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnativeHttpServer.class);

    private final CamelContext context;
    private final String host;
    private final int port;
    private final String path;
    private final BlockingQueue<HttpServerRequest> requests;
    private final Handler<RoutingContext> handler;

    private Vertx vertx;
    private Router router;
    private ExecutorService executor;
    private HttpServer server;

    public KnativeHttpServer(CamelContext context) {
        this(context, "localhost", AvailablePortFinder.getNextAvailable(), "/", null);
    }

    public KnativeHttpServer(CamelContext context, int port) {
        this(context, "localhost", port, "/", null);
    }

    public KnativeHttpServer(CamelContext context, int port, Handler<RoutingContext> handler) {
        this(context, "localhost", port, "/", handler);
    }

    public KnativeHttpServer(CamelContext context, Handler<RoutingContext> handler) {
        this(context, "localhost", AvailablePortFinder.getNextAvailable(), "/", handler);
    }

    public KnativeHttpServer(CamelContext context, String host, int port, String path) {
        this(context, host, port, path, null);
    }

    public KnativeHttpServer(CamelContext context, String host, String path) {
        this(context, host, AvailablePortFinder.getNextAvailable(), path, null);
    }

    public KnativeHttpServer(CamelContext context, String host, String path, Handler<RoutingContext> handler) {
        this(context, host, AvailablePortFinder.getNextAvailable(), path, handler);
    }

    public KnativeHttpServer(CamelContext context, String host, int port, String path, Handler<RoutingContext> handler) {
        this.context = context;
        this.host = host;
        this.port = port;
        this.path = path;
        this.requests = new LinkedBlockingQueue<>();
        this.handler = handler != null
                ? handler
                : event -> {
                    event.response().setStatusCode(200);
                    event.response().end();
                };
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public HttpServerRequest poll(int timeout, TimeUnit unit) throws InterruptedException {
        return requests.poll(timeout, unit);
    }

    @Override
    protected void doStart() {
        this.executor = context.getExecutorServiceManager().newSingleThreadExecutor(this, "knative-http-server");
        this.vertx = Vertx.vertx();
        this.server = vertx.createHttpServer();
        this.router = Router.router(vertx);
        this.router.route(path)
                .handler(event -> {
                    event.request().resume();
                    BodyHandler.create().handle(event);
                })
                .handler(event -> {
                    this.requests.offer(event.request());
                    event.next();
                })
                .handler(handler);

        CompletableFuture.runAsync(
                () -> {
                    CountDownLatch latch = new CountDownLatch(1);
                    server.requestHandler(router).listen(port, host, result -> {
                        try {
                            if (result.failed()) {
                                LOGGER.warn("Failed to start Vert.x HttpServer on {}:{}, reason: {}",
                                        host,
                                        port,
                                        result.cause().getMessage());

                                throw new RuntimeException(result.cause());
                            }

                            LOGGER.info("Vert.x HttpServer started on {}:{}", host, port);
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
                executor).toCompletableFuture().join();
    }

    @Override
    protected void doStop() throws Exception {
        try {
            if (server != null) {
                CompletableFuture.runAsync(
                        () -> {
                            CountDownLatch latch = new CountDownLatch(1);

                            // remove the platform-http component
                            context.removeComponent(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME);

                            server.close(result -> {
                                try {
                                    if (result.failed()) {
                                        LOGGER.warn("Failed to close Vert.x HttpServer reason: {}",
                                                result.cause().getMessage());

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
                        executor).toCompletableFuture().join();
            }
        } finally {
            this.server = null;
        }

        if (vertx != null) {
            Future<?> future = executor.submit(
                    () -> {
                        CountDownLatch latch = new CountDownLatch(1);

                        vertx.close(result -> {
                            try {
                                if (result.failed()) {
                                    LOGGER.warn("Failed to close Vert.x reason: {}",
                                            result.cause().getMessage());

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
                    });

            try {
                future.get();
            } finally {
                vertx = null;
            }
        }

        if (executor != null) {
            context.getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }
}
