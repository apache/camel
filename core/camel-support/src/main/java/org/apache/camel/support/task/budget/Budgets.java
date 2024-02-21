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

package org.apache.camel.support.task.budget;

import java.time.Duration;

/**
 * Helper builder of budgets
 */
public final class Budgets {
    private static final long DEFAULT_INITIAL_DELAY = 0;
    private static final long DEFAULT_INTERVAL = 1000;

    /**
     * A helper builder of time bounded builders. Provide generic/safe default values, but should be adjusted on a
     * per-case basis
     */
    public static class TimeBoundedBudgetBuilder {
        private static final long DEFAULT_MAX_DURATION = 5000;

        private long initialDelay = DEFAULT_INITIAL_DELAY;
        private long interval = DEFAULT_INTERVAL;
        private long maxDuration = DEFAULT_MAX_DURATION;

        public TimeBoundedBudgetBuilder withInitialDelay(Duration duration) {
            this.initialDelay = duration.toMillis();

            return this;
        }

        public TimeBoundedBudgetBuilder withInterval(Duration duration) {
            this.interval = duration.toMillis();

            return this;
        }

        public TimeBoundedBudgetBuilder withMaxDuration(Duration duration) {
            this.maxDuration = duration.toMillis();

            return this;
        }

        public TimeBoundedBudgetBuilder withUnlimitedDuration() {
            this.maxDuration = TimeBoundedBudget.UNLIMITED_DURATION;

            return this;
        }

        public TimeBoundedBudget build() {
            return new TimeBoundedBudget(initialDelay, interval, maxDuration);
        }
    }

    private Budgets() {
    }

    public static TimeBoundedBudgetBuilder timeBudget() {
        return new TimeBoundedBudgetBuilder();
    }

    public static IterationBoundedBudgetBuilder iterationBudget() {
        return new IterationBoundedBudgetBuilder();
    }

    public static IterationTimeBoundedBudgetBuilder iterationTimeBudget() {
        return new IterationTimeBoundedBudgetBuilder();
    }

    /**
     * Some components use 0 to disable retrying the task. This sanitizes it to run at least once
     *
     * @param  iterations the number of iterations
     * @return            an integer greater than or equal to 1 equivalent to the maximum number of iterations allowed
     */
    public static int atLeastOnce(int iterations) {
        if (iterations <= 0) {
            return 1;
        }

        return iterations;
    }
}
