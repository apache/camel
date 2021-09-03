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
package org.apache.camel.component.infinispan;

import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;

public abstract class InfinispanConsumer<
        ContainerType extends BasicCacheContainer,
        ManagerType extends InfinispanManager<ContainerType>,
        ConfigurationType extends InfinispanConfiguration>
        extends DefaultConsumer
        implements InfinispanEventProcessor {

    protected final ConfigurationType configuration;
    protected final ManagerType manager;
    protected final String cacheName;

    protected InfinispanConsumer(InfinispanEndpoint endpoint, Processor processor, String cacheName, ManagerType manager,
                                 ConfigurationType configuration) {
        super(endpoint, processor);
        this.cacheName = cacheName;
        this.configuration = configuration;
        this.manager = manager;
    }

    @Override
    public void processEvent(String eventType, String cacheName, Object key, Object eventData, Consumer<Exchange> consumer) {
        Exchange exchange = createExchange(false);
        try {
            exchange.getMessage().setHeader(InfinispanConstants.EVENT_TYPE, eventType);
            exchange.getMessage().setHeader(InfinispanConstants.CACHE_NAME, cacheName);

            if (key != null) {
                exchange.getMessage().setHeader(InfinispanConstants.KEY, key);
            }
            if (eventData != null) {
                exchange.getMessage().setHeader(InfinispanConstants.EVENT_DATA, eventData);
            }
            if (consumer != null) {
                consumer.accept(exchange);
            }

            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        } finally {
            releaseExchange(exchange, false);
        }
    }

    public <K, V> BasicCache<K, V> getCache() {
        return manager.getCache(cacheName);
    }

    public <K, V, C extends BasicCache<K, V>> C getCache(Class<C> type) {
        return type.cast(getCache());
    }

    public ConfigurationType getConfiguration() {
        return configuration;
    }
}
