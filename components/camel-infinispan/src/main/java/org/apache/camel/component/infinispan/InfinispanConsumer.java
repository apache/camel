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
import org.apache.camel.component.infinispan.remote.InfinispanRemoteOperation;
import org.apache.camel.impl.DefaultConsumer;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.query.api.continuous.ContinuousQuery;
import org.infinispan.query.api.continuous.ContinuousQueryListener;
import org.infinispan.query.dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanConsumer extends DefaultConsumer {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanProducer.class);
    private final InfinispanConfiguration configuration;
    private final InfinispanManager manager;
    private final String cacheName;
    private InfinispanEventListener listener;
    private InfinispanConsumerHandler consumerHandler;
    private BasicCache<Object, Object> cache;
    private ContinuousQuery<Object, Object> continuousQuery;

    public InfinispanConsumer(InfinispanEndpoint endpoint, Processor processor, String cacheName, InfinispanConfiguration configuration) {
        super(endpoint, processor);
        this.cacheName = cacheName;
        this.configuration = configuration;
        this.manager = new InfinispanManager(endpoint.getCamelContext(), configuration);
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
        super.doStart();
        manager.start();

        cache = manager.getCache(cacheName);
        if (configuration.hasQueryBuilder()) {
            if (InfinispanUtil.isRemote(cache)) {
                RemoteCache<Object, Object> remoteCache = InfinispanUtil.asRemote(cache);
                Query query = InfinispanRemoteOperation.buildQuery(configuration.getQueryBuilder(), remoteCache);

                continuousQuery = Search.getContinuousQuery(remoteCache);
                continuousQuery.addContinuousQueryListener(query, new ContinuousQueryEventListener(cache.getName()));
            } else {
                throw new IllegalArgumentException(
                    "Can't run continuous queries against embedded cache (" + cache.getName() + ")");
            }
        } else {
            if (manager.isCacheContainerEmbedded()) {
                consumerHandler = InfinispanConsumerEmbeddedHandler.INSTANCE;
            } else if (manager.isCacheContainerRemote()) {
                consumerHandler = InfinispanConsumerRemoteHandler.INSTANCE;
            } else {
                throw new UnsupportedOperationException(
                    "Unsupported CacheContainer type " + manager.getCacheContainer().getClass().getName());
            }

            listener = consumerHandler.start(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (continuousQuery != null) {
            continuousQuery.removeAllListeners();
        }

        if (consumerHandler != null) {
            consumerHandler.stop(this);
        }

        manager.stop();
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

    private class ContinuousQueryEventListener implements ContinuousQueryListener<Object, Object> {
        private final String cacheName;

        ContinuousQueryEventListener(String cacheName) {
            this.cacheName = cacheName;
        }

        @Override
        public void resultJoining(Object key, Object value) {
            processEvent(InfinispanConstants.CACHE_ENTRY_JOINING, false, cacheName, key, value);
        }
        
        @Override
        public void resultUpdated(Object key, Object value) {
            processEvent(InfinispanConstants.CACHE_ENTRY_UPDATED, false, cacheName, key, value);
        }

        @Override
        public void resultLeaving(Object key) {
            processEvent(InfinispanConstants.CACHE_ENTRY_LEAVING, false, cacheName, key);
        }
    }
}
