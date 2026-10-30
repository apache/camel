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
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.RemoveMembersFromConsumerGroupOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.GroupState;
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

/** Verifies stale group metadata is fenced when the consumer group rebalances during processing. */
@Tags({ @Tag("breakOnFirstError") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64" },
             disabledReason = "This test does not run reliably on some platforms")
class KafkaExactlyOnceRebalanceIT extends BaseKafkaTestSupport {

    private static final String ROUTE_ID = "exactlyOnceRebalance";
    private static final String TOPIC_IN = "exactlyOnceRebalanceIn";
    private static final String TOPIC_OUT = "exactlyOnceRebalanceOut";
    private static final String GROUP_ID = "exactlyOnceRebalanceGroup";
    private static final String VALUE = "rebalance-me";

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private final CountDownLatch firstAttemptStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstAttempt = new CountDownLatch(1);
    private final AtomicBoolean blockFirstAttempt = new AtomicBoolean(true);
    private final AtomicInteger attempts = new AtomicInteger();
    private KafkaProducer<String, String> producer;

    @BeforeEach
    void before() {
        producer = new KafkaProducer<>(getDefaultProperties());
    }

    @AfterEach
    void after() {
        releaseFirstAttempt.countDown();
        if (producer != null) {
            producer.close();
        }
        kafkaAdminClient.deleteTopics(List.of(TOPIC_IN, TOPIC_OUT)).all();
    }

    @Test
    void staleConsumerGenerationAbortsOutputAndAllowsReplay() throws Exception {
        to.expectedMinimumMessageCount(2);
        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);
        producer.send(new ProducerRecord<>(TOPIC_IN, VALUE)).get(30, TimeUnit.SECONDS);
        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);

        assertTrue(firstAttemptStarted.await(30, TimeUnit.SECONDS), "The first transaction did not reach the gate");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var group = kafkaAdminClient.describeConsumerGroups(List.of(GROUP_ID))
                    .describedGroups().get(GROUP_ID).get(30, TimeUnit.SECONDS);
            assertEquals(GroupState.STABLE, group.groupState(), "Expected a stable source consumer group");
            assertEquals(1, group.members().size(), "Expected the Camel source consumer to be in the group");
        });

        // Remove the current member while its route exchange has an open Kafka producer transaction.
        // Its captured ConsumerGroupMetadata is now stale when sendOffsetsToTransaction is attempted.
        kafkaAdminClient.removeMembersFromConsumerGroup(
                GROUP_ID, new RemoveMembersFromConsumerGroupOptions()).all().get(30, TimeUnit.SECONDS);
        releaseFirstAttempt.countDown();

        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(
                attempts.get() >= 2, "The input record was not replayed after the stale generation was rejected"));
        to.assertIsSatisfied(60000);

        List<String> committed = consumeRecords("read_committed", 1);
        assertEquals(List.of(VALUE), committed);
        List<String> uncommitted = consumeRecords("read_uncommitted", 2);
        assertTrue(uncommitted.size() >= 2);
        assertEquals(uncommitted.size(), uncommitted.stream().filter(VALUE::equals).count());

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            OffsetAndMetadata offset = kafkaAdminClient.listConsumerGroupOffsets(GROUP_ID)
                    .partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS)
                    .get(new TopicPartition(TOPIC_IN, 0));
            assertNotNull(offset);
            assertEquals(1L, offset.offset());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kafka:%s?groupId=%s&exactlyOnce=true&autoOffsetReset=earliest"
                      + "&autoCommitEnable=false&allowManualCommit=true&breakOnFirstError=true"
                      + "&maxPollRecords=1&pollTimeoutMs=500"
                      + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        TOPIC_IN, GROUP_ID)
                        .routeId(ROUTE_ID)
                        .toF("kafka:%s?transacted=true&exactlyOnce=true&requestRequiredAcks=-1", TOPIC_OUT)
                        .process(exchange -> {
                            attempts.incrementAndGet();
                            if (blockFirstAttempt.compareAndSet(true, false)) {
                                firstAttemptStarted.countDown();
                                if (!releaseFirstAttempt.await(45, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "Timed out waiting for the test to release processing");
                                }
                            }
                        })
                        .to(to);
            }
        };
    }

    private List<String> consumeRecords(String isolationLevel, int expectedCount) {
        List<String> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = createConsumer(isolationLevel)) {
            consumer.subscribe(List.of(TOPIC_OUT));
            Awaitility.await().atMost(45, TimeUnit.SECONDS).untilAsserted(() -> {
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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "exactlyOnceRebalanceVerifier");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }
}
