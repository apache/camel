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

public class JmsInOutRoutingSlipTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsInOutRoutingSlip() throws Exception {
        getMockEndpoint("mock:JmsInOutRoutingSlipTest.foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:JmsInOutRoutingSlipTest.result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:end").expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("activemq:queue:JmsInOutRoutingSlipTest.start", "World", "slip",
                "activemq:queue:JmsInOutRoutingSlipTest.foo,activemq:queue:JmsInOutRoutingSlipTest.result");

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
                from("activemq:queue:JmsInOutRoutingSlipTest.start")
                        .setExchangePattern(ExchangePattern.InOut)
                        .routingSlip(header("slip"))
                        .to("log:end")
                        .to("mock:end");

                from("activemq:queue:JmsInOutRoutingSlipTest.foo")
                        .to("mock:JmsInOutRoutingSlipTest.foo")
                        .to("log:JmsInOutRoutingSlipTest.foo")
                        .transform(body().prepend("Bye "));

                from("activemq:queue:JmsInOutRoutingSlipTest.result")
                        .to("log:JmsInOutRoutingSlipTest.result")
                        .to("mock:JmsInOutRoutingSlipTest.result");
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
