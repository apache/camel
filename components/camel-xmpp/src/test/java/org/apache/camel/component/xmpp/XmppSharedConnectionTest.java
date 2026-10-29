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
package org.apache.camel.component.xmpp;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Endpoints of one account that differ in the participant share the account's connection: a server keeps one session
 * per account and resource and closes the older one when another logs in. Each endpoint still sends to its own
 * participant.
 */
class XmppSharedConnectionTest extends CamelTestSupport {

    private static final String ALICE = "alice@" + FakeXmppServer.DOMAIN;
    private static final String CAROL = "carol@" + FakeXmppServer.DOMAIN;
    private static final String ROOM = "dev@conference." + FakeXmppServer.DOMAIN;

    private FakeXmppServer server;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        server = new FakeXmppServer();
        CamelContext context = super.createCamelContext();
        // the test server has no TLS
        context.getRegistry().bind("connectionConfig", XMPPTCPConnectionConfiguration.builder()
                .setHostAddress(InetAddress.getLoopbackAddress())
                .setPort(server.getPort())
                .setXmppDomain(FakeXmppServer.DOMAIN)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .build());
        return context;
    }

    @AfterEach
    void closeServer() throws Exception {
        // stop the routes before the server, so the consumers do not try to connect to a server that is closed
        context.stop();
        if (server != null) {
            server.close();
        }
    }

    @Test
    void endpointsOfOneAccountShareItsConnection() {
        template.sendBody("direct:alice", "hello alice");
        template.sendBody("direct:carol", "hello carol");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(List.of("hello alice"), server.messagesTo(ALICE));
            assertEquals(List.of("hello carol"), server.messagesTo(CAROL));
        });
        assertEquals(0, server.conflicts(), "A session of the account was closed by another one");
        assertEquals(1, server.binds(), "The account logged in more than once");
        assertEquals(1, server.boundSessions());
    }

    @Test
    void stoppingOneEndpointKeepsTheConnectionOfTheOther() throws Exception {
        context.getRouteController().stopRoute("alice");
        context.removeEndpoint(context.getEndpoint(uri(ALICE)));

        template.sendBody("direct:carol", "hello carol");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of("hello carol"), server.messagesTo(CAROL)));
        assertEquals(1, server.binds(), "The account logged in more than once");
        assertEquals(1, server.boundSessions());

        // the connection is closed when its last consumer or producer stops
        context.getRouteController().stopRoute("carol");
        assertEquals(1, server.boundSessions());
        context.getRouteController().stopRoute("in");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, server.boundSessions()));
    }

    @Test
    void consumerRestartedOnASharedConnectionReceivesEachMessageOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:in");
        mock.expectedBodiesReceived("first", "second");

        template.sendBody("direct:carol", "hello carol");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.boundSessions() == 1);
        server.sendChat(CAROL, "first");
        await().atMost(10, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() == 1);

        // the producer of the account keeps the connection while the consumer restarts
        context.getRouteController().stopRoute("in");
        context.getRouteController().startRoute("in");
        server.sendChat(CAROL, "second");

        // the restarted consumer must not receive a message twice
        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();
        assertEquals(1, server.binds(), "The account logged in more than once");
    }

    @Test
    void producersOfTheAccountReconnectTheSharedConnectionConcurrently() throws Exception {
        template.sendBody("direct:alice", "before alice");
        template.sendBody("direct:carol", "before carol");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(CAROL).size() == 1);

        // the server closes the session: every producer of the account finds the shared connection disconnected
        server.dropConnections();
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.boundSessions() == 0);

        int sends = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < sends; i++) {
                String route = i % 2 == 0 ? "direct:alice" : "direct:carol";
                String body = "after " + i;
                results.add(pool.submit(() -> {
                    start.await();
                    template.sendBody(route, body);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                // a producer that lost the race to connect() must not fail its exchange
                result.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // the reconnect also logged the connection in again, so the server delivers what the producers send now
        template.sendBody("direct:alice", "later alice");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(ALICE).contains("later alice"));
        assertEquals(0, server.conflicts(), "A session of the account was closed by another one");
    }

    @Test
    void connectionAcquiredDuringAnOutageIsTheConnectionOfTheAccount() throws Exception {
        template.sendBody("direct:alice", "before");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(ALICE).size() == 1);

        XmppEndpoint endpoint = context.getEndpoint(uri(ALICE), XmppEndpoint.class);
        XmppComponent component = endpoint.getComponent();
        Object first = new Object();
        Object second = new Object();
        XMPPTCPConnection connection = component.acquireConnection(endpoint, first);
        try {
            server.dropConnections();
            await().atMost(10, TimeUnit.SECONDS).until(() -> !connection.isConnected());

            // a consumer or producer of the account starts during the outage (a route that starts, the first message
            // of a toD producer): it gets the connection the others hold, connected and logged in again
            XMPPTCPConnection acquired = component.acquireConnection(endpoint, second);
            assertSame(connection, acquired, "A second connection of the account was created");
            assertTrue(acquired.isAuthenticated(), "The connection was not logged in again");
        } finally {
            component.releaseConnection(endpoint, second);
            component.releaseConnection(endpoint, first);
        }

        // the producer that got the connection before the outage sends on it again
        template.sendBody("direct:alice", "after");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of("before", "after"), server.messagesTo(ALICE)));
        assertEquals(0, server.conflicts(), "A session of the account was closed by another one");
        assertEquals(1, server.boundSessions());
    }

    @Test
    void consumerThatCannotLeaveItsRoomReleasesTheConnection() throws Exception {
        assertReleasedWhenLeavingFails("roomConsumer", roomUri("listener"));
    }

    @Test
    void groupChatProducerThatCannotLeaveItsRoomReleasesTheConnection() throws Exception {
        assertReleasedWhenLeavingFails("roomProducer", roomUri("speaker"));
    }

    private void assertReleasedWhenLeavingFails(String routeId, String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if ("roomConsumer".equals(routeId)) {
                    from(uri).routeId(routeId).to("mock:room");
                } else {
                    // the group chat producer enters the room when it starts
                    from("direct:room").routeId(routeId).to(uri);
                }
            }
        });
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.occupants(ROOM) == 1);
        XmppEndpoint endpoint = context.getEndpoint(uri, XmppEndpoint.class);
        XmppComponent component = endpoint.getComponent();
        assertEquals(1, component.connectionUsers(endpoint));

        // the connection is lost: leaving the room fails with NotConnectedException when the route stops
        Object probe = new Object();
        XMPPTCPConnection connection = component.acquireConnection(endpoint, probe);
        component.releaseConnection(endpoint, probe);
        server.dropConnections();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !connection.isConnected());

        context.getRouteController().stopRoute(routeId);

        // the connection is released all the same (it is closed, as nothing else of the account uses it)
        assertEquals(0, component.connectionUsers(endpoint));
        context.removeRoute(routeId);
    }

    private String roomUri(String user) {
        return "xmpp://" + user + "@127.0.0.1:" + server.getPort() + "?room=" + ROOM + "&nickname=" + user
               + "&password=secret&connectionConfig=#connectionConfig";
    }

    @Test
    void reconnectAcceptsAConnectionThatAnotherUserOfTheAccountConnected() throws Exception {
        XmppEndpoint endpoint = context.getEndpoint(uri(ALICE), XmppEndpoint.class);
        XMPPTCPConnectionConfiguration config
                = context.getRegistry().lookupByNameAndType("connectionConfig", XMPPTCPConnectionConfiguration.class);
        XMPPTCPConnection connection = new XMPPTCPConnection(config) {
            @Override
            public synchronized AbstractXMPPConnection connect() throws SmackException {
                // what Smack 4.4.8 connect() throws to a caller when another thread connected the connection first
                throw new SmackException.AlreadyConnectedException();
            }

            @Override
            public synchronized void login() throws SmackException {
                // what Smack 4.4.8 login() throws to a caller when another thread logged the connection in first
                throw new SmackException.AlreadyLoggedInException();
            }
        };
        assertDoesNotThrow(() -> endpoint.reconnect(connection));
    }

    private String uri(String participant) {
        return "xmpp://bot@127.0.0.1:" + server.getPort() + "/" + participant
               + "?password=secret&connectionConfig=#connectionConfig";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:alice").routeId("alice").to(uri(ALICE));
                from("direct:carol").routeId("carol").to(uri(CAROL));
                from(uri(CAROL)).routeId("in").to("mock:in");
            }
        };
    }
}
