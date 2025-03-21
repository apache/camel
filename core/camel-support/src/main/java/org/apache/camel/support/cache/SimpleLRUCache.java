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

import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code SimpleLRUCache} is a simple implementation of a cache of type Least Recently Used . The implementation doesn't
 * accept null values. Generally speaking, the parameters of all the public methods must have a value otherwise a
 * {@code NullPointerException} is thrown.
 *
 * @param <K> type of the key
 * @param <V> type of the value
 */
public class SimpleLRUCache<K, V> implements Map<K, V> {

    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    /**
     * The minimum size of the queue of changes.
     */
    static final int MINIMUM_QUEUE_SIZE = 128;
    /**
     * The flag indicating that an eviction process is in progress.
     */
    private final AtomicBoolean eviction = new AtomicBoolean();
    /**
     * The lock to prevent the addition of changes during the swap of queue of changes or the cache cleaning.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    /**
     * The maximum cache size.
     */
    private final int maximumCacheSize;
    /**
     * The last changes recorded.
     */
    private final AtomicReference<Deque<Entry<K, ValueHolder<V>>>> lastChanges
            = new AtomicReference<>(new ConcurrentLinkedDeque<>());
    /**
     * The function to call when an entry is evicted.
     */
    private final Consumer<V> evict;
    /**
     * The sequence number used to generate a unique id for each cache change.
     */
    private final AtomicLong sequence = new AtomicLong();
    /**
     * The underlying map.
     */
    private final Map<K, ValueHolder<V>> delegate;

    public SimpleLRUCache(int initialCapacity, int maximumCacheSize, Consumer<V> evicted) {
        if (maximumCacheSize <= 0) {
            throw new IllegalArgumentException("The maximum cache size must be greater than 0");
        }
        this.delegate = new ConcurrentHashMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
        this.maximumCacheSize = maximumCacheSize;
        this.evict = Objects.requireNonNull(evicted);
    }

    /**
     * Adds a new change in case the mapping function doesn't return {@code null}.
     *
     * @param  context         the context of the write operation
     * @param  mappingFunction the mapping function to apply.
     * @return                 the result of the mapping function.
     */
    private ValueHolder<V> addChange(OperationContext<K, V> context, Function<? super K, ? extends V> mappingFunction) {
        K key = context.key;
        V value = mappingFunction.apply(key);
        if (value == null) {
            return null;
        }
        ValueHolder<V> holder = newValue(value);
        lastChanges.get().add(Map.entry(key, holder));
        return holder;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();
        return delegate.values().stream()
                .map(ValueHolder::get)
                .anyMatch(v -> Objects.equals(v, value));
    }

    @Override
    public V get(Object key) {
        return extractValue(delegate.get(key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object key, Object value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        K keyK = (K) key;
        try (OperationContext<K, V> context = new OperationContext<>(this, keyK)) {
            delegate.compute(
                    keyK,
                    (k, v) -> {
                        V extractedValue = extractValue(v);
                        if (Objects.equals(value, extractedValue)) {
                            context.result = extractedValue;
                            return null;
                        }
                        return v;
                    });
            return context.result != null;
        }
    }

    @Override
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            delegate.compute(
                    key,
                    (k, v) -> {
                        context.result = extractValue(v);
                        return addChange(context, x -> value);
                    });
            return context.result;
        }
    }

