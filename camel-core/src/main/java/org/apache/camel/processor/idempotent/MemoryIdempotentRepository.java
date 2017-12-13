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
package org.apache.camel.processor.idempotent;

import java.util.Map;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;

/**
 * A memory based implementation of {@link org.apache.camel.spi.IdempotentRepository}. 
 * <p/>
 * Care should be taken to use a suitable underlying {@link Map} to avoid this class being a
 * memory leak.
 *
 * @version 
 */
@ManagedResource(description = "Memory based idempotent repository")
public class MemoryIdempotentRepository extends MapIdempotentRepository {

    private int cacheSize;

    @SuppressWarnings("unchecked")
    public MemoryIdempotentRepository() {
        super(LRUCacheFactory.newLRUCache(1000));
    }

    public MemoryIdempotentRepository(Map<String, Object> cache) {
        super(cache);
    }

    /**
     * Creates a new memory based repository using a {@link LRUCache}
     * with a default of 1000 entries in the cache.
     */
    public static IdempotentRepository<String> memoryIdempotentRepository() {
        return new MemoryIdempotentRepository();
    }

    /**
     * Creates a new memory based repository using a {@link LRUCache}.
     *
     * @param cacheSize  the cache size
     */
    @SuppressWarnings("unchecked")
    public static IdempotentRepository<String> memoryIdempotentRepository(int cacheSize) {
        return new MemoryIdempotentRepository(LRUCacheFactory.newLRUCache(cacheSize));
    }

    /**
     * Creates a new memory based repository using the given {@link Map} to
     * use to store the processed message ids.
     * <p/>
     * Care should be taken to use a suitable underlying {@link Map} to avoid this class being a
     * memory leak.
     *
     * @param cache  the cache
     * @deprecated Prefer {@link MapIdempotentRepository#mapIdempotentRepository(Map)}
     */
    @Deprecated
    public static IdempotentRepository<String> memoryIdempotentRepository(Map<String, Object> cache) {
        return new MemoryIdempotentRepository(cache);
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        if (cacheSize > 0) {
            cache = LRUCacheFactory.newLRUCache(cacheSize);
        }
    }

    @Override
    protected void doStop() throws Exception {
        cache.clear();
    }

}
