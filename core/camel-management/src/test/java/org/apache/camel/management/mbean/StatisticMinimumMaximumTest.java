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

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatisticMinimumMaximumTest {

    private static final int THREADS = 8;
    private static final int UPDATES = 20000;

    @Test
    public void testMinimumMaximum() {
        StatisticMinimum min = new StatisticMinimum();
        StatisticMaximum max = new StatisticMaximum();
        assertFalse(min.isUpdated());
        assertFalse(max.isUpdated());
        assertEquals(0, min.getValue());
        assertEquals(0, max.getValue());

        for (long v : new long[] { 5, 3, 8, 3, 6 }) {
            min.updateValue(v);
            max.updateValue(v);
        }
        assertTrue(min.isUpdated());
        assertEquals(3, min.getValue());
        assertEquals(8, max.getValue());

        min.reset();
        max.reset();
        assertFalse(min.isUpdated());
        assertFalse(max.isUpdated());
    }

    @RepeatedTest(5)
    public void testConcurrentUpdates() throws Exception {
        StatisticMinimum min = new StatisticMinimum();
        StatisticMaximum max = new StatisticMaximum();

        // each thread updates with its own range of values, in the order that makes each update a new min and max
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int thread = t;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < UPDATES; i++) {
                        max.updateValue((long) i * THREADS + thread + 1);
                        min.updateValue((long) (UPDATES - i) * THREADS - thread);
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

        // a lost update would leave a value from another thread that is not the real min or max
        assertEquals((long) UPDATES * THREADS, max.getValue());
        assertEquals(1L, min.getValue());
    }
}
