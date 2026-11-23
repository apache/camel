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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.ErrorHandler;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer that fails on a message that expects a reply sends no reply: the failure it logs says so, as the caller
 * sees only its own timeout later.
 */
public class JmsInOutFailedRequestMessageTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();
    protected ProducerTemplate template;

    private final List<Throwable> requestFailures = new CopyOnWriteArrayList<>();
    private final List<Throwable> eventFailures = new CopyOnWriteArrayList<>();

    @Test
    public void testTheFailureOfARequestSaysThatNoReplyIsSent() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:reserve", "ORD-1003"));
        assertInstanceOf(ExchangeTimedOutException.class, e.getCause());

        await().atMost(5, TimeUnit.SECONDS).until(() -> !requestFailures.isEmpty());
        Throwable failure = requestFailures.get(0);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        String msg = failure.getMessage();
        assertTrue(msg.startsWith("java.lang.IllegalStateException: not enough CAMEL-CAP. No reply is sent (JMSReplyTo "),
                msg);
        assertTrue(msg.contains("), as the exchange failed: the caller waits until its requestTimeout and then fails with"
                                + " ExchangeTimedOutException"),
                msg);
        assertTrue(msg.contains("reply with a message that says the request failed and check it in the caller"), msg);
        assertTrue(msg.endsWith("set transferException=true to send the exception back (with objectMessageEnabled=true"
                                + " on both sides)"),
                msg);
    }

    @Test
    public void testTheFailureOfAMessageThatExpectsNoReplyIsAsBefore() {
        template.sendBody("activemq:queue:JmsInOutFailedRequestMessageTest.event", "E-1");

        await().atMost(5, TimeUnit.SECONDS).until(() -> !eventFailures.isEmpty());
        assertEquals("java.lang.IllegalStateException: bad event", eventFailures.get(0).getMessage());
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
                getContext().getRegistry().bind("requestFailures", (ErrorHandler) requestFailures::add);
                getContext().getRegistry().bind("eventFailures", (ErrorHandler) eventFailures::add);

                from("activemq:queue:JmsInOutFailedRequestMessageTest.stock?errorHandler=#requestFailures")
                        .throwException(new IllegalStateException("not enough CAMEL-CAP"));
                from("activemq:queue:JmsInOutFailedRequestMessageTest.event?errorHandler=#eventFailures")
                        .throwException(new IllegalStateException("bad event"));
                from("direct:reserve").to("activemq:queue:JmsInOutFailedRequestMessageTest.stock?requestTimeout=1000");
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
    }
}
