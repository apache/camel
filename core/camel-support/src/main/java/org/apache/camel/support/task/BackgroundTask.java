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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

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
         * @param  timeBudget the time budget
         * @return
         */
        public BackgroundTaskBuilder withBudget(TimeBudget timeBudget) {
            this.budget = timeBudget;

            return this;
        }

        /**
         * Sets an executor service manager for managing the threads
         *
         * @param  service an instance of an executor service to use
         * @return
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
    private Duration elapsed = Duration.ZERO;

    BackgroundTask(TimeBudget budget, ScheduledExecutorService service, String name) {
        this.budget = budget;
        this.service = Objects.requireNonNull(service);
        this.name = name;
    }

    private <T> void runTaskWrapper(CountDownLatch latch, Predicate<T> predicate, T payload) {
        LOG.trace("Current latch value: {}", latch.getCount());

        if (latch.getCount() == 0) {
            return;
        }

        if (!budget.next()) {
            LOG.warn("The task {} does not have more budget to continue running", name);

            return;
        }

        if (predicate.test(payload)) {
            latch.countDown();
            LOG.trace("Task {} has succeeded and the current task won't be schedulable anymore: {}", name, latch.getCount());
        }
    }

    private void runTaskWrapper(CountDownLatch latch, BooleanSupplier supplier) {
        LOG.trace("Current latch value: {}", latch.getCount());
        if (latch.getCount() == 0) {
            return;
        }

        if (!budget.next()) {
            LOG.warn("The task {} does not have more budget to continue running", name);

            return;
        }

        if (supplier.getAsBoolean()) {
            latch.countDown();
            LOG.trace("Task {} succeeded and the current task won't be schedulable anymore: {}", name, latch.getCount());
        }
    }

    @Override
    public <T> boolean run(Predicate<T> predicate, T payload) {
        CountDownLatch latch = new CountDownLatch(1);

        // We need it to be cancellable/non-runnable after reaching a certain point, and it needs to be deterministic.
        // This is why we ignore the ScheduledFuture returned and implement the go/no-go using a latch.
        service.scheduleAtFixedRate(() -> runTaskWrapper(latch, predicate, payload),
                budget.initialDelay(), budget.interval(), TimeUnit.MILLISECONDS);

        return waitForTaskCompletion(latch, service);
    }

    @Override
    public boolean run(BooleanSupplier supplier) {
        CountDownLatch latch = new CountDownLatch(1);

        // We need it to be cancellable/non-runnable after reaching a certain point, and it needs to be deterministic.
        // This is why we ignore the ScheduledFuture returned and implement the go/no-go using a latch.
        service.scheduleAtFixedRate(() -> runTaskWrapper(latch, supplier), budget.initialDelay(),
                budget.interval(), TimeUnit.MILLISECONDS);

        return waitForTaskCompletion(latch, service);
    }

    private boolean waitForTaskCompletion(CountDownLatch latch, ScheduledExecutorService service) {
        boolean completed = false;
        try {
            if (budget.maxDuration() == TimeBoundedBudget.UNLIMITED_DURATION) {
                latch.await();
                completed = true;
            } else {
                if (!latch.await(budget.maxDuration(), TimeUnit.MILLISECONDS)) {
                    LOG.debug("Timeout out waiting for the completion of the task");
                } else {
                    LOG.debug("The task has finished the execution and it is ready to continue");

                    completed = true;
                }
            }

            service.shutdown();
            service.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for the repeatable task to execute");
            Thread.currentThread().interrupt();
        } finally {
            elapsed = budget.elapsed();
            service.shutdownNow();
        }

        return completed;
    }

    @Override
    public Duration elapsed() {
        return elapsed;
    }
}
