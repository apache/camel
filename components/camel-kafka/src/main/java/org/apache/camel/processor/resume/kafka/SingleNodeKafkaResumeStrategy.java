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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.util.IOHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resume strategy that publishes offsets to a Kafka topic. This resume strategy is suitable for single node
 * integrations.
 */
public class SingleNodeKafkaResumeStrategy<T extends Resumable> implements KafkaResumeStrategy<T> {
    private static final Logger LOG = LoggerFactory.getLogger(SingleNodeKafkaResumeStrategy.class);

    private Consumer<byte[], byte[]> consumer;
    private Producer<byte[], byte[]> producer;
    private Duration pollDuration = Duration.ofSeconds(1);

    private final Queue<RecordError> producerErrors = new ConcurrentLinkedQueue<>();

    private boolean subscribed;
    private ResumeAdapter adapter;
    private final KafkaResumeStrategyConfiguration resumeStrategyConfiguration;
    private final ExecutorService executorService;

    /**
     * Builds an instance of this class
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     *
     */
    public SingleNodeKafkaResumeStrategy(KafkaResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.resumeStrategyConfiguration = resumeStrategyConfiguration;
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Builds an instance of this class
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     *
     */
    public SingleNodeKafkaResumeStrategy(KafkaResumeStrategyConfiguration resumeStrategyConfiguration,
                                         ExecutorService executorService) {
        this.resumeStrategyConfiguration = resumeStrategyConfiguration;
        this.executorService = executorService;
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
    protected void produce(byte[] key, byte[] message) throws ExecutionException, InterruptedException {
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(resumeStrategyConfiguration.getTopic(), key, message);

        producer.send(record, (recordMetadata, e) -> {
            if (e != null) {
                LOG.error("Failed to send message {}", e.getMessage(), e);
                producerErrors.add(new RecordError(recordMetadata, e));
            }
        });
    }

    protected void doAdd(OffsetKey<?> key, Offset<?> offsetValue) {
        if (adapter instanceof Cacheable) {
            Cacheable cacheable = (Cacheable) adapter;

            cacheable.add(key, offsetValue);
        }
    }

    @Override
    public void updateLastOffset(T offset) throws Exception {
        createProducer();

        OffsetKey<?> key = offset.getOffsetKey();
        Offset<?> offsetValue = offset.getLastOffset();

        LOG.debug("Updating offset on Kafka with key {} to {}", key.getValue(), offsetValue.getValue());

        ByteBuffer keyBuffer = key.serialize();
        ByteBuffer valueBuffer = offsetValue.serialize();

        produce(keyBuffer.array(), valueBuffer.array());

        doAdd(key, offsetValue);
    }

    /**
     * Loads the existing data into the cache
     *
     * @throws Exception
     */
    public void loadCache() throws Exception {
        createConsumer();

        subscribe();

        LOG.debug("Loading records from topic {}", resumeStrategyConfiguration.getTopic());

        if (!(adapter instanceof Deserializable)) {
            throw new RuntimeCamelException("Cannot load data for an adapter that is not deserializable");
        }

        executorService.submit(() -> refresh());
    }

    protected void poll() {
        Deserializable deserializable = (Deserializable) adapter;

        ConsumerRecords<byte[], byte[]> records;
        do {
            records = consume();

            if (records.isEmpty()) {
                break;
            }

            for (ConsumerRecord<byte[], byte[]> record : records) {
                byte[] value = record.value();

                LOG.trace("Read from Kafka: {}", value);

                if (!deserializable.deserialize(ByteBuffer.wrap(record.key()), ByteBuffer.wrap(record.value()))) {
                    LOG.warn("Deserializar indicates that this is the last record to deserialize");
                }
            }
        } while (true);
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
     * @param  remaining the number of remaining messages on the topic to try to collect
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
            LOG.warn("The consumer is likely already closed. Skipping unsubscribing from {}",
                    resumeStrategyConfiguration.getTopic());
        } catch (Exception e) {
            LOG.error("Error unsubscribing from the Kafka topic {}: {}", resumeStrategyConfiguration.getTopic(), e.getMessage(),
                    e);
        }
    }

    /**
     * Consumes message from the topic previously setup
     *
     * @return An instance of the consumer records
     */
    protected ConsumerRecords<byte[], byte[]> consume() {
        int retries = 10;

        return consume(retries);
    }

    /**
     * Consumes message from the topic previously setup
     *
     * @param  retries how many times to retry consuming data from the topic
     * @return         An instance of the consumer records
     */
    protected ConsumerRecords<byte[], byte[]> consume(int retries) {
        while (retries > 0) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(pollDuration);
            if (!records.isEmpty()) {
                return records;
            }
            retries--;
        }

        return ConsumerRecords.empty();
    }

    /**
     * Consumes message from the topic previously setup
     *
     * @param  retries  how many times to retry consuming data from the topic
     * @param  consumer the kafka consumer object instance to use
     * @return          An instance of the consumer records
     */
    protected ConsumerRecords<byte[], byte[]> consume(int retries, Consumer<byte[], byte[]> consumer) {
        while (retries > 0) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(pollDuration);
            if (!records.isEmpty()) {
                return records;
            }
            retries--;
        }

        return ConsumerRecords.empty();
    }

    public void subscribe() throws Exception {
        if (adapter instanceof Cacheable) {
            ResumeCache<?> cache = ((Cacheable) adapter).getCache();

            if (cache.capacity() >= 1) {
                checkAndSubscribe(resumeStrategyConfiguration.getTopic(), cache.capacity());
            } else {
                checkAndSubscribe(resumeStrategyConfiguration.getTopic());
            }
        } else {
            checkAndSubscribe(resumeStrategyConfiguration.getTopic());
        }
    }

    @Override
    public ResumeAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Gets the set record of sent items
     *
     * @return A collection with all the record errors
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
    }

    private void createProducer() {
        if (producer == null) {
            producer = new KafkaProducer<>(resumeStrategyConfiguration.getProducerProperties());
        }
    }

    private void createConsumer() {
        if (consumer == null) {
            consumer = new KafkaConsumer<>(resumeStrategyConfiguration.getConsumerProperties());
        }
    }

    @Override
    public void stop() {
        LOG.info("Closing the Kafka producer");
        IOHelper.close(producer, "Kafka producer", LOG);

        LOG.info("Closing the Kafka consumer");
        IOHelper.close(producer, "Kafka consumer", LOG);

        executorService.shutdown();
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    @Override
    public void start() {
        LOG.info("Starting the kafka resume strategy");
    }

    public Duration getPollDuration() {
        return pollDuration;
    }

    public void setPollDuration(Duration pollDuration) {
        this.pollDuration = Objects.requireNonNull(pollDuration, "The poll duration cannot be null");
    }

    protected Consumer<byte[], byte[]> getConsumer() {
        return consumer;
    }

    protected Producer<byte[], byte[]> getProducer() {
        return producer;
    }

    /**
     * Clear the producer errors
     */
    public void resetProducerErrors() {
        producerErrors.clear();
    }

    protected KafkaResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return resumeStrategyConfiguration;
    }

    /**
     * Launch a thread to refresh the offsets periodically
     */
    private void refresh() {
        LOG.trace("Creating a offset cache refresher");
        try {
            Properties prop = (Properties) getResumeStrategyConfiguration().getConsumerProperties().clone();
            prop.setProperty(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

            try (Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(prop)) {
                consumer.subscribe(Collections.singletonList(getResumeStrategyConfiguration().getTopic()));

                poll();
            }
        } catch (Exception e) {
            LOG.error("Error while refreshing the local cache: {}", e.getMessage(), e);
        }
    }
}
