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
package org.apache.camel.component.jcache.processor.aggregate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.cache.Cache;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.jcache.JCacheConfiguration;
import org.apache.camel.component.jcache.JCacheHelper;
import org.apache.camel.component.jcache.JCacheManager;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCacheAggregationRepository extends ServiceSupport implements  OptimisticLockingAggregationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JCacheAggregationRepository.class);

    private JCacheConfiguration configuration;
    private Cache<String, DefaultExchangeHolder> cache;
    private boolean optimistic;
    private boolean allowSerializedHeaders;
    private JCacheManager<String, DefaultExchangeHolder> cacheManager;

    public JCacheAggregationRepository() {
        this.configuration = new JCacheConfiguration();
    }

    public JCacheConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JCacheConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getCacheName() {
        return configuration.getCacheName();
    }

    public void setCacheName(String cacheName) {
        configuration.setCacheName(cacheName);
    }

    public Cache<String, DefaultExchangeHolder> getCache() {
        return cache;
    }

    public void setCache(Cache<String, DefaultExchangeHolder> cache) {
        this.cache = cache;
    }

    public boolean isOptimistic() {
        return optimistic;
    }

    public void setOptimistic(boolean optimistic) {
        this.optimistic = optimistic;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) throws OptimisticLockingException {
        if (!optimistic) {
            throw new UnsupportedOperationException();
        }

        LOG.trace("Adding an Exchange with ID {} for key {} in an optimistic manner.", newExchange.getExchangeId(), key);
        if (oldExchange == null) {
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(newExchange, true, allowSerializedHeaders);
            DefaultExchangeHolder oldHolder = cache.getAndPut(key, newHolder);
            if (oldHolder != null) {
                Exchange exchange = unmarshallExchange(camelContext, oldHolder);
                LOG.error("Optimistic locking failed for exchange with key {}: IMap#putIfAbsend returned Exchange with ID {}, while it's expected no exchanges to be returned",
                    key,
                    exchange != null ? exchange.getExchangeId() : "<null>");

                throw new OptimisticLockingException();
            }
        } else {
            DefaultExchangeHolder oldHolder = DefaultExchangeHolder.marshal(oldExchange, true, allowSerializedHeaders);
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(newExchange, true, allowSerializedHeaders);
            if (!cache.replace(key, oldHolder, newHolder)) {
                LOG.error("Optimistic locking failed for exchange with key {}: IMap#replace returned no Exchanges, while it's expected to replace one", key);
                throw new OptimisticLockingException();
            }
        }
        LOG.trace("Added an Exchange with ID {} for key {} in optimistic manner.", newExchange.getExchangeId(), key);
        return oldExchange;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        if (optimistic) {
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        DefaultExchangeHolder oldHolder = cache.getAndPut(key, newHolder);
        return unmarshallExchange(camelContext, oldHolder);
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, cache.get(key));
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        if (optimistic) {
            LOG.trace("Removing an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
            if (!cache.remove(key, holder)) {
                LOG.error("Optimistic locking failed for exchange with key {}: IMap#remove removed no Exchanges, while it's expected to remove one.", key);
                throw new OptimisticLockingException();
            }
            LOG.trace("Removed an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
        } else {
            cache.remove(key);
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
    }

    @Override
    public Set<String> getKeys() {
        Set<String> keys = new HashSet<>();

        Iterator<Cache.Entry<String, DefaultExchangeHolder>> entries = cache.iterator();
        while (entries.hasNext()) {
            keys.add(entries.next().getKey());
        }

        return Collections.unmodifiableSet(keys);
    }

    @Override
    protected void doStart() throws Exception {
        if (cache != null) {
            cacheManager = new JCacheManager<>(cache);
        } else {
            cacheManager = JCacheHelper.createManager(
                ObjectHelper.notNull(configuration, "configuration")
            );

            cache = cacheManager.getCache();
        }
    }

    @Override
    protected void doStop() throws Exception {
        cacheManager.close();
    }

    protected Exchange unmarshallExchange(CamelContext camelContext, DefaultExchangeHolder holder) {
        Exchange exchange = null;
        if (holder != null) {
            exchange = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(exchange, holder);
        }
        return exchange;
    }
}
