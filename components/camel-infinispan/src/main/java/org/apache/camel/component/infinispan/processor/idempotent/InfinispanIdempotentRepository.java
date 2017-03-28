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
package org.apache.camel.component.infinispan.processor.idempotent;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.manager.DefaultCacheManager;

@ManagedResource(description = "Infinispan based message id repository")
public class InfinispanIdempotentRepository extends ServiceSupport implements IdempotentRepository<Object> {
    private final String cacheName;
    private final BasicCacheContainer cacheContainer;
    private final boolean isManagedCacheContainer;
    private BasicCache<Object, Boolean> cache;

    public InfinispanIdempotentRepository(BasicCacheContainer cacheContainer, String cacheName) {
        this.cacheContainer = cacheContainer;
        this.cacheName = cacheName;
        this.isManagedCacheContainer = false;
    }

    public InfinispanIdempotentRepository(String cacheName) {
        this.cacheContainer = new DefaultCacheManager();
        this.cacheName = cacheName;
        this.isManagedCacheContainer = true;
    }

    public InfinispanIdempotentRepository() {
        this(null);
    }

    public static InfinispanIdempotentRepository infinispanIdempotentRepository(
            BasicCacheContainer cacheContainer, String processorName) {
        return new InfinispanIdempotentRepository(cacheContainer, processorName);
    }

    public static InfinispanIdempotentRepository infinispanIdempotentRepository(String processorName) {
        return new InfinispanIdempotentRepository(processorName);
    }

    public static InfinispanIdempotentRepository infinispanIdempotentRepository() {
        return new InfinispanIdempotentRepository();
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(Object key) {
        Boolean put = getCache().put(key, true);
        return put == null;
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(Object key) {
        return getCache().containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(Object key) {
        return getCache().remove(key) != null;
    }
    
    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        getCache().clear();      
    }

    @ManagedAttribute(description = "The processor name")
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public boolean confirm(Object key) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }

        super.doShutdown();
    }

    private BasicCache<Object, Boolean> getCache() {
        if (cache == null) {
            // By default, previously existing values for java.util.Map operations
            // are not returned for remote caches but idempotent repository needs
            // them so force it.
            if (InfinispanUtil.isRemote(cacheContainer)) {
                RemoteCacheManager manager = InfinispanUtil.asRemote(cacheContainer);
                cache = cacheName != null
                    ? manager.getCache(cacheName, true)
                    : manager.getCache(true);
            } else {
                cache = cacheName != null
                    ? cacheContainer.getCache(cacheName)
                    : cacheContainer.getCache();
            }
        }

        return cache;
    }
}

