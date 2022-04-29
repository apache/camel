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

package org.apache.camel.processor.resume.kafka;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resume strategy that publishes offsets to a Kafka topic. This resume strategy is suitable for single node
 * integrations. For multi-node integrations (i.e: using clusters with the master component check
 * {@link MultiNodeKafkaResumeStrategy}.
 * 
 * @param <K> the type of key
 * @param <V> the type of the value
 */
public class SingleNodeKafkaResumeStrategy<K, V> implements KafkaResumeStrategy<K, V> {
    private static final Logger LOG = LoggerFactory.getLogger(SingleNodeKafkaResumeStrategy.class);

    private final String topic;

    private Consumer<K, V> consumer;
    private Producer<K, V> producer;
    private Duration pollDuration = Duration.ofSeconds(1);

    private final Queue<RecordError> producerErrors = new ConcurrentLinkedQueue<>();
    private final ResumeCache<K, V> resumeCache;
    private boolean subscribed;
    private final Properties producerConfig;
    private final Properties consumerConfig;
    private ResumeAdapter resumeAdapter;

    /**
     * Builds an instance of this class
     * 
     * @param bootstrapServers the address of the Kafka broker
     * @param topic            the topic where to publish the offsets
     * @param resumeCache      a cache instance where to store the offsets locally for faster access
     * @param resumeAdapter    the component-specific resume adapter
     */
    public SingleNodeKafkaResumeStrategy(String bootstrapServers, String topic, ResumeCache<K, V> resumeCache,
                                         ResumeAdapter resumeAdapter) {
        this(topic, resumeCache, resumeAdapter, createProducer(bootstrapServers), createConsumer(bootstrapServers));
    }

    /**
     * Builds an instance of this class
     *
     * @param topic          the topic where to publish the offsets
     * @param resumeCache    a cache instance where to store the offsets locally for faster access
     * @param resumeAdapter  the component-specific resume adapter
     * @param producerConfig the set of properties to be used by the Kafka producer within this class
     * @param consumerConfig the set of properties to be used by the Kafka consumer within this class
     */
    public SingleNodeKafkaResumeStrategy(String topic, ResumeCache<K, V> resumeCache, ResumeAdapter resumeAdapter,
                                         Properties producerConfig,
                                         Properties consumerConfig) {
        this.topic = ObjectHelper.notNull(topic, "The topic must not be null");
        this.resumeCache = resumeCache;
        this.resumeAdapter = resumeAdapter;
        this.producerConfig = producerConfig;
        this.consumerConfig = consumerConfig;

        init();
    }

    /**
     * Creates a basic string-based producer
     * 
     * @param  bootstrapServers the Kafka host
     * @return                  A set of default properties for producing string-based key/pair records from Kafka
     */
    public static Properties createProducer(String bootstrapServers) {
        Properties config = new Properties();

        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        StringHelper.notEmpty(bootstrapServers, "bootstrapServers");
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        return config;
    }

    /**
     * Creates a basic string-based consumer
     * 
     * @param  bootstrapServers the Kafka host
     * @return                  A set of default properties for consuming string-based key/pair records from Kafka
     */
    public static Properties createConsumer(String bootstrapServers) {
        Properties config = new Properties();

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        StringHelper.notEmpty(bootstrapServers, "bootstrapServers");
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        String groupId = UUID.randomUUID().toString();
        LOG.debug("Creating consumer with {}[{}]", ConsumerConfig.GROUP_ID_CONFIG, groupId);

        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE.toString());

        return config;
    }

    /**
     * Sends data to a topic. The records will always be sent asynchronously. If there's an error, a producer error
     * counter will be increased.
     *
     * @see                         SingleNodeKafkaResumeStrategy#getProducerErrors()
     * @param  message              the message to send
     * @throws ExecutionException
     * @throws InterruptedException
     *
     */
    protected void produce(K key, V message) throws ExecutionException, InterruptedException {
        ProducerRecord<K, V> record = new ProducerRecord<>(topic, key, message);

        producer.send(record, (recordMetadata, e) -> {
            if (e != null) {
                LOG.error("Failed to send message {}", e.getMessage(), e);
                producerErrors.add(new RecordError(recordMetadata, e));
            }
        });
    }

    @Override
    public void updateLastOffset(Resumable<K, V> offset) throws Exception {
        K key = offset.getAddressable();
        V offsetValue = offset.getLastOffset().offset();

        LOG.debug("Updating offset on Kafka with key {} to {}", key, offsetValue);

        produce(key, offsetValue);

        resumeCache.add(key, offsetValue);
    }

    /**
     * Loads the existing data into the cache
     * 
     * @throws Exception
     */
    protected void loadCache() throws Exception {
        subscribe();

        LOG.debug("Loading records from topic {}", topic);

        ConsumerRecords<K, V> records;
        do {
            records = consume();

            if (records.isEmpty()) {
                break;
            }

            for (ConsumerRecord<K, V> record : records) {
                V value = record.value();

                LOG.trace("Read from Kafka: {}", value);
                resumeCache.add(record.key(), record.value());

                if (resumeCache.isFull()) {
                    break;
                }
            }
        } while (true);

        unsubscribe();
    }

    /**
     * Subscribe to the topic if not subscribed yet
     * 
     * @param topic the topic to consume the messages from
     */
    protected void checkAndSubscribe(String topic) {
        if (!subscribed) {
            consumer.subscribe(Collections.singletonList(topic));

            subscribed = true;
        }
    }

    /**
     * Subscribe to the topic if not subscribed yet
     * 
     * @param topic     the topic to consume the messages from
     * @param remaining the number of messages to rewind from the last offset position (used to fill the cache)
     */
    public void checkAndSubscribe(String topic, long remaining) {
        if (!subscribed) {
            consumer.subscribe(Collections.singletonList(topic), getConsumerRebalanceListener(remaining));

            subscribed = true;
        }
    }

    /**
     * Creates a new consumer rebalance listener. This can be useful for setting the exact Kafka offset when necessary
     * to read a limited amount of messages or customize the resume strategy behavior when a rebalance occurs.
     * 
     * @param  remaining
     * @return
     */
    protected ConsumerRebalanceListener getConsumerRebalanceListener(long remaining) {
        return new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> collection) {

            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> assignments) {
                for (TopicPartition assignment : assignments) {
                    final long endPosition = consumer.position(assignment);
                    final long startPosition = endPosition - remaining;

                    if (startPosition >= 0) {
                        consumer.seek(assignment, startPosition);
                    } else {
                        LOG.info(
                                "Ignoring the seek command because the initial offset is negative (the topic is likely empty)");
                    }
                }
            }
        };
    }

    /**
     * Unsubscribe from the topic
     */
    protected void unsubscribe() {
        try {
            consumer.unsubscribe();
        } catch (IllegalStateException e) {
            LOG.warn("The consumer is likely already closed. Skipping unsubscribing from {}", topic);
        } catch (Exception e) {
            LOG.error("Error unsubscribing from the Kafka topic {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Consumes message from the topic previously setup
     *
     * @return An instance of the consumer records
     */
    protected ConsumerRecords<K, V> consume() {
        int retries = 10;

        return consume(retries);
    }

    /**
     * Consumes message from the topic previously setup
     * 
     * @param  retries how many times to retry consuming data from the topic
     * @return         An instance of the consumer records
     */
    protected ConsumerRecords<K, V> consume(int retries) {
        while (retries > 0) {
            ConsumerRecords<K, V> records = consumer.poll(pollDuration);
            if (!records.isEmpty()) {
                return records;
            }
            retries--;
        }

        return ConsumerRecords.empty();
    }

    public void subscribe() throws Exception {
        if (resumeCache.capacity() >= 1) {
            checkAndSubscribe(topic, resumeCache.capacity());
        } else {
            checkAndSubscribe(topic);
        }
    }

    @Override
    public ResumeAdapter getAdapter() {
        return resumeAdapter;
    }

    /**
     * Gets the set record of sent items
     *
     * @return
     */
    protected Collection<RecordError> getProducerErrors() {
        return Collections.unmodifiableCollection(producerErrors);
    }

    @Override
    public void build() {
        // NO-OP
    }

    @Override
    public void init() {

        LOG.debug("Initializing the Kafka resume strategy");
        if (consumer == null) {
            consumer = new KafkaConsumer<>(consumerConfig);
        }

        if (producer == null) {
            producer = new KafkaProducer<>(producerConfig);
        }
    }

    @Override
    public void stop() {
        LOG.info("Closing the Kafka producer");
        IOHelper.close(producer, "Kafka producer", LOG);

        LOG.info("Closing the Kafka consumer");
        IOHelper.close(producer, "Kafka consumer", LOG);
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    @Override
    public void start() {
        LOG.info("Starting the kafka resume strategy");

        try {
            loadCache();
        } catch (Exception e) {
            LOG.error("Failed to load already processed items: {}", e.getMessage(), e);
        }
    }

    public Duration getPollDuration() {
        return pollDuration;
    }

    public void setPollDuration(Duration pollDuration) {
        this.pollDuration = Objects.requireNonNull(pollDuration, "The poll duration cannot be null");
    }

    protected Consumer<K, V> getConsumer() {
        return consumer;
    }

    protected Producer<K, V> getProducer() {
        return producer;
    }

    protected Properties getProducerConfig() {
        return producerConfig;
    }

    protected Properties getConsumerConfig() {
        return consumerConfig;
    }

    protected String getTopic() {
        return topic;
    }

    protected ResumeCache<K, V> getResumeCache() {
        return resumeCache;
    }

    /**
     * Clear the producer errors
     */
    public void resetProducerErrors() {
        producerErrors.clear();
    }
}
