/**
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
package org.apache.camel.spi;

import org.apache.camel.Service;

/**
 * This {@link StateRepository} holds a set of key/value pairs for defining a particular <em>state</em> of a component. For instance it can be a set of indexes.
 * <p/>
 * An {@link IdempotentRepository} behaves more or less like a {@code Set} whereas this {@link StateRepository} behaves like a {@code Map}.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public interface StateRepository<K, V> extends Service {

    /**
     * Sets the state value for the given key.
     *
     * @param key State key
     * @param value State value
     */
    void setState(K key, V value);

    /**
     * Gets the state value for the given key. It returns {@code null} if the key is unknown.
     *
     * @param key State key
     * @return State value or null the key is unknown
     */
    V getState(K key);
}

