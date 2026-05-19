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
package org.apache.camel.component.statestore;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default in-memory implementation of {@link StateStoreBackend} using a {@link ConcurrentHashMap}. Supports optional
 * TTL with lazy expiry (entries are checked on access, no background thread).
 */
public class InMemoryStateStoreBackend implements StateStoreBackend {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Object put(String key, Object value, long ttlMillis) {
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0;
        Entry previous = store.put(key, new Entry(value, expiresAt));
        if (previous != null && !previous.isExpired()) {
            return previous.value();
        }
        return null;
    }

    @Override
    public Object get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    @Override
    public Object delete(String key) {
        Entry entry = store.remove(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        return null;
    }

    @Override
    public boolean contains(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            store.remove(key, entry);
            return false;
        }
        return true;
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0;
        Object[] result = new Object[1];
        store.compute(key, (k, current) -> {
            if (current == null || current.isExpired()) {
                return new Entry(value, expiresAt);
            }
            result[0] = current.value();
            return current;
        });
        return result[0];
    }

    @Override
    public int size() {
        return (int) store.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .count();
    }

    @Override
    public Set<String> keys() {
        return store.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        store.clear();
    }

    private record Entry(Object value, long expiresAt) {

        boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }
    }
}
