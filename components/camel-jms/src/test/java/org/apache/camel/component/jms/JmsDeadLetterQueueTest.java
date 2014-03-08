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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test for using JMS as DLQ
 *
 * @version 
 */
public class JmsDeadLetterQueueTest extends CamelTestSupport {

    protected String getUri() {
        return "activemq:queue:dead";
    }

    @Test
    public void testOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testKabom() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:dead");
        mock.expectedBodiesReceived("Kabom");

        template.sendBody("direct:start", "Kabom");

        assertMockEndpointsSatisfied();

        // the cause exception is gone in the transformation below
        assertNull(mock.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
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
                errorHandler(deadLetterChannel("seda:dead").disableRedelivery());

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if ("Kabom".equals(body)) {
                            throw new IllegalArgumentException("Kabom");
                        }
                    }
                }).to("mock:result");

                from("seda:dead").transform(exceptionMessage()).to(getUri());

                from(getUri()).to("mock:dead");
            }
        };
    }

}