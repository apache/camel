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
 * This task budget limits the execution by both a given number of iterations or a maximum amount of time for the
 * execution. When evaluating the budget, the iteration takes precedence over the time budget (hence why: Iteration Time
 * Budget).
 *
 * @see IterationBudget
 * @see TimeBoundedBudget
 */
public class IterationTimeBoundedBudget implements IterationBudget, TimeBudget {
    private final IterationBudget iterationBudget;
    private final TimeBoundedBudget timeBoundedBudget;

    public IterationTimeBoundedBudget(long initialDelay, long interval, int maxIterations, long maxDuration) {
        iterationBudget = new IterationBoundedBudget(initialDelay, interval, maxIterations);
        timeBoundedBudget = new TimeBoundedBudget(initialDelay, interval, maxDuration);
    }

    @Override
    public long initialDelay() {
        return iterationBudget.initialDelay();
    }

    @Override
    public long interval() {
        return iterationBudget.interval();
    }

    @Override
    public int maxIterations() {
        return iterationBudget.maxIterations();
    }

    @Override
    public int iteration() {
        return iterationBudget.maxIterations();
    }

    @Override
    public boolean next() {
        if (canContinue()) {
            return iterationBudget.next();
        }

        return false;
    }

    @Override
    public boolean canContinue() {
        // Abort if the iteration budget or ...
        if (!iterationBudget.canContinue()) {
            return false;
        }

        // Otherwise, can continue to schedule/run the task
        return timeBoundedBudget.canContinue();
    }

    @Override
    public long maxDuration() {
        return timeBoundedBudget.maxDuration();
    }

    @Override
    public Duration elapsed() {
        return timeBoundedBudget.elapsed();
    }
}
