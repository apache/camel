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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
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
@ManagedResource(description = "Vert.x HTTP Server")
public class VertxPlatformHttpServer extends ServiceSupport implements CamelContextAware, StaticService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPlatformHttpServer.class);

    private final VertxPlatformHttpServerConfiguration configuration;

    private CamelContext context;
    private ExecutorService executor;

    private boolean localVertx;
    private Vertx vertx;
    private Router router;
    private Router subRouter;
    private HttpServer server;

    public VertxPlatformHttpServer(VertxPlatformHttpServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }

    public HttpServer getServer() {
        return server;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        if (ServiceHelper.isStarted(this)) {
            throw new IllegalArgumentException("Can't set the Vertx instance after the service has been started");
        }

        this.vertx = vertx;
        this.localVertx = false;
    }

    @Override
    protected void doInit() throws Exception {
        // we can only optimize to lookup existing vertx instance at init phase
        vertx = lookupVertx();
        if (vertx != null) {
            LOGGER.info("Found Vert.x instance in registry: {}", vertx);
        }
    }

    @Override
    protected void doStart() throws Exception {
        initializeServer();
        startServer();
    }

    @Override
    protected void doStop() throws Exception {
        stopServer();
        stopVertx();

        if (this.executor != null) {
            this.context.getExecutorServiceManager().shutdown(this.executor);
            this.executor = null;
        }
    }

    @ManagedAttribute(description = "HTTP port number")
    public int getPort() {
        if (server != null) {
            return server.actualPort();
        }
        return configuration.getBindPort();
    }

    @ManagedAttribute(description = "HTTP hostname")
    public String getHost() {
        return configuration.getBindHost();
    }

    @ManagedAttribute(description = "HTTP context-path")
    public String getPath() {
        return configuration.getPath();
    }

    @ManagedAttribute(description = "HTTP maximum HTTP body size")
    public Long getMaxBodySize() {
        return configuration.getMaxBodySize();
    }

    @ManagedAttribute(description = "Should SSL be used from global SSL configuration")
    public boolean isUseGlobalSslContextParameters() {
        return configuration.isUseGlobalSslContextParameters();
    }

    // *******************************
    //
    // Helpers
    //
    // *******************************

    protected Vertx lookupVertx() {
        return CamelContextHelper.findSingleByType(context, Vertx.class);
    }

    protected Vertx createVertxInstance() {
        VertxOptions options = CamelContextHelper.findSingleByType(context, VertxOptions.class);
        if (options == null) {
            options = new VertxOptions();
        }

        return Vertx.vertx(options);
    }

    protected void initializeServer() {
        if (vertx == null) {
            vertx = lookupVertx();
            if (vertx == null) {
                LOGGER.debug("Creating new Vert.x instance");
                vertx = createVertxInstance();
                localVertx = true;
            }
        }

        this.router = Router.router(vertx);
        this.subRouter = Router.router(vertx);

        if (configuration.getCors().isEnabled()) {
            subRouter.route().handler(createCorsHandler(configuration));
        }

        router.mountSubRouter(configuration.getPath(), subRouter);

        context.getRegistry().bind(
                VertxPlatformHttpRouter.PLATFORM_HTTP_ROUTER_NAME,
                new VertxPlatformHttpRouter(this, vertx, subRouter) {
                    @Override
                    public Handler<RoutingContext> bodyHandler() {
                        return createBodyHandler(configuration);
                    }
                });
    }

    protected void startServer() throws Exception {
        HttpServerOptions options = new HttpServerOptions();

        configureSSL(options, configuration, context);

        executor = context.getExecutorServiceManager().newSingleThreadExecutor(this, "platform-http-service");
        server = vertx.createHttpServer(options);

        CompletableFuture.runAsync(
                () -> {
                    CountDownLatch latch = new CountDownLatch(1);
                    server.requestHandler(router).listen(configuration.getBindPort(), configuration.getBindHost(), result -> {
                        try {
                            if (result.failed()) {
                                LOGGER.warn("Failed to start Vert.x HttpServer on {}:{}, reason: {}",
                                        configuration.getBindHost(),
                                        configuration.getBindPort(),
                                        result.cause().getMessage());

                                throw new RuntimeException(result.cause());
                            }

                            LOGGER.info("Vert.x HttpServer started on {}:{}", configuration.getBindHost(),
                                    configuration.getBindPort());
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

    protected void stopServer() {
        if (this.server == null) {
            return;
        }

        try {
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
        } finally {
            this.server = null;
        }
    }

    protected void stopVertx() {
        if (this.vertx == null || this.localVertx) {
            return;
        }

        try {
            CompletableFuture.runAsync(
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
                    },
                    executor).toCompletableFuture().join();
        } finally {
            this.vertx = null;
            this.localVertx = false;
        }
    }
}
