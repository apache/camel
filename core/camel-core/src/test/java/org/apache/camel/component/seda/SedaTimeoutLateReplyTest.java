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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reply that arrives while the seda producer times out must either be returned to the caller, or be ignored. It must
 * never be copied into the caller's exchange after the producer has returned with the timeout.
 */
public class SedaTimeoutLateReplyTest extends ContextTestSupport {

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
            if (armed.getAndSet(false)) {
                copyStarted.countDown();
                try {
                    releaseCopy.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return this;
        }
    }

    @Test
    public void testReplyBeingCopiedWhenTimeoutOccurs() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> caller = new AtomicReference<>();
        try {
            Future<Exchange> future = executor.submit(() -> {
                caller.set(Thread.currentThread());
                Exchange exchange = context.getEndpoint("seda:reply").createExchange(ExchangePattern.InOut);
                exchange.getMessage().setBody("request");
                return template.send("seda:reply?timeout=250", exchange);
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
        } finally {
            releaseCopy.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testReplyAfterTimeoutIsIgnored() throws Exception {
        Exchange exchange = context.getEndpoint("seda:late").createExchange(ExchangePattern.InOut);
        exchange.getMessage().setBody("request");
        Exchange out = template.send("seda:late?timeout=100", exchange);
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
                from("seda:reply").routeId("reply")
                        .setBody(constant("reply"))
                        // copying the reply into the caller's exchange pauses in the middle of the copy
                        .process(e -> e.getExchangeExtension().setSafeCopyProperty("pause", new PauseCopy()));

                from("seda:late").routeId("late")
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
