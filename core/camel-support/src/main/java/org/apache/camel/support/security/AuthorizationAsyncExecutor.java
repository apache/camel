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
package org.apache.camel.support.security;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/**
 * Managed bounded authorization work. Cancelled tasks cannot commit a late result, and downstream routing does not
 * consume authorization admission capacity.
 *
 * @since 4.24
 */
public final class AuthorizationAsyncExecutor implements AutoCloseable {
    /** The authorization request: runs on a worker thread and returns the future of the remote decision. */
    @FunctionalInterface
    public interface Action {
        CompletableFuture<?> run() throws Exception;
    }

    /** Builds the {@link Action} for a snapshot of the exchange; runs on the admitting thread. */
    @FunctionalInterface
    public interface Preparation {
        Action prepare(Exchange snapshot) throws Exception;
    }

    private final CamelContext context;
    private final ExecutorService executor;
    private final ScheduledExecutorService deadlines;
    private final ExecutorService completions;
    private final Semaphore workerSlots;
    private final long timeout;
    private final Semaphore permits;
    private final Set<Task> tasks = ConcurrentHashMap.newKeySet();
    private final Object lifecycle = new Object();
    private volatile boolean closed;

    /**
     * Creates an executor with {@code poolSize} workers, {@code queueSize} queued admissions and a {@code timeout} in
     * milliseconds for each authorization. The pools come from the executor service manager of {@code context} on
     * behalf of {@code owner}.
     */
    public AuthorizationAsyncExecutor(CamelContext context, Object owner, int poolSize, int queueSize, long timeout) {
        if (poolSize <= 0 || queueSize <= 0 || timeout <= 0) {
            throw new IllegalArgumentException("asyncPoolSize, asyncQueueSize and asyncTimeout must be positive");
        }
        this.context = context;
        this.timeout = timeout;
        int capacity = Math.addExact(poolSize, queueSize);
        permits = new Semaphore(capacity);
        workerSlots = new Semaphore(poolSize);
        ThreadPoolProfile profile = profile("Authorization", poolSize, capacity);
        executor = context.getExecutorServiceManager().newThreadPool(owner, "Authorization", profile);
        deadlines = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(owner, "AuthorizationDeadline");
        // A continuation can run the rest of the route. It must not be limited by the authorization worker count.
        completions = context.getExecutorServiceManager().newCachedThreadPool(owner, "AuthorizationContinuation");
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

    /**
     * Runs {@code processor} for {@code exchange} and completes the returned future with the exchange when the
     * processor is done. Cancelling the future cancels the authorization that {@code executor} runs for the exchange: a
     * claimed task leaves the exchange to the canceller, as described in {@link #cancel(Exchange, Throwable)}.
     */
    public static CompletableFuture<Exchange> processFuture(
            Exchange exchange, AsyncProcessor processor,
            AuthorizationAsyncExecutor executor) {
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

    /**
     * Admits {@code original} for asynchronous authorization. A snapshot of the exchange is handed to
     * {@code preparation} here, the action runs on a worker, and {@code callback} runs on the continuation pool once
     * the decision is committed to the original exchange.
     *
     * @return {@code true} when the exchange was completed on the calling thread, because the capacity is exhausted,
     *         the preparation failed or the executor is stopped
     */
    public boolean process(Exchange original, AsyncCallback callback, Preparation preparation) {
        if (!permits.tryAcquire()) {
            original.setException(new RejectedExecutionException("Authorization capacity is exhausted"));
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
            Map<String, Object> initialHeaders = new LinkedHashMap<>(snapshot.getMessage().getHeaders());
            Map<String, Object> initialProperties = new LinkedHashMap<>(snapshot.getProperties());
            Object initialBody = snapshot.getMessage().getBody();
            task = new Task(
                    original, snapshot, callback, preparation.prepare(snapshot),
                    initialBody, initialHeaders, initialProperties);
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

    /**
     * Bounds {@code source} by {@code timeoutMillis}: the returned future fails with a {@link TimeoutException} and
     * cancels {@code source} when the deadline passes first, and cancelling it cancels {@code source}.
     */
    public <T> CompletableFuture<T> bound(CompletableFuture<T> source, long timeoutMillis) {
        Objects.requireNonNull(source, "Authorization request future");
        RequestFuture<T> result = new RequestFuture<>(source);
        ScheduledFuture<?> deadline;
        try {
            deadline = deadlines.schedule(() -> {
                if (result.completeExceptionally(new TimeoutException("Authorization request deadline exceeded"))) {
                    source.cancel(true);
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            source.cancel(true);
            throw e;
        }
        result.whenComplete((value, failure) -> deadline.cancel(false));
        source.whenComplete((value, failure) -> {
            deadline.cancel(false);
            if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                result.complete(value);
            }
        });
        return result;
    }

    private static class RequestFuture<T> extends CompletableFuture<T> {
        private final CompletableFuture<?> source;

        RequestFuture(CompletableFuture<?> source) {
            this.source = source;
        }

        @Override
        public boolean cancel(boolean interrupt) {
            if (!super.cancel(interrupt)) {
                return false;
            }
            source.cancel(interrupt);
            return true;
        }

        @Override
        public <U> CompletableFuture<U> newIncompleteFuture() {
            return new RequestFuture<>(this);
        }
    }

    /**
     * Cancels the pending authorization of {@code exchange} with a {@link CancellationException}.
     *
     * @return {@code true} when a pending task was claimed, see {@link #cancel(Exchange, Throwable)}
     */
    public boolean cancel(Exchange exchange) {
        return cancel(exchange, new CancellationException("Asynchronous authorization cancelled"), null);
    }

    /**
     * Cancels the pending authorization of {@code exchange}. A claimed task leaves the exchange to the calling thread:
     * {@code failure} is set on the exchange before this method returns (an {@link InterruptedException} already on the
     * exchange is kept), the pending request is cancelled and its worker interrupted from the continuation pool, and
     * the callback given to {@link #process(Exchange, AsyncCallback, Preparation)} is not invoked, so a caller that
     * already routes the exchange is not written to again.
     *
     * @return {@code true} when a pending task was claimed, {@code false} when it had already completed or the exchange
     *         has no task
     */
    public boolean cancel(Exchange exchange, Throwable failure) {
        return cancel(exchange, failure, null);
    }

    private boolean cancel(Exchange exchange, Throwable failure, Runnable onClaim) {
        boolean claimed = false;
        for (Task task : tasks) {
            if (task.original == exchange && task.abandon(failure, onClaim)) {
                claimed = true;
            }
        }
        return claimed;
    }

    /** Stops the executor: pending tasks complete through their callbacks with a {@link CancellationException}. */
    @Override
    public void close() {
        synchronized (lifecycle) {
            closed = true;
        }
        tasks.forEach(task -> task.finish(new CancellationException("Authorization executor stopped"), false));
        context.getExecutorServiceManager().shutdownNow(executor);
        context.getExecutorServiceManager().shutdownNow(deadlines);
        shutdownCompletionsIfDrained();
    }

    private void shutdownCompletionsIfDrained() {
        // A winner can still be between claiming its task and submitting the callback. Keep the executor alive until
        // every admitted decision has been committed, then drain callbacks through Camel without interrupting them.
        if (closed && tasks.isEmpty()) {
            context.getExecutorServiceManager().shutdown(completions);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return (Map<String, Object>) copyValue(values);
    }

    /** Copies {@code value} the way a snapshot does: maps and lists recursively, other values are shared. */
    public static Object snapshotValue(Object value) {
        return copyValue(value);
    }

    private static Object copyValue(Object value) {
        return copyValue(value, new IdentityHashMap<>());
    }

    private static Object copyValue(Object value, IdentityHashMap<Object, Object> visited) {
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
        private final AtomicBoolean decisionCommitted = new AtomicBoolean();
        private final Object initialBody;
        private final Map<String, Object> initialHeaders;
        private final Map<String, Object> initialProperties;
        private final Object runnerLock = new Object();
        private final AtomicBoolean permitReleased = new AtomicBoolean();
        private volatile boolean executionActive;
        private volatile boolean submissionReturned;
        private final AtomicReference<Thread> runner = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<?>> request = new AtomicReference<>();
        private volatile ScheduledFuture<?> deadline;

        Task(Exchange original, Exchange snapshot, AsyncCallback callback, Action action,
             Object initialBody, Map<String, Object> initialHeaders, Map<String, Object> initialProperties) {
            this.original = original;
            this.snapshot = snapshot;
            this.callback = callback;
            this.action = action;
            this.initialBody = initialBody;
            this.initialHeaders = initialHeaders;
            this.initialProperties = initialProperties;
        }

        @Override
        public void run() {
            if (finished.get()) {
                return;
            }
            synchronized (runnerLock) {
                if (finished.get()) {
                    return;
                }
                executionActive = true;
                runner.set(Thread.currentThread());
            }
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
                synchronized (runnerLock) {
                    runner.set(null);
                    executionActive = false;
                }
                releasePermit();
            }
        }

        private void releasePermit() {
            // Logical timeout does not free physical capacity while an SDK call ignores interruption.
            if (finished.get() && decisionCommitted.get() && (!executionActive || submissionReturned)
                    && permitReleased.compareAndSet(false, true)) {
                permits.release();
            }
        }

        void finish(Throwable error, boolean doneSync) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (deadline != null) {
                deadline.cancel(false);
            }
            if (error != null) {
                Throwable failure = unwrap(error);
                original.setException(
                        original.getException() instanceof InterruptedException ? original.getException() : failure);
                if (executor instanceof ThreadPoolExecutor pool) {
                    pool.remove(this);
                }
            }
            Runnable completion = () -> {
                try {
                    Throwable failure
                            = original.getException() instanceof InterruptedException ? original.getException() : error;
                    if (failure == null) {
                        try {
                            commitResults();
                        } catch (Throwable e) {
                            original.setException(e);
                        }
                    } else {
                        original.setException(unwrap(failure));
                        cancelRequest();
                    }
                } finally {
                    release();
                }
                callback.done(doneSync);
            };
            if (doneSync) {
                completion.run();
            } else {
                completions.execute(completion);
            }
        }

        /**
         * Claims the task for a cancelling caller, which owns the exchange from now on: the failure is written here, on
         * the calling thread, the continuation pool only cancels the request and releases the capacity, and the
         * callback is not invoked.
         */
        boolean abandon(Throwable failure, Runnable onClaim) {
            if (!finished.compareAndSet(false, true)) {
                return false;
            }
            if (onClaim != null) {
                onClaim.run();
            }
            if (deadline != null) {
                deadline.cancel(false);
            }
            if (!(original.getException() instanceof InterruptedException)) {
                original.setException(unwrap(failure));
            }
            if (executor instanceof ThreadPoolExecutor pool) {
                pool.remove(this);
            }
            completions.execute(() -> {
                try {
                    cancelRequest();
                } finally {
                    release();
                }
            });
            return true;
        }

        private void cancelRequest() {
            try {
                CompletableFuture<?> pending = request.get();
                if (pending != null) {
                    pending.cancel(true);
                }
            } catch (Throwable ignored) {
                // Cancellation is best effort; a broken SDK future must not discard the caller's callback.
            } finally {
                synchronized (runnerLock) {
                    Thread thread = runner.get();
                    if (thread != null && thread != Thread.currentThread()) {
                        thread.interrupt();
                    }
                }
            }
        }

        private void release() {
            // Authorization ends before the routing continuation; downstream work owns its own concurrency.
            decisionCommitted.set(true);
            tasks.remove(this);
            releasePermit();
            shutdownCompletionsIfDrained();
        }

        private void commitResults() {
            // Input containers were snapshotted only to isolate authorization. Preserve the caller's references unless
            // the operation explicitly replaced an output, rather than copying the entire snapshot exchange back.
            if (snapshot.getMessage().getBody() != initialBody) {
                original.getMessage().setBody(snapshot.getMessage().getBody());
            }
            commitMap(original.getMessage().getHeaders(), snapshot.getMessage().getHeaders(), initialHeaders);
            commitMap(original.getProperties(), snapshot.getProperties(), initialProperties);
            original.setException(snapshot.getException());
        }

        private void commitMap(Map<String, Object> destination, Map<String, Object> result, Map<String, Object> initial) {
            initial.keySet().stream().filter(key -> !result.containsKey(key)).forEach(destination::remove);
            result.forEach((key, value) -> {
                if (!initial.containsKey(key) || value != initial.get(key)) {
                    destination.put(key, value);
                }
            });
        }

        private Throwable unwrap(Throwable error) {
            return error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
        }
    }
}
