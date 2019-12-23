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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsInOnlyParameterTest extends CamelTestSupport {

    @Test
    public void testInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").message(0).header("JMSCorrelationID").isNull();
        getMockEndpoint("mock:result").message(0).exchangePattern().isEqualTo(ExchangePattern.InOut);

        getMockEndpoint("mock:in").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:in").message(0).header("JMSCorrelationID").isNull();
        getMockEndpoint("mock:in").message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyWithJMSCorrelationID() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").message(0).header("JMSCorrelationID").isEqualTo("foobar");
        getMockEndpoint("mock:result").message(0).exchangePattern().isEqualTo(ExchangePattern.InOut);

        getMockEndpoint("mock:in").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:in").message(0).header("JMSCorrelationID").isEqualTo("foobar");
        getMockEndpoint("mock:in").message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        String out = template.requestBodyAndHeader("direct:start", "Hello World", "JMSCorrelationID", "foobar", String.class);
        assertEquals("Bye World", out);

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
            public void configure() throws Exception {
                from("direct:start")
                    .to("activemq:queue:in?exchangePattern=InOnly")
                    .transform().constant("Bye World")
                    .to("mock:result");

                from("activemq:queue:in")
                    .to("mock:in");
            }
        };
    }

}
