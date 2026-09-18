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
package org.apache.camel.component.caffeine.processor;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A {@link KeyValueRepository} implementation backed by a Caffeine {@link Cache} with per-entry TTL support.
 * <p/>
 * Caffeine's {@link Expiry} API is used to assign a TTL to each entry individually. Entries without a TTL (null, zero,
 * or negative duration) are stored with {@link Long#MAX_VALUE} as their duration so they effectively never expire.
 * <p/>
 * This single implementation can serve as idempotent repository, aggregation repository, and state store via the
 * adapters in {@code camel-support} ({@code KeyValueIdempotentRepository} and {@code KeyValueAggregationRepository}).
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A Caffeine-based KeyValueRepository with per-entry TTL support.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Caffeine based key-value repository")
public class CaffeineKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    /**
     * Internal value wrapper that carries the per-entry TTL so the custom {@link Expiry} can read it at insertion time.
     */
    private static final class TtlValue {

        private final Object value;
        private final long ttlNanos;

        TtlValue(Object value, long ttlNanos) {
            this.value = value;
            this.ttlNanos = ttlNanos;
        }

        Object value() {
            return value;
        }

        long ttlNanos() {
            return ttlNanos;
        }
    }

    private Cache<String, TtlValue> cache;

    @Metadata(description = "Maximum number of entries in the cache. 0 or negative means unbounded.",
              defaultValue = "0")
    private int maximumSize;

    /**
     * Creates a new Caffeine-backed key-value repository with default settings (unbounded, no global TTL).
     */
    public CaffeineKeyValueRepository() {
    }

    /**
     * Creates a new Caffeine-backed key-value repository wrapping a pre-built cache.
     * <p/>
     * This is primarily for testing. The supplied cache must use {@link TtlValue} as its value type.
     */
    CaffeineKeyValueRepository(Cache<String, TtlValue> cache) {
        this.cache = cache;
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    /**
     * Sets the maximum number of entries in the cache. 0 or negative means unbounded.
     */
    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        TtlValue entry = cache.getIfPresent(key);
        return entry != null ? entry.value() : null;
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, Duration ttl) {
        long ttlNanos = hasPositiveTtl(ttl) ? ttl.toNanos() : Long.MAX_VALUE;
        TtlValue previous = cache.asMap().put(key, new TtlValue(value, ttlNanos));
        return previous != null ? previous.value() : null;
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        TtlValue previous = cache.asMap().remove(key);
        return previous != null ? previous.value() : null;
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public Set<String> keys() {
        cache.cleanUp();
        return Set.copyOf(cache.asMap().keySet());
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public Object putIfAbsent(String key, Object value, Duration ttl) {
        long ttlNanos = hasPositiveTtl(ttl) ? ttl.toNanos() : Long.MAX_VALUE;
        ConcurrentMap<String, TtlValue> map = cache.asMap();
        TtlValue newEntry = new TtlValue(value, ttlNanos);
        TtlValue existing = map.putIfAbsent(key, newEntry);
        return existing != null ? existing.value() : null;
    }

    @Override
    public boolean replace(String key, Object expectedOldValue, Object newValue, Duration ttl) {
        long ttlNanos = hasPositiveTtl(ttl) ? ttl.toNanos() : Long.MAX_VALUE;
        boolean[] replaced = { false };
        cache.asMap().computeIfPresent(key, (k, current) -> {
            if (Objects.equals(current.value(), expectedOldValue)) {
                replaced[0] = true;
                return new TtlValue(newValue, ttlNanos);
            }
            return current;
        });
        return replaced[0];
    }

    @Override
    public boolean delete(String key, Object expectedValue) {
        boolean[] removed = { false };
        cache.asMap().computeIfPresent(key, (k, current) -> {
            if (Objects.equals(current.value(), expectedValue)) {
                removed[0] = true;
                return null; // returning null removes the entry
            }
            return current;
        });
        return removed[0];
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        cache.cleanUp();
        return (int) cache.estimatedSize();
    }

    private static boolean hasPositiveTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    @Override
    protected void doStart() throws Exception {
        if (cache == null) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder();
            if (maximumSize > 0) {
                builder.maximumSize(maximumSize);
            }
            // Use a custom Expiry to support per-entry TTL
            builder.expireAfter(new Expiry<String, TtlValue>() {
                @Override
                public long expireAfterCreate(String key, TtlValue value, long currentTime) {
                    return value.ttlNanos();
                }

                @Override
                public long expireAfterUpdate(String key, TtlValue value, long currentTime, long currentDuration) {
                    return value.ttlNanos();
                }

                @Override
                public long expireAfterRead(String key, TtlValue value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            });
            cache = builder.build();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (cache != null) {
            cache.invalidateAll();
        }
    }
}
