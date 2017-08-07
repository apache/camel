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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.function.ThrowingFunction;

/**
 * A simple timer utility that use a linked {@link BackOff} to determine when
 * a task should be executed.
 */
public class BackOffTimer {
    private final ScheduledExecutorService scheduler;

    public BackOffTimer(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Schedule the given function/task to be executed some time in the future
     * according to the given backOff.
     */
    public CompletableFuture<BackOffContext> schedule(BackOff backOff, ThrowingFunction<BackOffContext, Boolean, Exception> function) {
        final BackOffContext context = new BackOffContext(backOff);
        final Task task = new Task(context, function);

        long delay = context.next();
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

    private final class Task extends CompletableFuture<BackOffContext> implements Runnable {
        private final BackOffContext context;
        private final ThrowingFunction<BackOffContext, Boolean, Exception> function;

        Task(BackOffContext context, ThrowingFunction<BackOffContext, Boolean, Exception> function) {
            this.context = context;
            this.function = function;
        }

        @Override
        public void run() {
            if (context.isExhausted() || isDone() || isCancelled()) {
                if (!isDone()) {
                    complete();
                }

                return;
            }

            try {
                if (function.apply(context)) {
                    long delay = context.next();
                    if (context.isExhausted()) {
                        complete();
                    } else if (!context.isExhausted() && !isDone() && !isCancelled()) {
                        scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
                    }
                } else {
                    complete();
                }
            } catch (Exception e) {
                completeExceptionally(e);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            context.stop();

            return super.cancel(mayInterruptIfRunning);
        }

        boolean complete() {
            return super.complete(context);
        }
    }
}
