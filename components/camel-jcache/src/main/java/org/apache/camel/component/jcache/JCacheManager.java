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
package org.apache.camel.component.jcache;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.EventType;
import javax.cache.spi.CachingProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

public class JCacheManager<K, V> implements Closeable {
    // The caching provider is a JVM singleton, and it returns the same cache manager for the same uri, which returns
    // the same cache for the same name: other managers (the other endpoints of the cache, other caches, repositories,
    // other CamelContexts) use them too, so each one is closed by the last manager that uses it
    private static final Map<Object, Integer> USERS = new HashMap<>();
    private static final Lock USERS_LOCK = new ReentrantLock();

    private final JCacheConfiguration configuration;
    private final String cacheName;
    private final CamelContext camelContext;
    private final Lock lock = new ReentrantLock();
    // what this manager counted in USERS, so that close() releases only its own uses
    private final List<Object> retained = new ArrayList<>(3);
    private CachingProvider provider;
    private CacheManager manager;
    private Cache<K, V> cache;
    // set by close(): the cache is not opened again, as nothing would release it (see getCache)
    private boolean closed;

    public JCacheManager(JCacheConfiguration configuration) {
        this.configuration = configuration;
        this.camelContext = configuration.getCamelContext();
        this.cacheName = configuration.getCacheName();
        this.provider = null;
        this.manager = null;
        this.cache = null;
    }

    public JCacheManager(Cache<K, V> cache) {
        this.configuration = null;
        this.camelContext = null;
        this.cacheName = cache.getName();
        this.provider = null;
        this.manager = null;
        this.cache = cache;
    }

    public String getCacheName() {
        return this.cacheName;
    }

    public JCacheConfiguration getConfiguration() {
        return this.configuration;
    }

    public Cache<K, V> getCache() throws Exception {
        lock.lock();
        try {
            if (closed) {
                // such as the producer of a route that uses the endpoint of a removed route with the same uri: its
                // endpoint has stopped, so a cache opened again here would never be released (and with its caching
                // provider closed, it would be a new, empty cache)
                throw new IllegalStateException("The JCache manager of cache " + cacheName + " is closed");
            }
            if (cache == null) {
                JCacheProvider provider = JCacheProviders.lookup(configuration.getCachingProvider());
                cache = doGetCache(provider);
                retain(this.provider);
                retain(this.manager);
                retain(cacheKey());
            }

            return cache;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (configuration != null) {
                if (cache != null && release(cacheKey())) {
                    cache.close();
                }

                if (manager != null && release(manager)) {
                    manager.close();
                }

                if (provider != null && release(provider)) {
                    provider.close();
                }
                cache = null;
                manager = null;
                provider = null;
                closed = true;
            }
        } finally {
            lock.unlock();
        }
    }

    private Object cacheKey() {
        return cache != null ? new CacheKey(manager, cache.getName()) : null;
    }

    private void retain(Object resource) {
        if (resource != null && !retained.contains(resource)) {
            USERS_LOCK.lock();
            try {
                USERS.merge(resource, 1, Integer::sum);
                retained.add(resource);
            } finally {
                USERS_LOCK.unlock();
            }
        }
    }

    /**
     * Whether the resource is no longer used by any manager, so it can be closed.
     */
    private boolean release(Object resource) {
        USERS_LOCK.lock();
        try {
            Integer count = USERS.get(resource);
            if (!retained.remove(resource)) {
                // not counted by this manager, such as the provider and cache manager of a getCache that failed (the
                // cache does not exist and createCacheIfNotExists=false): closed as before, unless others use it
                return count == null;
            }
            if (count == null || count <= 1) {
                USERS.remove(resource);
                return true;
            }
            USERS.put(resource, count - 1);
            return false;
        } finally {
            USERS_LOCK.unlock();
        }
    }

    private record CacheKey(CacheManager manager, String name) {
    }

    protected CacheEntryEventFilter getEventFilter() {
        if (configuration.getEventFilters() != null) {
            return new JCacheEntryEventFilters.Chained(configuration.getEventFilters());
        }

        if (configuration.getFilteredEvents() != null) {
            List<EventType> list = new ArrayList<>();
            for (String s : configuration.getFilteredEvents().split(",")) {
                EventType et = EventType.valueOf(s.trim());
                list.add(et);
            }
            return new JCacheEntryEventFilters.Named(list);
        } else {
            return cacheEntryEvent -> true;
        }
    }

    protected Cache<K, V> doGetCache(JCacheProvider jcacheProvider) throws Exception {
        if (cache == null) {
            String uri = configuration.getConfigurationUri();
            if (uri != null && camelContext != null) {
                uri = camelContext.resolvePropertyPlaceholders(uri);
            }

            provider = ObjectHelper.isNotEmpty(jcacheProvider.className())
                    ? Caching.getCachingProvider(jcacheProvider.className())
                    : Caching.getCachingProvider();

            manager = provider.getCacheManager(
                    ObjectHelper.isNotEmpty(uri) ? URI.create(uri) : null,
                    null,
                    configuration.getCacheConfigurationProperties());

            cache = manager.getCache(cacheName);
            if (cache == null) {
                if (!configuration.isCreateCacheIfNotExists()) {
                    throw new IllegalStateException(
                            "Cache " + cacheName + " does not exist and should not be created (createCacheIfNotExists=false)");
                }

                cache = manager.createCache(
                        cacheName,
                        getOrCreateCacheConfiguration());
            }
        }

        return cache;
    }

    private Configuration getOrCreateCacheConfiguration() {
        if (configuration.getCacheConfiguration() != null) {
            return configuration.getCacheConfiguration();
        }

        MutableConfiguration mutableConfiguration = new MutableConfiguration();
        if (configuration.getCacheLoaderFactory() != null) {
            mutableConfiguration.setCacheLoaderFactory(configuration.getCacheLoaderFactory());
        }
        if (configuration.getCacheWriterFactory() != null) {
            mutableConfiguration.setCacheWriterFactory(configuration.getCacheWriterFactory());
        }
        if (configuration.getExpiryPolicyFactory() != null) {
            mutableConfiguration.setExpiryPolicyFactory(configuration.getExpiryPolicyFactory());
        }

        mutableConfiguration.setManagementEnabled(configuration.isManagementEnabled());
        mutableConfiguration.setStatisticsEnabled(configuration.isStatisticsEnabled());
        mutableConfiguration.setReadThrough(configuration.isReadThrough());
        mutableConfiguration.setStoreByValue(configuration.isStoreByValue());
        mutableConfiguration.setWriteThrough(configuration.isWriteThrough());

        return mutableConfiguration;
    }
}
