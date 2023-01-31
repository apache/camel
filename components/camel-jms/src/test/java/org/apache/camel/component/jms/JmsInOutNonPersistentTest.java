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

import jakarta.jms.DeliveryMode;

import org.apache.camel.CamelContext;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsInOutNonPersistentTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testInOutNonPersistent() throws Exception {
        getMockEndpoint("mock:JmsInOutNonPersistentTest.foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:JmsInOutNonPersistentTest.foo").expectedHeaderReceived("JMSDeliveryMode",
                DeliveryMode.NON_PERSISTENT);
        getMockEndpoint("mock:done").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:done").expectedHeaderReceived("JMSDeliveryMode", DeliveryMode.NON_PERSISTENT);

        String reply = template.requestBody("direct:start", "World", String.class);
        assertEquals("Bye World", reply);

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
                        .to("activemq:queue:JmsInOutNonPersistentTest.foo?replyTo=queue:JmsInOutNonPersistentTest.bar&deliveryPersistent=false")
                        .to("log:done?showAll=true", "mock:done");

                from("activemq:queue:JmsInOutNonPersistentTest.foo?replyToDeliveryPersistent=false&preserveMessageQos=true")
                        .to("log:JmsInOutNonPersistentTest.foo?showAll=true", "mock:JmsInOutNonPersistentTest.foo")
                        .transform(body().prepend("Bye "));
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
