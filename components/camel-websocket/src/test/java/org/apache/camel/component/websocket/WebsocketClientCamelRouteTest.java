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
import java.net.URISyntaxException;

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.CamelTestSupport;
import org.junit.Test;

public class WebsocketClientCamelRouteTest extends CamelTestSupport {

    private static URI uriWS;
    private static WebSocketConnection webSocketConnection;

    @Test
    public void testWSHttpCall() throws WebSocketException {

        try {
            uriWS = new URI("ws://127.0.0.1:9292/test");
            WebSocketConnection webSocketConnection = new WebSocketConnection(uriWS);

            // Register Event Handlers
            webSocketConnection.setEventHandler(new WebSocketEventHandler() {
                public void onOpen() {
                    System.out.println("--open");
                }

                public void onMessage(WebSocketMessage message) {
                    System.out.println("--received message: " + message.getText());
                }

                public void onClose() {
                    System.out.println("--close");
                }
            });

            // Establish WebSocket Connection
            webSocketConnection.connect();
            System.out.println(">>> Connection established.");

            // Send Data
            webSocketConnection.send("Hello from WS Client");


        } catch (WebSocketException ex) {
            ex.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }


    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("websocket://test")
                    .log(">>> Message received from WebSocket Client : ${body}")
                    .loop(10)
                        .setBody().constant(">> Welcome on board !")
                        .to("websocket://test");

            }
        };
    }


}
