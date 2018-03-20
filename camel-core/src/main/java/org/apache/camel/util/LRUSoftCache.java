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

/**
 * A cache that uses a near optional LRU Cache using {@link java.lang.ref.SoftReference}.
 * <p/>
 * The Cache is implemented by Caffeine which provides an <a href="https://github.com/ben-manes/caffeine/wiki/Efficiency">efficient cache</a>.
 * <p/>
 * This implementation uses {@link java.lang.ref.SoftReference} for stored values in the cache, to support the JVM
 * when it wants to reclaim objects when it's running out of memory. Therefore this implementation does
 * not support <b>all</b> the {@link java.util.Map} methods.
 * <p/>
 * <b>Only</b> methods below should be used:
 * <ul>
 *   <li>containsKey - To determine if the key is in the cache and refers to a value</li>
 *   <li>entrySet - To return a set of all the entries (as key/value pairs)</li>
 *   <li>get - To get a value from the cache</li>
 *   <li>isEmpty - To determine if the cache contains any values</li>
 *   <li>keySet - To return a set of the current keys which refers to a value</li>
 *   <li>put - To add a value to the cache</li>
 *   <li>putAll - To add values to the cache</li>
 *   <li>remove - To remove a value from the cache by its key</li>
 *   <li>size - To get the current size</li>
 *   <li>values - To return a copy of all the value in a list</li>
 * </ul>
 * <p/>
 * The {@link #containsValue(Object)} method should <b>not</b> be used as it's not adjusted to check
 * for the existence of a value without catering for the soft references.
 * <p/>
 * Notice that if the JVM reclaims memory, the content of this cache may be garbage collected without any
 * eviction notifications.
 * <p/>
 * Use {@link LRUCacheFactory} to create a new instance (do not use the constructor).
 *
 * @see LRUCacheFactory
 * @see LRUCache
 * @see LRUWeakCache
 */
public class LRUSoftCache<K, V> extends LRUCache<K, V> {

    public LRUSoftCache(int maximumCacheSize) {
        this(16, maximumCacheSize);
    }

    public LRUSoftCache(int initialCapacity, int maximumCacheSize) {
        this(initialCapacity, maximumCacheSize, false);
    }

    public LRUSoftCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        super(initialCapacity, maximumCacheSize, stopOnEviction, true, false, false);
    }

    @Override
    public String toString() {
        return "LRUSoftCache@" + ObjectHelper.getIdentityHashCode(this);
    }
}