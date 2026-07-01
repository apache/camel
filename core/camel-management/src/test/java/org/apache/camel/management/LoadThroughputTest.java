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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.management.mbean.LoadThroughput;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadThroughputTest {

    @Test
    public void testInitialValueIsZero() {
        LoadThroughput t = new LoadThroughput();
        assertEquals(0.0, t.getThroughput());
    }

    @Test
    public void testConvergesToSteadyRate() {
        LoadThroughput t = new LoadThroughput();
        AtomicLong total = new AtomicLong(0);
        t.update(total.get());

        await().pollInterval(10, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    total.incrementAndGet();
                    t.update(total.get());
                    assertTrue(t.getThroughput() > 5.0,
                            "Throughput should converge toward steady rate: " + t.getThroughput());
                });
    }

    @Test
    public void testSmoothing() {
        LoadThroughput t = new LoadThroughput();
        AtomicLong total = new AtomicLong(0);
        AtomicInteger count = new AtomicInteger(0);
        t.update(total.get());

        await().pollInterval(10, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    int i = count.incrementAndGet();
                    if (i % 5 == 0) {
                        total.incrementAndGet();
                    }
                    t.update(total.get());
                    double thp = t.getThroughput();
                    assertTrue(thp > 1.0, "Smoothed throughput should be well above zero: " + thp);
                    assertTrue(thp < 80.0, "Smoothed throughput should be below the instantaneous spike: " + thp);
                });
    }

    @Test
    public void testReset() {
        LoadThroughput t = new LoadThroughput();
        AtomicLong total = new AtomicLong(0);
        t.update(total.get());

        await().pollInterval(10, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    total.addAndGet(10);
                    t.update(total.get());
                    assertTrue(t.getThroughput() > 0);
                });

        t.reset();
        assertEquals(0.0, t.getThroughput());
    }

}
