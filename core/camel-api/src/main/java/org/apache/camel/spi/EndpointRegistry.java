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

import java.util.Collection;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.StaticService;

/**
 * Registry to cache endpoints in memory.
 * <p/>
 * The registry contains two caches:
 * <ul>
 * <li>static - which keeps all the endpoints in the cache for the entire lifecycle</li>
 * <li>dynamic - which keeps the endpoints in a {@link org.apache.camel.support.LRUCache} and may evict endpoints which
 * hasn't been requested recently</li>
 * </ul>
 * The static cache stores all the endpoints that are created as part of setting up and starting routes. The static
 * cache has no upper limit.
 * <p/>
 * The dynamic cache stores the endpoints that are created and used ad-hoc, such as from custom Java code that creates
 * new endpoints etc. The dynamic cache has an upper limit, that by default is 1000 entries.
 *
 * @param <K> endpoint key
 */
public interface EndpointRegistry<K> extends Map<K, Endpoint>, StaticService {

    /**
     * Number of endpoints in the static registry.
     */
    int staticSize();

    /**
     * Number of endpoints in the dynamic registry
     */
    int dynamicSize();

    /**
     * Maximum number of entries to store in the dynamic registry
     */
    int getMaximumCacheSize();

    /**
     * Purges the cache (removes endpoints from the dynamic cache)
     */
    void purge();

    /**
     * Whether the given endpoint is stored in the static cache
     *
     * @param  key the endpoint key
     * @return     <tt>true</tt> if in static cache, <tt>false</tt> if not
     */
    boolean isStatic(String key);

    /**
     * Whether the given endpoint is stored in the dynamic cache
     *
     * @param  key the endpoint key
     * @return     <tt>true</tt> if in dynamic cache, <tt>false</tt> if not
     */
    boolean isDynamic(String key);

    /**
     * Whether the given endpoint is stored in the registry
     *
     * @param  key the endpoint key
     * @return     <tt>true</tt> if present, <tt>false</tt> if not
     */
    @Override
    boolean containsKey(Object key);

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

    /**
     * Gets a read-only collection of the endpoints currently in the registry.
     */
    Collection<Endpoint> getReadOnlyValues();

    /**
     * Gets a read-only map of the endpoints currently in the registry.
     */
    Map<String, Endpoint> getReadOnlyMap();

}
