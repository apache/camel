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
package org.apache.camel.component.infinispan.embedded;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanManager;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.context.Flag;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import static org.apache.camel.component.infinispan.InfinispanConstants.CACHE_MANAGER_CURRENT;

public class InfinispanEmbeddedManager extends ServiceSupport implements InfinispanManager<EmbeddedCacheManager> {
    private final InfinispanEmbeddedConfiguration configuration;
    private CamelContext camelContext;
    private EmbeddedCacheManager cacheContainer;
    private boolean isManagedCacheContainer;

    public InfinispanEmbeddedManager() {
        this(null, new InfinispanEmbeddedConfiguration());
    }

    public InfinispanEmbeddedManager(InfinispanEmbeddedConfiguration configuration) {
        this(null, configuration);
    }

    public InfinispanEmbeddedManager(CamelContext camelContext, InfinispanEmbeddedConfiguration configuration) {
        this.camelContext = camelContext;
        this.configuration = configuration;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void doStart() throws Exception {
        cacheContainer = configuration.getCacheContainer();

        if (cacheContainer == null) {
            final Configuration containerConf = configuration.getCacheContainerConfiguration();
            // Check if a container configuration object has been provided so use
            // it and discard any other additional configuration.
            if (containerConf != null) {
                cacheContainer = new DefaultCacheManager(
                        new GlobalConfigurationBuilder().defaultCacheName("default").build(),
                        containerConf,
                        true);
            } else {
                if (ObjectHelper.isNotEmpty(configuration.getConfigurationUri())) {
                    cacheContainer = new DefaultCacheManager(
                            InfinispanUtil.openInputStream(camelContext, configuration.getConfigurationUri()),
                            true);
                } else {
                    cacheContainer = new DefaultCacheManager(
                            new GlobalConfigurationBuilder().defaultCacheName("default").build(),
                            new ConfigurationBuilder().build());
                }
            }

            isManagedCacheContainer = true;
        }
    }

    @Override
    public void doStop() throws Exception {
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }
        super.doStop();
    }

    @Override
    public EmbeddedCacheManager getCacheContainer() {
        return cacheContainer;
    }

    @Override
    public <K, V> BasicCache<K, V> getCache() {
        Cache<K, V> cache = cacheContainer.getCache();

        return configuration.hasFlags()
                ? cache.getAdvancedCache().withFlags(configuration.getFlags())
                : cache;
    }

    @Override
    public <K, V> BasicCache<K, V> getCache(String cacheName) {
        Cache<K, V> cache;
        if (ObjectHelper.isEmpty(cacheName) || CACHE_MANAGER_CURRENT.equals(cacheName)) {
            cache = cacheContainer.getCache();
        } else {
            cache = cacheContainer.getCache(cacheName);
        }

        return configuration.hasFlags()
                ? cache.getAdvancedCache().withFlags(configuration.getFlags())
                : cache;
    }

    @Override
    public <K, V> BasicCache<K, V> getCache(Message message, String defaultCache) {
        final String cacheName = message.getHeader(InfinispanConstants.CACHE_NAME, defaultCache, String.class);
        final Cache<K, V> cache = (Cache<K, V>) getCache(cacheName);

        return message.getHeader(InfinispanConstants.IGNORE_RETURN_VALUES, false, boolean.class)
                ? cache.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES)
                : cache;
    }

    @Override
    public Set<String> getCacheNames() {
        return cacheContainer.getCacheNames();
    }
}
