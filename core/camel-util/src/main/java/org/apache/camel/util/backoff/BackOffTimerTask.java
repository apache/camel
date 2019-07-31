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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.apache.camel.util.function.ThrowingFunction;

final class BackOffTimerTask implements BackOffTimer.Task, Runnable {
    private final BackOff backOff;
    private final ScheduledExecutorService scheduler;
    private final ThrowingFunction<BackOffTimer.Task, Boolean, Exception> function;
    private final AtomicReference<ScheduledFuture<?>> futureRef;
    private final List<BiConsumer<BackOffTimer.Task, Throwable>> consumers;

    private Status status;
    private long currentAttempts;
    private long currentDelay;
    private long currentElapsedTime;
    private long lastAttemptTime;
    private long nextAttemptTime;

    BackOffTimerTask(BackOff backOff, ScheduledExecutorService scheduler, ThrowingFunction<BackOffTimer.Task, Boolean, Exception> function) {
        this.backOff = backOff;
        this.scheduler = scheduler;
        this.status = Status.Active;

        this.currentAttempts = 0;
        this.currentDelay = backOff.getDelay().toMillis();
        this.currentElapsedTime = 0;
        this.lastAttemptTime = BackOff.NEVER;
        this.nextAttemptTime = BackOff.NEVER;

        this.function = function;
        this.consumers = new ArrayList<>();
        this.futureRef = new AtomicReference<>();
    }

    // *****************************
    // Properties
    // *****************************

    @Override
    public BackOff getBackOff() {
        return backOff;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public long getCurrentAttempts() {
        return currentAttempts;
    }

    @Override
    public long getCurrentDelay() {
        return currentDelay;
    }

    @Override
    public long getCurrentElapsedTime() {
        return currentElapsedTime;
    }

    @Override
    public long getLastAttemptTime() {
        return lastAttemptTime;
    }

    @Override
    public long getNextAttemptTime() {
        return nextAttemptTime;
    }

    @Override
    public void reset() {
        this.currentAttempts = 0;
        this.currentDelay = 0;
        this.currentElapsedTime = 0;
        this.lastAttemptTime = BackOff.NEVER;
        this.nextAttemptTime = BackOff.NEVER;
        this.status = Status.Active;
    }

    @Override
    public void cancel() {
        stop();

        ScheduledFuture<?> future = futureRef.get();
        if (future != null) {
            future.cancel(true);
        }

        // signal task completion on cancel.
        complete(null);
    }

    @Override
    public void whenComplete(BiConsumer<BackOffTimer.Task, Throwable> whenCompleted) {
        synchronized (this.consumers) {
            consumers.add(whenCompleted);
        }
    }

    // *****************************
    // Task execution
    // *****************************

    @Override
    public void run() {
        if (status == Status.Active) {
            try {
                lastAttemptTime = System.currentTimeMillis();

                if (function.apply(this)) {
                    long delay = next();
                    if (status != Status.Active) {
                        // if the call to next makes the context not more
                        // active, signal task completion.
                        complete(null);
                    } else {
                        nextAttemptTime = lastAttemptTime + delay;

                        // Cache the scheduled future so it can be cancelled
                        // later by Task.cancel()
                        futureRef.lazySet(scheduler.schedule(this, delay, TimeUnit.MILLISECONDS));
                    }
                } else {
                    stop();

                    // if the function return false no more attempts should
                    // be made so stop the context.
                    complete(null);
                }
            } catch (Exception e) {
                stop();

                complete(e);
            }
        }
    }

    void stop() {
        this.currentAttempts = 0;
        this.currentDelay = BackOff.NEVER;
        this.currentElapsedTime = 0;
        this.lastAttemptTime = BackOff.NEVER;
        this.nextAttemptTime = BackOff.NEVER;
        this.status = Status.Inactive;
    }

    void complete(Throwable throwable) {
        synchronized (this.consumers) {
            consumers.forEach(c -> c.accept(this, throwable));
        }
    }

    // *****************************
    // Impl
    // *****************************

    /**
     * Return the number of milliseconds to wait before retrying the operation
     * or ${@link BackOff#NEVER} to indicate that no further attempt should be
     * made.
     */
    long next() {
        // A call to next when currentDelay is set to NEVER has no effects
        // as this means that either the timer is exhausted or it has explicit
        // stopped
        if (status == Status.Active) {

            currentAttempts++;

            if (currentAttempts > backOff.getMaxAttempts()) {
                currentDelay = BackOff.NEVER;
                status = Status.Exhausted;
            } else if (currentElapsedTime > backOff.getMaxElapsedTime().toMillis()) {
                currentDelay = BackOff.NEVER;
                status = Status.Exhausted;
            } else {
                if (currentDelay <= backOff.getMaxDelay().toMillis()) {
                    currentDelay = (long) (currentDelay * backOff.getMultiplier());
                }

                currentElapsedTime += currentDelay;
            }
        }

        return currentDelay;
    }

    @Override
    public String toString() {
        return "BackOffTimerTask["
            + "status=" + status
            + ", currentAttempts=" + currentAttempts
            + ", currentDelay=" + currentDelay
            + ", currentElapsedTime=" + currentElapsedTime
            + ", lastAttemptTime=" + lastAttemptTime
            + ", nextAttemptTime=" + nextAttemptTime
            + ']';
    }
}
