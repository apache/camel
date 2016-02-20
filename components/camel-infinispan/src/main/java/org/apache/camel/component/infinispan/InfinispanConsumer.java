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
import org.apache.camel.component.infinispan.embedded.InfinispanConsumerEmbeddedHandler;
import org.apache.camel.component.infinispan.remote.InfinispanConsumerRemoteHandler;
import org.apache.camel.impl.DefaultConsumer;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanConsumer extends DefaultConsumer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanProducer.class);
    private final InfinispanConfiguration configuration;
    private InfinispanEventListener listener;
    private EmbeddedCacheManager cacheManager;
    private BasicCache<Object, Object> cache;

    public InfinispanConsumer(InfinispanEndpoint endpoint, Processor processor, InfinispanConfiguration configuration) {
        super(endpoint, processor);
        this.configuration = configuration;
    }

    public void processEvent(String eventType, boolean isPre, String cacheName, Object key) {
        processEvent(eventType, isPre, cacheName, key, null);
    }

    public void processEvent(String eventType, boolean isPre, String cacheName, Object key, Object eventData) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getOut().setHeader(InfinispanConstants.EVENT_TYPE, eventType);
        exchange.getOut().setHeader(InfinispanConstants.IS_PRE, isPre);
        exchange.getOut().setHeader(InfinispanConstants.CACHE_NAME, cacheName);
        exchange.getOut().setHeader(InfinispanConstants.KEY, key);
        if (eventData != null) {
            exchange.getOut().setHeader(InfinispanConstants.EVENT_DATA, eventData);
        }

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOGGER.error("Error processing event ", e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        BasicCacheContainer cacheContainer = configuration.getCacheContainer();
        String cacheName = configuration.getCacheName();
        cache = cacheName == null ? cacheContainer.getCache() : cacheContainer.getCache(cacheName);
        if (InfinispanUtil.isEmbedded(cacheContainer)) {
            listener = InfinispanConsumerEmbeddedHandler.INSTANCE.start(this);
        } else if (InfinispanUtil.isRemote(cacheContainer)) {
            listener = InfinispanConsumerRemoteHandler.INSTANCE.start(this);
        } else {
            throw new UnsupportedOperationException("Consumer not support for CacheContainer: " + cacheContainer);
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

    public BasicCache<Object, Object> getCache() {
        return cache;
    }

    public InfinispanEventListener getListener() {
        return listener;
    }

    public InfinispanConfiguration getConfiguration() {
        return configuration;
    }
}
