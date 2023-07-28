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
package org.apache.camel.component.jms;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Creates multiple threads")
public class JmsPriorityConsumerTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected ProducerTemplate template;
    private CamelContext context;

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @Test
    public void testPriority() throws Exception {
        int messages = 100;

        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < messages; i++) {
            final int index = i;
            executor.submit(() -> {
                template.sendBody("jms:queue:JmsPriorityConsumerTest", "Message " + index);
                return null;
            });
        }

        executor.awaitTermination(5, TimeUnit.SECONDS);

        MockEndpoint lowPriority = getMockEndpoint("mock:lowPriority");
        MockEndpoint mediumPriority = getMockEndpoint("mock:mediumPriority");
        MockEndpoint highPriority = getMockEndpoint("mock:highPriority");

        Assertions.assertThat(highPriority.getReceivedExchanges().size())
                .isGreaterThan(mediumPriority.getReceivedExchanges().size());

        Assertions.assertThat(mediumPriority.getReceivedExchanges().size())
                .isGreaterThanOrEqualTo(lowPriority.getReceivedExchanges().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsPriorityConsumerTest?artemisConsumerPriority=1")
                        .to("mock:lowPriority");

                from("jms:queue:JmsPriorityConsumerTest?artemisConsumerPriority=4")
                        .to("mock:mediumPriority");

                from("jms:queue:JmsPriorityConsumerTest?artemisConsumerPriority=9")
                        .to("mock:highPriority");
            }
        };
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
    }
}
