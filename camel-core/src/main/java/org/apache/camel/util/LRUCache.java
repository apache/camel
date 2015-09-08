/**
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
package org.apache.camel.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Least Recently Used Cache.
 * <p/>
 * If this cache stores {@link org.apache.camel.Service} then this implementation will on eviction
 * invoke the {@link org.apache.camel.Service#stop()} method, to auto-stop the service.
 *
 * @see LRUSoftCache
 * @see LRUWeakCache
 */
public class LRUCache<K, V> implements Map<K, V>, EvictionListener<K, V>, Serializable {
    private static final long serialVersionUID = -342098639681884414L;
    private static final Logger LOG = LoggerFactory.getLogger(LRUCache.class);

    protected final AtomicLong hits = new AtomicLong();
    protected final AtomicLong misses = new AtomicLong();
    protected final AtomicLong evicted = new AtomicLong();

    private int maxCacheSize = 10000;
    private boolean stopOnEviction;
    private ConcurrentLinkedHashMap<K, V> map;

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public LRUCache(int maximumCacheSize) {
        this(16, maximumCacheSize); // 16 is the default initial capacity in ConcurrentLinkedHashMap
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public LRUCache(int initialCapacity, int maximumCacheSize) {
        this(initialCapacity, maximumCacheSize, true);
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
    public LRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        map = new ConcurrentLinkedHashMap.Builder<K, V>()
                .initialCapacity(initialCapacity)
                .maximumWeightedCapacity(maximumCacheSize)
                .listener(this).build();
        this.maxCacheSize = maximumCacheSize;
        this.stopOnEviction = stopOnEviction;
    }

    @Override
    public V get(Object o) {
        V answer = map.get(o);
        if (answer != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
        }
        return answer;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return map.containsValue(0);
    }

    @Override
    public V put(K k, V v) {
        return map.put(k, v);
    }

    @Override
    public V remove(Object o) {
        return map.remove(o);
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        this.map.putAll(map);
    }

    @Override
    public void clear() {
        map.clear();
        resetStatistics();
    }

    @Override
    public Set<K> keySet() {
        return map.ascendingKeySet();
    }

    @Override
    public Collection<V> values() {
        return map.ascendingMap().values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.ascendingMap().entrySet();
    }

    @Override
    public void onEviction(K key, V value) {
        evicted.incrementAndGet();
        LOG.trace("onEviction {} -> {}", key, value);
        if (stopOnEviction) {
            try {
                // stop service as its evicted from cache
                ServiceHelper.stopService(value);
            } catch (Exception e) {
                LOG.warn("Error stopping service: " + value + ". This exception will be ignored.", e);
            }
        }
    }

    /**
     * Gets the number of cache hits
     */
    public long getHits() {
        return hits.get();
    }

    /**
     * Gets the number of cache misses.
     */
    public long getMisses() {
        return misses.get();
    }

    /**
     * Gets the number of evicted entries.
     */
    public long getEvicted() {
        return evicted.get();
    }

    /**
     * Returns the maxCacheSize.
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Rest the cache statistics such as hits and misses.
     */
    public void resetStatistics() {
        hits.set(0);
        misses.set(0);
        evicted.set(0);
    }

    @Override
    public String toString() {
        return "LRUCache@" + ObjectHelper.getIdentityHashCode(this);
    }
}
