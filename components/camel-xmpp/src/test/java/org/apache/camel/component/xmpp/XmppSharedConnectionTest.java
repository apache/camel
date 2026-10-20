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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Endpoints of one account that differ in the participant share the account's connection: a server keeps one session
 * per account and resource and closes the older one when another logs in. Each endpoint still sends to its own
 * participant.
 */
class XmppSharedConnectionTest extends CamelTestSupport {

    private static final String ALICE = "alice@" + FakeXmppServer.DOMAIN;
    private static final String CAROL = "carol@" + FakeXmppServer.DOMAIN;

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
