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
    private JCacheConfiguration configuration;
    private CachingProvider provider;
    private CacheManager manager;
    private ClassLoader classLoader;
    private Cache<K, V> cache;
    private String cacheName;
    private CamelContext camelContext;

    public JCacheManager(JCacheConfiguration configuration, String cacheName) {
        this(configuration, cacheName, null, null);
    }

    public JCacheManager(JCacheConfiguration configuration, String cacheName, CamelContext camelContext) {
        this(configuration, cacheName, null, camelContext);
    }

    public JCacheManager(JCacheConfiguration configuration, String cacheName, ClassLoader classLoader) {
        this(configuration, cacheName, classLoader, null);
    }

    public JCacheManager(JCacheConfiguration configuration, String cacheName, ClassLoader classLoader, CamelContext camelContext) {
        this.configuration = configuration;
        this.provider = null;
        this.manager = null;
        this.classLoader = classLoader;
        this.cache = null;
        this.cacheName = cacheName;
        this.camelContext = camelContext;
    }

    public JCacheManager(Cache<K, V> cache) {
        this.cache = cache;
        this.configuration = null;
        this.cacheName = cache.getName();
        this.provider = null;
        this.manager = null;
        this.camelContext = null;
    }

    public String getCacheName() {
        return this.cacheName;
    }

    public JCacheConfiguration getConfiguration() {
        return this.configuration;
    }

    public synchronized Cache<K, V> getCache() throws Exception {
        if (cache == null) {
            String uri = configuration.getConfigurationUri();
            if (uri != null && camelContext != null) {
                uri = camelContext.resolvePropertyPlaceholders(uri);
            }

            provider = configuration.getCachingProvider() != null
                ? Caching.getCachingProvider(configuration.getCachingProvider())
                : Caching.getCachingProvider();

            manager = provider.getCacheManager(
                ObjectHelper.isNotEmpty(uri) ? URI.create(uri) : null,
                classLoader,
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


    Configuration getOrCreateCacheConfiguration() {
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

        return mutableConfiguration;
    }

    CacheEntryEventFilter getEventFilter() {
        if (configuration.getEventFilters() != null) {
            return new JCacheEntryEventFilters.Chained(configuration.getEventFilters());
        }

        return new JCacheEntryEventFilters.Named(configuration.getFilteredEvents());
    }
}
