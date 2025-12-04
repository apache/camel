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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmsRequestReplySharedReplyToTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsRequestReplySharedReplyTo() {
        StopWatch watch = new StopWatch();

        // shared is more slower than exclusive, due it need to use a JMS Message Selector
        // and has a receiveTimeout of 1 sec per default, so it react slower to new messages

        assertEquals(
                "Hello A",
                template.requestBody(
                        "activemq:queue:JmsRequestReplySharedReplyToTest?replyTo=JmsRequestReplySharedReplyToTest.reply&replyToType=Shared",
                        "A"));
        assertEquals(
                "Hello B",
                template.requestBody(
                        "activemq:queue:JmsRequestReplySharedReplyToTest?replyTo=JmsRequestReplySharedReplyToTest.reply&replyToType=Shared",
                        "B"));
        assertEquals(
                "Hello C",
                template.requestBody(
                        "activemq:queue:JmsRequestReplySharedReplyToTest?replyTo=JmsRequestReplySharedReplyToTest.reply&replyToType=Shared",
                        "C"));
        assertEquals(
                "Hello D",
                template.requestBody(
                        "activemq:queue:JmsRequestReplySharedReplyToTest?replyTo=JmsRequestReplySharedReplyToTest.reply&replyToType=Shared",
                        "D"));
        assertEquals(
                "Hello E",
                template.requestBody(
                        "activemq:queue:JmsRequestReplySharedReplyToTest?replyTo=JmsRequestReplySharedReplyToTest.reply&replyToType=Shared",
                        "E"));

        long delta = watch.taken();
        assertTrue(delta > 2000, "Should be slower than about 2 seconds, was: " + delta);
    }

    /**
     * When setting the 'correlationProperty' option we decide which request header will hold the correlation id that
     * would later be used by the reply listener as a selector in form of 'JMSCorrelationID=<correlation id>'. Setting
     * the 'replyCorrelationProperty' option would instead allow to change that reply listener's selector to
     * '<replyCorrelationProperty>=<correlation id>'
     */
    String correlationProperty = "CustomID";

    String replyCorrelationProperty = "CustomCorrelationID";

    @Test
    public void testCustomCorrelation() throws Exception {

        Exchange out = template.request(
                "activemq:queue:JmsRequestReplySharedReplyToWithCustomCorrelationTest"
                        + "?replyTo=JmsRequestReplySharedReplyToWithCustomCorrelationTest.reply"
                        + "&correlationProperty="
                        + correlationProperty + "&replyCorrelationProperty="
                        + replyCorrelationProperty,
                exchange -> {
                    Message in = exchange.getIn();
                    in.setBody("A");
                });

        assertNotNull(out);
        assertNotNull(out.getMessage().getHeader(replyCorrelationProperty));
        assertEquals(
                out.getMessage().getHeader(replyCorrelationProperty),
                out.getMessage().getHeader(correlationProperty));
        assertEquals("Hello A", out.getMessage().getBody());
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
                from("activemq:queue:JmsRequestReplySharedReplyToTest").transform(body().prepend("Hello "));

                from("activemq:queue:JmsRequestReplySharedReplyToWithCustomCorrelationTest")
                        .transform(body().prepend("Hello "))
                        .setHeader(replyCorrelationProperty, header(correlationProperty));
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
