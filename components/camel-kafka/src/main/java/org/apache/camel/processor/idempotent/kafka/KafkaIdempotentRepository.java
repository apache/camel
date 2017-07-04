/**
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

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Kafka topic-based implementation of {@link org.apache.camel.spi.IdempotentRepository}.
 *
 * Uses a local cache of previously seen Message IDs. Mutations that come in via the ({@link #add(String)}), or
 * {@link #remove(String)} method will update the local cache and broadcast the change in state on a Kafka topic to
 * other instances. The cache is back-filled from the topic by a Kafka consumer.
 *
 * The topic used must be unique per logical repository (i.e. two routes de-duplicate using different repositories,
 * and different topics).
 *
 * This class makes no assumptions about the number of partitions (it is designed to consume from all at the
 * same time), or replication factor of the topic.
 *
 * Each repository instance that uses the topic (e.g. typically on different machines running in parallel) controls its own
 * consumer group, so in a cluster of 10 Camel processes using the same topic each will control its own offset.
 *
 * On startup, the instance subscribes to the topic and rewinds the offset to the beginning, rebuilding the cache to the
 * latest state. The cache will not be considered warmed up until one poll of {@link #pollDurationMs} in length
 * returns 0 records. Startup will not be completed until either the cache has warmed up, or 30 seconds go by; if the
 * latter happens the idempotent repository may be in an inconsistent state until its consumer catches up to the end
 * of the topic.
 *
 * To use, this repository must be placed in the Camel registry, either manually or by registration as a bean in
 * Spring/Blueprint, as it is CamelContext aware.
 */
@ManagedResource(description = "Kafka IdempotentRepository")
public class KafkaIdempotentRepository extends ServiceSupport implements IdempotentRepository<String>, CamelContextAware {

    private static final int DEFAULT_MAXIMUM_CACHE_SIZE = 1000;
    private static final int DEFAULT_POLL_DURATION_MS = 100;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AtomicLong duplicateCount = new AtomicLong(0);

    // configurable
    private String topic;
    private String bootstrapServers;
    private Properties producerConfig;
    private Properties consumerConfig;
    private int maxCacheSize = DEFAULT_MAXIMUM_CACHE_SIZE;
    private int pollDurationMs = DEFAULT_POLL_DURATION_MS;

    // internal properties
    private Map<String, Object> cache;
    private Consumer<String, String> consumer;
    private Producer<String, String> producer;
    private TopicPoller topicPoller;

    private CamelContext camelContext;
    private ExecutorService executorService;
    private CountDownLatch cacheReadyLatch;

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

