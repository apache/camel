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

import io.vertx.core.Vertx;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class VertxWebSocketSlowClientConsumerTest extends VertxWebSocketTestSupport {
    private static final String MESSAGE_BODY = "Hello World";
    private final BlockedThreadReporter reporter = new BlockedThreadReporter();

    @AfterEach
    public void afterEach() {
        reporter.reset();
    }

    @BindToRegistry
    public Vertx createVertx() {
        return createVertxWithThreadBlockedHandler(reporter);
    }

    @Test
    void slowClientConsumerDoesNotBlockEventLoop() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:clientConsumerResult");
        mockEndpoint.expectedBodiesReceived(MESSAGE_BODY);

        template.sendBody("vertx-websocket:localhost:" + port + "/echo/slow", MESSAGE_BODY);

        mockEndpoint.assertIsSatisfied();
        assertFalse(reporter.isEventLoopBlocked(), "Expected Vert.x event loop to not be blocked");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("vertx-websocket:localhost:%d/echo/slow", port)
                        .toF("vertx-websocket:localhost:%d/echo/slow?sendToAll=true", port);

                fromF("vertx-websocket:localhost:%d/echo/slow?consumeAsClient=true", port)
                        .delay(600).syncDelayed()
                        .to("mock:clientConsumerResult");
            }
        };
    }
}
