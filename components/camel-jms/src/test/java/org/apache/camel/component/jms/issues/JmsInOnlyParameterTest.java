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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class JmsInOnlyParameterTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

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

        MockEndpoint.assertIsSatisfied(context);
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

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("activemq:queue:JmsInOnlyParameterTest.in?exchangePattern=InOnly")
                        .transform().constant("Bye World")
                        .to("mock:result");

                from("activemq:queue:JmsInOnlyParameterTest.in")
                        .to("mock:in");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
