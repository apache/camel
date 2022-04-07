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
    public static final String DEFAULT_VERTX_SERVER_PATH = "/";

    @Metadata(description = "Sends the message to the client with the given connection key. You can\n" +
                            "use a comma separated list of keys to send a message to multiple clients",
              javaType = "String")
    public static final String CONNECTION_KEY = "CamelVertxWebsocket.connectionKey";
    @Metadata(label = "producer", description = "Sends the message to all clients which are currently connected. You can\n" +
                                                "use the `sendToAll` option on the endpoint instead of using this header.",
              javaType = "boolean")
    public static final String SEND_TO_ALL = "CamelVertxWebsocket.sendToAll";
    @Metadata(label = "consumer", description = "The remote address.", javaType = "io.vertx.core.net.SocketAddress")
    public static final String REMOTE_ADDRESS = "CamelVertxWebsocket.remoteAddress";

    private VertxWebsocketConstants() {
    }
}
