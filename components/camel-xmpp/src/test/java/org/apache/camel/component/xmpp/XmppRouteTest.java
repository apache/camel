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
package org.apache.camel.component.xmpp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ProducerCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jivesoftware.smack.packet.Message;

/**
 * An integration test which requires a Jabber server to be running, by default on localhost.
 * <p/>
 * You can overload the <b>xmpp.url</b> system property to define the jabber connection URI
 * to something like <b>xmpp://camel@localhost/?login=false&room=</b>
 *
 * @version $Revision$
 */
public class XmppRouteTest extends TestCase {
    protected static boolean enabled;
    protected static String xmppUrl;
    private static final transient Log LOG = LogFactory.getLog(XmppRouteTest.class);
    protected XmppExchange receivedExchange;
    protected CamelContext container = new DefaultCamelContext();
    protected CountDownLatch latch = new CountDownLatch(1);
    protected Endpoint<XmppExchange> endpoint;
    protected ProducerCache<XmppExchange> client = new ProducerCache<XmppExchange>();

    public static void main(String[] args) {
        enabled = true;
        if (args.length > 0) {
            xmppUrl = args[0];
        }
        TestRunner.run(XmppRouteTest.class);
    }

    public void testXmppRouteWithTextMessage() throws Exception {
        if (isXmppServerPresent()) {
            String expectedBody = "Hello there!";
            sendExchange(expectedBody);

            Object body = assertReceivedValidExchange();
            assertEquals("body", expectedBody, body);

            //Thread.sleep(100000);
        }
    }

    protected static boolean isXmppServerPresent() {
        if (enabled) {
            return true;
        }
        return ObjectConverter.toBoolean(System.getProperty("xmpp.enable"));
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
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);

        assertNotNull(receivedExchange);
        XmppMessage receivedMessage = receivedExchange.getIn();

        Assert.assertEquals("cheese header", 123, receivedMessage.getHeader("cheese"));
        Object body = receivedMessage.getBody();
        XmppRouteTest.LOG.debug("Received body: " + body);
        Message xmppMessage = receivedMessage.getXmppMessage();
        assertNotNull(xmppMessage);

        XmppRouteTest.LOG.debug("Received XMPP message: " + xmppMessage.getBody());
        return body;
    }

    @Override
    protected void setUp() throws Exception {
        if (isXmppServerPresent()) {
            String uriPrefx = getUriPrefix();
            final String uri1 = uriPrefx + "a";
            final String uri2 = uriPrefx + "b";
            LOG.info("Using URI " + uri1 + " and " + uri2);

            // lets add some routes
            container.addRoutes(new RouteBuilder() {
                public void configure() {
                    from(uri1).to(uri2);
                    from(uri2).process(new Processor() {
                        public void process(Exchange e) {
                            LOG.info("Received exchange: " + e);
                            receivedExchange = (XmppExchange) e;
                            latch.countDown();
                        }
                    });
                }
            });
            endpoint = container.getEndpoint(uri1);
            assertNotNull("No endpoint found!", endpoint);
        }

        container.start();
    }

    protected String getUriPrefix() {
        if (xmppUrl != null) {
            return xmppUrl;
        }
        return System.getProperty("xmpp.url", "xmpp://camel@localhost/?login=false&room=").trim();
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        container.stop();
    }
}
