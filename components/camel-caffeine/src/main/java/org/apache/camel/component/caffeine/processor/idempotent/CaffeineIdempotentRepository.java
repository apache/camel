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
package org.apache.camel.component.caffeine.processor.idempotent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;

@ManagedResource(description = "Caffeine based message id repository")
public class CaffeineIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private String cacheName;
    private Cache<String, Boolean> cache;

    public CaffeineIdempotentRepository() {
        this(CaffeineIdempotentRepository.class.getSimpleName());
    }

    public CaffeineIdempotentRepository(String repositoryName) {
        this.cacheName = repositoryName;
    }

    @ManagedAttribute(description = "The processor name")
    public String getCacheName() {
        return cacheName;
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        if (cache.asMap().containsKey(key)) {
            return false;
        } else {
            cache.put(key, true);
            return true;
        }
    }

    @Override
    public boolean confirm(String key) {
        return cache.asMap().containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return cache.asMap().containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        cache.invalidate(key);
        return true;
    }

    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    protected void doStart() throws Exception {
        if (cache == null) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder();
            cache = builder.build();
        }
    }
    
    protected Cache getCache() {
        return this.cache;
    }

    @Override
    protected void doStop() throws Exception {
    }
}
