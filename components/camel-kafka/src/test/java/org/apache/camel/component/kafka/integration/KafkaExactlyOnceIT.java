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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

/**
 * Tests the exactly-once (read-process-write) guarantee of a Kafka-to-Kafka route: the source offsets are committed
 * inside the producer transaction, and the records of a transaction that was aborted never become visible to a
 * read_committed consumer.
 */
@Tags({ @Tag("breakOnFirstError") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64" },
             disabledReason = "This test does not run reliably on some platforms")
class KafkaExactlyOnceIT extends BaseKafkaTestSupport {

    public static final String ROUTE_ID = "exactlyOnce";
    public static final String TOPIC_IN = "exactlyOnceIn";
    public static final String TOPIC_OUT = "exactlyOnceOut";
    public static final String GROUP_ID = "exactlyOnceGroup";

    private static final int MESSAGE_COUNT = 5;
    private static final String FAILING_MESSAGE = "message-2";

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private final AtomicBoolean failureTriggered = new AtomicBoolean();

    private KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() {
        producer = new KafkaProducer<>(getDefaultProperties());
        failureTriggered.set(false);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        kafkaAdminClient.deleteTopics(List.of(TOPIC_IN, TOPIC_OUT)).all();
    }

    @Test
    public void exactlyOnceCommitsOffsetsAndRecordsAtomically() throws Exception {
        to.expectedMessageCount(MESSAGE_COUNT);

        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);
        publishMessagesToKafka();
        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);

        // the failing message is produced inside a transaction that is then aborted, so the route sees it twice
        // but only the retry reaches the end of the route
        to.assertIsSatisfied(30000);

        // a read_committed consumer must see every message exactly once: the record of the aborted transaction is
        // never visible, even though the producer did send it
        final List<String> received = consumeCommittedRecords();
        assertEquals(MESSAGE_COUNT, received.size(), "Every message must be produced exactly once: " + received);
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            assertEquals("message-" + i, received.get(i));
        }

        // the route never commits the offsets itself, so a committed offset can only come from the producer
        // transaction, and it must cover every consumed record
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    final OffsetAndMetadata committed = committedOffset();
                    assertNotNull(committed, "The producer transaction must have committed the source offsets");
                    assertEquals(MESSAGE_COUNT, committed.offset(),
                            "The committed offset must be the next offset to read");
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                fromF("kafka:%s"
                      + "?groupId=%s"
                      + "&autoOffsetReset=earliest"
                      + "&autoCommitEnable=false"
                      + "&allowManualCommit=true"
                      + "&breakOnFirstError=true"
                      + "&maxPollRecords=1"
                      + "&pollTimeoutMs=1000"
                      + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer", TOPIC_IN,
                        GROUP_ID)
                        .routeId(ROUTE_ID)
                        .toF("kafka:%s?transacted=true&exactlyOnce=true&requestRequiredAcks=-1", TOPIC_OUT)
                        // fail after the record was produced, so that an open transaction has to be aborted
                        .process(KafkaExactlyOnceIT.this::failOnceOnFailingMessage)
                        .to(to);
            }
        };
    }

    private void failOnceOnFailingMessage(Exchange exchange) {
        if (FAILING_MESSAGE.equals(exchange.getMessage().getBody(String.class))
                && failureTriggered.compareAndSet(false, true)) {
            throw new RuntimeException("ERROR TRIGGERED BY TEST");
        }
    }

    private void publishMessagesToKafka() {
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(new ProducerRecord<>(TOPIC_IN, null, "message-" + i));
        }
        producer.flush();
    }

    private OffsetAndMetadata committedOffset() throws Exception {
        final Map<TopicPartition, OffsetAndMetadata> offsets = kafkaAdminClient
                .listConsumerGroupOffsets(GROUP_ID)
                .partitionsToOffsetAndMetadata()
                .get(30, TimeUnit.SECONDS);

        return offsets.get(new TopicPartition(TOPIC_IN, 0));
    }

    private List<String> consumeCommittedRecords() {
        final List<String> received = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = createReadCommittedConsumer()) {
            consumer.subscribe(List.of(TOPIC_OUT));

            Awaitility.await()
                    .atMost(30, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        poll(consumer, received);
                        assertEquals(MESSAGE_COUNT, received.size());
                    });

            // keep polling for a while: a duplicate of the aborted transaction would show up after the expected
            // records, and would otherwise go unnoticed
            for (int i = 0; i < 4; i++) {
                poll(consumer, received);
            }
        }

        return received;
    }

    private static void poll(KafkaConsumer<String, String> consumer, List<String> received) {
        final ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
            received.add(record.value());
        }
    }

    private static KafkaConsumer<String, String> createReadCommittedConsumer() {
        final Properties props = new Properties();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "exactlyOnceVerifier");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // the whole point of the test: records of an aborted transaction must not be delivered
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        return new KafkaConsumer<>(props);
    }
}
