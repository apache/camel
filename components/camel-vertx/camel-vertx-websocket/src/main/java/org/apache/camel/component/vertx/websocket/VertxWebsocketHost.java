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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.component.vertx.common.VertxHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Vert.x backed WebSocket host bound to a specified host & port
 */
public class VertxWebsocketHost {
    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketHost.class);
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([^/}]+)\\}");

    private final VertxWebsocketHostConfiguration hostConfiguration;
    private final VertxWebsocketHostKey hostKey;
    private final Map<String, Route> routeRegistry = new HashMap<>();
    private final List<VertxWebsocketPeer> connectedPeers = Collections.synchronizedList(new ArrayList<>());
    private final CamelContext camelContext;
    private HttpServer server;
    private int port = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PORT;

    public VertxWebsocketHost(CamelContext camelContext, VertxWebsocketHostConfiguration websocketHostConfiguration,
                              VertxWebsocketHostKey key) {
        this.camelContext = camelContext;
        this.hostConfiguration = websocketHostConfiguration;
        this.hostKey = key;
    }

    /**
     * Sets up a Vert.x route and handler for the WebSocket path specified by the consumer configuration
     */
    public void connect(VertxWebsocketConsumer consumer) {
        VertxWebsocketEndpoint endpoint = consumer.getEndpoint();
        VertxWebsocketConfiguration configuration = endpoint.getConfiguration();

        URI websocketURI = configuration.getWebsocketURI();

        // Transform from the Camel path param syntax /path/{key} to Vert.x web /path/:key
        String path = PATH_PARAMETER_PATTERN.matcher(websocketURI.getPath()).replaceAll(":$1");
        Router router = hostConfiguration.getRouter();
        Route route = router.route(path);
        LOG.info("Connected consumer for path {}", path);

        if (!ObjectHelper.isEmpty(configuration.getAllowedOriginPattern())) {
            CorsHandler corsHandler = CorsHandler.create().addRelativeOrigin(configuration.getAllowedOriginPattern());
            route.handler(corsHandler);
        }

        route.handler(routingContext -> {
            HttpServerRequest request = routingContext.request();
            String connectionHeader = request.headers().get(HttpHeaders.CONNECTION);
            if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
                routingContext.response().setStatusCode(400);
                routingContext.response().end("Can \"Upgrade\" only to \"WebSocket\".");
            } else {
                // we're about to upgrade the connection, which means an asynchronous
                // operation. We have to pause the request otherwise we will loose the
                // body of the request once the upgrade completes
                final boolean parseEnded = request.isEnded();
                if (!parseEnded) {
                    request.pause();
                }
                // upgrade
                request.toWebSocket(toWebSocket -> {
                    if (toWebSocket.succeeded()) {
                        // resume the parsing
                        if (!parseEnded) {
                            request.resume();
                        }
                        // handle the websocket session as usual
                        ServerWebSocket webSocket = toWebSocket.result();
                        SocketAddress socketAddress = webSocket.localAddress();
                        SocketAddress remote = webSocket.remoteAddress();

                        VertxWebsocketPeer peer = new VertxWebsocketPeer(webSocket, websocketURI.getPath());
                        connectedPeers.add(peer);

                        if (LOG.isDebugEnabled()) {
                            if (socketAddress != null) {
                                LOG.debug("WebSocket peer {} connected from {}", peer.getConnectionKey(), socketAddress.host());
                            }
                        }

                        webSocket.textMessageHandler(
                                message -> consumer.onMessage(peer.getConnectionKey(), message, remote, routingContext));
                        webSocket
                                .binaryMessageHandler(
                                        message -> consumer.onMessage(peer.getConnectionKey(), message.getBytes(), remote,
                                                routingContext));
                        webSocket.exceptionHandler(
                                exception -> consumer.onException(peer.getConnectionKey(), exception, remote, routingContext));
                        webSocket.closeHandler(closeEvent -> {
                            if (LOG.isDebugEnabled()) {
                                if (socketAddress != null) {
                                    LOG.debug("WebSocket peer {} disconnected from {}", peer.getConnectionKey(),
                                            socketAddress.host());
                                }
                            }

                            if (configuration.isFireWebSocketConnectionEvents()) {
                                consumer.onClose(peer.getConnectionKey(), remote, routingContext);
                            }

                            connectedPeers.remove(peer);
                        });

                        if (configuration.isFireWebSocketConnectionEvents()) {
                            consumer.onOpen(peer.getConnectionKey(), remote, routingContext, webSocket);
                        }
                    } else {
                        // the upgrade failed
                        routingContext.fail(toWebSocket.cause());
                    }
                });
            }
        });

        routeRegistry.put(websocketURI.getPath(), route);
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
    public void start() throws Exception {
        if (server == null) {
            Vertx vertx = hostConfiguration.getVertx();
            Router router = hostConfiguration.getRouter();
            HttpServerOptions options = hostConfiguration.getServerOptions();

            SSLContextParameters sslContextParameters = hostConfiguration.getSslContextParameters();
            if (sslContextParameters != null) {
                if (options == null) {
                    options = new HttpServerOptions();
                }

                VertxHelper.setupSSLOptions(camelContext, sslContextParameters, options);
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
     * Stops a previously started Vert.x HTTP server
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
        port = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PORT;
    }

    /**
     * Gets all WebSocket peers connected to the Vert.x HTTP sever together with their associated connection key
     */
    public List<VertxWebsocketPeer> getConnectedPeers() {
        return connectedPeers;
    }

    /**
     * Gets a connected peer for the given connection key
     */
    public VertxWebsocketPeer getConnectedPeer(String connectionKey) {
        return getConnectedPeers().stream()
                .filter(peer -> peer.getConnectionKey().equals(connectionKey))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the port that the server is bound to. This could be a random value if 0 was specified as the initial port
     * number
     */
    public int getPort() {
        return port;
    }

    /**
     * Determines whether the specified host name is one that is managed by this host.
     */
    public boolean isManagedHost(String host) {
        return hostKey.getHost().equals(host);
    }

    /**
     * Determines whether the specified port is one that is managed by this host.
     */
    public boolean isManagedPort(int port) {
        return getPort() == port;
    }
}
