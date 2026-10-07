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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

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
        LOG.debug("Stopped KafkaIdempotentRepository. Cache counter: {}", cacheCounter.get());
    }

    private void addToCache(ConsumerRecord<String, String> consumerRecord) {
        cacheCounter.incrementAndGet();
        CacheAction action;
        try {
            action = CacheAction.valueOf(consumerRecord.value());
            String messageId = consumerRecord.key();
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
                    consumerRecord.key(), consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset());
        }
    }

    @Override
    public boolean add(String key) {
        if (cache.putIfAbsent(key, key) != null) {
            return false;
        }
        try {
            broadcastAction(key, CacheAction.add);
            return true;
        } catch (Exception e) {
            cache.remove(key, key);
            throw e;
        }
    }

    private void broadcastAction(String key, CacheAction action) {
        LOG.debug("Broadcasting action:{} for key:{}", action, key);
        ObjectHelper.notNull(changelog, "changelog");
        changelog.send(key, action.toString());
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        // update the local cache and broadcast the addition on the topic, which
        // will be reflected
        // at a later point in any peers
        cache.remove(key, key);
        broadcastAction(key, CacheAction.remove);
        return true;
    }

    @Override
    public boolean confirm(String key) {
        return true; // no-op
    }

    @Override
    public void clear() {
        cache.clear();
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
}
