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

package org.apache.camel.component.caffeine.resume.single;

import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.resume.cache.SingleEntryCache;

/**
 * This is a simple cache implementation that uses Caffeine to store the resume offsets
 *
 * @param <K> The type of the key to cache
 * @param <V> The type of the value/entry to cache
 */
public class CaffeineCache<K, V> implements SingleEntryCache<K, V> {
    private final Cache<K, V> cache;
    private final long cacheSize;

    /**
     * Builds a new cache with the given cache size
     * 
     * @param cacheSize the cache size
     */
    public CaffeineCache(long cacheSize) {
        this(Caffeine.newBuilder().maximumSize(cacheSize).build(), cacheSize);
    }

    /**
     * Builds a new instance of this object
     * 
     * @param cache     an instance of a pre-constructed cache object
     * @param cacheSize the size of the pre-constructed cache object
     */
    public CaffeineCache(Cache<K, V> cache, long cacheSize) {
        this.cache = cache;
        this.cacheSize = cacheSize;
    }

    @Override
    public boolean contains(K key, V entry) {
        assert key != null;
        V cachedEntry = cache.getIfPresent(key);

        return entry.equals(cachedEntry);
    }

    @Override
    public void add(K key, V offsetValue) {
        cache.put(key, offsetValue);
    }

    @Override
    public Optional<V> get(K key) {
        V entry = cache.getIfPresent(key);

        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    @Override
    public boolean isFull() {
        if (cache.estimatedSize() >= cacheSize) {
            return true;
        }

        return false;
    }

    @Override
    public long capacity() {
        return cacheSize;
    }
}
