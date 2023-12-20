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

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.support.cache.SimpleLRUCache;
import org.apache.camel.support.cache.SimpleSoftCache;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link LRUCacheFactory} which uses a {@link LinkedHashMap} based implementation.
 */
public class DefaultLRUCacheFactory extends LRUCacheFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLRUCacheFactory.class);

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the specified maximumCacheSize, and will stop on eviction.
     *
     * @param  maximumCacheSize         the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize) {
        return createLRUCache(16, maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the specified maximumCacheSize, and will stop on eviction.
     *
     * @param  maximumCacheSize         the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int maximumCacheSize, Consumer<V> onEvict) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, with onEvict", 16, maximumCacheSize);
        return new SimpleLRUCache<>(16, maximumCacheSize, onEvict);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the specified initial capacity, maximumCacheSize, and will
     * stop on eviction.
     *
     * @param  initialCapacity          the initial capacity.
     * @param  maximumCacheSize         the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize) {
        return createLRUCache(initialCapacity, maximumCacheSize, maximumCacheSize > 0);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the specified initial capacity, maximumCacheSize,load factor
     * and ordering mode.
     *
     * @param  initialCapacity          the initial capacity.
     * @param  maximumCacheSize         the max capacity.
     * @param  stopOnEviction           whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity,
                maximumCacheSize, stopOnEviction);
        return new SimpleLRUCache<K, V>(
                initialCapacity, maximumCacheSize,
                stopOnEviction ? DefaultLRUCacheFactory.this::doStop : DefaultLRUCacheFactory.this::doNothing);
    }

    /**
     * Constructs an empty <tt>LRUSoftCache</tt> instance with the specified maximumCacheSize, and will stop on
     * eviction.
     *
     * @param  maximumCacheSize         the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int maximumCacheSize) {
        return createLRUSoftCache(16, maximumCacheSize);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize) {
        return createLRUSoftCache(initialCapacity, maximumCacheSize, maximumCacheSize > 0);
    }

    @Override
    public <K, V> Map<K, V> createLRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUSoftCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity,
                maximumCacheSize, stopOnEviction);
        return new SimpleSoftCache<>(
                new SimpleLRUCache<K, SoftReference<V>>(
                        initialCapacity, maximumCacheSize,
                        asSoftReferenceConsumer(stopOnEviction
                                ? DefaultLRUCacheFactory.this::doStop : DefaultLRUCacheFactory.this::doNothing)));
    }

    /**
     * Converts a consumer of values of type {@code V} into a consumer of referent of {@code SoftReference} of type
     * {@code V}.
     */
    private static <V> Consumer<SoftReference<V>> asSoftReferenceConsumer(Consumer<V> evicted) {
        return ref -> {
            V v = ref.get();
            if (v != null) {
                evicted.accept(v);
            }
        };
    }

    /**
     * Constructs an empty <tt>LRUWeakCache</tt> instance with the specified maximumCacheSize, and will stop on
     * eviction.
     *
     * @param  maximumCacheSize         the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    @Override
    @Deprecated
    public <K, V> Map<K, V> createLRUWeakCache(int maximumCacheSize) {
        return createLRUWeakCache(16, maximumCacheSize);
    }

    @Override
    @Deprecated
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize) {
        return createLRUWeakCache(initialCapacity, maximumCacheSize, maximumCacheSize > 0);
    }

    @Override
    @Deprecated
    public <K, V> Map<K, V> createLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        LOG.trace("Creating LRUWeakCache with initialCapacity: {}, maximumCacheSize: {}, stopOnEviction: {}", initialCapacity,
                maximumCacheSize, stopOnEviction);
        return new SimpleSoftCache<>(
                new SimpleLRUCache<K, SoftReference<V>>(
                        initialCapacity, maximumCacheSize,
                        asSoftReferenceConsumer(stopOnEviction
                                ? DefaultLRUCacheFactory.this::doStop : DefaultLRUCacheFactory.this::doNothing)));
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
