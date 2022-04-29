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

package org.apache.camel.component.caffeine.resume.multi;

import java.util.ArrayList;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.resume.cache.MultiEntryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cache that can store multiple key/valued resumables where the value is a list containing multiple values
 * 
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
public class CaffeineCache<K, V> implements MultiEntryCache<K, V> {
    private static final Logger LOG = LoggerFactory.getLogger(CaffeineCache.class);

    private final Cache<K, List<V>> cache;
    private final long cacheSize;

    /**
     * Builds a new instance of this object with the given cache size
     * 
     * @param cacheSize the size of the cache
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
    public CaffeineCache(Cache<K, List<V>> cache, long cacheSize) {
        this.cache = cache;
        this.cacheSize = cacheSize;
    }

    @Override
    public long capacity() {
        return cacheSize;
    }

    @Override
    public synchronized void add(K key, V offsetValue) {
        LOG.trace("Adding entry to the cache (k/v): {}/{}", key, offsetValue);
        LOG.trace("Adding entry to the cache (k/v) with types: {}/{}", key.getClass(), offsetValue.getClass());
        List<V> entries = cache.get(key, k -> new ArrayList<>());

        entries.add(offsetValue);
    }

    @Override
    public synchronized boolean contains(K key, V entry) {
        final List<V> entries = cache.getIfPresent(key);

        if (entries == null) {
            return false;
        }

        boolean ret = entries.contains(entry);
        LOG.trace("Checking if cache contains key {} with value {} ({})", key, entry, ret);

        return ret;
    }

    @Override
    public boolean isFull() {
        if (cache.estimatedSize() >= cacheSize) {
            return true;
        }

        return false;
    }

}
