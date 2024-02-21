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
package org.apache.camel;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.concurrent.Rejectable;
import org.apache.camel.util.concurrent.RejectableThreadPoolExecutor;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThreadPoolRejectedPolicyTest extends TestSupport {

    @Test
    public void testAbortAsRejectedExecutionHandler() throws InterruptedException {

        final ExecutorService executorService
                = createTestExecutorService(ThreadPoolRejectedPolicy.Abort.asRejectedExecutionHandler());

        final MockCallable<String> task1 = new MockCallable<>();
        final Future<?> result1 = executorService.submit(task1);
        final MockRunnable task2 = new MockRunnable();
        final Future<?> result2 = executorService.submit(task2);
        final MockCallable<String> task3 = new MockCallable<>();
        try {
            executorService.submit(task3);
            fail("Third task should have been rejected by a threadpool is full with 1 task and queue is full with 1 task.");
        } catch (RejectedExecutionException e) {
        }

        shutdownAndAwait(executorService);

        assertInvoked(task1, result1);
        assertInvoked(task2, result2);
        assertRejected(task3, null);
    }

    @Test
    public void testAbortAsRejectedExecutionHandlerWithRejectableTasks() throws InterruptedException {

        final ExecutorService executorService
                = createTestExecutorService(ThreadPoolRejectedPolicy.Abort.asRejectedExecutionHandler());

        final MockRejectableRunnable task1 = new MockRejectableRunnable();
        final Future<?> result1 = executorService.submit(task1);
        final MockRejectableCallable<String> task2 = new MockRejectableCallable<>();
        final Future<?> result2 = executorService.submit(task2);
        final MockRejectableRunnable task3 = new MockRejectableRunnable();
        final Future<?> result3 = executorService.submit(task3);

        final MockRejectableCallable<String> task4 = new MockRejectableCallable<>();
        final Future<?> result4 = executorService.submit(task4);

        shutdownAndAwait(executorService);

        assertInvoked(task1, result1);
        assertInvoked(task2, result2);
        assertRejected(task3, result3);
        assertRejected(task4, result4);
    }

    @Test
    public void testCallerRunsAsRejectedExecutionHandler() throws InterruptedException {

        final ExecutorService executorService
                = createTestExecutorService(ThreadPoolRejectedPolicy.CallerRuns.asRejectedExecutionHandler());

        final MockRunnable task1 = new MockRunnable();
        final Future<?> result1 = executorService.submit(task1);
        final MockRunnable task2 = new MockRunnable();
        final Future<?> result2 = executorService.submit(task2);
        final MockRunnable task3 = new MockRunnable();
        final Future<?> result3 = executorService.submit(task3);

        shutdownAndAwait(executorService);

        assertInvoked(task1, result1);
        assertInvoked(task2, result2);
        assertInvoked(task3, result3);
    }

    @Test
    public void testCallerRunsAsRejectedExecutionHandlerWithRejectableTasks() throws InterruptedException {

        final ExecutorService executorService
                = createTestExecutorService(ThreadPoolRejectedPolicy.CallerRuns.asRejectedExecutionHandler());

        final MockRejectableRunnable task1 = new MockRejectableRunnable();
        final Future<?> result1 = executorService.submit(task1);
        final MockRejectableRunnable task2 = new MockRejectableRunnable();
        final Future<?> result2 = executorService.submit(task2);
        final MockRejectableRunnable task3 = new MockRejectableRunnable();
        final Future<?> result3 = executorService.submit(task3);

        shutdownAndAwait(executorService);

        assertInvoked(task1, result1);
        assertInvoked(task2, result2);
        assertInvoked(task3, result3);
    }

    private ExecutorService createTestExecutorService(final RejectedExecutionHandler rejectedExecutionHandler) {
        return new RejectableThreadPoolExecutor(
                1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1), rejectedExecutionHandler);
    }

    private void shutdownAndAwait(final ExecutorService executorService) {
        executorService.shutdown();
        try {
            assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS),
                    "Test ExecutorService shutdown is not expected to take longer than 10 seconds.");
        } catch (InterruptedException e) {
            fail("Test ExecutorService shutdown is not expected to be interrupted.");
        }
    }

    private void assertInvoked(MockTask task, Future<?> result) {
        assertTrue(result.isDone());
        assertEquals(1, task.getInvocationCount());
        if (task instanceof Rejectable) {
            assertEquals(0, task.getRejectionCount());
        }
    }

    private void assertRejected(MockTask task, Future<?> result) {
        if (result != null) {
            assertFalse(result.isDone());
        }
        assertEquals(0, task.getInvocationCount());
        if (task instanceof Rejectable) {
            assertEquals(1, task.getRejectionCount());
        }
    }

    private abstract static class MockTask {
        private final AtomicInteger invocationCount = new AtomicInteger();

        private final AtomicInteger rejectionCount = new AtomicInteger();

        public int getInvocationCount() {
            return invocationCount.get();
        }

        protected void countInvocation() {
            invocationCount.incrementAndGet();
        }

        public int getRejectionCount() {
            return rejectionCount.get();
        }

        protected void countRejection() {
            rejectionCount.incrementAndGet();
        }
    }

    private static class MockRunnable extends MockTask implements Runnable {
        @Override
        public void run() {
            countInvocation();
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                fail("MockRunnable task is not expected to be interrupted.");
            }
        }
    }

    private static class MockRejectableRunnable extends MockRunnable implements Rejectable {
        @Override
        public void reject() {
            countRejection();
        }
    }

    private static class MockCallable<T> extends MockTask implements Callable<T> {
        @Override
        public T call() throws Exception {
            countInvocation();
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                fail("MockCallable task is not expected to be interrupted.");
            }
            return null;
        }
    }

    private static class MockRejectableCallable<T> extends MockCallable<T> implements Rejectable {
        @Override
        public void reject() {
            countRejection();
        }
    }
}
