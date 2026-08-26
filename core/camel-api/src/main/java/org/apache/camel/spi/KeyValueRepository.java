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
package org.apache.camel.spi;

import java.util.Set;

import org.apache.camel.Service;
import org.jspecify.annotations.Nullable;

/**
 * A generic key-value repository SPI that provides a unified abstraction over various state stores.
 * <p/>
 * This interface can be used as the single underlying storage mechanism for multiple Camel patterns, including:
 * <ul>
 * <li><b>Idempotent Consumer</b> -- via {@code KeyValueIdempotentRepository} adapter</li>
 * <li><b>Aggregation</b> -- via {@code KeyValueAggregationRepository} adapter</li>
 * </ul>
 * Instead of implementing both {@link IdempotentRepository} and {@link AggregationRepository} separately for each
 * storage technology (Redis, Hazelcast, Infinispan, JDBC, etc.), a single {@code KeyValueRepository} implementation can
 * be wrapped by the appropriate adapter.
 * <p/>
 * Implementations must be thread-safe. Entries may optionally have a time-to-live (TTL); a TTL of {@code 0} or less
 * means the entry does not expire.
 *
 * @since 4.23
 */
public interface KeyValueRepository extends Service {

    /**
     * Retrieves the value associated with the given key.
     *
     * @param  key the key to look up
     * @return     the value, or {@code null} if the key is not present or has expired
     */
    @Nullable
    Object get(String key);

    /**
     * Stores a value under the given key with an optional time-to-live.
     *
     * @param key       the key
     * @param value     the value to store
     * @param ttlMillis the time-to-live in milliseconds; {@code 0} or negative means no expiration
     */
    void put(String key, Object value, long ttlMillis);

    /**
     * Removes the entry for the given key.
     *
     * @param  key the key to remove
     * @return     the previous value associated with the key, or {@code null} if no mapping existed
     */
    @Nullable
    Object delete(String key);

    /**
     * Tests whether the repository contains a non-expired entry for the given key.
     *
     * @param  key the key to test
     * @return     {@code true} if the key is present and has not expired
     */
    boolean contains(String key);

    /**
     * Returns the set of all non-expired keys in the repository.
     *
     * @return a snapshot of the current key set
     */
    Set<String> keys();

    /**
     * Removes all entries from the repository.
     */
    void clear();

    /**
     * Stores the value under the given key only if no non-expired mapping already exists.
     * <p/>
     * The default implementation is not atomic. Implementations backed by stores that support atomic compare-and-set
     * operations should override this method for better concurrency guarantees.
     *
     * @param  key       the key
     * @param  value     the value to store
     * @param  ttlMillis the time-to-live in milliseconds; {@code 0} or negative means no expiration
     * @return           the existing value if the key was already present, or {@code null} if the put succeeded
     */
    @Nullable
    default Object putIfAbsent(String key, Object value, long ttlMillis) {
        Object existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value, ttlMillis);
        return null;
    }

    /**
     * Returns the number of non-expired entries in the repository.
     *
     * @return the entry count
     */
    default int size() {
        return keys().size();
    }
}
