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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.apache.camel.CamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.task.budget.TimeBoundedBudget;
import org.apache.camel.support.task.budget.TimeBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sleepless blocking task that runs in a thread in the background (using a scheduled thread pool). The execution is
 * processed until the task budget is either completed, or exhausted. All background tasks are constrained by a time
 * budget.
 */
public class BackgroundTask extends AbstractTask implements BlockingTask {

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
    private final CountDownLatch latch = new CountDownLatch(1);
    private Duration elapsed = Duration.ZERO;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    BackgroundTask(TimeBudget budget, ScheduledExecutorService service, String name) {
        super(name);
        this.budget = budget;
        this.service = Objects.requireNonNull(service);
    }

    private void runTaskWrapper(CamelContext camelContext, BooleanSupplier supplier) {
        LOG.trace("Current latch value: {}", latch.getCount());
        if (latch.getCount() == 0) {
            return;
        }

        TaskManagerRegistry registry = null;
        if (camelContext != null) {
            registry = PluginHelper.getTaskManagerRegistry(camelContext.getCamelContextExtension());
            registry.addTask(this);
        }
        if (!budget.next()) {
            LOG.warn("The task {} does not have more budget to continue running", getName());
            status = Status.Exhausted;
            completed.set(false);
            if (registry != null) {
                registry.removeTask(this);
            }
            latch.countDown();
            return;
        }

        lastAttemptTime = System.currentTimeMillis();
        if (firstAttemptTime < 0) {
            firstAttemptTime = lastAttemptTime;
        }
        try {
            if (doRun(supplier)) {
                status = Status.Completed;
                completed.set(true);
                if (registry != null) {
                    registry.removeTask(this);
                }
                latch.countDown();
                LOG.trace("Task {} succeeded and the current task is unscheduled: {}", getName(), latch.getCount());
            }
        } catch (Exception e) {
            status = Status.Failed;
            cause = e;
            throw e;
        }
        nextAttemptTime = lastAttemptTime + budget.interval();
    }

    /**
     * Schedules the task to be run
     *
     * @param  camelContext the camel context
     * @param  supplier     the task as a boolean supplier. The result is used to check if the task has completed or
     *                      not. The supplier must return true if the execution has completed or false otherwise.
     * @return              a future for the task
     */
    public Future<?> schedule(CamelContext camelContext, BooleanSupplier supplier) {
        running.set(true);
        return service.scheduleWithFixedDelay(
                () -> runTaskWrapper(camelContext, supplier),
                budget.initialDelay(),
                budget.interval(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean run(CamelContext camelContext, BooleanSupplier supplier) {
        running.set(true);
        Future<?> task = service.scheduleWithFixedDelay(
                () -> runTaskWrapper(camelContext, supplier),
                budget.initialDelay(),
                budget.interval(),
                TimeUnit.MILLISECONDS);
        waitForTaskCompletion(camelContext, task);
        return completed.get();
    }

    protected boolean doRun(BooleanSupplier supplier) {
        try {
            cause = null;
            return supplier.getAsBoolean();
        } catch (TaskRunFailureException e) {
            LOG.debug(
                    "Task {} failed at {} iterations and will attempt again on next interval: {}",
                    getName(),
                    budget.iteration(),
                    e.getMessage());
            cause = e;
            return false;
        }
    }

    private void waitForTaskCompletion(CamelContext camelContext, Future<?> task) {
        try {
            // We need it to be cancellable/non-runnable after reaching a certain point, and it needs to be
            // deterministic.
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

            TaskManagerRegistry registry = null;
            if (camelContext != null) {
                registry = PluginHelper.getTaskManagerRegistry(camelContext.getCamelContextExtension());
            }
            if (registry != null) {
                registry.removeTask(this);
            }

            task.cancel(true);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for the repeatable task to execute: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } finally {
            elapsed = budget.elapsed();
            running.set(false);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Duration elapsed() {
        return elapsed;
    }

    @Override
    public int iteration() {
        return budget.iteration();
    }

    @Override
    public long getCurrentDelay() {
        return budget.interval();
    }
}
