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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.CamelObjectInputStream;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.CompositeCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.apache.camel.spi.AggregationRepository} using Redis as store.
 * <p/>
 * The exchanges are stored in Redis hashes: the field is the correlation key (or the exchange id in the persistence
 * map) as a string, and the value is the {@link DefaultExchangeHolder} of the exchange in Java serialization. The
 * values are read back through an {@link java.io.ObjectInputFilter}, see {@link #setDeserializationFilter(String)}. The
 * codec configured on the {@link RedissonClient} is not used for these hashes, unless it is given with
 * {@link #setCodec(Codec)}, which replaces the Java serialization and its filter.
 */
@Metadata(label = "bean",
          description = "Aggregation repository that uses Redis to store exchanges.",
          annotations = { "interfaceName=org.apache.camel.spi.AggregationRepository" })
@Configurer(metadataOnly = true)
public class RedisAggregationRepository extends ServiceSupport
        implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(RedisAggregationRepository.class);
    private static final String COMPLETED_SUFFIX = "-completed";

    /**
     * The codec of the hashes: the fields are strings and the values are the serialized {@link DefaultExchangeHolder}.
     */
    private static final Codec CODEC
            = new CompositeCodec(StringCodec.INSTANCE, ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE);

    /**
     * The number of entries checked in each hash at startup, which is also the number read per HSCAN call.
     */
    private static final int SCAN_COUNT = 100;

    private RMap<String, Object> cache;
    /** The active hash with the values as the bytes stored in Redis, for the optimistic compares. */
    private RMap<String, Object> rawCache;
    private RMap<String, Object> persistedCache;
    private Codec mapCodec;
    /**
     * The ids of the completed exchanges that could not be read, to log the WARN once per exchange.
     */
    private final Set<String> unreadableExchangeIds = ConcurrentHashMap.newKeySet();
    private boolean shutdownRedisson;
    /**
     * Optimistic locking: the groups that {@link #get(CamelContext, String)} returned, each with its key and the value
     * that was read from Redis, to compare the stored group with. A {@link WeakHashMap} compares its keys with
     * {@code equals()}: the keys are the {@link DefaultExchange} instances that {@code get} creates, which do not
     * override it, so an entry is found only for the same exchange instance. Weak, so a group that is never updated or
     * removed does not stay.
     */
    private final Map<Exchange, ReadGroup> readGroups = Collections.synchronizedMap(new WeakHashMap<>());

    private record ReadGroup(String key, byte[] value) {
    }

    /**
     * Atomic Lua script for optimistic removal with recovery.
     *
     * <p>
     * Implements compare-and-swap by comparing the current value in the active map with the expected value, which is
     * the value that {@link #get(CamelContext, String)} read. If they match, the script writes the given exchange (with
     * the last aggregated message and the properties the aggregator set) to the recovery map and removes the group from
     * the active map atomically.
     *
     * <p>
     * Returns 1 on success and 0 if the active exchange does not match the expected value.
     *
     * <p>
     * The {@code HSET} into the recovery map is intentionally placed before the {@code HDEL} from the active map. Redis
     * does not roll back a Lua script on error; a failed {@code HSET} aborts the script before the {@code HDEL} runs,
     * leaving the exchange in the active map. This ordering ensures that a storage failure never results in exchange
     * loss.
     */
    private static final String OPTIMISTIC_REMOVE_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], ARGV[1])

            if not current or current ~= ARGV[2] then
                return 0
            end

            redis.call('HSET', KEYS[2], ARGV[3], ARGV[4])
            redis.call('HDEL', KEYS[1], ARGV[1])

            return 1
            """;

    @Metadata(label = "advanced", description = "To use an existing Redis client to connect to Redis server")
    private RedissonClient redisson;
    @Metadata(description = "URL to remote Redis server", required = true)
    private String endpoint;
    @Metadata(description = "Name of cache to use. On Redis Cluster with optimistic and useRecovery, "
                            + "this cache and the persistence cache must hash to the same slot. "
                            + "For example, use orders:{aggregation}.",
              required = true)
    private String mapName;

    @Metadata(label = "advanced",
              description = "Name of cache to use for completed exchanges. On Redis Cluster with optimistic "
                            + "and useRecovery, this cache and the active cache must hash to the same slot.")
    private String persistenceMapName;
    @Metadata(description = "Whether optimistic locking is in use")
    private boolean optimistic;
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
    @Metadata(label = "advanced,security",
              description = "Sets an ObjectInputFilter pattern (jdk.serialFilter syntax) applied when deserializing"
                            + " the exchanges read back from Redis. When not set, the JVM-wide jdk.serialFilter is"
                            + " used if present; otherwise a conservative default filter denying java.net.* and"
                            + " otherwise allowing java.*, javax.* and org.apache.camel.* packages is applied. Widen"
                            + " this pattern when the message bodies or headers stored in the repository are instances"
                            + " of your own classes.")
    private String deserializationFilter;
    @Metadata(label = "advanced,security", security = "insecure:serialization",
              description = "The Redisson codec of the Redis hashes of the repository, in place of the Java serialization"
                            + " read through the deserialization filter. When set, the codec encodes and decodes the"
                            + " exchanges, and the deserializationFilter option does not apply. For example, a"
                            + " org.redisson.codec.Kryo5Codec reads the exchanges stored by Camel 4.23 or older, which"
                            + " used the default codec of the Redisson client.")
    private Codec codec;

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
            Object holder = marshallHolder(newExchange);
            final Object misbehaviorHolder = cache.putIfAbsent(key, holder);
            if (misbehaviorHolder != null) {
                Exchange misbehaviorEx = unmarshallExchange(camelContext, mapName, key, misbehaviorHolder);
                LOG.warn(
                        "Optimistic locking failed for exchange with key {}: RMap#putIfAbsent returned Exchange with ID {}, while it's expected no exchanges to be returned",
                        key, misbehaviorEx != null ? misbehaviorEx.getExchangeId() : "<null>");
                throw new OptimisticLockingException();
            }
        } else {
            byte[] oldValue = readValue(key, oldExchange);
            byte[] newValue = encodeValue(marshallHolder(newExchange));
            if (!rawCache.replace(key, oldValue, newValue)) {
                LOG.warn(
                        "Optimistic locking failed for exchange with key {}: RMap#replace returned no Exchanges, while it's expected to replace one",
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
        Object newHolder = marshallHolder(exchange);
        RLock lock = redisson.getLock(mapName + "-lock-" + key);
        try {
            lock.lock();
            Object oldHolder = cache.put(key, newHolder);
            return unmarshallExchange(camelContext, mapName, key, oldHolder);
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
        if (!useRecovery) {
            return null;
        }
        try {
            return unmarshallExchange(camelContext, persistenceMapName, exchangeId, persistedCache.get(exchangeId));
        } catch (RuntimeException e) {
            // skip this exchange, so the recovery of the other exchanges goes on
            String reason;
            if (e.getCause() instanceof InvalidClassException) {
                reason = "rejected by the deserialization filter, see the deserializationFilter option";
            } else if (e instanceof RuntimeCamelException) {
                // the messages of this repository do not include the value
                reason = e.getMessage();
            } else {
                reason = e.getClass().getName();
            }
            if (unreadableExchangeIds.add(exchangeId)) {
                LOG.warn("Cannot read the completed exchange {} in the Redis hash {}, so it is not recovered: {}",
                        exchangeId, persistenceMapName, reason);
            } else {
                LOG.debug("Cannot read the completed exchange {} in the Redis hash {}, so it is not recovered: {}",
                        exchangeId, persistenceMapName, reason);
            }
            return null;
        }
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
    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
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

    /**
     * Gets the group of the correlation key.
     * <p>
     * With optimistic locking, {@link #add(CamelContext, String, Exchange, Exchange)} and
     * {@link #remove(CamelContext, String, Exchange)} compare the stored group with the bytes that this method read
     * from Redis, not with the exchange they are given serialized again: the Aggregate EIP sets properties on the group
     * before it removes it, and an aggregation strategy may change the group and return it. With a
     * {@link #setCodec(Codec) codec} too, the bytes are read as they are stored and kept for the compare, and the
     * exchange is made of the value that the codec decodes from them: a codec does not always encode a decoded value to
     * the same bytes (the order of a {@code HashSet} body can change, for example).
     */
    @Override
    public Exchange get(CamelContext camelContext, String key) {
        if (!optimistic) {
            return unmarshallExchange(camelContext, mapName, key, cache.get(key));
        }
        // the bytes stored in Redis, to compare with as they are; the exchange is made of an object decoded from them,
        // which nothing else references, so an aggregation strategy may change it in place
        byte[] read = (byte[]) rawCache.get(key);
        Object value = codec != null && read != null ? decode(read) : read;
        Exchange exchange = unmarshallExchange(camelContext, mapName, key, value);
        if (exchange != null) {
            readGroups.put(exchange, new ReadGroup(key, read));
        }
        return exchange;
    }

    /**
     * The value to compare the stored group with, for an optimistic update or remove of the group that the exchange
     * stands for: the bytes that {@link #get(CamelContext, String)} read for this key, or else the exchange as it is
     * now, encoded as it is stored.
     */
    private byte[] readValue(String key, Exchange exchange) {
        ReadGroup read = readGroups.remove(exchange);
        if (read != null && read.key().equals(key)) {
            return read.value();
        }
        return encodeValue(marshallHolder(exchange));
    }

    /**
     * The value that {@link #marshallHolder(Exchange)} made, as the bytes stored in Redis.
     */
    private byte[] encodeValue(Object holder) {
        return codec != null ? encode(mapCodec.getMapValueEncoder(), holder) : (byte[]) holder;
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

    public String getDeserializationFilter() {
        return deserializationFilter;
    }

    /**
     * Sets an {@link java.io.ObjectInputFilter} pattern (same syntax as {@code jdk.serialFilter}) applied when
     * deserializing the exchanges read back from Redis. When not set, the JVM-wide {@code jdk.serialFilter} is used if
     * present, otherwise a conservative default filter is applied, see
     * {@link org.apache.camel.support.DeserializationFilterHelper#DEFAULT_DESERIALIZATION_FILTER}. Widen this pattern
     * when the message bodies or headers stored in the repository are instances of your own classes.
     */
    public void setDeserializationFilter(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

    public Codec getCodec() {
        return codec;
    }

    /**
     * The Redisson codec of the Redis hashes of the repository, in place of the Java serialization read through the
     * deserialization filter. When set, the codec encodes and decodes the exchanges, and
     * {@link #setDeserializationFilter(String)} does not apply. For example, a {@code org.redisson.codec.Kryo5Codec}
     * reads the exchanges stored by Camel 4.23 or older, which used the default codec of the Redisson client.
     */
    public void setCodec(Codec codec) {
        this.codec = codec;
    }

    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        Object holder = marshallHolder(exchange);

        if (optimistic) {
            LOG.trace("Removing an exchange with ID {} for key {} in an optimistic manner.",
                    exchange.getExchangeId(), key);

            if (useRecovery) {
                LOG.trace("Atomically moving exchange with ID {} for key {} into recoverable storage.",
                        exchange.getExchangeId(), key);

                // the arguments are passed as bytes, encoded as the fields and values of the hashes
                RScript script = redisson.getScript(ByteArrayCodec.INSTANCE);

                Long result = script.eval(
                        RScript.Mode.READ_WRITE,
                        OPTIMISTIC_REMOVE_SCRIPT,
                        RScript.ReturnType.LONG,
                        List.of(mapName, persistenceMapName),
                        encode(mapCodec.getMapKeyEncoder(), key),
                        readValue(key, exchange),
                        encode(mapCodec.getMapKeyEncoder(), exchange.getExchangeId()),
                        encode(mapCodec.getMapValueEncoder(), holder));

                if (result == null || result.longValue() != 1L) {
                    LOG.warn(
                            "Optimistic locking failed for exchange with key {}: the active exchange does not match the expected exchange.",
                            key);

                    throw new OptimisticLockingException();
                }

                LOG.trace("Successfully moved exchange with ID {} for key {} into recoverable storage.",
                        exchange.getExchangeId(), key);

            } else {
                if (!rawCache.remove(key, readValue(key, exchange))) {
                    LOG.warn(
                            "Optimistic locking failed for exchange with key {}: RMap#remove removed no Exchanges, while it's expected to remove one.",
                            key);

                    throw new OptimisticLockingException();
                }
            }

            LOG.trace("Removed an exchange with ID {} for key {} in an optimistic manner.",
                    exchange.getExchangeId(), key);
        } else {
            if (useRecovery) {
                LOG.trace("Removing an exchange with ID {} for key {} in a thread-safe manner.", exchange.getExchangeId(), key);
                TransactionOptions tOpts = TransactionOptions.defaults();
                RTransaction transaction = redisson.createTransaction(tOpts);

                try {
                    RMap<String, Object> tCache = transaction.getMap(mapName, mapCodec);
                    RMap<String, Object> tPersistentCache = transaction.getMap(persistenceMapName, mapCodec);

                    tCache.remove(key);
                    LOG.trace("Putting an exchange with ID {} for key {} into a recoverable storage in a thread-safe manner.",
                            exchange.getExchangeId(), key);
                    // store the given exchange and not the removed entry: when a group is completed by an incoming
                    // exchange, the last aggregated exchange is not added to the repository before it is removed
                    tPersistentCache.put(exchange.getExchangeId(), holder);

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
                    throw new RuntimeCamelException(msg, throwable);
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
            unreadableExchangeIds.remove(exchangeId);
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
        if (redisson == null) {
            StringHelper.notEmpty(endpoint, "endpoint");
        }
        if (maximumRedeliveries < 0) {
            throw new IllegalArgumentException("Maximum redelivery retries must be zero or a positive integer.");
        }
        if (recoveryInterval < 0) {
            throw new IllegalArgumentException("Recovery interval must be zero or a positive integer.");
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (persistenceMapName == null) {
            persistenceMapName = String.format("%s%s", mapName, COMPLETED_SUFFIX);
        }

        if (redisson == null) {
            Config config = new Config();
            config.useSingleServer().setAddress(String.format("redis://%s", endpoint));
            redisson = Redisson.create(config);
            shutdownRedisson = true;
        }

        // Redis Cluster requires both keys used by the Lua script
        // to belong to the same hash slot.
        if (optimistic && useRecovery && redisson.getConfig().isClusterConfig()) {
            int activeSlot = redisson.getKeys().getSlot(mapName);
            int recoverySlot = redisson.getKeys().getSlot(persistenceMapName);

            if (activeSlot != recoverySlot) {
                throw new IllegalArgumentException(
                        "Redis Cluster requires mapName and persistenceMapName to use the same hash slot "
                                                   + "when optimistic removal with recovery is enabled. "
                                                   + "Configure both names with a common hash tag, for example: "
                                                   + "mapName=orders:{aggregation}, "
                                                   + "persistenceMapName=orders:{aggregation}-completed");
            }
        }

        unreadableExchangeIds.clear();
        mapCodec = codec != null ? codec : CODEC;
        cache = redisson.getMap(mapName, mapCodec);
        rawCache = codec != null
                ? redisson.getMap(mapName, new CompositeCodec(codec, ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE))
                : cache;
        if (codec == null) {
            warnUnreadableEntries(cache, mapName);
        }

        if (useRecovery) {
            persistedCache = redisson.getMap(persistenceMapName, mapCodec);
            if (codec == null) {
                warnUnreadableEntries(persistedCache, persistenceMapName);
            }
        }
    }

    /**
     * Logs a WARN when the hash has entries that are not serialized exchanges, such as the entries stored by an older
     * version of this repository, which are not read. Only the first entries of the hash, and the first bytes of their
     * values, are checked.
     */
    private void warnUnreadableEntries(RMap<String, Object> map, String name) {
        try {
            // only the first entries are checked, which is enough to spot a hash written by an older version
            int checked = 0;
            int count = 0;
            for (Map.Entry<String, Object> entry : map.entrySet(SCAN_COUNT)) {
                if (!isSerializedObject(entry.getValue())) {
                    count++;
                }
                if (++checked >= SCAN_COUNT) {
                    break;
                }
            }
            if (count > 0) {
                boolean partial = checked >= SCAN_COUNT && map.size() > checked;
                LOG.warn("The Redis hash {} has entries that are not exchanges serialized by this version of"
                         + " RedisAggregationRepository (count: {}{}). These entries are ignored: entries stored by an"
                         + " older Camel version are not read. See the camel-redis section of the Camel 4.24 upgrade"
                         + " guide.",
                        name, partial ? "at least " : "", count);
            }
        } catch (Exception e) {
            LOG.warn("Cannot check the entries of the Redis hash {}", name, e);
        }
    }

    /**
     * Whether the value starts with the magic number 0xACED of a Java serialization stream.
     */
    private static boolean isSerializedObject(Object value) {
        return value instanceof byte[] bytes && bytes.length >= 2 && bytes[0] == (byte) 0xAC && bytes[1] == (byte) 0xED;
    }

    /**
     * Encodes the value with the encoder of the codec of the hashes, as it is stored in Redis.
     */
    private static byte[] encode(Encoder encoder, Object value) {
        ByteBuf buf = null;
        try {
            buf = encoder.encode(value);
            byte[] answer = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), answer);
            return answer;
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot encode the value to compare it in Redis", e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    /**
     * Decodes the value with the decoder of the codec of the hashes, as it is read from Redis.
     */
    private Object decode(byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            return mapCodec.getMapValueDecoder().decode(buf, new State());
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot decode the value read from Redis", e);
        } finally {
            buf.release();
        }
    }

    @Override
    protected void doStop() throws Exception {
        readGroups.clear();
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

    /**
     * The {@link DefaultExchangeHolder} of the exchange, as stored in the Redis hashes: in Java serialization, or the
     * holder itself when a {@link #setCodec(Codec) codec} is set.
     */
    protected Object marshallHolder(Exchange exchange) {
        DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange, true, allowSerializedHeaders);
        if (codec != null) {
            return holder;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(holder);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeCamelException(
                    "Cannot serialize the exchange with ID " + exchange.getExchangeId() + " to store it in Redis", e);
        }
    }

    /**
     * Turns a value read from Redis into an exchange. Without a {@link #setCodec(Codec) codec}, the value is
     * deserialized through the deserialization filter.
     *
     * @param  camelContext the camel context
     * @param  name         the name of the Redis hash the value was read from
     * @param  key          the field of the value in the hash
     * @param  value        the value, may be null
     * @return              the exchange, or null when the value is null
     */
    protected Exchange unmarshallExchange(CamelContext camelContext, String name, String key, Object value) {
        if (value == null) {
            return null;
        }
        if (codec != null) {
            if (!(value instanceof DefaultExchangeHolder holder)) {
                throw new RuntimeCamelException(
                        "The value of " + key + " in the Redis hash " + name + " is not an exchange but "
                                                + value.getClass().getName());
            }
            return unmarshallExchange(camelContext, holder);
        }
        if (!isSerializedObject(value)) {
            throw new RuntimeCamelException(
                    "The value of " + key + " in the Redis hash " + name + " is not a serialized exchange");
        }
        Object answer;
        try (ObjectInputStream in
                = new CamelObjectInputStream(new ByteArrayInputStream((byte[]) value), camelContext, deserializationFilter)) {
            answer = in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException(
                    "Cannot deserialize the exchange of " + key + " in the Redis hash " + name, e);
        }
        if (!(answer instanceof DefaultExchangeHolder holder)) {
            throw new RuntimeCamelException(
                    "The value of " + key + " in the Redis hash " + name + " is not a serialized exchange but "
                                            + (answer != null ? answer.getClass().getName() : "null"));
        }
        return unmarshallExchange(camelContext, holder);
    }
}
