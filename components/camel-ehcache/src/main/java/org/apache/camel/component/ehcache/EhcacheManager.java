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
package org.apache.camel.component.ehcache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReferenceCount;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.spi.service.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhcacheManager extends ServiceSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(EhcacheManager.class);

    private final EhcacheConfiguration configuration;
    private final CacheManager cacheManager;
    private final ReferenceCount refCount;
    // the reference count changes together with the start and stop of the manager, so that the last two users that
    // stop at the same time do not both (or neither) see the count reach 0, and a user that starts while the last one
    // closes the cache manager waits for the close (then opens it again) rather than taking the closing one
    private final Lock refLock = new ReentrantLock();

    public EhcacheManager(CacheManager cacheManager, boolean managed, EhcacheConfiguration configuration) {
        this.cacheManager = ObjectHelper.notNull(cacheManager, "cacheManager");
        this.configuration = configuration;
        this.refCount = ReferenceCount.on(
                managed ? cacheManager::init : () -> {
                },
                () -> {
                    if (managed) {
                        cacheManager.close();
                    }
                });
    }

    /**
     * Takes a reference on the manager, which starts it when nothing used it.
     */
    protected void incRef() {
        refLock.lock();
        try {
            if (refCount.get() == 0) {
                ServiceHelper.startService(this);
            }
            refCount.retain();
        } finally {
            refLock.unlock();
        }
    }

    /**
     * Releases a reference on the manager: the user that releases the last one closes the caches and stops the manager.
     *
     * @return the reference count after the release
     */
    protected long decRef() {
        refLock.lock();
        try {
            refCount.release();
            long count = refCount.get();
            if (count == 0) {
                ServiceHelper.stopService(this);
            }
            return count;
        } finally {
            refLock.unlock();
        }
    }

    /**
     * Shuts the manager down, when nothing uses it.
     */
    void shutdownIfUnused() {
        refLock.lock();
        try {
            if (refCount.get() == 0) {
                ServiceHelper.stopAndShutdownService(this);
            }
        } finally {
            refLock.unlock();
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        // nothing to do: managed caches are closed by the CacheManager
    }

    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name, Class<K> keyType, Class<V> valueType) throws Exception {
        return getCache(name, keyType, valueType, configuration);
    }

    @SuppressWarnings("unchecked")
    <K, V> Cache<K, V> getCache(String name, Class<K> keyType, Class<V> valueType, EhcacheConfiguration endpointConfiguration)
            throws Exception {
        EhcacheConfiguration effectiveConfig = endpointConfiguration != null ? endpointConfiguration : configuration;
        CacheConfiguration<K, V> cacheConfiguration = null;
        if (effectiveConfig != null) {
            if (effectiveConfig.hasConfiguration(name)) {
                LOGGER.debug("Using custom cache configuration for cache {}", name);
                cacheConfiguration = CacheConfiguration.class.cast(effectiveConfig.getConfigurations().get(name));
            } else if (effectiveConfig.hasConfiguration()) {
                LOGGER.debug("Using global cache configuration for cache {}", name);
                cacheConfiguration = CacheConfiguration.class.cast(effectiveConfig.getConfiguration());
            }
        }
        if (keyType == null) {
            keyType = cacheConfiguration != null ? cacheConfiguration.getKeyType() : (Class<K>) Object.class;
        }
        if (valueType == null) {
            valueType = cacheConfiguration != null ? cacheConfiguration.getValueType() : (Class<V>) Object.class;
        }
        Cache<K, V> cache = cacheManager.getCache(name, keyType, valueType);
        if (cache == null && effectiveConfig != null && effectiveConfig.isCreateCacheIfNotExist()) {
            if (cacheConfiguration != null) {
                if (keyType != cacheConfiguration.getKeyType() || valueType != cacheConfiguration.getValueType()) {
                    LOGGER.info("Mismatch keyType / valueType configuration for cache {}", name);
                    CacheConfigurationBuilder builder = CacheConfigurationBuilder
                            .newCacheConfigurationBuilder(keyType, valueType, cacheConfiguration.getResourcePools())
                            .withClassLoader(cacheConfiguration.getClassLoader())
                            .withEvictionAdvisor(cacheConfiguration.getEvictionAdvisor())
                            .withExpiry(cacheConfiguration.getExpiryPolicy());
                    for (ServiceConfiguration<?, ?> serviceConfig : cacheConfiguration.getServiceConfigurations()) {
                        builder = builder.withService(serviceConfig);
                    }
                    cacheConfiguration = builder.build();
                }
                cache = cacheManager.createCache(name, cacheConfiguration);
            } else {
                // If a cache configuration is not provided, create a managed cache with default configuration
                // so that event listeners work correctly (UserManagedCache lacks event executor services)
                LOGGER.debug("Creating a managed cache for {} with default configuration as no configuration has been found",
                        name);
                cache = cacheManager.createCache(name,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                keyType, valueType, ResourcePoolsBuilder.heap(100)).build());
            }
        }

        if (cache == null) {
            throw new RuntimeCamelException("Unable to retrieve the cache " + name + " from cache manager " + cacheManager);
        }

        return cache;
    }

    CacheManager getCacheManager() {
        return this.cacheManager;
    }

    // read only: incRef and decRef change the count, under the lock
    ReferenceCount getReferenceCount() {
        return refCount;
    }
}
