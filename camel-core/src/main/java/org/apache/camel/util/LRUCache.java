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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Service;

/**
 * A Least Recently Used Cache
 *
 * @version $Revision$
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> implements Service {
    private static final long serialVersionUID = -342098639681884413L;
    private int maxCacheSize = 10000;

    public LRUCache(int maximumCacheSize) {
        this(maximumCacheSize, maximumCacheSize, 0.75f, true);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param loadFactor       the load factor.
     * @param accessOrder      the ordering mode - <tt>true</tt> for
     *                         access-order, <tt>false</tt> for insertion-order.
     * @throws IllegalArgumentException if the initial capacity is negative
     *                                  or the load factor is non positive.
     */
    public LRUCache(int initialCapacity, int maximumCacheSize, float loadFactor, boolean accessOrder) {
        super(initialCapacity, loadFactor, accessOrder);
        this.maxCacheSize = maximumCacheSize;
    }

    /**
     * Returns the maxCacheSize.
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
        return size() > maxCacheSize;
    }

    public void start() throws Exception {
        // noop
    }

    public void stop() throws Exception {
        // stop the value and clear the cache
        if (!isEmpty()) {
            ServiceHelper.stopServices(values());
            clear();
        }
    }

    @Override
    public String toString() {
        return "LRUCache@" + ObjectHelper.getIdentityHashCode(this);
    }
}