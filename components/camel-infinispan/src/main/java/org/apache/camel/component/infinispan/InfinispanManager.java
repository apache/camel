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


import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
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
            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.classLoader(Thread.currentThread().getContextClassLoader());

            Properties properties = new Properties();

            String uri = configuration.getConfigurationUri();
            if (uri != null && camelContext != null) {
                uri = camelContext.resolvePropertyPlaceholders(uri);
            }

            if (uri != null) {
                properties.putAll(InfinispanUtil.loadProperties(camelContext, uri));
            }
            if (configuration.getConfigurationProperties() != null) {
                properties.putAll(configuration.getConfigurationProperties());
            }

            if (!properties.isEmpty()) {
                builder.withProperties(properties);
            }

            if (configuration.getHost() != null) {
                builder.addServers(configuration.getHost());
            }



            cacheContainer = new RemoteCacheManager(builder.build(), true);
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

    public <K, V > BasicCache<K, V> getCache() {
        return getCache(configuration.getCacheName());
    }

    public <K, V > BasicCache<K, V> getCache(Exchange exchange) {
        return getCache(exchange.getIn().getHeader(InfinispanConstants.CACHE_NAME, String.class));
    }

    public <K, V > BasicCache<K, V> getCache(String cacheName) {
        if (cacheName == null) {
            cacheName = configuration.getCacheName();
        }

        LOGGER.trace("Cache[{}]", cacheName);

        BasicCache<K, V> cache = InfinispanUtil.getCache(cacheContainer, cacheName);
        if (configuration.hasFlags() && InfinispanUtil.isEmbedded(cache)) {
            cache = new DecoratedCache(InfinispanUtil.asAdvanced(cache), configuration.getFlags());
        }

        return cache;
    }
}
