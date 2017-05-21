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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class XmppRouteChatTest extends CamelTestSupport {

    protected MockEndpoint consumerEndpoint;
    protected MockEndpoint producerEndpoint;
    protected String body1 = "the first message";
    protected String body2 = "the second message";

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        EmbeddedXmppTestServer.instance().bindSSLContextTo(registry);

        return registry;
    }

    @Test
    public void testXmppChat() throws Exception {
        consumerEndpoint = context.getEndpoint("mock:out1", MockEndpoint.class);
        producerEndpoint = context.getEndpoint("mock:out2", MockEndpoint.class);

        consumerEndpoint.expectedBodiesReceived(body1, body2);
        producerEndpoint.expectedBodiesReceived(body1, body2);

        //will send chat messages to the consumer
        template.sendBody("direct:toConsumer", body1);
        Thread.sleep(50);
        template.sendBody("direct:toConsumer", body2);
        
        template.sendBody("direct:toProducer", body1);
        Thread.sleep(50);
        template.sendBody("direct:toProducer", body2);

        consumerEndpoint.assertIsSatisfied();
        producerEndpoint.assertIsSatisfied();

    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                from("direct:toConsumer")
                    .to(getConsumerUri());

                from("direct:toProducer")
                    .to(getProducerUri());

                from(getConsumerUri())
                    .to("mock:out1");

                from(getProducerUri())
                    .to("mock:out2");
            }
        };
    }

    protected String getProducerUri() {
        return "xmpp://localhost:" + EmbeddedXmppTestServer.instance().getXmppPort()
            + "/camel_producer@apache.camel?connectionConfig=#customConnectionConfig&room=camel-test-producer@conference.apache.camel&user=camel_producer&password=secret&serviceName=apache.camel";
    }
    
    protected String getConsumerUri() {
        return "xmpp://localhost:" + EmbeddedXmppTestServer.instance().getXmppPort()
            + "/camel_consumer@apache.camel?connectionConfig=#customConnectionConfig&room=camel-test-consumer@conference.apache.camel&user=camel_consumer&password=secret&serviceName=apache.camel";
    }
}
