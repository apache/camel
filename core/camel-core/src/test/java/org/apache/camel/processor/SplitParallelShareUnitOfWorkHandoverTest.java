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
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of a parallel split with shareUnitOfWork use the same unit of work at the same time, so a part that hands
 * over the on completions it added (such as sending to a seda endpoint) must not fail, or hand over an on completion
 * twice, when another part adds or hands over on completions at the same time.
 */
@Timeout(60)
class SplitParallelShareUnitOfWorkHandoverTest extends ContextTestSupport {

    private final CountingSynchronization consumer = new CountingSynchronization();
    private final CountingSynchronization part = new CountingSynchronization();
    // the on completions the seda parts add themselves (they are handed over to their seda copies)
    private final CountingSynchronization sedaPart = new CountingSynchronization();
    // the number of handovers of the consumer on completion that wait until the other part has done its work
    private final AtomicInteger pausedHandovers = new AtomicInteger();
    private final CountDownLatch handoverStarted = new CountDownLatch(1);
    private final CountDownLatch partAdded = new CountDownLatch(1);
    private final CyclicBarrier bothInHandover = new CyclicBarrier(2);
    private volatile boolean barrier;

    @Test
    void testHandoverWhileAnotherPartAddsOnCompletion() throws Exception {
        pausedHandovers.set(1);
        NotifyBuilder seda = new NotifyBuilder(context).from("seda:a").whenDone(1).create();

        Exchange out = template.send("direct:handoverAndAdd", e -> e.getIn().setBody("A,B"));

        assertNull(out.getException(), "the split must not fail");
        assertTrue(seda.matches(10, TimeUnit.SECONDS));
        // a part that sends to seda hands over the on completions it added to its copy, but not those of the parent
        assertEquals(List.of("handoverAndAdd"), consumer.completed,
                "the consumer on completion runs once, with the split exchange");
        assertEquals(List.of(), consumer.failed);
        assertEquals(List.of("seda"), sedaPart.completed,
                "the on completion of the seda part runs once, with its copy");
        assertEquals(List.of("handoverAndAdd"), part.completed,
                "the on completion of the part runs once, with the split exchange");
        assertEquals(List.of(), part.failed);
    }

    @Test
    void testTwoPartsHandoverAtTheSameTime() throws Exception {
        barrier = true;
        pausedHandovers.set(2);
        NotifyBuilder seda = new NotifyBuilder(context).from("seda:a").whenDone(2).create();

        Exchange out = template.send("direct:twoHandovers", e -> e.getIn().setBody("A,B"));

        assertNull(out.getException(), "the split must not fail");
        assertTrue(seda.matches(10, TimeUnit.SECONDS));
        assertEquals(List.of("twoHandovers"), consumer.completed,
                "the consumer on completion runs once, with the split exchange");
        assertEquals(List.of(), consumer.failed);
        assertEquals(List.of("seda", "seda"), sedaPart.completed,
                "the on completion of each seda part is handed over to its own copy only, and runs once");
    }

    @Test
    void testSequentialSplit() throws Exception {
        // control: one part at a time
        NotifyBuilder seda = new NotifyBuilder(context).from("seda:a").whenDone(2).create();

        Exchange out = template.send("direct:sequential", e -> e.getIn().setBody("A,B"));

        assertNull(out.getException());
        assertTrue(seda.matches(10, TimeUnit.SECONDS));
        assertEquals(List.of("sequential"), consumer.completed);
        assertEquals(List.of(), consumer.failed);
        assertEquals(List.of("seda", "seda"), sedaPart.completed);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the consumer on completion is added before the split (such as by a file or JMS consumer)
                from("direct:handoverAndAdd").routeId("handoverAndAdd")
                        .process(e -> e.getExchangeExtension().addOnCompletion(consumer))
                        .split(body().tokenize(",")).parallelProcessing().shareUnitOfWork()
                        .choice()
                        .when(body().isEqualTo("A")).process(this::addSedaPartOnCompletion).to("seda:a")
                        .otherwise().process(e -> {
                            // another part adds an on completion while part A hands over
                            awaitOtherPart(handoverStarted);
                            e.getExchangeExtension().addOnCompletion(part);
                            partAdded.countDown();
                        })
                        .end()
                        .end()
                        .to("mock:result");

                from("direct:twoHandovers").routeId("twoHandovers")
                        .process(e -> e.getExchangeExtension().addOnCompletion(consumer))
                        .split(body().tokenize(",")).parallelProcessing().shareUnitOfWork()
                            .process(this::addSedaPartOnCompletion)
                            .to("seda:a")
                        .end()
                        .to("mock:result");

                from("direct:sequential").routeId("sequential")
                        .process(e -> e.getExchangeExtension().addOnCompletion(consumer))
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .process(this::addSedaPartOnCompletion)
                            .to("seda:a")
                        .end()
                        .to("mock:result");

                from("seda:a").routeId("seda").to("mock:a");
            }

            private void addSedaPartOnCompletion(Exchange exchange) {
                // a new instance per part, which records in the same lists
                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onComplete(Exchange exchange) {
                        sedaPart.onComplete(exchange);
                    }

                    @Override
                    public void onFailure(Exchange exchange) {
                        sedaPart.onFailure(exchange);
                    }
                });
            }
        };
    }

    private static void awaitOtherPart(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for the other part");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private final class CountingSynchronization extends SynchronizationAdapter {
        // the routes of the exchanges it ran with
        private final List<String> completed = new CopyOnWriteArrayList<>();
        private final List<String> failed = new CopyOnWriteArrayList<>();

        @Override
        public void onComplete(Exchange exchange) {
            completed.add(exchange.getFromRouteId());
        }

        @Override
        public void onFailure(Exchange exchange) {
            failed.add(exchange.getFromRouteId());
        }

        @Override
        public boolean allowHandover() {
            // the handover of a seda part asks every on completion of the shared unit of work (also this one of the
            // consumer, which it then does not hand over) whether it may be handed over, so it can hold the handover
            // at that point until the other part has done its work (the handover calls this without holding the
            // lock of the unit of work, so the other part can add or hand over meanwhile)
            if (this == consumer && pausedHandovers.getAndDecrement() > 0) {
                if (barrier) {
                    try {
                        bothInHandover.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                } else {
                    handoverStarted.countDown();
                    awaitOtherPart(partAdded);
                }
            }
            return true;
        }
    }
}
