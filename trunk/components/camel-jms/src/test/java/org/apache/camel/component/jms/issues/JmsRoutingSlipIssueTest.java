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
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsRoutingSlipIssueTest extends CamelTestSupport {

    @Test
    public void testJmsRoutingSlip() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:b").expectedBodiesReceived("HelloA");
        getMockEndpoint("mock:c").expectedBodiesReceived("HelloAB");
        getMockEndpoint("mock:result").expectedBodiesReceived("HelloABC");

        String slip = "activemq:queue:a,activemq:queue:b,activemq:queue:c";
        template.sendBodyAndHeader("direct:start", "Hello", "mySlip", slip);

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
                from("direct:start")
                    // need to use InOut as we do request/reply over JMS
                    .setExchangePattern(ExchangePattern.InOut)
                    .routingSlip(header("mySlip"))
                    .to("mock:result");

                from("activemq:queue:a")
                    .to("mock:a")
                    .transform(body().append("A"));

                from("activemq:queue:b")
                    .to("mock:b")
                    .transform(body().append("B"));

                from("activemq:queue:c")
                    .to("mock:c")
                    .transform(body().append("C"));
            }
        };
    }
}
