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

/**
 * A Least Recently Used Cache
 *
 * @version 
 */
public class LRUCache<K, V> implements Map<K, V>, Serializable {
    private static final long serialVersionUID = -342098639681884414L;
    
    private int maxCacheSize = 10000;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private ConcurrentLinkedHashMap<K, V> map;

    public LRUCache(int maximumCacheSize) {
        this(maximumCacheSize, maximumCacheSize);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     *                                  or the load factor is non positive.
     */
    public LRUCache(int initialCapacity, int maximumCacheSize) {
        map = new ConcurrentLinkedHashMap.Builder<K, V>()
            .initialCapacity(initialCapacity)
            .maximumWeightedCapacity(maximumCacheSize).build();
        this.maxCacheSize = maximumCacheSize;
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

    @Override
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
    }

    @Override
    public String toString() {
        return "LRUCache@" + ObjectHelper.getIdentityHashCode(this);
    }
}
