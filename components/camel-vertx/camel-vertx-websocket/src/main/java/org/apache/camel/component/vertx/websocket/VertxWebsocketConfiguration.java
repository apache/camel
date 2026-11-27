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
import java.util.Map;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.ext.web.Router;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class VertxWebsocketConfiguration {

    private URI websocketURI;

    @UriPath(description = "The host name or IP address. The server consumer binds its HTTP server to it, and the producer"
                           + " and a consumer with consumeAsClient=true connect to it. If the URI has no host, the defaultHost"
                           + " option of the component is used.")
    @Metadata(required = true)
    private String host;
    @UriPath(description = "The port number. The server consumer binds its HTTP server to it, and with 0 to a free port that"
                           + " the operating system picks. The producer and a consumer with consumeAsClient=true connect to it,"
                           + " and with 0 to port 80, or to port 443 when the connection uses TLS. If the URI has no port, the"
                           + " defaultPort option of the component is used.")
    @Metadata(required = true)
    private int port;
    @UriPath(description = "The path of the WebSocket. The server consumer accepts WebSocket connections on it: the path can"
                           + " hold parameters such as /chat/{user}, whose values the consumer sets as headers, and can end"
                           + " with * to accept each path that starts with it. The producer and a consumer with"
                           + " consumeAsClient=true connect to it. If the URI has no path, / is used.")
    private String path;
    @UriParam(label = "consumer",
              description = "A regular expression for the Origin header of the WebSocket handshake requests that the server"
                            + " consumer accepts. A handshake request whose Origin header does not match is rejected with"
                            + " status 403. If not set, the server consumer does not check the Origin header.")
    private String allowedOriginPattern;
    @UriParam(label = "consumer,advanced",
              description = "The Vert.x Router of the HTTP server of the server consumer, for example to serve other HTTP"
                            + " routes on the same server. All consumers on the same host and port share one HTTP server, which"
                            + " uses the router of the consumer that starts first. If not set, the router option of the"
                            + " component is used.")
    private Router router;
    @UriParam(label = "consumer,advanced",
              description = "The Vert.x HttpServerOptions of the HTTP server of the server consumer. All consumers on the same"
                            + " host and port share one HTTP server, which uses the options of the consumer that starts first."
                            + " The server listens on the host and port of the endpoint, not on those of the options.")
    private HttpServerOptions serverOptions;
    @UriParam(label = "consumer",
              description = "Whether the consumer connects as a WebSocket client to the server at the host, port and path,"
                            + " instead of starting a WebSocket server, and routes each text or binary message that the server"
                            + " sends. The consumer fails to start if it cannot connect. When the connection is lost later, it"
                            + " reconnects as set by reconnectInitialDelay, reconnectInterval and maxReconnectAttempts.")
    private boolean consumeAsClient;
    @UriParam(label = "consumer",
              description = "The delay in milliseconds from the loss of the connection to the first reconnect attempt, for a"
                            + " consumer with consumeAsClient=true. With the default 0, the first attempt is made at once.")
    private int reconnectInitialDelay;
    @UriParam(label = "consumer", defaultValue = "1000",
              description = "The interval in milliseconds between the reconnect attempts of a consumer with"
                            + " consumeAsClient=true. When an attempt is still running at the end of an interval, no other"
                            + " attempt starts until it ends.")
    private int reconnectInterval = 1000;
    @UriParam(label = "consumer",
              description = "The maximum number of failed reconnect attempts of a consumer with consumeAsClient=true, counted"
                            + " again for each loss of the connection. When they are used up, the consumer logs a warning and"
                            + " stays started without a connection. With the default 0, the consumer tries to reconnect"
                            + " without limit.")
    private int maxReconnectAttempts;
    @UriParam(label = "consumer")
    private String connectMessage;
    @UriParam(label = "consumer,advanced")
    private boolean parallelProcessing;
    @UriParam(label = "producer,advanced",
              description = "The Vert.x WebSocketClientOptions of the WebSocket client that the producer and a consumer with"
                            + " consumeAsClient=true connect with. Its connect timeout also limits how long they wait for the"
                            + " handshake to complete. When sslContextParameters is set, its TLS settings are applied on top of"
                            + " these options.")
    private WebSocketClientOptions clientOptions;
    @UriParam(label = "producer",
              description = "Whether the producer sends each message to all the peers that are connected on the path of the"
                            + " endpoint to a server consumer of this component with the same host name and port, instead of"
                            + " to a remote WebSocket server. If no peer is connected, the message is not sent. The"
                            + " CamelVertxWebsocket.sendToAll header replaces this option for a message.")
    private boolean sendToAll;
    @UriParam(label = "producer",
              description = "A comma-separated list of the WebSocket subprotocols that the producer and a consumer with"
                            + " consumeAsClient=true request in the Sec-WebSocket-Protocol header of the handshake request.")
    private String clientSubProtocols;
    @UriParam(label = "consumer",
              description = "Whether the server consumer also routes an exchange when a peer connects, with the event OPEN and"
                            + " the Vert.x ServerWebSocket as body, and when a peer disconnects, with the event CLOSE and no"
                            + " body. The CamelVertxWebsocket.event header holds the event. A consumer with"
                            + " consumeAsClient=true does not use it.")
    private boolean fireWebSocketConnectionEvents;
    @UriParam(label = "security", defaultValue = "true",
              description = "Whether the producer and a consumer with consumeAsClient=true add an Origin header, derived from"
                            + " the host and port of the server, to the WebSocket handshake request. Set it to false for a"
                            + " server that rejects handshake requests with an Origin header. Not used when originHeaderUrl is"
                            + " set, as that Origin is always sent.")
    private boolean allowOriginHeader = true;
    @UriParam(label = "security",
              description = "The value of the Origin header of the WebSocket handshake request that the producer and a"
                            + " consumer with consumeAsClient=true send, such as https://example.com. It is sent even when"
                            + " allowOriginHeader is false. If not set, the Origin is derived from the host and port of the"
                            + " server, unless allowOriginHeader is false.")
    private String originHeaderUrl;
    @UriParam(label = "security", prefix = "handshake.", multiValue = true,
              description = "The HTTP headers to add to the WebSocket handshake request that the producer and a consumer with"
                            + " consumeAsClient=true send, such as handshake.X-Tenant=acme for an X-Tenant header. The server"
                            + " consumer does not use them.")
    private Map<String, Object> handshakeHeaders;
    @UriParam(label = "security",
              description = "The TLS configuration as org.apache.camel.support.jsse.SSLContextParameters. The server consumer"
                            + " accepts wss connections with it, and all consumers on the same host and port must use the same"
                            + " instance or none, otherwise the consumer fails to start. The producer and a consumer with"
                            + " consumeAsClient=true connect with TLS using it. If not set and the"
                            + " useGlobalSslContextParameters option of the component is true, the global SSL context"
                            + " parameters are used.")
    private SSLContextParameters sslContextParameters;

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

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setClientOptions(WebSocketClientOptions clientOptions) {
        this.clientOptions = clientOptions;
    }

    public HttpServerOptions getServerOptions() {
        return serverOptions;
    }

    public void setServerOptions(HttpServerOptions serverOptions) {
        this.serverOptions = serverOptions;
    }

    public boolean isConsumeAsClient() {
        return consumeAsClient;
    }

    public int getReconnectInitialDelay() {
        return reconnectInitialDelay;
    }

    public void setReconnectInitialDelay(int reconnectInitialDelay) {
        this.reconnectInitialDelay = reconnectInitialDelay;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public void setReconnectInterval(int reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    public String getConnectMessage() {
        return connectMessage;
    }

    /**
     * When consumeAsClient is set to true, a text message to send to the server each time the consumer connects: when
     * it starts and after each reconnect. For example a subscribe request, for a server that only sends messages to a
     * client that asked for them.
     */
    public void setConnectMessage(String connectMessage) {
        this.connectMessage = connectMessage;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    /**
     * When consumeAsClient is set to true, whether the received messages are routed in parallel, on the threads of the
     * Vert.x worker pool, in any order. By default (false), the messages of the WebSocket are routed one after the
     * other, in the order they are received.
     */
    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public void setConsumeAsClient(boolean consumeAsClient) {
        this.consumeAsClient = consumeAsClient;
    }

    public WebSocketClientOptions getClientOptions() {
        return clientOptions;
    }

    public void setSendToAll(boolean sendToAll) {
        this.sendToAll = sendToAll;
    }

    public boolean isSendToAll() {
        return sendToAll;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getAllowedOriginPattern() {
        return allowedOriginPattern;
    }

    public void setAllowedOriginPattern(String allowedOriginPattern) {
        this.allowedOriginPattern = allowedOriginPattern;
    }

    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public void setClientSubProtocols(String clientSubProtocols) {
        this.clientSubProtocols = clientSubProtocols;
    }

    public String getClientSubProtocols() {
        return clientSubProtocols;
    }

    public void setFireWebSocketConnectionEvents(boolean fireWebSocketConnectionEvents) {
        this.fireWebSocketConnectionEvents = fireWebSocketConnectionEvents;
    }

    public boolean isFireWebSocketConnectionEvents() {
        return fireWebSocketConnectionEvents;
    }

    public boolean isAllowOriginHeader() {
        return allowOriginHeader;
    }

    public void setAllowOriginHeader(boolean allowOriginHeader) {
        this.allowOriginHeader = allowOriginHeader;
    }

    public String getOriginHeaderUrl() {
        return originHeaderUrl;
    }

    public void setOriginHeaderUrl(String originHeaderUrl) {
        this.originHeaderUrl = originHeaderUrl;
    }

    public Map<String, Object> getHandshakeHeaders() {
        return handshakeHeaders;
    }

    public void setHandshakeHeaders(final Map<String, Object> handshakeHeaders) {
        this.handshakeHeaders = handshakeHeaders;
    }
}
