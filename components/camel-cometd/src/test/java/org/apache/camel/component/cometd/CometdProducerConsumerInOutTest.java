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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.cometd.client.BayeuxClient;
import org.cometd.client.http.jetty.JettyHttpClientTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CometdProducerConsumerInOutTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port portSSL = AvailablePortFinder.find();
    private String uri;
    private String uriSSL;

    private String pwd = "changeit";

    @Test
    void testInOutExchangePattern() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedReply = new AtomicReference<>();

        HttpClient httpClient = new HttpClient();
        httpClient.start();
        try {
            String bayeuxUrl = "http://127.0.0.1:" + port.getPort() + "/cometd";
            BayeuxClient client = new BayeuxClient(bayeuxUrl, new JettyHttpClientTransport(null, httpClient));
            client.handshake();
            assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

            client.getChannel("/service/test").subscribe((channel, message) -> {
                receivedReply.set((String) message.getData());
                latch.countDown();
            });

            client.getChannel("/service/test").publish("hello", null);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive reply within timeout");
            assertEquals("reply: hello", receivedReply.get());

            client.disconnect();
            client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
        } finally {
            httpClient.stop();
        }
    }

    @Test
    void testInOutExchangePatternSSL() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedReply = new AtomicReference<>();

        URL keyStoreUrl = CometdProducerConsumerInOutTest.class.getResource("/jsse/localhost.p12");
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setTrustStorePath(keyStoreUrl.getPath());
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("PKCS12");
        HttpClient httpClient = new HttpClient();
        httpClient.setSslContextFactory(sslContextFactory);
        httpClient.start();
        try {
            // Use localhost to match the CN in localhost.p12 and endpoint is still /cometd as this is the only path added to servlet
            String bayeuxUrl = "https://localhost:" + portSSL.getPort() + "/cometd";
            BayeuxClient client = new BayeuxClient(bayeuxUrl, new JettyHttpClientTransport(null, httpClient));
            client.handshake();
            assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

            client.getChannel("/service/test").subscribe((channel, message) -> {
                receivedReply.set((String) message.getData());
                latch.countDown();
            });

            client.getChannel("/service/test").publish("hello", null);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive reply within timeout");
            assertEquals("reply SSL: hello", receivedReply.get());

            client.disconnect();
            client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
        } finally {
            httpClient.stop();
        }
    }

    @Override
    public void setupResources() {
        uri = "cometd://127.0.0.1:" + port.getPort() + "/service/test?baseResource=file:./target/test-classes/webapp&"
              + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";

        //cometds protocal to start getSslSocketConnector
        uriSSL = "cometds://127.0.0.1:" + portSSL.getPort() + "/service/test?baseResource=file:./target/test-classes/webapp&"
                 + "timeout=240000&interval=0&maxInterval=30000&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
    }

    @Override
    @SuppressWarnings("deprecation")
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws URISyntaxException {
                CometdComponent component = (CometdComponent) context.getComponent("cometds");
                component.setSslPassword(pwd);
                component.setSslKeyPassword(pwd);
                URI keyStoreUrl
                        = CometdProducerConsumerInOutTest.class.getResource("/jsse/localhost.p12").toURI();
                component.setSslKeystore(keyStoreUrl.getPath());

                from(uri)
                        .setExchangePattern(ExchangePattern.InOut)
                        .process(exchange -> exchange.getOut().setBody("reply: " + exchange.getIn().getBody()));

                from(uriSSL)
                        .setExchangePattern(ExchangePattern.InOut)
                        .process(exchange -> exchange.getOut().setBody("reply SSL: " + exchange.getIn().getBody()));
            }
        };
    }
}
