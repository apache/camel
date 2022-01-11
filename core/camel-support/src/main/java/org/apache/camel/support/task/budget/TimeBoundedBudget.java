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

/**
 * This task budget limits the execution by both a maximum amount of time for the execution.
 */
public class TimeBoundedBudget implements TimeBudget {
    public static final long UNLIMITED_DURATION = -1;

    private final long initialDelay;
    private final long interval;
    private final long maxDuration;
    private final Instant startTime;

    TimeBoundedBudget(long initialDelay, long interval, long maxDuration) {
        this.initialDelay = initialDelay;
        this.interval = interval;
        this.maxDuration = maxDuration;
        this.startTime = Instant.now();
    }

    @Override
    public long maxDuration() {
        return maxDuration;
    }

    @Override
    public long initialDelay() {
        return initialDelay;
    }

    @Override
    public long interval() {
        return interval;
    }

    @Override
    public boolean canContinue() {
        // ... unless running forever
        if (maxDuration == UNLIMITED_DURATION) {
            return true;
        }

        // ... or if time budget is NOT exhausted
        if (elapsed().toMillis() >= maxDuration) {
            return false;
        }

        return true;
    }

    @Override
    public boolean next() {
        return true;
    }

    @Override
    public Duration elapsed() {
        return Duration.between(startTime, Instant.now());
    }
}
