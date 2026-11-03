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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.async.MyAsyncComponent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.StepHook;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many concurrent exchanges through a route with steps that have an error handler (with redelivery and a dead letter
 * channel), steps without one (inside doTry, multicast), an asynchronous producer, split and choice, with a
 * {@link StepHook} that randomly fails or delays each step: every exchange completes exactly once.
 */
class StepHookStressTest extends ContextTestSupport {

    private static final int SENDERS = 8;
    private static final int PER_SENDER = 250;

    private final AtomicLong asked = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong delayed = new AtomicLong();
    // the exchanges sent by the test, and how often each completed (or failed)
    private final Map<String, AtomicInteger> completions = new ConcurrentHashMap<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent) {
                    AtomicInteger count = completions.get(((CamelEvent.ExchangeEvent) event).getExchange().getExchangeId());
                    if (count != null) {
                        count.incrementAndGet();
                    }
                }
            }
        });
        context.getCamelContextExtension().setStepHook(new RandomHook());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:stress")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0))
                        .to("mock:s1")
                        .to("async:bye:camel?delay=0")
                        .split(constant("a,b,c"))
                            .to("mock:item")
                        .end()
                        .doTry()
                            .to("mock:try")
                        .doCatch(IllegalStateException.class)
                            .to("mock:caught")
                        .end()
                        .multicast()
                            .to("mock:m1", "mock:m2")
                        .end()
                        .choice()
                            .when(header("n").isEqualTo(2)).to("mock:two")
                            .otherwise().to("mock:other")
                        .end()
                        .to("mock:end");
            }
        };
    }

    @Test
    void testEveryExchangeCompletesExactlyOnce() throws Exception {
        List<CompletableFuture<Exchange>> futures = new ArrayList<>();
        ExecutorService senders = Executors.newFixedThreadPool(SENDERS);
        try {
            List<Future<List<CompletableFuture<Exchange>>>> sent = new ArrayList<>();
            for (int t = 0; t < SENDERS; t++) {
                sent.add(senders.submit(() -> {
                    List<CompletableFuture<Exchange>> mine = new ArrayList<>();
                    for (int i = 0; i < PER_SENDER; i++) {
                        Exchange exchange = new DefaultExchange(context);
                        exchange.getMessage().setBody("Hello " + i);
                        exchange.getMessage().setHeader("n", 2 + (i % 2));
                        completions.put(exchange.getExchangeId(), new AtomicInteger());
                        mine.add(template.asyncSend("direct:stress", exchange));
                    }
                    return mine;
                }));
            }
            for (Future<List<CompletableFuture<Exchange>>> f : sent) {
                futures.addAll(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            senders.shutdownNow();
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        // stop the context: every thread that could complete an exchange a second time is done after this
        context.stop();

        assertEquals(SENDERS * PER_SENDER, completions.size());
        completions.forEach((id, count) -> assertEquals(1, count.get(), "completions of " + id));
        // the hook did fail and delay steps, so those paths were exercised
        assertTrue(failed.get() > 0, "failed " + failed);
        assertTrue(delayed.get() > 0, "delayed " + delayed);
        assertTrue(asked.get() > SENDERS * PER_SENDER, "asked " + asked);
    }

    private final class RandomHook implements StepHook {

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            asked.incrementAndGet();
            int dice = ThreadLocalRandom.current().nextInt(10);
            switch (dice) {
                case 0:
                    failed.incrementAndGet();
                    exchange.setException(new IllegalStateException("Failed by the step hook"));
                    return PROCEED;
                case 1, 2:
                    delayed.incrementAndGet();
                    return 1 + ThreadLocalRandom.current().nextInt(3);
                default:
                    return PROCEED;
            }
        }
    }
}
