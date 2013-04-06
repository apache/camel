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
package org.apache.camel.component.jms.tuning;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;


/**
 * @version 
 */
@Ignore
public class PerformanceRouteTest extends CamelTestSupport {

    private int size = 200;

    @Test
    public void testPerformance() throws Exception {
        if (!canRunOnThisPlatform()) {
            return;
        }

        long start = System.currentTimeMillis();

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

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        log.info("RoutePerformanceTest: Sent: " + size + " Took: " + delta + " ms");
    }

    private boolean canRunOnThisPlatform() {
        String os = System.getProperty("os.name");
        // HP-UX is just to slow to run this test
        return !os.toLowerCase().contains("hp-ux");
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
}
