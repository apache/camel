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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanProducer extends DefaultProducer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanProducer.class);
    private final InfinispanConfiguration configuration;
    private BasicCacheContainer cacheContainer;
    private boolean isManagedCacheContainer;

    public InfinispanProducer(InfinispanEndpoint endpoint, InfinispanConfiguration configuration) {
        super(endpoint);
        this.configuration = configuration;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        new InfinispanOperation(getCache(exchange), configuration).process(exchange);
    }

    @Override
    protected void doStart() throws Exception {
        cacheContainer = configuration.getCacheContainer();
        if (cacheContainer == null) {
            Configuration config = new ConfigurationBuilder().classLoader(Thread.currentThread().getContextClassLoader()).addServers(configuration.getHost()).build();
            cacheContainer = new RemoteCacheManager(config, true);
            isManagedCacheContainer = true;
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }
        super.doStop();
    }

    private BasicCache<Object, Object> getCache(Exchange exchange) {
        String cacheName = exchange.getIn().getHeader(InfinispanConstants.CACHE_NAME, String.class);
        if (cacheName == null) {
            cacheName = configuration.getCacheName();
        }
        LOGGER.trace("Cache[{}]", cacheName);
        return cacheName != null ? cacheContainer.getCache(cacheName) : cacheContainer.getCache();
    }
}
