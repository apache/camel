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
import java.time.Instant;

public class IterationTimeBoundedBudget implements IterationBudget, TimeBudget {
    private IterationBudget iterationBudget;
    private final long maxDuration;
    private final Instant startTime;

    public IterationTimeBoundedBudget(long initialDelay, long interval, int maxIterations, long maxDuration) {
        iterationBudget = new IterationBoundedBudget(initialDelay, interval, maxIterations);
        this.maxDuration = maxDuration;
        this.startTime = Instant.now();
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
    public int iterations() {
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

        // ... the time budget is exhausted
        if (Duration.between(startTime, Instant.now()).toMillis() >= maxDuration) {
            return false;
        }

        // Otherwise, can continue to schedule/run the task
        return true;
    }

    @Override
    public long maxDuration() {
        return maxDuration;
    }

}
