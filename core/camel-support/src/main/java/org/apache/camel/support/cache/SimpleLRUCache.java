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

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code SimpleLRUCache} is a simple implementation of a cache of type Least Recently Used . The implementation doesn't
 * accept null values. Generally speaking, the parameters of all the public methods must have a value otherwise a
 * {@code NullPointerException} is thrown.
 *
 * @param <K> type of the key
 * @param <V> type of the value
 */
public class SimpleLRUCache<K, V> extends ConcurrentHashMap<K, V> {

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
     * The lock to prevent the addition of changes during the swap of queue of changes.
     */
    private final ReadWriteLock swapLock = new ReentrantReadWriteLock();
    /**
     * The maximum cache size.
     */
    private final int maximumCacheSize;
    /**
     * The last changes recorded.
     */
    private final AtomicReference<Deque<Entry<K, V>>> lastChanges = new AtomicReference<>(new ConcurrentLinkedDeque<>());
    /**
     * The function to call when an entry is evicted.
     */
    private final Consumer<V> evict;

    public SimpleLRUCache(int initialCapacity, int maximumCacheSize, Consumer<V> evicted) {
        super(initialCapacity, DEFAULT_LOAD_FACTOR);
        if (maximumCacheSize <= 0) {
            throw new IllegalArgumentException("The maximum cache size must be greater than 0");
        }
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
    private V addChange(OperationContext<K, V> context, Function<? super K, ? extends V> mappingFunction) {
        K key = context.key;
        V value = mappingFunction.apply(key);
        if (value == null) {
            return null;
        }
        Entry<K, V> entry = Map.entry(key, value);
        swapLock.readLock().lock();
        try {
            lastChanges.get().add(entry);
        } finally {
            swapLock.readLock().unlock();
        }
        return value;
    }

    @Override
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            super.compute(
                    key,
                    (k, v) -> {
                        context.result = v;
                        return addChange(context, x -> value);
                    });
            return context.result;
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            super.compute(
                    key,
                    (k, v) -> {
                        context.result = v;
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
            return super.computeIfAbsent(key, k -> addChange(context, mappingFunction));
        }
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return super.computeIfPresent(key, (k, v) -> addChange(context, x -> remappingFunction.apply(x, v)));
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return super.compute(key, (k, v) -> addChange(context, x -> remappingFunction.apply(x, v)));
        }
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            return super.compute(
                    key,
                    (k, oldValue) -> {
                        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
                        return addChange(context, x -> newValue);
                    });
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        try (OperationContext<K, V> context = new OperationContext<>(this, key)) {
            super.computeIfPresent(
                    key,
                    (k, v) -> {
                        if (Objects.equals(oldValue, v)) {
                            context.result = addChange(context, x -> newValue);
                            return context.result;
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
            super.computeIfPresent(
                    key,
                    (k, v) -> {
                        context.result = v;
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
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        for (Entry<? extends K, ? extends V> e : entrySet()) {
            replace(e.getKey(), e.getValue(), function.apply(e.getKey(), e.getValue()));
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(super.entrySet());
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
    private Entry<K, V> nextOldestChange() {
        return lastChanges.get().poll();
    }

    /**
     * Removes duplicates from the queue of changes.
     */
    private void compressChanges() {
        Deque<Entry<K, V>> newChanges = new ConcurrentLinkedDeque<>();
        Deque<Entry<K, V>> currentChanges;
        swapLock.writeLock().lock();
        try {
            currentChanges = lastChanges.getAndSet(newChanges);
        } finally {
            swapLock.writeLock().unlock();
        }
        Set<K> keys = new HashSet<>();
        Entry<K, V> entry;
        while ((entry = currentChanges.pollLast()) != null) {
            if (keys.add(entry.getKey())) {
                newChanges.addFirst(entry);
            }
        }
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
        }

        @Override
        public void close() {
            if (cache.evictionNeeded() && cache.eviction.compareAndSet(false, true)) {
                try {
                    do {
                        cache.compressChanges();
                        if (cache.isCacheFull()) {
                            Entry<K, V> oldest = cache.nextOldestChange();
                            if (oldest != null && cache.remove(oldest.getKey(), oldest.getValue())) {
                                cache.evict.accept(oldest.getValue());
                            }
                        } else {
                            break;
                        }
                    } while (cache.evictionNeeded());
                } finally {
                    cache.eviction.set(false);
                }
            }
        }
    }
}
