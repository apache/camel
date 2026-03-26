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

/**
 * Interface for pluggable state store backends.
 */
public interface StateStoreBackend {

    /**
     * Store a value with the given key and optional TTL.
     *
     * @param  key       the key
     * @param  value     the value to store
     * @param  ttlMillis time-to-live in milliseconds (0 = no expiry)
     * @return           the previous value associated with the key, or null
     */
    Object put(String key, Object value, long ttlMillis);

    /**
     * Store a value only if the key does not already exist.
     * <p>
     * <b>Note:</b> The default implementation is not atomic. Implementations that require thread safety should override
     * this method with an atomic version (e.g., using the backing store's native putIfAbsent).
     *
     * @param  key       the key
     * @param  value     the value to store
     * @param  ttlMillis time-to-live in milliseconds (0 = no expiry)
     * @return           the existing value if the key already exists, or null if the value was stored
     */
    default Object putIfAbsent(String key, Object value, long ttlMillis) {
        if (contains(key)) {
            return get(key);
        }
        put(key, value, ttlMillis);
        return null;
    }

    /**
     * Retrieve the value associated with the given key.
     *
     * @param  key the key
     * @return     the value, or null if not found or expired
     */
    Object get(String key);

    /**
     * Remove the value associated with the given key.
     *
     * @param  key the key
     * @return     the removed value, or null if not found
     */
    Object delete(String key);

    /**
     * Check if a key exists in the store.
     *
     * @param  key the key
     * @return     true if the key exists and has not expired
     */
    boolean contains(String key);

    /**
     * Return all keys in the store.
     *
     * @return a set of all keys
     */
    Set<String> keys();

    /**
     * Return the number of entries in the store.
     *
     * @return the number of entries
     */
    default int size() {
        return keys().size();
    }

    /**
     * Remove all entries from the store.
     */
    void clear();

    /**
     * Start the backend (lifecycle).
     */
    void start();

    /**
     * Stop the backend (lifecycle).
     */
    void stop();
}
