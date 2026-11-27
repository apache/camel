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
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With testConnectionOnStartup=false the consumer starts in the background when the server cannot be reached at start.
 * When the server is reachable again, it can still refuse the first login or the first room entry (its authentication
 * backend or its room service is not ready yet): the delayed start must try again, as it does while the server cannot
 * be reached, until the consumer receives messages.
 */
class XmppConsumerDelayedStartRetryTest {

    private static final String ROOM = "dev@conference." + FakeXmppServer.DOMAIN;
    private static final String BOB = "bob@" + FakeXmppServer.DOMAIN;

    private DefaultCamelContext context;
    private FakeXmppServer server;

    @AfterEach
    void stop() throws Exception {
        if (context != null) {
            context.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void roomIsEnteredWhenTheFirstEntryAfterTheDelayedStartWasRefused() throws Exception {
        int port = freePort();
        startRoute(port, "xmpp://127.0.0.1:" + port + "?room=" + ROOM + "&nickname=camel"
                         + "&connectionConfig=#connectionConfig&connectionPollDelay=1&testConnectionOnStartup=false");

        // the server is reachable now, but refuses the first room entry (its room service is not ready yet)
        server = new FakeXmppServer(port);
        server.rejectRoomJoins(1);

        await().atMost(20, TimeUnit.SECONDS).until(() -> server.occupants(ROOM) == 1);
        assertEquals(2, server.roomJoinAttempts(), "the refused entry, then the one that was accepted");

        MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        server.sendGroupChat(ROOM, "alice", "hello");
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("in"));
    }

    @Test
    void chatIsConsumedWhenTheFirstLoginAfterTheDelayedStartFailed() throws Exception {
        int port = freePort();
        startRoute(port, "xmpp://127.0.0.1:" + port + "/" + BOB
                         + "?connectionConfig=#connectionConfig&connectionPollDelay=1&testConnectionOnStartup=false");

        // the server is reachable now, but its first login fails with a temporary authentication failure
        server = new FakeXmppServer(port);
        server.failLogins(1);

        await().atMost(20, TimeUnit.SECONDS).until(() -> server.boundSessions() == 1);
        assertEquals(2, server.logins(), "the failed login, then the one that worked");
        // the connection whose login failed is closed, not left open next to the one that works
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.openConnections() == 1);

        MockEndpoint mock = context.getEndpoint("mock:out", MockEndpoint.class);
        mock.expectedBodiesReceived("hello");
        server.sendChat(BOB, "hello");
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    private void startRoute(int port, String uri) throws Exception {
        context = new DefaultCamelContext();
        // the test server has no TLS
        context.getRegistry().bind("connectionConfig", XMPPTCPConnectionConfiguration.builder()
                .setHostAddress(InetAddress.getLoopbackAddress())
                .setPort(port)
                .setXmppDomain(FakeXmppServer.DOMAIN)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .performSaslAnonymousAuthentication()
                .build());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("in").to("mock:out");
            }
        });
        // nothing listens on the port yet, so the consumer starts in the background
        context.start();
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("in"));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
