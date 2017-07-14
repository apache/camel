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
package org.apache.camel.component.caffeine.processor.aggregate;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaffeineAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(CaffeineAggregationRepository.class);

    private CamelContext camelContext;
    private Cache<String, DefaultExchangeHolder> cache;
    private boolean allowSerializedHeaders;

    private boolean useRecovery = true;
    private String deadLetterChannel;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries = 3;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Cache<String, DefaultExchangeHolder> getCache() {
        return cache;
    }

    public void setCache(Cache<String, DefaultExchangeHolder> cache) {
        this.cache = cache;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
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
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    public String getDeadLetterChannel() {
        return deadLetterChannel;
    }

    public void setDeadLetterChannel(String deadLetterChannel) {
        this.deadLetterChannel = deadLetterChannel;
    }

    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    @Override
    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    @Override
    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public Exchange add(final CamelContext camelContext, final String key, final Exchange exchange) {
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);

        final DefaultExchangeHolder oldHolder = cache.getIfPresent(key);
        final DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);

        cache.put(key, newHolder);

        return unmarshallExchange(camelContext, oldHolder);
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, cache.getIfPresent(key));
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        LOG.trace("Removing an exchange with ID {} for key {} ", exchange.getExchangeId(), key);
        cache.invalidate(key);
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
        cache.invalidate(exchangeId);
    }

    @Override
    public Set<String> getKeys() {
        Set<String> keys = cache.asMap().keySet();

        return Collections.unmodifiableSet(keys);
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
        Set<String> scanned = Collections.unmodifiableSet(getKeys());
        LOG.trace("Found {} keys for exchanges to recover in {} context", scanned.size(), camelContext.getName());
        return scanned;
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}.", exchangeId);
        return useRecovery ? unmarshallExchange(camelContext, cache.getIfPresent(exchangeId)) : null;
    }

    @Override
    protected void doStart() throws Exception {
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }

        if (cache == null) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder();
            cache = builder.build();
        }
    }

    @Override
    protected void doStop() throws Exception {
    }

    public static Exchange unmarshallExchange(CamelContext camelContext, DefaultExchangeHolder holder) {
        Exchange exchange = null;
        if (holder != null) {
            exchange = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(exchange, holder);
        }

        return exchange;
    }
}
