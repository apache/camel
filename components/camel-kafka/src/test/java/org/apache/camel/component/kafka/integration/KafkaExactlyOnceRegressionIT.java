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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mock.MockEndpoint;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broker-backed regression routes for the exactly-once failure and edge-case scenarios in CAMEL-24781. Unsafe source
 * settings are rejected at route startup; supported routes are checked against read_committed output and the actual
 * committed source offsets in Kafka.
 */
@Tags({ @Tag("breakOnFirstError") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64" },
             disabledReason = "This test does not run reliably on some platforms")
class KafkaExactlyOnceRegressionIT extends BaseKafkaTestSupport {

    private static final String PREFIX = "exactlyOnceRegression";
    private KafkaProducer<String, String> producer;
    private final Set<String> createdTopics = new HashSet<>();
    private final String testRun = UUID.randomUUID().toString().replace("-", "");

    private String name(String suffix) {
        return PREFIX + testRun + suffix;
    }

    @BeforeEach
    void createProducer() {
        producer = new KafkaProducer<>(getDefaultProperties());
    }

    @AfterEach
    void stopRoutesAndCloseProducer() throws Exception {
        var context = contextExtension.getContext();
        try {
            context.getRouteController().stopAllRoutes();

            // Remove only routes installed by this test run; the CamelContext is shared by
            // every test method in this class.
            for (var route : List.copyOf(context.getRoutes())) {
                if (route.getId().startsWith(PREFIX + testRun)) {
                    context.removeRoute(route.getId());
                }
            }

            if (producer != null) {
                producer.close();
                producer = null;
            }

            if (!createdTopics.isEmpty()) {
                Set<String> existingTopics = kafkaAdminClient.listTopics().names().get(30, TimeUnit.SECONDS);
                createdTopics.retainAll(existingTopics);
                if (!createdTopics.isEmpty()) {
                    kafkaAdminClient.deleteTopics(createdTopics).all().get(30, TimeUnit.SECONDS);
                }
            }
            MockEndpoint.resetMocks(context);
        } finally {
            if (producer != null) {
                producer.close();
                producer = null;
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Routes are installed explicitly in each test so unsafe endpoint configuration can be
        // asserted at route start without failing the shared test CamelContext startup.
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no static routes
            }
        };
    }

    /** Review 1: EOS batching is rejected, so a pending batch can never be cleared after a rebalance. */
    @Test
    void rejectsBatchingWithExactlyOnceAtRealRouteStartup() throws Exception {
        String topic = name("BatchIn");
        String group = name("BatchGroup");
        String routeId = name("BatchRoute");
        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(topic, group, "&batching=true&maxPollRecords=6&pollTimeoutMs=10000"))
                        .routeId(routeId).autoStartup(false).to("mock:" + routeId);
            }
        });

        Exception failure = assertThrows(Exception.class,
                () -> contextExtension.getContext().getRouteController().startRoute(routeId));
        assertTrue(allMessages(failure).contains("batching=false"), allMessages(failure));
    }

    /** Exactly-once rejects multiple consumer workers sharing the same transactional producer. */
    @Test
    void rejectsMultipleConsumersAtRealRouteStartup() throws Exception {
        String topic = name("ConsumersIn");
        String group = name("ConsumersGroup");
        String routeId = name("ConsumersRoute");
        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(topic, group, "&consumersCount=2"))
                        .routeId(routeId).autoStartup(false).to("mock:" + routeId);
            }
        });

        Exception failure = assertThrows(Exception.class,
                () -> contextExtension.getContext().getRouteController().startRoute(routeId));
        assertTrue(allMessages(failure).contains("consumersCount=1"), allMessages(failure));
    }

    /**
     * An automatically generated endpoint/route transaction ID is rejected during route warm-up because it is not safe
     * across application replicas.
     */
    @Test
    void rejectsImplicitTransactionalIdForExactlyOnceDestinationDuringRouteWarmup() throws Exception {
        String in = name("ImplicitTxIn");
        String out = name("ImplicitTxOut");
        String group = name("ImplicitTxGroup");
        String routeId = name("ImplicitTxRoute");

        Exception failure = assertThrows(Exception.class, () -> addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .to("kafka:" + out + "?transacted=true&exactlyOnce=true");
            }
        }));
        assertTrue(allMessages(failure).contains("explicit transactionalId"), allMessages(failure));
    }

    /** Review 2a: successfully filtered records commit their offsets even without a Kafka destination. */
    @Test
    void filteredRecordsCommitSourceOffsetsAndAreNotReplayedAfterRestart() throws Exception {
        String in = name("FilterIn");
        String out = name("FilterOut");
        String group = name("FilterGroup");
        String routeId = name("FilterRoute");
        AtomicInteger processed = new AtomicInteger();

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .process(exchange -> processed.incrementAndGet())
                        .filter(header(KafkaConstants.KEY).startsWith("m"))
                            .to(sinkUri(out, ""))
                        .end();
            }
        });

        publish(in, "m0", "m0");
        publish(in, "skip1", "skip1");
        publish(in, "skip2", "skip2");
        publish(in, "skip3", "skip3");

        awaitOffset(group, in, 4);
        assertEquals(4, processed.get(), "The route must process every source record");
        assertEquals(List.of("m0"), readCommitted(out, 1));

        contextExtension.getContext().getRouteController().stopRoute(routeId);
        contextExtension.getContext().getRouteController().startRoute(routeId);
        Awaitility.await().during(2, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(4, processed.get(),
                        "Filtered records must not be replayed after route restart"));
        assertEquals(4, awaitOffset(group, in, 4).offset());
        assertEquals(List.of("m0"), readCommitted(out, 1));
    }

    /** Review 2b: forgetting exactlyOnce on a Kafka destination must fail visibly, never silently skip offsets. */
    @Test
    void rejectsKafkaDestinationWithoutExactlyOnceAndReplaysSource() throws Exception {
        String in = name("NonEosIn");
        String out = name("NonEosOut");
        String group = name("NonEosGroup");
        String routeId = name("NonEosRoute");
        AtomicInteger attempts = new AtomicInteger();

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .process(exchange -> attempts.incrementAndGet())
                        .to("kafka:" + out + "?transacted=true&requestRequiredAcks=-1");
            }
        });

        publish(in, "m0", "m0");
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(
                attempts.get() >= 2, "A non-EOS Kafka destination must fail and cause source replay"));
        OffsetAndMetadata offset = committedOffset(group, in);
        assertTrue(offset == null || offset.offset() == 0,
                "The rejected destination must not commit the source offset: " + offset);
        assertEquals(List.of(), readCommitted(out, 0));
        contextExtension.getContext().getRouteController().stopRoute(routeId);
    }

    /** Review 3: a handled poison send aborts output, then commits only the source offset in a new transaction. */
    @Test
    void deadLetterChannelSkipsHandledPoisonRecordWithoutReplayingIt() throws Exception {
        String in = name("DlqIn");
        String out = name("DlqOut");
        String group = name("DlqGroup");
        String routeId = name("DlqRoute");
        String dlqName = name("Dlq");
        String dlqDirect = "direct:" + dlqName;
        String dlqMock = "mock:" + dlqName;
        String poison = "x".repeat(4096);
        MockEndpoint dlq = contextExtension.getContext().getEndpoint(dlqMock, MockEndpoint.class);
        dlq.expectedMessageCount(1);
        dlq.setAssertPeriod(2000);

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel(dlqDirect).maximumRedeliveries(0));
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .to(sinkUri(out, "&maxRequestSize=1024"));
                from(dlqDirect)
                        .routeId(name("DlqProcessorRoute"))
                        .to(dlqMock);
            }
        });

        publish(in, "small-0", "small-0");
        publish(in, "big-1", poison);
        publish(in, "small-2", "small-2");

        MockEndpoint.assertIsSatisfied(contextExtension.getContext(), 60, TimeUnit.SECONDS);
        assertEquals(poison, dlq.getExchanges().get(0).getMessage().getBody(String.class));
        assertEquals(List.of("small-0", "small-2"), readCommitted(out, 2));
        assertEquals(3, awaitOffset(group, in, 3).offset());
        assertEquals(1, dlq.getReceivedCounter(), "A handled poison record must not be replayed to the DLQ");
    }

    /** A failed Kafka DLQ delivery must leave the poison source record uncommitted, even when the DLC handles it. */
    @Test
    void failedKafkaDeadLetterDeliveryReplaysTheSourceRecord() throws Exception {
        assertFailedKafkaDeadLetterDeliveryIsReplayed(false);
        assertFailedKafkaDeadLetterDeliveryIsReplayed(true);
    }

    private void assertFailedKafkaDeadLetterDeliveryIsReplayed(boolean exactlyOnceDlq) throws Exception {
        String suffix = exactlyOnceDlq ? "ExactlyOnce" : "Plain";
        String in = name("KafkaDlq" + suffix + "In");
        String out = name("KafkaDlq" + suffix + "Out");
        String dlq = name("KafkaDlq" + suffix);
        String group = name("KafkaDlq" + suffix + "Group");
        String routeId = name("KafkaDlq" + suffix + "Route");
        String dlqUri = exactlyOnceDlq
                ? sinkUri(dlq, "&maxRequestSize=1024")
                : "kafka:" + dlq + "?requestRequiredAcks=-1&maxRequestSize=1024"
                  + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
                  + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer";
        String poison = "x".repeat(4096);
        AtomicInteger poisonAttempts = new AtomicInteger();

        // Pre-create the DLQ topic with a sentinel so the assertion can prove that the poison record was not written.
        publish(dlq, "sentinel", "sentinel");

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel(dlqUri).maximumRedeliveries(0));
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .process(exchange -> {
                            if ("big-1".equals(exchange.getIn().getHeader(KafkaConstants.KEY, String.class))) {
                                poisonAttempts.incrementAndGet();
                            }
                        })
                        .to(sinkUri(out, "&maxRequestSize=1024"));
            }
        });

        publish(in, "small-0", "small-0");
        publish(in, "big-1", poison);
        publish(in, "small-2", "small-2");

        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(poisonAttempts.get() >= 2, "The failed poison record must be replayed"));
        assertEquals(List.of("small-0"), readCommitted(out, 1));
        assertEquals(List.of("sentinel"), readCommitted(dlq, 1));
        OffsetAndMetadata offset = committedOffset(group, in);
        assertTrue(offset == null || offset.offset() == 1,
                "A failed Kafka DLQ delivery must not commit the poison record: " + offset);
        contextExtension.getContext().getRouteController().stopRoute(routeId);
    }

    /** A split without aggregation is rejected; its child exchanges cannot claim the source offsets. */
    @Test
    void splitFanOutWithoutAggregationReplaysTheSourceRecord() throws Exception {
        String in = name("SplitFanOutIn");
        String out = name("SplitFanOutOut");
        String group = name("SplitFanOutGroup");
        String routeId = name("SplitFanOutRoute");
        AtomicInteger attempts = new AtomicInteger();

        publish(out, "sentinel", "sentinel");
        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .process(exchange -> attempts.incrementAndGet())
                        .split(body().tokenize(","))
                            .to(sinkUri(out, ""))
                        .end();
            }
        });

        publish(in, "m0", "a,b,c");
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(attempts.get() >= 2, "An unsupported split fan-out must replay the source record"));
        assertEquals(List.of("sentinel"), readCommitted(out, 1));
        OffsetAndMetadata offset = committedOffset(group, in);
        assertTrue(offset == null || offset.offset() == 0,
                "Split child exchanges must not commit the source offset: " + offset);
        contextExtension.getContext().getRouteController().stopRoute(routeId);
    }

    /** The documented grouped-body pattern aggregates split children before the original exchange claims offsets. */
    @Test
    void groupedSplitAggregationSendsAllRecordsInOneExactlyOnceTransaction() throws Exception {
        String in = name("GroupedSplitIn");
        String out = name("GroupedSplitOut");
        String group = name("GroupedSplitGroup");
        String routeId = name("GroupedSplitRoute");

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .split(body().tokenize(","), AggregationStrategies.groupedBody())
                            .transform(body().append("!"))
                        .end()
                        .to(sinkUri(out, ""));
            }
        });

        publish(in, "m0", "a,b,c");
        assertEquals(List.of("a!", "b!", "c!"), readCommitted(out, 3));
        assertEquals(1, awaitOffset(group, in, 1).offset());
    }

    /** Review 4: message-header removal must not remove exchange-property source metadata. */
    @Test
    void removeHeadersDoesNotLoseExactlyOnceSourceMetadata() throws Exception {
        String in = name("HeadersIn");
        String out = name("HeadersOut");
        String group = name("HeadersGroup");
        String routeId = name("HeadersRoute");

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .removeHeaders("*")
                        .to(sinkUri(out, ""));
            }
        });

        publish(in, "m0", "m0");
        assertEquals(List.of("m0"), readCommitted(out, 1));
        assertEquals(1, awaitOffset(group, in, 1).offset());
    }

    /** Review 5: the transaction synchronization stays with the original exchange across SEDA hand-over. */
    @Test
    void sedaAfterKafkaProducerDoesNotLeakExactlyOnceProducerSlot() throws Exception {
        String in = name("SedaIn");
        String out = name("SedaOut");
        String group = name("SedaGroup");
        String routeId = name("SedaRoute");
        String auditRouteId = name("SedaAuditRoute");
        MockEndpoint audit = contextExtension.getContext().getEndpoint("mock:" + name("SedaAudit"), MockEndpoint.class);
        audit.expectedMessageCount(3);
        audit.setAssertPeriod(1500);

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .to(sinkUri(out, ""))
                        .to("seda:" + name("SedaAudit"));
                from("seda:" + name("SedaAudit"))
                        .routeId(auditRouteId)
                        .to("mock:" + name("SedaAudit"));
            }
        });

        publish(in, "m0", "m0");
        publish(in, "m1", "m1");
        publish(in, "m2", "m2");

        MockEndpoint.assertIsSatisfied(contextExtension.getContext(), 60, TimeUnit.SECONDS);
        assertEquals(List.of("m0", "m1", "m2"), readCommitted(out, 3));
        assertEquals(3, awaitOffset(group, in, 3).offset());
        assertEquals(3, audit.getReceivedCounter());
    }

    /** Review 6: seekTo must not override the committed-offset replay position on reconnect. */
    @Test
    void rejectsBothSeekPoliciesAtRealRouteStartup() throws Exception {
        assertSeekRejected("BEGINNING");
        assertSeekRejected("END");
    }

    /** Review 7: the parent exchange after split aggregation may claim the original source metadata once. */
    @Test
    void splitAggregationCanSendOneExactlyOnceParentTransaction() throws Exception {
        String in = name("SplitIn");
        String out = name("SplitOut");
        String group = name("SplitGroup");
        String routeId = name("SplitRoute");

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .split(body().tokenize(","), AggregationStrategies.useLatest())
                            .transform(body().append("!"))
                        .end()
                        .to(sinkUri(out, ""));
            }
        });

        publish(in, "m0", "a,b");
        assertEquals(List.of("b!"), readCommitted(out, 1));
        assertEquals(1, awaitOffset(group, in, 1).offset());
    }

    /** Regression scenario: a wireTap copy cannot claim the source offset ahead of the main output. */
    @Test
    void wireTapExactlyOnceProducerCannotStealSourceOffsetClaim() throws Exception {
        String in = name("WireTapIn");
        String mainOut = name("WireTapMainOut");
        String auditOut = name("WireTapAuditOut");
        String group = name("WireTapGroup");
        String routeId = name("WireTapRoute");

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .wireTap(sinkUri(auditOut, ""))
                        .to(sinkUri(mainOut, ""));
            }
        });

        publish(in, "m0", "m0");
        assertEquals(List.of("m0"), readCommitted(mainOut, 1));
        assertEquals(1, awaitOffset(group, in, 1).offset());
        assertEquals(List.of(), readCommitted(auditOut, 0));
    }

    /** Review 8: rollback-only must be reported to the poller and replayed, not silently skipped. */
    @Test
    void markRollbackOnlyAbortsAndReplaysRecordBeforeCommittingIt() throws Exception {
        String in = name("RollbackIn");
        String out = name("RollbackOut");
        String group = name("RollbackGroup");
        String routeId = name("RollbackRoute");
        AtomicInteger m1Attempts = new AtomicInteger();
        AtomicBoolean rollbackFirstM1 = new AtomicBoolean();

        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(in, group, ""))
                        .routeId(routeId)
                        .process(exchange -> {
                            if ("m1".equals(exchange.getIn().getHeader(KafkaConstants.KEY, String.class))) {
                                m1Attempts.incrementAndGet();
                            }
                        })
                        .to(sinkUri(out, ""))
                        .filter(header(KafkaConstants.KEY).isEqualTo("m1"))
                        .filter(exchange -> rollbackFirstM1.compareAndSet(false, true))
                        .markRollbackOnly()
                        .end()
                        .end();
            }
        });

        publish(in, "m0", "m0");
        publish(in, "m1", "m1");
        publish(in, "m2", "m2");

        assertEquals(List.of("m0", "m1", "m2"), readCommitted(out, 3));
        assertTrue(m1Attempts.get() >= 2, "The rolled-back m1 exchange must be replayed");
        assertEquals(3, awaitOffset(group, in, 3).offset());
    }

    private void assertSeekRejected(String seekTo) throws Exception {
        String routeId = name("Seek" + seekTo + "Route");
        String topic = name("Seek" + seekTo + "In");
        String group = name("Seek" + seekTo + "Group");
        addRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri(topic, group, "&seekTo=" + seekTo))
                        .routeId(routeId).autoStartup(false).to("mock:" + routeId);
            }
        });
        Exception failure = assertThrows(Exception.class,
                () -> contextExtension.getContext().getRouteController().startRoute(routeId));
        assertTrue(allMessages(failure).contains("seekTo"), allMessages(failure));
    }

    private void addRoute(RouteBuilder routeBuilder) throws Exception {
        contextExtension.getContext().addRoutes(routeBuilder);
    }

    private static String sourceUri(String topic, String group, String extra) {
        String maxPollRecords = extra.contains("maxPollRecords=") ? "" : "&maxPollRecords=1";
        String pollTimeout = extra.contains("pollTimeoutMs=") ? "" : "&pollTimeoutMs=500";
        return "kafka:" + topic + "?groupId=" + group
               + "&exactlyOnce=true&autoOffsetReset=earliest&autoCommitEnable=false"
               + "&allowManualCommit=true&breakOnFirstError=true" + maxPollRecords + pollTimeout
               + "&keyDeserializer=" + StringDeserializer.class.getName()
               + "&valueDeserializer=" + StringDeserializer.class.getName() + extra;
    }

    private String sinkUri(String topic, String extra) {
        // Exactly-once producers need a stable transactional.id that is unique across producer instances and
        // application replicas. The per-test run id keeps the integration-test producers isolated.
        String transactionalId = name("Producer" + UUID.randomUUID().toString().replace("-", ""));
        return "kafka:" + topic + "?exactlyOnce=true&transactionalId=" + transactionalId
               + "&requestRequiredAcks=-1"
               + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
               + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer" + extra;
    }

    private void publish(String topic, String key, String value) throws Exception {
        createdTopics.add(topic);
        producer.send(new ProducerRecord<>(topic, key, value)).get(30, TimeUnit.SECONDS);
        producer.flush();
    }

    private List<String> readCommitted(String topic, int expectedCount) {
        createdTopics.add(topic);
        List<String> values = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, PREFIX + "Verifier-" + UUID.randomUUID());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> verifier = new KafkaConsumer<>(properties)) {
            verifier.subscribe(List.of(topic));
            if (expectedCount == 0) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (System.nanoTime() < deadline) {
                    ConsumerRecords<String, String> records = verifier.poll(Duration.ofMillis(250));
                    assertTrue(records.isEmpty(),
                            "Expected no committed output records for " + topic + " but received " + records.count());
                }
                return values;
            }
            Awaitility.await().atMost(40, TimeUnit.SECONDS)
                    .pollInterval(250, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = verifier.poll(Duration.ofMillis(500));
                        records.forEach(record -> values.add(record.value()));
                        assertEquals(expectedCount, values.size(),
                                "Unexpected committed records for " + topic + ": " + values);
                    });
            // Continue polling briefly so a delayed duplicate is also detected.
            for (int i = 0; i < 4; i++) {
                ConsumerRecords<String, String> records = verifier.poll(Duration.ofMillis(250));
                records.forEach(record -> values.add(record.value()));
            }
        }
        assertEquals(expectedCount, values.size(), "Duplicate committed output records for " + topic + ": " + values);
        return values;
    }

    private OffsetAndMetadata awaitOffset(String group, String topic, long expectedOffset) throws Exception {
        Awaitility.await().atMost(40, TimeUnit.SECONDS).pollInterval(250, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            OffsetAndMetadata current = committedOffset(group, topic);
            assertNotNull(current, "No source offset committed for " + group + " / " + topic);
            assertEquals(expectedOffset, current.offset());
        });
        return committedOffset(group, topic);
    }

    private OffsetAndMetadata committedOffset(String group, String topic) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> offsets = kafkaAdminClient
                .listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS);
        return offsets.get(new TopicPartition(topic, 0));
    }

    private static String allMessages(Throwable failure) {
        StringBuilder answer = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            answer.append(current.getMessage()).append('\n');
        }
        return answer.toString();
    }
}
