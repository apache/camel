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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Follow-up of CAMEL-26741 and CAMEL-26824: an Error that is not a VirtualMachineError, thrown by the aggregation
 * strategy of a split or multicast (also when its timeout is called), or by the iterator of a streaming split, fails
 * the exchange once, instead of escaping from the task of the multicast (to the caller, or lost on a thread of the
 * pool).
 */
class MulticastTaskThrowsErrorTest extends ContextTestSupport {

    private static final AggregationStrategy FAILING = (oldExchange, newExchange) -> {
        throw new AssertionError("aggregation failed");
    };

    // releases the slow part of the timeout test
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    void errorOfTheAggregationStrategyOfASplitFailsTheExchange() throws Exception {
        assertFailsWithAssertionError("direct:split", false);
    }

    @Test
    void errorOfTheAggregationStrategyOfAParallelSplitFailsTheExchange() throws Exception {
        assertFailsWithAssertionError("direct:parallelSplit", false);
    }

    @Test
    void errorOfTheAggregationStrategyOfAMulticastFailsTheExchange() throws Exception {
        assertFailsWithAssertionError("direct:multicast", false);
    }

    @Test
    void errorOfTheTimeoutOfTheAggregationStrategyFailsTheExchange() throws Exception {
        try {
            // the slow part is released once the split answered, so it can end before the inflight check
            assertFailsWithAssertionError("direct:timeout", false, release::countDown);
        } finally {
            release.countDown();
        }
    }

    @Test
    void errorOfTheIteratorOfAParallelStreamingSplitFailsTheExchange() throws Exception {
        assertFailsWithAssertionError("direct:parallelStreamingSplit", false);
    }

    @Test
    void errorOfTheIteratorOfAStreamingSplitFailsTheExchange() throws Exception {
        assertFailsWithAssertionError("direct:streamingSplit", false);
    }

    @Test
    void errorOfAProcessorOfATransactedSplitFailsTheExchange() throws Exception {
        // control: the step of the processor handles its Error already (CAMEL-26741), also in a transacted split
        assertFailsWithAssertionError("direct:transactedSplit", true);
    }

    @Test
    void errorOfTheIteratorOfATransactedStreamingSplitFailsTheExchange() throws Exception {
        // the transacted task now fails the exchange itself; without it, the transacted step around the split
        // (CAMEL-26866) fails the exchange with the Error too, so this case passes either way
        assertFailsWithAssertionError("direct:streamingSplit", true);
    }

    @Test
    void errorOfAPartOfATransactedMulticastFailsOnlyThatPart() throws Exception {
        assertOnlyTheTransactedPartFails(new AssertionError("part failed"));
    }

    @Test
    void exceptionOfAPartOfATransactedMulticastFailsOnlyThatPart() throws Exception {
        // control: an exception already failed only the part
        assertOnlyTheTransactedPartFails(new IllegalArgumentException("part failed"));
    }

    private void assertOnlyTheTransactedPartFails(Throwable failure) throws Exception {
        // the parts are not wrapped (in a unit of work, an error handler or a step, which handle an Error already),
        // so what the first part throws reaches the transacted task, as an Error that escapes the step of a part does
        List<String> sent = new CopyOnWriteArrayList<>();
        Processor first = e -> {
            if (failure instanceof Error error) {
                throw error;
            }
            throw (Exception) failure;
        };
        Processor second = e -> sent.add("second");
        MulticastProcessor multicast
                = new MulticastProcessor(context, null, List.of(first, second), new UseLatestAggregationStrategy()) {
                    @Override
                    protected Processor wrapInErrorHandler(Route route, Exchange exchange, Processor processor) {
                        return processor;
                    }
                };
        ServiceHelper.startService(multicast);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getExchangeExtension().setTransacted(true);
            multicast.process(exchange);

            assertEquals(List.of("second"), sent, "the part after the failed part must still be sent");
            assertTrue(causedBy(exchange.getException(), failure.getClass()),
                    "expected " + failure + " in the causes of " + exchange.getException());
        } finally {
            ServiceHelper.stopService(multicast);
        }
    }

    private void assertFailsWithAssertionError(String uri, boolean transacted) throws Exception {
        assertFailsWithAssertionError(uri, transacted, () -> {
        });
    }

    private void assertFailsWithAssertionError(String uri, boolean transacted, Runnable afterReply) throws Exception {
        Map<String, AtomicInteger> done = countDoneExchanges();
        // bounded: before, with parallelProcessing the exchange never completed
        Exchange out = template.asyncSend(uri, e -> {
            e.getMessage().setBody(List.of("a", "b", "c"));
            e.getExchangeExtension().setTransacted(transacted);
        }).get(10, TimeUnit.SECONDS);

        afterReply.run();
        assertNotNull(out.getException(), "the exchange must fail");
        assertTrue(causedBy(out.getException(), AssertionError.class), "expected an AssertionError in the causes of "
                                                                       + out.getException());
        // the future completes when the callback is called, the unit of work of the exchange ends right after
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, context.getInflightRepository().size(),
                        "the exchange must not stay inflight"));
        // done once: a second completion would fire a second event for the same exchange
        await().during(200, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, done.getOrDefault(out.getExchangeId(), new AtomicInteger()).get()));
    }

    private Map<String, AtomicInteger> countDoneExchanges() {
        Map<String, AtomicInteger> done = new ConcurrentHashMap<>();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                Exchange exchange = ((CamelEvent.ExchangeEvent) event).getExchange();
                done.computeIfAbsent(exchange.getExchangeId(), k -> new AtomicInteger()).incrementAndGet();
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent;
            }
        });
        return done;
    }

    private static boolean causedBy(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
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
                errorHandler(noErrorHandler());

                from("direct:split").split(body(), FAILING).to("mock:part").end();
                from("direct:parallelSplit").split(body(), FAILING).parallelProcessing().to("mock:part").end();
                from("direct:multicast").multicast(FAILING).to("mock:a", "mock:b").end();
                from("direct:slow").process(e -> release.await(20, TimeUnit.SECONDS));
                // the split is done by its timeout, while the second part still waits: the timeout of the strategy
                // throws
                from("direct:timeout").split(body(), new TimeoutFailing()).parallelProcessing().timeout(1000)
                        .choice()
                            .when(body().isEqualTo("b")).to("direct:slow")
                            .otherwise().to("mock:part")
                        .end()
                        .end();
                from("direct:parallelStreamingSplit")
                        .split(method(MulticastTaskThrowsErrorTest.class, "failingIterator")).streaming()
                        .parallelProcessing()
                        .to("mock:part")
                        .end();
                from("direct:streamingSplit")
                        .split(method(MulticastTaskThrowsErrorTest.class, "failingIterator")).streaming()
                        .to("mock:part")
                        .end();
                from("direct:transactedSplit").split(body())
                        .process(e -> {
                            throw new AssertionError("in a transacted split");
                        })
                        .end();
            }
        };
    }

    /** A strategy that aggregates, and whose timeout throws an AssertionError. */
    private static final class TimeoutFailing implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            return oldExchange != null ? oldExchange : newExchange;
        }

        @Override
        public void timeout(Exchange exchange, int index, int total, long timeout) {
            throw new AssertionError("timeout failed");
        }
    }

    /** An iterator whose second next() throws an AssertionError. */
    public static Iterator<String> failingIterator() {
        return new Iterator<>() {
            private int n;

            @Override
            public boolean hasNext() {
                return n < 3;
            }

            @Override
            public String next() {
                if (n++ == 1) {
                    throw new AssertionError("iterator failed");
                }
                return "part" + n;
            }
        };
    }
}
