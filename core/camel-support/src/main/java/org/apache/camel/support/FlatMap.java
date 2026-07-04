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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * A lightweight {@link Map} backed by a flat {@code Object[]} with alternating key/value pairs. Optimized for very
 * small maps (0-8 entries) where linear scan is faster than hashing due to cache locality and zero per-entry object
 * overhead.
 * <p/>
 * Not thread-safe. Keys are compared by {@link Object#equals}.
 */
class FlatMap<K, V> extends AbstractMap<K, V> {

    private static final int DEFAULT_CAPACITY = 4;

    private Object[] data;
    private int size;

    FlatMap() {
        this(DEFAULT_CAPACITY);
    }

    FlatMap(int initialCapacity) {
        this.data = new Object[initialCapacity * 2];
    }

    FlatMap(Map<? extends K, ? extends V> source) {
        this.data = new Object[Math.max(source.size(), DEFAULT_CAPACITY) * 2];
        for (Entry<? extends K, ? extends V> e : source.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        int i = indexOf(key);
        return i >= 0 ? (V) data[i + 1] : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        int i = indexOf(key);
        if (i >= 0) {
            V old = (V) data[i + 1];
            data[i + 1] = value;
            return old;
        }
        int pos = size * 2;
        if (pos >= data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[pos] = key;
        data[pos + 1] = value;
        size++;
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        int i = indexOf(key);
        if (i < 0) {
            return null;
        }
        V old = (V) data[i + 1];
        int last = (size - 1) * 2;
        if (i < last) {
            data[i] = data[last];
            data[i + 1] = data[last + 1];
        }
        data[last] = null;
        data[last + 1] = null;
        size--;
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        int needed = (size + m.size()) * 2;
        if (needed > data.length) {
            data = Arrays.copyOf(data, Math.max(needed, data.length * 2));
        }
        for (Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        Arrays.fill(data, 0, size * 2, null);
        size = 0;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private int indexOf(Object key) {
        for (int i = 0, len = size * 2; i < len; i += 2) {
            if (Objects.equals(key, data[i])) {
                return i;
            }
        }
        return -1;
    }

    private class EntrySet extends AbstractSet<Entry<K, V>> {
        @Override
        public int size() {
            return size;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new Iterator<>() {
                private int index;
                private int lastReturned = -1;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                @SuppressWarnings("unchecked")
                public Entry<K, V> next() {
                    if (index >= size) {
                        throw new NoSuchElementException();
                    }
                    lastReturned = index;
                    int pos = index * 2;
                    index++;
                    return new FlatEntry(pos);
                }

                @Override
                public void remove() {
                    if (lastReturned < 0) {
                        throw new IllegalStateException();
                    }
                    FlatMap.this.removeAt(lastReturned * 2);
                    index = lastReturned;
                    lastReturned = -1;
                }
            };
        }

        @Override
        public void clear() {
            FlatMap.this.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private void removeAt(int pos) {
        int last = (size - 1) * 2;
        if (pos < last) {
            data[pos] = data[last];
            data[pos + 1] = data[last + 1];
        }
        data[last] = null;
        data[last + 1] = null;
        size--;
    }

    private class FlatEntry implements Entry<K, V> {
        private final int pos;

        FlatEntry(int pos) {
            this.pos = pos;
        }

        @Override
        @SuppressWarnings("unchecked")
        public K getKey() {
            return (K) data[pos];
        }

        @Override
        @SuppressWarnings("unchecked")
        public V getValue() {
            return (V) data[pos + 1];
        }

        @Override
        @SuppressWarnings("unchecked")
        public V setValue(V value) {
            V old = (V) data[pos + 1];
            data[pos + 1] = value;
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }
            return Objects.equals(getKey(), e.getKey()) && Objects.equals(getValue(), e.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }
    }
}
