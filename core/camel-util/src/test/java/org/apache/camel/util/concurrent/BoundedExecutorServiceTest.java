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
package org.apache.camel.util.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoundedExecutorServiceTest {

    @Test
    public void testCallerRunsOnTimeout() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 1, 200, TimeUnit.MILLISECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            CountDownLatch blockTask = new CountDownLatch(1);
            sized.execute(() -> {
                try {
                    blockTask.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            AtomicBoolean ranOnCallerThread = new AtomicBoolean();
            String callerName = Thread.currentThread().getName();

            sized.execute(() -> ranOnCallerThread
                    .set(Thread.currentThread().getName().equals(callerName)));

            assertTrue(ranOnCallerThread.get(),
                    "After timeout, task should run on the caller's thread");
            assertEquals(1, sized.getCallerRunsCount());
            assertEquals(0, sized.getRejectedCount());

            blockTask.countDown();
        } finally {
            sized.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    public void testAbortOnTimeout() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 1, 200, TimeUnit.MILLISECONDS, false, ThreadPoolRejectedPolicy.Abort);
        try {
            CountDownLatch blockTask = new CountDownLatch(1);
            sized.execute(() -> {
                try {
                    blockTask.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThrows(RejectedExecutionException.class,
                    () -> sized.execute(() -> {
                    }),
                    "Should reject after timeout with Abort policy");
            assertEquals(0, sized.getCallerRunsCount());
            assertEquals(1, sized.getRejectedCount());

            blockTask.countDown();
        } finally {
            sized.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    public void testBlockForeverPolicy() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 1, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.Block);
        try {
            CountDownLatch blockTask = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);

            sized.execute(() -> {
                try {
                    blockTask.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            Thread submitter = new Thread(() -> sized.execute(secondStarted::countDown));
            submitter.start();

            Thread.sleep(200);
            assertEquals(1, secondStarted.getCount(), "Second task should be blocked waiting");

            blockTask.countDown();

            assertTrue(secondStarted.await(5, TimeUnit.SECONDS),
                    "Second task should run after first completes");
            submitter.join(5000);
        } finally {
            sized.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    public void testConcurrencyBounded() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        int maxConcurrent = 3;
        var sized = new BoundedExecutorService(
                delegate, maxConcurrent, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.Block);
        try {
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            int totalTasks = 20;
            CountDownLatch allDone = new CountDownLatch(totalTasks);

            ExecutorService senders = Executors.newFixedThreadPool(totalTasks);
            for (int i = 0; i < totalTasks; i++) {
                senders.submit(() -> {
                    sized.execute(() -> {
                        int current = inFlight.incrementAndGet();
                        peak.accumulateAndGet(current, Math::max);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        inFlight.decrementAndGet();
                        allDone.countDown();
                    });
                });
            }

            assertTrue(allDone.await(30, TimeUnit.SECONDS), "All tasks should complete");
            assertTrue(peak.get() <= maxConcurrent,
                    "Peak concurrency (" + peak.get() + ") should be <= " + maxConcurrent);
            senders.shutdown();
        } finally {
            sized.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    public void testPermitsReleasedAfterCompletion() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 2, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            CountDownLatch firstBatch = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                sized.execute(firstBatch::countDown);
            }
            assertTrue(firstBatch.await(5, TimeUnit.SECONDS), "First batch should complete");

            Thread.sleep(50);

            CountDownLatch secondBatch = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                sized.execute(secondBatch::countDown);
            }
            assertTrue(secondBatch.await(5, TimeUnit.SECONDS),
                    "Second batch should succeed after permits are released");
        } finally {
            sized.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    public void testSubmitAfterShutdown() {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 5, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);

        sized.shutdown();
        assertTrue(sized.isShutdown());
        assertTrue(delegate.isShutdown());

        assertThrows(RejectedExecutionException.class,
                () -> sized.execute(() -> {
                }),
                "Should reject after shutdown");
    }
}
