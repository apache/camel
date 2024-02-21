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
package org.apache.camel.component.jms.discovery;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class JmsDiscoveryTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected final MyRegistry myRegistry = new MyRegistry();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testDiscovery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(5000);
        // force shutdown after 5 seconds as otherwise the bean will keep generating a new input
        context.getShutdownStrategy().setTimeout(5);

        MockEndpoint.assertIsSatisfied(context);

        // sleep a little
        Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(myRegistry.getServices()).hasSizeGreaterThanOrEqualTo(1));
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @ContextFixture
    public void configureComponent(CamelContext context) {
        context.getRegistry().bind("service1", new MyService("service1"));
        context.getRegistry().bind("registry", myRegistry);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // lets setup the heartbeats
                from("timer:heartbeats?delay=100")
                        .to("bean:service1?method=status")
                        .to("activemq:topic:registry.heartbeats");

                // defer shutting this route down as the first route depends upon it to
                // be running so it can complete its current exchanges
                from("activemq:topic:registry.heartbeats")
                        .shutdownRoute(ShutdownRoute.Defer)
                        .to("bean:registry?method=onEvent", "mock:result");
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
