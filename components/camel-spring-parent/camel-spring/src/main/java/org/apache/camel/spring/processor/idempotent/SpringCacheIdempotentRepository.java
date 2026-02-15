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
package org.apache.camel.spring.processor.idempotent;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@Metadata(label = "bean",
        description = "SpringCache based message id repository.",
        annotations = { "interfaceName=org.apache.camel.spi.IdempotentRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "SpringCache based message id repository")
public class SpringCacheIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    @Metadata(required = true, description = "The Spring Cache Manager")
    private CacheManager manager;
    @Metadata(required = true, description = "The name of the cache to use")
    private String cacheName;
    private Cache cache;

    public SpringCacheIdempotentRepository(CacheManager manager, String cacheName) {
        this.manager = manager;
        this.cacheName = cacheName;
        this.cache = null;
    }

    public SpringCacheIdempotentRepository() {
    }

    public CacheManager getManager() {
        return manager;
    }

    public void setManager(CacheManager manager) {
        this.manager = manager;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        return cache.putIfAbsent(key, true) == null;
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return cache.get(key) != null;
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        cache.evict(key);
        return true;
    }

    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        cache.clear();
    }

    @ManagedAttribute(description = "The processor name")
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        if (cache == null) {
            cache = manager.getCache(cacheName);
        }
    }

    @Override
    protected void doStop() throws Exception {
        cache = null;
    }
}
