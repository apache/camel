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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.camel.Resumable;
import org.apache.camel.ResumeCache;
import org.apache.camel.Service;
import org.apache.camel.UpdatableConsumerResumeStrategy;
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
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractKafkaResumeStrategy<K, V>
        implements UpdatableConsumerResumeStrategy<K, V, Resumable<K, V>>, Service {
    public static final int UNLIMITED = -1;
    private static final Logger LOG = LoggerFactory.getLogger(AbstractKafkaResumeStrategy.class);

    private final String topic;

    private Consumer<K, V> consumer;
    private Producer<K, V> producer;
    private long errorCount;
    private Duration pollDuration = Duration.ofSeconds(1);

    private final List<Future<RecordMetadata>> sentItems = new ArrayList<>();
    private final ResumeCache<K, V> resumeCache;
    private boolean subscribed;
    private Properties producerConfig;
    private Properties consumerConfig;

    public AbstractKafkaResumeStrategy(String bootstrapServers, String topic, ResumeCache<K, V> resumeCache) {
        this.topic = topic;

        this.producerConfig = createProducer(bootstrapServers);
        this.consumerConfig = createConsumer(bootstrapServers);
        this.resumeCache = resumeCache;

        init();
    }

    public AbstractKafkaResumeStrategy(String topic, ResumeCache<K, V> resumeCache, Properties producerConfig,
                                       Properties consumerConfig) {
        this.topic = topic;
        this.resumeCache = resumeCache;
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
     * Sends data to a topic
     * 
     * @param  message              the message to send
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void produce(K key, V message) throws ExecutionException, InterruptedException {
        ProducerRecord<K, V> record = new ProducerRecord<>(topic, key, message);

        errorCount = 0;
        Future<RecordMetadata> future = producer.send(record, (recordMetadata, e) -> {
            if (e != null) {
                LOG.error("Failed to send message {}", e.getMessage(), e);
                errorCount++;
            }
        });

        sentItems.add(future);
    }

    @Override
    public void updateLastOffset(Resumable<K, V> offset) throws Exception {
        K key = offset.getAddressable();
        V offsetValue = offset.getLastOffset().offset();

        LOG.debug("Updating offset on Kafka with key {} to {}", key, offsetValue);

        produce(key, offsetValue);

        resumeCache.add(key, offsetValue);
    }

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

    // TODO: bad method ...
    /**
     * @param topic the topic to consume the messages from
     */
    public void checkAndSubscribe(String topic) {
        if (!subscribed) {
            consumer.subscribe(Collections.singletonList(topic));

            subscribed = true;
        }
    }

    /**
     * @param topic the topic to consume the messages from
     */
    public void checkAndSubscribe(String topic, long remaining) {
        if (!subscribed) {
            consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> collection) {

                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> assignments) {
                    consumer.seekToEnd(assignments);
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
            });

            subscribed = true;
        }
    }

    public abstract void subscribe() throws Exception;

    public void unsubscribe() {
        try {
            consumer.unsubscribe();
        } catch (IllegalStateException e) {
            LOG.warn("The consumer is likely already closed. Skipping unsubscribing from {}", topic);
        } catch (Exception e) {
            LOG.error("Error unsubscribing from the Kafka topic {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Consumes message from the given topic until the predicate returns false
     *
     * @return
     */
    public ConsumerRecords<K, V> consume() {
        int retries = 10;

        return consume(retries);
    }

    public ConsumerRecords<K, V> consume(int retries) {
        while (retries > 0) {
            ConsumerRecords<K, V> records = consumer.poll(pollDuration);
            if (!records.isEmpty()) {
                return records;
            }
            retries--;
        }

        return ConsumerRecords.empty();
    }

    public long getErrorCount() {
        return errorCount;
    }

    public List<Future<RecordMetadata>> getSentItems() {
        return Collections.unmodifiableList(sentItems);
    }

    @Override
    public void build() {
        Service.super.build();
    }

    @Override
    public void init() {
        Service.super.init();

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

    }

    @Override
    public void close() throws IOException {
        Service.super.close();
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
}
