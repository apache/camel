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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.junit.Ignore;

@Ignore("Run this test manually")
public class CometdProducerConsumerInteractiveExtensionMain {

    private static final String URI = "cometd://127.0.0.1:9091/channel/test?baseResource=file:./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private static final String URIS = "cometds://127.0.0.1:9443/channel/test?baseResource=file:./src/test/resources/webapp&"
            + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

    private CamelContext context;

    private String pwd = "changeit";

    public static void main(String[] args) throws Exception {
        CometdProducerConsumerInteractiveExtensionMain me = new CometdProducerConsumerInteractiveExtensionMain();
        me.testCometdProducerConsumerInteractive();
    }

    public void testCometdProducerConsumerInteractive() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(createRouteBuilder());
        context.start();
    }

    private RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws URISyntaxException {
                CometdComponent component = (CometdComponent) context.getComponent("cometds");
                component.setSslPassword(pwd);
                component.setSslKeyPassword(pwd);

                CometdComponent component2 = (CometdComponent) context.getComponent("cometd");
                Censor bayeuxAuthenticator = new Censor();
                component2.addExtension(bayeuxAuthenticator);

                URI keyStoreUrl = CometdProducerConsumerInteractiveExtensionMain.class.getResource("/jsse/localhost.p12").toURI();
                component.setSslKeystore(keyStoreUrl.getPath());

                from("stream:in").to(URI).to(URIS);
            }
        };
    }

    public static final class Censor implements BayeuxServer.Extension, ServerSession.RemoveListener {

        private HashSet<String> forbidden = new HashSet<>(Arrays.asList("one", "two"));

        @Override
        public void removed(ServerSession session, boolean timeout) {
            // called on remove of client
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
            Object data = message.getData();
            if (forbidden.contains(data)) {
                message.put("data", "***");
            }
            return true;
        }

        @Override
        public boolean sendMeta(ServerSession from, ServerMessage.Mutable message) {
            return true;
        }
    }
}
