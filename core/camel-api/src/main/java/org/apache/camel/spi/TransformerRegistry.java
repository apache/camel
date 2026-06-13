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
 * Registry that stores and resolves {@link Transformer} instances for Camel's
 * <a href="https://camel.apache.org/manual/transformer.html">data type contract</a> mechanism.
 * <p/>
 * The registry maintains two separate caches to balance memory usage and lookup performance:
 * <ul>
 * <li><b>static cache</b> — holds all transformers registered at route startup; no eviction, no size limit. Contains
 * every transformer bound to a {@link DataType} pair when the {@link org.apache.camel.CamelContext} starts.</li>
 * <li><b>dynamic cache</b> — holds transformers created or registered at runtime (for example, from custom Java code or
 * hot-deployed routes); backed by an LRU cache with a configurable size limit (default 1000 entries) to prevent
 * unbounded growth.</li>
 * </ul>
 * Lookup is performed by {@link #resolveTransformer(TransformerKey)}, which searches both caches using the from/to
 * {@link DataType} pair encoded in the {@link TransformerKey}.
 *
 * @see Transformer
 * @see TransformerKey
 * @see DataType
 */
public interface TransformerRegistry extends Map<TransformerKey, Transformer>, StaticService {

    /**
     * Lookup a {@link Transformer} in the registry which supports the transformation for the data types represented by
     * the key.
     *
     * @param  key a key represents the from/to data types to transform
     * @return     {@link Transformer} if matched, otherwise null
     */
    @Nullable
    Transformer resolveTransformer(TransformerKey key);

    /**
     * Number of transformers in the static registry.
     */
    int staticSize();

    /**
     * Number of transformers in the dynamic registry
     */
    int dynamicSize();

    /**
     * Maximum number of entries to store in the dynamic registry
     */
    int getMaximumCacheSize();

    /**
     * Purges the cache (removes transformers from the dynamic cache)
     */
    void purge();

    /**
     * Whether the given transformer is stored in the static cache
     *
     * @param  scheme the scheme supported by this transformer
     * @return        <tt>true</tt> if in static cache, <tt>false</tt> if not
     */
    boolean isStatic(String scheme);

    /**
     * Whether the given transformer is stored in the static cache
     *
     * @param  from 'from' data type
     * @param  to   'to' data type
     * @return      <tt>true</tt> if in static cache, <tt>false</tt> if not
     */
    boolean isStatic(DataType from, DataType to);

    /**
     * Whether the given transformer is stored in the dynamic cache
     *
     * @param  scheme the scheme supported by this transformer
     * @return        <tt>true</tt> if in dynamic cache, <tt>false</tt> if not
     */
    boolean isDynamic(String scheme);

    /**
     * Whether the given {@link Transformer} is stored in the dynamic cache
     *
     * @param  from 'from' data type
     * @param  to   'to' data type
     * @return      <tt>true</tt> if in dynamic cache, <tt>false</tt> if not
     */
    boolean isDynamic(DataType from, DataType to);

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

}
