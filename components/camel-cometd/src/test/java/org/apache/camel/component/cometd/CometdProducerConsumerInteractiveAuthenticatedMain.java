/**
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

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.DefaultSecurityPolicy;
import org.junit.Ignore;

@Ignore("Run this test manually")
public class CometdProducerConsumerInteractiveAuthenticatedMain {

    private static final String URI = "cometd://127.0.0.1:9091/channel/test?baseResource=file:./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private static final String URIS = "cometds://127.0.0.1:9443/channel/test?baseResource=file:./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private CamelContext context;

    private String pwd = "changeit";

    public static void main(String[] args) throws Exception {
        CometdProducerConsumerInteractiveAuthenticatedMain me = new CometdProducerConsumerInteractiveAuthenticatedMain();
        me.testCometdProducerConsumerInteractive();
    }

    public void testCometdProducerConsumerInteractive() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        context.start();
    }

    private RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                CometdComponent component = (CometdComponent) context.getComponent("cometds");
                component.setSslPassword(pwd);
                component.setSslKeyPassword(pwd);

                CometdComponent component2 = (CometdComponent) context.getComponent("cometd");
                BayeuxAuthenticator bayeuxAuthenticator = new BayeuxAuthenticator();
                component2.setSecurityPolicy(bayeuxAuthenticator);
                component2.addExtension(bayeuxAuthenticator);

                File file = new File("./src/test/resources/jsse/localhost.ks");
                URI keyStoreUrl = file.toURI();
                component.setSslKeystore(keyStoreUrl.getPath());

                from("stream:in").to(URI).to(URIS);
            }
        };
    }

    /**
     * Custom SecurityPolicy, see http://cometd.org/documentation/howtos/authentication for details
     */
    public static final class BayeuxAuthenticator extends DefaultSecurityPolicy implements BayeuxServer.Extension, ServerSession.RemoveListener {

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

        public boolean sendMeta(ServerSession to, ServerMessage.Mutable message) {
            if (Channel.META_HANDSHAKE.equals(message.getChannel())) {
                if (!message.isSuccessful()) {
                    Map<String, Object> advice = message.getAdvice(true);
                    advice.put(Message.RECONNECT_FIELD, Message.RECONNECT_HANDSHAKE_VALUE);

                    Map<String, Object> ext = message.getExt(true);
                    Map<String, Object> authentication = new HashMap<String, Object>();
                    ext.put("authentication", authentication);
                    authentication.put("failed", true);
                    authentication.put("failureReason", "invalid_credentials");
                }
            }
            return true;
        }

        public void removed(ServerSession session, boolean timeout) {
            // Remove authentication data
        }

        public boolean rcv(ServerSession from, ServerMessage.Mutable message) {
            return true;
        }

        public boolean rcvMeta(ServerSession from, ServerMessage.Mutable message) {
            return true;
        }

        public boolean send(ServerSession from, ServerSession to, ServerMessage.Mutable message) {
            return true;
        }
    }

}
