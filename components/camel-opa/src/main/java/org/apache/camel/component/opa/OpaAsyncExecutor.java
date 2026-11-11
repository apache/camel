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
package org.apache.camel.component.opa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/** Managed bounded authorization work; cancelled or stopped tasks can never commit a late result to the caller. */
public final class OpaAsyncExecutor implements AutoCloseable {
    @FunctionalInterface
    public interface Action {
        CompletableFuture<?> run() throws Exception;
    }

    @FunctionalInterface
    public interface Preparation {
        Action prepare(Exchange snapshot) throws Exception;
    }

    private final CamelContext context;
    private final ExecutorService executor;
    private final ScheduledExecutorService deadlines;
    private final ExecutorService completions;
    private final java.util.concurrent.BlockingQueue<Runnable> completionQueue;
    private final java.util.concurrent.Semaphore workerSlots;
    private final long timeout;
    private final java.util.concurrent.Semaphore permits;
    private final Set<Task> tasks = ConcurrentHashMap.newKeySet();
    private final Object lifecycle = new Object();
    private volatile boolean closed;

    public OpaAsyncExecutor(CamelContext context, Object owner, int poolSize, int queueSize, long timeout) {
        if (poolSize <= 0 || queueSize <= 0 || timeout <= 0) {
            throw new IllegalArgumentException("asyncPoolSize, asyncQueueSize and asyncTimeout must be positive");
        }
        this.context = context;
        this.timeout = timeout;
        int capacity = Math.addExact(poolSize, queueSize);
        permits = new java.util.concurrent.Semaphore(capacity);
        workerSlots = new java.util.concurrent.Semaphore(poolSize);
        completionQueue = new java.util.concurrent.ArrayBlockingQueue<>(capacity);
        ThreadPoolProfile profile = profile("OpaAuthorization", poolSize, capacity);
        executor = context.getExecutorServiceManager().newThreadPool(owner, "OpaAuthorization", profile);
        deadlines = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(owner, "OpaAuthorizationDeadline");
        int completionWorkers = Math.min(capacity, Math.max(2, poolSize));
        completions = context.getExecutorServiceManager().newThreadPool(owner, "OpaAuthorizationCompletion",
                profile("OpaAuthorizationCompletion", completionWorkers, capacity));
        for (int index = 0; index < completionWorkers; index++) {
            completions.execute(this::completeWork);
        }
    }

    private static ThreadPoolProfile profile(String name, int workers, int capacity) {
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(workers);
        profile.setMaxPoolSize(workers);
        profile.setMaxQueueSize(capacity);
        // Virtual-thread BoundedExecutorService uses keep-alive as its admission wait: zero means immediate rejection.
        profile.setKeepAliveTime(0L);
        profile.setTimeUnit(TimeUnit.MILLISECONDS);
        profile.setAllowCoreThreadTimeOut(false);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        return profile;
    }

