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

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A task of a {@link Dst} run. Periodic tasks have a positive period (fixed rate) or a negative one (fixed delay).
 */
public final class DstTask<V> implements RunnableScheduledFuture<V> {

    private enum State {
        PENDING,
        RUNNING,
        DONE,
        FAILED,
        CANCELLED
    }

    private final Dst dst;
    private final DstExecutor executor;
    private final long period;
    private final Callable<V> callable;
    private final String description;
    private long due;
    private long sequence;
    private State state = State.PENDING;
    private V result;
    private Throwable failure;

    DstTask(Dst dst, DstExecutor executor, long due, long sequence, long period, Callable<V> callable,
            String description) {
        this.dst = dst;
        this.executor = executor;
        this.due = due;
        this.sequence = sequence;
        this.period = period;
        this.callable = callable;
        this.description = description;
    }

    DstExecutor executor() {
        return executor;
    }

    long due() {
        return due;
    }

    long sequence() {
        return sequence;
    }

    String description() {
        return description;
    }

    void reschedule(long now, long nextSequence) {
        due = period > 0 ? due + period : now + (-period);
        sequence = nextSequence;
        state = State.PENDING;
    }

    void runOnce() {
        if (state == State.CANCELLED) {
            return;
        }
        state = State.RUNNING;
        try {
            V value = callable.call();
            if (state == State.RUNNING) {
                result = value;
                state = isPeriodic() ? State.PENDING : State.DONE;
            }
        } catch (Throwable e) {
            failure = e;
            state = State.FAILED;
            dst.trace("task " + description + " failed: " + e);
        }
    }

    @Override
    public void run() {
        runOnce();
    }

    @Override
    public boolean isPeriodic() {
        return period != 0;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(due - dst.now(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (state == State.DONE || state == State.FAILED || state == State.CANCELLED) {
            return false;
        }
        state = State.CANCELLED;
        dst.remove(this);
        dst.trace("cancel " + description);
        return true;
    }

    @Override
    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state == State.DONE || state == State.FAILED || state == State.CANCELLED;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        if (!isDone()) {
            dst.await(this);
        }
        return report();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // virtual time: the timeout does not apply, the loop runs until the task is done or nothing can run
        return get();
    }

    private V report() throws ExecutionException {
        if (state == State.CANCELLED) {
            throw new CancellationException(description);
        }
        if (state == State.FAILED) {
            throw new ExecutionException(failure);
        }
        return result;
    }
}
