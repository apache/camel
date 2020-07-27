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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.jivesoftware.smack.packet.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Caused by: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target")
public class XmppRouteTest {
    protected static boolean enabled;
    protected static String xmppUrl;
    private static final Logger LOG = LoggerFactory.getLogger(XmppRouteTest.class);
    protected Exchange receivedExchange;
    protected CamelContext context = new DefaultCamelContext();
    protected CountDownLatch latch = new CountDownLatch(1);
    protected Endpoint endpoint;
    protected ProducerTemplate client;
    private EmbeddedXmppTestServer embeddedXmppTestServer;

    @Test
    public void testXmppRouteWithTextMessage() throws Exception {
        String expectedBody = "Hello there!";
        sendExchange(expectedBody);

        Object body = assertReceivedValidExchange();
        assertEquals(expectedBody, body, "body");
    }

    protected void sendExchange(final Object expectedBody) {
        client.send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                exchange.getIn().setBody(expectedBody);
                exchange.getIn().setHeader("cheese", 123);
            }
        });
    }

    protected Object assertReceivedValidExchange() throws Exception {
        // lets wait on the message being received
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertNotNull(receivedExchange);
        XmppMessage receivedMessage = (XmppMessage) receivedExchange.getIn();

        assertEquals(123, receivedMessage.getHeader("cheese"), "cheese header");
        Object body = receivedMessage.getBody();
        XmppRouteTest.LOG.debug("Received body: " + body);
        Message xmppMessage = receivedMessage.getXmppMessage();
        assertNotNull(xmppMessage);

        XmppRouteTest.LOG.debug("Received XMPP message: " + xmppMessage.getBody());
        return body;
    }

    @BeforeEach
    public void setUp() throws Exception {
        client = new DefaultProducerTemplate(context);

        String uriPrefix = getUriPrefix();
        final String uri1 = uriPrefix + "&resource=camel-test-from&nickname=came-test-from";
        final String uri2 = uriPrefix + "&resource=camel-test-to&nickname=came-test-to";
        final String uri3 = uriPrefix + "&resource=camel-test-from-processor&nickname=came-test-from-processor";
        LOG.info("Using URI " + uri1 + " and " + uri2);

        endpoint = context.getEndpoint(uri1);
        assertNotNull(endpoint, "No endpoint found!");

        // lets add some routes
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(uri1).to(uri2);
                from(uri3).process(new Processor() {
                    public void process(Exchange e) {
                        LOG.info("Received exchange: " + e);
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        });

        context.start();
        embeddedXmppTestServer = new EmbeddedXmppTestServer();
    }

    protected String getUriPrefix() {
        return "xmpp://localhost:" + embeddedXmppTestServer.getXmppPort() + "/camel?login=false&room=camel-anon";
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.stop();
        context.stop();
        embeddedXmppTestServer.stop();
    }
}
