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
 * Unit test to verify that we can route a JMS message and do header lookup by name
 * without mutating it and that it can handle the default keyFormatStrategy with _HYPHEN_
 * in the key name 
 *
 * @version 
 */
public class JmsGetHeaderKeyFormatIssueWithContentTypeHeaderTest extends CamelTestSupport {

    private String uri = "activemq:queue:hello?jmsKeyFormatStrategy=default";

    @Test
    public void testSendWithHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo("Hello World");
        mock.message(0).header("Content-Type").isEqualTo("text/plain");

        MockEndpoint copy = getMockEndpoint("mock:copy");
        copy.expectedMessageCount(1);
        copy.message(0).body().isEqualTo("Hello World");
        copy.message(0).header("Content-Type").isEqualTo("text/plain");

        template.sendBodyAndHeader(uri, "Hello World", "Content-Type", "text/plain");

        assertMockEndpointsSatisfied();
    }

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
                from(uri)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            assertEquals("text/plain", exchange.getIn().getHeader("Content-Type"));

                            // do not mutate it
                            JmsMessage msg = assertIsInstanceOf(JmsMessage.class, exchange.getIn());
                            assertNotNull("javax.jms.Message should not be null", msg.getJmsMessage());
                        }
                    })
                    .to("activemq:queue:copy", "mock:result");

                from("activemq:queue:copy").to("mock:copy");
            }
        };
    }
}