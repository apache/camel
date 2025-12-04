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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConvertible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents utilization statistics
 */
final class TypeConverterStatistics implements ConverterStatistics {
    private static final Logger LOG = LoggerFactory.getLogger(TypeConverterStatistics.class);

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
    public boolean isStatisticsEnabled() {
        return true;
    }

    @Override
    public void incrementFailed() {
        failedCounter.increment();
    }

    @Override
    public void incrementNoop() {
        noopCounter.increment();
    }

    @Override
    public void incrementHit() {
        hitCounter.increment();
    }

    @Override
    public void incrementMiss() {
        missCounter.increment();
    }

    @Override
    public void incrementAttempt() {
        attemptCounter.increment();
    }

    @Override
    public void reset() {
        noopCounter.reset();
        attemptCounter.reset();
        hitCounter.reset();
        missCounter.reset();
        failedCounter.reset();
    }

    /**
     * Compute the total number of cached missed conversions
     *
     * @param  converters    the converters cache instance
     * @param  missConverter the type that represents a type conversion miss
     * @return               The number of cached missed conversions as an AtomicInteger instance
     */
    private static AtomicInteger computeCachedMisses(
            Map<TypeConvertible<?, ?>, TypeConverter> converters, TypeConverter missConverter) {
        AtomicInteger misses = new AtomicInteger();

        converters.forEach((k, v) -> {
            if (v == missConverter) {
                misses.incrementAndGet();
            }
        });
        return misses;
    }

    @Override
    public void logMappingStatisticsMessage(
            Map<TypeConvertible<?, ?>, TypeConverter> converters, TypeConverter missConverter) {
        final AtomicInteger misses = computeCachedMisses(converters, missConverter);

        LOG.info(
                "TypeConverterStatistics utilization[noop={}, attempts={}, hits={}, misses={}, failures={}] mappings[total={}, misses={}]",
                getNoopCounter(),
                getAttemptCounter(),
                getHitCounter(),
                getMissCounter(),
                getFailedCounter(),
                converters.size(),
                misses);
    }
}
