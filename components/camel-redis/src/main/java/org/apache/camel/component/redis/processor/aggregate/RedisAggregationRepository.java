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
package org.apache.camel.component.redis.processor.aggregate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.apache.camel.spi.AggregationRepository} using Redis as store.
 */
public class RedisAggregationRepository extends ServiceSupport
        implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(RedisAggregationRepository.class);
    private static final String COMPLETED_SUFFIX = "-completed";

    private boolean optimistic;
    private boolean useRecovery = true;
    private Map<String, DefaultExchangeHolder> cache;
    private Map<String, DefaultExchangeHolder> persistedCache;
    private String endpoint;
    private String mapName;
    private String persistenceMapName;
    private RedissonClient redisson;
    private boolean shutdownRedisson;
    private String deadLetterChannel;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries = 3;
    private boolean allowSerializedHeaders;

    public RedisAggregationRepository() {
    }

    public RedisAggregationRepository(final String mapName, final String endpoint) {
        this.mapName = mapName;
        this.persistenceMapName = String.format("%s%s", mapName, COMPLETED_SUFFIX);
        this.optimistic = false;
        this.endpoint = endpoint;
    }

    public RedisAggregationRepository(final String mapName, final String persistenceMapName,
                                      final String endpoint) {
        this.mapName = mapName;
        this.persistenceMapName = persistenceMapName;
        this.optimistic = false;
        this.endpoint = endpoint;
    }

    public RedisAggregationRepository(final String mapName, final String endpoint, boolean optimistic) {
        this(mapName, endpoint);
        this.optimistic = optimistic;
    }

    public RedisAggregationRepository(final String mapName, final String persistenceMapName,
                                      final String endpoint, boolean optimistic) {
        this(mapName, persistenceMapName, endpoint);
        this.optimistic = optimistic;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange)
            throws OptimisticLockingException {
        if (!optimistic) {
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in an optimistic manner.", newExchange.getExchangeId(), key);
        if (oldExchange == null) {
            DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(newExchange, true, allowSerializedHeaders);
            final DefaultExchangeHolder misbehaviorHolder = cache.putIfAbsent(key, holder);
            if (misbehaviorHolder != null) {
                Exchange misbehaviorEx = unmarshallExchange(camelContext, misbehaviorHolder);
                LOG.warn(
                        "Optimistic locking failed for exchange with key {}: IMap#putIfAbsend returned Exchange with ID {}, while it's expected no exchanges to be returned",
                        key, misbehaviorEx != null ? misbehaviorEx.getExchangeId() : "<null>");
                throw new OptimisticLockingException();
            }
        } else {
            DefaultExchangeHolder oldHolder = DefaultExchangeHolder.marshal(oldExchange, true, allowSerializedHeaders);
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(newExchange, true, allowSerializedHeaders);
            if (!cache.replace(key, oldHolder, newHolder)) {
                LOG.warn(
                        "Optimistic locking failed for exchange with key {}: IMap#replace returned no Exchanges, while it's expected to replace one",
                        key);
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
        RLock lock = redisson.getLock("aggregationLock");
        try {
            lock.lock();
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
            DefaultExchangeHolder oldHolder = cache.put(key, newHolder);
            return unmarshallExchange(camelContext, oldHolder);
        } finally {
            LOG.trace("Added an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
            lock.unlock();
        }
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        if (useRecovery) {
            LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
            Set<String> scanned = Collections.unmodifiableSet(persistedCache.keySet());
            LOG.trace("Found {} keys for exchanges to recover in {} context", scanned.size(), camelContext.getName());
            return scanned;
        } else {
            LOG.warn(
                    "What for to run recovery scans in {} context while repository {} is running in non-recoverable aggregation repository mode?!",
                    camelContext.getName(), mapName);
            return Collections.emptySet();
        }
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}.", exchangeId);
        return useRecovery ? unmarshallExchange(camelContext, persistedCache.get(exchangeId)) : null;
    }

    public boolean isOptimistic() {
        return optimistic;
    }

    public void setOptimistic(boolean optimistic) {
        this.optimistic = optimistic;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public String getPersistenceMapName() {
        return persistenceMapName;
    }

    public void setPersistenceMapName(String persistenceMapName) {
        this.persistenceMapName = persistenceMapName;
    }

    public RedissonClient getRedisson() {
        return redisson;
    }

    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
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
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, cache.get(key));
    }

    /**
     * Checks if the key in question is in the repository.
     *
     * @param key Object - key in question
     */
    public boolean containsKey(Object key) {
        if (cache != null) {
            return cache.containsKey(key);
        } else {
            return false;
        }
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        if (optimistic) {
            LOG.trace("Removing an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
            if (!cache.remove(key, holder)) {
                LOG.warn(
                        "Optimistic locking failed for exchange with key {}: IMap#remove removed no Exchanges, while it's expected to remove one.",
                        key);
                throw new OptimisticLockingException();
            }
            LOG.trace("Removed an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
            if (useRecovery) {
                LOG.trace("Putting an exchange with ID {} for key {} into a recoverable storage in an optimistic manner.",
                        exchange.getExchangeId(), key);
                persistedCache.put(exchange.getExchangeId(), holder);
                LOG.trace("Put an exchange with ID {} for key {} into a recoverable storage in an optimistic manner.",
                        exchange.getExchangeId(), key);
            }
        } else {
            if (useRecovery) {
                LOG.trace("Removing an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
                TransactionOptions tOpts = TransactionOptions.defaults();
                RTransaction transaction = redisson.createTransaction(tOpts);

                try {
                    RMap<String, DefaultExchangeHolder> tCache = transaction.getMap(mapName);
                    RMap<String, DefaultExchangeHolder> tPersistentCache = transaction.getMap(persistenceMapName);

                    DefaultExchangeHolder removedHolder = tCache.remove(key);
                    LOG.trace("Putting an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                    tPersistentCache.put(exchange.getExchangeId(), removedHolder);

                    transaction.commit();
                    LOG.trace("Removed an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(),
                            key);
                    LOG.trace("Put an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                } catch (Exception throwable) {
                    transaction.rollback();

                    final String msg = String.format(
                            "Transaction was rolled back for remove operation with a key %s and an Exchange ID %s.",
                            key, exchange.getExchangeId());
                    LOG.warn(msg, throwable);
                    throw new RuntimeException(msg, throwable);
                }
            } else {
                cache.remove(key);
            }
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
        if (useRecovery) {
            persistedCache.remove(exchangeId);
        }
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    public String getPersistentRepositoryName() {
        return persistenceMapName;
    }

    @Override
    protected void doInit() throws Exception {
        StringHelper.notEmpty(mapName, "repositoryName");
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (redisson == null) {
            Config config = new Config();
            config.useSingleServer().setAddress(String.format("redis://%s", endpoint));
            redisson = Redisson.create(config);
            shutdownRedisson = true;
        }
        cache = redisson.getMap(mapName);
        if (useRecovery) {
            persistedCache = redisson.getMap(persistenceMapName);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (redisson != null && shutdownRedisson) {
            redisson.shutdown();
            redisson = null;
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
}
