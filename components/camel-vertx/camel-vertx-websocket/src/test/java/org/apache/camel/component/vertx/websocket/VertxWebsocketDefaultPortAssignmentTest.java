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
import io.vertx.core.http.WebSocketConnectOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.component.vertx.websocket.VertxWebsocketConstants.DEFAULT_VERTX_CLIENT_WSS_PORT;
import static org.apache.camel.component.vertx.websocket.VertxWebsocketConstants.DEFAULT_VERTX_CLIENT_WS_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxWebsocketDefaultPortAssignmentTest extends VertxWebSocketTestSupport {

    @ParameterizedTest
    @ValueSource(strings = { "", "ws:", "wss:" })
    void testDefaultPortAssignment(String wsScheme) {
        String uri = "vertx-websocket:" + wsScheme + "localhost/test";
        VertxWebsocketEndpoint endpoint = context.getEndpoint(uri, VertxWebsocketEndpoint.class);
        WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());

        int expectedPort = wsScheme.startsWith("wss") ? DEFAULT_VERTX_CLIENT_WSS_PORT : DEFAULT_VERTX_CLIENT_WS_PORT;
        assertEquals(expectedPort, connectOptions.getPort());
    }
}
