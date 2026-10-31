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
package org.apache.camel.management.mbean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedPerformanceCounterTest {

    private static final int THREADS = 8;
    private static final int UPDATES = 20000;

    private CamelContext context;
    private ManagedPerformanceCounter counter;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        counter = new ManagedPerformanceCounter() {
        };
        counter.init(context.getManagementStrategy());
    }

    @AfterEach
    public void tearDown() throws Exception {
        context.close();
    }

    @Test
    public void testMeanIsUnsetUntilAnExchangeCompletes() {
        // monitoring tools rely on -1 meaning no data, which is not the same as a mean of 0 ms
        assertEquals(-1, counter.getMeanProcessingTime());

        counter.failedExchange(new DefaultExchange(context));
        assertEquals(-1, counter.getMeanProcessingTime());

        counter.completedExchange(new DefaultExchange(context), 10);
        counter.completedExchange(new DefaultExchange(context), 20);
        assertEquals(15, counter.getMeanProcessingTime());

        counter.reset();
        assertEquals(-1, counter.getMeanProcessingTime());
    }

    @Test
    public void testDeltaIsZeroForEqualProcessingTimes() {
        // repeated values must not leave the delta of the previous change behind
        counter.completedExchange(new DefaultExchange(context), 5);
        assertEquals(5, counter.getDeltaProcessingTime());
        counter.completedExchange(new DefaultExchange(context), 8);
        assertEquals(3, counter.getDeltaProcessingTime());
        counter.completedExchange(new DefaultExchange(context), 8);
        assertEquals(0, counter.getDeltaProcessingTime());
        counter.completedExchange(new DefaultExchange(context), 2);
        assertEquals(-6, counter.getDeltaProcessingTime());
    }

    @Test
    public void testInflightSurvivesReset() {
        // exchanges that are inflight during a reset complete afterwards, and must still be counted down
        counter.processExchange(new DefaultExchange(context), "processor");
        counter.reset();
        assertEquals(1, counter.getExchangesInflight());

        counter.completedExchange(new DefaultExchange(context), 1);
        assertEquals(0, counter.getExchangesInflight());
    }

    @Test
    public void testInflightIsNeverNegative() {
        // the sum read over the striped cells of the counter can see a decrement before its increment
        counter.completedExchange(new DefaultExchange(context), 1);
        assertEquals(0, counter.getExchangesInflight());
    }

    @Test
    public void testLastExchangeIdDoesNotGenerateAnIdPerExchange() {
        // exchange ids are lazy and a random id is costly, so the counter must not ask each exchange for its id
        AtomicLong generated = new AtomicLong();
        context.setUuidGenerator(() -> "ID-" + generated.incrementAndGet());

        int exchanges = 50000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < exchanges; i++) {
            counter.completedExchange(new DefaultExchange(context), 0);
        }
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(exchanges, counter.getExchangesCompleted());
        // at most one id for every millisecond the loop was seen in
        long max = elapsed + 1;
        assertTrue(generated.get() <= max,
                "Generated " + generated.get() + " ids in " + elapsed + " ms, expected at most " + max);
    }

    @Test
    public void testLastExchangeIdIsUpdatedInALaterMillisecond() {
        Exchange first = new DefaultExchange(context);
        counter.completedExchange(first, 1);
        assertEquals(first.getExchangeId(), counter.getFirstExchangeCompletedExchangeId());
        assertEquals(first.getExchangeId(), counter.getLastExchangeCompletedExchangeId());

        long time = counter.getLastExchangeCompletedTimestamp().getTime();
        while (System.currentTimeMillis() <= time) {
            Thread.onSpinWait();
        }

        Exchange second = new DefaultExchange(context);
        counter.completedExchange(second, 1);
        assertEquals(first.getExchangeId(), counter.getFirstExchangeCompletedExchangeId());
        assertEquals(second.getExchangeId(), counter.getLastExchangeCompletedExchangeId());
    }

    @Test
    public void testResetClearsLastExchangeId() {
        counter.completedExchange(new DefaultExchange(context), 1);
        counter.reset();
        assertNull(counter.getLastExchangeCompletedExchangeId());

        // also within the same millisecond as the exchange before the reset
        Exchange exchange = new DefaultExchange(context);
        counter.completedExchange(exchange, 1);
        assertEquals(exchange.getExchangeId(), counter.getLastExchangeCompletedExchangeId());
    }

    @Test
    public void testLastFailedExchangeIdIsExact() {
        // failures are rare and worth finding, so they are not throttled as completed exchanges are
        counter.failedExchange(new DefaultExchange(context));
        Exchange last = new DefaultExchange(context);
        counter.failedExchange(last);
        assertEquals(last.getExchangeId(), counter.getLastExchangeFailureExchangeId());
    }

    @Test
    public void testConcurrentExchangesAreAllCounted() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final long time = t + 1L;
                futures.add(executor.submit(() -> {
                    start.await();
                    Exchange exchange = new DefaultExchange(context);
                    for (int i = 0; i < UPDATES; i++) {
                        counter.processExchange(exchange, "processor");
                        counter.completedExchange(exchange, time);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(160000, counter.getExchangesTotal());
        assertEquals(160000, counter.getExchangesCompleted());
        assertEquals(0, counter.getExchangesInflight());
        // 20000 * (1 + 2 + ... + 8)
        assertEquals(720000, counter.getTotalProcessingTime());
        assertEquals(4, counter.getMeanProcessingTime());
        assertEquals(1, counter.getMinProcessingTime());
        assertEquals(8, counter.getMaxProcessingTime());
    }
}
