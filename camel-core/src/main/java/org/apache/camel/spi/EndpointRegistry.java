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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.StaticService;

/**
 * Registry to store endpoints in a map like cache.
 *
 * @param <K> endpoint key
 */
public interface EndpointRegistry<K> extends Map<K, Endpoint>, StaticService {

    /**
     * Number of static endpoints in the registry.
     */
    int staticSize();

    /**
     * Number of dynamic endpoints in the registry
     */
    int dynamicSize();

    /**
     * Maximum number of entries to store in the dynamic cache
     */
    public int getMaximumCacheSize();

    /**
     * Purges the cache (removes dynamic endpoints)
     */
    void purge();

}
