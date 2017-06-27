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
package org.apache.camel.component.infinispan;

import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Service;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.cache.impl.DecoratedCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanManager implements Service {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanManager.class);

    private final InfinispanConfiguration configuration;
    private final CamelContext camelContext;
    private BasicCacheContainer cacheContainer;
    private boolean isManagedCacheContainer;

    public InfinispanManager() {
        this.camelContext = null;
        this.configuration = new InfinispanConfiguration();
        this.configuration.setCacheContainer(new DefaultCacheManager(true));
    }

    public InfinispanManager(InfinispanConfiguration configuration) {
        this(null, configuration);
    }

    public InfinispanManager(CamelContext camelContext, InfinispanConfiguration configuration) {
        this.camelContext = camelContext;
        this.configuration = configuration;
    }

    @Override
    public void start() throws Exception {
        cacheContainer = configuration.getCacheContainer();

        if (cacheContainer == null) {
            // Check if a container configuration object has been provided so use
            // it and discard any other additional configuration.
            if (configuration.getCacheContainerConfiguration() != null) {
                final Object containerConf = configuration.getCacheContainerConfiguration();
                if (containerConf instanceof org.infinispan.client.hotrod.configuration.Configuration) {
                    cacheContainer = new RemoteCacheManager(
                        (org.infinispan.client.hotrod.configuration.Configuration)containerConf,
                        true
                    );
                } else if (containerConf instanceof org.infinispan.configuration.cache.Configuration) {
                    cacheContainer = new DefaultCacheManager(
                        (org.infinispan.configuration.cache.Configuration)containerConf,
                        true
                    );
                } else {
                    throw new IllegalArgumentException("Unsupported CacheContainer Configuration type: " + containerConf.getClass());
                }
            }

            // If the hosts properties has been configured, it means we want to
            // connect to a remote cache so set-up a RemoteCacheManager
            if (cacheContainer == null && configuration.getHosts() != null) {
                ConfigurationBuilder builder = new ConfigurationBuilder();
                builder.addServers(configuration.getHosts());

                if (camelContext != null && camelContext.getApplicationContextClassLoader() != null) {
                    builder.classLoader(camelContext.getApplicationContextClassLoader());
                } else {
                    builder.classLoader(Thread.currentThread().getContextClassLoader());
                }

                Properties properties = new Properties();

                // Properties can be set either via a properties file or via
                // properties on configuration, if you set both they are merged
                // with properties defined on configuration overriding those from
                // file.
                if (ObjectHelper.isNotEmpty(configuration.getConfigurationUri())) {
                    properties.putAll(InfinispanUtil.loadProperties(camelContext, configuration.getConfigurationUri()));
                }
                if (ObjectHelper.isNotEmpty(configuration.getConfigurationProperties())) {
                    properties.putAll(configuration.getConfigurationProperties());
                }
                if (!properties.isEmpty()) {
                    builder.withProperties(properties);
                }

                cacheContainer = new RemoteCacheManager(builder.build(), true);
            }

            // Finally we can set-up a DefaultCacheManager if none of the methods
            // above was triggered. You can configure the cache using a configuration
            // file.
            if (cacheContainer == null) {
                if (ObjectHelper.isNotEmpty(configuration.getConfigurationUri())) {
                    try (InputStream is = InfinispanUtil.openInputStream(camelContext, configuration.getConfigurationUri())) {
                        cacheContainer = new DefaultCacheManager(is, true);
                    }
                } else {
                    cacheContainer = new DefaultCacheManager(new org.infinispan.configuration.cache.ConfigurationBuilder().build());
                }
            }

            isManagedCacheContainer = true;
        }
    }

    @Override
    public void stop() throws Exception {
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }
    }

    public BasicCacheContainer getCacheContainer() {
        return cacheContainer;
    }

    public boolean isCacheContainerEmbedded() {
        return InfinispanUtil.isEmbedded(cacheContainer);
    }

    public boolean isCacheContainerRemote() {
        return InfinispanUtil.isRemote(cacheContainer);
    }

    public <K, V> BasicCache<K, V> getCache(String cacheName) {
        BasicCache<K, V> cache;
        if (ObjectHelper.isEmpty(cacheName)) {
            cache = cacheContainer.getCache();
            cacheName = cache.getName();
        } else {
            cache = cacheContainer.getCache(cacheName);
        }

        LOGGER.trace("Cache[{}]", cacheName);

        if (configuration.hasFlags() && InfinispanUtil.isEmbedded(cache)) {
            cache = new DecoratedCache(InfinispanUtil.asAdvanced(cache), configuration.getFlags());
        }

        return cache;
    }

    public <K, V> BasicCache<K, V> getCache(String cacheName, boolean forceReturnValue) {
        if (isCacheContainerRemote()) {
            BasicCache<K, V> cache;
            if (ObjectHelper.isEmpty(cacheName)) {
                cache = InfinispanUtil.asRemote(cacheContainer).getCache(forceReturnValue);
                cacheName = cache.getName();
            } else {
                cache = InfinispanUtil.asRemote(cacheContainer).getCache(cacheName, forceReturnValue);
            }

            LOGGER.trace("Cache[{}]", cacheName);

            return cache;
        } else {
            return getCache(cacheName);
        }
    }

    public <K, V> BasicCache<K, V> getCache(Exchange exchange, String defaultCache) {
        return getCache(exchange.getIn(), defaultCache);
    }

    public <K, V> BasicCache<K, V> getCache(Message message, String defaultCache) {
        BasicCache<K, V> cache = getCache(message.getHeader(InfinispanConstants.CACHE_NAME, defaultCache, String.class));

        return message.getHeader(InfinispanConstants.IGNORE_RETURN_VALUES) != null
            ? cache
            : InfinispanUtil.ignoreReturnValuesCache(cache);
    }
}
