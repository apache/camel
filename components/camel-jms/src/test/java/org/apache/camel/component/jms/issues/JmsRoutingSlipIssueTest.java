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
import org.apache.camel.component.jms.AbstractJMSTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;

@Tags({ @Tag("not-parallel") })
public class JmsRoutingSlipIssueTest extends AbstractJMSTest {

    @Test
    public void testJmsRoutingSlip() throws Exception {
        getMockEndpoint("mock:JmsRoutingSlipIssueTest.a").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:JmsRoutingSlipIssueTest.b").expectedBodiesReceived("HelloA");
        getMockEndpoint("mock:JmsRoutingSlipIssueTest.c").expectedBodiesReceived("HelloAB");
        getMockEndpoint("mock:result").expectedBodiesReceived("HelloABC");

        String slip
                = "activemq:queue:JmsRoutingSlipIssueTest.a,activemq:queue:JmsRoutingSlipIssueTest.b,activemq:queue:JmsRoutingSlipIssueTest.c";
        template.sendBodyAndHeader("direct:start", "Hello", "mySlip", slip);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
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
}
