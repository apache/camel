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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            CountDownLatch submitterBlocked = new CountDownLatch(1);

            sized.execute(() -> {
                try {
                    blockTask.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            Thread submitter = new Thread(() -> {
                submitterBlocked.countDown();
                sized.execute(secondStarted::countDown);
            });
            submitter.start();

            assertTrue(submitterBlocked.await(5, TimeUnit.SECONDS));
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertTrue(sized.getWaitingCount() > 0,
                            "Submitter should be blocked waiting for a permit"));

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
            CountDownLatch holdTasks = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(totalTasks);

            ExecutorService senders = Executors.newFixedThreadPool(totalTasks);
            for (int i = 0; i < totalTasks; i++) {
                senders.submit(() -> sized.execute(() -> {
                    int current = inFlight.incrementAndGet();
                    peak.accumulateAndGet(current, Math::max);
                    try {
                        holdTasks.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    inFlight.decrementAndGet();
                    allDone.countDown();
                }));
            }

            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(maxConcurrent, sized.getActiveCount()));

            holdTasks.countDown();
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
                delegate, 2, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.Block);
        try {
            CountDownLatch firstBatch = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                sized.execute(firstBatch::countDown);
            }
            assertTrue(firstBatch.await(5, TimeUnit.SECONDS), "First batch should complete");

            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(2, sized.getAvailablePermits()));

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

    @Test
    public void testSubmitReturnsFuture() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 5, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            AtomicBoolean executed = new AtomicBoolean();
            Future<?> future = sized.submit(() -> executed.set(true));

            future.get(5, TimeUnit.SECONDS);
            assertTrue(executed.get(), "Task submitted via submit() should execute");
            assertTrue(future.isDone());
        } finally {
            sized.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    public void testNestedTaskRunsInlineWhenItHoldsTheOnlyPermit() throws Exception {
        // a parallel split runs in the executor and submits its items to it, so waiting cannot succeed
        assertNestedTaskRunsInline(ThreadPoolRejectedPolicy.CallerRuns);
    }

    @Test
    public void testNestedTaskRunsInlineWithBlockPolicy() throws Exception {
        // this wait has no timeout, so the task would hang forever
        assertNestedTaskRunsInline(ThreadPoolRejectedPolicy.Block);
    }

    private void assertNestedTaskRunsInline(ThreadPoolRejectedPolicy policy) throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(delegate, 1, 60, TimeUnit.SECONDS, false, policy);
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean sameThread = new AtomicBoolean();

            sized.execute(() -> {
                Thread outer = Thread.currentThread();
                sized.execute(() -> {
                    sameThread.set(Thread.currentThread() == outer);
                    done.countDown();
                });
            });

            assertTrue(done.await(5, TimeUnit.SECONDS), "The nested task waited for the permit its parent holds");
            assertTrue(sameThread.get(), "The nested task should run in the task that submitted it");
            assertEquals(1, sized.getCallerRunsCount());
        } finally {
            sized.shutdownNow();
        }
    }

    @Test
    public void testTaskThatResubmitsItselfDoesNotOverflowTheStack() throws Exception {
        // a parallel split submits itself again for each item, so a large split must not run all of them inline
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 1, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            int resubmits = 200_000;
            AtomicInteger count = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(1);
            Runnable[] task = new Runnable[1];
            task[0] = () -> {
                if (count.incrementAndGet() == resubmits) {
                    done.countDown();
                } else {
                    sized.execute(task[0]);
                }
            };

            sized.execute(task[0]);

            assertTrue(done.await(30, TimeUnit.SECONDS), "Stopped after " + count.get() + " resubmits");
        } finally {
            sized.shutdownNow();
        }
    }

    @Test
    public void testNestedTaskThatThrowsDoesNotUseUpTheInlineDepth() throws Exception {
        // the depth must be restored on failure too, or a task that survives failures of the work it submits would
        // reach the depth limit and hand the work to new tasks, outside the concurrency limit
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 1, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            int failures = 100;
            AtomicInteger inline = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(1);

            sized.execute(() -> {
                Thread outer = Thread.currentThread();
                for (int i = 0; i < failures; i++) {
                    try {
                        sized.execute(() -> {
                            if (Thread.currentThread() == outer) {
                                inline.incrementAndGet();
                            }
                            throw new IllegalStateException("Forced");
                        });
                    } catch (IllegalStateException e) {
                        // an inline task fails in its caller, as a caller-run task does
                    }
                }
                done.countDown();
            });

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(failures, inline.get(), "Every nested task should still run inline after a failure");
            assertEquals(failures, sized.getCallerRunsCount());
        } finally {
            sized.shutdownNow();
        }
    }

    @Test
    public void testDelegateRunningATaskOnTheCallingThreadKeepsTheDepthOfTheOuterTask() throws Exception {
        // a delegate that runs a task on the calling thread must not reset the depth of the task that submitted it,
        // or that task would wait for a permit it holds itself
        var pool = Executors.newCachedThreadPool();
        ThreadLocal<Boolean> direct = new ThreadLocal<>();
        var delegate = new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                if (direct.get() != null) {
                    command.run();
                } else {
                    pool.execute(command);
                }
            }

            @Override
            public void shutdown() {
                pool.shutdown();
            }

            @Override
            public List<Runnable> shutdownNow() {
                return pool.shutdownNow();
            }

            @Override
            public boolean isShutdown() {
                return pool.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return pool.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return pool.awaitTermination(timeout, unit);
            }
        };
        var sized = new BoundedExecutorService(delegate, 2, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.Block);
        try {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean ranInline = new AtomicBoolean();

            sized.execute(() -> {
                Thread outer = Thread.currentThread();
                // a free permit: the delegate runs this one on the calling thread
                direct.set(true);
                sized.execute(() -> {
                });
                direct.remove();
                // take the other permit, so that the next task finds none
                sized.execute(() -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                // with Block this waits forever if the depth of this task was lost
                sized.execute(() -> ranInline.set(Thread.currentThread() == outer));
                done.countDown();
            });

            assertTrue(done.await(5, TimeUnit.SECONDS), "The nested task should not wait for the permit");
            assertTrue(ranInline.get(), "The nested task should run inline");
            release.countDown();
        } finally {
            sized.shutdownNow();
        }
    }

    @Test
    public void testNestedTaskUsesAFreePermit() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 2, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean sameThread = new AtomicBoolean(true);

            sized.execute(() -> {
                Thread outer = Thread.currentThread();
                sized.execute(() -> {
                    sameThread.set(Thread.currentThread() == outer);
                    done.countDown();
                });
            });

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertFalse(sameThread.get(), "The bound still allows this task to run concurrently");
            assertEquals(0, sized.getCallerRunsCount());
        } finally {
            sized.shutdownNow();
        }
    }

    @Test
    public void testNestedTaskIsStillRejectedWithAbortPolicy() throws Exception {
        var delegate = Executors.newCachedThreadPool();
        var sized = new BoundedExecutorService(
                delegate, 1, 100, TimeUnit.MILLISECONDS, false, ThreadPoolRejectedPolicy.Abort);
        try {
            CountDownLatch rejected = new CountDownLatch(1);

            sized.execute(() -> {
                try {
                    sized.execute(() -> {
                        // never runs
                    });
                } catch (RejectedExecutionException e) {
                    rejected.countDown();
                }
            });

            assertTrue(rejected.await(5, TimeUnit.SECONDS), "Abort must fail the task and not run it");
            assertEquals(0, sized.getCallerRunsCount());
        } finally {
            sized.shutdownNow();
        }
    }

    @Test
    public void testTaskOfAnotherExecutorStillWaits() throws Exception {
        // only a task of this executor can hold one of its permits, so others wait for backpressure as usual
        var full = new BoundedExecutorService(
                Executors.newCachedThreadPool(), 1, 200, TimeUnit.MILLISECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        var other = new BoundedExecutorService(
                Executors.newCachedThreadPool(), 1, 60, TimeUnit.SECONDS, false, ThreadPoolRejectedPolicy.CallerRuns);
        try {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger waitedMillis = new AtomicInteger();
            full.execute(() -> {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            other.execute(() -> {
                long start = System.nanoTime();
                full.execute(() -> waitedMillis.set((int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
                done.countDown();
            });

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(waitedMillis.get() >= 150, "It should wait for the timeout, but waited " + waitedMillis.get());
            release.countDown();
        } finally {
            full.shutdownNow();
            other.shutdownNow();
        }
    }
}
