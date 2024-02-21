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
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.camel.support.task.budget.IterationBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a task in the foreground, executing for a given number of iteration and sleeping between each of them.
 */
public class ForegroundTask implements BlockingTask {

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

    private final String name;
    private final IterationBudget budget;
    private Duration elapsed = Duration.ZERO;

    ForegroundTask(IterationBudget budget, String name) {
        this.budget = budget;
        this.name = name;
    }

    @Override
    public boolean run(BooleanSupplier supplier) {
        boolean completed = false;

        try {
            if (budget.initialDelay() > 0) {
                Thread.sleep(budget.initialDelay());
            }

            while (budget.next()) {
                if (supplier.getAsBoolean()) {
                    LOG.debug("Task {} is complete after {} iterations and it is ready to continue",
                            name, budget.iteration());
                    completed = true;

                    break;
                }

                if (budget.canContinue()) {
                    Thread.sleep(budget.interval());
                }
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted {} while waiting for the repeatable task to finish", name);
            Thread.currentThread().interrupt();
        } finally {
            elapsed = budget.elapsed();
        }

        return completed;
    }

    /**
     * Run a task until it produces a result
     *
     * @param  supplier  the supplier of the result
     * @param  predicate a predicate to test if the result is acceptable
     * @param  <T>       the type for the result
     * @return           An optional with the result
     */
    public <T> Optional<T> run(Supplier<T> supplier, Predicate<T> predicate) {
        try {
            if (budget.initialDelay() > 0) {
                Thread.sleep(budget.initialDelay());
            }

            while (budget.next()) {
                T ret = supplier.get();
                if (predicate.test(ret)) {
                    LOG.debug("Task {} is complete after {} iterations and it is ready to continue",
                            name, budget.iteration());
                    return Optional.ofNullable(ret);
                }

                if (budget.canContinue()) {
                    Thread.sleep(budget.interval());
                }
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted {} while waiting for the repeatable task to finish", name);
            Thread.currentThread().interrupt();
        } finally {
            elapsed = budget.elapsed();
        }

        return Optional.empty();
    }

    @Override
    public Duration elapsed() {
        return elapsed;
    }
}
