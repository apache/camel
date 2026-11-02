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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reading the clock is the main single threaded cost of the statistics, so a node starts at the end time of the node
 * before it. A time reported for a node must not include anything that ran outside that node, so the end time may only
 * be reused when nothing can have happened since then.
 */
public class DefaultInstrumentationProcessorTimingTest {

    private static final long MILLI = 1_000_000L;

    private final RecordingCounter counter = new RecordingCounter();
    private final DefaultInstrumentationProcessor route = new DefaultInstrumentationProcessor("route");
    private final DefaultInstrumentationProcessor node = new DefaultInstrumentationProcessor("process");
    private final DefaultInstrumentationProcessor nextNode = new DefaultInstrumentationProcessor("process");

    private TimeSource.Provider previous;
    private CamelContext context;
    private Exchange exchange;
    private long nanos = 1000 * MILLI;
    private int clockReads;

    @BeforeEach
    public void setUp() {
        previous = TimeSource.install(new TimeSource.Provider() {
            @Override
            public long currentTimeMillis() {
                return 0;
            }

            @Override
            public long nanoTime() {
                clockReads++;
                return nanos;
            }

            @Override
            public void sleep(long millis) {
                // not used
            }
        });
        route.setCounter(counter);
        node.setCounter(counter);
        nextNode.setCounter(counter);
        context = new DefaultCamelContext();
        exchange = new DefaultExchange(context);
        // only count the reads of the statistics
        clockReads = 0;
    }

    @AfterEach
    public void tearDown() throws Exception {
        TimeSource.install(previous);
        context.close();
    }

    @Test
    public void testNextNodeStartsAtTheEndOfTheNodeBeforeIt() {
        StopWatch routeWatch = route.before(exchange);
        run(node, 5);
        run(nextNode, 7);
        route.after(exchange, routeWatch);

        assertEquals(List.of(5L, 7L, 12L), counter.times);
        // a read at the start of the route, and one at the end of each of the 2 nodes and of the route
        assertEquals(4, clockReads);
    }

    @Test
    public void testRouteDoesNotReuseAnEndTimeOfAnExchangeThatIsSentAgain() {
        StopWatch routeWatch = route.before(exchange);
        run(node, 5);
        route.after(exchange, routeWatch);
        counter.times.clear();

        // the same exchange object is sent to a route an hour later
        nanos += 3_600_000 * MILLI;
        routeWatch = route.before(exchange);
        run(node, 2);
        route.after(exchange, routeWatch);

        assertEquals(List.of(2L, 2L), counter.times);
    }

    @Test
    public void testNestedRouteAfterARedeliveryDelayDoesNotCountTheDelay() {
        run(node, 5);

        // the redelivery delay of an error handler of the calling node, which then calls a route again
        nanos += 5000 * MILLI;
        counter.times.clear();
        StopWatch routeWatch = route.before(exchange);
        run(nextNode, 1);
        route.after(exchange, routeWatch);

        assertEquals(List.of(1L, 1L), counter.times);
    }

    @Test
    public void testNodeAfterAFailedNodeDoesNotReuseItsEndTime() {
        StopWatch watch = node.before(exchange);
        nanos += 5 * MILLI;
        exchange.setException(new IllegalArgumentException("Forced"));
        node.after(exchange, watch);
        exchange.setException(null);

        // an error handler can wait or run other things before the next node
        nanos += 5000 * MILLI;
        run(nextNode, 1);

        assertEquals(List.of(1L), counter.times);
    }

    @Test
    public void testNodeAfterANodeWithoutACounterDoesNotCountThatNode() {
        // such as with onlyRegisterProcessorWithCustomId, which leaves other nodes without statistics
        DefaultInstrumentationProcessor uncounted = new DefaultInstrumentationProcessor("process");

        run(node, 5);
        StopWatch none = uncounted.before(exchange);
        nanos += 5000 * MILLI;
        uncounted.after(exchange, none);
        run(nextNode, 1);

        assertEquals(List.of(5L, 1L), counter.times);
    }

    @Test
    public void testNodeAfterStatisticsWereDisabledDoesNotCountTheDisabledNodes() {
        run(node, 5);

        counter.statisticsEnabled = false;
        StopWatch none = nextNode.before(exchange);
        nanos += 5000 * MILLI;
        nextNode.after(exchange, none);
        counter.statisticsEnabled = true;

        run(nextNode, 1);

        assertEquals(List.of(5L, 1L), counter.times);
    }

    @Test
    public void testCopyOfAnExchangeDoesNotReuseTheEndTime() {
        run(node, 5);

        // a split or multicast item runs at a later time, possibly in another thread
        Exchange copy = exchange.copy();
        nanos += 5000 * MILLI;
        StopWatch watch = nextNode.before(copy);
        nanos += MILLI;
        nextNode.after(copy, watch);

        assertEquals(List.of(5L, 1L), counter.times);
    }

    private void run(DefaultInstrumentationProcessor processor, long millis) {
        StopWatch watch = processor.before(exchange);
        nanos += millis * MILLI;
        processor.after(exchange, watch);
    }

    private static final class RecordingCounter implements PerformanceCounter {
        private final List<Long> times = new ArrayList<>();
        private boolean statisticsEnabled = true;

        @Override
        public void processExchange(Exchange exchange, String type) {
            // not used
        }

        @Override
        public void completedExchange(Exchange exchange, long time) {
            times.add(time);
        }

        @Override
        public void failedExchange(Exchange exchange) {
            // not used
        }

        @Override
        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }

        @Override
        public void setStatisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
        }
    }
}
