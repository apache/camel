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

    @UriPath
    @Metadata(required = true)
    private URI websocketURI;
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

    /**
     * The WebSocket URI address to use.
     */
    public void setWebsocketURI(URI websocketURI) {
        this.websocketURI = websocketURI;
    }

    public URI getWebsocketURI() {
        return websocketURI;
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
     * To send to all websocket subscribers. Can be used to configure on endpoint level, instead of having to use the
     * {@code VertxWebsocketConstants.SEND_TO_ALL} header on the message.
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
}
