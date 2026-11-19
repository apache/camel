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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error of a request that gets no reply says why the producer waited for a reply, and how to send without waiting.
 */
public class JmsInOutTimeoutMessageTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testTimeoutMessageSaysWhyTheProducerWaitedForAReply() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:noReply", "Hello"));

        ExchangeTimedOutException timeout = assertInstanceOf(ExchangeTimedOutException.class, e.getCause());
        assertEquals(500, timeout.getTimeout());
        String msg = timeout.getMessage();
        assertTrue(msg.startsWith("The OUT message was not received within: 500 millis due reply message with correlationID: "),
                msg);
        assertTrue(msg.contains(" not received on destination: "), msg);
        assertTrue(msg.contains("the exchange pattern is InOut"), msg);
        assertTrue(msg.contains("increase requestTimeout"), msg);
        assertTrue(msg.contains("exchangePattern=InOnly or disableReplyTo=true. Exchange["), msg);
    }

    @Test
    public void testInOnlyExchangeDoesNotWaitForAReply() {
        template.sendBody("direct:inOnly", "Hello");

        assertEquals("Hello", consumer.receiveBody("activemq:queue:JmsInOutTimeoutMessageTest.inOnly", 5000, String.class));
    }

    @Test
    public void testInOnlyPatternOnTheEndpointDoesNotWaitForAReply() {
        // an InOut exchange, as from an HTTP consumer, sent with the InOnly pattern
        Object reply = template.requestBody("direct:inOnlyPattern", "Hello");

        assertEquals("Hello", reply);
        assertEquals("Hello",
                consumer.receiveBody("activemq:queue:JmsInOutTimeoutMessageTest.inOnlyPattern", 5000, String.class));
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
                from("direct:noReply").to("activemq:queue:JmsInOutTimeoutMessageTest.noReply?requestTimeout=500");
                from("direct:inOnly").to("activemq:queue:JmsInOutTimeoutMessageTest.inOnly?requestTimeout=500");
                from("direct:inOnlyPattern").to(ExchangePattern.InOnly,
                        "activemq:queue:JmsInOutTimeoutMessageTest.inOnlyPattern?requestTimeout=500");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
