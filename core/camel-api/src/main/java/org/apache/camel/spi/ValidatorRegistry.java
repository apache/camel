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
package org.apache.camel.spi;

import java.util.Map;

import org.apache.camel.StaticService;
import org.jspecify.annotations.Nullable;

/**
 * Registry that stores and resolves {@link Validator} instances for Camel's
 * <a href="https://camel.apache.org/manual/transformer.html">data type validation</a> mechanism.
 * <p/>
 * The registry maintains two separate caches mirroring the design of {@link TransformerRegistry}:
 * <ul>
 * <li><b>static cache</b> — holds all validators registered at route startup; no eviction, no size limit. Contains
 * every validator bound to a {@link DataType} when the {@link org.apache.camel.CamelContext} starts.</li>
 * <li><b>dynamic cache</b> — holds validators created or registered at runtime (for example, from custom Java code or
 * hot-deployed routes); backed by an LRU cache with a configurable size limit (default 1000 entries) to prevent
 * unbounded growth.</li>
 * </ul>
 * Lookup is performed by {@link #resolveValidator(ValidatorKey)}, which searches both caches using the {@link DataType}
 * encoded in the {@link ValidatorKey}.
 *
 * @see Validator
 * @see ValidatorKey
 * @see DataType
 */
public interface ValidatorRegistry extends Map<ValidatorKey, Validator>, StaticService {

    /**
     * Lookup a {@link Validator} in the registry which supports the validation for the data type represented by the
     * key.
     *
     * @param  key a key represents the data type
     * @return     {@link Validator} if matched, otherwise null
     */
    @Nullable
    Validator resolveValidator(ValidatorKey key);

    /**
     * Number of validators in the static registry.
     */
    int staticSize();

    /**
     * Number of validators in the dynamic registry
     */
    int dynamicSize();

    /**
     * Maximum number of entries to store in the dynamic registry
     */
    int getMaximumCacheSize();

    /**
     * Purges the cache (removes validators from the dynamic cache)
     */
    void purge();

    /**
     * Whether the given {@link Validator} is stored in the static cache
     *
     * @param  type the data type
     * @return      <tt>true</tt> if in static cache, <tt>false</tt> if not
     */
    boolean isStatic(DataType type);

    /**
     * Whether the given {@link Validator} is stored in the dynamic cache
     *
     * @param  type the data type
     * @return      <tt>true</tt> if in dynamic cache, <tt>false</tt> if not
     */
    boolean isDynamic(DataType type);

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

}
