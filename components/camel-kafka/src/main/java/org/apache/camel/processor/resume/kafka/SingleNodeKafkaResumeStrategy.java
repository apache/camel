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
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
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
@JdkService("kafka-resume-strategy")
public class SingleNodeKafkaResumeStrategy implements KafkaResumeStrategy, CamelContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(SingleNodeKafkaResumeStrategy.class);

    private Consumer<byte[], byte[]> consumer;
    private Producer<byte[], byte[]> producer;
    private Duration pollDuration = Duration.ofSeconds(1);

    private boolean subscribed;
    private ResumeAdapter adapter;
    private KafkaResumeStrategyConfiguration resumeStrategyConfiguration;
    private ExecutorService executorService;
    private final ReentrantLock writeLock = new ReentrantLock();
    private CountDownLatch initLatch;
    private CamelContext camelContext;

    public SingleNodeKafkaResumeStrategy() {

    }

    /**
     * Builds an instance of this class
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     */
    public SingleNodeKafkaResumeStrategy(KafkaResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.resumeStrategyConfiguration = resumeStrategyConfiguration;
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
     * @param message the message to send
     *
     */
    protected void produce(byte[] key, byte[] message, UpdateCallBack updateCallBack) {
        ProducerRecord<byte[], byte[]> producerRecord
                = new ProducerRecord<>(resumeStrategyConfiguration.getTopic(), key, message);

        producer.send(producerRecord, (recordMetadata, e) -> {
            if (e != null) {
                LOG.error("Failed to send message {}", e.getMessage(), e);
            }

            if (updateCallBack != null) {
                updateCallBack.onUpdate(e);
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
    public <T extends Resumable> void updateLastOffset(T offset) throws Exception {
        updateLastOffset(offset, null);
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) throws Exception {
        OffsetKey<?> key = offset.getOffsetKey();
        Offset<?> offsetValue = offset.getLastOffset();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Updating offset on Kafka with key {} to {}", key.getValue(), offsetValue.getValue());
        }

        updateLastOffset(key, offsetValue);
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset) throws Exception {
        updateLastOffset(offsetKey, offset, null);
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) throws Exception {
        ByteBuffer keyBuffer = offsetKey.serialize();
        ByteBuffer valueBuffer = offset.serialize();

        try {
            writeLock.lock();
            produce(keyBuffer.array(), valueBuffer.array(), updateCallBack);
        } finally {
            writeLock.unlock();
        }

        doAdd(offsetKey, offset);
    }

    /**
     * Loads the existing data into the cache
     */
    @Override
    public void loadCache() {
        if (!(adapter instanceof Deserializable)) {
            throw new RuntimeCamelException("Cannot load data for an adapter that is not deserializable");
        }

        initLatch = new CountDownLatch(resumeStrategyConfiguration.getMaxInitializationRetries());
        if (executorService == null) {
            executorService
                    = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, "SingleNodeKafkaResumeStrategy");
        }

        executorService.submit(() -> refresh(initLatch));
    }

    private void waitForInitialization() {
        try {
            LOG.trace("Waiting for kafka resume strategy async initialization");
            if (!initLatch.await(resumeStrategyConfiguration.getMaxInitializationDuration().toMillis(),
                    TimeUnit.MILLISECONDS)) {
                LOG.debug("The initialization timed out");
            }
            LOG.trace("Kafka resume strategy initialization complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Launch a thread to refresh the offsets periodically
     */
    private void refresh(CountDownLatch latch) {
        LOG.trace("Creating a offset cache refresher");

        try {
            consumer = createConsumer();

            subscribe(consumer);

            LOG.debug("Loading records from topic {}", resumeStrategyConfiguration.getTopic());
            consumer.subscribe(Collections.singletonList(resumeStrategyConfiguration.getTopic()));

            poll(consumer, latch);
        } catch (WakeupException e) {
            LOG.info("Kafka consumer was interrupted during a blocking call");
        } catch (Exception e) {
            LOG.error("Error while refreshing the local cache: {}", e.getMessage(), e);
        } finally {
            if (consumer != null) {
                consumer.unsubscribe();
                try {
                    consumer.close(Duration.ofSeconds(5));
                } catch (Exception e) {
                    LOG.warn("Error closing the consumer: {} (this error will be ignored)", e.getMessage(), e);
                }
            }
        }
    }

    protected void poll(Consumer<byte[], byte[]> consumer, CountDownLatch latch) {
        Deserializable deserializable = (Deserializable) adapter;
        boolean initialized = false;

        do {
            ConsumerRecords<byte[], byte[]> records = consume(consumer);

            for (ConsumerRecord<byte[], byte[]> consumerRecord : records) {
                byte[] value = consumerRecord.value();

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Read from Kafka at {} ({}): {}", Instant.ofEpochMilli(consumerRecord.timestamp()),
                            consumerRecord.timestampType(), value);
                }

                if (!deserializable.deserialize(ByteBuffer.wrap(consumerRecord.key()),
                        ByteBuffer.wrap(consumerRecord.value()))) {
                    LOG.warn("Deserializer indicates that this is the last record to deserialize");
                }
            }

            if (!initialized) {
                if (latch.getCount() == 1) {
                    initialized = true;
                }

                latch.countDown();
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
        if (adapter == null) {
            waitForInitialization();
        }

        return adapter;
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {
        this.adapter = adapter;
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
            if (!writeLock.tryLock(1, TimeUnit.SECONDS)) {
                LOG.warn("Failed to obtain a lock for closing the producer. Force closing the producer ...");
            }

            LOG.info("Closing the Kafka producer");
            IOHelper.close(producer, "Kafka producer", LOG);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("Error closing the Kafka producer: {} (this error will be ignored)", e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }

        try {
            LOG.info("Closing the Kafka consumer");
            consumer.wakeup();

            if (executorService != null) {
                executorService.shutdown();

                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOG.warn("Kafka consumer did not shutdown within 2 seconds");
                    executorService.shutdownNow();
                }
            } else {
                // This may happen if the start up has failed in some other part
                LOG.trace("There's no executor service to shutdown");
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

    @Override
    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        if (resumeStrategyConfiguration instanceof KafkaResumeStrategyConfiguration) {
            this.resumeStrategyConfiguration = (KafkaResumeStrategyConfiguration) resumeStrategyConfiguration;
        } else {
            throw new RuntimeCamelException(
                    "Invalid resume strategy configuration of type " +
                                            ObjectHelper.className(resumeStrategyConfiguration));
        }
    }

    @Override
    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return resumeStrategyConfiguration;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
