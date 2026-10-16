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
package org.apache.camel.test.dst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An executor whose tasks run on the loop of a {@link Dst} run. {@code threads} is the number of tasks of this executor
 * that may be in progress at once (a task blocked in a fake external call keeps its thread).
 */
public final class DstExecutor implements ScheduledExecutorService {

    private final Dst dst;
    private final String label;
    private final int threads;
    private int busy;
    private boolean shutdown;

    DstExecutor(Dst dst, String label, int threads) {
        this.dst = dst;
        this.label = label;
        this.threads = Math.max(1, threads);
    }

    public String label() {
        return label;
    }

    int threads() {
        return threads;
    }

    int busy() {
        return busy;
    }

    void enter() {
        busy++;
    }

    void exit() {
        busy--;
    }

    private static String describe(Object task) {
        String name = task.getClass().getName();
        int lambda = name.indexOf("$$Lambda");
        if (lambda > 0) {
            name = name.substring(0, lambda) + "$$Lambda";
        }
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private void checkNotShutdown() {
        if (shutdown) {
            throw new RejectedExecutionException("DST executor " + label + " is shut down");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    public <V> DstTask<V> schedule(String description, long delayMillis, Callable<V> callable) {
        checkNotShutdown();
        return dst.schedule(this, delayMillis, 0, callable, description);
    }

    public DstTask<Object> schedule(String description, long delayMillis, Runnable runnable) {
        return schedule(description, delayMillis, Executors.callable(runnable));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedule(describe(command), unit.toMillis(delay), command);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return schedule(describe(callable), unit.toMillis(delay), callable);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        checkNotShutdown();
        return dst.schedule(this, unit.toMillis(initialDelay), Math.max(1, unit.toMillis(period)),
                Executors.callable(command), describe(command));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        checkNotShutdown();
        return dst.schedule(this, unit.toMillis(initialDelay), -Math.max(1, unit.toMillis(delay)),
                Executors.callable(command), describe(command));
    }

    @Override
    public void execute(Runnable command) {
        schedule(describe(command), 0, command);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return schedule(describe(task), 0, task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return schedule(describe(task), 0, Executors.callable(task, result));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return schedule(describe(task), 0, task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> answer = new ArrayList<>();
        for (Callable<T> task : tasks) {
            answer.add(submit(task));
        }
        for (Future<T> f : answer) {
            try {
                f.get();
            } catch (ExecutionException e) {
                // reported by the future
            }
        }
        return answer;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        ExecutionException last = null;
        for (Callable<T> task : tasks) {
            try {
                return submit(task).get();
            } catch (ExecutionException e) {
                last = e;
            }
        }
        throw last != null ? last : new ExecutionException(new IllegalArgumentException("no tasks"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException {
        return invokeAny(tasks);
    }

    @Override
    public void shutdown() {
        if (!shutdown) {
            shutdown = true;
            dst.trace("shutdown executor " + label);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        dst.dropTasksOf(this);
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown && busy == 0;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public String toString() {
        return "DstExecutor[" + label + "]";
    }
}
