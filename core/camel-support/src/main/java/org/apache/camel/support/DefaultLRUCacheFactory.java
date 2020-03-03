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
package org.apache.camel.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link LRUCacheFactory} which uses a {@link LinkedHashMap}.
 * You can use camel-caffeine-lrucache instead which has a better LRUCache implementation.
 */
public class DefaultLRUCacheFactory extends LRUCacheFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLRUCacheFactory.class);

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        return new SimpleLRUCache<>(maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize, Consumer<V> onEvict) {
        LOG.trace("Creating LRUCache with maximumCacheSize: {}", maximumCacheSize);
        return new SimpleLRUCache<>(16, maximumCacheSize, onEvict);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new SimpleLRUCache<>(initialCapacity, maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new SimpleLRUCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int maximumCacheSize) {
        LOG.trace("Creating LRUSoftCache with maximumCacheSize: {}", maximumCacheSize);
        return new SimpleLRUCache<>(maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new SimpleLRUCache<>(initialCapacity, maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new SimpleLRUCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUWeakCache(int maximumCacheSize) {
        LOG.trace("Creating LRUWeakCache with maximumCacheSize: {}", maximumCacheSize);
        return new SimpleLRUCache<>(maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}", initialCapacity, maximumCacheSize);
        return new SimpleLRUCache<>(initialCapacity, maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity, maximumCacheSize, stopOnEviction);
        return new SimpleLRUCache<>(initialCapacity, maximumCacheSize, stopOnEviction);
    }

    private class SimpleLRUCache<K, V> extends LinkedHashMap<K, V> {

        static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final int maximumCacheSize;
        private final Consumer<V> evict;

        public SimpleLRUCache(int maximumCacheSize) {
            this(16, maximumCacheSize, maximumCacheSize > 0);
        }

        public SimpleLRUCache(int initialCapacity, int maximumCacheSize) {
            this(initialCapacity, maximumCacheSize, maximumCacheSize > 0);
        }

        public SimpleLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
            this(initialCapacity, maximumCacheSize, stopOnEviction ? DefaultLRUCacheFactory.this::doStop : DefaultLRUCacheFactory.this::doNothing);
        }

        public SimpleLRUCache(int initialCapacity, int maximumCacheSize, Consumer<V> evicted) {
            super(initialCapacity, DEFAULT_LOAD_FACTOR, true);
            this.maximumCacheSize = maximumCacheSize;
            this.evict = Objects.requireNonNull(evicted);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            if (size() > maximumCacheSize) {
                V value = eldest.getValue();
                evict.accept(value);
                return true;
            }
            return false;
        }

    }

    <V> void doNothing(V value) {
    }

    <V> void doStop(V value) {
        try {
            // stop service as its evicted from cache
            ServiceHelper.stopService(value);
        } catch (Exception e) {
            LOG.warn("Error stopping service: {}. This exception will be ignored.", value, e);
        }
    }
}
