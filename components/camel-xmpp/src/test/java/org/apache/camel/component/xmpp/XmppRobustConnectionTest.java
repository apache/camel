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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test to verify that the XMPP consumer will reconnect when the connection is lost.
 * Also verifies that the XMPP producer will lazily re-establish a lost connection.
 */
public class XmppRobustConnectionTest extends CamelTestSupport {

    private EmbeddedXmppTestServer embeddedXmppTestServer;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();

        embeddedXmppTestServer.bindSSLContextTo(registry);

        return registry;
    }

    @Ignore("Since upgrade to smack 4.2.0 the robust connection handling doesn't seem to work, as consumerEndpoint below receives only 5 payloads instead of the expected 9")
    @Test
    public void testXmppChatWithRobustConnection() throws Exception {
        // does not work well on aix or solaris
        if (isPlatform("aix") || isPlatform("sunos")) {
            return;
        }

        MockEndpoint consumerEndpoint = context.getEndpoint("mock:out", MockEndpoint.class);
        MockEndpoint errorEndpoint = context.getEndpoint("mock:error", MockEndpoint.class);

        // the sleep may not be sufficient so assume around 9 or so messages
        consumerEndpoint.setMinimumExpectedMessageCount(9);
        errorEndpoint.setExpectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:start", "Test message [ " + i + " ]");
        }

        consumerEndpoint.assertIsNotSatisfied();
        errorEndpoint.assertIsNotSatisfied();

        embeddedXmppTestServer.stopXmppEndpoint();
        Thread.sleep(2000);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:start", "Test message [ " + i + " ]");
        }

        errorEndpoint.assertIsSatisfied();
        consumerEndpoint.assertIsNotSatisfied();

        embeddedXmppTestServer.startXmppEndpoint();
        Thread.sleep(2000);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:start", "Test message [ " + i + " ]");
        }

        consumerEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                onException(RuntimeException.class).handled(true).to("mock:error");

                from("direct:start").id("direct:start")
                    .to(getProducerUri());

                from(getConsumerUri())
                    .to("mock:out");
            }
        };
    }

    protected String getProducerUri() {
        return "xmpp://localhost:" + embeddedXmppTestServer.getXmppPort()
            + "/camel_producer@apache.camel?connectionConfig=#customConnectionConfig&room=camel-test@conference.apache.camel&user=camel_producer&password=secret&serviceName=apache.camel";
    }

    protected String getConsumerUri() {
        return "xmpp://localhost:" + embeddedXmppTestServer.getXmppPort()
            + "/camel_consumer@apache.camel?connectionConfig=#customConnectionConfig&room=camel-test@conference.apache.camel&user=camel_consumer&password=secret&serviceName=apache.camel"
            + "&connectionPollDelay=1";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        embeddedXmppTestServer = new EmbeddedXmppTestServer();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        embeddedXmppTestServer.stop();
    }
}
