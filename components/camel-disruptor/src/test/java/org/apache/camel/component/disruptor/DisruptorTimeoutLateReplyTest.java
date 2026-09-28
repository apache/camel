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
package org.apache.camel.component.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reply that arrives while the disruptor producer times out, or is interrupted, must either be returned to the
 * caller, or be ignored. It must never be copied into the caller's exchange after the producer has returned with the
 * timeout (or the interruption).
 */
class DisruptorTimeoutLateReplyTest extends CamelTestSupport {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch copyStarted = new CountDownLatch(1);
    private final CountDownLatch releaseCopy = new CountDownLatch(1);
    private final CountDownLatch releaseConsumer = new CountDownLatch(1);
    private final CountDownLatch consumerDone = new CountDownLatch(1);

    /**
     * Pauses the thread which copies the reply into the caller's exchange, in the middle of the copy.
     */
    private final class PauseCopy implements SafeCopyProperty {
        private final AtomicBoolean armed = new AtomicBoolean(true);

        @Override
        public SafeCopyProperty safeCopy() {
            // the consumer copies the exchange as well, only pause when the producer's reply copy is in progress
            if (isCopyingReply() && armed.getAndSet(false)) {
                copyStarted.countDown();
                try {
                    releaseCopy.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return this;
        }

        private static boolean isCopyingReply() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (element.getClassName().startsWith(DisruptorProducer.class.getName())) {
                    return true;
                }
            }
            return false;
        }
    }

    @AfterEach
    void releaseAndShutdown() {
        releaseCopy.countDown();
        releaseConsumer.countDown();
        executor.shutdownNow();
    }

    @Test
    void testReplyBeingCopiedWhenTimeoutOccurs() throws Exception {
        AtomicReference<Thread> caller = new AtomicReference<>();
        Future<Exchange> future = executor.submit(() -> {
            caller.set(Thread.currentThread());
            Exchange exchange = context.getEndpoint("disruptor:reply").createExchange(ExchangePattern.InOut);
            exchange.getMessage().setBody("request");
            return template.send("disruptor:reply?timeout=1000", exchange);
        });

        // the consumer is copying its reply into the caller's exchange
        assertTrue(copyStarted.await(10, TimeUnit.SECONDS));
        // let the timeout occur while the copy is in progress: either the producer returns
        // or it waits (without timeout) for the copy to complete
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> future.isDone() || caller.get().getState() == Thread.State.WAITING);
        boolean returnedBeforeCopyCompleted = future.isDone();
        releaseCopy.countDown();

        Exchange out = future.get(10, TimeUnit.SECONDS);
        // the reply won the race, so the caller gets the complete reply and no timeout
        assertFalse(returnedBeforeCopyCompleted, "Producer returned while the reply was copied into the exchange");
        assertNull(out.getException());
        assertEquals("reply", out.getMessage().getBody());
    }

    @Test
    void testReplyBeingCopiedWhenInterrupted() throws Exception {
        AtomicReference<Thread> caller = new AtomicReference<>();
        AtomicBoolean interruptedAfterSend = new AtomicBoolean();
        Future<Exchange> future = executor.submit(() -> {
            caller.set(Thread.currentThread());
            Exchange exchange = context.getEndpoint("disruptor:reply").createExchange(ExchangePattern.InOut);
            exchange.getMessage().setBody("request");
            Exchange answer = template.send("disruptor:reply?timeout=0", exchange);
            interruptedAfterSend.set(Thread.currentThread().isInterrupted());
            return answer;
        });

        // the consumer is copying its reply into the caller's exchange
        assertTrue(copyStarted.await(10, TimeUnit.SECONDS));
        // interrupt the producer while the copy is in progress: either the producer returns
        // or it waits (uninterruptibly) for the copy to complete
        caller.get().interrupt();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> future.isDone() || isWaitingForCopy(caller.get()));
        boolean returnedBeforeCopyCompleted = future.isDone();
        releaseCopy.countDown();

        Exchange out = future.get(10, TimeUnit.SECONDS);
        // the reply won the race, so the caller gets the complete reply, and the interrupt status is kept
        assertFalse(returnedBeforeCopyCompleted, "Producer returned while the reply was copied into the exchange");
        assertNull(out.getException());
        assertEquals("reply", out.getMessage().getBody());
        assertTrue(interruptedAfterSend.get(), "The interrupt status of the caller should be kept");
    }

    private static boolean isWaitingForCopy(Thread thread) {
        if (thread.getState() != Thread.State.WAITING) {
            return false;
        }
        for (StackTraceElement element : thread.getStackTrace()) {
            if (DisruptorProducer.class.getName().equals(element.getClassName())
                    && "awaitUninterruptibly".equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testReplyAfterTimeoutIsIgnored() throws Exception {
        Exchange exchange = context.getEndpoint("disruptor:late").createExchange(ExchangePattern.InOut);
        exchange.getMessage().setBody("request");
        Exchange out = template.send("disruptor:late?timeout=100", exchange);
        assertInstanceOf(ExchangeTimedOutException.class, out.getException());

        // now the consumer completes after the timeout
        releaseConsumer.countDown();
        assertTrue(consumerDone.await(10, TimeUnit.SECONDS));

        assertInstanceOf(ExchangeTimedOutException.class, out.getException());
        assertEquals("request", out.getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:reply").routeId("reply")
                        .setBody(constant("reply"))
                        // copying the reply into the caller's exchange pauses in the middle of the copy
                        .process(e -> e.getExchangeExtension().setSafeCopyProperty("pause", new PauseCopy()));

                from("disruptor:late").routeId("late")
                        .process(e -> releaseConsumer.await(20, TimeUnit.SECONDS))
                        .setBody(constant("late reply"))
                        .process(e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                consumerDone.countDown();
                            }
                        }));
            }
        };
    }
}
