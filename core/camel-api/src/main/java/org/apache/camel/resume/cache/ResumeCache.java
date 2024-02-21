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

package org.apache.camel.resume.cache;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.resume.ResumeStrategy;

/**
 * This cache stored the resumed data from a {@link ResumeStrategy}.
 *
 * @param <K> the type of the key
 */
public interface ResumeCache<K> {
    /**
     * If the specified key is not present, compute its value from the mapping function (like Java's standard Map one)
     *
     * @param  key     the key to get or associate with the value
     * @param  mapping the mapping function used to compute the value
     * @return         the value associated with the key (either the present or the one computed from the mapping
     *                 function)
     */
    Object computeIfAbsent(K key, Function<? super K, ? super Object> mapping);

    /**
     * If the specified key is present, compute a new value from the mapping function (like Java's standard Map one)
     *
     * @param  key       the key to get or associate with the value
     * @param  remapping the remapping function used to compute the new value
     * @return           the value associated with the key (either the present or the one computed from the mapping
     *                   function)
     */
    Object computeIfPresent(K key, BiFunction<? super K, ? super Object, ? super Object> remapping);

    /**
     * Whether the cache contains the key with the given entry value
     *
     * @param  key   the key
     * @param  entry the entry
     * @return       true if the key/entry pair is stored in the cache
     */
    boolean contains(K key, Object entry);

    /**
     * Adds a value to the cache
     *
     * @param key         the key to add
     * @param offsetValue the offset value
     */
    void add(K key, Object offsetValue);

    /**
     * Checks whether the cache is full
     *
     * @return true if full, or false otherwise
     */
    boolean isFull();

    /**
     * Gets the cache pool size
     */
    long capacity();

    /**
     * Gets the offset entry for the key
     *
     * @param  key   the key
     * @param  clazz the class object representing the value to be obtained
     * @return       the offset value wrapped in an optional
     */
    <T> T get(K key, Class<T> clazz);

    /**
     * Gets the offset entry for the key
     *
     * @param  key the key
     * @return     the offset value
     */
    Object get(K key);

    /**
     * Performs the given action for each member of the cache
     *
     * @param action the action to execute
     */
    void forEach(BiFunction<? super K, ? super Object, Boolean> action);
}
