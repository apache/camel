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
package org.apache.camel.builder.component;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.timer.TimerComponent;
import org.junit.Test;

public class ComponentsBuilderFactoryTest extends ContextTestSupport {

    @Test
    public void testIfCreateComponentCorrectlyWithoutContextProvided() {
        final TimerComponent timerComponent = ComponentsBuilderFactory.timer().build();
        assertNotNull(timerComponent);
    }

    @Test
    public void testNegativeDelay() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIfResolvePlaceholdersCorrectly() {
        context.getPropertiesComponent().setLocation("classpath:application.properties");

        final KafkaComponent kafkaComponent = ComponentsBuilderFactory.kafka()
                .brokers("{{kafka.host}}:{{kafka.port}}")
                .build(context);

        assertNotNull(kafkaComponent.getConfiguration());
        assertEquals("localhost:9092", kafkaComponent.getConfiguration().getBrokers());
    }

    @Test
    public void testIfSetsSettingsCorrectly() {
        final KafkaConfiguration kafkaConfiguration = new KafkaConfiguration();
        kafkaConfiguration.setGroupId("testGroup");
        kafkaConfiguration.setConsumerRequestTimeoutMs(5000);
        kafkaConfiguration.setBrokers("localhost:9092");

        final KafkaComponent kafkaComponent = ComponentsBuilderFactory.kafka()
                .configuration(kafkaConfiguration)
                .allowManualCommit(true)
                .build();

        assertNotNull(kafkaComponent);

        assertEquals("localhost:9092", kafkaComponent.getConfiguration().getBrokers());
        assertTrue(kafkaComponent.getConfiguration().isAllowManualCommit());

        assertEquals("testGroup", kafkaComponent.getConfiguration().getGroupId());
        assertEquals(5000, kafkaComponent.getConfiguration().getConsumerRequestTimeoutMs().intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ComponentsBuilderFactory.timer().register(context, "awesomeTimer");

                from("awesomeTimer:foo?delay=-1&repeatCount=10")
                        .to("mock:result");
            }
        };
    }
}