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
package org.apache.camel.component.sjms.producer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the send of an InOut message fails after the reply has been registered, the exchange must be completed exactly
 * once: not a second time by the request timeout, and not a second time by the send failure when the request timeout or
 * the reply has already completed the exchange while the send was still running.
 */
public class InOutSendFailureCallbackTest extends JmsTestSupport {

    private static final String FAIL_QUEUE = "InOutSendFailureCallbackTest.fail";
    private static final String TIMEOUT_QUEUE = "InOutSendFailureCallbackTest.timeout";
    private static final String REPLY_QUEUE = "InOutSendFailureCallbackTest.reply";

    private static final AtomicInteger COMPLETED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    // the send to TIMEOUT_QUEUE and REPLY_QUEUE waits until the exchange has been completed
    private static volatile CountDownLatch exchangeDone = new CountDownLatch(1);

    public InOutSendFailureCallbackTest() {
        addSjmsComponent = false;
    }

    @BeforeEach
    void resetCounters() {
        COMPLETED.set(0);
        FAILED.set(0);
        exchangeDone = new CountDownLatch(1);
    }

    @Test
    public void testSendFailure() {
        Exchange result = template.send("direct:fail", ExchangePattern.InOut, e -> e.getIn().setBody("Hello"));

        Exception cause = result.getException();
        assertNotNull(cause);
        assertTrue(hasMessageInChain(cause, "Simulated send failure"), "Should fail with the send failure: " + cause);

        // the request timeout (500 ms) must not complete the exchange a second time
        await().during(1500, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertSame(cause, result.getException(), "The exception changed after the send failure");
            assertEquals(1, FAILED.get(), "onFailure calls");
        });
        assertCompletedOnce(0, 1);
    }

    @Test
    public void testTimeoutDuringFailingSend() {
        // the send blocks until the request timeout has completed the exchange, and then fails
        Exchange result = template.send("direct:timeout", ExchangePattern.InOut, e -> e.getIn().setBody("Hello"));

        assertInstanceOf(ExchangeTimedOutException.class, result.getException(),
                "Should fail with the timeout that completed the exchange, not with the later send failure");
        assertCompletedOnce(0, 1);
    }

    @Test
    public void testReplyDuringFailingSend() {
        // the request reaches the broker and is answered, and then the send fails
        Exchange result = template.send("direct:reply", ExchangePattern.InOut, e -> e.getIn().setBody("Hello"));

        assertNull(result.getException(), "The reply completed the exchange before the send failed");
        assertEquals("Bye World", result.getMessage().getBody(String.class));
        assertCompletedOnce(1, 0);
    }

    private static boolean hasMessageInChain(Throwable t, String message) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(message)) {
                return true;
            }
        }
        return false;
    }

    private void assertCompletedOnce(int expectedCompleted, int expectedFailed) {
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, context.getInflightRepository().size(), "inflight exchanges"));
        assertEquals(expectedCompleted, COMPLETED.get(), "onComplete calls");
        assertEquals(expectedFailed, FAILED.get(), "onFailure calls");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(createFailingSendConnectionFactory(connectionFactory));
        component.setRequestTimeoutCheckerInterval(50);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fail")
                        .process(InOutSendFailureCallbackTest::countCompletions)
                        .to(ExchangePattern.InOut, "sjms:queue:" + FAIL_QUEUE + "?requestTimeout=500");

                from("direct:timeout")
                        .process(InOutSendFailureCallbackTest::countCompletions)
                        .to(ExchangePattern.InOut, "sjms:queue:" + TIMEOUT_QUEUE + "?requestTimeout=100");

                from("direct:reply")
                        .process(InOutSendFailureCallbackTest::countCompletions)
                        .to(ExchangePattern.InOut, "sjms:queue:" + REPLY_QUEUE + "?requestTimeout=10000");

                from("sjms:queue:" + REPLY_QUEUE)
                        .setBody(constant("Bye World"));
            }
        };
    }

    private static void countCompletions(Exchange exchange) {
        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onComplete(Exchange exchange) {
                COMPLETED.incrementAndGet();
                exchangeDone.countDown();
            }

            @Override
            public void onFailure(Exchange exchange) {
                FAILED.incrementAndGet();
                exchangeDone.countDown();
            }
        });
    }

    private static ConnectionFactory createFailingSendConnectionFactory(ConnectionFactory delegate) {
        return proxyOf(ConnectionFactory.class, (proxy, method, args) -> {
            Object result = invoke(method, delegate, args);
            return result instanceof Connection connection ? wrapConnection(connection) : result;
        });
    }

    private static Connection wrapConnection(Connection delegate) {
        return proxyOf(Connection.class, (proxy, method, args) -> {
            Object result = invoke(method, delegate, args);
            return result instanceof Session session ? wrapSession(session) : result;
        });
    }

    private static Session wrapSession(Session delegate) {
        return proxyOf(Session.class, (proxy, method, args) -> {
            Object result = invoke(method, delegate, args);
            return result instanceof MessageProducer producer ? wrapProducer(producer) : result;
        });
    }

    private static MessageProducer wrapProducer(MessageProducer delegate) {
        return proxyOf(MessageProducer.class, (proxy, method, args) -> {
            if ("send".equals(method.getName())) {
                String queue = queueName(delegate.getDestination(), args);
                if (FAIL_QUEUE.equals(queue)) {
                    throw new JMSException("Simulated send failure: broker rejected message");
                } else if (TIMEOUT_QUEUE.equals(queue)) {
                    // a send that blocks longer than the request timeout, and then fails
                    awaitExchangeDone();
                    throw new JMSException("Simulated send failure after the request timeout");
                } else if (REPLY_QUEUE.equals(queue)) {
                    // the message is sent and answered, but the send reports a failure afterwards
                    invoke(method, delegate, args);
                    awaitExchangeDone();
                    throw new JMSException("Simulated send failure after the message was sent");
                }
            }
            return invoke(method, delegate, args);
        });
    }

    private static void awaitExchangeDone() throws InterruptedException {
        if (!exchangeDone.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The exchange was not completed while the send was in progress");
        }
    }

    private static String queueName(Destination producerDestination, Object[] args) throws JMSException {
        Destination destination = producerDestination;
        if (destination == null && args != null && args.length > 0 && args[0] instanceof Destination d) {
            destination = d;
        }
        return destination instanceof Queue queue ? queue.getQueueName() : null;
    }

    private static Object invoke(Method method, Object delegate, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyOf(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, handler);
    }
}
