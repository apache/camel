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
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.http.WebSocket;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                VertxWebsocketConstants.CONNECTION_KEY, connectionKey);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        assertTrue(results.contains("Hello World"));
    }

    @Test
    void testSendWithConnectionKeyForParameterizedPath() throws Exception {
        int expectedResultCount = 1;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            openWebSocketConnection("localhost", port, "/test/paramA/other/paramB", message -> {
                synchronized (latch) {
                    results.add(message);
                    latch.countDown();
                }
            });
        }

        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost:" + port + "/test/paramA/other/paramB",
                        VertxWebsocketEndpoint.class);
        Map<String, ServerWebSocket> connectedPeers = endpoint.findPeersForHostPort();
        assertEquals(2, connectedPeers.size());

        String connectionKey = connectedPeers.keySet().iterator().next();

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test/paramA/other/paramB",
                "Hello World",
                VertxWebsocketConstants.CONNECTION_KEY, connectionKey);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        assertTrue(results.contains("Hello World"));
    }

    @Test
    void testSendWithConnectionKeyForRawParameterizedPath() throws Exception {
        int expectedResultCount = 1;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            openWebSocketConnection("localhost", port, "/test/paramA/other/paramB", message -> {
                synchronized (latch) {
                    results.add(message);
                    latch.countDown();
                }
            });
        }

        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost:" + port + "/test/paramA/other/paramB",
                        VertxWebsocketEndpoint.class);
        Map<String, ServerWebSocket> connectedPeers = endpoint.findPeersForHostPort();
        assertEquals(2, connectedPeers.size());

        String connectionKey = connectedPeers.keySet().iterator().next();

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test/{paramA}/other/{paramB}",
                "Hello World",
                VertxWebsocketConstants.CONNECTION_KEY, connectionKey);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        assertTrue(results.contains("Hello World"));
    }

    @Test
    void testSendWithConnectionKeyForWildcardPath() throws Exception {
        int expectedResultCount = 1;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            openWebSocketConnection("localhost", port, "/test/wildcarded/path", message -> {
                synchronized (latch) {
                    results.add(message);
                    latch.countDown();
                }
            });
        }

        openWebSocketConnection("localhost", port, "/test/wildcarded/otherpath", message -> {
            synchronized (latch) {
                results.add(message);
                latch.countDown();
            }
        });

        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost:" + port + "/test/wildcarded/path",
                        VertxWebsocketEndpoint.class);
        Map<String, ServerWebSocket> connectedPeers = endpoint.findPeersForHostPort();
        assertEquals(2, connectedPeers.size());

        String connectionKey = connectedPeers.keySet().iterator().next();

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test/wildcarded/path", "Hello World",
                VertxWebsocketConstants.CONNECTION_KEY, connectionKey);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        assertTrue(results.contains("Hello World"));
    }

    @Test
    void testSendWithConnectionKeyForRawPath() throws Exception {
        int expectedResultCount = 1;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            openWebSocketConnection("localhost", port, "/test/wildcarded/path", message -> {
                synchronized (latch) {
                    results.add(message);
                    latch.countDown();
                }
            });
        }

        openWebSocketConnection("localhost", port, "/test/wildcarded/otherpath", message -> {
            synchronized (latch) {
                results.add(message);
                latch.countDown();
            }
        });

        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost:" + port + "/test/wildcarded/path",
                        VertxWebsocketEndpoint.class);
        Map<String, ServerWebSocket> connectedPeers = endpoint.findPeersForHostPort();
        assertEquals(2, connectedPeers.size());

        String connectionKey = connectedPeers.keySet().iterator().next();

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test/wildcarded/*", "Hello World",
                VertxWebsocketConstants.CONNECTION_KEY, connectionKey);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        assertTrue(results.contains("Hello World"));
    }

    @Test
    void testSendWithInvalidConnectionKey() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");
        mockEndpoint.setResultWaitTime(500);

        template.sendBodyAndHeader("direct:start", "Hello World", VertxWebsocketConstants.CONNECTION_KEY, "invalid-key");

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
                VertxWebsocketConstants.CONNECTION_KEY, joiner.toString());

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());
        results.forEach(result -> assertEquals("Hello World", result));
    }

    @Test
    public void testSendToAll() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(0);

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

        // Open a connection on path /test on another port to ensure the 'send to all' operation
        // only targeted peers connected on path /test
        openWebSocketConnection("localhost", port2, "/test", message -> {
            results.add("/test on port " + port2 + " should not have been called");
        });

        // Open a connection on path /test-other to ensure the 'send to all' operation
        // only targeted peers connected on path /test
        openWebSocketConnection("localhost", port, "/test-other", message -> {
            results.add("/test-other should not have been called");
        });

        template.sendBody("vertx-websocket:localhost:" + port + "/test?sendToAll=true", "Hello World");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }

        mockEndpoint.assertIsSatisfied(TimeUnit.SECONDS.toMillis(1));
    }

    @Test
    public void testSendToAllWithHeader() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(0);

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

        // Open a connection on path /test on another port to ensure the 'send to all' operation
        // only targeted peers connected on path /test
        openWebSocketConnection("localhost", port2, "/test", message -> {
            results.add("/test on port " + port2 + " should not have been called");
        });

        // Open a connection on path /test-other to ensure the 'send to all' operation
        // only targeted peers connected on path /test
        openWebSocketConnection("localhost", port, "/test-other", message -> {
            results.add("/test-other should not have been called");
        });

        template.sendBodyAndHeader("vertx-websocket:localhost:" + port + "/test", "Hello World",
                VertxWebsocketConstants.SEND_TO_ALL, true);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }

        mockEndpoint.assertIsSatisfied(TimeUnit.SECONDS.toMillis(1));
    }

    @Test
    void testSendToAllForExactParameterizedPath() throws Exception {
        int expectedResultCount = 5;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < expectedResultCount; i++) {
            openWebSocketConnection("localhost", port, "/test/firstParam/other/secondParam", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });

            // Below we produce to an explicit path so this peer should be ignored
            openWebSocketConnection("localhost", port, "/test/otherFirstParam/other/otherSecondParam", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });
        }

        template.sendBody("vertx-websocket:localhost:" + port + "/test/firstParam/other/secondParam?sendToAll=true",
                "Hello World");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }
    }

    @Test
    void testSendToAllForRawParameterizedPath() throws Exception {
        int expectedResultCount = 10;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            openWebSocketConnection("localhost", port, "/test/firstParam/other/secondParam", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });

            openWebSocketConnection("localhost", port, "/test/otherFirstParam/other/otherSecondParam", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });
        }

        template.sendBody("vertx-websocket:localhost:" + port + "/test/{paramA}/other/{paramB}?sendToAll=true", "Hello World");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }
    }

    @Test
    void testSendToAllForWildcardPath() throws Exception {
        int expectedResultCount = 5;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < expectedResultCount; i++) {
            openWebSocketConnection("localhost", port, "/test/wildcarded/path", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });

            // Below we produce to an explicit path so this peer should be ignored
            openWebSocketConnection("localhost", port, "/test/wildcarded/other", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });
        }

        template.sendBody("vertx-websocket:localhost:" + port + "/test/wildcarded/path?sendToAll=true", "Hello World");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(expectedResultCount, results.size());

        for (int i = 1; i <= expectedResultCount; i++) {
            assertTrue(results.contains("Hello World " + i));
        }
    }

    @Test
    void testSendToAllForRawWildcardPath() throws Exception {
        int expectedResultCount = 10;
        CountDownLatch latch = new CountDownLatch(expectedResultCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            openWebSocketConnection("localhost", port, "/test/wildcarded/path", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });

            openWebSocketConnection("localhost", port, "/test/wildcarded/other", message -> {
                synchronized (latch) {
                    results.add(message + " " + latch.getCount());
                    latch.countDown();
                }
            });
        }

        template.sendBody("vertx-websocket:localhost:" + port + "/test/wildcarded/*?sendToAll=true", "Hello World");

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
        int wsPort = getVertxServerRandomPort();

        WebSocket webSocket = openWebSocketConnection("localhost", wsPort, "/greeting", message -> {
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

    @Test
    void echoRouteWithPathParams() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = new ArrayList<>();

        WebSocket webSocket = openWebSocketConnection("localhost", port, "/testA/echo/testB", message -> {
            synchronized (latch) {
                results.add(message);
                latch.countDown();
            }
        });
        webSocket.writeTextMessage("Hello");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(1, results.size());
        assertEquals("Hello testA testB", results.get(0));
    }

    @Test
    void echoRouteWithWildcardPath() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = new ArrayList<>();

        WebSocket webSocket = openWebSocketConnection("localhost", port, "/wildcard/echo/foo/bar", message -> {
            synchronized (latch) {
                results.add(message);
                latch.countDown();
            }
        });
        webSocket.writeTextMessage("Hello");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(1, results.size());
        assertEquals("Hello World", results.get(0));
    }

    @Test
    void testWsSchemeUriPrefix() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World 1", "Hello World 2", "Hello World 3");
        template.sendBody("vertx-websocket:ws:localhost:" + port + "/test", "World 1");
        template.sendBody("vertx-websocket:ws:/localhost:" + port + "/test", "World 2");
        template.sendBody("vertx-websocket:ws://localhost:" + port + "/test", "World 3");
        mockEndpoint.assertIsSatisfied(5000);
    }

    @Test
    void testPathParams() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:pathParamResult");
        mockEndpoint.expectedHeaderReceived("firstParam", "Hello");
        mockEndpoint.expectedHeaderReceived("secondParam", "World");
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.sendBody("vertx-websocket:localhost:" + port + "/path/params/Hello/World", "null");

        mockEndpoint.assertIsSatisfied(5000);
    }

    @Test
    void testQueryParams() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:queryParamResult");
        mockEndpoint.expectedHeaderReceived("firstParam", "Hello");
        mockEndpoint.expectedHeaderReceived("secondParam", "World");
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.sendBody("vertx-websocket:localhost:" + port + "/query/params?firstParam=Hello&secondParam=World", "null");

        mockEndpoint.assertIsSatisfied(5000);
    }

    @Test
    void testOverlappingPathAndQueryParams() throws InterruptedException {
        List<String> expectedFirstParams = new ArrayList<>();
        expectedFirstParams.add("Goodbye");
        expectedFirstParams.add("Hello");

        MockEndpoint mockEndpoint = getMockEndpoint("mock:pathParamResult");
        mockEndpoint.expectedHeaderReceived("firstParam", expectedFirstParams);
        mockEndpoint.expectedHeaderReceived("secondParam", "World");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("vertx-websocket:localhost:" + port + "/path/params/Hello/World?firstParam=Goodbye", "null");

        mockEndpoint.assertIsSatisfied(5000);
    }

    @Test
    void defaultPath() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:defaultPath");
        mockEndpoint.expectedBodiesReceived("Hello World from the default path");

        template.sendBody("vertx-websocket:localhost:" + port, "World");

        mockEndpoint.assertIsSatisfied(5000);
    }

    @Test
    void wildcardPath() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:wildcardPath");
        mockEndpoint.expectedBodiesReceived("Hello World from the wildcard path");

        template.sendBody("vertx-websocket:localhost:" + port + "/wild/card/foo/bar", "World");

        mockEndpoint.assertIsSatisfied(5000);
    }

    @Test
    void nonManagedPathReturns404() {
        Exchange exchange = template.request("vertx-websocket:localhost:" + port + "/invalid", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setBody("Test");
            }
        });

        Exception exception = exchange.getException();
        assertNotNull(exception);

        Throwable cause = exception.getCause();
        assertNotNull(exception);
        assertInstanceOf(UpgradeRejectedException.class, cause);

        assertEquals(404, ((UpgradeRejectedException) cause).getStatus());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/test", port);

                fromF("vertx-websocket:localhost:%d/test", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");

                fromF("vertx-websocket:localhost:%d/test", port2)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");

                fromF("vertx-websocket:localhost:%d/test-other", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");

                fromF("vertx-websocket:localhost:%d/path/params/{firstParam}/{secondParam}", port)
                        .setBody(simple("${header.firstParam} ${header.secondParam}"))
                        .to("mock:pathParamResult");

                fromF("vertx-websocket:localhost:%d/{firstParam}/echo/{secondParam}", port)
                        .setBody(simple("${body} ${header.firstParam} ${header.secondParam}"))
                        .toF("vertx-websocket:localhost:%d/testA/echo/testB", port);

                fromF("vertx-websocket:localhost:%d/test/{paramA}/other/{paramB}", port)
                        .setBody(simple("${header.firstParam} ${header.secondParam}"));

                fromF("vertx-websocket:localhost:%d/query/params", port)
                        .setBody(simple("${header.firstParam} ${header.secondParam}"))
                        .to("mock:queryParamResult");

                fromF("vertx-websocket:localhost:0/greeting")
                        .setBody(simple("Hello ${body}"))
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                int serverPort = getVertxServerRandomPort();
                                exchange.getMessage().setHeader("port", serverPort);
                            }
                        })
                        .toD("vertx-websocket:localhost:${header.port}/greeting");

                fromF("vertx-websocket:localhost:%d", port)
                        .setBody().simple("Hello ${body} from the default path")
                        .to("mock:defaultPath");

                fromF("vertx-websocket:localhost:%d/wild/card*", port)
                        .setBody().simple("Hello ${body} from the wildcard path")
                        .to("mock:wildcardPath");

                fromF("vertx-websocket:localhost:%d/wildcard/echo*", port)
                        .setBody().simple("${body} World")
                        .toF("vertx-websocket:localhost:%d/wildcard/echo/foo/bar", port);

                fromF("vertx-websocket:localhost:%d/test/wildcarded*", port)
                        .setBody().simple("Hello ${body} from the wildcard path");
            }
        };
    }
}
