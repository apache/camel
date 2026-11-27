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

import io.vertx.core.net.NetServerOptions;
import org.apache.camel.spi.Metadata;

public final class VertxWebsocketConstants {

    public static final String DEFAULT_VERTX_SERVER_HOST = NetServerOptions.DEFAULT_HOST;
    public static final int DEFAULT_VERTX_SERVER_PORT = NetServerOptions.DEFAULT_PORT;
    public static final int DEFAULT_VERTX_CLIENT_WS_PORT = 80;
    public static final int DEFAULT_VERTX_CLIENT_WSS_PORT = 443;
    public static final String ORIGIN_HTTP_HEADER_NAME = "Origin";

    @Metadata(description = "The key of a peer connected to the server consumer. The server consumer sets it to the key of the"
                            + " peer that the exchange comes from, a key that it creates when the peer connects. The producer"
                            + " sends the message to the peer with this key, or to each peer of a comma-separated list of keys,"
                            + " among the peers that are connected on its path to a server consumer of this component with the"
                            + " same host name and port.",
              javaType = "String")
    public static final String CONNECTION_KEY = "CamelVertxWebsocket.connectionKey";
    @Metadata(label = "producer",
              description = "Whether the producer sends the message to all the peers that are connected on its path to a"
                            + " server consumer of this component with the same host name and port. Replaces the sendToAll"
                            + " option of the endpoint for this message.",
              javaType = "boolean")
    public static final String SEND_TO_ALL = "CamelVertxWebsocket.sendToAll";
    @Metadata(label = "consumer",
              description = "The address of the peer that the exchange comes from, as a Vert.x SocketAddress with its host and"
                            + " port. Set by the server consumer.",
              javaType = "io.vertx.core.net.SocketAddress")
    public static final String REMOTE_ADDRESS = "CamelVertxWebsocket.remoteAddress";
    @Metadata(label = "consumer",
              description = "The event that the exchange comes from: MESSAGE for a received message, and OPEN or CLOSE for a"
                            + " peer that connects or disconnects when fireWebSocketConnectionEvents is true. Set by the server"
                            + " consumer. An exchange with the event ERROR goes to the exception handler of the consumer"
                            + " instead of the route.",
              javaType = "org.apache.camel.component.vertx.websocket.VertxWebsocketEvent")
    public static final String EVENT = "CamelVertxWebsocket.event";

    private VertxWebsocketConstants() {
    }
}
