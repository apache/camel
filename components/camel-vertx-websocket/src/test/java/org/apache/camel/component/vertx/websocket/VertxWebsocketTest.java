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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebsocketTest extends VertxWebSocketTestSupport {

    @Test
    public void testTextMessage() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");

        template.sendBody("direct:start", "world");

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testBinaryMessage() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");

        template.sendBody("direct:start", "world".getBytes(StandardCharsets.UTF_8));

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testStreamMessage() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");

        InputStream stream = new ByteArrayInputStream("world".getBytes(StandardCharsets.UTF_8));

        template.sendBody("direct:start", stream);

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testNullMessage() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");
        mockEndpoint.setResultWaitTime(500);

        template.sendBody("direct:start", null);

        // Since the message body is null, the WebSocket producer will not send payload to the WS endpoint
        mockEndpoint.assertIsNotSatisfied();
    }

    @Test
    public void testSendWithConnectionKey() throws Exception {
        int expectedResultCount = 1;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            openWebSocketConnection("localhost", port, "/test", message -> {
                synchronized (latch) {
                    results.add(message);
                    latch.countDown();
                }
            });
        }

        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost:" + port + "/test", VertxWebsocketEndpoint.class);
        Map<String, ServerWebSocket> connectedPeers = endpoint.findPeersForHostPort();
        assertEquals(2, connectedPeers.size());

        String connectionKey = connectedPeers.keySet().iterator().next();

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test", "Hello World",
                VertxWebsocketContants.CONNECTION_KEY, connectionKey);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        assertTrue(results.contains("Hello World"));
    }

    @Test
    public void testSendWithInvalidConnectionKey() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");
        mockEndpoint.setResultWaitTime(500);

        template.sendBodyAndHeader("direct:start", "Hello World", VertxWebsocketContants.CONNECTION_KEY, "invalid-key");

        // Since the message body is null, the WebSocket producer will not send payload to the WS endpoint
        mockEndpoint.assertIsNotSatisfied();
    }

    @Test
    public void testSendWithMultipleConnectionKeys() throws Exception {
        int expectedResultCount = 3;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            openWebSocketConnection("localhost", port, "/test", message -> {
                synchronized (latch) {
                    results.add(message);
                    latch.countDown();
                }
            });
        }

        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost:" + port + "/test", VertxWebsocketEndpoint.class);
        Map<String, ServerWebSocket> connectedPeers = endpoint.findPeersForHostPort();
        assertEquals(5, connectedPeers.size());

        StringJoiner joiner = new StringJoiner(",");
        Iterator<String> iterator = connectedPeers.keySet().iterator();
        for (int i = 0; i < 3; i++) {
            joiner.add(iterator.next());
        }

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test", "Hello World",
                VertxWebsocketContants.CONNECTION_KEY, joiner.toString());

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        results.forEach(result -> assertEquals("Hello World", result));
    }

    @Test
    public void testSendToAll() throws Exception {
        int expectedResultCount = 5;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < expectedResultCount; i++) {
            openWebSocketConnection("localhost", port, "/test", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });
        }

        template.sendBody("vertx-websocket:localhost:" + port + "/test?sendToAll=true", "Hello World");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }
    }

    @Test
    public void testSendToAllWithHeader() throws Exception {
        int expectedResultCount = 5;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < expectedResultCount; i++) {
            openWebSocketConnection("localhost", port, "/test", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });
        }

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test", "Hello World",
                VertxWebsocketContants.SEND_TO_ALL, true);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }
    }

    @Test
    public void testEchoRoute() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = new ArrayList<>();

        VertxWebsocketComponent component = context.getComponent("vertx-websocket", VertxWebsocketComponent.class);
        Map<VertxWebsocketHostKey, VertxWebsocketHost> registry = component.getVerxHostRegistry();
        VertxWebsocketHost host = registry.values()
                .stream()
                .filter(wsHost -> wsHost.getPort() != port)
                .findFirst()
                .get();

        WebSocket webSocket = openWebSocketConnection("localhost", host.getPort(), "/greeting", message -> {
            synchronized (latch) {
                results.add(message);
                latch.countDown();
            }
        });

        webSocket.writeTextMessage("Camel");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(1, results.size());
        assertEquals("Hello Camel", results.get(0));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/test", port);

                fromF("vertx-websocket:localhost:%d/test", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");

                from("vertx-websocket://greeting")
                        .setBody(simple("Hello ${body}"))
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                int serverPort = getVertxServerRandomPort();
                                exchange.getMessage().setHeader("port", serverPort);
                            }
                        })
                        .toD("vertx-websocket:localhost:${header.port}/greeting");
            }
        };
    }
}
