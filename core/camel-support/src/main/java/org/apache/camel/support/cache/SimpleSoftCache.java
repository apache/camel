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
package org.apache.camel.support.cache;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code SimpleSoftCache} is a simple implementation of a cache where values are soft references which allows the
 * Garbage Collector to clear the referents in response of a memory demand to potentially prevent
 * {@code OutOfMemoryError}. The entries where the referent is missing are removed lazily when they are accessed
 * directly or indirectly through the {@code Map} API. The implementation doesn't accept null values. Generally
 * speaking, the parameters of all the public methods must have a value otherwise a {@code NullPointerException} is
 * thrown.
 *
 * @param <K> type of the key
 * @param <V> type of the value
 * @see       SimpleLRUCache
 */
public class SimpleSoftCache<K, V> implements Map<K, V> {

    /**
     * The underlying cache to which the modifications are applied to.
     */
    private final Map<K, SoftReference<V>> delegate;

    /**
     * Constructs a {@code SimpleSoftCache} with the given underlying cache.
     *
     * @param delegate the underlying cache to which the modifications are applied to. Be aware that the implementation
     *                 of the provided map must accept concurrent modifications to allow lazy evictions of empty
     *                 references.
     */
    public SimpleSoftCache(Map<K, SoftReference<V>> delegate) {
        this.delegate = delegate;
    }

    /**
     * @return the size of the cache without considering if the soft references still have a referent set for the sake
     *         of simplicity and efficiency.
     */
    @Override
    public int size() {
        return delegate.size();
    }

    /**
     * Returns true if this map contains no key-value mappings without considering if the soft references still have a
     * referent set for the sake of simplicity and efficiency.
     */
    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Entry<K, SoftReference<V>> entry : delegate.entrySet()) {
            SoftReference<V> ref = entry.getValue();
            V refVal = ref.get();
            if (refVal == null) {
                delegate.remove(entry.getKey(), ref);
            } else if (Objects.equals(value, refVal)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        SoftReference<V> ref = delegate.get(key);
        if (ref == null) {
            return null;
        }
        V v = ref.get();
        if (v == null) {
            delegate.remove(key, ref);
        }
        return v;
    }

    @Override
    public V put(K key, V value) {
        SoftReference<V> prev = delegate.put(key, new SoftReference<>(value));
        return prev == null ? null : prev.get();
    }

    @Override
    public V remove(Object key) {
        SoftReference<V> prev = delegate.remove(key);
        return prev == null ? null : prev.get();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> e : m.entrySet()) {
            delegate.put(e.getKey(), new SoftReference<>(e.getValue()));
        }
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        return delegate.values().stream().map(Reference::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> result = new HashSet<>(delegate.size());
        for (Entry<K, SoftReference<V>> entry : delegate.entrySet()) {
            SoftReference<V> ref = entry.getValue();
            V v = ref.get();
            if (v == null) {
                delegate.remove(entry.getKey(), ref);
                continue;
            }
            result.add(Map.entry(entry.getKey(), v));
        }
        return result;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach((k, ref) -> {
            V v = ref.get();
            if (v == null) {
                delegate.remove(k, ref);
            } else {
                action.accept(k, v);
            }
        });
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        for (Entry<? extends K, ? extends V> e : entrySet()) {
            replace(e.getKey(), e.getValue(), function.apply(e.getKey(), e.getValue()));
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            V prev = null;
            if (ref == null) {
                SoftReference<V> prevRef = delegate.putIfAbsent(key, new SoftReference<>(value));
                if (prevRef != null && (prev = prevRef.get()) == null) {
                    // The referent is missing let's try again
                    delegate.remove(key, prevRef);
                    continue;
                }
            } else {
                prev = ref.get();
                if (prev == null && !delegate.replace(key, ref, new SoftReference<>(value))) {
                    // The state has changed, let's try again
                    continue;
                }
            }
            return prev;
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            if (ref != null) {
                V v = ref.get();
                if (v == null || Objects.equals(v, value)) {
                    if (delegate.remove(key, ref)) {
                        return v != null;
                    }
                    // The state has changed, let's try again
                    continue;
                }
            }
            return false;
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            if (ref != null) {
                V v = ref.get();
                if (v == null) {
                    if (!delegate.remove(key, ref)) {
                        // The state has changed, let's try again
                        continue;
                    }
                } else if (Objects.equals(v, oldValue)) {
                    if (!delegate.replace(key, ref, new SoftReference<>(newValue))) {
                        // The state has changed, let's try again
                        continue;
                    }
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public V replace(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            if (ref != null) {
                V v = ref.get();
                if (v == null) {
                    if (!delegate.remove(key, ref)) {
                        // The state has changed, let's try again
                        continue;
                    }
                } else if (!delegate.replace(key, ref, new SoftReference<>(value))) {
                    // The state has changed, let's try again
                    continue;
                }
                return v;
            }
            return null;
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            if (ref == null) {
                V newValue = mappingFunction.apply(key);
                if (newValue != null && delegate.putIfAbsent(key, new SoftReference<>(newValue)) != null) {
                    // The state has changed, let's try again
                    continue;
                }
                return newValue;
            } else {
                V v = ref.get();
                if (v == null) {
                    // The referent is missing let's try again
                    delegate.remove(key, ref);
                    continue;
                }
                return v;
            }
        }
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            if (ref != null) {
                V v = ref.get();
                if (v == null) {
                    if (delegate.remove(key, ref)) {
                        return null;
                    }
                    // The state has changed, let's try again
                    continue;
                }
                V newValue = remappingFunction.apply(key, v);
                if (newValue == null) {
                    if (!delegate.remove(key, ref)) {
                        // The state has changed, let's try again
                        continue;
                    }
                } else if (!delegate.replace(key, ref, new SoftReference<>(newValue))) {
                    // The state has changed, let's try again
                    continue;
                }
                return newValue;
            }
            return null;
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            V oldValue = ref == null ? null : ref.get();
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue == null) {
                // delete mapping
                if (ref != null && !delegate.remove(key, ref)) {
                    // The state has changed, let's try again
                    continue;
                }
            } else if (ref == null) {
                if (delegate.putIfAbsent(key, new SoftReference<>(newValue)) != null) {
                    // The state has changed, let's try again
                    continue;
                }
            } else if (!delegate.replace(key, ref, new SoftReference<>(newValue))) {
                // The state has changed, let's try again
                continue;
            }
            return newValue;
        }
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        for (;;) {
            SoftReference<V> ref = delegate.get(key);
            V oldValue = ref == null ? null : ref.get();
            V newValue = oldValue == null ? value : remappingFunction.apply(oldValue, value);
            if (newValue == null) {
                if (!delegate.remove(key, ref)) {
                    // The state has changed, let's try again
                    continue;
                }
            } else if (ref == null) {
                if (delegate.putIfAbsent(key, new SoftReference<>(newValue)) != null) {
                    // The state has changed, let's try again
                    continue;
                }
            } else if (!delegate.replace(key, ref, new SoftReference<>(newValue))) {
                // The state has changed, let's try again
                continue;
            }
            return newValue;
        }
    }

    /**
     * Only meant for testing purpose.
     *
     * @return the underlying cache.
     */
    Map<K, SoftReference<V>> getInnerCache() {
        return delegate;
    }
}
