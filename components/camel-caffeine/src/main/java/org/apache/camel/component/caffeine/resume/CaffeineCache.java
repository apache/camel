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

package org.apache.camel.component.caffeine.resume;

import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.util.ObjectHelper;

/**
 * This is a simple cache implementation that uses Caffeine to store the resume offsets
 *
 * @param <K> The type of the key to cache
 */
public class CaffeineCache<K> implements ResumeCache<K> {
    private final Cache<K, Object> cache;
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
    public CaffeineCache(Cache<K, Object> cache, long cacheSize) {
        this.cache = cache;
        this.cacheSize = cacheSize;
    }

    @Override
    public boolean contains(K key, Object entry) {
        ObjectHelper.notNull(key, "key");
        Object cachedEntry = cache.getIfPresent(key);

        return entry.equals(cachedEntry);
    }

    @Override
    public void add(K key, Object offsetValue) {
        cache.put(key, offsetValue);
    }

    @Override
    public Object get(K key) {
        Object entry = cache.getIfPresent(key);

        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    @Override
    public <T> T get(K key, Class<T> clazz) {
        Object entry = cache.getIfPresent(key);
        if (entry != null) {
            return clazz.cast(entry);
        }

        return null;
    }

    @Override
    public Object computeIfAbsent(K key, Function<? super K, ? super Object> mapping) {
        Object entry = cache.getIfPresent(key);

        if (entry == null) {
            entry = mapping.apply(key);
            cache.put(key, entry);
        }

        return entry;
    }

    @Override
    public Object computeIfPresent(K key, BiFunction<? super K, ? super Object, ? super Object> remapping) {
        Object entry = cache.getIfPresent(key);

        if (entry != null) {
            entry = remapping.apply(key, entry);
            cache.put(key, entry);
        }

        return entry;
    }

    @Override
    public boolean isFull() {
        return cache.estimatedSize() >= cacheSize;
    }

    @Override
    public long capacity() {
        return cacheSize;
    }

    @Override
    public void forEach(BiFunction<? super K, ? super Object, Boolean> action) {

        final ConcurrentMap<K, Object> kObjectConcurrentMap = cache.asMap();
        for (var entry : kObjectConcurrentMap.entrySet()) {
            final boolean invalidate = action.apply(entry.getKey(), entry.getValue());
            if (invalidate) {
                cache.invalidate(entry.getKey());
            }
        }
    }
}
