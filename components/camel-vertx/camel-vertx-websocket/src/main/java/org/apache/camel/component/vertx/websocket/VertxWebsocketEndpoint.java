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
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.common.VertxHelper;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.vertx.websocket.VertxWebsocketConstants.DEFAULT_VERTX_CLIENT_WSS_PORT;
import static org.apache.camel.component.vertx.websocket.VertxWebsocketConstants.DEFAULT_VERTX_CLIENT_WS_PORT;
import static org.apache.camel.component.vertx.websocket.VertxWebsocketConstants.ORIGIN_HTTP_HEADER_NAME;

@UriEndpoint(firstVersion = "3.5.0", scheme = "vertx-websocket", title = "Vert.x WebSocket",
             syntax = "vertx-websocket:host:port/path", category = { Category.HTTP, Category.NETWORKING },
             headersClass = VertxWebsocketConstants.class, lenientProperties = true)
public class VertxWebsocketEndpoint extends DefaultEndpoint implements EndpointServiceLocation, HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketEndpoint.class);

    @UriParam
    private VertxWebsocketConfiguration configuration;
    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;

    private WebSocketClient client;
    private WebSocketClientOptions resolvedClientOptions;
    private volatile WebSocket webSocket;
    private final Lock clientConsumerConnectLock = new ReentrantLock();

    public VertxWebsocketEndpoint(String uri, VertxWebsocketComponent component, VertxWebsocketConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "websocket";
    }

    @Override
    public boolean isLenientProperties() {
        // Enable custom query parameters to be passed on the producer WebSocket URI
        return true;
    }

    @Override
    public VertxWebsocketComponent getComponent() {
        return (VertxWebsocketComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new VertxWebsocketProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer;
        if (getConfiguration().isConsumeAsClient()) {
            consumer = new VertxWebsocketClientConsumer(this, processor);
        } else {
            consumer = new VertxWebsocketConsumer(this, processor);
        }
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStop() throws Exception {
        if (webSocket != null && !webSocket.isClosed()) {
            webSocket.close();
            webSocket = null;
        }

        if (client != null) {
            client.close();
            client = null;
        }

        super.doStop();
    }

    public VertxWebsocketConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new DefaultHeaderFilterStrategy();
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    protected Vertx getVertx() {
        return getComponent().getVertx();
    }

    /**
     * The lock under which the client consumers of this endpoint connect ({@code consumeAsClient=true}). It belongs to
     * the endpoint and not to the consumer, because a route that is stopped and started again gets a new consumer while
     * a reconnect attempt of the previous one can still be waiting for its handshake.
     */
    Lock getClientConsumerConnectLock() {
        return clientConsumerConnectLock;
    }

    /**
     * Clears the message, close, and exception handlers on the endpoint's WebSocket, but only if it is still open and
     * {@code owner} is the last consumer that registered handlers on it. The caller must hold
     * {@link #clientConsumerConnectLock}.
     */
    void clearWebSocketHandlers(WebSocket owner) {
        if (owner != null && webSocket == owner && !webSocket.isClosed()) {
            webSocket.textMessageHandler(null);
            webSocket.binaryMessageHandler(null);
            webSocket.closeHandler(null);
            webSocket.exceptionHandler(null);
        }
    }

    protected WebSocket getWebSocket() throws Exception {
        return getWebSocket((java.util.function.Consumer<WebSocket>) null);
    }

    /**
     * Connects to the WebSocket server, optionally invoking a setup callback on the socket <em>before</em> the blocking
     * {@code future.get()} returns. This guarantees that any message/close/exception handlers supplied by the callback
     * are registered atomically with the connection, so no messages sent by the server immediately after the handshake
     * can be missed.
     * <p>
     * If the WebSocket is already open (e.g. a producer connected first, or the consumer route was stopped and
     * restarted while the connection was healthy), the setup callback is still invoked on the existing socket so that
     * the caller's handlers are registered.
     */
    protected WebSocket getWebSocket(java.util.function.Consumer<WebSocket> setupCallback) throws Exception {
        if (client == null) {
            resolvedClientOptions = configuration.getClientOptions();
            if (resolvedClientOptions == null) {
                resolvedClientOptions = new WebSocketClientOptions();
            }

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), sslContextParameters, resolvedClientOptions);
            }

            client = getVertx().createWebSocketClient(resolvedClientOptions);
        }

        if (webSocket == null || webSocket.isClosed()) {
            WebSocketConnectOptions connectOptions = getWebSocketConnectOptions(resolvedClientOptions);
            CompletableFuture<WebSocket> future = new CompletableFuture<>();
            client.connect(connectOptions).onComplete(result -> {
                if (result.succeeded()) {
                    WebSocket ws = result.result();
                    if (future.isDone()) {
                        // the caller gave up waiting (see below): nobody would use or close this WebSocket
                        ws.close();
                        return;
                    }
                    LOG.info("Connected to WebSocket on {}", ws.remoteAddress());
                    if (setupCallback != null) {
                        try {
                            setupCallback.accept(ws);
                        } catch (Exception e) {
                            ws.close();
                            future.completeExceptionally(e);
                            return;
                        }
                    }
                    if (!future.complete(ws)) {
                        ws.close();
                    }
                } else {
                    future.completeExceptionally(result.cause());
                }
            });
            try {
                webSocket = future.get(resolvedClientOptions.getConnectTimeout(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                webSocket = null;
                // a connect that completes from now on closes its WebSocket
                future.cancel(false);
                throw e;
            }
        } else if (setupCallback != null) {
            // WebSocket already open — register the caller's handlers on the existing socket (CAMEL-25601).
            setupCallback.accept(webSocket);
        }
        return webSocket;
    }

    /**
     * Like {@link #getWebSocket(java.util.function.Consumer)}, but the setup callback also receives a flag indicating
     * whether the WebSocket was already open before this call ({@code true}) or was newly connected ({@code false}).
     * This lets the caller distinguish the two cases without a racy external check, since {@code getWebSocket} itself
     * knows which branch it took.
     */
    WebSocket getWebSocket(BiConsumer<WebSocket, Boolean> setupCallback) throws Exception {
        if (client == null) {
            resolvedClientOptions = configuration.getClientOptions();
            if (resolvedClientOptions == null) {
                resolvedClientOptions = new WebSocketClientOptions();
            }

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), sslContextParameters, resolvedClientOptions);
            }

            client = getVertx().createWebSocketClient(resolvedClientOptions);
        }

        if (webSocket == null || webSocket.isClosed()) {
            WebSocketConnectOptions connectOptions = getWebSocketConnectOptions(resolvedClientOptions);
            CompletableFuture<WebSocket> future = new CompletableFuture<>();
            client.connect(connectOptions).onComplete(result -> {
                if (result.succeeded()) {
                    WebSocket ws = result.result();
                    if (future.isDone()) {
                        ws.close();
                        return;
                    }
                    LOG.info("Connected to WebSocket on {}", ws.remoteAddress());
                    if (setupCallback != null) {
                        try {
                            setupCallback.accept(ws, false);
                        } catch (Exception e) {
                            ws.close();
                            future.completeExceptionally(e);
                            return;
                        }
                    }
                    if (!future.complete(ws)) {
                        ws.close();
                    }
                } else {
                    future.completeExceptionally(result.cause());
                }
            });
            try {
                webSocket = future.get(resolvedClientOptions.getConnectTimeout(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                webSocket = null;
                future.cancel(false);
                throw e;
            }
        } else {
            if (setupCallback != null) {
                // WebSocket already open — register the caller's handlers on the existing socket (CAMEL-25601).
                setupCallback.accept(webSocket, true);
            }
        }
        return webSocket;
    }

    protected WebSocket getWebSocket(Exchange exchange) throws Exception {
        return getWebSocket().exceptionHandler(event -> exchange.setException(event.getCause()));
    }

    protected WebSocketConnectOptions getWebSocketConnectOptions(WebSocketClientOptions options) {
        URI websocketURI = configuration.getWebsocketURI();
        WebSocketConnectOptions connectOptions = new WebSocketConnectOptions();
        connectOptions.setHost(websocketURI.getHost());
        connectOptions.setURI(URISupport.pathAndQueryOf(websocketURI));
        connectOptions.setSsl(options.isSsl() || websocketURI.getScheme().length() == 3);

        if (websocketURI.getPort() > 0) {
            connectOptions.setPort(websocketURI.getPort());
        } else {
            connectOptions.setPort(connectOptions.isSsl() ? DEFAULT_VERTX_CLIENT_WSS_PORT : DEFAULT_VERTX_CLIENT_WS_PORT);
        }

        String subProtocols = configuration.getClientSubProtocols();
        if (ObjectHelper.isNotEmpty(subProtocols)) {
            connectOptions.setSubProtocols(Arrays.asList(subProtocols.split(",")));
        }

        connectOptions.setAllowOriginHeader(configuration.isAllowOriginHeader());

        String defaultOriginHeader = configuration.getOriginHeaderUrl();
        if (ObjectHelper.isNotEmpty(defaultOriginHeader)) {
            connectOptions.addHeader(ORIGIN_HTTP_HEADER_NAME, defaultOriginHeader);
        }

        if (ObjectHelper.isNotEmpty(configuration.getHandshakeHeaders())) {
            configuration.getHandshakeHeaders()
                    .forEach((headerName, headerValue) -> connectOptions.addHeader(headerName, headerValue.toString()));
        }

        return connectOptions;
    }

    protected Map<VertxWebsocketHostKey, VertxWebsocketHost> getVertxHostRegistry() {
        return getComponent().getVertxHostRegistry();
    }

    /**
     * Finds a WebSocket associated with host for the given connection key
     */
    protected ServerWebSocket findPeerForConnectionKey(String connectionKey) {
        Map<VertxWebsocketHostKey, VertxWebsocketHost> registry = getVertxHostRegistry();
        for (VertxWebsocketHost host : registry.values()) {
            VertxWebsocketPeer peer = host.getConnectedPeer(connectionKey);
            if (peer != null && host.isManagedHost(getConfiguration().getWebsocketURI().getHost())
                    && host.isManagedPort(getConfiguration().getWebsocketURI().getPort())) {
                return peer.getWebSocket();
            }
        }
        return null;
    }

    /**
     * Finds all WebSockets associated with a host matching this endpoint configured port and resource path
     */
    protected Map<String, ServerWebSocket> findPeersForHostPort() {
        return getVertxHostRegistry()
                .values()
                .stream()
                .filter(host -> host.isManagedHost(getConfiguration().getWebsocketURI().getHost()))
                .filter(host -> host.isManagedPort(getConfiguration().getWebsocketURI().getPort()))
                .flatMap(host -> host.getConnectedPeers().stream())
                .filter(connectedPeer -> {
                    String producerPath = getConfiguration().getWebsocketURI().getPath();
                    String peerConnectedPath;
                    if (producerPath.contains("{") || producerPath.contains("*")) {
                        peerConnectedPath = connectedPeer.getRawPath();
                    } else {
                        peerConnectedPath = connectedPeer.getPath();
                    }
                    return VertxWebsocketHelper.webSocketHostPathMatches(peerConnectedPath, producerPath);
                })
                .collect(Collectors.toMap(VertxWebsocketPeer::getConnectionKey, VertxWebsocketPeer::getWebSocket));
    }
}
