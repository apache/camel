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
package org.apache.camel.component.paho;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PahoOverrideTopicTest extends PahoTestSupport {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                PahoComponent paho = getContext().getComponent("paho", PahoComponent.class);
                paho.getConfiguration().setBrokerUrl("tcp://localhost:" + service.brokerPort());

                from("direct:test").to("paho:queue").log("Message sent");

                from("paho:myoverride").log("Message received").to("mock:test");
            }
        };
    }

    // Tests

    @Test
    public void shouldOverride() throws InterruptedException {
        // Given
        getMockEndpoint("mock:test").expectedMessageCount(1);

        // When
        template.sendBodyAndHeader("direct:test", "Hello World", PahoConstants.CAMEL_PAHO_OVERRIDE_TOPIC, "myoverride");

        // Then
        MockEndpoint.assertIsSatisfied(getCamelContextExtension().getContext());
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        template = getCamelContextExtension().getProducerTemplate();
        consumer = getCamelContextExtension().getConsumerTemplate();
    }
}
