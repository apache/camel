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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
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
 * must be placed in the Camel registry, either manually or by registration as a bean in Spring/Blueprint, as it is
 * CamelContext aware.
 */
@ManagedResource(description = "Kafka IdempotentRepository")
public class KafkaIdempotentRepository extends ServiceSupport implements IdempotentRepository, CamelContextAware {

    private static final int DEFAULT_MAXIMUM_CACHE_SIZE = 1000;
    private static final int DEFAULT_POLL_DURATION_MS = 100;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    // configurable
    private String topic;
    private String bootstrapServers;

    private String groupId;
    private Properties producerConfig;
    private Properties consumerConfig;
    private int maxCacheSize = DEFAULT_MAXIMUM_CACHE_SIZE;
    private int pollDurationMs = DEFAULT_POLL_DURATION_MS;

    // internal properties
    private Map<String, Object> cache;
    private Consumer<String, String> consumer;
    private Producer<String, String> producer;

    private CamelContext camelContext;

    enum CacheAction {
        add,
        remove,
        clear
    }

    /**
     * No-op constructor for XML/property-based object initialisation. From Java, prefer one of the other constructors.
     */
    public KafkaIdempotentRepository() {
    }

    public KafkaIdempotentRepository(String topic, String bootstrapServers) {
        this(topic, bootstrapServers, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_POLL_DURATION_MS);
    }

    /**
     * @deprecated Use the constructor without groupId; the parameter groupId is ignored.
     */
    @Deprecated
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

    /**
     * @deprecated Use the constructor without groupId; the parameter groupId is ignored.
     */
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

    /**
     * @deprecated Use the constructor without groupId; the parameter groupId is ignored.
     */
    @Deprecated
    public KafkaIdempotentRepository(String topic, String bootstrapServers, int maxCacheSize, int pollDurationMs,
                                     String groupId) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
        this.groupId = groupId;
    }

    /**
     * @deprecated Use the constructor without groupId; the parameter groupId is ignored.
     */
    @Deprecated
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
     * Sets the
     *
     * <pre>
     * bootstrap.servers
     * </pre>
     *
     * property on the internal Kafka producer and consumer. Use this as shorthand if not setting
     * {@link #consumerConfig} and {@link #producerConfig}. If used, this component will apply sensible default
     * configurations for the producer and consumer.
     *
     * @param bootstrapServers The
     *
     *                         <pre>
     *                         bootstrap.servers
     *                         </pre>
     *
     *                         value to use.
     */
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
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
     *
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
     *
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

    /**
     * @deprecated The parameter groupId is ignored.
     */
    @Deprecated
    public String getGroupId() {
        return groupId;
    }

    /**
     * Sets the group id of the Kafka consumer.
     *
     * @param      groupId The poll duration in milliseconds.
     * @deprecated         The parameter groupId is ignored.
     */
    @Deprecated
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

        if (consumerConfig == null) {
            consumerConfig = new Properties();
            StringHelper.notEmpty(bootstrapServers, "bootstrapServers");
            consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        if (producerConfig == null) {
            producerConfig = new Properties();
            StringHelper.notEmpty(bootstrapServers, "bootstrapServers");
            producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        ObjectHelper.notNull(consumerConfig, "consumerConfig");
        ObjectHelper.notNull(producerConfig, "producerConfig");

        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE.toString());
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        consumer = new KafkaConsumer<>(consumerConfig);

        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // set up the producer to remove all batching on send, we want all sends
        // to be fully synchronous
        producerConfig.putIfAbsent(ProducerConfig.ACKS_CONFIG, "1");
        producerConfig.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, "0");
        producer = new KafkaProducer<>(producerConfig);

        populateCache();
    }

    private void populateCache() {
        log.debug("Getting partitions of topic {}", topic);
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        Collection<TopicPartition> partitions = partitionInfos.stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .collect(Collectors.toUnmodifiableList());

        log.debug("Assigning consumer to partitions {}", partitions);
        consumer.assign(partitions);

        log.debug("Seeking consumer to beginning of partitions {}", partitions);
        consumer.seekToBeginning(partitions);

        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
        log.debug("Consuming records from partitions {} till end offsets {}", partitions, endOffsets);
        while (!KafkaConsumerUtil.isReachedOffsets(consumer, endOffsets)) {
            ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(pollDurationMs));
            for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                addToCache(consumerRecord);
            }
        }

    }

    private void addToCache(ConsumerRecord<String, String> consumerRecord) {
        CacheAction action = null;
        try {
            action = CacheAction.valueOf(consumerRecord.value());
        } catch (IllegalArgumentException iax) {
            log.error(
                    "Unexpected action value:\"{}\" received on [topic:{}, partition:{}, offset:{}]. Shutting down.",
                    consumerRecord.key(), consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset());
        }
        String messageId = consumerRecord.key();
        if (action == CacheAction.add) {
            log.debug("Adding to cache messageId:{}", messageId);
            cache.put(messageId, messageId);
        } else if (action == CacheAction.remove) {
            log.debug("Removing from cache messageId:{}", messageId);
            cache.remove(messageId);
        } else if (action == CacheAction.clear) {
            cache.clear();
        } else {
            // this should never happen
            throw new RuntimeException("Illegal action " + action + " for key " + consumerRecord.key());
        }
    }

    @Override
    protected void doStop() {
        IOHelper.close(consumer, "consumer", log);
        IOHelper.close(producer, "producer", log);
    }

    @Override
    public boolean add(String key) {
        if (cache.containsKey(key)) {
            return false;
        } else {
            // update the local cache and broadcast the addition on the topic,
            // which will be reflected
            // at a later point in any peers
            cache.put(key, key);
            broadcastAction(key, CacheAction.add);
            return true;
        }
    }

    private void broadcastAction(String key, CacheAction action) {
        try {
            log.debug("Broadcasting action:{} for key:{}", action, key);
            ObjectHelper.notNull(producer, "producer");

            producer.send(new ProducerRecord<>(topic, key, action.toString())).get(); // sync send
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeCamelException(e);
        } catch (ExecutionException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        log.debug("Checking cache for key:{}", key);
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
        broadcastAction(null, CacheAction.clear);
    }
}
