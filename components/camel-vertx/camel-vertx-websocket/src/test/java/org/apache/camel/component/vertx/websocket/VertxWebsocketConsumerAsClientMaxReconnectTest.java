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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxWebsocketConsumerAsClientMaxReconnectTest extends VertxWebSocketTestSupport {
    @Test
    void testMaxReconnect() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World");

        String uri = String.format("vertx-websocket:localhost:%d/echo", port);
        template.sendBody(uri, "Hello World");
        mockEndpoint.assertIsSatisfied();

        // Stop server
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(0);

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

        // Wait for client consumer reconnect max attempts to be exhausted
        Thread.sleep(300);

        // Restart server
        context.getRouteController().startRoute("server");

        // Verify that the client consumer gave up reconnecting
        template.sendBody(uri, "Hello World Again");
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/echo", port).routeId("server")
                        .log("Server consumer: Received message: ${body}")
                        .toF("vertx-websocket:localhost:%d/echo?sendToAll=true", port);

                fromF("vertx-websocket:localhost:%d/echo?consumeAsClient=true&reconnectInterval=10&maxReconnectAttempts=1",
                        port)
                        .log("Client consumer 1: Received message: ${body}")
                        .to("mock:result");
            }
        };
    }
}
