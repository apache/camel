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

import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConvertible;

final class NoopTypeConverterStatistics implements ConverterStatistics {
    @Override
    public long getNoopCounter() {
        return 0;
    }

    @Override
    public long getAttemptCounter() {
        return 0;
    }

    @Override
    public long getHitCounter() {
        return 0;
    }

    @Override
    public long getMissCounter() {
        return 0;
    }

    @Override
    public long getFailedCounter() {
        return 0;
    }

    @Override
    public void reset() {
        // NO-OP
    }

    @Override
    public boolean isStatisticsEnabled() {
        return false;
    }

    @Override
    public void incrementFailed() {
        // NO-OP
    }

    @Override
    public void incrementNoop() {
        // NO-OP
    }

    @Override
    public void incrementHit() {
        // NO-OP
    }

    @Override
    public void incrementMiss() {
        // NO-OP
    }

    @Override
    public void incrementAttempt() {
        // NO-OP
    }

    @Override
    public void logMappingStatisticsMessage(
            Map<TypeConvertible<?, ?>, TypeConverter> converters, TypeConverter missConverter) {
        // NO-OP
    }
}
