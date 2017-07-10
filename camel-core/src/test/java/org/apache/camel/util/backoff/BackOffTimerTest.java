/**
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
package org.apache.camel.util.backoff;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class BackOffTimerTest {
    @Test
    public void testBackOffTimer() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100 * counter.get(), context.getCurrentElapsedTime());

                return counter.get() < 5;
            }
        ).thenAccept(
            context -> {
                Assert.assertEquals(5, counter.get());
            }
        ).get(5, TimeUnit.SECONDS);

        executor.shutdownNow();
    }

    @Test
    public void testBackOffTimerWithMaxAttempts() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).maxAttempts(5L).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100 * counter.get(), context.getCurrentElapsedTime());

                return true;
            }
        ).thenAccept(
            context -> {
                Assert.assertEquals(5, counter.get());
            }
        ).get(5, TimeUnit.SECONDS);

        executor.shutdownNow();
    }

    @Test
    public void testBackOffTimerWithMaxElapsedTime() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).maxElapsedTime(400).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100 * counter.get(), context.getCurrentElapsedTime());

                return true;
            }
        ).thenAccept(
            context -> {
                Assert.assertTrue(counter.get() <= 5);
            }
        ).get(5, TimeUnit.SECONDS);

        executor.shutdownNow();
    }
}
