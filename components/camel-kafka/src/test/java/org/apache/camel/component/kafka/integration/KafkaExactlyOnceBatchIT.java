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
package org.apache.camel.component.kafka.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies batched, multi-partition offsets survive body transformation and commit with output records. */
@Tags({ @Tag("breakOnFirstError") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64" },
             disabledReason = "This test does not run reliably on some platforms")
class KafkaExactlyOnceBatchIT extends BaseKafkaTestSupport {

    private static final String ROUTE_ID = "exactlyOnceBatch";
    private static final String TOPIC_IN = "exactlyOnceBatchIn";
    private static final String TOPIC_OUT = "exactlyOnceBatchOut";
    private static final String GROUP_ID = "exactlyOnceBatchGroup";
    private static final int PARTITION_COUNT = 2;
    private static final int RECORDS_PER_PARTITION = 4;
    private static final int MESSAGE_COUNT = PARTITION_COUNT * RECORDS_PER_PARTITION;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void before() {
        producer = new KafkaProducer<>(getDefaultProperties());
    }

    @AfterEach
    void after() {
        if (producer != null) {
            producer.close();
        }
        kafkaAdminClient.deleteTopics(List.of(TOPIC_IN, TOPIC_OUT)).all();
    }

    @Test
    void exactlyOnceCommitsBatchOffsetsForEveryPartitionAfterBodyTransformation() throws Exception {
        to.expectedMessageCount(1);
        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);
        ensurePartitionCount(TOPIC_IN, PARTITION_COUNT);
        publishMessages();
        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);
        to.assertIsSatisfied(60000);

        List<String> committed = consumeRecords("read_committed", MESSAGE_COUNT);
        assertEquals(MESSAGE_COUNT, committed.size());
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            expected.add("processed-message-" + i);
        }
        assertEquals(expected, new HashSet<>(committed));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<TopicPartition, OffsetAndMetadata> offsets = kafkaAdminClient
                    .listConsumerGroupOffsets(GROUP_ID).partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS);
            assertEquals(PARTITION_COUNT, offsets.size());
            for (int partition = 0; partition < PARTITION_COUNT; partition++) {
                OffsetAndMetadata offset = offsets.get(new TopicPartition(TOPIC_IN, partition));
                assertNotNull(offset, "No committed offset for input partition " + partition);
                assertEquals(RECORDS_PER_PARTITION, offset.offset());
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kafka:%s?groupId=%s&exactlyOnce=true&autoOffsetReset=earliest"
                      + "&autoCommitEnable=false&allowManualCommit=true&breakOnFirstError=true"
                      + "&batching=true&maxPollRecords=%s&batchingIntervalMs=1000&pollTimeoutMs=500"
                      + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        TOPIC_IN, GROUP_ID, MESSAGE_COUNT)
                        .routeId(ROUTE_ID)
                        .process(exchange -> {
                            List<?> batch = exchange.getMessage().getBody(List.class);
                            List<String> transformed = batch.stream()
                                    .map(Exchange.class::cast)
                                    .map(item -> "processed-" + item.getMessage().getBody(String.class))
                                    .toList();
                            // Deliberately replace the List<Exchange> body. The source offsets must come from
                            // the immutable metadata snapshot, not from inspecting this transformed body later.
                            exchange.getMessage().setBody(transformed);
                        })
                        .toF("kafka:%s?transacted=true&exactlyOnce=true&requestRequiredAcks=-1", TOPIC_OUT)
                        .to(to);
            }
        };
    }

    private void ensurePartitionCount(String topic, int count) throws Exception {
        Set<String> topics = kafkaAdminClient.listTopics().names().get(30, TimeUnit.SECONDS);
        if (!topics.contains(topic)) {
            kafkaAdminClient.createTopics(List.of(new NewTopic(topic, count, (short) 1))).all()
                    .get(30, TimeUnit.SECONDS);
            return;
        }
        int existing = kafkaAdminClient.describeTopics(List.of(topic)).allTopicNames().get(30, TimeUnit.SECONDS)
                .get(topic).partitions().size();
        if (existing < count) {
            kafkaAdminClient.createPartitions(Map.of(topic, NewPartitions.increaseTo(count))).all()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    private void publishMessages() {
        for (int partition = 0; partition < PARTITION_COUNT; partition++) {
            for (int i = 0; i < RECORDS_PER_PARTITION; i++) {
                int number = partition * RECORDS_PER_PARTITION + i;
                producer.send(new ProducerRecord<>(TOPIC_IN, partition, null, "message-" + number));
            }
        }
        producer.flush();
    }

    private List<String> consumeRecords(String isolationLevel, int expectedCount) {
        List<String> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = createConsumer(isolationLevel)) {
            consumer.subscribe(List.of(TOPIC_OUT));
            Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> received.add(record.value()));
                assertTrue(received.size() >= expectedCount);
            });
            for (int i = 0; i < 4; i++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                records.forEach(record -> received.add(record.value()));
            }
        }
        return received;
    }

    private static KafkaConsumer<String, String> createConsumer(String isolationLevel) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "exactlyOnceBatchVerifier");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }
}
