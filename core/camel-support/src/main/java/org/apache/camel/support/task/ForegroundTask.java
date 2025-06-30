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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.task.budget.IterationBudget;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a task in the foreground, executing for a given number of iteration and sleeping between each of them.
 */
public class ForegroundTask extends AbstractTask implements BlockingTask {

    /**
     * A builder helper for building new foreground tasks
     */
    public static class ForegroundTaskBuilder extends AbstractTaskBuilder<ForegroundTask> {

        private String name;
        private IterationBudget budget;

        /**
         * Sets the name of the task
         *
         * @param  name the name
         * @return      an instance of this builder
         */
        @Override
        public ForegroundTaskBuilder withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets an iteration budget for the task (i.e.: the task will not run more than the given number of iterations)
         *
         * @param  budget the budget
         * @return        an instance of this builder
         */
        public ForegroundTaskBuilder withBudget(IterationBudget budget) {
            this.budget = budget;
            return this;
        }

        @Override
        public ForegroundTask build() {
            return new ForegroundTask(budget, name != null ? name : getName());
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ForegroundTask.class);

    private final IterationBudget budget;
    private Duration elapsed = Duration.ZERO;
    private final AtomicBoolean running = new AtomicBoolean();

    ForegroundTask(IterationBudget budget, String name) {
        super(name);
        this.budget = budget;
    }

    @Override
    public boolean run(CamelContext camelContext, BooleanSupplier supplier) {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        running.set(true);
        boolean completed = false;

        TaskManagerRegistry registry = PluginHelper.getTaskManagerRegistry(camelContext.getCamelContextExtension());
        registry.addTask(this);
        try {
            if (budget.initialDelay() > 0) {
                Thread.sleep(budget.initialDelay());
            }

            while (budget.next()) {
                lastAttemptTime = System.currentTimeMillis();
                if (firstAttemptTime < 0) {
                    firstAttemptTime = lastAttemptTime;
                }
                nextAttemptTime = lastAttemptTime + budget.interval();
                if (supplier.getAsBoolean()) {
                    LOG.debug("Task {} is complete after {} iterations and it is ready to continue",
                            getName(), budget.iteration());
                    status = Status.Completed;
                    completed = true;
                    break;
                }

                if (budget.canContinue()) {
                    Thread.sleep(budget.interval());
                } else {
                    status = Status.Exhausted;
                }
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted {} while waiting for the repeatable task to finish", getName());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            status = Status.Failed;
            cause = e;
            throw e;
        } finally {
            elapsed = budget.elapsed();
            running.set(false);
            registry.removeTask(this);
        }

        return completed;
    }

    /**
     * Run a task until it produces a result
     *
     * @param  camelContext the camel context
     * @param  supplier     the supplier of the result
     * @param  predicate    a predicate to test if the result is acceptable
     * @param  <T>          the type for the result
     * @return              An optional with the result
     */
    public <T> Optional<T> run(CamelContext camelContext, Supplier<T> supplier, Predicate<T> predicate) {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        running.set(true);
        TaskManagerRegistry registry = PluginHelper.getTaskManagerRegistry(camelContext.getCamelContextExtension());
        registry.addTask(this);
        try {
            if (budget.initialDelay() > 0) {
                Thread.sleep(budget.initialDelay());
            }

            while (budget.next()) {
                lastAttemptTime = System.currentTimeMillis();
                if (firstAttemptTime < 0) {
                    firstAttemptTime = lastAttemptTime;
                }
                T ret = supplier.get();
                if (predicate.test(ret)) {
                    LOG.debug("Task {} is complete after {} iterations and it is ready to continue",
                            getName(), budget.iteration());
                    status = Status.Completed;
                    return Optional.ofNullable(ret);
                }
                nextAttemptTime = lastAttemptTime + budget.interval();

                if (budget.canContinue()) {
                    Thread.sleep(budget.interval());
                } else {
                    status = Status.Exhausted;
                }
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted {} while waiting for the repeatable task to finish", getName());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            status = Status.Failed;
            cause = e;
        } finally {
            elapsed = budget.elapsed();
            running.set(false);
            registry.removeTask(this);
        }

        return Optional.empty();
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
