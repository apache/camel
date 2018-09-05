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
package org.apache.camel.component.jcache;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.spi.CachingProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

public class JCacheManager<K, V> implements Closeable {
    private final JCacheConfiguration configuration;
    private final ClassLoader classLoader;
    private final String cacheName;
    private final CamelContext camelContext;
    private CachingProvider provider;
    private CacheManager manager;
    private Cache<K, V> cache;

    public JCacheManager(JCacheConfiguration configuration) {
        this.configuration = configuration;
        this.camelContext = configuration.getCamelContext();
        this.classLoader = camelContext != null ? camelContext.getApplicationContextClassLoader() : null;
        this.cacheName = configuration.getCacheName();
        this.provider = null;
        this.manager = null;
        this.cache = null;
    }

    public JCacheManager(Cache<K, V> cache) {
        this.configuration = null;
        this.camelContext = null;
        this.classLoader = null;
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

    public synchronized Cache<K, V> getCache() throws Exception {
        if (cache == null) {
            JCacheProvider provider = JCacheProviders.lookup(configuration.getCachingProvider());
            cache = doGetCache(provider);
        }

        return cache;
    }

    @Override
    public synchronized void close() throws IOException {
        if (configuration != null) {
            if (cache != null) {
                cache.close();
            }

            if (manager != null) {
                manager.close();
            }

            if (provider != null) {
                provider.close();
            }
        }
    }

    protected CacheEntryEventFilter getEventFilter() {
        if (configuration.getEventFilters() != null) {
            return new JCacheEntryEventFilters.Chained(configuration.getEventFilters());
        }

        return new JCacheEntryEventFilters.Named(configuration.getFilteredEvents());
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
