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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * An {@link ExecutorService} wrapper that enforces bounded concurrency via a {@link Semaphore}.
 * <p>
 * When virtual threads are enabled, Camel replaces the traditional {@link java.util.concurrent.ThreadPoolExecutor} with
 * {@code Executors.newThreadPerTaskExecutor()}, which accepts every task immediately (unbounded). This wrapper limits
 * the maximum number of tasks delegated to the underlying executor. Unlike {@code ThreadPoolExecutor} there is no
 * distinction between pool threads and queued tasks — the semaphore enforces a flat concurrency cap on delegated tasks.
 * <p>
 * When the semaphore has no available permits, behavior depends on the configured {@link ThreadPoolRejectedPolicy}:
 * <ul>
 * <li><b>CallerRuns</b> (default): blocks until a permit is available or the timeout expires; on timeout, runs the task
 * on the caller's thread. Tasks are never lost. Note that caller-run tasks execute outside semaphore accounting, so
 * total system concurrency may temporarily exceed {@code maxConcurrent}.</li>
 * <li><b>Abort</b>: blocks until a permit is available or the timeout expires; on timeout, throws
 * {@link RejectedExecutionException}.</li>
 * <li><b>Block</b>: blocks indefinitely until a permit becomes available. No timeout, no rejection.</li>
 * </ul>
 * <p>
 * <b>Caller thread blocking:</b> while waiting for a permit, the calling thread is blocked. When callers are virtual
 * threads this is inexpensive (the carrier thread is released). When callers are platform threads (e.g., HTTP server
 * threads) the blocked thread is unavailable for other work — this is standard backpressure behavior but worth noting
 * for capacity planning.
 *
 */
public class BoundedExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;
    private final Semaphore semaphore;
    private final int maxConcurrent;
    private final long timeoutNanos;
    private final ThreadPoolRejectedPolicy rejectedPolicy;
    private final LongAdder callerRunsCount = new LongAdder();
    private final LongAdder rejectedCount = new LongAdder();
    private final LongAdder delegatedTaskCount = new LongAdder();

    /**
     * @param delegate       the underlying executor (typically {@code newThreadPerTaskExecutor})
     * @param maxConcurrent  the maximum number of tasks delegated to the underlying executor concurrently
     * @param acquireTimeout the maximum time to wait for a permit (ignored when policy is {@code Block})
     * @param timeUnit       the time unit for {@code acquireTimeout}
     * @param fair           {@code true} for FIFO permit ordering (predictable latency), {@code false} for barging
     *                       (higher throughput)
     * @param rejectedPolicy the policy to apply when no permit is available
     */
    public BoundedExecutorService(ExecutorService delegate, int maxConcurrent,
                                  long acquireTimeout, TimeUnit timeUnit,
                                  boolean fair, ThreadPoolRejectedPolicy rejectedPolicy) {
        this.delegate = delegate;
        this.maxConcurrent = maxConcurrent;
        this.semaphore = new Semaphore(maxConcurrent, fair);
        this.timeoutNanos = timeUnit.toNanos(acquireTimeout);
        this.rejectedPolicy = rejectedPolicy;
    }

    @Override
    public void execute(Runnable command) {
        if (delegate.isShutdown()) {
            throw new RejectedExecutionException("Executor has been shut down");
        }

        boolean acquired = false;
        try {
            if (rejectedPolicy == ThreadPoolRejectedPolicy.Block) {
                semaphore.acquire();
                acquired = true;
            } else {
                acquired = semaphore.tryAcquire(timeoutNanos, TimeUnit.NANOSECONDS);
            }

            if (!acquired) {
                if (rejectedPolicy == ThreadPoolRejectedPolicy.CallerRuns) {
                    callerRunsCount.increment();
                    command.run();
                    return;
                }
                rejectedCount.increment();
                throw new RejectedExecutionException("Executor saturated: timed out waiting for a permit");
            }

            boolean submitted = false;
            try {
                delegate.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        delegatedTaskCount.increment();
                        semaphore.release();
                    }
                });
                submitted = true;
            } finally {
                if (!submitted) {
                    semaphore.release();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for permit", e);
        }
    }

    // -- Metrics --

    /**
     * The maximum number of tasks that can be delegated to the underlying executor concurrently. CallerRuns tasks
     * execute outside this limit.
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    /**
     * The number of permits currently available.
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * The number of tasks currently delegated to the underlying executor.
     */
    public int getActiveCount() {
        return maxConcurrent - semaphore.availablePermits();
    }

    /**
     * The number of threads currently blocked waiting for a permit.
     */
    public int getWaitingCount() {
        return semaphore.getQueueLength();
    }

    /**
     * The number of times the timeout expired and a task fell back to running on the caller's thread.
     */
    public long getCallerRunsCount() {
        return callerRunsCount.sum();
    }

    /**
     * The number of tasks rejected because no permit was available within the timeout.
     */
    public long getRejectedCount() {
        return rejectedCount.sum();
    }

    /**
     * The total number of tasks that completed via the underlying executor (excludes caller-runs).
     */
    public long getDelegatedTaskCount() {
        return delegatedTaskCount.sum();
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        if (runnable instanceof Rejectable) {
            return new RejectableFutureTask<>(runnable, value);
        }
        return super.newTaskFor(runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof Rejectable) {
            return new RejectableFutureTask<>(callable);
        }
        return super.newTaskFor(callable);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public String toString() {
        return "BoundedExecutorService[active=" + getActiveCount()
               + ", max=" + maxConcurrent
               + ", waiting=" + getWaitingCount()
               + ", callerRuns=" + callerRunsCount.sum()
               + ", rejected=" + rejectedCount.sum()
               + ", delegated=" + delegatedTaskCount.sum() + "]";
    }
}
