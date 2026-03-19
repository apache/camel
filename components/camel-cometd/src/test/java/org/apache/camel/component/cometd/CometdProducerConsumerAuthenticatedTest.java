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
package org.apache.camel.component.cometd;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.client.BayeuxClient;
import org.cometd.client.http.jetty.JettyHttpClientTransport;
import org.cometd.server.DefaultSecurityPolicy;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CometdProducerConsumerAuthenticatedTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();
    private String uri;

    @Test
    void testAuthenticatedProducerConsumer() throws Exception {
        getMockEndpoint("mock:test").expectedBodiesReceived("hello");
        template.sendBody("direct:input", "hello");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testHandshakeWithValidCredentials() throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        try {
            BayeuxClient client = createRemoteClient(httpClient, "changeit", "changeit");
            client.handshake();
            try {
                assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED),
                        "Handshake with valid credentials should reach CONNECTED state");
            } finally {
                client.disconnect();
                client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
            }
        } finally {
            httpClient.stop();
        }
    }

    @Test
    void testHandshakeWithInvalidCredentials() throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        try {
            BayeuxClient client = createRemoteClient(httpClient, "changeit", "wrongpassword");
            client.handshake();
            try {
                // Server rejects invalid credentials. client never reaches CONNECTED within timeout
                assertFalse(client.waitFor(3000, BayeuxClient.State.CONNECTED),
                        "Handshake with invalid credentials should not reach CONNECTED state");
            } finally {
                client.disconnect();
                client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
            }
        } finally {
            httpClient.stop();
        }
    }

    private BayeuxClient createRemoteClient(HttpClient httpClient, String user, String credentials) {
        String url = "http://127.0.0.1:" + port.getPort() + "/cometd";
        BayeuxClient client = new BayeuxClient(url, new JettyHttpClientTransport(null, httpClient));
        client.addExtension(new ClientSession.Extension() {
            @Override
            public boolean sendMeta(ClientSession session, org.cometd.bayeux.Message.Mutable message) {
                if (Channel.META_HANDSHAKE.equals(message.getChannel())) {
                    Map<String, Object> authentication = new HashMap<>();
                    authentication.put("user", user);
                    authentication.put("credentials", credentials);
                    Map<String, Object> ext = message.getExt(true);
                    ext.put("authentication", authentication);
                }
                return true;
            }
        });
        return client;
    }

    @Override
    public void setupResources() {
        uri = "cometd://127.0.0.1:" + port.getPort() + "/service/test?baseResource=file:./target/test-classes/webapp&"
              + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                CometdComponent component = context.getComponent("cometd", CometdComponent.class);
                CometdProducerConsumerAuthenticatedTest.BayeuxAuthenticator bayeuxAuthenticator
                        = new CometdProducerConsumerAuthenticatedTest.BayeuxAuthenticator();
                component.setSecurityPolicy(bayeuxAuthenticator);
                component.addExtension(bayeuxAuthenticator);

                from("direct:input").to(uri);
                from(uri).to("mock:test");
            }
        };
    }

    /**
     * Custom SecurityPolicy, see http://cometd.org/documentation/howtos/authentication for details
     */
    public static final class BayeuxAuthenticator extends DefaultSecurityPolicy
            implements BayeuxServer.Extension, ServerSession.RemovedListener {

        private String user = "changeit";
        private String pwd = "changeit";

        @Override
        public boolean canHandshake(BayeuxServer server, ServerSession session, ServerMessage message) {
            if (session.isLocalSession()) {
                return true;
            }

            Map<String, Object> ext = message.getExt();
            if (ext == null) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> authentication = (Map<String, Object>) ext.get("authentication");
            if (authentication == null) {
                return false;
            }

            Object authenticationData = verify(authentication);
            if (authenticationData == null) {
                return false;
            }

            session.addListener(this);

            return true;
        }

        private Object verify(Map<String, Object> authentication) {
            if (!user.equals(authentication.get("user"))) {
                return null;
            }
            if (!pwd.equals(authentication.get("credentials"))) {
                return null;
            }
            return "OK";
        }

        @Override
        public boolean sendMeta(ServerSession to, ServerMessage.Mutable message) {
            if (Channel.META_HANDSHAKE.equals(message.getChannel())) {
                if (!message.isSuccessful()) {
                    Map<String, Object> advice = message.getAdvice(true);
                    advice.put(Message.RECONNECT_FIELD, Message.RECONNECT_HANDSHAKE_VALUE);

                    Map<String, Object> ext = message.getExt(true);
                    Map<String, Object> authentication = new HashMap<>();
                    ext.put("authentication", authentication);
                    authentication.put("failed", true);
                    authentication.put("failureReason", "invalid_credentials");
                }
            }
            return true;
        }

        @Override
        public void removed(ServerSession session, ServerMessage message, boolean timeout) {
            // Remove authentication data
        }

        @Override
        public boolean rcv(ServerSession from, ServerMessage.Mutable message) {
            return true;
        }

        @Override
        public boolean rcvMeta(ServerSession from, ServerMessage.Mutable message) {
            return true;
        }

        @Override
        public boolean send(ServerSession from, ServerSession to, ServerMessage.Mutable message) {
            return true;
        }
    }

}
