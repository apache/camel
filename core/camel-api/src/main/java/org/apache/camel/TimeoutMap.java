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
package org.apache.camel;

/**
 * Represents a map of values which timeout after a period of inactivity.
 */
public interface TimeoutMap<K, V> extends Service {

    /**
     * Looks up the value in the map by the given key.
     *
     * @param key the key of the value to search for
     * @return the value for the given key or <tt>null</tt> if it is not present (or has timed out)
     */
    V get(K key);

    /**
     * Returns the size of the map
     *
     * @return the size
     */
    int size();

    /**
     * Adds the key value pair into the map such that some time after the given
     * timeout the entry will be evicted
     *
     * @param key   the key
     * @param value the value
     * @param timeoutMillis  timeout in millis
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     */
    V put(K key, V value, long timeoutMillis);
    
    /**
     * Adds the key value pair into the map if the specified key is not already associated with a value
     * such that some time after the given timeout the entry will be evicted. Expiry time of an existing mapping
     * is left unchanged.
     *
     * @param key   the key
     * @param value the value
     * @param timeoutMillis  timeout in millis
     * @return the value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     */
    V putIfAbsent(K key, V value, long timeoutMillis);

    /**
     * Removes the object with the given key
     *
     * @param key  key for the object to remove
     * @return the value for the given key or <tt>null</tt> if it is not present (or has timed out)
     */
    V remove(K key);

    /**
     * Assign the (singular) {@link Listener}
     *
     * @param listener the new listener
     */
    void addListener(Listener<K, V> listener);

    @FunctionalInterface
    interface Listener<K, V> {

        enum Type { Put, Remove, Evict }

        /**
         * Callback when the map changes content
         *
         * @param key the item key
         * @param value the item value
         */
        void timeoutMapEvent(Type type, K key, V value);

    }
}
