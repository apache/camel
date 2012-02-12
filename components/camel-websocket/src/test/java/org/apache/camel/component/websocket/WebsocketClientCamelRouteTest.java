/**
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

import java.net.URI;

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.CamelTestSupport;
import org.junit.Test;

public class WebsocketClientCamelRouteTest extends CamelTestSupport {

    @Test
    public void testWSHttpCall() throws Exception {
        WebSocketConnection webSocketConnection = new WebSocketConnection(new URI("ws://127.0.0.1:9292/test"));

        // Register Event Handlers
        webSocketConnection.setEventHandler(new WebSocketEventHandler() {
            public void onOpen() {
                log.info("--open");
            }

            public void onMessage(WebSocketMessage message) {
                log.info("--received message: " + message.getText());
            }

            public void onClose() {
                log.info("--close");
            }
        });

        // Establish WebSocket Connection
        webSocketConnection.connect();
        log.info(">>> Connection established.");

        // Send Data
        webSocketConnection.send("Hello from WS Client");

        // Close WebSocket Connection
        webSocketConnection.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("websocket://test")
                    .log(">>> Message received from WebSocket Client : ${body}")
                    .loop(10)
                        .setBody().constant(">> Welcome on board!")
                        .to("websocket://test");
            }
        };
    }
}
