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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class BackOffTimerTest {
    @Test
    public void testBackOffTimer() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100 * counter.get(), context.getCurrentElapsedTime());

                return counter.get() < 5;
            }
        );

        task.whenComplete(
            (context, throwable) -> {
                Assert.assertEquals(5, counter.get());
                latch.countDown();
            }
        );

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    @Test
    public void testBackOffTimerWithMaxAttempts() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).maxAttempts(5L).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100 * counter.get(), context.getCurrentElapsedTime());

                return true;
            }
        );

        task.whenComplete(
            (context, throwable) -> {
                Assert.assertEquals(5, counter.get());
                Assert.assertEquals(BackOffTimer.Task.Status.Exhausted, context.getStatus());
                latch.countDown();
            }
        );

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    @Test
    public void testBackOffTimerWithMaxElapsedTime() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger(0);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).maxElapsedTime(400).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100, context.getCurrentDelay());
                Assert.assertEquals(100 * counter.get(), context.getCurrentElapsedTime());

                return true;
            }
        );

        task.whenComplete(
            (context, throwable) -> {
                Assert.assertTrue(counter.get() <= 5);
                Assert.assertEquals(BackOffTimer.Task.Status.Exhausted, context.getStatus());
                latch.countDown();
            }
        );

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    @Test
    public void testBackOffTimerStop() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);
        final AtomicBoolean done = new AtomicBoolean(false);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder().delay(100).build();
        final BackOffTimer timer = new BackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(
            backOff,
            context -> {
                Assert.assertEquals(BackOffTimer.Task.Status.Active, context.getStatus());

                latch.countDown();

                return false;
            }
        );

        task.whenComplete(
            (context, throwable) -> {
                Assert.assertEquals(BackOffTimer.Task.Status.Inactive, context.getStatus());
                done.set(true);
            }
        );

        latch.await(2, TimeUnit.SECONDS);
        task.cancel();
        Assert.assertTrue(done.get());

        executor.shutdownNow();
    }
}
