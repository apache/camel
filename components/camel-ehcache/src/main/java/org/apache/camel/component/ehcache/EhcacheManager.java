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
package org.apache.camel.component.ehcache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.Service;
import org.apache.camel.util.ObjectHelper;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.UserManagedCache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.UserManagedCacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhcacheManager implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(EhcacheManager.class);

    private final EhcacheConfiguration configuration;
    private final CacheManager cacheManager;
    private final ConcurrentMap<String, UserManagedCache<?, ?>> userCaches;

    private final boolean managed;

    public EhcacheManager(EhcacheConfiguration configuration) throws IOException {
        this(configuration.createCacheManager(), !configuration.hasCacheManager(), configuration);
    }

    public EhcacheManager(CacheManager cacheManager) {
        this(cacheManager, false, null);
    }

    public EhcacheManager(CacheManager cacheManager, boolean managed) {
        this(cacheManager, managed, null);
    }

    private EhcacheManager(CacheManager cacheManager, boolean managed, EhcacheConfiguration configuration) {
        this.cacheManager = cacheManager;
        this.userCaches = new ConcurrentHashMap<>();
        this.managed = managed;
        this.configuration = configuration;

        ObjectHelper.notNull(cacheManager, "cacheManager");
    }

    @Override
    public synchronized void start() throws Exception {
        if (managed) {
            cacheManager.init();
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (managed) {
            cacheManager.close();
        }

        // Clean up any User managed cache
        userCaches.values().forEach(UserManagedCache::close);
    }

    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name, Class<K> keyType, Class<V> valueType) throws Exception {
        Cache<K, V> cache = cacheManager.getCache(name, keyType, valueType);
        if (cache == null && configuration != null && configuration.isCreateCacheIfNotExist()) {
            CacheConfiguration<K, V> cacheConfiguration = configuration.getConfiguration();

            if (cacheConfiguration != null) {
                cache = cacheManager.createCache(name, cacheConfiguration);
            } else {
                // If a cache configuration is not provided, create a User Managed
                // Cache
                cache = (Cache<K, V>)userCaches.computeIfAbsent(
                    name,
                    key -> UserManagedCacheBuilder.newUserManagedCacheBuilder(keyType, valueType).build(true)
                );
            }
        }

        return cache;
    }

    public Cache<?, ?> getCache(String name) throws Exception {
        return getCache(
            name,
            configuration.getKeyType(),
            configuration.getValueType());
    }

    public Cache<?, ?> getCache() throws Exception  {
        ObjectHelper.notNull(configuration, "Ehcache configuration");

        return getCache(
            configuration.getCacheName(),
            configuration.getKeyType(),
            configuration.getValueType());
    }
}
