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
package org.apache.camel.itest.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Based on user forum.
 */
public class JmsHttpPostIssueWithMockTest extends CamelTestSupport {

    private int port;

    @Test
    public void testJmsInOnlyHttpPostIssue() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("jms:queue:in", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJmsInOutHttpPostIssue() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        String out = template.requestBody("jms:queue:in", "Hello World", String.class);
        assertEquals("OK", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                from("jms:queue:in")
                    .setBody().simple("name=${body}")
                    .setHeader(CONTENT_TYPE).constant("application/x-www-form-urlencoded")
                    .setHeader(HTTP_METHOD).constant("POST")
                    .to("http://localhost:" + port + "/myservice")
                    .to("mock:result");

                from("jetty:http://0.0.0.0:" + port + "/myservice")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            assertEquals("name=Hello World", body);

                            exchange.getOut().setBody("OK");
                            exchange.getOut().setHeader(CONTENT_TYPE, "text/plain");
                            exchange.getOut().setHeader(HTTP_RESPONSE_CODE, 200);
                        }
                    });
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);

        registry.bind("jms", amq);
    }

}
