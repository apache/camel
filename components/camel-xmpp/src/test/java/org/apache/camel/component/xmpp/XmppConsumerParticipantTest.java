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
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * A consumer with a participant routes only the chat messages from that participant, although the consumers of one
 * account share its connection, which receives the chat messages of every sender. A consumer without a participant
 * routes the chat messages of every sender.
 */
class XmppConsumerParticipantTest extends CamelTestSupport {

    private static final String CAROL = "carol@" + FakeXmppServer.DOMAIN;
    private static final String DAVE = "dave@" + FakeXmppServer.DOMAIN;
    private static final String ERIN = "erin@" + FakeXmppServer.DOMAIN;
    private static final String FRANK = "frank@" + FakeXmppServer.DOMAIN;

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
    void consumerRoutesOnlyTheChatMessagesOfItsParticipant() throws Exception {
        MockEndpoint carol = getMockEndpoint("mock:carol");
        carol.expectedBodiesReceived("from carol");
        carol.message(0).header(XmppConstants.FROM).convertToString().isEqualTo(CAROL + "/phone");
        // with doc the body is the Smack message
        MockEndpoint dave = getMockEndpoint("mock:dave");
        dave.expectedMessageCount(1);
        dave.message(0).header(XmppConstants.FROM).convertToString().isEqualTo(DAVE + "/phone");
        // a participant without a domain is in the domain of the server
        MockEndpoint erin = getMockEndpoint("mock:erin");
        erin.expectedBodiesReceived("from erin");
        MockEndpoint all = getMockEndpoint("mock:all");
        all.expectedBodiesReceivedInAnyOrder("from carol", "from dave", "from erin", "from frank");

        await().atMost(10, TimeUnit.SECONDS).until(() -> server.boundSessions() == 1);
        server.sendChat(CAROL, "from carol");
        server.sendChat(DAVE, "from dave");
        server.sendChat(ERIN, "from erin");
        server.sendChat(FRANK, "from frank");

        all.assertIsSatisfied();
        // the consumers with a participant must not route the chat messages of the other senders
        carol.setAssertPeriod(500);
        MockEndpoint.assertIsSatisfied(context);
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
                from(uri(CAROL)).routeId("carol").to("mock:carol");
                from(uri(DAVE) + "&doc=true").routeId("dave").to("mock:dave");
                from(uri("erin")).routeId("erin").to("mock:erin");
                from(uri("")).routeId("all").to("mock:all");
            }
        };
    }
}
