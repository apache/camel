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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
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
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Kafka topic used as the changelog of the local cache of a repository, shared by the Kafka repositories
 * ({@link KafkaIdempotentRepository} and the KafkaKeyValueRepository).
 * <p/>
 * On start, the whole topic is read from the beginning and every record is applied to the cache; then, unless the
 * repository syncs on startup only, the records written by the other instances keep being applied by a background
 * thread. The changes of this instance are written to the topic synchronously.
 * <p/>
 * This class is internal to the Kafka repositories of camel-kafka, and is not meant to be used by applications.
 *
 * @param <V> the type of the record values
 */
public final class KafkaChangelog<V> {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaChangelog.class);

    private final String name;
    private final String topic;
    private final Properties consumerConfig;
    private final Properties producerConfig;
    private final Class<? extends Deserializer<V>> valueDeserializer;
    private final Class<? extends Serializer<V>> valueSerializer;
    private final int pollDurationMs;
    private final boolean startupOnly;
    private final Consumer<ConsumerRecord<String, V>> recordHandler;

    private CamelContext camelContext;
    private org.apache.kafka.clients.consumer.Consumer<String, V> consumer;
    private Producer<String, V> producer;
    private TopicPoller poller;
    private ExecutorService executorService;

    /**
     * @param name              the name of the repository, used in the logs and in the name of the background thread
     * @param topic             the changelog topic
     * @param consumerConfig    the properties of the consumer, see {@link #consumerConfig(Properties, String, String)}
     * @param producerConfig    the properties of the producer, see {@link #producerConfig(Properties, String)}
     * @param valueDeserializer the deserializer of the record values (the keys are strings)
     * @param valueSerializer   the serializer of the record values (the keys are strings)
     * @param pollDurationMs    the poll duration of the consumer
     * @param startupOnly       whether to read the topic on start only, or to keep reading it in the background
     * @param recordHandler     applies a record of the topic to the local cache
     */
    public KafkaChangelog(String name, String topic, Properties consumerConfig, Properties producerConfig,
                          Class<? extends Deserializer<V>> valueDeserializer, Class<? extends Serializer<V>> valueSerializer,
                          int pollDurationMs, boolean startupOnly, Consumer<ConsumerRecord<String, V>> recordHandler) {
        this.name = name;
        this.topic = topic;
        this.consumerConfig = consumerConfig;
        this.producerConfig = producerConfig;
        this.valueDeserializer = valueDeserializer;
        this.valueSerializer = valueSerializer;
        this.pollDurationMs = pollDurationMs;
        this.startupOnly = startupOnly;
        this.recordHandler = recordHandler;
    }

    /**
     * The consumer properties of a repository: the configured properties, otherwise properties with the bootstrap
     * servers and the group id.
     */
    public static Properties consumerConfig(Properties consumerConfig, String bootstrapServers, String groupId) {
        if (consumerConfig != null) {
            return consumerConfig;
        }
        Properties answer = new Properties();
        StringHelper.notEmpty(bootstrapServers, "bootstrapServers");
        answer.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (groupId != null) {
            answer.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }
        return answer;
    }

    /**
     * The producer properties of a repository: the configured properties, otherwise properties with the bootstrap
     * servers.
     */
    public static Properties producerConfig(Properties producerConfig, String bootstrapServers) {
        if (producerConfig != null) {
            return producerConfig;
        }
        Properties answer = new Properties();
        StringHelper.notEmpty(bootstrapServers, "bootstrapServers");
        answer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return answer;
    }

    /**
     * Creates the consumer and the producer, reads the topic from the beginning, and starts the background thread
     * unless the repository syncs on startup only.
     *
     * @param camelContext the CamelContext
     * @param source       the owner of the background thread
     */
    public void start(CamelContext camelContext, Object source) {
        this.camelContext = camelContext;

        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE.toString());
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
        consumer = new KafkaConsumer<>(consumerConfig);

        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());
        // set up the producer to remove all batching on send, we want all sends to be fully synchronous
        producerConfig.putIfAbsent(ProducerConfig.ACKS_CONFIG, "1");
        producerConfig.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, "0");
        producer = new KafkaProducer<>(producerConfig);

        poller = new TopicPoller();
        ServiceHelper.startService(poller);
        // populate cache on startup to be ready
        StopWatch watch = new StopWatch();
        LOG.info("Syncing {} from topic: {} starting", name, topic);
        poller.run();
        LOG.info("Syncing {} from topic: {} complete: {}", name, topic, TimeUtils.printDuration(watch.taken(), true));

        if (!startupOnly) {
            // continue sync job in background
            executorService = camelContext.getExecutorServiceManager().newSingleThreadExecutor(source, name + "Sync");
            LOG.info("Syncing {} from topic: {} continuously using background thread", name, topic);
            executorService.submit(poller);
        }
    }

    /**
     * Stops the background thread, and closes the consumer and the producer.
     */
    public void stop() {
        ServiceHelper.stopService(poller);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (executorService != null && camelContext != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        IOHelper.close(consumer, "consumer", LOG);
        IOHelper.close(producer, "producer", LOG);
    }

    /**
     * Writes a record to the topic, and waits until it is written.
     */
    public void send(String key, V value) {
        try {
            ObjectHelper.notNull(producer, "producer");
            producer.send(new ProducerRecord<>(topic, key, value)).get(); // sync send
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeCamelException(e);
        } catch (ExecutionException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * To use the given producer, for testing.
     */
    void setProducer(Producer<String, V> producer) {
        this.producer = producer;
    }

    private void populateCache() {
        LOG.debug("Getting partitions of topic {}", topic);
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        Collection<TopicPartition> partitions = partitionInfos.stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .toList();

        LOG.debug("Assigning consumer to partitions {}", partitions);
        consumer.assign(partitions);

        LOG.debug("Seeking consumer to beginning of partitions {}", partitions);
        consumer.seekToBeginning(partitions);

        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
        LOG.debug("Consuming records from partitions {} till end offsets {}", partitions, endOffsets);
        while (!KafkaConsumerUtil.isReachedOffsets(consumer, endOffsets)) {
            ConsumerRecords<String, V> consumerRecords = consumer.poll(Duration.ofMillis(pollDurationMs));
            for (ConsumerRecord<String, V> consumerRecord : consumerRecords) {
                recordHandler.accept(consumerRecord);
            }
        }
    }

    private class TopicPoller extends ServiceSupport implements Runnable {

        private final AtomicBoolean init = new AtomicBoolean();

        @Override
        public void run() {
            if (init.compareAndSet(false, true)) {
                // sync cache on startup
                LOG.debug("TopicPoller populating cache on startup");
                populateCache();
                LOG.debug("TopicPoller populated cache on startup complete");
                return;
            }

            LOG.debug("TopicPoller running");
            while (isRunAllowed()) {
                try {
                    ConsumerRecords<String, V> consumerRecords = consumer.poll(Duration.ofMillis(pollDurationMs));
                    for (ConsumerRecord<String, V> consumerRecord : consumerRecords) {
                        recordHandler.accept(consumerRecord);
                    }
                } catch (WakeupException e) {
                    LOG.debug("TopicPoller woken up during shutdown");
                } catch (Exception e) {
                    LOG.warn("TopicPoller error syncing due to: {}. This exception is ignored.", e.getMessage(), e);
                }
            }
            LOG.debug("TopicPoller stopping");
        }
    }
}
