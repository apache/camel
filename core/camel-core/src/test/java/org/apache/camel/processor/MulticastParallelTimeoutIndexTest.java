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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A parallel multicast with a timeout must report to {@link AggregationStrategy#timeout} the index of each branch that
 * did not complete, and never the index of a branch that was aggregated. In streaming mode the branches are aggregated
 * in the order they complete, so the aggregated indexes are not always the first ones. A nested multicast changes the
 * index property of a branch, so the reported indexes must not depend on it.
 */
class MulticastParallelTimeoutIndexTest extends ContextTestSupport {

    // holds the branch that must time out until the test is done
    private final CountDownLatch release = new CountDownLatch(1);
    private final IndexStrategy strategy = new IndexStrategy();

    @AfterEach
    void releaseTimedOutBranch() {
        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);
    }

    @Test
    void testStreamingFirstBranchTimesOut() {
        String body = template.requestBody("direct:streamingFirst", "x", String.class);

        assertEquals(List.of(0), strategy.timedOut, "The branch that timed out is index 0");
        assertEquals(Set.of(1, 2), Set.copyOf(strategy.aggregated));
        assertEquals(Set.of("B", "C"), Set.copyOf(List.of(body.split(","))));
    }

    @Test
    void testStreamingMiddleBranchTimesOut() {
        String body = template.requestBody("direct:streamingMiddle", "x", String.class);

        assertEquals(List.of(1), strategy.timedOut, "The branch that timed out is index 1");
        assertEquals(Set.of(0, 2), Set.copyOf(strategy.aggregated));
        assertEquals(Set.of("B", "C"), Set.copyOf(List.of(body.split(","))));
    }

    @Test
    void testOrderedFirstBranchTimesOut() {
        String body = template.requestBody("direct:ordered", "x", String.class);

        assertEquals(List.of(0), strategy.timedOut, "The branch that timed out is index 0");
        assertEquals(List.of(1, 2), strategy.aggregated);
        assertEquals("B,C", body);
    }

    @Test
    void testStreamingFirstBranchTimesOutWithNestedMulticast() {
        String body = template.requestBody("direct:streamingNested", "x", String.class);

        // the nested multicasts copy the index property of their last branch (2) to branches 1 and 2
        assertEquals(List.of(0), strategy.timedOut, "The branch that timed out is index 0");
        assertEquals(Set.of("B", "C"), Set.copyOf(List.of(body.split(","))));
    }

    @Test
    void testOrderedLastBranchTimesOutAfterNestedMulticast() {
        String body = template.requestBody("direct:orderedNested", "x", String.class);

        // the nested multicasts copy the index property of their last branch (2) to branches 0 and 1
        assertEquals(List.of(2), strategy.timedOut, "The branch that timed out is index 2");
        assertEquals("B,C", body);
    }

    @Test
    void testOrderedMiddleBranchTimesOutBeforeNestedMulticast() {
        String body = template.requestBody("direct:orderedNestedLast", "x", String.class);

        // the last branch completes while the middle branch runs, and its nested multicast leaves index 4 on it
        assertEquals(List.of(1), strategy.timedOut, "The branch that timed out is index 1");
        assertEquals("B,C", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:streamingFirst")
                        .multicast(strategy).parallelProcessing().streaming().timeout(2000)
                        .to("direct:a", "direct:b", "direct:c");

                from("direct:streamingMiddle")
                        .multicast(strategy).parallelProcessing().streaming().timeout(2000)
                        .to("direct:b", "direct:a", "direct:c");

                from("direct:ordered")
                        .multicast(strategy).parallelProcessing().timeout(2000)
                        .to("direct:a", "direct:b", "direct:c");

                from("direct:orderedNested")
                        .multicast(strategy).parallelProcessing().timeout(2000)
                        .to("direct:nestedB", "direct:nestedC", "direct:a");

                from("direct:streamingNested")
                        .multicast(strategy).parallelProcessing().streaming().timeout(2000)
                        .to("direct:a", "direct:nestedB", "direct:nestedC");

                from("direct:orderedNestedLast")
                        .multicast(strategy).parallelProcessing().timeout(2000)
                        .to("direct:b", "direct:a", "direct:nestedC5");

                from("direct:nestedB")
                        .multicast(new UseLatestAggregationStrategy()).to("direct:c", "direct:c", "direct:b").end();
                from("direct:nestedC")
                        .multicast(new UseLatestAggregationStrategy()).to("direct:b", "direct:b", "direct:c").end();
                from("direct:nestedC5")
                        .multicast(new UseLatestAggregationStrategy())
                        .to("direct:b", "direct:b", "direct:b", "direct:b", "direct:c").end();

                from("direct:a").process(e -> release.await(30, TimeUnit.SECONDS)).setBody(constant("A"));
                from("direct:b").setBody(constant("B"));
                from("direct:c").setBody(constant("C"));
            }
        };
    }

    private static final class IndexStrategy implements AggregationStrategy {
        final List<Integer> aggregated = new CopyOnWriteArrayList<>();
        final List<Integer> timedOut = new CopyOnWriteArrayList<>();

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            aggregated.add(newExchange.getProperty(ExchangePropertyKey.MULTICAST_INDEX, Integer.class));
            if (oldExchange == null) {
                return newExchange;
            }
            oldExchange.getMessage().setBody(oldExchange.getMessage().getBody(String.class) + ","
                                             + newExchange.getMessage().getBody(String.class));
            return oldExchange;
        }

        @Override
        public void timeout(Exchange exchange, int index, int total, long timeout) {
            timedOut.add(index);
        }
    }
}
