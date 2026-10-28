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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.async.MyAsyncComponent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.SendHook;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many concurrent sends through {@code to}, {@code toD}, {@code recipientList} (with an asynchronous producer) and
 * {@code routingSlip}, with a {@link SendHook} that randomly stops, skips, delays or changes each send, and a dead
 * letter channel with redelivery: every exchange completes exactly once, and the hook runs after once for every before.
 */
class SendHookStressTest extends ContextTestSupport {

    private static final int SENDERS = 8;
    private static final int PER_SENDER = 250;

    private final AtomicLong before = new AtomicLong();
    private final AtomicLong after = new AtomicLong();
    private final AtomicLong stopped = new AtomicLong();
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
        context.getCamelContextExtension().setSendHook(new RandomHook());
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
                        .toD("mock:s${header.n}")
                        .recipientList(constant("mock:s4,async:bye:camel?delay=0"))
                        .routingSlip(constant("mock:s5,mock:s6"))
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
        assertEquals(before.get(), after.get());
        // the hook did stop and delay sends, so those paths were exercised
        assertTrue(stopped.get() > 0, "stopped " + stopped);
        assertTrue(delayed.get() > 0, "delayed " + delayed);
    }

    private final class RandomHook implements SendHook {

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            before.incrementAndGet();
            if (endpoint.getEndpointUri().startsWith("direct:")) {
                // the send of the test into the route: it must reach the route, so the exchange completes there
                return PROCEED;
            }
            int dice = ThreadLocalRandom.current().nextInt(10);
            switch (dice) {
                case 0, 1:
                    stopped.incrementAndGet();
                    exchange.setException(new IllegalStateException("Stopped by the send hook"));
                    return PROCEED;
                case 2:
                    return SKIP;
                case 3, 4:
                    delayed.incrementAndGet();
                    return 1 + ThreadLocalRandom.current().nextInt(3);
                case 5:
                    exchange.getMessage().setBody("changed");
                    return PROCEED;
                default:
                    return PROCEED;
            }
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            after.incrementAndGet();
        }
    }
}