    private void completeWork() {
        while (!closed || !tasks.isEmpty() || !completionQueue.isEmpty()) {
            try {
                Runnable completion = completionQueue.poll(100, TimeUnit.MILLISECONDS);
                if (completion != null) {
                    try {
                        completion.run();
                    } catch (Throwable ignored) {
                        // A callback is external routing code. Its failure must not retire the completion worker.
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static CompletableFuture<Exchange> processFuture(
            Exchange exchange, org.apache.camel.AsyncProcessor processor,
            OpaAsyncExecutor executor) {
        CompletableFuture<Exchange> result = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean interrupt) {
                if (isDone()) {
                    return false;
                }
                if (executor == null) {
                    return super.cancel(interrupt);
                }
                AtomicBoolean cancelled = new AtomicBoolean();
                executor.cancel(exchange, new CancellationException("Asynchronous authorization cancelled"),
                        () -> cancelled.set(super.cancel(interrupt)));
                return cancelled.get();
            }
        };
        processor.process(exchange, doneSync -> result.complete(exchange));
        return result;
    }

    public boolean process(Exchange original, AsyncCallback callback, Preparation preparation) {
        if (!permits.tryAcquire()) {
            original.setException(new java.util.concurrent.RejectedExecutionException("Authorization capacity is exhausted"));
            callback.done(true);
            return true;
        }
        Task task;
        try {
            Exchange snapshot = ExchangeHelper.createCopy(original, true);
            // A copied authorization request must not complete the caller's unit of work.
            snapshot.getExchangeExtension().setUnitOfWork(null);
            snapshot.getMessage().setBody(copyValue(snapshot.getMessage().getBody()));
            snapshot.getMessage().setHeaders(copyMap(snapshot.getMessage().getHeaders()));
            snapshot.getProperties().replaceAll((key, value) -> copyValue(value));
            task = new Task(original, snapshot, callback, preparation.prepare(snapshot));
        } catch (Exception e) {
            permits.release();
            original.setException(e);
            callback.done(true);
            return true;
        }
        synchronized (lifecycle) {
            if (closed) {
                task.finish(new CancellationException("Authorization executor is stopped"), true);
                return true;
            }
            tasks.add(task);
            try {
                task.deadline = deadlines.schedule(
                        () -> task.finish(new TimeoutException("Asynchronous authorization deadline exceeded"), false),
                        timeout, TimeUnit.MILLISECONDS);
                executor.execute(task);
                return false;
            } catch (Exception e) {
                task.finish(e, true);
                return true;
            }
        }
    }

    public void cancel(Exchange exchange) {
        cancel(exchange, new CancellationException("Asynchronous authorization cancelled"));
    }

    public void cancel(Exchange exchange, Throwable failure) {
        cancel(exchange, failure, null);
    }

    private void cancel(Exchange exchange, Throwable failure, Runnable onClaim) {
        tasks.forEach(task -> {
            if (task.original == exchange) {
                task.finish(failure, false, onClaim);
            }
        });
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            closed = true;
        }
        tasks.forEach(task -> task.finish(new CancellationException("Authorization executor stopped"), false));
        context.getExecutorServiceManager().shutdownNow(executor);
        context.getExecutorServiceManager().shutdownNow(deadlines);
        // Existing completion workers drain every admitted callback; do not discard queued routing continuations.
        completions.shutdown();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return (Map<String, Object>) copyValue(values);
    }

    public static Object snapshotValue(Object value) {
        return copyValue(value);
    }

    private static Object copyValue(Object value) {
        return copyValue(value, new java.util.IdentityHashMap<>());
    }

    private static Object copyValue(Object value, java.util.IdentityHashMap<Object, Object> visited) {
        if (visited.containsKey(value)) {
            return visited.get(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            visited.put(value, copy);
            map.forEach((key, item) -> copy.put(key, copyValue(item, visited)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            visited.put(value, copy);
            list.forEach(item -> copy.add(copyValue(item, visited)));
            return copy;
        }
        return value;
    }

    private final class Task implements Runnable {
        private final Exchange original;
        private final Exchange snapshot;
        private final AsyncCallback callback;
        private final Action action;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean callbackFinished = new AtomicBoolean();
        private final AtomicBoolean permitReleased = new AtomicBoolean();
        private volatile boolean executionActive;
        private volatile boolean submissionReturned;
        private final AtomicReference<Thread> runner = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<?>> request = new AtomicReference<>();
        private volatile ScheduledFuture<?> deadline;

        Task(Exchange original, Exchange snapshot, AsyncCallback callback, Action action) {
            this.original = original;
            this.snapshot = snapshot;
            this.callback = callback;
            this.action = action;
        }

        @Override
        public void run() {
            if (finished.get()) {
                return;
            }
            executionActive = true;
            runner.set(Thread.currentThread());
            boolean workerAcquired = false;
            try {
                if (finished.get()) {
                    return;
                }
                // Also honor asyncPoolSize when Camel uses its virtual-thread pool factory.
                workerSlots.acquire();
                workerAcquired = true;
                if (finished.get()) {
                    return;
                }
                CompletableFuture<?> future = action.run();
                submissionReturned = true;
                request.set(future);
                if (finished.get()) {
                    future.cancel(true);
                    return;
                }
                future.whenComplete((value, failure) -> finish(failure, false));
            } catch (Exception e) {
                submissionReturned = true;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                finish(e, false);
            } finally {
                if (workerAcquired) {
                    workerSlots.release();
                }
                runner.set(null);
                executionActive = false;
                releasePermit();
            }
        }

        private void releasePermit() {
            // Logical timeout does not free physical capacity while an SDK call ignores interruption.
            if (finished.get() && callbackFinished.get() && (!executionActive || submissionReturned)
                    && permitReleased.compareAndSet(false, true)) {
                permits.release();
            }
        }

        void finish(Throwable error, boolean doneSync) {
            finish(error, doneSync, null);
        }

        void finish(Throwable error, boolean doneSync, Runnable onClaim) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (onClaim != null) {
                onClaim.run();
            }
            if (deadline != null) {
                deadline.cancel(false);
            }
            if (error != null) {
                Throwable failure = unwrap(error);
                original.setException(
                        original.getException() instanceof InterruptedException ? original.getException() : failure);
                if (executor instanceof java.util.concurrent.ThreadPoolExecutor pool) {
                    pool.remove(this);
                }
            }
            Runnable completion = () -> {
                try {
                    Throwable failure
                            = original.getException() instanceof InterruptedException ? original.getException() : error;
                    if (failure == null) {
                        try {
                            ExchangeHelper.copyResults(original, snapshot);
                        } catch (Throwable e) {
                            original.setException(e);
                        }
                    } else {
                        original.setException(unwrap(failure));
                        try {
                            CompletableFuture<?> pending = request.get();
                            if (pending != null) {
                                pending.cancel(true);
                            }
                        } catch (Throwable ignored) {
                            // Cancellation is best effort; a broken SDK future must not discard the caller's callback.
                        } finally {
                            Thread thread = runner.get();
                            if (thread != null && thread != Thread.currentThread()) {
                                thread.interrupt();
                            }
                        }
                    }
                    callback.done(doneSync);
                } finally {
                    callbackFinished.set(true);
                    tasks.remove(this);
                    releasePermit();
                }
            };
            if (doneSync) {
                completion.run();
            } else {
                // A task retains its admission permit until this callback returns, so at most capacity entries exist.
                completionQueue.add(completion);
            }
        }

        private Throwable unwrap(Throwable error) {
            return error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                    ? error.getCause() : error;
        }
    }
}