    public KafkaIdempotentRepository(String topic, String bootstrapServers, int maxCacheSize, int pollDurationMs) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
    }

    public KafkaIdempotentRepository(String topic, Properties consumerConfig, Properties producerConfig) {
        this(topic, consumerConfig, producerConfig, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_POLL_DURATION_MS);
    }

    public KafkaIdempotentRepository(String topic, Properties consumerConfig, Properties producerConfig, int maxCacheSize, int pollDurationMs) {
        this.topic = topic;
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
        this.maxCacheSize = maxCacheSize;
        this.pollDurationMs = pollDurationMs;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Sets the name of the Kafka topic used by this idempotent repository. Each functionally-separate repository
     * should use a different topic.
     * @param topic The topic name.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Sets the <pre>bootstrap.servers</pre> property on the internal Kafka producer and consumer. Use this as shorthand
     * if not setting {@link #consumerConfig} and {@link #producerConfig}. If used, this component will apply sensible
     * default configurations for the producer and consumer.
     * @param bootstrapServers The <pre>bootstrap.servers</pre> value to use.
     */
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Properties getProducerConfig() {
        return producerConfig;
    }

    /**
     * Sets the properties that will be used by the Kafka producer. Overrides {@link #bootstrapServers}, so must define
     * the <pre>bootstrap.servers</pre> property itself.
     *
     * Prefer using {@link #bootstrapServers} for default configuration unless you specifically need non-standard
     * configuration options such as SSL/SASL.
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
     * the <pre>bootstrap.servers</pre> property itself.
     *
     * Prefer using {@link #bootstrapServers} for default configuration unless you specifically need non-standard
     * configuration options such as SSL/SASL.
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
     * idempotent consumer instance issued the cache action message.
     *
     * The default value of this is {@link #DEFAULT_POLL_DURATION_MS}. If setting this value explicitly, be aware that
     * there is a tradeoff between the remote cache liveness and the volume of network traffic between this repository's
     * consumer and the Kafka brokers.
     *
     * The cache warmup process also depends on there being one poll that fetches nothing - this indicates that the
     * stream has been consumed up to the current point. If the poll duration is excessively long for the rate at
     * which messages are sent on the topic, there exists a possibility that the cache cannot be warmed up and will
     * operate in an inconsistent state relative to its peers until it catches up.
     * @param pollDurationMs The poll duration in milliseconds.
     */
    public void setPollDurationMs(int pollDurationMs) {
        this.pollDurationMs = pollDurationMs;
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
    @SuppressWarnings("unchecked")
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

        // each consumer instance must have control over its own offset, so assign a groupID at random
        String groupId = UUID.randomUUID().toString();
        log.debug("Creating consumer with {}[{}]", ConsumerConfig.GROUP_ID_CONFIG, groupId);

        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE.toString());
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        consumer = new KafkaConsumer<>(consumerConfig);

        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // set up the producer to remove all batching on send, we want all sends to be fully synchronous
        producerConfig.putIfAbsent(ProducerConfig.ACKS_CONFIG, "1");
        producerConfig.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, "0");
        producer = new KafkaProducer<>(producerConfig);

        cacheReadyLatch = new CountDownLatch(1);
        topicPoller = new TopicPoller(consumer, cacheReadyLatch, pollDurationMs);

        // warm up the cache
        executorService = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, "KafkaIdempotentRepository");
        executorService.submit(topicPoller);
        log.info("Warming up cache from topic {}", topic);
        try {
            if (cacheReadyLatch.await(30, TimeUnit.SECONDS)) {
                log.info("Cache OK");
            } else {
                log.warn("Timeout waiting for cache warm-up from topic {}. Proceeding anyway. "
                    + "Duplicate records may not be detected.", topic);
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while warming up cache. This exception is ignored.", e.getMessage());
        }
    }

    @Override
    protected void doStop() {
        // stop the thread
        topicPoller.setRunning(false);
        try {
            if (topicPoller.getShutdownLatch().await(30, TimeUnit.SECONDS)) {
                log.info("Cache from topic {} shutdown successfully", topic);
            } else {
                log.warn("Timeout waiting for cache to shutdown from topic {}. Proceeding anyway.", topic);
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted waiting on shutting down cache due {}. This exception is ignored.", e.getMessage());
        }
        camelContext.getExecutorServiceManager().shutdown(executorService);

        IOHelper.close(consumer, "consumer", log);
        IOHelper.close(producer, "producer", log);
    }

    @Override
    public boolean add(String key) {
        if (cache.containsKey(key)) {
            duplicateCount.incrementAndGet();
            return false;
        } else {
            // update the local cache and broadcast the addition on the topic, which will be reflected
            // at a later point in any peers
            cache.put(key, key);
            broadcastAction(key, CacheAction.add);
            return true;
        }
    }

    private void broadcastAction(String key, CacheAction action) {
        try {
            log.debug("Broadcasting action:{} for key:{}", action, key);
            producer.send(new ProducerRecord<>(topic, key, action.toString())).get(); // sync send
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        log.debug("Checking cache for key:{}", key);
        boolean containsKey = cache.containsKey(key);
        if (containsKey) {
            duplicateCount.incrementAndGet();
        }
        return containsKey;
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        // update the local cache and broadcast the addition on the topic, which will be reflected
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

    @ManagedOperation(description = "Number of times duplicate messages have been detected")
    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    @ManagedOperation(description = "Number of times duplicate messages have been detected")
    public boolean isPollerRunning() {
        return topicPoller.isRunning();
    }

    private class TopicPoller implements Runnable {

        private final Logger log = LoggerFactory.getLogger(this.getClass());
        private final Consumer<String, String> consumer;
        private final CountDownLatch cacheReadyLatch;
        private final int pollDurationMs;

        private final CountDownLatch shutdownLatch = new CountDownLatch(1);
        private final AtomicBoolean running = new AtomicBoolean(true);

        TopicPoller(Consumer<String, String> consumer, CountDownLatch cacheReadyLatch, int pollDurationMs) {
            this.consumer = consumer;
            this.cacheReadyLatch = cacheReadyLatch;
            this.pollDurationMs = pollDurationMs;
        }

        @Override
        public void run() {
            log.debug("Subscribing consumer to {}", topic);
            consumer.subscribe(Collections.singleton(topic));
            log.debug("Seeking to beginning");
            consumer.seekToBeginning(consumer.assignment());

            POLL_LOOP: while (running.get()) {
                log.trace("Polling");
                ConsumerRecords<String, String> consumerRecords = consumer.poll(pollDurationMs);
                if (consumerRecords.isEmpty()) {
                    // the first time this happens, we can assume that we have consumed all
                    // messages up to this point
                    log.trace("0 messages fetched on poll");
                    if (cacheReadyLatch.getCount() > 0) {
                        log.debug("Cache warmed up");
                        cacheReadyLatch.countDown();
                    }
                }
                for (ConsumerRecord<String, String> consumerRecord: consumerRecords) {
                    CacheAction action;
                    try {
                        action = CacheAction.valueOf(consumerRecord.value());
                    } catch (IllegalArgumentException iax) {
                        log.error("Unexpected action value:\"{}\" received on [topic:{}, partition:{}, offset:{}]. Shutting down.",
                                consumerRecord.key(), consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset());
                        setRunning(false);
                        continue POLL_LOOP;
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
                        log.warn("No idea how to {} a record. Shutting down.", action);
                        setRunning(false);
                        continue POLL_LOOP;
                    }
                }

            }
            log.debug("TopicPoller finished - triggering shutdown latch");
            shutdownLatch.countDown();
        }

        CountDownLatch getShutdownLatch() {
            return shutdownLatch;
        }

        void setRunning(boolean running) {
            this.running.set(running);
        }

        boolean isRunning() {
            return running.get();
        }

        @Override
        public String toString() {
            return "TopicPoller[" + topic + "]";
        }
    }

}
