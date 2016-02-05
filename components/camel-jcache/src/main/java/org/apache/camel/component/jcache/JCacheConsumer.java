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

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JCache consumer.
 */
public class JCacheConsumer extends DefaultConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JCacheConsumer.class);

    private CacheEntryListenerConfiguration<Object, Object> entryListenerConfiguration;

    public JCacheConsumer(final JCacheEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.entryListenerConfiguration = null;
    }

    @Override
    protected void doStart() throws Exception {
        if (entryListenerConfiguration != null) {
            getCache().deregisterCacheEntryListener(entryListenerConfiguration);
        }

        entryListenerConfiguration = createEntryListenerConfiguration();
        getCache().registerCacheEntryListener(entryListenerConfiguration);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (entryListenerConfiguration != null) {
            getCache().deregisterCacheEntryListener(entryListenerConfiguration);
            entryListenerConfiguration = null;
        }

        super.doStop();
    }

    private JCacheEndpoint getJCacheEndpoint() {
        return (JCacheEndpoint)getEndpoint();
    }

    private Cache getCache() throws Exception {
        return getJCacheEndpoint().getManager().getCache();
    }

    private CacheEntryListenerConfiguration<Object, Object> createEntryListenerConfiguration() {
        return new MutableCacheEntryListenerConfiguration<>(
            new Factory<CacheEntryListener<Object, Object>>() {
                @Override
                public CacheEntryListener<Object, Object> create() {
                    return new JCacheEntryEventListener() {
                        @Override
                        protected void onEvents(Iterable<CacheEntryEvent<?, ?>> events) {
                            for (CacheEntryEvent<?, ?> event : events) {
                                Exchange exchange = getEndpoint().createExchange();
                                Message message = exchange.getIn();
                                message.setHeader(JCacheConstants.EVENT_TYPE, event.getEventType().name());
                                message.setHeader(JCacheConstants.KEY, event.getKey());
                                message.setBody(event.getValue());

                                if (event.isOldValueAvailable()) {
                                    message.setHeader(JCacheConstants.OLD_VALUE, event.getOldValue());
                                }

                                try {
                                    getProcessor().process(exchange);
                                } catch (Exception e) {
                                    LOGGER.error("Error processing event ", e);
                                }
                            }
                        }
                    };
                }
            },
            new Factory<CacheEntryEventFilter<Object, Object>>() {
                @Override
                public CacheEntryEventFilter<Object, Object> create() {
                    return getJCacheEndpoint().getManager().getEventFilter();
                }
            },
            getJCacheEndpoint().getManager().getConfiguration().isOldValueRequired(),
            getJCacheEndpoint().getManager().getConfiguration().isSynchronous()
        );
    }
}
