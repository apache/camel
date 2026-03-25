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
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.client.BayeuxClient;
import org.cometd.client.http.jetty.JettyHttpClientTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CometdProducerConsumerExtensionTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port portSSL = AvailablePortFinder.find();
    private String uri;
    private String uriSSL;

    private String pwd = "changeit";

    @Test
    void testCensorExtensionReplaceForbiddenWords() throws Exception {
        List<Object> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        HttpClient httpClient = new HttpClient();
        httpClient.start();
        try {
            BayeuxClient client = new BayeuxClient(
                    "http://127.0.0.1:" + port.getPort() + "/cometd", new JettyHttpClientTransport(null, httpClient));
            client.handshake();
            assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

            // Wait for subscription to be confirmed before publishing
            CountDownLatch subscribed = new CountDownLatch(1);
            client.getChannel("/channel/test").subscribe(
                    (channel, message) -> {
                        received.add(message.getData());
                        latch.countDown();
                    },
                    message -> subscribed.countDown());
            assertTrue(subscribed.await(5, TimeUnit.SECONDS));

            template.sendBody("direct:input", "one");
            template.sendBody("direct:input", "two");
            template.sendBody("direct:input", "hello");

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("***", received.get(0));
            assertEquals("***", received.get(1));
            assertEquals("hello", received.get(2));

            client.disconnect();
            client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
        } finally {
            httpClient.stop();
        }
    }

    @Test
    void testCensorExtensionReplaceForbiddenWordsThroughSSL() throws Exception {
        List<Object> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        URL keyStoreUrl = CometdProducerConsumerInOutTest.class.getResource("/jsse/localhost.p12");
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setTrustStorePath(keyStoreUrl.getPath());
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("PKCS12");
        HttpClient httpClient = new HttpClient();
        httpClient.setSslContextFactory(sslContextFactory);
        httpClient.start();
        try {
            BayeuxClient client = new BayeuxClient(
                    "https://localhost:" + portSSL.getPort() + "/cometd", new JettyHttpClientTransport(null, httpClient));
            client.handshake();
            assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

            // Wait for subscription to be confirmed before publishing
            CountDownLatch subscribed = new CountDownLatch(1);
            client.getChannel("/channel/test").subscribe(
                    (channel, message) -> {
                        received.add(message.getData());
                        latch.countDown();
                    },
                    message -> subscribed.countDown());
            assertTrue(subscribed.await(5, TimeUnit.SECONDS));

            template.sendBody("direct:input-ssl", "one");
            template.sendBody("direct:input-ssl", "hello again");

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("***", received.get(0));
            assertEquals("hello again", received.get(1));

            client.disconnect();
            client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
        } finally {
            httpClient.stop();
        }
    }

    @Override
    public void setupResources() {
        uri = "cometd://127.0.0.1:" + port.getPort() + "/channel/test?baseResource=file:./target/test-classes/webapp&"
              + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

        uriSSL = "cometds://127.0.0.1:" + portSSL.getPort() + "/channel/test?baseResource=file:./target/test-classes/webapp&"
                 + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws URISyntaxException {
                CometdComponent component = context.getComponent("cometd", CometdComponent.class);
                component.addExtension(new Censor());

                CometdComponent component2 = (CometdComponent) context.getComponent("cometds");
                component2.setSslPassword(pwd);
                component2.setSslKeyPassword(pwd);
                URI keyStoreUrl
                        = CometdProducerConsumerInOutTest.class.getResource("/jsse/localhost.p12").toURI();
                component2.setSslKeystore(keyStoreUrl.getPath());
                component2.addExtension(new Censor());

                from("direct:input").to(uri);
                from("direct:input-ssl").to(uriSSL);
            }
        };
    }

    public static final class Censor implements BayeuxServer.Extension, ServerSession.RemovedListener {

        private HashSet<String> forbidden = new HashSet<>(Arrays.asList("one", "two"));

        @Override
        public void removed(ServerSession session, ServerMessage message, boolean timeout) {
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
