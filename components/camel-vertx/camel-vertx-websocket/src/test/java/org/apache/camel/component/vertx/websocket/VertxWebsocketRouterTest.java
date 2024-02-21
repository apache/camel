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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.ext.web.Router;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebsocketRouterTest extends VertxWebSocketTestSupport {

    @Test
    public void testCustomRouter() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test?router=#customRouter", port)
                        .to("mock:result");
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        Router router = createRouter("/custom", latch);

        context.getRegistry().bind("customRouter", router);
        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello world");

            // Verify the WebSocket consumer we configured in the camel route
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("vertx-websocket:localhost:" + port + "/test", "Hello world");
            mockEndpoint.assertIsSatisfied();

            // Verify the WebSocket route manually added to the vertx router
            String result = template.requestBody("vertx-websocket:localhost:" + port + "/custom", "Hello world", String.class);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals("Hello world", result);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomRouterSetOnComponent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Router router = createRouter("/custom", latch);

        VertxWebsocketComponent component = new VertxWebsocketComponent();
        component.setRouter(router);

        CamelContext context = new DefaultCamelContext();
        context.addComponent("vertx-websocket", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test", port)
                        .to("mock:result");
            }
        });

        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello world");

            // Verify the WebSocket consumer we configured in the camel route
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("vertx-websocket:localhost:" + port + "/test", "Hello world");
            mockEndpoint.assertIsSatisfied();

            // Verify the WebSocket route manually added to the vertx router
            String result = template.requestBody("vertx-websocket:localhost:" + port + "/custom", "Hello world", String.class);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals("Hello world", result);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomRouterEndpointConfigurationTakesPrecedence() throws Exception {
        CountDownLatch latchForComponent = new CountDownLatch(1);
        Router routerForComponent = createRouter("/component", latchForComponent);

        VertxWebsocketComponent component = new VertxWebsocketComponent();
        component.setRouter(routerForComponent);

        CamelContext context = new DefaultCamelContext();
        context.addComponent("vertx-websocket", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test?router=#customRouter", port)
                        .to("mock:result");
            }
        });

        CountDownLatch latchForEndpoint = new CountDownLatch(1);
        Router routerForEndpoint = createRouter("/endpoint", latchForEndpoint);

        context.getRegistry().bind("customRouter", routerForEndpoint);

        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello world");

            // Verify the WebSocket consumer we configured in the camel route
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("vertx-websocket:localhost:" + port + "/test", "Hello world");
            mockEndpoint.assertIsSatisfied();

            // Verify the WebSocket route manually added to the vertx router was the one configured on the endpoint
            String result
                    = template.requestBody("vertx-websocket:localhost:" + port + "/endpoint", "Hello world", String.class);
            assertTrue(latchForEndpoint.await(10, TimeUnit.SECONDS));
            assertEquals("Hello world", result);

            // Verify the WebSocket route manually added to the component fails since the endpoint configured one takes precedence
            assertThrows(CamelExecutionException.class, () -> template
                    .requestBody("vertx-websocket:localhost:" + port + "/component", "Hello world", String.class));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomRouterFallbackFromRegistry() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test", port)
                        .to("mock:result");
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        Router router = createRouter("/custom", latch);

        context.getRegistry().bind("vertx-router", router);
        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello world");

            // Verify the WebSocket consumer we configured in the camel route
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("vertx-websocket:localhost:" + port + "/test", "Hello world");
            mockEndpoint.assertIsSatisfied();

            // Verify the WebSocket route manually added to the vertx router
            String result = template.requestBody("vertx-websocket:localhost:" + port + "/custom", "Hello world", String.class);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals("Hello world", result);
        } finally {
            context.stop();
        }
    }

    @Override
    protected void startCamelContext() {
    }
}
