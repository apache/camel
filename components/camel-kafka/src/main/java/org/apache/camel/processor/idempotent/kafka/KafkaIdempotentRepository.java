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
package org.apache.camel.processor.idempotent.kafka;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Kafka topic-based implementation of {@link org.apache.camel.spi.IdempotentRepository}. Uses a local cache of
 * previously seen Message IDs. Mutations that come in via the ({@link #add(String)}), or {@link #remove(String)} method
 * will update the local cache and broadcast the change in state on a Kafka topic to other instances. The cache is
 * back-filled from the topic by a Kafka consumer. The topic used must be unique per logical repository (i.e. two routes
 * de-duplicate using different repositories, and different topics). This class makes no assumptions about the number of
 * partitions (it is designed to consume from all at the same time), or replication factor of the topic. Each repository
 * instance that uses the topic (e.g. typically on different machines running in parallel) controls its own consumer
 * group, so in a cluster of 10 Camel processes using the same topic each will control its own offset. On startup, the
 * instance consumes the full content of the topic, rebuilding the cache to the latest state. To use, this repository
 * must be placed in the Camel registry.
 */
@Metadata(label = "bean",
          description = "Idempotent repository that uses Kafka to store message ids. Uses a local cache of previously seen Message IDs."
                        + " The topic used must be unique per logical repository (i.e. two routes de-duplicate using different repositories, and different topics)"
                        + " On startup, the instance consumes the full content of the topic, rebuilding the cache to the latest state.",
          annotations = { "interfaceName=org.apache.camel.spi.IdempotentRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Kafka IdempotentRepository")
public class KafkaIdempotentRepository extends ServiceSupport implements IdempotentRepository, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaIdempotentRepository.class);

    private static final int DEFAULT_MAXIMUM_CACHE_SIZE = 1000;
    private static final int DEFAULT_POLL_DURATION_MS = 100;

    private CamelContext camelContext;
    private final AtomicLong cacheCounter = new AtomicLong();
    // internal properties
    private Map<String, Object> cache;
    private KafkaChangelog<String> changelog;
    // the writes of this instance that the poller has not read back from the topic yet, by key: an older record of the
    // key that the poller reads after the local change must not undo it. At most maxCacheSize writes are kept, in case
    // the poller never reads a record back (a partition added after the start is not read)
    private final Map<String, LocalWrite> localWrites = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LocalWrite> eldest) {
            return size() > maxCacheSize && !eldest.getValue().sending;
        }
    };
    // guards the local changes of the cache against the records applied by the poller, never held while sending
    private final Lock lock = new ReentrantLock();

    private Properties producerConfig;
    private Properties consumerConfig;

    // configurable
    @Metadata(description = "Sets the name of the Kafka topic used by this idempotent repository."
                            + " Each functionally-separate repository should use a different topic.",
              required = true)
    private String topic;
    @Metadata(description = "The URL for the kafka brokers to use", required = true)
    private String bootstrapServers;
    @Metadata(description = "A string that uniquely identifies the group of consumer processes to which this consumer belongs. By setting the"
                            + " same group id, multiple processes can indicate that they are all part of the same consumer group.")
    private String groupId;
    @Metadata(description = "Sets the maximum size of the local key cache. The value must be greater than 0.",
              defaultValue = "" + DEFAULT_MAXIMUM_CACHE_SIZE)
    private int maxCacheSize = DEFAULT_MAXIMUM_CACHE_SIZE;
    @Metadata(description = "Sets the poll duration of the Kafka consumer. The local caches are updated immediately; this value will affect"
                            + " how far behind other peers in the cluster are, which are updating their caches from the topic, relative to the"
                            + " idempotent consumer instance issued the cache action message. The default value of this is 100"
                            + " If setting this value explicitly, be aware that there is a tradeoff between"
                            + " the remote cache liveness and the volume of network traffic between this repository's consumer and the Kafka"
                            + " brokers. The cache warmup process also depends on there being one poll that fetches nothing - this indicates that"
                            + " the stream has been consumed up to the current point. If the poll duration is excessively long for the rate at"
                            + " which messages are sent on the topic, there exists a possibility that the cache cannot be warmed up and will"
                            + " operate in an inconsistent state relative to its peers until it catches up.",
              defaultValue = "" + DEFAULT_POLL_DURATION_MS)
    private int pollDurationMs = DEFAULT_POLL_DURATION_MS;
    @Metadata(description = "Whether to sync on startup only, or to continue syncing while Camel is running.")
    private boolean startupOnly;

    enum CacheAction {
        add,
        remove,
        clear
    }

    public KafkaIdempotentRepository() {
    }

    public KafkaIdempotentRepository(String topic, String bootstrapServers) {
        this(topic, bootstrapServers, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_POLL_DURATION_MS);
    }

    public KafkaIdempotentRepository(String topic, String bootstrapServers, String groupId) {
        this(topic, bootstrapServers, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_POLL_DURATION_MS, groupId);
    }

    public KafkaIdempotentRepository(String topic, String bootstrapServers, int maxCacheSize, int pollDurationMs) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
    }

    public KafkaIdempotentRepository(String topic, Properties consumerConfig, Properties producerConfig) {
        this(topic, consumerConfig, producerConfig, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_POLL_DURATION_MS);
    }

    @Deprecated
    public KafkaIdempotentRepository(String topic, Properties consumerConfig, Properties producerConfig, String groupId) {
        this(topic, consumerConfig, producerConfig, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_POLL_DURATION_MS, groupId);
    }

    public KafkaIdempotentRepository(String topic, Properties consumerConfig, Properties producerConfig, int maxCacheSize,
                                     int pollDurationMs) {
        this.topic = topic;
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
    }

    public KafkaIdempotentRepository(String topic, String bootstrapServers, int maxCacheSize, int pollDurationMs,
                                     String groupId) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
        this.groupId = groupId;
    }

    public KafkaIdempotentRepository(String topic, Properties consumerConfig, Properties producerConfig, int maxCacheSize,
                                     int pollDurationMs, String groupId) {
        this.topic = topic;
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
        this.groupId = groupId;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Sets the name of the Kafka topic used by this idempotent repository. Each functionally-separate repository should
     * use a different topic.
     *
     * @param topic The topic name.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Sets the bootstrap.servers property on the internal Kafka producer and consumer. Use this as shorthand if not
     * setting {@link #consumerConfig} and {@link #producerConfig}. If used, this component will apply sensible default
     * configurations for the producer and consumer.
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
     * Sets the properties that will be used by the Kafka producer. Overrides {@link #bootstrapServers}, so must define
     * the
     *
     * <pre>
     * bootstrap.servers
     * </pre>
     * <p>
     * property itself. Prefer using {@link #bootstrapServers} for default configuration unless you specifically need
     * non-standard configuration options such as SSL/SASL.
     *
     * @param producerConfig The producer configuration properties.
     */
    public void setProducerConfig(Properties producerConfig) {
        this.producerConfig = producerConfig;
    }

    public Properties getConsumerConfig() {
        return consumerConfig;
    }

    /**
     * Sets the properties that will be used by the Kafka consumer. Overrides {@link #bootstrapServers}, so must define
     * the
     *
     * <pre>
     * bootstrap.servers
     * </pre>
     * <p>
     * property itself. Prefer using {@link #bootstrapServers} for default configuration unless you specifically need
     * non-standard configuration options such as SSL/SASL.
     *
     * @param consumerConfig The consumer configuration properties.
     */
    public void setConsumerConfig(Properties consumerConfig) {
        this.consumerConfig = consumerConfig;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Sets the maximum size of the local key cache.
     *
     * @param maxCacheSize The maximum key cache size.
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
     * Sets the poll duration of the Kafka consumer. The local caches are updated immediately; this value will affect
     * how far behind other peers in the cluster are, which are updating their caches from the topic, relative to the
     * idempotent consumer instance issued the cache action message. The default value of this is
     * {@link #DEFAULT_POLL_DURATION_MS}. If setting this value explicitly, be aware that there is a tradeoff between
     * the remote cache liveness and the volume of network traffic between this repository's consumer and the Kafka
     * brokers. The cache warmup process also depends on there being one poll that fetches nothing - this indicates that
     * the stream has been consumed up to the current point. If the poll duration is excessively long for the rate at
     * which messages are sent on the topic, there exists a possibility that the cache cannot be warmed up and will
     * operate in an inconsistent state relative to its peers until it catches up.
     *
     * @param pollDurationMs The poll duration in milliseconds.
     */
    public void setPollDurationMs(int pollDurationMs) {
        this.pollDurationMs = pollDurationMs;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * A string that uniquely identifies the group of consumer processes to which this consumer belongs. By setting the
     * same group id, multiple processes can indicate that they are all part of the same consumer group.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext");
        StringHelper.notEmpty(topic, "topic");

        this.cache = LRUCacheFactory.newLRUCache(maxCacheSize);

        consumerConfig = KafkaChangelog.consumerConfig(consumerConfig, bootstrapServers, groupId);
        producerConfig = KafkaChangelog.producerConfig(producerConfig, bootstrapServers);
        changelog = new KafkaChangelog<>(
                "KafkaIdempotentRepository", topic, consumerConfig, producerConfig, StringDeserializer.class,
                StringSerializer.class, pollDurationMs,
                startupOnly, this::addToCache);
        changelog.start(camelContext, this);
    }

    @Override
    protected void doStop() throws Exception {
        if (changelog != null) {
            changelog.stop();
        }
        lock.lock();
        try {
            localWrites.clear();
        } finally {
            lock.unlock();
        }
        LOG.debug("Stopped KafkaIdempotentRepository. Cache counter: {}", cacheCounter.get());
    }

    private void addToCache(ConsumerRecord<String, String> consumerRecord) {
        cacheCounter.incrementAndGet();
        lock.lock();
        try {
            String key = consumerRecord.key();
            LocalWrite write = key != null ? localWrites.get(key) : null;
            if (write != null) {
                if (write.sending) {
                    // the record cannot be ordered against the write yet: decided once its offset is known
                    write.held.add(consumerRecord);
                    return;
                }
                if (!isAfter(key, write, consumerRecord)) {
                    return;
                }
            }
            applyRecord(consumerRecord);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Starts a write of this instance to the topic, called under the lock together with the change of the local cache.
     */
    private LocalWrite startWrite(String key) {
        if (startupOnly) {
            // no record is applied after the start, so none can undo the write
            return null;
        }
        LocalWrite write = new LocalWrite();
        LocalWrite previous = localWrites.put(key, write);
        if (previous != null && previous.sending) {
            // the records held by an earlier write of the key that is still sent are decided by this later write
            write.held.addAll(previous.held);
            previous.held.clear();
        }
        return write;
    }

    /**
     * Completes a write of this instance, and decides the records of its key that the poller read while it was sent.
     *
     * @param metadata the partition and offset of the record, or null when the send failed
     */
    private void completeWrite(String key, LocalWrite write, RecordMetadata metadata) {
        if (write == null) {
            return;
        }
        lock.lock();
        try {
            write.sending = false;
            if (localWrites.get(key) != write) {
                // a later write of the key took over the held records
                return;
            }
            if (metadata == null || !metadata.hasOffset()) {
                // the records cannot be ordered against the write: apply them as they come
                localWrites.remove(key);
                write.held.forEach(this::applyRecord);
            } else {
                write.partition = metadata.partition();
                write.offset = metadata.offset();
                for (ConsumerRecord<String, String> consumerRecord : write.held) {
                    if (isAfter(key, write, consumerRecord)) {
                        applyRecord(consumerRecord);
                    }
                }
            }
            write.held.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the record of the key was written after the given write of this instance, so it is applied. A record
     * written before it, or the record of the write itself, would undo the local change, and is skipped.
     */
    private boolean isAfter(String key, LocalWrite write, ConsumerRecord<String, String> consumerRecord) {
        if (consumerRecord.partition() != write.partition) {
            // records of different partitions cannot be ordered (the docs recommend a topic with one partition)
            return true;
        }
        if (consumerRecord.offset() == write.offset) {
            // the record of the write is read back: the later records of the key are applied
            localWrites.remove(key, write);
            return false;
        }
        if (consumerRecord.offset() < write.offset) {
            LOG.debug("Skipping record of messageId:{} at offset:{} written before the local change at offset:{}",
                    key, consumerRecord.offset(), write.offset);
            return false;
        }
        return true;
    }

    private void applyRecord(ConsumerRecord<String, String> consumerRecord) {
        String value = consumerRecord.value();
        String messageId = consumerRecord.key();
        // a record without a value (a tombstone) or an add or remove without a key is not written by this repository:
        // skip it, so the records after it in the same poll are still applied, and the start does not fail
        if (value == null || (messageId == null && !CacheAction.clear.name().equals(value))) {
            LOG.warn(
                    "Unexpected record with key:\"{}\" and value:\"{}\" received on [topic:{}, partition:{}, offset:{}]. Ignoring.",
                    messageId, value, consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset());
            return;
        }
        CacheAction action;
        try {
            action = CacheAction.valueOf(value);
            if (action == CacheAction.add) {
                LOG.debug("Adding to cache messageId:{}", messageId);
                cache.put(messageId, messageId);
            } else if (action == CacheAction.remove) {
                LOG.debug("Removing from cache messageId:{}", messageId);
                cache.remove(messageId);
            } else if (action == CacheAction.clear) {
                cache.clear();
            } else {
                throw new IllegalArgumentException("Unknown action");
            }
        } catch (IllegalArgumentException iax) {
            LOG.warn(
                    "Unexpected action value:\"{}\" received on [topic:{}, partition:{}, offset:{}]. Ignoring.",
                    value, consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset());
        }
    }

    @Override
    public boolean add(String key) {
        // the poller stores key -> key for an add of a peer, so roll back only the value put by this call
        Object token = new Object();
        LocalWrite write;
        lock.lock();
        try {
            if (cache.putIfAbsent(key, token) != null) {
                return false;
            }
            write = startWrite(key);
        } finally {
            lock.unlock();
        }
        RecordMetadata metadata;
        try {
            metadata = broadcastAction(key, CacheAction.add);
        } catch (Exception e) {
            lock.lock();
            try {
                cache.remove(key, token);
                completeWrite(key, write, null);
            } finally {
                lock.unlock();
            }
            throw e;
        }
        completeWrite(key, write, metadata);
        return true;
    }

    private RecordMetadata broadcastAction(String key, CacheAction action) {
        LOG.debug("Broadcasting action:{} for key:{}", action, key);
        ObjectHelper.notNull(changelog, "changelog");
        return changelog.send(key, action.toString());
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        // update the local cache and broadcast the removal on the topic, which
        // will be reflected
        // at a later point in any peers
        LocalWrite write;
        lock.lock();
        try {
            cache.remove(key);
            write = startWrite(key);
        } finally {
            lock.unlock();
        }
        RecordMetadata metadata = null;
        try {
            metadata = broadcastAction(key, CacheAction.remove);
        } finally {
            completeWrite(key, write, metadata);
        }
        return true;
    }

    @Override
    public boolean confirm(String key) {
        return true; // no-op
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            cache.clear();
        } finally {
            lock.unlock();
        }
        broadcastAction(null, CacheAction.clear);
    }

    @ManagedOperation(description = "Number of sync events received from the kafka topic")
    public long getCacheCounter() {
        return cacheCounter.get();
    }

    @ManagedOperation(description = "Number of elements currently in the cache")
    public long getCacheSize() {
        return cache != null ? cache.size() : 0;
    }

    /**
     * A write of this instance to the topic that the background poller has not read back yet.
     */
    private static final class LocalWrite {
        private boolean sending = true;
        private int partition = -1;
        private long offset = -1;
        // the records of the key that the poller read while the write was sent
        private final List<ConsumerRecord<String, String>> held = new ArrayList<>();
    }
}
