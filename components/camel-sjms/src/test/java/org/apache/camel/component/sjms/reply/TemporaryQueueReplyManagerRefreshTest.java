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
package org.apache.camel.component.sjms.reply;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.JMSException;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.consumer.SimpleMessageListenerContainer;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After the connection of the temporary reply queue's listener fails, an InOut request waits for the new temporary
 * queue before it is sent. It is not registered yet, so neither its request timeout nor the stop of the reply manager
 * completes it: the wait itself must end when the reply manager stops, and after the request timeout.
 */
class TemporaryQueueReplyManagerRefreshTest extends JmsTestSupport {

    private Thread sender;

    @AfterEach
    void interruptSender() {
        if (sender != null) {
            sender.interrupt();
        }
    }

    @Test
    void testStopReleasesRequestWaitingForNewTemporaryQueue() throws Exception {
        SjmsProducer producer = startProducer("sjms:queue:TemporaryQueueRefresh.stop?requestTimeout=60000");
        // the connection failed, and no recovery creates a new temporary queue
        scheduleRefresh(producer);

        Exchange exchange = inOut("Hello");
        AtomicInteger done = sendAsync(producer, exchange);
        awaitWaitingForTemporaryQueue();

        ServiceHelper.stopService(producer);

        await().atMost(5, TimeUnit.SECONDS).until(() -> done.get() > 0);
        assertEquals(1, done.get());
        assertInstanceOf(IllegalStateException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().contains("stopped"), exchange.getException().getMessage());
    }

    @Test
    void testRequestFailsWhenNoNewTemporaryQueueWithinRequestTimeout() throws Exception {
        SjmsProducer producer = startProducer("sjms:queue:TemporaryQueueRefresh.timeout?requestTimeout=1000");
        scheduleRefresh(producer);

        Exchange exchange = inOut("Hello");
        AtomicInteger done = sendAsync(producer, exchange);

        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() > 0);
        assertEquals(1, done.get());
        assertInstanceOf(IllegalStateException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().contains("request timeout"), exchange.getException().getMessage());
        ServiceHelper.stopService(producer);
    }

    @Test
    void testRequestIsSentWhenTheRecoveryCreatesTheNewTemporaryQueue() throws Exception {
        SjmsProducer producer
                = startProducer("sjms:queue:TemporaryQueueRefresh.recover?requestTimeout=3000&recoveryInterval=200");
        TemporaryQueueReplyManager replyManager = replyManager(producer);
        // the connection fails: the exception listener wants a refresh, and the recovery creates the new queue
        ((SimpleMessageListenerContainer) replyManager.listenerContainer)
                .onException(new JMSException("simulated connection failure"));

        Exchange exchange = inOut("Hello");
        AtomicInteger done = sendAsync(producer, exchange);

        // the recovery runs after 1 second, then the request is sent; nothing replies, so it times out as usual
        await().atMost(15, TimeUnit.SECONDS).until(() -> done.get() > 0);
        assertEquals(1, done.get());
        assertInstanceOf(ExchangeTimedOutException.class, exchange.getException());
        ServiceHelper.stopService(producer);
    }

    private SjmsProducer startProducer(String uri) throws Exception {
        SjmsProducer producer = (SjmsProducer) context.getEndpoint(uri).createProducer();
        ServiceHelper.startService(producer);
        // a first request creates the reply manager and its temporary queue; nothing replies, so it times out
        Exchange first = inOut("first");
        first.getIn().setHeader(SjmsConstants.JMS_REQUEST_TIMEOUT, 200L);
        AtomicInteger done = new AtomicInteger();
        ((AsyncProcessor) producer).process(first, doneSync -> done.incrementAndGet());
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() == 1);
        return producer;
    }

    private Exchange inOut(String body) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody(body);
        return exchange;
    }

    private AtomicInteger sendAsync(SjmsProducer producer, Exchange exchange) {
        AtomicInteger done = new AtomicInteger();
        sender = new Thread(
                () -> ((AsyncProcessor) producer).process(exchange, doneSync -> done.incrementAndGet()),
                "TemporaryQueueRefreshSender");
        sender.start();
        return done;
    }

    private void awaitWaitingForTemporaryQueue() {
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            for (StackTraceElement element : sender.getStackTrace()) {
                if ("destinationReady".equals(element.getMethodName())) {
                    return sender.getState() == Thread.State.WAITING || sender.getState() == Thread.State.TIMED_WAITING;
                }
            }
            return false;
        });
    }

    private static TemporaryQueueReplyManager replyManager(SjmsProducer producer) throws Exception {
        Field field = SjmsProducer.class.getDeclaredField("replyManager");
        field.setAccessible(true);
        return (TemporaryQueueReplyManager) field.get(producer);
    }

    /**
     * What the exception listener of the reply listener does when its connection fails, without the recovery that would
     * create the new queue.
     */
    private static void scheduleRefresh(SjmsProducer producer) throws Exception {
        Object resolver = replyManager(producer).destResolver;
        Method method = resolver.getClass().getDeclaredMethod("scheduleRefresh");
        method.setAccessible(true);
        method.invoke(resolver);
    }
}
