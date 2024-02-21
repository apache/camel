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

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class VertxWebsocketConfiguration {

    private URI websocketURI;

    @UriPath
    @Metadata(required = true)
    private String host;
    @UriPath
    @Metadata(required = true)
    private int port;
    @UriPath
    private String path;
    @UriParam(label = "consumer")
    private String allowedOriginPattern;
    @UriParam(label = "consumer")
    private Router router;
    @UriParam(label = "consumer")
    private HttpServerOptions serverOptions;
    @UriParam(label = "consumer")
    private boolean consumeAsClient;
    @UriParam(label = "consumer", defaultValue = "0")
    private int reconnectInitialDelay;
    @UriParam(label = "consumer", defaultValue = "1000")
    private int reconnectInterval = 1000;
    @UriParam(label = "consumer", defaultValue = "0")
    private int maxReconnectAttempts;
    @UriParam(label = "producer")
    private HttpClientOptions clientOptions;
    @UriParam(label = "producer")
    private boolean sendToAll;
    @UriParam(label = "producer")
    private String clientSubProtocols;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "consumer")
    private boolean fireWebSocketConnectionEvents;
    @UriParam(label = "producer,consumer", defaultValue = "true")
    private boolean allowOriginHeader = true;
    @UriParam(label = "producer,consumer")
    private String originHeaderUrl;

    /**
     * The WebSocket URI address to use.
     */
    public void setWebsocketURI(URI websocketURI) {
        this.websocketURI = websocketURI;
        this.host = websocketURI.getHost();
        this.port = websocketURI.getPort();
        this.path = websocketURI.getPath();
    }

    public URI getWebsocketURI() {
        return websocketURI;
    }

    public String getHost() {
        return host;
    }

    /**
     * WebSocket hostname, such as localhost or a remote host when in client mode.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * WebSocket port number to use.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    /**
     * WebSocket path to use.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Sets customized options for configuring the WebSocket client used in the producer
     */
    public void setClientOptions(HttpClientOptions clientOptions) {
        this.clientOptions = clientOptions;
    }

    public HttpServerOptions getServerOptions() {
        return serverOptions;
    }

    /**
     * Sets customized options for configuring the HTTP server hosting the WebSocket for the consumer
     */
    public void setServerOptions(HttpServerOptions serverOptions) {
        this.serverOptions = serverOptions;
    }

    public boolean isConsumeAsClient() {
        return consumeAsClient;
    }

    public int getReconnectInitialDelay() {
        return reconnectInitialDelay;
    }

    /**
     * When consumeAsClient is set to true this sets the initial delay in milliseconds before attempting to reconnect to
     * a previously closed WebSocket.
     */
    public void setReconnectInitialDelay(int reconnectInitialDelay) {
        this.reconnectInitialDelay = reconnectInitialDelay;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    /**
     * When consumeAsClient is set to true this sets the interval in milliseconds at which reconnecting to a previously
     * closed WebSocket occurs.
     */
    public void setReconnectInterval(int reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    /**
     * When consumeAsClient is set to true this sets the maximum number of allowed reconnection attempts to a previously
     * closed WebSocket. A value of 0 (the default) will attempt to reconnect indefinitely.
     */
    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    /**
     * When set to true, the consumer acts as a WebSocket client, creating exchanges on each received WebSocket event.
     */
    public void setConsumeAsClient(boolean consumeAsClient) {
        this.consumeAsClient = consumeAsClient;
    }

    public HttpClientOptions getClientOptions() {
        return clientOptions;
    }

    /**
     * To send to all websocket subscribers. Can be used to configure at the endpoint level, instead of providing the
     * {@code VertxWebsocketConstants.SEND_TO_ALL} header on the message. Note that when using this option, the host
     * name specified for the vertx-websocket producer URI must match one used for an existing vertx-websocket consumer.
     * Note that this option only applies when producing messages to endpoints hosted by the vertx-websocket consumer
     * and not to an externally hosted WebSocket.
     */
    public void setSendToAll(boolean sendToAll) {
        this.sendToAll = sendToAll;
    }

    public boolean isSendToAll() {
        return sendToAll;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getAllowedOriginPattern() {
        return allowedOriginPattern;
    }

    /**
     * Regex pattern to match the origin header sent by WebSocket clients
     */
    public void setAllowedOriginPattern(String allowedOriginPattern) {
        this.allowedOriginPattern = allowedOriginPattern;
    }

    public Router getRouter() {
        return router;
    }

    /**
     * To use an existing vertx router for the HTTP server
     */
    public void setRouter(Router router) {
        this.router = router;
    }

    /**
     * Comma separated list of WebSocket subprotocols that the client should use for the Sec-WebSocket-Protocol header
     */
    public void setClientSubProtocols(String clientSubProtocols) {
        this.clientSubProtocols = clientSubProtocols;
    }

    public String getClientSubProtocols() {
        return clientSubProtocols;
    }

    /**
     * Whether the server consumer will create a message exchange when a new WebSocket peer connects or disconnects
     */
    public void setFireWebSocketConnectionEvents(boolean fireWebSocketConnectionEvents) {
        this.fireWebSocketConnectionEvents = fireWebSocketConnectionEvents;
    }

    public boolean isFireWebSocketConnectionEvents() {
        return fireWebSocketConnectionEvents;
    }

    public boolean isAllowOriginHeader() {
        return allowOriginHeader;
    }

    /**
     * Whether the WebSocket client should add the Origin header to the WebSocket handshake request.
     */
    public void setAllowOriginHeader(boolean allowOriginHeader) {
        this.allowOriginHeader = allowOriginHeader;
    }

    public String getOriginHeaderUrl() {
        return originHeaderUrl;
    }

    /**
     * The value of the Origin header that the WebSocket client should use on the WebSocket handshake request. When not
     * specified, the WebSocket client will automatically determine the value for the Origin from the request URL.
     */
    public void setOriginHeaderUrl(String originHeaderUrl) {
        this.originHeaderUrl = originHeaderUrl;
    }
}
