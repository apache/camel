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
import org.cometd.Bayeux;
import org.cometd.Client;
import org.cometd.Extension;
import org.cometd.Message;
import org.cometd.RemoveListener;
import org.cometd.server.AbstractBayeux;

public class CometdProducerConsumerInteractiveAuthenticatedMain {

    private static final String URI = "cometd://127.0.0.1:9091/service/test?baseResource=file:./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private static final String URIS = "cometds://127.0.0.1:9443/service/test?baseResource=file:./src/test/resources/webapp&"
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
    public static final class BayeuxAuthenticator extends AbstractBayeux.DefaultPolicy implements Extension, RemoveListener {

        private String user = "changeit";
        private String pwd = "changeit";

        @Override
        public boolean canHandshake(Message message) {
            Map<String, Object> ext = message.getExt(false);
            if (ext == null) {
                return false;
            }

            // Be sure the client does not cheat us
            ext.remove("authenticationData");

            @SuppressWarnings("unchecked")
            Map<String, Object> authentication = (Map<String, Object>) ext.get("authentication");
            if (authentication == null) {
                return false;
            }

            Object authenticationData = verify(authentication);
            if (authenticationData == null) {
                return false;
            }

            // Store the authentication result in the message for later processing
            ext.put("authenticationData", authenticationData);

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
        public Message sendMeta(Client remote, Message responseMessage) {
            if (Bayeux.META_HANDSHAKE.equals(responseMessage.getChannel())) {
                Message requestMessage = responseMessage.getAssociated();

                Map<String, Object> requestExt = requestMessage.getExt(false);
                if (requestExt != null && requestExt.get("authenticationData") != null) {
                    Object authenticationData = requestExt.get("authenticationData");
                    // Authentication successful

                    // Link authentication data to the remote client

                    // Be notified when the remote client disappears
                    remote.addListener(this);
                } else {
                    // Authentication failed

                    // Add extra fields to the response
                    Map<String, Object> responseExt = responseMessage.getExt(true);
                    Map<String, Object> authentication = new HashMap<String, Object>();
                    responseExt.put("authentication", authentication);
                    authentication.put("failed", true);

                    // Tell the client to stop any further attempt to handshake
                    Map<String, Object> advice = new HashMap<String, Object>();
                    advice.put(Bayeux.RECONNECT_FIELD, Bayeux.NONE_RESPONSE);
                    responseMessage.put(Bayeux.ADVICE_FIELD, advice);
                }
            }
            return responseMessage;
        }

        @Override
        public void removed(String clientId, boolean timeout) {
            // Remove authentication data
        }

        @Override
        public Message rcv(Client client, Message message) {
            return message;
        }

        @Override
        public Message rcvMeta(Client client, Message message) {
            return message;
        }

        @Override
        public Message send(Client client, Message message) {
            return message;
        }
    }

}
