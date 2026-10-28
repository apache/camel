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
package org.apache.camel.processor.keyvalue.kafka;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.processor.idempotent.kafka.KafkaChangelog;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.KeyValueRepositoryHelper;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Kafka topic-based implementation of {@link KeyValueRepository}. Uses a local cache backed by a Kafka topic as a
 * changelog for durable, distributed key-value storage.
 * <p/>
 * Each mutation ({@link #put}, {@link #delete}, {@link #clear}) updates the local cache immediately and broadcasts the
 * change to the Kafka topic. Other instances consuming the same topic will eventually see the update. On startup, the
 * instance consumes the full content of the topic to rebuild the cache to the latest state.
 * <p/>
 * The topic used must be unique per logical repository. TTL is managed locally via expiration timestamps in the cache;
 * expired entries are lazily evicted on access.
 * <p/>
 * <b>Note on TTL and topic growth:</b> TTL expiration is managed locally in the LRU cache. The Kafka topic retains all
 * entries (including expired ones) indefinitely. On restart, expired entries are replayed from the topic and
 * re-populated in the cache with their original expiration timestamps (they will appear as expired immediately). To
 * prevent unbounded topic growth, configure the topic with Kafka's built-in log compaction
 * ({@code cleanup.policy=compact}) and optionally {@code min.compaction.lag.ms} / {@code delete.retention.ms} to
 * control retention. Deleted entries (tombstones) are represented as records with a {@code DELETE} action byte, which
 * log compaction will eventually remove.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A Kafka topic-based KeyValueRepository. Uses a local cache backed by a Kafka topic as a changelog."
                        + " The topic must be unique per logical repository. On startup, the instance consumes the full content"
                        + " of the topic, rebuilding the cache to the latest state.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Kafka KeyValueRepository")
public class KafkaKeyValueRepository extends ServiceSupport implements KeyValueRepository, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaKeyValueRepository.class);

    private static final int DEFAULT_MAXIMUM_CACHE_SIZE = 1000;
    private static final int DEFAULT_POLL_DURATION_MS = 100;

    // Action bytes for the changelog protocol
    private static final byte ACTION_PUT = 0;
    private static final byte ACTION_DELETE = 1;
    private static final byte ACTION_CLEAR = 2;

    private CamelContext camelContext;
    private final AtomicLong cacheCounter = new AtomicLong();

    // internal state
    private Map<String, CacheEntry> cache;
    private KafkaChangelog<byte[]> changelog;

    @Metadata(description = "Custom properties for the Kafka consumer")
    private Properties consumerConfig;
    @Metadata(description = "Custom properties for the Kafka producer")
    private Properties producerConfig;

    @Metadata(description = "Sets the name of the Kafka topic used by this repository."
                            + " Each functionally-separate repository should use a different topic.",
              required = true)
    private String topic;
    @Metadata(description = "The URL for the kafka brokers to use", required = true)
    private String bootstrapServers;
    @Metadata(description = "A string that uniquely identifies the group of consumer processes to which this consumer belongs.")
    private String groupId;
    @Metadata(description = "Sets the maximum size of the local key cache.",
              defaultValue = "" + DEFAULT_MAXIMUM_CACHE_SIZE)
    private int maxCacheSize = DEFAULT_MAXIMUM_CACHE_SIZE;
    @Metadata(description = "Sets the poll duration of the Kafka consumer in milliseconds.",
              defaultValue = "" + DEFAULT_POLL_DURATION_MS)
    private int pollDurationMs = DEFAULT_POLL_DURATION_MS;
    @Metadata(description = "Whether to sync on startup only, or to continue syncing while Camel is running.")
    private boolean startupOnly;
    @Metadata(label = "advanced,security",
              description = "Sets an ObjectInputFilter pattern (jdk.serialFilter syntax) applied when deserializing"
                            + " values read back from the repository. When not set, the JVM-wide jdk.serialFilter is"
                            + " used if present; otherwise a conservative default filter denying java.net.* and"
                            + " otherwise allowing java.*, javax.* and org.apache.camel.* packages is applied. Widen"
                            + " this pattern when storing instances of your own classes in the repository.")
    private String deserializationFilter;

    public KafkaKeyValueRepository() {
    }

    public KafkaKeyValueRepository(String topic, String bootstrapServers) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
    }

    public KafkaKeyValueRepository(String topic, Properties consumerConfig, Properties producerConfig) {
        this.topic = topic;
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
    }

    // -------------------------------------------------------------------------
    // KeyValueRepository implementation
    // -------------------------------------------------------------------------

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            return null;
        }
        return entry.value;
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, Duration ttl) {
        long expiresAt = toExpiresAt(ttl);
        CacheEntry oldEntry = cache.put(key, new CacheEntry(value, expiresAt));
        Object oldValue = (oldEntry != null && !oldEntry.isExpired()) ? oldEntry.value : null;
        try {
            broadcastPut(key, value, expiresAt);
        } catch (Exception e) {
            // rollback the cache on broadcast failure
            if (oldEntry != null) {
                cache.put(key, oldEntry);
            } else {
                cache.remove(key);
            }
            throw e;
        }
        return oldValue;
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        CacheEntry oldEntry = cache.remove(key);
        Object oldValue = (oldEntry != null && !oldEntry.isExpired()) ? oldEntry.value : null;
        try {
            broadcastDelete(key);
        } catch (Exception e) {
            // rollback the cache on broadcast failure
            if (oldEntry != null) {
                cache.put(key, oldEntry);
            }
            throw e;
        }
        return oldValue;
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            return false;
        }
        return true;
    }

    @Override
    public Set<String> keys() {
        evictExpired();
        return cache.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        // Snapshot the cache before clearing so we can rollback on broadcast failure.
        // This is a shallow copy — sufficient for rollback since CacheEntry is immutable.
        Map<String, CacheEntry> snapshot = Map.copyOf(cache);
        cache.clear();
        try {
            broadcastClear();
        } catch (Exception e) {
            // rollback: restore previous cache entries
            cache.putAll(snapshot);
            throw e;
        }
    }

    /**
     * Associates {@code value} with {@code key} only if {@code key} is not already present (or its entry has expired).
     * Returns the existing (non-expired) value if one was already mapped, or {@code null} if the insertion succeeded.
     *
     * <p/>
     * <b>Note on atomicity:</b> The check-then-act sequence is locally atomic with respect to other threads in this JVM
     * (backed by {@link java.util.concurrent.ConcurrentMap#putIfAbsent}), but the subsequent broadcast to Kafka is a
     * separate, non-atomic step. In a multi-instance deployment, two nodes may each observe the key as absent, both
     * insert locally, and both broadcast — resulting in the last broadcast winning in the Kafka topic and propagating
     * to all other instances. This implementation therefore does <em>not</em> provide distributed compare-and-swap
     * (CAS) semantics; it is suitable only for best-effort deduplication within a single JVM or in scenarios where
     * occasional duplicates across instances are acceptable.
     *
     * @param  key   the key to insert
     * @param  value the value to associate
     * @param  ttl   optional time-to-live; {@code null} or zero means no expiration
     * @return       the existing value if already present and not expired, or {@code null} if the entry was inserted
     */
    @Override
    public Object putIfAbsent(String key, Object value, Duration ttl) {
        CacheEntry existing = cache.get(key);
        if (existing != null && !existing.isExpired()) {
            return existing.value;
        }
        // Remove expired entry if present
        if (existing != null) {
            cache.remove(key, existing);
        }
        long expiresAt = toExpiresAt(ttl);
        CacheEntry newEntry = new CacheEntry(value, expiresAt);
        CacheEntry prev = cache.putIfAbsent(key, newEntry);
        if (prev != null) {
            // Another thread beat us
            return prev.isExpired() ? null : prev.value;
        }
        try {
            broadcastPut(key, value, expiresAt);
        } catch (Exception e) {
            cache.remove(key, newEntry);
            throw e;
        }
        return null;
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        evictExpired();
        return cache.size();
    }

    // -------------------------------------------------------------------------
    // Broadcast methods
    // -------------------------------------------------------------------------

    private void broadcastPut(String key, Object value, long expiresAt) {
        byte[] payload = serializePutAction(value, expiresAt);
        broadcastToTopic(key, payload);
    }

    private void broadcastDelete(String key) {
        broadcastToTopic(key, new byte[] { ACTION_DELETE });
    }

    private void broadcastClear() {
        broadcastToTopic(null, new byte[] { ACTION_CLEAR });
    }

    private void broadcastToTopic(String key, byte[] payload) {
        LOG.debug("Broadcasting to topic {} for key {}", topic, key);
        ObjectHelper.notNull(changelog, "changelog");
        changelog.send(key, payload);
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    /**
     * Serializes a put action: [1 byte action=0][8 bytes expiresAt][serialized Object]
     */
    private byte[] serializePutAction(Object value, long expiresAt) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(ACTION_PUT);
            // Write expiresAt as 8 bytes big-endian
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.putLong(expiresAt);
            bos.write(buf.array());
            // Write serialized value
            bos.write(KeyValueRepositoryHelper.serialize(value));
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to serialize value for Kafka", e);
        }
    }

    private Object deserializeValue(byte[] data) {
        // Value starts at offset 9 (1 byte action + 8 bytes expiresAt)
        return KeyValueRepositoryHelper.deserialize(data, 9, data.length - 9, deserializationFilter);
    }

    private long deserializeExpiresAt(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data, 1, 8);
        return buf.getLong();
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    private void addToCache(ConsumerRecord<String, byte[]> record) {
        cacheCounter.incrementAndGet();
        byte[] data = record.value();
        if (data == null || data.length == 0) {
            return;
        }
        byte action = data[0];
        String key = record.key();
        if (action == ACTION_PUT) {
            if (data.length < 10) {
                LOG.warn("Malformed put record on topic:{}, partition:{}, offset:{}. Ignoring.",
                        record.topic(), record.partition(), record.offset());
                return;
            }
            long expiresAt = deserializeExpiresAt(data);
            Object value = deserializeValue(data);
            LOG.debug("Adding to cache key:{}", key);
            cache.put(key, new CacheEntry(value, expiresAt));
        } else if (action == ACTION_DELETE) {
            LOG.debug("Removing from cache key:{}", key);
            cache.remove(key);
        } else if (action == ACTION_CLEAR) {
            LOG.debug("Clearing cache");
            cache.clear();
        } else {
            LOG.warn("Unknown action byte:{} on topic:{}, partition:{}, offset:{}. Ignoring.",
                    action, record.topic(), record.partition(), record.offset());
        }
    }

    private static long toExpiresAt(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0;
        }
        return System.currentTimeMillis() + ttl.toMillis();
    }

    private void evictExpired() {
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext");
        StringHelper.notEmpty(topic, "topic");

        // The local cache is an LRU map: when the number of live keys exceeds maxCacheSize,
        // the least-recently-used entries are silently evicted from memory. get() and contains()
        // will report evicted keys as absent even though the value is still in the Kafka topic.
        // This matches the trade-off used by KafkaIdempotentRepository. Increase maxCacheSize
        // if you need a larger working set to be visible locally.
        this.cache = LRUCacheFactory.newLRUCache(maxCacheSize);

        consumerConfig = KafkaChangelog.consumerConfig(consumerConfig, bootstrapServers, groupId);
        producerConfig = KafkaChangelog.producerConfig(producerConfig, bootstrapServers);
        changelog = new KafkaChangelog<>(
                "KafkaKeyValueRepository", topic, consumerConfig, producerConfig, ByteArrayDeserializer.class,
                ByteArraySerializer.class, pollDurationMs,
                startupOnly, this::addToCache);
        changelog.start(camelContext, this);
    }

    @Override
    protected void doStop() throws Exception {
        if (changelog != null) {
            changelog.stop();
        }
        LOG.debug("Stopped KafkaKeyValueRepository. Cache counter: {}", cacheCounter.get());
    }

    // -------------------------------------------------------------------------
    // CacheEntry
    // -------------------------------------------------------------------------

    private static final class CacheEntry {
        final Object value;
        final long expiresAt;

        CacheEntry(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
        }
    }

    // -------------------------------------------------------------------------
    // CamelContextAware
    // -------------------------------------------------------------------------

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public String getTopic() {
        return topic;
    }

    /**
     * Sets the name of the Kafka topic used by this repository. Each functionally-separate repository should use a
     * different topic.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Sets the bootstrap.servers property on the internal Kafka producer and consumer.
     */
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public boolean isStartupOnly() {
        return startupOnly;
    }

    /**
     * Whether to sync on startup only, or to continue syncing while Camel is running.
     */
    public void setStartupOnly(boolean startupOnly) {
        this.startupOnly = startupOnly;
    }

    public Properties getProducerConfig() {
        return producerConfig;
    }

    /**
     * Sets the properties that will be used by the Kafka producer.
     */
    public void setProducerConfig(Properties producerConfig) {
        this.producerConfig = producerConfig;
    }

    public Properties getConsumerConfig() {
        return consumerConfig;
    }

    /**
     * Sets the properties that will be used by the Kafka consumer.
     */
    public void setConsumerConfig(Properties consumerConfig) {
        this.consumerConfig = consumerConfig;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Sets the maximum size of the local key cache.
     */
    public void setMaxCacheSize(int maxCacheSize) {
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException("maxCacheSize must be greater than 0, was: " + maxCacheSize);
        }
        this.maxCacheSize = maxCacheSize;
    }

    public int getPollDurationMs() {
        return pollDurationMs;
    }

    /**
     * Sets the poll duration of the Kafka consumer in milliseconds.
     */
    public void setPollDurationMs(int pollDurationMs) {
        this.pollDurationMs = pollDurationMs;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * A string that uniquely identifies the group of consumer processes to which this consumer belongs.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getDeserializationFilter() {
        return deserializationFilter;
    }

    /**
     * Sets an {@link java.io.ObjectInputFilter} pattern (same syntax as {@code jdk.serialFilter}) applied when
     * deserializing values read back from the repository. When not set, the JVM-wide {@code jdk.serialFilter} is used
     * if present, otherwise a conservative default filter is applied. Widen this pattern when storing instances of your
     * own classes in the repository.
     */
    public void setDeserializationFilter(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

    @ManagedOperation(description = "Number of sync events received from the kafka topic")
    public long getCacheCounter() {
        return cacheCounter.get();
    }

    @ManagedOperation(description = "Number of elements currently in the cache")
    public long getCacheSize() {
        return cache != null ? cache.size() : 0;
    }
}
