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
package org.apache.camel.processor.aggregate.hazelcast;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionalMap;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Hazelcast-based AggregationRepository implementing {@link RecoverableAggregationRepository} and
 * {@link OptimisticLockingAggregationRepository}. Defaults to thread-safe (non-optimistic) locking and recoverable
 * strategy. Hazelcast settings are given to an end-user and can be controlled with repositoryName and
 * persistentRespositoryName, both are {@link com.hazelcast.map.IMap} &lt;String, Exchange&gt;. However
 * HazelcastAggregationRepository can run it's own Hazelcast instance, but obviously no benefits of Hazelcast clustering
 * are gained this way. If the {@link HazelcastAggregationRepository} uses it's own local {@link HazelcastInstance} it
 * will DESTROY this instance on {@link #doStop()}. You should control {@link HazelcastInstance} lifecycle yourself
 * whenever you instantiate {@link HazelcastAggregationRepository} passing a reference to the instance.
 *
 */
public class HazelcastAggregationRepository extends ServiceSupport
        implements RecoverableAggregationRepository,
        OptimisticLockingAggregationRepository {

    protected static final String COMPLETED_SUFFIX = "-completed";

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastAggregationRepository.class.getName());

    protected boolean optimistic;
    protected boolean useLocalHzInstance;
    protected boolean useRecovery = true;
    protected IMap<String, DefaultExchangeHolder> cache;
    protected IMap<String, DefaultExchangeHolder> persistedCache;
    protected HazelcastInstance hzInstance;
    protected String mapName;
    protected String persistenceMapName;
    protected String deadLetterChannel;
    protected long recoveryInterval = 5000;
    protected int maximumRedeliveries = 3;
    protected boolean allowSerializedHeaders;

    /**
     * Creates new {@link HazelcastAggregationRepository} that defaults to non-optimistic locking with recoverable
     * behavior and a local Hazelcast instance. Recoverable repository name defaults to {@code repositoryName} +
     * "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     */
    public HazelcastAggregationRepository(final String repositoryName) {
        mapName = repositoryName;
        persistenceMapName = String.format("%s%s", mapName, COMPLETED_SUFFIX);
        optimistic = false;
        useLocalHzInstance = true;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} that defaults to non-optimistic locking with recoverable
     * behavior and a local Hazelcast instance.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     */
    public HazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName) {
        mapName = repositoryName;
        persistenceMapName = persistentRepositoryName;
        optimistic = false;
        useLocalHzInstance = true;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} with recoverable behavior and a local Hazelcast instance.
     * Recoverable repository name defaults to {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     * @param optimistic     whether to use optimistic locking manner.
     */
    public HazelcastAggregationRepository(final String repositoryName, boolean optimistic) {
        this(repositoryName);
        this.optimistic = optimistic;
        useLocalHzInstance = true;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} with recoverable behavior and a local Hazelcast instance.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     * @param optimistic               whether to use optimistic locking manner.
     */
    public HazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                          boolean optimistic) {
        this(repositoryName, persistentRepositoryName);
        this.optimistic = optimistic;
        useLocalHzInstance = true;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} that defaults to non-optimistic locking with recoverable
     * behavior. Recoverable repository name defaults to {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     * @param hzInstanse     externally configured {@link HazelcastInstance}.
     */
    public HazelcastAggregationRepository(final String repositoryName, HazelcastInstance hzInstanse) {
        this(repositoryName, false);
        this.hzInstance = hzInstanse;
        useLocalHzInstance = false;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} that defaults to non-optimistic locking with recoverable
     * behavior.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     * @param hzInstanse               externally configured {@link HazelcastInstance}.
     */
    public HazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                          HazelcastInstance hzInstanse) {
        this(repositoryName, persistentRepositoryName, false);
        this.hzInstance = hzInstanse;
        useLocalHzInstance = false;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} with recoverable behavior. Recoverable repository name
     * defaults to {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     * @param optimistic     whether to use optimistic locking manner;
     * @param hzInstance     externally configured {@link HazelcastInstance}.
     */
    public HazelcastAggregationRepository(final String repositoryName, boolean optimistic, HazelcastInstance hzInstance) {
        this(repositoryName, optimistic);
        this.hzInstance = hzInstance;
        useLocalHzInstance = false;
    }

    /**
     * Creates new {@link HazelcastAggregationRepository} with recoverable behavior.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param optimistic               whether to use optimistic locking manner;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     * @param hzInstance               externally configured {@link HazelcastInstance}.
     */
    public HazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                          boolean optimistic, HazelcastInstance hzInstance) {
        this(repositoryName, persistentRepositoryName, optimistic);
        this.hzInstance = hzInstance;
        useLocalHzInstance = false;
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
                LOG.error(
                        "Optimistic locking failed for exchange with key {}: IMap#putIfAbsend returned Exchange with ID {}, while it's expected no exchanges to be returned",
                        key, misbehaviorEx != null ? misbehaviorEx.getExchangeId() : "<null>");
                throw new OptimisticLockingException();
            }
        } else {
            DefaultExchangeHolder oldHolder = DefaultExchangeHolder.marshal(oldExchange, true, allowSerializedHeaders);
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(newExchange, true, allowSerializedHeaders);
            if (!cache.replace(key, oldHolder, newHolder)) {
                LOG.error(
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
        Lock l = hzInstance.getCPSubsystem().getLock(mapName);
        try {
            l.lock();
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
            DefaultExchangeHolder oldHolder = cache.put(key, newHolder);
            return unmarshallExchange(camelContext, oldHolder);
        } finally {
            LOG.trace("Added an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
            l.unlock();
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

    /**
     * This method performs transactional operation on removing the {@code exchange} from the operational storage and
     * moving it into the persistent one if the {@link HazelcastAggregationRepository} runs in recoverable mode and
     * {@code optimistic} is false. It will act at <u>your own</u> risk otherwise.
     *
     * @param camelContext the current CamelContext
     * @param key          the correlation key
     * @param exchange     the exchange to remove
     */
    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        if (optimistic) {
            LOG.trace("Removing an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
            if (!cache.remove(key, holder)) {
                LOG.error(
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
                // The only considerable case for transaction usage is fault tolerance:
                // the transaction will be rolled back automatically (default timeout is 2 minutes)
                // if no commit occurs during the timeout. So we are still consistent whether local node crashes.
                TransactionOptions tOpts = new TransactionOptions();

                tOpts.setTransactionType(TransactionOptions.TransactionType.ONE_PHASE);
                TransactionContext tCtx = hzInstance.newTransactionContext(tOpts);

                try {
                    tCtx.beginTransaction();

                    TransactionalMap<String, DefaultExchangeHolder> tCache = tCtx.getMap(cache.getName());
                    TransactionalMap<String, DefaultExchangeHolder> tPersistentCache = tCtx.getMap(persistedCache.getName());

                    DefaultExchangeHolder removedHolder = tCache.remove(key);
                    LOG.trace("Putting an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                    tPersistentCache.put(exchange.getExchangeId(), removedHolder);

                    tCtx.commitTransaction();
                    LOG.trace("Removed an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(),
                            key);
                    LOG.trace("Put an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                } catch (Exception exception) {
                    tCtx.rollbackTransaction();

                    final String msg = String.format(
                            "Transaction with ID %s was rolled back for remove operation with a key %s and an Exchange ID %s.",
                            tCtx.getTxnId(), key, exchange.getExchangeId());
                    LOG.warn(msg, exception);
                    throw new RuntimeCamelException(msg, exception);
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

    /**
     * @return Persistent repository {@link IMap} name;
     */
    public String getPersistentRepositoryName() {
        return persistenceMapName;
    }

    @Override
    protected void doStart() throws Exception {
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }
        StringHelper.notEmpty(mapName, "repositoryName");
        if (useLocalHzInstance) {
            Config cfg = new XmlConfigBuilder().build();
            cfg.setProperty("hazelcast.version.check.enabled", "false");
            hzInstance = Hazelcast.newHazelcastInstance(cfg);
        } else {
            ObjectHelper.notNull(hzInstance, "hzInstanse");
        }
        cache = hzInstance.getMap(mapName);
        if (useRecovery) {
            persistedCache = hzInstance.getMap(persistenceMapName);
        }
    }

    @Override
    protected void doStop() throws Exception {
        //noop
        if (useLocalHzInstance) {
            hzInstance.getLifecycleService().shutdown();
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
