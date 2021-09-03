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

    @UriPath(name = "host", defaultValue = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_HOST)
    private String host;
    @UriPath(name = "port", defaultValue = "0")
    private int port;
    @UriPath(name = "path", defaultValue = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PATH)
    @Metadata(required = true)
    private String path;
    @UriParam(label = "consumer")
    private String allowedOriginPattern;
    @UriParam(label = "consumer")
    private Router router;
    @UriParam(label = "consumer")
    private HttpServerOptions serverOptions;
    @UriParam(label = "producer")
    private HttpClientOptions clientOptions;
    @UriParam(label = "producer")
    private boolean sendToAll;
    @UriParam(label = "producer")
    private String clientSubProtocols;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    public String getHost() {
        return host;
    }

    /**
     * The host that the consumer should bind to or the host of the remote websocket destination that the producer
     * should connect to
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port that the consumer should bind to or port of the remote websocket destination that the producer should
     * connect to
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
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

    /**
     * The path that the consumer should bind to or path of the remote websocket destination that the producer should
     * connect to
     */
    public void setPath(String path) {
        this.path = path;
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
