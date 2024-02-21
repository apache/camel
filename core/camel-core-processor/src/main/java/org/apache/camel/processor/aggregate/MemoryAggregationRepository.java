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
package org.apache.camel.processor.aggregate;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A memory based {@link org.apache.camel.spi.AggregationRepository} which stores {@link Exchange}s in memory only.
 *
 * Supports both optimistic locking and non-optimistic locking modes. Defaults to non-optimistic locking mode.
 */
public class MemoryAggregationRepository extends ServiceSupport implements OptimisticLockingAggregationRepository {
    private final ConcurrentMap<String, Exchange> cache = new ConcurrentHashMap<>();
    private final boolean optimisticLocking;

    public MemoryAggregationRepository() {
        this(false);
    }

    public MemoryAggregationRepository(boolean optimisticLocking) {
        this.optimisticLocking = optimisticLocking;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
        if (!optimisticLocking) {
            throw new UnsupportedOperationException();
        }
        if (oldExchange == null) {
            if (cache.putIfAbsent(key, newExchange) != null) {
                throw new OptimisticLockingException();
            }
        } else {
            if (!cache.replace(key, oldExchange, newExchange)) {
                throw new OptimisticLockingException();
            }
        }
        return oldExchange;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        if (optimisticLocking) {
            throw new UnsupportedOperationException();
        }
        return cache.put(key, exchange);
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return cache.get(key);
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        if (optimisticLocking) {
            if (!cache.remove(key, exchange)) {
                throw new OptimisticLockingException();
            }
        } else {
            cache.remove(key);
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        // noop
    }

    @Override
    public Set<String> getKeys() {
        // do not allow edits to the set
        return Collections.unmodifiableSet(cache.keySet());
    }

    @Override
    protected void doStop() throws Exception {
        cache.clear();
    }

}
