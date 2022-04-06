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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(firstVersion = "3.5.0", scheme = "vertx-websocket", title = "Vert.x WebSocket",
             syntax = "vertx-websocket:host:port/path", category = { Category.WEBSOCKET },
             headersClass = VertxWebsocketConstants.class)
public class VertxWebsocketEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketEndpoint.class);

    @UriParam
    private VertxWebsocketConfiguration configuration;

    private WebSocket webSocket;

    public VertxWebsocketEndpoint(String uri, VertxWebsocketComponent component, VertxWebsocketConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
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
        VertxWebsocketConsumer consumer = new VertxWebsocketConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStop() throws Exception {
        if (webSocket != null && !webSocket.isClosed()) {
            webSocket.close();
            webSocket = null;
        }
        super.doStop();
    }

    public VertxWebsocketConfiguration getConfiguration() {
        return configuration;
    }

    protected Vertx getVertx() {
        return getComponent().getVertx();
    }

    protected WebSocket getWebSocket(Exchange exchange) throws Exception {
        if (webSocket == null || webSocket.isClosed()) {
            HttpClientOptions options = configuration.getClientOptions();
            HttpClient client;

            if (options == null) {
                options = new HttpClientOptions();
            }

            SSLContextParameters sslContextParameters = configuration.getSslContextParameters();
            if (sslContextParameters != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), sslContextParameters, options);
            }

            client = getVertx().createHttpClient(options);

            WebSocketConnectOptions connectOptions = new WebSocketConnectOptions();
            connectOptions.setHost(configuration.getHost());
            connectOptions.setPort(configuration.getPort());
            connectOptions.setURI(configuration.getPath());
            connectOptions.setSsl(options.isSsl());

            String subProtocols = configuration.getClientSubProtocols();
            if (ObjectHelper.isNotEmpty(subProtocols)) {
                connectOptions.setSubProtocols(Arrays.asList(subProtocols.split(",")));
            }

            CompletableFuture<WebSocket> future = new CompletableFuture<>();
            client.webSocket(connectOptions, result -> {
                if (!result.failed()) {
                    LOG.info("Connected to WebSocket on {}:{}", configuration.getHost(), configuration.getPort());
                    future.complete(result.result());
                } else {
                    webSocket = null;
                    future.completeExceptionally(result.cause());
                }
            });
            webSocket = future.get(options.getConnectTimeout(), TimeUnit.MILLISECONDS);
            webSocket.exceptionHandler(event -> exchange.setException(event.getCause()));
        }
        return webSocket;
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
            Map<String, ServerWebSocket> hostPeers = host.getConnectedPeers();
            if (hostPeers.containsKey(connectionKey) && host.getPort() == getConfiguration().getPort()) {
                return hostPeers.get(connectionKey);
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
                .filter(host -> host.getPort() == getConfiguration().getPort())
                .flatMap(host -> host.getConnectedPeers().entrySet().stream())
                .filter(entry -> entry.getValue().path().equals(getConfiguration().getPath()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected boolean isManagedPort() {
        return getVertxHostRegistry().values()
                .stream()
                .anyMatch(host -> host.getPort() == getConfiguration().getPort());
    }
}
