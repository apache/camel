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

package org.apache.camel.support.task;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.camel.support.task.budget.TimeBoundedBudget;
import org.apache.camel.support.task.budget.TimeBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sleepless blocking task that runs in a Thread the background. The execution is blocked until the task budget is
 * exhausted. All background tasks are constrained by a time budget.
 */
public class BackgroundTask implements BlockingTask {

    /**
     * A builder helper for building new background tasks
     */
    public static class BackgroundTaskBuilder extends AbstractTaskBuilder<BackgroundTask> {
        private TimeBudget budget;
        private ScheduledExecutorService service;

        /**
         * Sets a time budget for the task
         *
         * @param timeBudget the time budget
         */
        public BackgroundTaskBuilder withBudget(TimeBudget timeBudget) {
            this.budget = timeBudget;

            return this;
        }

        /**
         * Sets an executor service manager for managing the threads
         *
         * @param service an instance of an executor service to use
         */
        public BackgroundTaskBuilder withScheduledExecutor(ScheduledExecutorService service) {
            this.service = service;

            return this;
        }

        @Override
        public BackgroundTask build() {
            return new BackgroundTask(budget, service, getName());
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundTask.class);

    private final TimeBudget budget;
    private final ScheduledExecutorService service;
    private final String name;
    private final CountDownLatch latch = new CountDownLatch(1);

    private Duration elapsed = Duration.ZERO;
    private boolean completed;

    BackgroundTask(TimeBudget budget, ScheduledExecutorService service, String name) {
        this.budget = budget;
        this.service = Objects.requireNonNull(service);
        this.name = name;
    }

    private void runTaskWrapper(BooleanSupplier supplier) {
        LOG.trace("Current latch value: {}", latch.getCount());
        if (latch.getCount() == 0) {
            return;
        }

        if (!budget.next()) {
            LOG.warn("The task {} does not have more budget to continue running", name);
            completed = false;
            latch.countDown();
            return;
        }

        if (supplier.getAsBoolean()) {
            completed = true;
            latch.countDown();
            LOG.trace("Task {} succeeded and the current task won't be schedulable anymore: {}", name, latch.getCount());
        }
    }

    @Override
    public boolean run(BooleanSupplier supplier) {

        Future<?> task = service.scheduleAtFixedRate(() -> runTaskWrapper(supplier), budget.initialDelay(),
                budget.interval(), TimeUnit.MILLISECONDS);

        waitForTaskCompletion(task);
        return completed;
    }

    private void waitForTaskCompletion(Future<?> task) {
        try {
            // We need it to be cancellable/non-runnable after reaching a certain point, and it needs to be deterministic.
            // This is why we ignore the ScheduledFuture returned and implement the go/no-go using a latch.
            if (budget.maxDuration() == TimeBoundedBudget.UNLIMITED_DURATION) {
                latch.await();
            } else {
                if (!latch.await(budget.maxDuration(), TimeUnit.MILLISECONDS)) {
                    LOG.debug("Timeout out waiting for the completion of the task");
                } else {
                    LOG.debug("The task has finished the execution and it is ready to continue");
                }
            }

            task.cancel(true);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for the repeatable task to execute: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } finally {
            elapsed = budget.elapsed();
        }
    }

    @Override
    public Duration elapsed() {
        return elapsed;
    }
}
