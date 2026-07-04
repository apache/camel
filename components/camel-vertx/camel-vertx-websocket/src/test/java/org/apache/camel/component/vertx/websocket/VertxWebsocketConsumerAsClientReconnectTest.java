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

        // Verify that the server is fully down by waiting until sends fail.
        // The producer endpoint's cached WebSocket may still appear open briefly
        // after stopRoute returns, until the Vert.x event loop processes the
        // TCP close frame and isClosed() starts returning true.
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Exchange exchange = template.send(uri, new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getMessage().setBody("Hello World Again");
                        }
                    });
                    Assertions.assertNotNull(exchange.getException());
                    Assertions.assertInstanceOf(ConnectException.class, exchange.getException().getCause());
                });

        // Restart server
        context.getRouteController().startRoute("server");

        // Wait for client consumer to reconnect and verify end-to-end message flow.
        // After the server stops, the client consumer's close handler fires
        // asynchronously on the Vert.x event loop, starting the reconnect timer
        // that connects to the restarted server.
        // Use ignoreExceptions() to retry through transient failures while both
        // the producer and client consumer re-establish their connections.
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    mockEndpoint.reset();
                    mockEndpoint.expectedBodiesReceived("Hello World Again");
                    mockEndpoint.setResultWaitTime(500);
                    template.sendBody(uri, "Hello World Again");
                    mockEndpoint.assertIsSatisfied();
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
