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

import java.nio.charset.StandardCharsets;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class VertxWebsocketConsumerAsClientBinaryMessageTest extends VertxWebSocketTestSupport {

    @Test
    void testConsumeAsClientBinaryMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(5);

        String uri = String.format("vertx-websocket:localhost:%d/echo", port);
        for (int i = 1; i <= 5; i++) {
            template.sendBody(uri, "Hello World".getBytes(StandardCharsets.UTF_8));
        }

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/echo", port)
                        .log("Server consumer received message: ${body}")
                        .toF("vertx-websocket:localhost:%d/echo?sendToAll=true", port);

                fromF("vertx-websocket:localhost:%d/echo?consumeAsClient=true", port)
                        .log("Client consumer received message: ${body}")
                        .to("mock:result");
            }
        };
    }
}
