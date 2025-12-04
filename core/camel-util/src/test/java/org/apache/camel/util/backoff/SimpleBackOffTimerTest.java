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

package org.apache.camel.util.backoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

public class SimpleBackOffTimerTest {

    @Test
    public void testBackOffTimer() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff =
                BackOff.builder().delay(100).removeOnComplete(false).build();
        final SimpleBackOffTimer timer = new SimpleBackOffTimer(executor);
        final AtomicLong first = new AtomicLong();

        BackOffTimer.Task task = timer.schedule(backOff, context -> {
            assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
            assertEquals(100, context.getCurrentDelay());
            assertEquals(100L * counter.get(), context.getCurrentElapsedTime());
            if (first.get() == 0) {
                first.set(context.getFirstAttemptTime());
            } else {
                assertEquals(first.get(), context.getFirstAttemptTime());
            }

            return counter.get() < 5;
        });

        task.whenComplete((context, throwable) -> {
            assertEquals(5, counter.get());
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, timer.size());
        assertEquals(
                BackOffTimer.Task.Status.Completed,
                timer.getTasks().iterator().next().getStatus());
        timer.close();
    }

    @Test
    public void testBackOffTimerWithMaxAttempts() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder()
                .delay(100)
                .maxAttempts(5L)
                .removeOnComplete(false)
                .build();
        final SimpleBackOffTimer timer = new SimpleBackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(backOff, context -> {
            assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
            assertEquals(100, context.getCurrentDelay());
            assertEquals(100L * counter.get(), context.getCurrentElapsedTime());

            return true;
        });

        task.whenComplete((context, throwable) -> {
            assertEquals(5, counter.get());
            assertEquals(BackOffTimer.Task.Status.Exhausted, context.getStatus());
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, timer.size());
        assertEquals(
                BackOffTimer.Task.Status.Exhausted,
                timer.getTasks().iterator().next().getStatus());
        timer.close();
    }

    @Test
    public void testBackOffTimerWithMaxElapsedTime() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff = BackOff.builder()
                .delay(100)
                .maxElapsedTime(400)
                .removeOnComplete(false)
                .build();
        final SimpleBackOffTimer timer = new SimpleBackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(backOff, context -> {
            assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
            assertEquals(100, context.getCurrentDelay());
            assertEquals(100L * counter.get(), context.getCurrentElapsedTime());

            return true;
        });

        task.whenComplete((context, throwable) -> {
            assertTrue(counter.get() <= 5);
            assertEquals(BackOffTimer.Task.Status.Exhausted, context.getStatus());
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, timer.size());
        assertEquals(
                BackOffTimer.Task.Status.Exhausted,
                timer.getTasks().iterator().next().getStatus());
        timer.close();
    }

    @Test
    public void testBackOffTimerStop() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);
        final AtomicBoolean done = new AtomicBoolean();
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff =
                BackOff.builder().delay(100).removeOnComplete(false).build();
        final SimpleBackOffTimer timer = new SimpleBackOffTimer(executor);

        BackOffTimer.Task task = timer.schedule(backOff, context -> {
            assertEquals(BackOffTimer.Task.Status.Active, context.getStatus());

            latch.countDown();

            return false;
        });

        task.whenComplete((context, throwable) -> {
            assertEquals(BackOffTimer.Task.Status.Inactive, context.getStatus());
            done.set(true);
        });

        latch.await(2, TimeUnit.SECONDS);
        assertEquals(1, timer.size());
        assertEquals(
                BackOffTimer.Task.Status.Completed,
                timer.getTasks().iterator().next().getStatus());
        task.cancel();
        assertEquals(0, timer.size());
        assertTrue(done.get());

        executor.shutdownNow();

        timer.close();
    }

    @Test
    public void testBackOffTimerRemoveOnComplete() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        final BackOff backOff =
                BackOff.builder().delay(100).removeOnComplete(true).build();
        final SimpleBackOffTimer timer = new SimpleBackOffTimer(executor);
        final AtomicLong first = new AtomicLong();

        BackOffTimer.Task task = timer.schedule(backOff, context -> {
            assertEquals(counter.incrementAndGet(), context.getCurrentAttempts());
            assertEquals(100, context.getCurrentDelay());
            assertEquals(100L * counter.get(), context.getCurrentElapsedTime());
            if (first.get() == 0) {
                first.set(context.getFirstAttemptTime());
            } else {
                assertEquals(first.get(), context.getFirstAttemptTime());
            }

            return counter.get() < 5;
        });

        task.whenComplete((context, throwable) -> {
            assertEquals(5, counter.get());
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // task is removed
        assertEquals(0, timer.size());
        timer.close();
    }
}
