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
package org.apache.camel.component.infinispan.processor.aggregate;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanRemoteAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanRemoteAggregationRepository.class.getName());
    
    private boolean useRecovery = true;
    private RemoteCacheManager manager;
    private String cacheName;
    private String deadLetterChannel;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries = 3;
    private boolean allowSerializedHeaders;
    private BasicCache<String, DefaultExchangeHolder> cache;
    private Configuration configuration;

    /**
     * Creates new {@link InfinispanRemoteAggregationRepository} that defaults to non-optimistic locking
     * with recoverable behavior and a local Infinispan cache. 
     */
    public InfinispanRemoteAggregationRepository() {
    }
    
    /**
     * Creates new {@link InfinispanRemoteAggregationRepository} that defaults to non-optimistic locking
     * with recoverable behavior and a local Infinispan cache. 
     * @param cacheName cache name
     */
    public InfinispanRemoteAggregationRepository(final String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public Exchange add(final CamelContext camelContext, final String key, final Exchange exchange) {
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        DefaultExchangeHolder oldHolder = cache.put(key, newHolder);
        return unmarshallExchange(camelContext, oldHolder);
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, cache.get(key));
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        LOG.trace("Removing an exchange with ID {} for key {}", exchange.getExchangeId(), key);
        cache.remove(key);
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
        cache.remove(exchangeId);
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
        Set<String> scanned = Collections.unmodifiableSet(cache.keySet());
        LOG.trace("Found {} keys for exchanges to recover in {} context", scanned.size(), camelContext.getName());
        return scanned;
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}.", exchangeId);
        return useRecovery ? unmarshallExchange(camelContext, cache.get(exchangeId)) : null;
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }
    
    @Override
    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterChannel = deadLetterUri;
    }

    @Override
    public String getDeadLetterUri() {
        return deadLetterChannel;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    @Override
    protected void doStart() throws Exception {
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }
        if (ObjectHelper.isEmpty(configuration)) {
            manager = new RemoteCacheManager();
            manager.start();
        } else {
            manager = new RemoteCacheManager(configuration);
            manager.start();
        }        
        if (ObjectHelper.isEmpty(cacheName)) {
            cache = manager.getCache();
        } else {
            cache = manager.getCache(cacheName);
        }
    }

    @Override
    protected void doStop() throws Exception {
        manager.stop();
    }

    protected Exchange unmarshallExchange(CamelContext camelContext, DefaultExchangeHolder holder) {
        Exchange exchange = null;
        if (holder != null) {
            exchange = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(exchange, holder);
        }
        return exchange;
    }
    
    public RemoteCacheManager getManager() {
        return manager;
    }

    public void setManager(RemoteCacheManager manager) {
        this.manager = manager;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getDeadLetterChannel() {
        return deadLetterChannel;
    }

    public void setDeadLetterChannel(String deadLetterChannel) {
        this.deadLetterChannel = deadLetterChannel;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    public BasicCache<String, DefaultExchangeHolder> getCache() {
        return cache;
    }

    public void setCache(BasicCache<String, DefaultExchangeHolder> cache) {
        this.cache = cache;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
