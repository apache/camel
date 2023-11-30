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
package org.apache.camel.component.undertow.ws;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowConstants;
import org.apache.camel.component.undertow.UndertowConstants.EventType;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowWsConsumerRouteTest extends BaseUndertowTest {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowWsConsumerRouteTest.class);

    private static final String CONNECTED_PREFIX = "connected ";
    private static final String BROADCAST_MESSAGE_PREFIX = "broadcast ";

    @Test
    public void wsClientSingleText() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/app1");
        testClient.connect();

        MockEndpoint result = getMockEndpoint("mock:result1");
        result.expectedBodiesReceived("Test");

        testClient.sendTextMessage("Test");

        result.await(60, TimeUnit.SECONDS);
        result.assertIsSatisfied();

        testClient.close();
    }

    @Test
    public void wsClientSingleTextStreaming() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/app2");
        testClient.connect();

        MockEndpoint result = getMockEndpoint("mock:result2");
        result.expectedMessageCount(1);

        testClient.sendTextMessage("Test");

        result.await(60, TimeUnit.SECONDS);
        List<Exchange> exchanges = result.getReceivedExchanges();
        assertEquals(1, exchanges.size());
        Exchange exchange = result.getReceivedExchanges().get(0);
        assertNotNull(exchange.getIn().getHeader(UndertowConstants.CHANNEL));
        Object body = exchange.getIn().getBody();
        assertTrue(body instanceof Reader, "body is " + body.getClass().getName());
        Reader r = (Reader) body;
        assertEquals("Test", IOConverter.toString(r));

        testClient.close();
    }

    @Test
    public void wsClientSingleBytes() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/app1");
        testClient.connect();

        MockEndpoint result = getMockEndpoint("mock:result1");
        final byte[] testmessage = "Test".getBytes(StandardCharsets.UTF_8);
        result.expectedBodiesReceived(testmessage);

        testClient.sendBytesMessage(testmessage);

        result.assertIsSatisfied();

        testClient.close();
    }

    @Test
    public void wsClientSingleBytesStreaming() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/app2");
        testClient.connect();

        MockEndpoint result = getMockEndpoint("mock:result2");
        result.expectedMessageCount(1);

        final byte[] testmessage = "Test".getBytes(StandardCharsets.UTF_8);
        testClient.sendBytesMessage(testmessage);

        result.await(60, TimeUnit.SECONDS);
        List<Exchange> exchanges = result.getReceivedExchanges();
        assertEquals(1, exchanges.size());
        Exchange exchange = result.getReceivedExchanges().get(0);
        assertNotNull(exchange.getIn().getHeader(UndertowConstants.CHANNEL));
        Object body = exchange.getIn().getBody();
        assertTrue(body instanceof InputStream, "body is " + body.getClass().getName());
        InputStream in = (InputStream) body;
        assertArrayEquals(testmessage, IOConverter.toBytes(in));

        testClient.close();
    }

    @Test
    public void wsClientMultipleText() throws Exception {
        WebsocketTestClient testClient1 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app1");
        testClient1.connect();

        WebsocketTestClient testClient2 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app1");
        testClient2.connect();

        MockEndpoint result = getMockEndpoint("mock:result1");
        result.expectedMessageCount(2);

        testClient1.sendTextMessage("Test1");
        testClient2.sendTextMessage("Test2");

        result.await(60, TimeUnit.SECONDS);
        result.assertIsSatisfied();
        List<Exchange> exchanges = result.getReceivedExchanges();
        Set<String> actual = new HashSet<>();
        actual.add(exchanges.get(0).getIn().getBody(String.class));
        actual.add(exchanges.get(1).getIn().getBody(String.class));
        assertEquals(new HashSet<>(Arrays.asList("Test1", "Test2")), actual);

        testClient1.close();
        testClient2.close();
    }

    @DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
    @Test
    public void echo() throws Exception {
        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app3", 2);
        wsclient1.connect();

        wsclient1.sendTextMessage("Test1");
        wsclient1.sendTextMessage("Test2");

        assertTrue(wsclient1.await(10));

        assertEquals(Arrays.asList("Test1", "Test2"), wsclient1.getReceived(String.class));

        wsclient1.close();
    }

    @Test
    public void echoMulti() throws Exception {
        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app3", 1);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app3", 1);
        wsclient1.connect();
        wsclient2.connect();

        wsclient1.sendTextMessage("Gambas");
        wsclient2.sendTextMessage("Calamares");

        assertTrue(wsclient1.await(10));
        assertTrue(wsclient2.await(10));

        assertEquals(List.of("Gambas"), wsclient1.getReceived(String.class));
        assertEquals(List.of("Calamares"), wsclient2.getReceived(String.class));

        wsclient1.close();
        wsclient2.close();
    }

    @Test
    public void sendToAll() throws Exception {
        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app4", 2);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app4", 2);
        wsclient1.connect();
        wsclient2.connect();

        wsclient1.sendTextMessage("Gambas");
        wsclient2.sendTextMessage("Calamares");

        assertTrue(wsclient1.await(10));
        assertTrue(wsclient2.await(10));

        List<String> received1 = wsclient1.getReceived(String.class);
        assertEquals(2, received1.size());

        assertTrue(received1.contains("Gambas"));
        assertTrue(received1.contains("Calamares"));

        List<String> received2 = wsclient2.getReceived(String.class);
        assertEquals(2, received2.size());
        assertTrue(received2.contains("Gambas"));
        assertTrue(received2.contains("Calamares"));

        wsclient1.close();
        wsclient2.close();
    }

    @Test
    public void fireWebSocketChannelEvents() throws Exception {

        MockEndpoint result = getMockEndpoint("mock:result5");
        result.expectedMessageCount(6);

        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app5", 2);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app5", 2);
        wsclient1.connect();
        wsclient2.connect();

        wsclient1.sendTextMessage("Gambas");
        wsclient2.sendTextMessage("Calamares");

        wsclient1.close();
        wsclient2.close();

        result.await(60, TimeUnit.SECONDS);

        final List<Exchange> exchanges = result.getReceivedExchanges();
        final Map<String, List<String>> connections = new HashMap<>();
        for (Exchange exchange : exchanges) {
            final Message in = exchange.getIn();
            final String key = (String) in.getHeader(UndertowConstants.CONNECTION_KEY);
            assertNotNull(key);
            final WebSocketChannel channel = in.getHeader(UndertowConstants.CHANNEL, WebSocketChannel.class);
            assertNotNull(channel);
            if (in.getHeader(UndertowConstants.EVENT_TYPE_ENUM, EventType.class) == EventType.ONOPEN) {
                final WebSocketHttpExchange transportExchange
                        = in.getHeader(UndertowConstants.EXCHANGE, WebSocketHttpExchange.class);
                assertNotNull(transportExchange);
            }
            List<String> messages = connections.get(key);
            if (messages == null) {
                messages = new ArrayList<>();
                connections.put(key, messages);
            }
            String body = in.getBody(String.class);
            if (body != null) {
                messages.add(body);
            } else {
                messages.add(in.getHeader(UndertowConstants.EVENT_TYPE_ENUM, EventType.class).name());
            }
        }

        final List<String> expected1 = Arrays.asList(EventType.ONOPEN.name(), "Gambas", EventType.ONCLOSE.name());
        final List<String> expected2 = Arrays.asList(EventType.ONOPEN.name(), "Calamares", EventType.ONCLOSE.name());

        assertEquals(2, connections.size());
        final Iterator<List<String>> it = connections.values().iterator();
        final List<String> actual1 = it.next();
        assertTrue(actual1.equals(expected1) || actual1.equals(expected2), "actual " + actual1);
        final List<String> actual2 = it.next();
        assertTrue(actual2.equals(expected1) || actual2.equals(expected2), "actual " + actual2);

    }

    @Test
    public void connectionKeyList() throws Exception {

        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app6", 1);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app6", 1);
        WebsocketTestClient wsclient3 = new WebsocketTestClient("ws://localhost:" + getPort() + "/app6", 1);
        wsclient1.connect();
        wsclient2.connect();
        wsclient3.connect();

        wsclient1.await(10);
        final String connectionKey1 = assertConnected(wsclient1);
        assertNotNull(connectionKey1);
        wsclient2.await(10);
        final String connectionKey2 = assertConnected(wsclient2);
        wsclient3.await(10);
        final String connectionKey3 = assertConnected(wsclient3);

        wsclient1.reset(1);
        wsclient2.reset(1);
        wsclient3.reset(1);
        final String broadcastMsg = BROADCAST_MESSAGE_PREFIX + connectionKey2 + " " + connectionKey3;
        wsclient1.sendTextMessage(broadcastMsg); // this one should go to wsclient2 and wsclient3
        wsclient1.sendTextMessage("private"); // this one should go to wsclient1 only

        wsclient2.await(10);
        assertEquals(broadcastMsg, wsclient2.getReceived(String.class).get(0));
        wsclient3.await(10);
        assertEquals(broadcastMsg, wsclient3.getReceived(String.class).get(0));
        wsclient1.await(10);
        assertEquals("private", wsclient1.getReceived(String.class).get(0));

        wsclient1.close();
        wsclient2.close();
        wsclient3.close();

    }

    private String assertConnected(WebsocketTestClient wsclient1) {
        final String msg0 = wsclient1.getReceived(String.class).get(0);
        assertTrue(msg0.startsWith(CONNECTED_PREFIX), "'" + msg0 + "' should start with '" + CONNECTED_PREFIX + "'");
        return msg0.substring(CONNECTED_PREFIX.length());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                final int port = getPort();
                from("undertow:ws://localhost:" + port + "/app1")
                        .log(">>> Message received from WebSocket Client : ${body}").to("mock:result1");

                from("undertow:ws://localhost:" + port + "/app2?useStreaming=true").to("mock:result2");

                /* echo */
                from("undertow:ws://localhost:" + port + "/app3").to("undertow:ws://localhost:" + port + "/app3");

                /* sendToAll */
                from("undertow:ws://localhost:" + port + "/app4") //
                        .to("undertow:ws://localhost:" + port + "/app4?sendToAll=true");

                /* fireWebSocketChannelEvents */
                from("undertow:ws://localhost:" + port + "/app5?fireWebSocketChannelEvents=true") //
                        .to("mock:result5") //
                        .to("undertow:ws://localhost:" + port + "/app5");

                /* fireWebSocketChannelEvents */
                from("undertow:ws://localhost:" + port + "/app6?fireWebSocketChannelEvents=true") //
                        .process(new Processor() {
                            private final Set<String> connectionKeys = new LinkedHashSet<>();

                            public void process(final Exchange exchange) {
                                final Message in = exchange.getIn();
                                final String connectionKey = in.getHeader(UndertowConstants.CONNECTION_KEY,
                                        String.class);
                                final EventType eventType = in.getHeader(UndertowConstants.EVENT_TYPE_ENUM,
                                        EventType.class);
                                final String body = in.getBody(String.class);
                                if (eventType == EventType.ONOPEN) {
                                    connectionKeys.add(connectionKey);
                                    in.setBody(CONNECTED_PREFIX + connectionKey);
                                } else if (eventType == EventType.ONCLOSE) {
                                    connectionKeys.remove(connectionKey);
                                } else if (body != null) {
                                    if (body.startsWith(BROADCAST_MESSAGE_PREFIX)) {
                                        List<String> keys = Arrays
                                                .asList(body.substring(BROADCAST_MESSAGE_PREFIX.length()).split(" "));
                                        in.setHeader(UndertowConstants.CONNECTION_KEY_LIST, keys);
                                    }
                                }
                            }
                        })//
                        .to("undertow:ws://localhost:" + port + "/app6");
            }
        };
    }

}
