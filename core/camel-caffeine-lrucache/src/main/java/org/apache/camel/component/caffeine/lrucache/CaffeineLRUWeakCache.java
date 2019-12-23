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
package org.apache.camel.component.caffeine.lrucache;

import org.apache.camel.util.ObjectHelper;

/**
 * A cache that uses a near optional LRU Cache using {@link java.lang.ref.WeakReference}.
 * <p/>
 * The Cache is implemented by Caffeine which provides an <a href="https://github.com/ben-manes/caffeine/wiki/Efficiency">efficient cache</a>.
 * <p/>
 * This implementation uses {@link java.lang.ref.WeakReference} for stored values in the cache, to support the JVM
 * when it wants to reclaim objects for example during garbage collection. Therefore this implementation does
 * not support <b>all</b> the {@link java.util.Map} methods.
 * <p/>
 * The following methods is <b>only</b> be be used:
 * <ul>
 *   <li>containsKey - To determine if the key is in the cache and refers to a value</li>
 *   <li>entrySet - To return a set of all the entries (as key/value paris)</li>
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
 * Notice that if the JVM reclaim memory the content of this cache may be garbage collected, without any
 * eviction notifications.
 * <p/>
 * Use {@link org.apache.camel.support.LRUCacheFactory} to create a new instance (do not use the constructor).
 *
 * @see org.apache.camel.support.LRUCacheFactory
 * @see CaffeineLRUCache
 * @see CaffeineLRUSoftCache
 */
public class CaffeineLRUWeakCache<K, V> extends CaffeineLRUCache<K, V> {

    public CaffeineLRUWeakCache(int maximumCacheSize) {
        this(16, maximumCacheSize);
    }

    public CaffeineLRUWeakCache(int initialCapacity, int maximumCacheSize) {
        this(initialCapacity, maximumCacheSize, false);
    }

    public CaffeineLRUWeakCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        super(initialCapacity, maximumCacheSize, stopOnEviction, false, true, false);
    }

    @Override
    public String toString() {
        return "CaffeineLRUWeakCache@" + ObjectHelper.getIdentityHashCode(this);
    }
}