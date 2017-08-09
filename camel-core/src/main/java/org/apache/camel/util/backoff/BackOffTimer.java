/**
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple timer utility that use a linked {@link BackOff} to determine when
 * a task should be executed.
 */
public class BackOffTimer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackOffTimer.class);

    private final ScheduledExecutorService scheduler;

    public BackOffTimer(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Schedule the given function/task to be executed some time in the future
     * according to the given backOff.
     */
    public Task schedule(BackOff backOff, ThrowingFunction<BackOffContext, Boolean, Exception> function) {
        final TaskImpl task = new TaskImpl(backOff, function);

        long delay = task.getContext().next();
        if (delay != BackOff.NEVER) {
            scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
        } else {
            task.complete();
        }

        return task;
    }

    // ****************************************
    // TimerTask
    // ****************************************

    public interface Task {
        /**
         * Gets the {@link BackOffContext} associated with this task.
         */
        BackOffContext getContext();

        /**
         * Cancel the task.
         */
        void cancel();

        /**
         * Action to execute when the context is completed (cancelled or exhausted)
         *
         * @param whenCompleted the consumer.
         */
        void whenComplete(BiConsumer<BackOffContext, Throwable> whenCompleted);
    }

    // ****************************************
    // TimerTask
    // ****************************************

    private final class TaskImpl implements Task, Runnable {
        private final BackOffContext context;
        private final ThrowingFunction<BackOffContext, Boolean, Exception> function;
        private final AtomicReference<ScheduledFuture<?>> futureRef;
        private final List<BiConsumer<BackOffContext, Throwable>> consumers;

        TaskImpl(BackOff backOff, ThrowingFunction<BackOffContext, Boolean, Exception> function) {
            this.context = new BackOffContext(backOff);
            this.function = function;
            this.consumers = new ArrayList<>();
            this.futureRef = new AtomicReference<>();
        }

        @Override
        public void run() {
            if (context.getStatus() == BackOffContext.Status.Active) {
                try {
                    final long currentTime = System.currentTimeMillis();

                    context.setLastAttemptTime(currentTime);

                    if (function.apply(context)) {
                        long delay = context.next();
                        if (context.getStatus() != BackOffContext.Status.Active) {
                            // if the call to next makes the context not more
                            // active, signal task completion.
                            complete();
                        } else {
                            context.setNextAttemptTime(currentTime + delay);

                            // Cache the scheduled future so it can be cancelled
                            // later by Task.cancel()
                            futureRef.lazySet(scheduler.schedule(this, delay, TimeUnit.MILLISECONDS));
                        }
                    } else {
                        // if the function return false no more attempts should
                        // be made so stop the context.
                        context.stop();

                        // and signal the task as completed.
                        complete();
                    }
                } catch (Exception e) {
                    context.stop();
                    consumers.forEach(c -> c.accept(context, e));
                }
            }
        }

        @Override
        public BackOffContext getContext() {
            return context;
        }

        @Override
        public void cancel() {
            context.stop();

            ScheduledFuture<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);
            }

            // signal task completion on cancel.
            complete();
        }

        @Override
        public void whenComplete(BiConsumer<BackOffContext, Throwable> whenCompleted) {
            synchronized (this.consumers) {
                consumers.add(whenCompleted);
            }
        }

        void complete() {
            synchronized (this.consumers) {
                consumers.forEach(c -> c.accept(context, null));
            }
        }
    }
}