    @Override
    public V remove(Object key) {
        return extractValue(delegate.remove(key));
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            delegate.compute(
                    key,
                    (k, v) -> {
                        context.result = extractValue(v);
                        if (v != null) {
                            return v;
                        }
                        return addChange(context, x -> value);
                    });
            return context.result;
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return extractValue(delegate.computeIfAbsent(key, k -> addChange(context, mappingFunction)));
        }
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return extractValue(delegate.computeIfPresent(key,
                    (k, v) -> addChange(context, x -> remappingFunction.apply(x, extractValue(v)))));
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return extractValue(
                    delegate.compute(key, (k, v) -> addChange(context, x -> remappingFunction.apply(x, extractValue(v)))));
        }
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return extractValue(delegate.compute(
                    key,
                    (k, oldValue) -> {
                        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue.get(), value);
                        return addChange(context, x -> newValue);
                    }));
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            delegate.computeIfPresent(
                    key,
                    (k, v) -> {
                        if (Objects.equals(oldValue, extractValue(v))) {
                            ValueHolder<V> result = addChange(context, x -> newValue);
                            context.result = extractValue(result);
                            return result;
                        }
                        return v;
                    });
            return context.result != null && Objects.equals(context.result, newValue);
        }
    }

    @Override
    public V replace(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            delegate.computeIfPresent(
                    key,
                    (k, v) -> {
                        context.result = extractValue(v);
                        return addChange(context, x -> value);
                    });
            return context.result;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            lastChanges.getAndSet(new ConcurrentLinkedDeque<>());
            delegate.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        return delegate.values().stream().map(ValueHolder::get).toList();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        for (Entry<? extends K, ? extends V> e : entrySet()) {
            K key = e.getKey();
            V value = e.getValue();
            try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
                delegate.computeIfPresent(
                        key,
                        (k, v) -> addChange(context, x -> function.apply(x, value)));
            }
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return delegate.entrySet().stream()
                .map(entry -> new CacheEntry<>(this, entry.getKey(), entry.getValue().get()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return the size of the queue of changes.
     */
    int getQueueSize() {
        return lastChanges.get().size();
    }

    /**
     * Indicates whether an eviction is needed. An eviction can be triggered if either the cache or the queue is full.
     *
     * @return {@code true} if an eviction is needed, {@code false} otherwise.
     */
    private boolean evictionNeeded() {
        return isCacheFull() || isQueueFull();
    }

    /**
     * Indicates whether the size of the map exceeds the maximum allowed size which is {@code maximumCacheSize}.
     *
     * @return {@code true} if the cache is full, {@code false} otherwise.
     */
    private boolean isCacheFull() {
        return size() > maximumCacheSize;
    }

    /**
     * Indicates whether the size of the queue of changes exceeds the maximum allowed size which is the max value
     * between {@link #MINIMUM_QUEUE_SIZE} and {@code 2 * maximumCacheSize}.
     *
     * @return {@code true} if the queue is full, {@code false} otherwise.
     */
    private boolean isQueueFull() {
        return getQueueSize() > Math.max(2 * maximumCacheSize, MINIMUM_QUEUE_SIZE);
    }

    /**
     * @return the oldest existing change.
     */
    private Entry<K, ValueHolder<V>> nextOldestChange() {
        return lastChanges.get().poll();
    }

    /**
     * Removes duplicates from the queue of changes if the queue is full.
     */
    private void compressChangesIfNeeded() {
        Deque<Entry<K, ValueHolder<V>>> newChanges;
        Deque<Entry<K, ValueHolder<V>>> currentChanges;
        lock.writeLock().lock();
        try {
            if (isQueueFull()) {
                newChanges = new ConcurrentLinkedDeque<>();
                currentChanges = lastChanges.getAndSet(newChanges);
            } else {
                return;
            }
        } finally {
            lock.writeLock().unlock();
        }
        Set<K> keys = new HashSet<>();
        Entry<K, ValueHolder<V>> entry;
        while ((entry = currentChanges.pollLast()) != null) {
            if (keys.add(entry.getKey())) {
                newChanges.addFirst(entry);
            }
        }
    }

    private ValueHolder<V> newValue(V value) {
        return new ValueHolder<>(sequence.incrementAndGet(), value);
    }

    private V extractValue(ValueHolder<V> holder) {
        return holder == null ? null : holder.get();
    }

    /**
     * The internal context of all write operations.
     */
    private static class OperationContext<K, V> implements AutoCloseable {
        /**
         * The result of the corresponding operation when applicable.
         */
        V result;
        /**
         * The key against which the operation is made.
         */
        final K key;
        /**
         * The underlying cache.
         */
        private final SimpleLRUCache<K, V> cache;

        OperationContext(SimpleLRUCache<K, V> cache, K key) {
            this.cache = cache;
            this.key = key;
            cache.lock.readLock().lock();
        }

        @Override
        public void close() {
            cache.lock.readLock().unlock();
            if (cache.evictionNeeded() && cache.eviction.compareAndSet(false, true)) {
                try {
                    do {
                        cache.compressChangesIfNeeded();
                        if (cache.isCacheFull()) {
                            Entry<K, ValueHolder<V>> oldest = cache.nextOldestChange();
                            if (cache.delegate.remove(oldest.getKey(), oldest.getValue())) {
                                cache.evict.accept(oldest.getValue().get());
                            }
                        }
                    } while (cache.evictionNeeded());
                } finally {
                    cache.eviction.set(false);
                }
            }
        }
    }

    /**
     * A cache value holder that leverages a revision id to be able to distinguish the same key value pair that has been
     * added several times to the cache.
     *
     * @param <V> the type of the value
     */
    private static class ValueHolder<V> {
        private final long revision;
        private final V value;

        ValueHolder(long revision, V value) {
            this.revision = revision;
            this.value = value;
        }

        V get() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            ValueHolder<?> that = (ValueHolder<?>) o;
            return revision == that.revision;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(revision);
        }
    }

    /**
     * A modifiable cache entry.
     *
     * @param <K> the type of the key
     * @param <V> the type of the value
     */
    private static class CacheEntry<K, V> implements Entry<K, V> {

        private final K key;
        private V val;
        /**
         * The underlying cache.
         */
        private final SimpleLRUCache<K, V> cache;

        CacheEntry(SimpleLRUCache<K, V> cache, K key, V value) {
            this.cache = cache;
            this.key = key;
            this.val = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return val;
        }

        @Override
        public V setValue(V value) {
            if (value == null)
                throw new NullPointerException();
            V v = val;
            val = value;
            cache.put(key, value);
            return v;
        }
    }
}
