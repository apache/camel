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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producers must send again after the connection of their account was lost: the server delivers nothing that is
 * sent before the connection logged in again, and takes the messages of the group chat producer only once it entered
 * its room again. The connection can be connected again, and logged in, by another consumer or producer of the account
 * or by Smack's ReconnectionManager.
 */
class XmppProducerReconnectTest extends CamelTestSupport {

    private static final String ALICE = "alice@" + FakeXmppServer.DOMAIN;
    private static final String ROOM = "dev@conference." + FakeXmppServer.DOMAIN;
    private static final String OPS_ROOM = "ops@conference." + FakeXmppServer.DOMAIN;

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
        context.stop();
        if (server != null) {
            server.close();
        }
    }

    @Test
    void privateChatProducerLogsInBeforeItSendsOnAConnectionConnectedByAnotherUser() throws Exception {
        template.sendBody("direct:alice", "before");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(ALICE).size() == 1);
        XMPPTCPConnection connection = connectionOf(chatUri());

        server.dropConnections();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !connection.isConnected());
        // another user of the account (or Smack's ReconnectionManager) connected it again, and has not logged it in yet
        connection.connect();
        assertFalse(connection.isAuthenticated());

        template.sendBody("direct:alice", "after");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of("before", "after"), server.messagesTo(ALICE)));
    }

    @Test
    void groupChatProducerEntersItsRoomAgainAfterItReconnected() throws Exception {
        template.sendBody("direct:room", "before");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(ROOM).size() == 1);
        XMPPTCPConnection connection = connectionOf(roomUri());

        server.dropConnections();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !connection.isConnected());

        // the producer connects and logs in again, and must enter its room again
        template.sendBody("direct:room", "after");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of("before", "after"), server.messagesTo(ROOM)));
        assertEquals(1, server.occupants(ROOM));
    }

    @Test
    void groupChatProducerEntersItsRoomAgainAfterAnotherUserLoggedTheConnectionIn() throws Exception {
        template.sendBody("direct:room", "before");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(ROOM).size() == 1);
        XMPPTCPConnection connection = connectionOf(roomUri());

        server.dropConnections();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !connection.isConnected());
        // another user of the account (or Smack's ReconnectionManager) connected and logged in the connection again
        connection.connect();
        connection.login();
        assertTrue(connection.isAuthenticated());

        template.sendBody("direct:room", "after");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of("before", "after"), server.messagesTo(ROOM)));
        assertEquals(1, server.occupants(ROOM));
    }

    @Test
    void groupChatProducerEntersItsRoomAgainWhileAnotherUserIsStillLoggingTheConnectionIn() throws Exception {
        // the connection of the account, logged in, with a listener that comes before the one of the producer
        XmppEndpoint endpoint = context.getEndpoint(opsRoomUri(), XmppEndpoint.class);
        Object probe = new Object();
        XMPPTCPConnection connection = endpoint.getComponent().acquireConnection(endpoint, probe);
        try {
            // the test logs the connection in again, not Smack
            ReconnectionManager.getInstanceFor(connection).disableAutomaticReconnection();
            AtomicBoolean holdLogin = new AtomicBoolean();
            CountDownLatch inLogin = new CountDownLatch(1);
            CountDownLatch releaseLogin = new CountDownLatch(1);
            connection.addConnectionListener(new ConnectionListener() {
                @Override
                public void authenticated(XMPPConnection c, boolean resumed) {
                    if (holdLogin.compareAndSet(true, false)) {
                        inLogin.countDown();
                        try {
                            releaseLogin.await(20, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
            // the producer starts now, so its listener comes after the one above
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:opsRoom").routeId("opsRoom").to(opsRoomUri());
                }
            });
            // a listener after the one of the producer: when it was called, the producer's was called too
            CountDownLatch closed = new CountDownLatch(1);
            connection.addConnectionListener(new ConnectionListener() {
                @Override
                public void connectionClosedOnError(Exception e) {
                    closed.countDown();
                }
            });

            template.sendBody("direct:opsRoom", "before");
            await().atMost(10, TimeUnit.SECONDS).until(() -> server.messagesTo(OPS_ROOM).size() == 1);

            server.dropConnections();
            assertTrue(closed.await(10, TimeUnit.SECONDS));

            // another user of the account (or Smack's ReconnectionManager) logs the connection in again: Smack reports
            // it as logged in, and the authenticated listeners have not been called yet
            holdLogin.set(true);
            Thread login = new Thread(() -> {
                try {
                    connection.connect();
                    connection.login();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, "other-user-login");
            login.start();
            assertTrue(inLogin.await(10, TimeUnit.SECONDS));
            assertTrue(connection.isAuthenticated());

            template.sendBody("direct:opsRoom", "after");

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(List.of("before", "after"), server.messagesTo(OPS_ROOM)));
            assertEquals(1, server.occupants(OPS_ROOM));
            int joinsAfterRejoin = server.roomJoins();

            // the login ends: the authenticated listeners are called now, after the producer entered its room again
            releaseLogin.countDown();
            login.join(10000);
            assertFalse(login.isAlive());

            template.sendBody("direct:opsRoom", "later");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(List.of("before", "after", "later"), server.messagesTo(OPS_ROOM)));
            assertEquals(1, server.occupants(OPS_ROOM));
            // the producer is in its room: it must not leave and enter it again, which is slow and can time out
            assertEquals(joinsAfterRejoin, server.roomJoins());
        } finally {
            endpoint.getComponent().releaseConnection(endpoint, probe);
        }
    }

    private XMPPTCPConnection connectionOf(String uri) throws Exception {
        XmppEndpoint endpoint = context.getEndpoint(uri, XmppEndpoint.class);
        XmppComponent component = endpoint.getComponent();
        Object probe = new Object();
        XMPPTCPConnection connection = component.acquireConnection(endpoint, probe);
        component.releaseConnection(endpoint, probe);
        return connection;
    }

    private String chatUri() {
        return "xmpp://bot@127.0.0.1:" + server.getPort() + "/" + ALICE
               + "?password=secret&connectionConfig=#connectionConfig";
    }

    private String roomUri() {
        return "xmpp://speaker@127.0.0.1:" + server.getPort() + "?room=" + ROOM
               + "&nickname=speaker&password=secret&connectionConfig=#connectionConfig";
    }

    private String opsRoomUri() {
        return "xmpp://writer@127.0.0.1:" + server.getPort() + "?room=" + OPS_ROOM
               + "&nickname=writer&password=secret&connectionConfig=#connectionConfig";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:alice").routeId("alice").to(chatUri());
                from("direct:room").routeId("room").to(roomUri());
            }
        };
    }
}
