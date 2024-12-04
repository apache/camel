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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Using exclusive fixed replyTo queues should be faster as there is no need for JMSMessage selectors.
 */
public class JmsRequestReplyExclusiveReplyToComponentTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsRequestReplyExclusiveFixedReplyTo() {
        StopWatch watch = new StopWatch();

        assertEquals("Hello A", template.requestBody(
                "activemq:queue:JmsRequestReplyExclusiveReplyToComponentTest?replyTo=JmsRequestReplyExclusiveReplyToComponentTest.bar",
                "A"));
        assertEquals("Hello B", template.requestBody(
                "activemq:queue:JmsRequestReplyExclusiveReplyToComponentTest?replyTo=JmsRequestReplyExclusiveReplyToComponentTest.bar",
                "B"));
        assertEquals("Hello C", template.requestBody(
                "activemq:queue:JmsRequestReplyExclusiveReplyToComponentTest?replyTo=JmsRequestReplyExclusiveReplyToComponentTest.bar",
                "C"));
        assertEquals("Hello D", template.requestBody(
                "activemq:queue:JmsRequestReplyExclusiveReplyToComponentTest?replyTo=JmsRequestReplyExclusiveReplyToComponentTest.bar",
                "D"));
        assertEquals("Hello E", template.requestBody(
                "activemq:queue:JmsRequestReplyExclusiveReplyToComponentTest?replyTo=JmsRequestReplyExclusiveReplyToComponentTest.bar",
                "E"));

        long delta = watch.taken();
        assertTrue(delta < 4200, "Should be faster than about 4 seconds, was: " + delta);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        final JmsComponent jms = super.setupComponent(camelContext, service, componentName);
        // mark the reply to type as exclusive on the component
        jms.getConfiguration().setReplyToType(ReplyToType.Exclusive);
        return jms;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsRequestReplyExclusiveReplyToComponentTest")
                        .transform(body().prepend("Hello "));
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
