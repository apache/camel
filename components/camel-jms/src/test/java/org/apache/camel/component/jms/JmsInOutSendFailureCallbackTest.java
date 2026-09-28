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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests that when a JMS send fails after the reply correlation has been registered, the AsyncCallback is invoked
 * exactly once: not a second time by the timeout handler (CAMEL-24073), and not a second time by the send failure when
 * the request timeout or the reply has already completed the exchange while the send was still running.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-24073">CAMEL-24073</a>
 */
public class JmsInOutSendFailureCallbackTest extends AbstractJMSTest {

    private static final String FAIL_QUEUE = "JmsInOutSendFailureCallbackTest";
    private static final String TIMEOUT_QUEUE = "JmsInOutSendFailureCallbackTest.timeout";
    private static final String REPLY_QUEUE = "JmsInOutSendFailureCallbackTest.reply";

    // counts the completions of the exchange, the send to TIMEOUT_QUEUE and REPLY_QUEUE waits for the first one
    private static final AtomicInteger COMPLETED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    private static volatile CountDownLatch exchangeDone = new CountDownLatch(1);

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    public void testCallbackInvokedOnceOnSendFailure() throws Exception {
        Exchange result = template.send("direct:JmsInOutSendFailureCallbackTest", ExchangePattern.InOut,
                p -> p.getIn().setBody("Hello"));

        assertNotNull(result.getException());
        assertFalse(result.getException() instanceof ExchangeTimedOutException,
                "Should fail with JMS send exception, not ExchangeTimedOutException");

        // wait past the requestTimeout (2s) and verify the timeout handler does not
        // overwrite the exception with ExchangeTimedOutException via a second callback
        Exception originalException = result.getException();
        await().during(3, TimeUnit.SECONDS)
                .atMost(4, TimeUnit.SECONDS)
                .untilAsserted(() -> assertSame(originalException, result.getException(),
                        "Exception changed after send failure - timeout handler fired a second callback"));
    }

    @Test
    public void testCallbackInvokedOnceWhenTimeoutFiresDuringFailingSend() throws Exception {
        // the send blocks until the request timeout has completed the exchange, and then fails
        Exchange result = template.send("direct:timeoutDuringSend", ExchangePattern.InOut,
                p -> p.getIn().setBody("Hello"));

        assertInstanceOf(ExchangeTimedOutException.class, result.getException(),
                "Should fail with the timeout that completed the exchange, not the later send failure");
        assertCompletedOnce(0, 1);
    }

    @Test
    public void testCallbackInvokedOnceWhenHandledTimeoutFiresDuringFailingSend() throws Exception {
        // the route handles the timeout, so the late send failure must not fail the exchange afterwards
        Exchange result = template.send("direct:timeoutDuringSendHandled", ExchangePattern.InOut,
                p -> p.getIn().setBody("Hello"));

        assertNull(result.getException(), "The timeout was handled by the route");
        assertEquals("Timed out", result.getMessage().getBody(String.class));
        assertCompletedOnce(1, 0);
    }

    @Test
    public void testCallbackInvokedOnceWhenReplyArrivesDuringFailingSend() throws Exception {
        // the request reaches the broker and is answered, and then the send fails
        Exchange result = template.send("direct:replyDuringSend", ExchangePattern.InOut,
                p -> p.getIn().setBody("Hello"));

        assertNull(result.getException(), "The reply completed the exchange before the send failed");
        assertEquals("Bye World", result.getMessage().getBody(String.class));
        assertCompletedOnce(1, 0);
    }

    private void assertCompletedOnce(int expectedCompleted, int expectedFailed) {
        assertEquals(expectedCompleted, COMPLETED.get(), "onComplete calls");
        assertEquals(expectedFailed, FAILED.get(), "onFailure calls");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, context.getInflightRepository().size(), "inflight exchanges"));
    }

    @Override
    public String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        ConnectionFactory failingCf = createFailingSendConnectionFactory(connectionFactory);
        return jmsComponentAutoAcknowledge(failingCf);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:JmsInOutSendFailureCallbackTest")
                        .to(ExchangePattern.InOut,
                                "activemq:queue:" + FAIL_QUEUE + "?requestTimeout=2000");

                from("direct:timeoutDuringSend")
                        .process(JmsInOutSendFailureCallbackTest::countCompletions)
                        .to(ExchangePattern.InOut,
                                "activemq:queue:" + TIMEOUT_QUEUE + "?requestTimeout=100&requestTimeoutCheckerInterval=50");

                from("direct:timeoutDuringSendHandled")
                        .process(JmsInOutSendFailureCallbackTest::countCompletions)
                        .doTry()
                            .to(ExchangePattern.InOut,
                                    "activemq:queue:" + TIMEOUT_QUEUE
                                                       + "?requestTimeout=100&requestTimeoutCheckerInterval=50")
                        .doCatch(ExchangeTimedOutException.class)
                            .setBody(constant("Timed out"))
                        .end();

                from("direct:replyDuringSend")
                        .process(JmsInOutSendFailureCallbackTest::countCompletions)
                        .to(ExchangePattern.InOut, "activemq:queue:" + REPLY_QUEUE + "?requestTimeout=10000");

                from("activemq:queue:" + REPLY_QUEUE)
                        .setBody(constant("Bye World"));
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
        COMPLETED.set(0);
        FAILED.set(0);
        exchangeDone = new CountDownLatch(1);
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
        return proxyOf(ConnectionFactory.class, delegate, (proxy, method, args) -> {
            Object result = method.invoke(delegate, args);
            return result instanceof Connection conn ? wrapConnection(conn) : result;
        });
    }

    private static Connection wrapConnection(Connection delegate) {
        return proxyOf(Connection.class, delegate, (proxy, method, args) -> {
            Object result = method.invoke(delegate, args);
            return result instanceof Session session ? wrapSession(session) : result;
        });
    }

    private static Session wrapSession(Session delegate) {
        return proxyOf(Session.class, delegate, (proxy, method, args) -> {
            Object result = method.invoke(delegate, args);
            return result instanceof MessageProducer producer ? wrapProducer(producer) : result;
        });
    }

    private static MessageProducer wrapProducer(MessageProducer delegate) {
        return proxyOf(MessageProducer.class, delegate, (proxy, method, args) -> {
            if ("send".equals(method.getName())) {
                String queue = queueName(delegate.getDestination());
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

    private static String queueName(Destination destination) throws JMSException {
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
    private static <T> T proxyOf(Class<T> iface, T delegate, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, handler);
    }
}
