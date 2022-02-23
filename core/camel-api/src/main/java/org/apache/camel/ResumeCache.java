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

import java.util.Optional;

/**
 * This cache stored the resumed data from a {@link ResumeStrategy}.
 * 
 * @param <K> the type of the key
 * @param <V> the type of the offset value
 */
public interface ResumeCache<K, V> {

    /**
     * Adds a value to the cache
     * 
     * @param key         the key to add
     * @param offsetValue the offset value
     */
    void add(K key, V offsetValue);

    /**
     * Checks whether the cache is full
     * 
     * @return true if full, or false otherwise
     */
    boolean isFull();

    /**
     * Gets the offset value for the key
     * 
     * @param  key the key
     * @return     the key
     */
    Optional<V> get(K key);
}
