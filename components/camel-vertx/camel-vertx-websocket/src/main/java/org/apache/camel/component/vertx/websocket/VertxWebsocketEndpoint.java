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
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.common.VertxHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
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
public class VertxWebsocketEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketEndpoint.class);

    @UriParam
    private VertxWebsocketConfiguration configuration;

    private HttpClient client;
    private WebSocket webSocket;

    public VertxWebsocketEndpoint(String uri, VertxWebsocketComponent component, VertxWebsocketConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
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

    protected Vertx getVertx() {
        return getComponent().getVertx();
    }

    protected WebSocket getWebSocket() throws Exception {
        if (client == null) {
            HttpClientOptions options = configuration.getClientOptions();
            if (options == null) {
                options = new HttpClientOptions();
            }

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), sslContextParameters, options);
            }

            client = getVertx().createHttpClient(options);
        }

        if (webSocket == null || webSocket.isClosed()) {
            HttpClientOptions clientOptions = configuration.getClientOptions();

            if (clientOptions == null) {
                clientOptions = new HttpClientOptions();
            }

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), sslContextParameters, clientOptions);
            }

            WebSocketConnectOptions connectOptions = getWebSocketConnectOptions(clientOptions);
            CompletableFuture<WebSocket> future = new CompletableFuture<>();
            client.webSocket(connectOptions, result -> {
                if (!result.failed()) {
                    LOG.info("Connected to WebSocket on {}", result.result().remoteAddress());
                    future.complete(result.result());
                } else {
                    webSocket = null;
                    future.completeExceptionally(result.cause());
                }
            });
            webSocket = future.get(clientOptions.getConnectTimeout(), TimeUnit.MILLISECONDS);
        }
        return webSocket;
    }

    protected WebSocket getWebSocket(Exchange exchange) throws Exception {
        return getWebSocket().exceptionHandler(event -> exchange.setException(event.getCause()));
    }

    protected WebSocketConnectOptions getWebSocketConnectOptions(HttpClientOptions options) {
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
