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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.ReconnectionManager.ReconnectionPolicy;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smack's ReconnectionManager (enabled by the component) connects and logs in again before the connection monitor of
 * the consumer runs: the consumer must still enter its room again, as the server removed it from the room when the
 * connection was lost.
 */
class XmppConsumerSmackReconnectTest extends CamelTestSupport {

    private static final String ROOM = "dev@conference." + FakeXmppServer.DOMAIN;

    private FakeXmppServer server;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // Smack's ReconnectionManager reconnects after one second, long before the connection monitor (one hour)
        ReconnectionManager.setDefaultReconnectionPolicy(ReconnectionPolicy.FIXED_DELAY);
        ReconnectionManager.setDefaultFixedDelay(1);
        server = new FakeXmppServer();
        CamelContext context = super.createCamelContext();
        // the test server has no TLS
        context.getRegistry().bind("connectionConfig", XMPPTCPConnectionConfiguration.builder()
                .setHostAddress(InetAddress.getLoopbackAddress())
                .setPort(server.getPort())
                .setXmppDomain(FakeXmppServer.DOMAIN)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .performSaslAnonymousAuthentication()
                .build());
        return context;
    }

    @AfterEach
    void closeServer() throws Exception {
        // stop the routes before the server, so the consumers do not try to connect to a server that is closed
        context.stop();
        // the defaults of Smack (setDefaultFixedDelay also sets the policy to FIXED_DELAY, so it comes first)
        ReconnectionManager.setDefaultFixedDelay(15);
        ReconnectionManager.setDefaultReconnectionPolicy(ReconnectionPolicy.RANDOM_INCREASING_DELAY);
        if (server != null) {
            server.close();
        }
    }

    @Test
    void roomIsConsumedAfterSmackReconnected() throws Exception {
        MockEndpoint room = getMockEndpoint("mock:room");
        room.expectedBodiesReceived("before", "after");

        await().atMost(20, TimeUnit.SECONDS).until(() -> server.occupants(ROOM) == 1);
        server.sendGroupChat(ROOM, "alice", "before");
        await().atMost(10, TimeUnit.SECONDS).until(() -> room.getReceivedCounter() == 1);

        server.dropConnections();

        // Smack's ReconnectionManager connects and logs in again; the consumer must enter its room again
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, server.boundSessions(), "Smack did not log in again"));
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, server.occupants(ROOM), "The consumer did not enter the room again"));
        server.sendGroupChat(ROOM, "alice", "after");

        room.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String server = "xmpp://127.0.0.1:" + XmppConsumerSmackReconnectTest.this.server.getPort();
                String options = "connectionConfig=#connectionConfig&connectionPollDelay=3600";

                from(server + "?room=" + ROOM + "&nickname=camel&" + options).routeId("room")
                        .to("mock:room");
            }
        };
    }
}
