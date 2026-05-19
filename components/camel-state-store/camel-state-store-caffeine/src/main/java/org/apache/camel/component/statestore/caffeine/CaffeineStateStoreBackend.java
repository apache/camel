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
package org.apache.camel.component.statestore.caffeine;

import java.time.Duration;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.apache.camel.component.statestore.StateStoreBackend;

/**
 * A {@link StateStoreBackend} implementation backed by Caffeine cache. Supports per-entry TTL using Caffeine's variable
 * expiration.
 */
public class CaffeineStateStoreBackend implements StateStoreBackend {

    private Cache<String, TimedValue> cache;
    private int maximumSize = 10_000;

    @Override
    public Object put(String key, Object value, long ttlMillis) {
        TimedValue previous = cache.asMap().put(key, new TimedValue(value, ttlMillis));
        return previous != null ? previous.value() : null;
    }

    @Override
    public Object get(String key) {
        TimedValue entry = cache.getIfPresent(key);
        return entry != null ? entry.value() : null;
    }

    @Override
    public Object delete(String key) {
        TimedValue previous = cache.asMap().remove(key);
        return previous != null ? previous.value() : null;
    }

    @Override
    public boolean contains(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        TimedValue newValue = new TimedValue(value, ttlMillis);
        TimedValue existing = cache.asMap().putIfAbsent(key, newValue);
        return existing != null ? existing.value() : null;
    }

    @Override
    public int size() {
        cache.cleanUp();
        return (int) cache.estimatedSize();
    }

    @Override
    public Set<String> keys() {
        return Set.copyOf(cache.asMap().keySet());
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public void start() {
        if (cache != null) {
            return;
        }
        cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<String, TimedValue>() {
                    @Override
                    public long expireAfterCreate(String key, TimedValue value, long currentTime) {
                        return value.ttlMillis() > 0
                                ? Duration.ofMillis(value.ttlMillis()).toNanos()
                                : Long.MAX_VALUE;
                    }

                    @Override
                    public long expireAfterUpdate(String key, TimedValue value, long currentTime, long currentDuration) {
                        return value.ttlMillis() > 0
                                ? Duration.ofMillis(value.ttlMillis()).toNanos()
                                : Long.MAX_VALUE;
                    }

                    @Override
                    public long expireAfterRead(String key, TimedValue value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public void stop() {
        if (cache != null) {
            cache.invalidateAll();
            cache.cleanUp();
            cache = null;
        }
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    private record TimedValue(Object value, long ttlMillis) {
    }
}
