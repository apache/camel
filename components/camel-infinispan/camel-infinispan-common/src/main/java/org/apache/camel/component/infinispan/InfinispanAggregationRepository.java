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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InfinispanAggregationRepository
        extends ServiceSupport
        implements RecoverableAggregationRepository, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanAggregationRepository.class);

    /**
     * Prefix of the keys under which completed exchanges are kept for recovery. Recovery entries are keyed by exchange
     * id, aggregations in progress by correlation key, and the prefix keeps the two apart in the same cache.
     */
    private static final String RECOVERY_KEY_PREFIX = "camel-recovery:";

    private CamelContext camelContext;

    @Metadata(description = "Name of cache", required = true)
    private String cacheName;
    @Metadata(description = "Whether or not recovery is enabled", defaultValue = "true")
    private boolean useRecovery = true;
    @Metadata(description = "Sets an optional dead letter channel which exhausted recovered Exchange should be send to.")
    private String deadLetterUri;
    @Metadata(description = "Sets the interval between recovery scans", defaultValue = "5000")
    private long recoveryInterval = 5000;
    @Metadata(description = "Sets an optional limit of the number of redelivery attempt of recovered Exchange should be attempted, before its exhausted."
                            + " When this limit is hit, then the Exchange is moved to the dead letter channel.",
              defaultValue = "3")
    private int maximumRedeliveries = 3;
    @Metadata(label = "advanced", security = "insecure:serialization",
              description = "Whether headers on the Exchange that are Java objects and Serializable should be included and saved to the repository")
    private boolean allowSerializedHeaders;

    public InfinispanAggregationRepository() {
    }

    /**
     * Creates new {@link InfinispanAggregationRepository} that defaults to non-optimistic locking with recoverable
     * behavior and a local Infinispan cache.
     *
     * @param cacheName cache name
     */
    protected InfinispanAggregationRepository(String cacheName) {
        this.cacheName = cacheName;
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
    public Exchange add(final CamelContext camelContext, final String key, final Exchange exchange) {
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        DefaultExchangeHolder oldHolder = getCache().put(key, newHolder);
        return unmarshallExchange(camelContext, oldHolder);
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, getCache().get(key));
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        LOG.trace("Removing an exchange with ID {} for key {}", exchange.getExchangeId(), key);
        DefaultExchangeHolder holder = getCache().remove(key);

        if (useRecovery) {
            // the aggregation is complete but the exchange has not been processed yet, so keep a copy that
            // recovery can pick up if the processing never confirms it
            if (holder == null) {
                holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
            }
            LOG.trace("Putting an exchange with ID {} into the recovery store", exchange.getExchangeId());
            getCache().put(recoveryKey(exchange.getExchangeId()), holder);
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
        if (useRecovery) {
            getCache().remove(recoveryKey(exchangeId));
        }
    }

    @Override
    public Set<String> getKeys() {
        return getCache().keySet().stream()
                .filter(key -> !isRecoveryKey(key))
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        if (!useRecovery) {
            LOG.debug("Recovery is disabled on the repository of {} context, nothing to scan", camelContext.getName());
            return Collections.emptySet();
        }

        LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
        Set<String> scanned = getCache().keySet().stream()
                .filter(InfinispanAggregationRepository::isRecoveryKey)
                .map(InfinispanAggregationRepository::exchangeIdOf)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
        LOG.trace("Found {} exchanges to recover in {} context", scanned.size(), camelContext.getName());
        return scanned;
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}.", exchangeId);
        return useRecovery ? unmarshallExchange(camelContext, getCache().get(recoveryKey(exchangeId))) : null;
    }

    private static String recoveryKey(String exchangeId) {
        return RECOVERY_KEY_PREFIX + exchangeId;
    }

    private static boolean isRecoveryKey(String key) {
        return key.startsWith(RECOVERY_KEY_PREFIX);
    }

    private static String exchangeIdOf(String recoveryKey) {
        return recoveryKey.substring(RECOVERY_KEY_PREFIX.length());
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
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
    protected void doStart() throws Exception {
        ObjectHelper.notNull(cacheName, "cacheName", this);
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }
    }

    protected Exchange unmarshallExchange(CamelContext camelContext, DefaultExchangeHolder holder) {
        Exchange exchange = null;
        if (holder != null) {
            exchange = new DefaultExchange(camelContext);
            DefaultExchangeHolder.unmarshal(exchange, holder);
        }
        return exchange;
    }

    public String getCacheName() {
        return cacheName;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    protected abstract BasicCache<String, DefaultExchangeHolder> getCache();

}
