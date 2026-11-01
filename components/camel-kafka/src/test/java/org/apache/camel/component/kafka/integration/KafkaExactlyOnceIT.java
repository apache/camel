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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Kafka exactly-once processing using real Kafka brokers and real Camel routes.
 *
 * <p>
 * The scenarios cover transaction rollback and replay, error handling, offsets, batching and route patterns.
 */
@Tags({ @Tag("breakOnFirstError") })
class KafkaExactlyOnceIT extends KafkaExactlyOnceITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaExactlyOnceIT.class);

    /** Verifies aborted output is invisible and the successful replay commits the output and source offset. */
    @Test
    @EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
                 architectures = { "amd64", "aarch64" },
                 disabledReason = "This test does not run reliably on some platforms")
    void exactlyOnceCommitsOffsetsAndRecordsAtomically() throws Exception {
        String in = topics("in", "out")[0];
        AtomicBoolean failureTriggered = new AtomicBoolean();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id)
                        .process(countAttempt())
                        .removeHeaders("*")
                        .to(sink(id + "-out", "tx"))
                        .process(exchange -> {
                            if ("message-2".equals(exchange.getMessage().getBody(String.class))
                                    && failureTriggered.compareAndSet(false, true)) {
                                throw new IllegalStateException("failure injected after the Kafka output send");
                            }
                        });
            }
        });

        produce(in, "message-0", "message-1", "message-2", "message-3", "message-4");
        awaitCommitted(in, 5);

        assertTrue(failureTriggered.get(), "The deliberate route failure was not triggered");
        assertEquals(2, attempts("message-2"), "The failed source record must be replayed once");
        assertEquals(List.of("message-0", "message-1", "message-2", "message-3", "message-4"),
                readCommittedValues(id + "-out"));
        assertEquals(Long.valueOf(5), committed(id, in), "The output and next source offset must commit together");
    }

    // Transaction and route behavior

    @Test
    void markRollbackOnlyReplaysTheRecord() throws Exception {
        String in = topics("in", "out")[0];
        AtomicBoolean rolledBack = new AtomicBoolean();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt()).to(sink(id + "-out", "tx"))
                        .filter(e -> "m1".equals(key(e)) && rolledBack.compareAndSet(false, true))
                        .markRollbackOnly().end();
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Exactly-once rollbackOnly: attempts={} out={} committed={}", attempts, out,
                committed(id, in));
        assertEquals(List.of("m0", "m1", "m2"), out);
        assertEquals(2, attempts("m1"));
    }

    @Test
    void removeHeadersKeepsTheOffsets() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt()).removeHeaders("*").to(sink(id + "-out", "tx"));
            }
        });
        produce(in, "m0", "m1");
        awaitCommitted(in, 2);
        List<String> out = readCommittedValues(id + "-out");
        LOG.info("Exactly-once removeHeaders: attempts={} out={}", attempts, out);
        assertEquals(List.of("m0", "m1"), out);
    }

    @Test
    void splitWithAggregationStrategySendsTheParent() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .split(body().tokenize(","), AggregationStrategies.useLatest())
                        .transform(body().append("!")).end()
                        .to(sink(id + "-out", "tx"));
            }
        });
        produce(in, Map.of("m0", "a,b,c"));
        awaitCommitted(in, 1);
        List<String> out = readCommittedValues(id + "-out");
        LOG.info("Exactly-once split+aggregation: attempts={} out={}", attempts, out);
        assertEquals(List.of("c!"), out);
        assertEquals(1, attempts("m0"));
    }

    @Test
    void sedaAfterTheProducerDoesNotWedgeTheRoute() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt()).to(sink(id + "-out", "tx")).to("seda:audit");
                from("seda:audit").to("log:audit?level=DEBUG");
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Exactly-once seda after producer: attempts={} out={} failures={}", attempts, out, sample());
        assertEquals(List.of("m0", "m1", "m2"), out);
    }

    @Test
    void deadLetterChannelWithoutRedeliverySkipsThePoisonRecord() throws Exception {
        String in = topics("in", "out")[0];
        AtomicInteger dlq = new AtomicInteger();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("direct:dlq"));
                from("direct:dlq").process(e -> dlq.incrementAndGet());
                from(source(in)).routeId(id).process(countAttempt())
                        .to(sink(id + "-out", "tx") + "&maxRequestSize=1024");
            }
        });
        produce(in, poisonRecords());
        awaitCommitted(in, 3);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Exactly-once DLC no redelivery: attempts={} dlq={} out={}", attempts, dlq.get(), out);
        assertEquals(List.of("small-0", "small-2"), out);
        assertEquals(1, dlq.get());
    }

    @Test
    void filteredRecordsCommitTheirOffsets() throws Exception {
        String in = topics("in", "out")[0];
        CamelContext context = start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .filter(header(KafkaConstants.KEY).startsWith("m")).to(sink(id + "-out", "tx"));
            }
        });
        produce(in, "m0", "skip1", "skip2", "skip3");
        awaitCommitted(in, 4);
        context.getRouteController().stopRoute(id);
        context.getRouteController().startRoute(id);
        produce(in, "m4");
        awaitCommitted(in, 5);
        LOG.info("Exactly-once filter: attempts={}", attempts);
        assertEquals(1, attempts("skip1"), "not consumed again after the restart");
    }

    @Test
    void producerWithoutExactlyOnceIsRejectedAndTheRecordReplays() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt()).to("kafka:" + id + "-out?transacted=true");
            }
        });
        produce(in, "m0");
        await().atMost(60, TimeUnit.SECONDS).until(() -> attempts("m0") >= 2);
        LOG.info("Exactly-once producer without exactlyOnce: attempts={} committed={} out={} failures={}", attempts,
                committed(id, in), readCommittedKeys(id + "-out"), sample());
        assertNull(committed(id, in));
        assertTrue(readCommittedKeys(id + "-out").isEmpty());
    }

    @Test
    void repositioningOptionsAreRejectedAtStartup() throws Exception {
        String in = topics("in")[0];
        for (String extra : List.of("&seekTo=BEGINNING", "&seekTo=END", "&batching=true", "&consumersCount=2")) {
            CamelContext context = newContext();
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(source(in) + extra).to(sink(id + "-out", "tx"));
                }
            });
            Exception e = assertThrows(Exception.class, context::start, extra);
            LOG.info("Exactly-once {} rejected: {}", extra, rootMessage(e));
            context.stop();
        }
    }

    @Test
    void wireTapNextToTheMainSendCannotSkipAFailedRecord() throws Exception {
        String in = topics("in", "out", "audit")[0];
        AtomicBoolean failed = new AtomicBoolean();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .wireTap("direct:audit")
                        .to(sink(id + "-out", "tx"))
                        .delay(1000)
                        .process(failOnce("m1", failed));
                from("direct:audit").to(sink(id + "-audit", "audit"));
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Exactly-once wireTap next to main send: attempts={} out={} audit={} failures={}", attempts, out,
                readCommittedKeys(id + "-audit"), sample());
        assertEquals(List.of("m0", "m1", "m2"), out);
    }

    /**
     * A WireTap target may contain a Kafka destination in one Choice branch while this exchange takes a non-Kafka
     * branch. The static route model must not mark the WireTap as Kafka-directed merely because an unvisited branch
     * contains a Kafka producer.
     */
    @Test
    void filteredRecordCommitsWhenWireTapChoiceTakesNonKafkaBranch() throws Exception {
        String in = topics("in", "out", "audit")[0];
        AtomicInteger nonKafkaAudit = new AtomicInteger();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .filter(header(KafkaConstants.KEY).startsWith("keep"))
                            .to(sink(id + "-out", "tx"))
                        .end()
                        .wireTap("direct:audit");

                from("direct:audit").routeId(id + "-audit")
                        .choice()
                            .when(header("auditToKafka").isEqualTo(true))
                                .to(sink(id + "-audit", "audit"))
                            .otherwise()
                                .process(e -> nonKafkaAudit.incrementAndGet())
                        .end();
            }
        });

        produce(in, "skip0", "skip1");
        boolean committed = awaitQuietly(() -> Long.valueOf(2).equals(committed(id, in)), 30);
        boolean audited = awaitQuietly(() -> nonKafkaAudit.get() == 2, 20);

        assertTrue(committed,
                "Filtered source records must commit when the executed WireTap Choice branch is non-Kafka; "
                              + "attempts=" + attempts + " failures=" + sample());
        assertTrue(audited, "The WireTap's non-Kafka branch must run for both source records");
        assertEquals(1, attempts("skip0"), "The first filtered record must not be replayed");
        assertEquals(1, attempts("skip1"), "The second filtered record must not be replayed");
        assertTrue(readCommittedKeys(id + "-out").isEmpty(), "The filter must not send to the main Kafka topic");
        assertTrue(readCommittedKeys(id + "-audit").isEmpty(),
                "The executed WireTap Choice branch must not send to its alternative Kafka topic");
    }

    /**
     * A selected Kafka branch in a WireTap target must be observed before the source consumer commits offsets. The
     * delay ensures the source route reaches its completion check while the copied route is still running.
     */
    @Test
    void wireTapChoiceKafkaBranchPreventsPrematureSourceOffsetCommit() throws Exception {
        String in = topics("in", "out", "audit")[0];
        AtomicInteger kafkaBranchSelected = new AtomicInteger();
        AtomicInteger nonKafkaBranchSelected = new AtomicInteger();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt()).wireTap("direct:audit");

                from("direct:audit").routeId(id + "-audit")
                        .delay(300)
                        .choice()
                            .when(header(KafkaConstants.KEY).startsWith("audit"))
                                .process(e -> kafkaBranchSelected.incrementAndGet())
                                .to(sink(id + "-audit", "audit"))
                            .otherwise()
                                .process(e -> nonKafkaBranchSelected.incrementAndGet())
                        .end();
            }
        });

        produce(in, "audit0");
        boolean replayed = awaitQuietly(() -> attempts("audit0") >= 2, 30);
        Long committedOffset = committed(id, in);
        LOG.info("Exactly-once WireTap conditional Kafka branch: replayed={} committed={} selected={} audit={} "
                 + "failures={}",
                replayed, committedOffset, kafkaBranchSelected.get(),
                readCommittedKeys(id + "-audit"), sample());

        assertTrue(kafkaBranchSelected.get() >= 1, "The Kafka branch in the WireTap target must actually execute");
        assertEquals(0, nonKafkaBranchSelected.get(), "The non-Kafka Choice branch must not execute");
        assertTrue(replayed, "The source record must replay when the executed WireTap branch invokes Kafka");
        assertTrue(committedOffset == null || committedOffset == 0L,
                "The source offset must not commit when the asynchronous Kafka copy is rejected: " + committedOffset);
        assertTrue(readCommittedKeys(id + "-audit").isEmpty(),
                "The copied exchange must not commit a Kafka output transaction");
    }

    // Dead-letter and unsupported route patterns

    /** A Kafka DLQ topic is the usual dead letter channel of a Kafka route. */
    @Test
    void deadLetterChannelToAKafkaTopic() throws Exception {
        deadLetterToKafka("kafka:" + id + "-dlq");
    }

    @Test
    void deadLetterChannelToAnExactlyOnceKafkaTopic() throws Exception {
        deadLetterToKafka("kafka:" + id + "-dlq?exactlyOnce=true&transactionalId=" + id + "-dlq-tx");
    }

    private void deadLetterToKafka(String dlqUri) throws Exception {
        String in = topics("in", "out", "dlq")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel(dlqUri));
                from(source(in)).routeId(id).process(countAttempt())
                        .to(sink(id + "-out", "tx") + "&maxRequestSize=1024");
            }
        });
        produce(in, poisonRecords());
        boolean committed3 = awaitQuietly(() -> Long.valueOf(3).equals(committed(id, in)), 45);
        List<String> out = readCommittedKeys(id + "-out");
        List<String> dlq = readCommittedKeys(id + "-dlq");
        LOG.info("Exactly-once {}: dlqUri={} committedOffset={} out={} dlqTopic={} attempts={} failures={}", id, dlqUri,
                committed(id, in), out, dlq, attempts, sample());
        assertTrue(committed3, "the route never moved past big-1: attempts=" + attempts);
        assertEquals(List.of("small-0", "small-2"), out,
                "The failed source output transaction must be aborted");
        assertEquals(1, dlq.stream().filter("big-1"::equals).count(),
                "The poison source record must be delivered to the Kafka DLQ exactly once: " + dlq);
    }

    /** A dead letter channel usually redelivers a few times first. */
    @Test
    void deadLetterChannelWithRedeliveries() throws Exception {
        String in = topics("in", "out")[0];
        AtomicInteger dlq = new AtomicInteger();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("direct:dlq").maximumRedeliveries(2).redeliveryDelay(0));
                from("direct:dlq").process(e -> dlq.incrementAndGet());
                from(source(in)).routeId(id).process(countAttempt())
                        .to(sink(id + "-out", "tx") + "&maxRequestSize=1024");
            }
        });
        produce(in, poisonRecords());
        boolean committed3 = awaitQuietly(() -> Long.valueOf(3).equals(committed(id, in)), 45);
        LOG.info("Exactly-once DLC maximumRedeliveries(2): committed3={} committedOffset={} attempts={} dlq={} out={}"
                 + " failures={}",
                committed3, committed(id, in), attempts, dlq.get(), readCommittedKeys(id + "-out"), sample());
        assertTrue(committed3, "the poison record is never skipped: attempts=" + attempts + " dlq=" + dlq.get());
        assertEquals(List.of("small-0", "small-2"), readCommittedKeys(id + "-out"),
                "The poison record output must be aborted while adjacent records commit");
        assertEquals(1, dlq.get());
    }

    /** The only Kafka send is in a SEDA route. */
    @Test
    void sedaIsTheOnlyPathToKafka() throws Exception {
        onlyKafkaPathIsACopy(true);
    }

    /** The only Kafka send is behind a wireTap. */
    @Test
    void wireTapIsTheOnlyPathToKafka() throws Exception {
        onlyKafkaPathIsACopy(false);
    }

    private void onlyKafkaPathIsACopy(boolean seda) throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                if (seda) {
                    from(source(in)).routeId(id).process(countAttempt()).to("seda:out");
                    from("seda:out").to(sink(id + "-out", "tx"));
                } else {
                    from(source(in)).routeId(id).process(countAttempt())
                            .wireTap("direct:out").to("log:main?level=DEBUG");
                    from("direct:out").to(sink(id + "-out", "tx"));
                }
            }
        });
        produce(in, "m0", "m1", "m2");
        boolean committed3 = awaitQuietly(() -> Long.valueOf(3).equals(committed(id, in)), 30);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Exactly-once {}: committedOffset={} out={} attempts={} failures={}", id,
                committed(id, in), out, attempts, sample());
        if (committed3) {
            assertEquals(List.of("m0", "m1", "m2"), out, "offsets committed, so the outputs must exist");
        } else {
            assertTrue(out.isEmpty(), "A replaying source must not have a committed asynchronous-only output");
        }
    }

    /** One input record fans out into several output records. */
    @Test
    void splitFanOutToKafka() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .split(body().tokenize(",")).to(sink(id + "-out", "tx")).end();
            }
        });
        produce(in, Map.of("m0", "a,b,c"));
        boolean committed1 = awaitQuietly(() -> Long.valueOf(1).equals(committed(id, in)), 30);
        List<String> out = readCommittedValues(id + "-out");
        LOG.info(
                "Exactly-once split fan-out: committed1={} committedOffset={} out={} attempts={} failures={}",
                committed1, committed(id, in), out, attempts, sample());
        if (committed1) {
            assertEquals(List.of("a", "b", "c"), out, "offset committed, so every split output must exist once");
        } else {
            assertTrue(out.isEmpty(), "A replaying split must not expose a partial committed fan-out");
        }
    }

    /** Workaround for the fan-out: one send of an iterable body, so every record is in one transaction. */
    @Test
    void fanOutAsOneIterableSend() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .split(body().tokenize(","), AggregationStrategies.groupedBody())
                        .transform(body().append("!")).end()
                        .to(sink(id + "-out", "tx"));
            }
        });
        produce(in, Map.of("m0", "a,b,c"));
        boolean committed1 = awaitQuietly(() -> Long.valueOf(1).equals(committed(id, in)), 30);
        List<String> out = readCommittedValues(id + "-out");
        LOG.info(
                "Exactly-once fan-out as one iterable send: committed1={} out={} attempts={} failures={}",
                committed1, out, attempts, sample());
        assertTrue(committed1);
        assertEquals(List.of("a!", "b!", "c!"), out);
    }

    /** Two routes write to the same sink endpoint URI, so to the same transactionalId. */
    @Test
    void twoRoutesShareOneSinkUri() throws Exception {
        String in1 = topics("in1", "in2", "out")[0];
        String in2 = id + "-in2";
        String sink = sink(id + "-out", "shared");
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in1, id + "-g1")).routeId(id + "-r1").process(countAttempt()).to(sink);
                from(source(in2, id + "-g2")).routeId(id + "-r2").process(countAttempt()).to(sink);
            }
        });
        String[] a = new String[10];
        String[] b = new String[10];
        for (int i = 0; i < 10; i++) {
            a[i] = "a" + i;
            b[i] = "b" + i;
        }
        long start = System.currentTimeMillis();
        produce(in1, a);
        produce(in2, b);
        boolean drained = awaitQuietly(() -> readCommittedKeys(id + "-out").size() >= 20, 90);
        List<String> out = readCommittedKeys(id + "-out");
        long fenced = failures.stream().filter(f -> f.contains("Fenced") || f.contains("fenced")
                || f.contains("InvalidProducerEpoch")).count();
        LOG.info(
                "Exactly-once shared transactionalId: drained={} in {} ms out={} (distinct {}) "
                 + "attempts={} fencedFailures={} failures={}",
                drained, System.currentTimeMillis() - start, out.size(), out.stream().distinct().count(),
                attempts.values().stream().mapToInt(AtomicInteger::get).sum(), fenced, sample());
        assertTrue(drained, "not all records delivered");
        assertEquals(20, out.size(), "exactly once");
        assertEquals(20L, out.stream().distinct().count(), "all records from both source routes must be unique");
        assertEquals(0L, fenced, "the route-scoped transactional producers must not fence each other");
    }

}
