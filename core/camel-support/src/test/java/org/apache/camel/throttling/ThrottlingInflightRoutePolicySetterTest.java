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
package org.apache.camel.throttling;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that concurrent JMX-style calls to {@link ThrottlingInflightRoutePolicy#setMaxInflightExchanges(int)} and
 * {@link ThrottlingInflightRoutePolicy#setResumePercentOfMax(int)} never produce a {@code ThrottlingLimits} holder with
 * inconsistent values (mixed stale snapshot).
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-24267">CAMEL-24267</a>
 */
class ThrottlingInflightRoutePolicySetterTest {

    /**
     * Hammers both setters concurrently and verifies the invariant: the published {@code ThrottlingLimits} always
     * reflects a consistent pair where {@code resumeInflightExchanges == max(maxInflightExchanges *
     * resumePercentOfMax / 100, 1)}.
     */
    @Test
    void concurrentSettersShouldProduceConsistentLimits() throws Exception {
        ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();

        // reflective access to the private volatile throttlingLimits field
        Field limitsField = ThrottlingInflightRoutePolicy.class.getDeclaredField("throttlingLimits");
        limitsField.setAccessible(true);
        // the record class is a private inner type — use its accessor methods via reflection
        Class<?> limitsClass = limitsField.getType();
        java.lang.reflect.Method getMax = limitsClass.getMethod("maxInflightExchanges");
        java.lang.reflect.Method getResume = limitsClass.getMethod("resumeInflightExchanges");

        int iterations = 5_000;
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < iterations; i++) {
                int maxVal = 500 + (i % 500);       // range [500..999]
                int pctVal = 10 + (i % 80);          // range [10..89]

                // alternate which setter goes first to vary the interleaving
                Runnable setMax = () -> {
                    awaitBarrier(barrier);
                    policy.setMaxInflightExchanges(maxVal);
                };
                Runnable setPct = () -> {
                    awaitBarrier(barrier);
                    policy.setResumePercentOfMax(pctVal);
                };

                List<Future<?>> futures = new ArrayList<>(2);
                futures.add(pool.submit(setMax));
                futures.add(pool.submit(setPct));

                for (Future<?> f : futures) {
                    f.get(5, TimeUnit.SECONDS);
                }

                // after both setters complete, the holder must be internally consistent:
                // resume == max(holder.max * policy.resumePercentOfMax / 100, 1)
                Object holder = limitsField.get(policy);
                int holderMax = (int) getMax.invoke(holder);
                int holderResume = (int) getResume.invoke(holder);
                int currentPct = policy.getResumePercentOfMax();
                int currentMax = policy.getMaxInflightExchanges();

                // the holder's max must equal the field's max (no stale snapshot)
                assertEquals(currentMax, holderMax,
                        "iteration " + i + ": holder.max must match field maxInflightExchanges");

                // the holder's resume must be derived from the holder's own max and the current percent
                int expectedResume = Math.max(currentMax * currentPct / 100, 1);
                assertEquals(expectedResume, holderResume,
                        "iteration " + i + ": holder.resume must equal max(" + currentMax
                                                           + " * " + currentPct + " / 100, 1) = " + expectedResume
                                                           + " but was " + holderResume);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Verifies that the basic single-threaded setter contract still holds after synchronization.
     */
    @Test
    void singleThreadedSettersShouldComputeCorrectly() throws Exception {
        ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();

        // reflective access to the private volatile throttlingLimits field
        Field limitsField = ThrottlingInflightRoutePolicy.class.getDeclaredField("throttlingLimits");
        limitsField.setAccessible(true);
        Class<?> limitsClass = limitsField.getType();
        java.lang.reflect.Method getMax = limitsClass.getMethod("maxInflightExchanges");
        java.lang.reflect.Method getResume = limitsClass.getMethod("resumeInflightExchanges");

        // set max first, then percent
        policy.setMaxInflightExchanges(2000);
        policy.setResumePercentOfMax(50);

        Object holder = limitsField.get(policy);
        assertEquals(2000, (int) getMax.invoke(holder));
        assertEquals(1000, (int) getResume.invoke(holder));

        // set percent first, then max
        policy.setResumePercentOfMax(25);
        policy.setMaxInflightExchanges(400);

        holder = limitsField.get(policy);
        assertEquals(400, (int) getMax.invoke(holder));
        assertEquals(100, (int) getResume.invoke(holder));
    }

    /**
     * Verifies resume is clamped to at least 1 when percentage or max is very small.
     */
    @Test
    void resumeShouldBeAtLeastOne() throws Exception {
        ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();

        Field limitsField = ThrottlingInflightRoutePolicy.class.getDeclaredField("throttlingLimits");
        limitsField.setAccessible(true);
        Class<?> limitsClass = limitsField.getType();
        java.lang.reflect.Method getResume = limitsClass.getMethod("resumeInflightExchanges");

        policy.setMaxInflightExchanges(1);
        policy.setResumePercentOfMax(1);

        Object holder = limitsField.get(policy);
        int resume = (int) getResume.invoke(holder);
        assertEquals(1, resume, "resume must be clamped to at least 1");
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Barrier await failed: " + e.getMessage());
        }
    }
}
