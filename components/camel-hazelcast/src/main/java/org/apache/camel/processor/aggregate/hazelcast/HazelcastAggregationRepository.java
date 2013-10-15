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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HazelcastAggregationRepository extends ServiceSupport
                                                  implements RecoverableAggregationRepository,
                                                             OptimisticLockingAggregationRepository {
    private boolean optimistic;
    private boolean localHzInstance;
    private boolean useRecovery;
    private IMap<String, Exchange> cache;
    private IMap<String, Exchange> persistedCache;
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastAggregationRepository.class.getName()) ;
    private HazelcastInstance hzInstance;
    private String mapName;
    private String completedSuffix = "-completed";
    private String deadLetterChannel;
    private long recoveryInterval;
    private int maximumRedeliveries;

    public HazelcastAggregationRepository(final String repositoryName) {
        mapName = repositoryName;
        optimistic = false;
        localHzInstance = true;
    }

    public HazelcastAggregationRepository(final String repositoryName, boolean optimistic) {
        this(repositoryName);
        this.optimistic = optimistic;
        localHzInstance = true;
    }

    public HazelcastAggregationRepository(final String repositoryName, HazelcastInstance hzInstanse) {
        this (repositoryName, false);
        this.hzInstance = hzInstanse;
        localHzInstance = false;
    }

    public HazelcastAggregationRepository(final String repositoryName, boolean optimistic, HazelcastInstance hzInstance) {
        this(repositoryName, optimistic);
        this.hzInstance = hzInstance;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) throws OptimisticLockingException {
        if (!optimistic) {
            throw new UnsupportedOperationException();
        }
        if (oldExchange == null) {
            if (cache.putIfAbsent(key, newExchange) != null) {
                throw  new OptimisticLockingException();
            }
        } else {
            if (!cache.replace(key, oldExchange, newExchange)) {
                throw new OptimisticLockingException();
            }
        }
        return oldExchange;
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        if (optimistic){
            throw new UnsupportedOperationException();
        }
        Lock l = hzInstance.getLock(mapName);
        try {
            l.lock();
            return cache.put(key, exchange);
        } finally {
            l.unlock();
        }
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        if (useRecovery)
            return Collections.unmodifiableSet(persistedCache.keySet());
        else
            return Collections.emptySet();
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
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
            if (!cache.remove(key, exchange)) {
                throw new OptimisticLockingException();
            }
            if (useRecovery) {
                persistedCache.put(key, exchange);
            }
        } else {
            if (useRecovery) {
                // The only considerable case for transaction usage is fault tolerance:
                // the transaction will be rolled back automatically (default timeout is 2 minutes)
                // if no commit occurs during the timeout. So we are still consistent whether local node crashes.

                TransactionOptions tOpts = new TransactionOptions();

                tOpts.setTransactionType(TransactionOptions.TransactionType.LOCAL);
                TransactionContext tCtx = hzInstance.newTransactionContext(tOpts);
                tCtx.beginTransaction();

                TransactionalMap<String, Exchange> tCache = tCtx.getMap(cache.getName());
                TransactionalMap<String, Exchange> tPersistentCache = tCtx.getMap(persistedCache.getName());

                tCache.remove(key);
                tPersistentCache.put(key, exchange);

                tCtx.commitTransaction();
            } else {
                cache.remove(key);
            }
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        persistedCache.remove(exchangeId);
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    public void setCompletedSuffix(final String completedSuffix) {
        this.completedSuffix = completedSuffix;
    }

    public String getCompletedSuffix() {
        return completedSuffix;
    }

    @Override
    protected void doStart() throws Exception {
        if (localHzInstance)  {
            Config cfg = new XmlConfigBuilder().build();
            cfg.setProperty("hazelcast.version.check.enabled", "false");
            hzInstance = Hazelcast.newHazelcastInstance(cfg);
        }
        cache = hzInstance.getMap(mapName);
        if (useRecovery) {
            persistedCache = hzInstance.getMap(String.format("%s%s", mapName, completedSuffix));
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (useRecovery) {
            persistedCache.clear();
        }

        cache.clear();

        if (localHzInstance) {
            hzInstance.getLifecycleService().shutdown();
        }
    }
}
