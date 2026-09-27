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
package org.apache.camel.component.seda;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seda producer that is interrupted while it waits must fail the exchange, and not report the send as successful.
 */
public class SedaProducerInterruptedTest extends ContextTestSupport {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch releaseConsumer = new CountDownLatch(1);
    private final CountDownLatch consumerDone = new CountDownLatch(1);

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        releaseConsumer.countDown();
        executor.shutdownNow();
        super.tearDown();
    }

    @Test
    public void testInterruptedWhileBlockedWhenFull() throws Exception {
        assertInterruptedWhileAddingToQueue("seda:full?size=1&blockWhenFull=true");
    }

    @Test
    public void testInterruptedWhileBlockedWhenFullWithOfferTimeout() throws Exception {
        assertInterruptedWhileAddingToQueue("seda:full?size=1&blockWhenFull=true&offerTimeout=20000");
    }

    private void assertInterruptedWhileAddingToQueue(String uri) throws Exception {
        // the queue is full (and has no consumer)
        template.sendBody(uri, "A");

        Exchange exchange = context.getEndpoint(uri).createExchange(ExchangePattern.InOnly);
        exchange.getMessage().setBody("B");
        Exchange out = sendAndInterrupt(uri, exchange);

        RejectedExecutionException e = assertInstanceOf(RejectedExecutionException.class, out.getException());
        assertInstanceOf(InterruptedException.class, e.getCause());
        // B was not added to the queue
        List<Object> bodies = new ArrayList<>();
        for (Exchange queued : context.getEndpoint(uri, SedaEndpoint.class).getQueue()) {
            bodies.add(queued.getMessage().getBody());
        }
        assertEquals(List.of("A"), bodies);
    }

    @Test
    public void testInterruptedWhileWaitingForReply() throws Exception {
        Exchange exchange = context.getEndpoint("seda:slow").createExchange(ExchangePattern.InOut);
        exchange.getMessage().setBody("request");
        Exchange out = sendAndInterrupt("seda:slow?timeout=0", exchange);

        // the request must not be returned as the reply
        assertInstanceOf(InterruptedException.class, out.getException());

        // and the reply from the consumer is ignored when it completes later
        releaseConsumer.countDown();
        assertTrue(consumerDone.await(10, TimeUnit.SECONDS));
        assertInstanceOf(InterruptedException.class, out.getException());
        assertEquals("request", out.getMessage().getBody());
    }

    private Exchange sendAndInterrupt(String uri, Exchange exchange) throws Exception {
        AtomicReference<Thread> sender = new AtomicReference<>();
        Future<Exchange> future = executor.submit(() -> {
            sender.set(Thread.currentThread());
            return template.send(uri, exchange);
        });
        // wait until the sender is blocked in the seda producer
        await().atMost(10, TimeUnit.SECONDS).until(() -> isWaitingInSedaProducer(sender.get()));
        sender.get().interrupt();
        return future.get(10, TimeUnit.SECONDS);
    }

    private static boolean isWaitingInSedaProducer(Thread thread) {
        if (thread == null
                || thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            return false;
        }
        for (StackTraceElement element : thread.getStackTrace()) {
            if (SedaProducer.class.getName().equals(element.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:slow").routeId("slow")
                        .process(e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                consumerDone.countDown();
                            }
                        }))
                        .process(e -> releaseConsumer.await(20, TimeUnit.SECONDS))
                        .setBody(constant("reply"));
            }
        };
    }
}
