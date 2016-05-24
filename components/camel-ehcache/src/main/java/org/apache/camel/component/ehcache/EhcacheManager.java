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

import org.apache.camel.Service;
import org.apache.camel.util.ObjectHelper;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhcacheManager implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(EhcacheManager.class);

    private final EhcacheConfiguration configuration;
    private final CacheManager cacheManager;
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
        this.managed = managed;
        this.configuration = configuration;

        ObjectHelper.notNull(cacheManager, "cacheManager");
    }

    @Override
    public void start() throws Exception {
        if (managed) {
            cacheManager.init();
        }
    }

    @Override
    public void stop() throws Exception {
        if (managed) {
            cacheManager.close();
        }
    }

    public <K, V> Cache<K, V> getCache(String name, Class<K> keyType, Class<V> valueType) throws Exception {
        Cache<K, V> cache = cacheManager.getCache(name, keyType, valueType);
        if (cache == null && configuration != null && configuration.isCreateCacheIfNotExist()) {
            cache = cacheManager.createCache(name, configuration.getMandatoryConfiguration());
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
