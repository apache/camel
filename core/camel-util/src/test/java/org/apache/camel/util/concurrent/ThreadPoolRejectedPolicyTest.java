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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rejected policies with a thread pool that is shut down: a {@link Rejectable} task must be rejected, not dropped
 * without notice.
 */
class ThreadPoolRejectedPolicyTest {

    @Test
    void testCallerRunsRejectsARejectableTaskOfAShutDownPool() {
        ThreadPoolExecutor pool = newPool(ThreadPoolRejectedPolicy.CallerRuns, 10);
        pool.shutdown();

        CountingTask task = new CountingTask();
        pool.execute(task);

        assertEquals(0, task.runs.get());
        assertEquals(1, task.rejects.get());
    }

    @Test
    void testCallerRunsRejectsARejectableTaskSubmittedToAShutDownPool() {
        ThreadPoolExecutor pool = newPool(ThreadPoolRejectedPolicy.CallerRuns, 10);
        pool.shutdown();

        // submit wraps the task in a RejectableFutureTask, which hands the rejection on
        CountingTask task = new CountingTask();
        pool.submit(task);

        assertEquals(0, task.runs.get());
        assertEquals(1, task.rejects.get());
    }

    @Test
    void testCallerRunsDropsAPlainTaskOfAShutDownPool() {
        ThreadPoolExecutor pool = newPool(ThreadPoolRejectedPolicy.CallerRuns, 10);
        pool.shutdown();

        // as ThreadPoolExecutor.CallerRunsPolicy: no exception, the task does not run
        AtomicInteger runs = new AtomicInteger();
        pool.execute(runs::incrementAndGet);

        assertEquals(0, runs.get());
    }

    @Test
    void testCallerRunsRunsTheTaskInTheCallerWhenThePoolIsFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = newPool(ThreadPoolRejectedPolicy.CallerRuns, 1);
        try {
            pool.execute(() -> awaitQuietly(release));
            pool.execute(() -> awaitQuietly(release));

            CountingTask task = new CountingTask();
            pool.execute(task);

            assertEquals(Thread.currentThread(), task.thread);
            assertEquals(1, task.runs.get());
            assertEquals(0, task.rejects.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void testBlockRejectsATaskPutIntoAPoolShutDownWhileWaiting() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = newPool(ThreadPoolRejectedPolicy.Block, 1);
        try {
            // one task runs, one fills the queue
            pool.execute(() -> awaitQuietly(release));
            pool.execute(() -> awaitQuietly(release));

            CountingTask task = new CountingTask();
            Thread caller = new Thread(() -> pool.execute(task));
            caller.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> caller.getState() == Thread.State.WAITING);

            // shutdownNow drains the queue: the waiting caller gets the space, but the pool no longer runs the task
            pool.shutdownNow();
            caller.join(10000);
            assertFalse(caller.isAlive());

            assertEquals(0, task.runs.get());
            assertEquals(1, task.rejects.get());
            assertTrue(pool.getQueue().isEmpty());
        } finally {
            release.countDown();
        }
    }

    private static ThreadPoolExecutor newPool(ThreadPoolRejectedPolicy policy, int queueSize) {
        return new RejectableThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueSize), policy.asRejectedExecutionHandler());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CountingTask implements Runnable, Rejectable {
        final AtomicInteger runs = new AtomicInteger();
        final AtomicInteger rejects = new AtomicInteger();
        volatile Thread thread;

        @Override
        public void run() {
            thread = Thread.currentThread();
            runs.incrementAndGet();
        }

        @Override
        public void reject() {
            rejects.incrementAndGet();
        }
    }
}
