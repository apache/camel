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
package org.apache.camel.component.ehcache.processor.idempotent;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.ehcache.EhcacheManager;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.ehcache.Cache;
import org.ehcache.CacheManager;

@ManagedResource(description = "Ehcache based message id repository")
public class EhcacheIdempotentRepository extends ServiceSupport implements IdempotentRepository<String> {

    private String cacheName;
    private Cache<String, Boolean> cache;
    private EhcacheManager cacheManager;

    public EhcacheIdempotentRepository(CacheManager cacheManager) {
        this(cacheManager, EhcacheIdempotentRepository.class.getSimpleName());
    }

    public EhcacheIdempotentRepository(CacheManager cacheManager, String repositoryName) {
        this.cacheName = repositoryName;
        this.cacheManager = new EhcacheManager(cacheManager, false, null);
    }

    @ManagedAttribute(description = "The processor name")
    public String getCacheName() {
        return cacheName;
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        return cache.putIfAbsent(key, false) == null;
    }

    @Override
    public boolean confirm(String key) {
        return cache.replace(key, false, true);
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return this.cache.containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        cache.remove(key);
        return true;
    }

    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        cache.clear();
    }

    @Override
    protected void doStart() throws Exception {
        cacheManager.start();
        cache = cacheManager.getCache(cacheName, String.class, Boolean.class);
    }

    @Override
    protected void doStop() throws Exception {
        cacheManager.stop();
    }
}