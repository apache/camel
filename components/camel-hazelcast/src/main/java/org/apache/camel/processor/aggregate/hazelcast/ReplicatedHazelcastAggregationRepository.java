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
import java.util.Map;
import java.util.Set;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastSerializationFilterHelper;
import org.apache.camel.component.hazelcast.HazelcastUtil;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Hazelcast-based AggregationRepository implementing {@link RecoverableAggregationRepository}, which keeps the groups
 * and the completed exchanges in {@link com.hazelcast.replicatedmap.ReplicatedMap}s named by repositoryName and
 * persistentRepositoryName, so every member of the cluster reads them from its own copy. Uses thread-safe
 * (non-optimistic) locking and defaults to recoverable strategy.
 * <p>
 * Optimistic locking is not supported, although the class inherits {@link OptimisticLockingAggregationRepository} from
 * {@link HazelcastAggregationRepository}: a {@code ReplicatedMap} has no atomic compare-and-set (its
 * {@code putIfAbsent}, {@code replace} and {@code remove(key, value)} are the {@link Map} default methods, a read of
 * the local copy followed by a separate write), so the repository fails to start when {@code optimistic} is
 * {@code true}.
 * <p>
 * In thread-safe mode, the Aggregate EIP reads the group, aggregates and writes it back, and only the write is done
 * under the cluster lock of the key. Only one aggregator in the cluster should aggregate a given correlation key at a
 * time: when two members aggregate the same key, a member can overwrite a group that the other member just updated, and
 * the messages added by that update are lost. A lock around the read would not prevent it either, as the read is from
 * the local copy, which can still miss a write of another member.
 * <p>
 * ReplicatedHazelcastAggregationRepository can run its own Hazelcast instance, but obviously no benefits of Hazelcast
 * clustering are gained this way. If the {@link ReplicatedHazelcastAggregationRepository} uses its own local
 * {@link HazelcastInstance} it will DESTROY this instance on {@link #doStop()}. You should control
 * {@link HazelcastInstance} lifecycle yourself whenever you instantiate
 * {@link ReplicatedHazelcastAggregationRepository} passing a reference to the instance.
 */
public class ReplicatedHazelcastAggregationRepository extends HazelcastAggregationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ReplicatedHazelcastAggregationRepository.class.getName());
    static final String OPTIMISTIC_NOT_SUPPORTED
            = "ReplicatedHazelcastAggregationRepository does not support optimistic locking (optimistic=true), as a Hazelcast"
              + " ReplicatedMap has no atomic compare-and-set. Use it with optimistic=false, and without optimisticLocking on"
              + " the Aggregate EIP.";
    static final String OPTIMISTIC_LOCKING_NOT_SUPPORTED
            = "ReplicatedHazelcastAggregationRepository does not support optimistic locking, as a Hazelcast ReplicatedMap"
              + " has no atomic compare-and-set, but the Aggregate EIP has optimisticLocking turned on. Use the Aggregate"
              + " EIP without optimisticLocking.";
    protected Map<String, DefaultExchangeHolder> replicatedCache;
    protected Map<String, DefaultExchangeHolder> replicatedPersistedCache;
    // IMap for distributed locking since ReplicatedMap doesn't support lock operations
    protected IMap<String, Boolean> lockMap;

    public ReplicatedHazelcastAggregationRepository() {
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior and a local Hazelcast instance. Recoverable repository name defaults to
     * {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName) {
        super(repositoryName);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior and a local Hazelcast instance.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName) {
        super(repositoryName, persistentRepositoryName);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} with recoverable behavior and a local Hazelcast
     * instance. Recoverable repository name defaults to {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     * @param optimistic     must be {@code false}: optimistic locking is not supported, and the repository fails to
     *                       start when it is {@code true}.
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, boolean optimistic) {
        super(repositoryName, optimistic);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} with recoverable behavior and a local Hazelcast
     * instance.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     * @param optimistic               must be {@code false}: optimistic locking is not supported, and the repository
     *                                 fails to start when it is {@code true}.
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                                    boolean optimistic) {
        super(repositoryName, persistentRepositoryName, optimistic);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior. Recoverable repository name defaults to {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     * @param hzInstanse     externally configured {@link HazelcastInstance}.
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, HazelcastInstance hzInstanse) {
        super(repositoryName, hzInstanse);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     * @param hzInstanse               externally configured {@link HazelcastInstance}.
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                                    HazelcastInstance hzInstanse) {
        super(repositoryName, persistentRepositoryName, hzInstanse);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} with recoverable behavior. Recoverable repository
     * name defaults to {@code repositoryName} + "-compeleted".
     *
     * @param repositoryName {@link IMap} repository name;
     * @param optimistic     must be {@code false}: optimistic locking is not supported, and the repository fails to
     *                       start when it is {@code true};
     * @param hzInstance     externally configured {@link HazelcastInstance}.
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, boolean optimistic,
                                                    HazelcastInstance hzInstance) {
        super(repositoryName, optimistic, hzInstance);
    }

    /**
     * Creates new {@link ReplicatedHazelcastAggregationRepository} with recoverable behavior.
     *
     * @param repositoryName           {@link IMap} repository name;
     * @param optimistic               must be {@code false}: optimistic locking is not supported, and the repository
     *                                 fails to start when it is {@code true};
     * @param persistentRepositoryName {@link IMap} recoverable repository name;
     * @param hzInstance               externally configured {@link HazelcastInstance}.
     */
    public ReplicatedHazelcastAggregationRepository(final String repositoryName, final String persistentRepositoryName,
                                                    boolean optimistic, HazelcastInstance hzInstance) {
        super(repositoryName, persistentRepositoryName, optimistic, hzInstance);
    }

    /**
     * Not supported: optimistic locking needs an atomic compare-and-set, which a {@code ReplicatedMap} does not have.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
        throw new UnsupportedOperationException(OPTIMISTIC_LOCKING_NOT_SUPPORTED);
    }

    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        if (optimistic) {
            throw new UnsupportedOperationException();
        }
        LOG.trace("Adding an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        // Use IMap-based distributed locking (community edition compatible)
        // ReplicatedMap doesn't support lock operations, so we use a separate IMap for locking
        lockMap.lock(key);
        try {
            DefaultExchangeHolder newHolder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
            DefaultExchangeHolder oldHolder = replicatedCache.put(key, newHolder);
            return unmarshallExchange(camelContext, oldHolder);
        } finally {
            LOG.trace("Added an Exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
            lockMap.unlock(key);
        }
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        if (useRecovery) {
            LOG.trace("Scanning for exchanges to recover in {} context", camelContext.getName());
            Set<String> scanned = Collections.unmodifiableSet(replicatedPersistedCache.keySet());
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
        return useRecovery ? unmarshallExchange(camelContext, replicatedPersistedCache.get(exchangeId)) : null;
    }

    @Override
    public Exchange get(CamelContext camelContext, String key) {
        return unmarshallExchange(camelContext, replicatedCache.get(key));
    }

    /**
     * Checks if the key in question is in the repository.
     *
     * @param key Object - key in question
     */
    @Override
    public boolean containsKey(Object key) {
        if (replicatedCache != null) {
            return replicatedCache.containsKey(key);
        } else {
            return false;
        }
    }

    /**
     * Removes the group of the key. With recovery, the given exchange is first stored in the persistent map for
     * recovery, under the cluster lock of the key that {@link #add(CamelContext, String, Exchange)} uses. A
     * {@code ReplicatedMap} cannot take part in a Hazelcast transaction, so the two writes are not atomic.
     *
     * @param camelContext the current CamelContext
     * @param key          the correlation key
     * @param exchange     the exchange to remove
     */
    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        if (useRecovery) {
            LOG.trace("Removing an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
            // a ReplicatedMap cannot take part in a Hazelcast transaction, so the completed exchange is stored for
            // recovery before the group is removed, under the lock that add() uses for the key.
            // The given exchange is stored and not the removed entry: when a group is completed by an incoming
            // exchange, the last aggregated exchange is not added to the repository before it is removed
            lockMap.lock(key);
            try {
                replicatedPersistedCache.put(exchange.getExchangeId(), holder);
                replicatedCache.remove(key);
            } finally {
                lockMap.unlock(key);
            }
            LOG.trace("Removed an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
        } else {
            replicatedCache.remove(key);
        }
    }

    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        LOG.trace("Confirming an exchange with ID {}.", exchangeId);
        if (useRecovery) {
            replicatedPersistedCache.remove(exchangeId);
        }
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(replicatedCache.keySet());
    }

    /**
     * Whether the aggregates are kept only in memory: a replicated map has a copy on every data member, so only a
     * member alone in its cluster loses them; see {@link HazelcastUtil#isReplicatedInMemory(HazelcastInstance)}.
     */
    @Override
    public boolean isInMemory() {
        return replicatedCache != null && HazelcastUtil.isReplicatedInMemory(hazelcastInstance);
    }

    @Override
    protected void doStart() throws Exception {
        if (optimistic) {
            throw new IllegalArgumentException(OPTIMISTIC_NOT_SUPPORTED);
        }
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }
        StringHelper.notEmpty(mapName, "repositoryName");
        if (hazelcastInstance == null) {
            useLocalHzInstance = true;
            Config cfg = new XmlConfigBuilder().build();
            cfg.setProperty("hazelcast.version.check.enabled", "false");
            HazelcastSerializationFilterHelper.applyDefault(cfg);
            hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
        } else {
            ObjectHelper.notNull(hazelcastInstance, "hazelcastInstance");
        }
        replicatedCache = hazelcastInstance.getReplicatedMap(mapName);
        // Create IMap for distributed locking (ReplicatedMap doesn't support lock operations)
        lockMap = hazelcastInstance.getMap(mapName + "-locks");
        if (useRecovery) {
            replicatedPersistedCache = hazelcastInstance.getReplicatedMap(persistenceMapName);
        }
    }

}
