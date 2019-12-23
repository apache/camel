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
import org.junit.Ignore;
import org.junit.Test;

@Ignore("This test is not working at the moment")
public class XmppMultiUserChatTest extends CamelTestSupport {

    protected MockEndpoint consumerEndpoint;
    protected String body1 = "the first message";
    protected String body2 = "the second message";
    private EmbeddedXmppTestServer embeddedXmppTestServer;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();

        embeddedXmppTestServer.bindSSLContextTo(registry);

        return registry;
    }

    @Test
    public void testXmppChat() throws Exception {
        consumerEndpoint = context.getEndpoint("mock:out", MockEndpoint.class);
        consumerEndpoint.expectedBodiesReceived(body1, body2);

        //will send chat messages to the room
        template.sendBody("direct:toProducer", body1);
        Thread.sleep(50);
        template.sendBody("direct:toProducer", body2);

        consumerEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                from("direct:toProducer")
                    .to(getProducerUri());

                from(getConsumerUri())
                    .to("mock:out");
            }
        };
    }

    protected String getProducerUri() {
        // the nickname parameter is necessary in these URLs because the '@' in the user name can not be parsed by
        // vysper during chat room message routing.

        // here on purpose we provide the room query parameter without the domain name as 'camel-test', and Camel
        // will resolve it properly to 'camel-test@conference.apache.camel'
        return "xmpp://localhost:" + embeddedXmppTestServer.getXmppPort()
            + "/?connectionConfig=#customConnectionConfig&room=camel-test&user=camel_producer@apache.camel&password=secret&nickname=camel_producer";
    }
    
    protected String getConsumerUri() {
        // however here we provide the room query parameter as fully qualified, including the domain name as
        // 'camel-test@conference.apache.camel'
        return "xmpp://localhost:" + embeddedXmppTestServer.getXmppPort()
            + "/?connectionConfig=#customConnectionConfig&room=camel-test@conference.apache.camel&user=camel_consumer@apache.camel&password=secret&nickname=camel_consumer";
    }

    @Override
    public void doPreSetup() throws Exception {
        embeddedXmppTestServer = new EmbeddedXmppTestServer();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        embeddedXmppTestServer.stop();
    }
}
