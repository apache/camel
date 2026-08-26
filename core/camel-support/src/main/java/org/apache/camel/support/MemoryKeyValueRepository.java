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

import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;

/**
 * An in-memory implementation of {@link KeyValueRepository} backed by a {@link ConcurrentHashMap} with lazy TTL
 * eviction.
 * <p/>
 * Expired entries are removed lazily on access ({@link #get}, {@link #contains}) and periodically during
 * {@link #keys()} and {@link #size()} scans. This avoids the overhead of a background eviction thread while still
 * ensuring that expired data is not returned.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "An in-memory KeyValueRepository with optional TTL support.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Memory based key-value repository")
public class MemoryKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * Creates a new in-memory key-value repository.
     */
    public MemoryKeyValueRepository() {
    }

    @Override
    @ManagedOperation(description = "Get value by key")
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
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public void put(String key, Object value, long ttlMillis) {
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        store.put(key, new Entry(value, expiresAt));
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        Entry entry = store.remove(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            return null;
        }
        return entry.value();
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
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
    public Set<String> keys() {
        evictExpired();
        return store.keySet().stream()
                .filter(k -> {
                    Entry e = store.get(k);
                    return e != null && !e.isExpired();
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        store.clear();
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        Entry newEntry = new Entry(value, expiresAt);
        Entry existing = store.putIfAbsent(key, newEntry);
        if (existing == null) {
            return null;
        }
        if (existing.isExpired()) {
            // Try to replace the expired entry atomically
            if (store.replace(key, existing, newEntry)) {
                return null;
            }
            // Another thread beat us -- read what they stored
            Entry updated = store.get(key);
            return updated != null && !updated.isExpired() ? updated.value() : null;
        }
        return existing.value();
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        evictExpired();
        return store.size();
    }

    @Override
    protected void doStop() throws Exception {
        store.clear();
    }

    private void evictExpired() {
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> mapEntry = it.next();
            if (mapEntry.getValue().isExpired()) {
                it.remove();
            }
        }
    }

    /**
     * An entry in the repository holding the value and its expiration timestamp.
     */
    static final class Entry implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final Object value;
        private final long expiresAt;

        Entry(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        Object value() {
            return value;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
