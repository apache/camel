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
package org.apache.camel.main.download;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.camel.processor.DefaultClaimCheckRepository;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.ClaimCheckRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.processor.state.MemoryStateRepository;

public class StubBeanRepository implements BeanRepository {

    private final String stubPattern;

    public StubBeanRepository(String stubPattern) {
        this.stubPattern = stubPattern;
    }

    @Override
    public Object lookupByName(String name) {
        return null;
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        if (PatternHelper.matchPattern(name, stubPattern)) {
            return stubType(type);
        }
        return null;
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        if (stubPattern != null) {
            T answer = stubType(type);
            if (answer != null) {
                // generate dummy name
                String name = UUID.randomUUID().toString();
                return Map.of(name, answer);
            }
        }
        return Collections.EMPTY_MAP;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        if (stubPattern != null) {
            T answer = stubType(type);
            if (answer != null) {
                return Set.of(answer);
            }
        }
        return Collections.EMPTY_SET;
    }

    private <T> T stubType(Class<T> type) {
        // add repositories and other stuff we need to stub out, so they run noop/in-memory only
        // and do not start live connections to databases or other services
        if (IdempotentRepository.class.isAssignableFrom(type)) {
            return (T) new MemoryIdempotentRepository();
        }
        if (AggregationRepository.class.isAssignableFrom(type)) {
            return (T) new MemoryAggregationRepository();
        }
        if (ClaimCheckRepository.class.isAssignableFrom(type)) {
            return (T) new DefaultClaimCheckRepository();
        }
        if (StateRepository.class.isAssignableFrom(type)) {
            return (T) new MemoryStateRepository();
        }
        return null;
    }
}
