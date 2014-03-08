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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test to verify issue we had in Camel 1.4
 */
public class JmsToFileMessageIdTest extends CamelTestSupport {

    @Test
    public void testFromJmsToFileAndMessageId() throws Exception {
        // Mock endpoint to collect message at the end of the route
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Make a route from an activemq queue to a file endpoint, then try to call getMessageId()
                from("activemq:foo")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // assert camel id is based on jms id 
                                String camelId = exchange.getIn().getMessageId();
                                assertNotNull(camelId);

                                JmsMessage jms = (JmsMessage) exchange.getIn();
                                String jmsId = jms.getJmsMessage().getJMSMessageID();
                                assertNotNull(jmsId);

                                assertEquals(jmsId, camelId);
                            }
                        })
                        .to("file://target/tofile")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                // in Camel 1.4 or older this caused a NPE
                                assertNotNull(exchange.getIn().getMessageId());
                            }
                        })
                        .to("mock:result");
            }
        };

    }
}
