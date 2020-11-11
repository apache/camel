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
package org.apache.camel.component.vertx.websocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Vert.x backed WebSocket host bound to a specified host & port
 */
public class VertxWebsocketHost {
    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketHost.class);

    private final VertxWebsocketHostConfiguration hostConfiguration;
    private final VertxWebsocketHostKey hostKey;
    private final Map<String, Route> routeRegistry = new HashMap<>();
    private final Map<String, ServerWebSocket> connectedPeers = new ConcurrentHashMap<>();
    private HttpServer server;
    private int port = VertxWebsocketContants.DEFAULT_VERTX_SERVER_PORT;

    public VertxWebsocketHost(VertxWebsocketHostConfiguration websocketHostConfiguration, VertxWebsocketHostKey key) {
        this.hostConfiguration = websocketHostConfiguration;
        this.hostKey = key;
    }

    /**
     * Sets up a Vert.x route and handler for the WebSocket path specified by the consumer configuration
     */
    public void connect(VertxWebsocketConsumer consumer) {
        VertxWebsocketEndpoint endpoint = consumer.getEndpoint();
        VertxWebsocketConfiguration configuration = endpoint.getConfiguration();

        LOG.info("Connected consumer for path {}", configuration.getPath());
        Router router = hostConfiguration.getRouter();
        Route route = router.route(configuration.getPath());

        if (!ObjectHelper.isEmpty(configuration.getAllowedOriginPattern())) {
            CorsHandler corsHandler = CorsHandler.create(configuration.getAllowedOriginPattern());
            route.handler(corsHandler);
        }

        route.handler(routingContext -> {
            HttpServerRequest request = routingContext.request();
            ServerWebSocket webSocket = request.upgrade();
            SocketAddress socketAddress = webSocket.localAddress();

            String connectionKey = UUID.randomUUID().toString();
            connectedPeers.put(connectionKey, webSocket);

            if (LOG.isDebugEnabled()) {
                if (socketAddress != null) {
                    LOG.debug("WebSocket peer {} connected from {}", connectionKey, socketAddress.host());
                }
            }

            webSocket.textMessageHandler(message -> consumer.onMessage(connectionKey, message));
            webSocket.binaryMessageHandler(message -> consumer.onMessage(connectionKey, message.getBytes()));
            webSocket.exceptionHandler(exception -> consumer.onException(connectionKey, exception));
            webSocket.closeHandler(closeEvent -> {
                if (LOG.isDebugEnabled()) {
                    if (socketAddress != null) {
                        LOG.debug("WebSocket peer {} disconnected from {}", connectionKey, socketAddress.host());
                    }
                }
                connectedPeers.remove(connectionKey);
            });
        });

        routeRegistry.put(configuration.getPath(), route);
    }

    /**
     * Removes the Vert.x route and handler for the WebSocket path specified by the consumer configuration
     */
    public void disconnect(String path) {
        LOG.info("Disconnected consumer for path {}", path);
        Route route = routeRegistry.remove(path);
        route.remove();
        if (routeRegistry.isEmpty()) {
            try {
                stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Starts a Vert.x HTTP server to host the WebSocket router
     */
    public void start() throws InterruptedException, ExecutionException {
        if (server == null) {
            Vertx vertx = hostConfiguration.getVertx();
            Router router = hostConfiguration.getRouter();
            HttpServerOptions options = hostConfiguration.getServerOptions();

            SSLContextParameters sslContextParameters = hostConfiguration.getSslContextParameters();
            if (sslContextParameters != null) {
                if (options == null) {
                    options = new HttpServerOptions();
                }

                VertxWebsocketHelper.setupSSLOptions(sslContextParameters, options);
            }

            if (options != null) {
                server = vertx.createHttpServer(options);
            } else {
                server = vertx.createHttpServer();
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            server.requestHandler(router).listen(hostKey.getPort(), hostKey.getHost(), result -> {
                if (!result.failed()) {
                    port = result.result().actualPort();
                    future.complete(null);
                    LOG.info("Vert.x HTTP server started on {}:{}", hostKey.getHost(), port);
                } else {
                    future.completeExceptionally(result.cause());
                }
            });
            future.get();
        }
    }

    /**
     * Starts a previously started Vert.x HTTP server
     */
    public void stop() throws ExecutionException, InterruptedException {
        if (server != null) {
            LOG.info("Stopping server");
            try {
                CompletableFuture<Void> future = new CompletableFuture<>();
                server.close(result -> {
                    if (result.failed()) {
                        future.completeExceptionally(result.cause());
                        return;
                    }
                    LOG.info("Vert.x HTTP server stopped");
                    future.complete(null);
                });
                future.get();
            } finally {
                this.server = null;
            }
        }
        connectedPeers.clear();
        routeRegistry.clear();
        port = VertxWebsocketContants.DEFAULT_VERTX_SERVER_PORT;
    }

    /**
     * Gets all WebSocket peers connected to the Vert.x HTTP sever together with their associated connection key
     */
    public Map<String, ServerWebSocket> getConnectedPeers() {
        return connectedPeers;
    }

    /**
     * Gets the port that the server is bound to. This could be a random value if 0 was specified as the initial port
     * number
     */
    public int getPort() {
        return port;
    }
}
