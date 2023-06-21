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
package org.apache.camel.component.websocket;

import org.apache.camel.spi.Metadata;

public final class WebsocketConstants {

    public static final int DEFAULT_PORT = 9292;
    public static final String DEFAULT_HOST = "0.0.0.0";

    @Metadata(description = "*Producer:* Sends the message to all clients which are currently connected. You can\n" +
                            "use the `sendToAll` option on the endpoint instead of using this header.\n" +
                            "*Consumer:* Connection key identifying an individual client connection. " +
                            "You can save this and specify it again when routing to a producer endpoing in order to direct messages to a specific connected client.",
              javaType = "String")
    public static final String CONNECTION_KEY = "websocket.connectionKey";
    @Metadata(label = "producer", description = "Sends the message to all clients which are currently connected. " +
                                                "You can use the sendToAll option on the endpoint instead of using this header.",
              javaType = "Boolean")
    public static final String SEND_TO_ALL = "websocket.sendToAll";
    @Metadata(label = "consumer", description = "Remote address of the websocket session.",
              javaType = "java.net.SocketAddress")
    public static final String REMOTE_ADDRESS = "websocket.remoteAddress";
    @Metadata(label = "consumer",
              description = "If a specific subprotocol was negotiated, it will be specfied in this header. " +
                            "Note that if you specify the \"any\" subprotocol to be supported, and a client requests a specific subprotocol, "
                            +
                            "the connection will be accepted without a specific subprotocol being used. " +
                            "You need to specifically support a given protocol by name if you want it returned to the client and to show up in the message header.",
              javaType = "String")
    public static final String SUBPROTOCOL = "websocket.subprotocol";
    @Metadata(label = "consumer",
              description = "If you specify a wildcard URI path for an endpoint, and a websocket client connects to that websocket endpoing, "
                            +
                            "the relative path that the client specified will be provided in this header.\n" +
                            "\n" +
                            "For example, if you specified `websocket://0.0.0.0:80/api/*` as your endpoint URI, and a client connects to the server at `ws://host.com/api/specialized/apipath` "
                            +
                            "then `specialized/apipath` is provided in the relative path header of all messages from that client.",
              javaType = "String")
    public static final String RELATIVE_PATH = "websocket.relativePath";

    public static final String WS_PROTOCOL = "ws";
    public static final String WSS_PROTOCOL = "wss";

    private WebsocketConstants() {
    }
}
