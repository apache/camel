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
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanConsumer extends DefaultConsumer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanProducer.class);
    private final InfinispanConfiguration configuration;
    private final InfinispanSyncEventListener listener;
    private EmbeddedCacheManager cacheManager;

    public InfinispanConsumer(InfinispanEndpoint endpoint, Processor processor, InfinispanConfiguration configuration) {
        super(endpoint, processor);
        this.configuration = configuration;
        if (configuration.isSync()) {
            listener = new InfinispanSyncEventListener(this, configuration.getEventTypes());
        } else {
            listener = new InfinispanAsyncEventListener(this, configuration.getEventTypes());
        }
    }

    public void processEvent(String eventType, boolean isPre, String cacheName, Object key) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getOut().setHeader(InfinispanConstants.EVENT_TYPE, eventType);
        exchange.getOut().setHeader(InfinispanConstants.IS_PRE, isPre);
        exchange.getOut().setHeader(InfinispanConstants.CACHE_NAME, cacheName);
        exchange.getOut().setHeader(InfinispanConstants.KEY, key);

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOGGER.error("Error processing event ", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getCacheContainer() instanceof EmbeddedCacheManager) {
            cacheManager = (EmbeddedCacheManager) configuration.getCacheContainer();
            Cache<Object, Object> cache;
            if (configuration.getCacheName() != null) {
                cache = cacheManager.getCache(configuration.getCacheName());
            } else {
                cache = cacheManager.getCache();
            }
            cache.addListener(listener);
        } else {
            throw new UnsupportedOperationException("Consumer not support for CacheContainer: " + configuration.getCacheContainer());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (cacheManager != null) {
            cacheManager.removeListener(listener);
        }
        super.doStop();
    }
}
