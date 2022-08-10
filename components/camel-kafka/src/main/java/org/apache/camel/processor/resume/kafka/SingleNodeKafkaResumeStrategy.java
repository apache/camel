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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
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
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Builds an instance of this class
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     */
    public SingleNodeKafkaResumeStrategy(KafkaResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.resumeStrategyConfiguration = resumeStrategyConfiguration;
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Builds an instance of this class
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
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
     * @param  message              the message to send
     * @throws ExecutionException
     * @throws InterruptedException
     * @see                         SingleNodeKafkaResumeStrategy#getProducerErrors()
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
        OffsetKey<?> key = offset.getOffsetKey();
        Offset<?> offsetValue = offset.getLastOffset();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Updating offset on Kafka with key {} to {}", key.getValue(), offsetValue.getValue());
        }

        ByteBuffer keyBuffer = key.serialize();
        ByteBuffer valueBuffer = offsetValue.serialize();

        try {
            lock.lock();
            produce(keyBuffer.array(), valueBuffer.array());
        } finally {
            lock.unlock();
        }

        doAdd(key, offsetValue);
    }

    /**
     * Loads the existing data into the cache
     */
    @Override
    public void loadCache() {
        if (!(adapter instanceof Deserializable)) {
            throw new RuntimeCamelException("Cannot load data for an adapter that is not deserializable");
        }

        executorService.submit(this::refresh);
    }

    /**
     * Launch a thread to refresh the offsets periodically
     */
    private void refresh() {
        LOG.trace("Creating a offset cache refresher");

        try {
            consumer = createConsumer();

            subscribe(consumer);

            LOG.debug("Loading records from topic {}", resumeStrategyConfiguration.getTopic());
            consumer.subscribe(Collections.singletonList(getResumeStrategyConfiguration().getTopic()));

            poll(consumer);
        } catch (WakeupException e) {
            LOG.info("Kafka consumer was interrupted during a blocking call");
        } catch (Exception e) {
            LOG.error("Error while refreshing the local cache: {}", e.getMessage(), e);
        } finally {
            if (consumer != null) {
                consumer.unsubscribe();
                consumer.close(Duration.ofSeconds(5));
            }
        }
    }

    protected void poll(Consumer<byte[], byte[]> consumer) {
        Deserializable deserializable = (Deserializable) adapter;

        do {
            ConsumerRecords<byte[], byte[]> records = consume(consumer);

            if (records.isEmpty()) {
                continue;
            }

            for (ConsumerRecord<byte[], byte[]> record : records) {
                byte[] value = record.value();

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Read from Kafka: {}", value);
                }

                if (!deserializable.deserialize(ByteBuffer.wrap(record.key()), ByteBuffer.wrap(record.value()))) {
                    LOG.warn("Deserializer indicates that this is the last record to deserialize");
                }
            }
        } while (true);
    }

    /**
     * Subscribe to the topic if not subscribed yet
     *
     * @param topic the topic to consume the messages from
     */
    protected void checkAndSubscribe(Consumer<byte[], byte[]> consumer, String topic) {
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
    public void checkAndSubscribe(Consumer<byte[], byte[]> consumer, String topic, long remaining) {
        if (!subscribed) {
            consumer.subscribe(Collections.singletonList(topic), getConsumerRebalanceListener(consumer, remaining));
            subscribed = true;
        }
    }

    private ConsumerRebalanceListener getConsumerRebalanceListener(Consumer<byte[], byte[]> consumer, long remaining) {
        return new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> collection) {
                // NO-OP
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
     * Consumes message from the topic previously setup
     *
     * @return An instance of the consumer records
     */
    protected ConsumerRecords<byte[], byte[]> consume(Consumer<byte[], byte[]> consumer) {
        ConsumerRecords<byte[], byte[]> records = consumer.poll(pollDuration);
        if (!records.isEmpty()) {
            return records;
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

    private void subscribe(Consumer<byte[], byte[]> consumer) {
        if (adapter instanceof Cacheable) {
            ResumeCache<?> cache = ((Cacheable) adapter).getCache();

            if (cache.capacity() >= 1) {
                checkAndSubscribe(consumer, resumeStrategyConfiguration.getTopic(), cache.capacity());
            } else {
                checkAndSubscribe(consumer, resumeStrategyConfiguration.getTopic());
            }
        } else {
            checkAndSubscribe(consumer, resumeStrategyConfiguration.getTopic());
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

    private Consumer<byte[], byte[]> createConsumer() {
        return new KafkaConsumer<>(resumeStrategyConfiguration.getConsumerProperties());
    }

    @Override
    public void stop() {
        try {
            LOG.trace("Trying to obtain a lock for closing the producer");
            if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                LOG.warn("Failed to obtain a lock for closing the producer. Force closing the producer ...");
            }

            LOG.info("Closing the Kafka producer");
            IOHelper.close(producer, "Kafka producer", LOG);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

        try {
            LOG.info("Closing the Kafka consumer");
            consumer.wakeup();
            executorService.shutdown();

            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warn("Kafka consumer did not shutdown within 2 seconds");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    @Override
    public void start() {
        LOG.info("Starting the kafka resume strategy");
        createProducer();
    }

    public Duration getPollDuration() {
        return pollDuration;
    }

    public void setPollDuration(Duration pollDuration) {
        this.pollDuration = Objects.requireNonNull(pollDuration, "The poll duration cannot be null");
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

}
