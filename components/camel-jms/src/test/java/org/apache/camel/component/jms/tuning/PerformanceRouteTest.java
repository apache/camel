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
package org.apache.camel.component.jms.tuning;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Disabled
public class PerformanceRouteTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceRouteTest.class);
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testPerformance() throws Exception {
        assumeTrue(canRunOnThisPlatform(), "Test is not intended for this platform");

        long start = System.currentTimeMillis();

        int size = 200;
        getMockEndpoint("mock:audit").expectedMessageCount(size);
        getMockEndpoint("mock:audit").expectsNoDuplicates().body();

        getMockEndpoint("mock:gold").expectedMinimumMessageCount((size / 2) - (size / 10));
        getMockEndpoint("mock:silver").expectedMinimumMessageCount(size / 10);

        for (int i = 0; i < size; i++) {
            String type;
            if (i % 10 == 0) {
                type = "silver";
            } else if (i % 2 == 0) {
                type = "gold";
            } else {
                type = "bronze";
            }
            template.sendBodyAndHeader("activemq:queue:inbox", "Message " + i, "type", type);
        }

        MockEndpoint.assertIsSatisfied(context);

        long delta = System.currentTimeMillis() - start;
        LOG.info("RoutePerformanceTest: Sent: {} Took: {} ms", size, delta);
    }

    private boolean canRunOnThisPlatform() {
        String os = System.getProperty("os.name");
        // HP-UX is just to slow to run this test
        return !os.toLowerCase().contains("hp-ux");
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
                from("activemq:queue:inbox?concurrentConsumers=10")
                        .to("activemq:topic:audit")
                        .choice()
                        .when(header("type").isEqualTo("gold"))
                        .to("direct:gold")
                        .when(header("type").isEqualTo("silver"))
                        .to("direct:silver")
                        .otherwise()
                        .to("direct:bronze")
                        .end();

                from("direct:gold")
                        .to("mock:gold");

                from("direct:silver")
                        .to("mock:silver");

                from("direct:bronze")
                        .to("mock:bronze");

                from("activemq:topic:audit").to("mock:audit");
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
