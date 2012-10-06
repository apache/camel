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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A Least Recently Used Cache which uses {@link java.lang.ref.WeakReference}.
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
 *
 * @see LRUCache
 * @see LRUSoftCache
 */
public class LRUWeakCache<K, V> extends LRUCache<K, V> {
    private static final long serialVersionUID = 1L;

    public LRUWeakCache(int maximumCacheSize) {
        super(maximumCacheSize);
    }

    public LRUWeakCache(int initialCapacity, int maximumCacheSize) {
        super(initialCapacity, maximumCacheSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        WeakReference<V> put = new WeakReference<V>(value);
        WeakReference<V> prev = (WeakReference<V>) super.put(key, (V) put);
        return prev != null ? prev.get() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object o) {
        WeakReference<V> ref = (WeakReference<V>) super.get(o);
        return ref != null ? ref.get() : null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object o) {
        WeakReference<V> ref = (WeakReference<V>) super.remove(o);
        return ref != null ? ref.get() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<V> values() {
        // return a copy of all the active values
        Collection<WeakReference<V>> col = (Collection<WeakReference<V>>) super.values();
        Collection<V> answer = new ArrayList<V>();
        for (WeakReference<V> ref : col) {
            V value = ref.get();
            if (value != null) {
                answer.add(value);
            }
        }
        return answer;
    }

    @Override
    public int size() {
        // only count as a size if there is a value
        int size = 0;
        for (V value : super.values()) {
            WeakReference<?> ref = (WeakReference<?>) value;
            if (ref != null && ref.get() != null) {
                size++;
            }
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object o) {
        // must lookup if the key has a value, as we only regard a key to be contained
        // if the value is still there (the JVM can remove the soft reference if it need memory)
        return get(o) != null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> original = super.entrySet();

        // must use a copy to avoid concurrent modifications and be able to get/set value using
        // the soft reference so the returned set is without the soft reference, and thus is
        // use able for the caller to use
        Set<Entry<K, V>> answer = new LinkedHashSet<Entry<K, V>>(original.size());
        for (final Entry<K, V> entry : original) {
            Entry<K, V> view = new Entry<K, V>() {
                @Override
                public K getKey() {
                    return entry.getKey();
                }

                @Override
                @SuppressWarnings("unchecked")
                public V getValue() {
                    WeakReference<V> ref = (WeakReference<V>) entry.getValue();
                    return ref != null ? ref.get() : null;
                }

                @Override
                @SuppressWarnings("unchecked")
                public V setValue(V v) {
                    V put = (V) new WeakReference<V>(v);
                    WeakReference<V> prev = (WeakReference<V>) entry.setValue(put);
                    return prev != null ? prev.get() : null;
                }
            };
            answer.add(view);
        }

        return answer;
    }

    @Override
    public String toString() {
        return "LRUWeakCache@" + ObjectHelper.getIdentityHashCode(this);
    }
}