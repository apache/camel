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

package org.apache.camel.impl.converter;

import java.util.concurrent.atomic.LongAdder;

/**
 * Represents utilization statistics
 */
final class TypeConverterStatistics implements ConverterStatistics {

    private boolean statisticsEnabled;
    private final LongAdder noopCounter = new LongAdder();
    private final LongAdder attemptCounter = new LongAdder();
    private final LongAdder missCounter = new LongAdder();
    private final LongAdder hitCounter = new LongAdder();
    private final LongAdder failedCounter = new LongAdder();

    @Override
    public long getNoopCounter() {
        return noopCounter.longValue();
    }

    @Override
    public long getAttemptCounter() {
        return attemptCounter.longValue();
    }

    @Override
    public long getHitCounter() {
        return hitCounter.longValue();
    }

    @Override
    public long getMissCounter() {
        return missCounter.longValue();
    }

    @Override
    public long getFailedCounter() {
        return failedCounter.longValue();
    }

    @Override
    public void incrementFailed() {
        if (statisticsEnabled) {
            failedCounter.increment();
        }
    }

    @Override
    public void incrementNoop() {
        if (statisticsEnabled) {
            noopCounter.increment();
        }
    }

    @Override
    public void incrementHit() {
        if (statisticsEnabled) {
            hitCounter.increment();
        }
    }

    @Override
    public void incrementMiss() {
        if (statisticsEnabled) {
            missCounter.increment();
        }
    }

    @Override
    public void incrementAttempt() {
        if (statisticsEnabled) {
            attemptCounter.increment();
        }
    }

    @Override
    public void reset() {
        noopCounter.reset();
        attemptCounter.reset();
        hitCounter.reset();
        missCounter.reset();
        failedCounter.reset();
    }

    @Override
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    @Override
    public String toString() {
        return String.format("TypeConverterRegistry utilization[noop=%s, attempts=%s, hits=%s, misses=%s, failures=%s]",
                getNoopCounter(), getAttemptCounter(), getHitCounter(), getMissCounter(), getFailedCounter());
    }
}
