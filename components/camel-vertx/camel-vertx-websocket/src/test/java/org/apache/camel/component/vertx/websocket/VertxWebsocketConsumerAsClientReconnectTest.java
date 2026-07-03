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

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.WebSocket;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

public class VertxWebsocketConsumerAsClientReconnectTest extends VertxWebSocketTestSupport {
    @Test
    void testReconnect() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World");

        String uri = String.format("vertx-websocket:localhost:%d/echo", port.getPort());
        template.sendBody(uri, "Hello World");
        mockEndpoint.assertIsSatisfied();

        // Stop server
        mockEndpoint.reset();
        mockEndpoint.expectedBodiesReceived("Hello World Again");

        context.getRouteController().stopRoute("server");

        // Verify that we cannot send messages
        Exchange exchange = template.send(uri, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setBody("Hello World Again");
            }
        });
        Exception exception = exchange.getException();
        Assertions.assertNotNull(exception);
        Assertions.assertInstanceOf(ConnectException.class, exception.getCause());

        // Restart server
        context.getRouteController().startRoute("server");

        // Wait for client consumer to reconnect and verify it can receive messages.
        // After the server is stopped, the client consumer's close handler fires
        // asynchronously on the Vert.x event loop. Once it fires, the reconnect
        // timer starts and will connect to the restarted server.
        // Use a fresh WebSocket connection to send messages (bypassing the Camel
        // producer endpoint's stale cached WebSocket from the pre-stop send).
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    mockEndpoint.reset();
                    mockEndpoint.expectedBodiesReceived("Hello World Again");
                    WebSocket ws = openWebSocketConnection("localhost", port.getPort(), "/echo", msg -> {
                    });
                    try {
                        ws.writeTextMessage("Hello World Again");
                        mockEndpoint.assertIsSatisfied(500);
                    } finally {
                        ws.close();
                    }
                });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/echo", port.getPort()).routeId("server")
                        .log("Server consumer: Received message: ${body}")
                        .toF("vertx-websocket:localhost:%d/echo?sendToAll=true", port.getPort());

                fromF("vertx-websocket:localhost:%d/echo?consumeAsClient=true&reconnectInterval=10", port.getPort())
                        .log("Client consumer 1: Received message: ${body}")
                        .to("mock:result");
            }
        };
    }
}
