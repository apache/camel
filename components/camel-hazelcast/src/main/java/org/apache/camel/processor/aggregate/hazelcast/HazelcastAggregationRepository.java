package org.apache.camel.processor.aggregate.hazelcast;


import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.TransactionalMap;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.ServiceSupport;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HazelcastAggregationRepository extends ServiceSupport
                                                  implements RecoverableAggregationRepository,
                                                             OptimisticLockingAggregationRepository {
    private boolean optimistic;
    private boolean useLocalHzInstance;
    private boolean useRecovery;
    private IMap<String, Exchange> cache;
    private IMap<String, Exchange> persistedCache;
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastAggregationRepository.class.getName()) ;
    private HazelcastInstance hzInstance;
    private String mapName;
    private String persistenceMapName;
    private static final String COMPLETED_SUFFIX = "-completed";
    private String deadLetterChannel;
    private long recoveryInterval = 5000;
    private int maximumRedeliveries;

    public HazelcastAggregationRepository(final String repositoryName) {
        mapName = repositoryName;
        persistenceMapName = String.format("%s%s", mapName, COMPLETED_SUFFIX);
        optimistic = false;
        useLocalHzInstance = true;
    }

    public HazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName) {
        mapName = repositoryName;
        persistenceMapName = persistentRepositoryName;
        optimistic = false;
        useLocalHzInstance = true;
    }

    public HazelcastAggregationRepository(final String repositoryName, boolean optimistic) {
        this(repositoryName);
        this.optimistic = optimistic;
        useLocalHzInstance = true;
    }

    public HazelcastAggregationRepository(final String repositoryName, final String persistenRepositoryName, boolean optimistic) {
        this(repositoryName, persistenRepositoryName);
        this.optimistic = optimistic;
        useLocalHzInstance = true;
    }

    public HazelcastAggregationRepository(final String repositoryName, HazelcastInstance hzInstanse) {
        this (repositoryName, false);
        this.hzInstance = hzInstanse;
        useLocalHzInstance = false;
    }

    public HazelcastAggregationRepository(final String repositoryName, final String persistenRepositoryName, HazelcastInstance hzInstanse) {
        this (repositoryName, persistenRepositoryName, false);
        this.hzInstance = hzInstanse;
        useLocalHzInstance = false;
    }

    public HazelcastAggregationRepository(final String repositoryName, boolean optimistic, HazelcastInstance hzInstance) {
        this(repositoryName, optimistic);
        this.hzInstance = hzInstance;
    }

    public HazelcastAggregationRepository(final String repositoryName, final String persistenRepositoryName, boolean optimistic, HazelcastInstance hzInstance) {
        this(repositoryName, persistenRepositoryName, optimistic);
        this.hzInstance = hzInstance;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) throws OptimisticLockingException {
        if (!optimistic) {
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in an optimistic manner.", newExchange.getExchangeId(), key);
        if (oldExchange == null) {
            final Exchange misbehaviorEx = cache.putIfAbsent(key, newExchange);
            if (misbehaviorEx != null) {
                LOG.error("Optimistic locking failed for exchange with key {}: IMap#putIfAbsend returned Exchange with ID {}, while it's expected no exchanges to be returned",
                        key, misbehaviorEx.getExchangeId());
                throw  new OptimisticLockingException();
            }
        } else {
            if (!cache.replace(key, oldExchange, newExchange)) {
                LOG.error("Optimistic locking failed for exchange with key {}: IMap#replace returned no Exchanges, while it's expected to replace one",
                        key);
                throw new OptimisticLockingException();
            }
        }
        LOG.trace("Added an Exchange with ID {} for key {} in optimistic manner.", newExchange.getExchangeId(), key);
        return oldExchange;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        if (optimistic){
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        Lock l = hzInstance.getLock(mapName);
        try {
            l.lock();
            return cache.put(key, exchange);
        } finally {
            LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
            l.unlock();
        }
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        if (useRecovery) {
            LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
            Set<String> scanned = Collections.unmodifiableSet(persistedCache.keySet());
            LOG.trace("Found {} keys for exchanges to recover in {} context", scanned.size(),camelContext.getName());
            return scanned;
        }
        else {
            LOG.warn("What for to run recovery scans in {} context while repository {} is running in non-recoverable aggregation repository mode?!",
                    camelContext.getName(), mapName);
            return Collections.emptySet();
        }
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        LOG.trace("Recovering an Exchange with ID {}.", exchangeId);
        return useRecovery ? persistedCache.get(exchangeId) : null;

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
        return cache.get(key);
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        if (optimistic) {
            LOG.trace("Removing an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
            if (!cache.remove(key, exchange)) {
                LOG.error("Optimistic locking failed for exchange with key {}: IMap#remove removed no Exchanges, while it's expected to remove one.",
                        key);
                throw new OptimisticLockingException();
            }
            LOG.trace("Removed an exchange with ID {} for key {} in an optimistic manner.", exchange.getExchangeId(), key);
            if (useRecovery) {
                LOG.trace("Putting an exchange with ID {} for key {} into a recoverable storage in an optimistic manner.",
                        exchange.getExchangeId(), key);
                persistedCache.put(key, exchange);
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

                tOpts.setTransactionType(TransactionOptions.TransactionType.LOCAL);
                TransactionContext tCtx = hzInstance.newTransactionContext(tOpts);

                try {

                    tCtx.beginTransaction();

                    TransactionalMap<String, Exchange> tCache = tCtx.getMap(cache.getName());
                    TransactionalMap<String, Exchange> tPersistentCache = tCtx.getMap(persistedCache.getName());

                    tCache.remove(key);
                    LOG.trace("Putting an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                    tPersistentCache.put(key, exchange);

                    tCtx.commitTransaction();
                    LOG.trace("Removed an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
                    LOG.trace("Put an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                } catch (Throwable throwable) {
                    tCtx.rollbackTransaction();

                    final String msg = String.format("Transaction with ID %s was rolled back for remove operation with a key %s and an Exchange ID %s.",
                            tCtx.getTxnId(), key, exchange.getExchangeId());
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
        persistedCache.remove(exchangeId);
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    public String getPersistentRepositoryName() {
        return persistenceMapName;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notEmpty(mapName, "repositoryName");
        if (useLocalHzInstance)  {
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
        if (useRecovery) {
            persistedCache.clear();
        }

        cache.clear();

        if (useLocalHzInstance) {
            hzInstance.getLifecycleService().shutdown();
        }
    }
}
