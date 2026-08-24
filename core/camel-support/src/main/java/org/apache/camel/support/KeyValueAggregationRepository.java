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
package org.apache.camel.support;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An adapter that implements {@link RecoverableAggregationRepository} by delegating to a {@link KeyValueRepository}.
 * <p/>
 * Exchanges are serialized and deserialized using {@link DefaultExchangeHolder}. Active (in-progress) aggregates are
 * stored under the correlation key. Completed-but-unconfirmed aggregates (for recovery) are stored under a separate key
 * derived from the exchange ID.
 * <p/>
 * This allows any {@link KeyValueRepository} implementation to be used as an aggregation repository without
 * implementing the {@link org.apache.camel.spi.AggregationRepository} interface directly.
 *
 * @since 4.23
 */
public class KeyValueAggregationRepository extends ServiceSupport
        implements RecoverableAggregationRepository, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(KeyValueAggregationRepository.class);

    /**
     * Prefix used to separate completed (pending confirmation) entries from active aggregates in the underlying store.
     */
    private static final String COMPLETED_PREFIX = "completed:";

    private final KeyValueRepository repository;
    private CamelContext camelContext;
    private boolean useRecovery = true;
    private String deadLetterUri;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries = 3;
    private boolean allowSerializedHeaders;

    /**
     * Creates an aggregation repository adapter backed by the given key-value repository.
     *
     * @param repository the underlying key-value repository; must not be {@code null}
     */
    public KeyValueAggregationRepository(KeyValueRepository repository) {
        ObjectHelper.notNull(repository, "repository");
        this.repository = repository;
    }

    /**
     * Creates a new {@link KeyValueAggregationRepository} wrapping the given {@link KeyValueRepository}.
     *
     * @param  repository the underlying key-value repository
     * @return            the adapter
     */
    public static KeyValueAggregationRepository keyValueAggregationRepository(KeyValueRepository repository) {
        return new KeyValueAggregationRepository(repository);
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        LOG.trace("Adding an Exchange with ID {} for key {}", exchange.getExchangeId(), key);
        DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        DefaultExchangeHolder oldHolder = (DefaultExchangeHolder) repository.get(key);
        repository.put(key, newHolder, 0);
        return unmarshallExchange(camelContext, oldHolder);
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, (DefaultExchangeHolder) repository.get(key));
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        LOG.trace("Removing an Exchange with ID {} for key {}", exchange.getExchangeId(), key);
        DefaultExchangeHolder holder = (DefaultExchangeHolder) repository.delete(key);
        if (useRecovery && holder != null) {
            // Store under the exchangeId for potential recovery
            LOG.trace("Moving Exchange with ID {} to completed (pending confirmation)", exchange.getExchangeId());
            repository.put(COMPLETED_PREFIX + exchange.getExchangeId(), holder, 0);
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an Exchange with ID {}", exchangeId);
        repository.delete(COMPLETED_PREFIX + exchangeId);
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(
                repository.keys().stream()
                        .filter(k -> !k.startsWith(COMPLETED_PREFIX))
                        .collect(Collectors.toSet()));
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
        Set<String> scanned = repository.keys().stream()
                .filter(k -> k.startsWith(COMPLETED_PREFIX))
                .map(k -> k.substring(COMPLETED_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        LOG.trace("Found {} keys for exchanges to recover in {} context", scanned.size(), camelContext.getName());
        return scanned;
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}", exchangeId);
        if (!useRecovery) {
            return null;
        }
        return unmarshallExchange(camelContext, (DefaultExchangeHolder) repository.get(COMPLETED_PREFIX + exchangeId));
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
    public long getRecoveryInterval() {
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
        this.deadLetterUri = deadLetterUri;
    }

    @Override
    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    /**
     * Returns the underlying {@link KeyValueRepository}.
     */
    public KeyValueRepository getRepository() {
        return repository;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(repository);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(repository);
    }

    private Exchange unmarshallExchange(CamelContext camelContext, DefaultExchangeHolder holder) {
        if (holder == null) {
            return null;
        }
        Exchange exchange = new DefaultExchange(camelContext);
        DefaultExchangeHolder.unmarshal(exchange, holder);
        return exchange;
    }
}
