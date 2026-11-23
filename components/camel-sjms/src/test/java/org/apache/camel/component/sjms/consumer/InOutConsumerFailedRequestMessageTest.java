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
package org.apache.camel.component.sjms.consumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer that fails on a message that expects a reply sends no reply: the failure it logs says so, as the caller
 * sees only its own timeout later.
 */
public class InOutConsumerFailedRequestMessageTest extends JmsTestSupport {

    private static final FailureCaptor CAPTOR = new FailureCaptor();

    @BeforeAll
    static void addCaptor() {
        ((Logger) LogManager.getLogger(SimpleMessageListenerContainer.class)).addAppender(CAPTOR);
    }

    @AfterAll
    static void removeCaptor() {
        ((Logger) LogManager.getLogger(SimpleMessageListenerContainer.class)).removeAppender(CAPTOR);
    }

    @Test
    public void testTheFailureOfARequestSaysThatNoReplyIsSent() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:reserve", "ORD-1003"));
        assertInstanceOf(ExchangeTimedOutException.class, e.getCause());

        String msg = CAPTOR.awaitFailure("not enough CAMEL-CAP");
        assertTrue(msg.startsWith("java.lang.IllegalStateException: not enough CAMEL-CAP. No reply is sent (JMSReplyTo "),
                msg);
        assertTrue(msg.contains("), as the exchange failed: the caller waits until its requestTimeout and then fails with"
                                + " ExchangeTimedOutException"),
                msg);
        assertTrue(msg.endsWith("To make the caller fail at once, reply with a message that says the request failed and"
                                + " check it in the caller"),
                msg);
        // no advice to send the exception back, which would be a serialized Java object (transferException)
        assertFalse(msg.contains("transferException"), msg);
    }

    @Test
    public void testTheFailureOfAMessageThatExpectsNoReplyIsAsBefore() {
        template.sendBody("sjms:queue:InOutConsumerFailedRequestMessageTest.event", "E-1");

        assertEquals("java.lang.IllegalStateException: bad event", CAPTOR.awaitFailure("bad event"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:queue:InOutConsumerFailedRequestMessageTest.stock")
                        .throwException(new IllegalStateException("not enough CAMEL-CAP"));
                from("sjms:queue:InOutConsumerFailedRequestMessageTest.event")
                        .throwException(new IllegalStateException("bad event"));
                from("direct:reserve").to("sjms:queue:InOutConsumerFailedRequestMessageTest.stock?requestTimeout=1000");
            }
        };
    }

    /**
     * The failures the listener container logs, with the exception it logs them with.
     */
    private static final class FailureCaptor extends AbstractAppender {
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();

        FailureCaptor() {
            super("InOutConsumerFailedRequestMessageTestCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            if (event.getThrown() != null) {
                failures.add(event.getThrown());
            }
        }

        /** The message of the logged failure that names the text, once it is logged. */
        String awaitFailure(String text) {
            await().atMost(5, TimeUnit.SECONDS).until(() -> find(text) != null);
            return find(text);
        }

        private String find(String text) {
            return failures.stream().map(Throwable::getMessage).filter(m -> m != null && m.contains(text)).findFirst()
                    .orElse(null);
        }
    }
}
