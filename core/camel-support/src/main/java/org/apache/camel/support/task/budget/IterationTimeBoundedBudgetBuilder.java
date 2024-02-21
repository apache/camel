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
 * A helper builder of iteration and time bounded builders. Provide generic/safe default values, but should be adjusted
 * on a per-case basis. By default, execute the iterations for up to Integer.MAX_VALUE and a duration of 5 seconds.
 */
public class IterationTimeBoundedBudgetBuilder implements BudgetBuilder<IterationTimeBoundedBudget> {
    private static final long DEFAULT_MAX_DURATION = 5000;
    private static final int DEFAULT_MAX_ITERATIONS = Integer.MAX_VALUE;
    private static final long DEFAULT_INITIAL_DELAY = 0;
    private static final long DEFAULT_INTERVAL = 1000;

    private long maxDuration = DEFAULT_MAX_DURATION;
    private long initialDelay = DEFAULT_INITIAL_DELAY;
    private long interval = DEFAULT_INTERVAL;
    private int maxIterations = DEFAULT_MAX_ITERATIONS;

    public IterationTimeBoundedBudgetBuilder withInitialDelay(Duration duration) {
        if (duration != null) {
            this.initialDelay = duration.toMillis();
        }

        return this;
    }

    public IterationTimeBoundedBudgetBuilder withInterval(Duration duration) {
        if (duration != null) {
            this.interval = duration.toMillis();
        }

        return this;
    }

    public IterationTimeBoundedBudgetBuilder withMaxIterations(int maxIterations) {
        if (maxIterations > 0) {
            this.maxIterations = maxIterations;
        }

        return this;
    }

    public IterationTimeBoundedBudgetBuilder withMaxDuration(Duration duration) {
        if (duration != null) {
            this.maxDuration = duration.toMillis();
        }

        return this;
    }

    public IterationTimeBoundedBudgetBuilder withUnlimitedDuration() {
        this.maxDuration = TimeBoundedBudget.UNLIMITED_DURATION;

        return this;
    }

    @Override
    public IterationTimeBoundedBudget build() {
        return new IterationTimeBoundedBudget(initialDelay, interval, maxIterations, maxDuration);
    }
}
