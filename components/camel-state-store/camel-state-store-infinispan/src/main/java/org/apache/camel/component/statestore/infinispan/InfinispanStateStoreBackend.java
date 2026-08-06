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
package org.apache.camel.component.statestore.infinispan;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.statestore.StateStoreBackend;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link StateStoreBackend} implementation backed by Infinispan remote (Hot Rod). Supports per-entry TTL via
 * Infinispan's lifespan parameter.
 */
public class InfinispanStateStoreBackend implements StateStoreBackend {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanStateStoreBackend.class);

    private RemoteCacheManager cacheManager;
    private RemoteCache<String, Object> cache;
    private String hosts = "localhost:11222";
    private String cacheName = "camel-state-store";
    private boolean managedCacheManager;

    @Override
    public Object put(String key, Object value, long ttlMillis) {
        if (ttlMillis > 0) {
            return cache.put(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
        return cache.put(key, value);
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        if (ttlMillis > 0) {
            return cache.putIfAbsent(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
        return cache.putIfAbsent(key, value);
    }

    @Override
    public Object get(String key) {
        return cache.get(key);
    }

    @Override
    public Object delete(String key) {
        return cache.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return Set.copyOf(cache.keySet());
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void start() {
        if (cache != null) {
            return;
        }
        if (cacheManager == null) {
            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.addServers(hosts);
            builder.forceReturnValues(true);
            cacheManager = new RemoteCacheManager(builder.build(), true);
            managedCacheManager = true;
        }
        // Retry cache creation to handle server startup race conditions
        Exception lastException = null;
        for (int i = 0; i < 10; i++) {
            try {
                cache = cacheManager.administration().getOrCreateCache(cacheName, (String) null);
                return;
            } catch (Exception e) {
                lastException = e;
                LOG.warn("Failed to access cache '{}' (attempt {}/10): {}", cacheName, i + 1, e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for cache to be ready", ie);
                }
            }
        }
        throw new RuntimeException("Failed to create or access cache '" + cacheName + "' after retries", lastException);
    }

    @Override
    public void stop() {
        cache = null;
        if (managedCacheManager && cacheManager != null) {
            cacheManager.stop();
            cacheManager = null;
        }
    }

    public RemoteCacheManager getCacheManager() {
        return cacheManager;
    }

    public void setCacheManager(RemoteCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }
}
