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
    void wireTapToKafkaTargetIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out", "audit")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:audit").to(sink(id + "-out", "tx"));
                from("direct:audit").to(sink(id + "-audit", "audit"));
            }
        });
    }

    /**
     * A conditional WireTap target that contains a Kafka destination is rejected before polling. The branch selected
     * for any individual record cannot make a copied exchange part of the source transaction.
     */
    @Test
    void wireTapTargetWithConditionalKafkaBranchIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out", "audit")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:audit");
                from("direct:audit").routeId(id + "-audit")
                        .choice()
                            .when(header("auditToKafka").isEqualTo(true))
                                .to(sink(id + "-audit", "audit"))
                            .otherwise()
                                .log("non-Kafka WireTap branch")
                        .end();
            }
        });
    }

    @Test
    void wireTapRouteChainedThroughSedaToKafkaIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out", "audit")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:audit");
                from("direct:audit").to("seda:audit");
                from("seda:audit").to(sink(id + "-audit", "audit"));
            }
        });
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
        onlyKafkaPathIsACopy();
    }

    /** A dynamic send inside a WireTap target cannot be proven non-Kafka at startup and is rejected. */
    @Test
    void wireTapWithDynamicTargetIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:out");
                from("direct:out").toD("${header.destination}");
            }
        });
    }

    @Test
    void wireTapToANonKafkaTargetRemainsSupported() throws Exception {
        String in = topics("in", "out")[0];
        AtomicInteger tapped = new AtomicInteger();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        .wireTap("direct:audit")
                        .to(sink(id + "-out", "tx"));
                from("direct:audit").process(e -> tapped.incrementAndGet());
            }
        });
        produce(in, "m0", "m1");
        awaitCommitted(in, 2);
        assertEquals(List.of("m0", "m1"), readCommittedKeys(id + "-out"));
        assertTrue(awaitQuietly(() -> tapped.get() == 2, 20),
                "A statically non-Kafka WireTap target should remain available");
    }

    @Test
    void filteredRecordWithNonKafkaWireTapStillCommits() throws Exception {
        String in = topics("in", "out")[0];
        AtomicInteger tapped = new AtomicInteger();
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id)
                        .wireTap("direct:audit")
                        .filter(body().isEqualTo("m0"))
                        .to(sink(id + "-out", "tx"));
                from("direct:audit").to("seda:audit");
                from("seda:audit").process(e -> tapped.incrementAndGet());
            }
        });
        produce(in, "m0", "m1");
        awaitCommitted(in, 2);
        assertEquals(List.of("m0"), readCommittedValues(id + "-out"));
        assertTrue(awaitQuietly(() -> tapped.get() == 2, 20),
                "The non-Kafka WireTap must still process both source records");
    }

    private void onlyKafkaPathIsACopy() throws Exception {
        String in = topics("in", "out")[0];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt()).to("seda:out");
                from("seda:out").to(sink(id + "-out", "tx"));
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

    @Test
    void kafkaSendAfterDoTryCatchSucceedsNormally() throws Exception {
        String in = topics("in", "out")[0];
        String publishUri = "direct:" + id + "-publish";
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        // This send and the send after doCatch must remain in the same source transaction.
                        .to(publishUri)
                        .doTry()
                        .process(exchange -> {
                            if ("m1".equals(key(exchange))) {
                                throw new IllegalArgumentException("Injected controlled error");
                            }
                        })
                        .doCatch(IllegalArgumentException.class)
                        .log("Caught expected exception, continuing...")
                        .end()
                        .to(publishUri);

                from(publishUri).routeId(id + "-publish").to(sink(id + "-out", "tx"));
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Kafka sends before and after doTry/doCatch: out={}", out);
        assertEquals(List.of("m0", "m0", "m1", "m1", "m2", "m2"), out,
                "Both Kafka sends must be treated as normal sends in the source transaction");
    }

    @Test
    void kafkaSendInsideDoCatchUsesFailureHandlerDelivery() throws Exception {
        String[] names = topics("in", "out", "catch");
        String in = names[0];
        String out = names[1];
        String catchTopic = names[2];
        String publishUri = "direct:" + id + "-publish";
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).process(countAttempt())
                        // Sends before and after doCatch remain in the original source transaction.
                        .to(publishUri)
                        .doTry()
                        .process(exchange -> {
                            if ("m1".equals(key(exchange))) {
                                throw new IllegalArgumentException("Injected controlled error");
                            }
                        })
                        .doCatch(IllegalArgumentException.class)
                        // A Kafka send inside the catch body uses a separate failure-handler transaction.
                        .to(sink(catchTopic, "catch"))
                        .end()
                        .to(publishUri);

                from(publishUri).routeId(id + "-publish").to(sink(out, "tx"));
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);

        List<String> outRecords = readCommittedKeys(out);
        List<String> caughtRecords = readCommittedKeys(catchTopic);
        LOG.info("Exactly-once Kafka send inside doCatch: out={} catch={}", outRecords, caughtRecords);
        assertEquals(List.of("m0", "m0", "m1", "m1", "m2", "m2"), outRecords,
                "The source transaction must remain usable after the doCatch body completes");
        assertEquals(List.of("m1"), caughtRecords,
                "A Kafka send inside doCatch must use a separate failure-handler producer transaction");
        assertEquals(1, attempts("m1"), "The locally caught failure must not replay the source record");
    }

    @Test
    void kafkaSendAfterContinuedExceptionSucceedsNormally() throws Exception {
        String in = topics("in", "out")[0];
        String publishUri = "direct:" + id + "-publish";
        start(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalArgumentException.class)
                        .continued(true)
                        .log("Handled via continued exception");

                from(source(in)).routeId(id).process(countAttempt())
                        // The send after continued(true) must not be mistaken for a DLQ send.
                        .to(publishUri)
                        .process(exchange -> {
                            if ("m1".equals(key(exchange))) {
                                throw new IllegalArgumentException("Injected controlled error");
                            }
                        })
                        .to(publishUri);

                from(publishUri).routeId(id + "-publish").to(sink(id + "-out", "tx"));
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);
        List<String> out = readCommittedKeys(id + "-out");
        LOG.info("Kafka sends before and after continued exception: out={}", out);
        assertEquals(List.of("m0", "m0", "m1", "m1", "m2", "m2"), out,
                "Both Kafka sends must be treated as normal sends in the source transaction");
    }

    @Test
    void wireTapInDirectSubRouteThatCanReachKafkaIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).to("direct:wiretap-subroute");
                from("direct:wiretap-subroute").wireTap("direct:wiretap-kafka-target");
                from("direct:wiretap-kafka-target").to(sink(id + "-out", "tx"));
            }
        });
    }

    @Test
    void wireTapWithRecipientListTargetIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:wiretap-recipient-list");
                from("direct:wiretap-recipient-list").recipientList(constant("log:wiretap-dynamic"));
            }
        });
    }

    @Test
    void wireTapWithRoutingSlipTargetIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:wiretap-routing-slip");
                from("direct:wiretap-routing-slip").routingSlip(constant("log:wiretap-dynamic"));
            }
        });
    }

    @Test
    void wireTapToUnresolvedDirectTargetIsRejectedAtStartup() throws Exception {
        String in = topics("in", "out")[0];
        assertWireTapRouteRejected(new RouteBuilder() {
            @Override
            public void configure() {
                from(source(in)).routeId(id).wireTap("direct:unresolved-wiretap-target");
            }
        });
    }

    @Test
    void onExceptionKafkaDeadLetterDestinationIsRecognized() throws Exception {
        String[] names = topics("in", "out", "dlq");
        String in = names[0];
        String out = names[1];
        String dlq = names[2];
        start(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalArgumentException.class)
                        .handled(true)
                        .to(sink(dlq, "dlq"));

                from(source(in)).routeId(id).process(countAttempt())
                        .to(sink(out, "tx"))
                        .process(exchange -> {
                            if ("m1".equals(key(exchange))) {
                                throw new IllegalArgumentException("Injected failure for onException Kafka DLQ");
                            }
                        });
            }
        });
        produce(in, "m0", "m1", "m2");
        awaitCommitted(in, 3);
        assertEquals(List.of("m0", "m2"), readCommittedKeys(out),
                "The aborted source-output transaction must not expose the failed record");
        assertEquals(List.of("m1"), readCommittedKeys(dlq),
                "The onException Kafka DLQ must commit in its separate producer transaction");
        assertEquals(1, attempts("m1"), "The handled onException delivery must not replay the source record");
    }

    private void assertWireTapRouteRejected(RouteBuilder routes) throws Exception {
        CamelContext context = newContext();
        try {
            context.addRoutes(routes);
            Exception failure = assertThrows(Exception.class, context::start);
            StringBuilder messages = new StringBuilder();
            for (Throwable current = failure; current != null; current = current.getCause()) {
                messages.append(current.getMessage()).append('\n');
            }
            assertTrue(messages.toString().contains("WireTap"), messages.toString());
        } finally {
            context.stop();
        }
    }

}
