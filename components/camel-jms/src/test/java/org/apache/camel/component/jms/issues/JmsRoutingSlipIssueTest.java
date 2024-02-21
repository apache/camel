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
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmsRoutingSlipIssueTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsRoutingSlip() throws Exception {
        getMockEndpoint("mock:JmsRoutingSlipIssueTest.a").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:JmsRoutingSlipIssueTest.b").expectedBodiesReceived("HelloA");
        getMockEndpoint("mock:JmsRoutingSlipIssueTest.c").expectedBodiesReceived("HelloAB");
        getMockEndpoint("mock:result").expectedBodiesReceived("HelloABC");

        String slip
                = "activemq:queue:JmsRoutingSlipIssueTest.a,activemq:queue:JmsRoutingSlipIssueTest.b,activemq:queue:JmsRoutingSlipIssueTest.c";
        template.sendBodyAndHeader("direct:start", "Hello", "mySlip", slip);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        // need to use InOut as we do request/reply over JMS
                        .setExchangePattern(ExchangePattern.InOut)
                        .routingSlip(header("mySlip"))
                        .to("mock:result");

                from("activemq:queue:JmsRoutingSlipIssueTest.a")
                        .to("mock:JmsRoutingSlipIssueTest.a")
                        .transform(body().append("A"));

                from("activemq:queue:JmsRoutingSlipIssueTest.b")
                        .to("mock:JmsRoutingSlipIssueTest.b")
                        .transform(body().append("B"));

                from("activemq:queue:JmsRoutingSlipIssueTest.c")
                        .to("mock:JmsRoutingSlipIssueTest.c")
                        .transform(body().append("C"));
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
