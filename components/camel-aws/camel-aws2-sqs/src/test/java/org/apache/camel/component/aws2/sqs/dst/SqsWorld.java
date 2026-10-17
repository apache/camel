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
package org.apache.camel.component.aws2.sqs.dst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around a camel-aws2-sqs consumer with extendMessageVisibility=true: a fake SQS queue ({@link FakeSqs}), a
 * producer that sends bursts of messages, and a route whose processing is usually quick and sometimes longer than the
 * visibility timeout, and sometimes fails. The consumer, its scheduled polls, its visibility timeout extender and
 * Camel's routing are real.
 * <p>
 * The property under test: while the consumer holds a message (from the receive until the route has finished with it),
 * SQS must not deliver it again, because the extender keeps it invisible. The only exception is a message whose
 * visibility change was lost by a fault of the fake.
 */
final class SqsWorld {

    static final String ROUTE = "sqs";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakeSqs sqs;
    private final int visibilityTimeout;
    private final int maxMessagesPerPoll;
    private final int concurrentConsumers;
    private final long delay;
    private final Map<String, Integer> held = new HashMap<>();
    private final Set<String> extensionFaulted = new HashSet<>();
    private final List<String> redeliveredWhileHeld = new ArrayList<>();
    private int sendsLeft;
    private boolean faults = true;

    SqsWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.sqs = new FakeSqs(dst, ledger, this::received, extensionFaulted::addAll);
        // -Ddst.sqs.visibilityTimeouts=2,3,5,10 leaves out 1 s (see the report: the extender is always late with 1 s)
        this.visibilityTimeout = dst.pick(Arrays.stream(System.getProperty("dst.sqs.visibilityTimeouts", "1,2,3,5,10")
                .split(",")).map(String::trim).map(Integer::valueOf).toList());
        this.maxMessagesPerPoll = dst.pick(List.of(1, 2, 5, 10));
        this.concurrentConsumers = dst.pick(List.of(1, 2, 3));
        this.delay = dst.pick(List.of(100L, 500L, 1000L));
        dst.trace("world: visibilityTimeout=" + visibilityTimeout + " maxMessagesPerPoll=" + maxMessagesPerPoll
                  + " concurrentConsumers=" + concurrentConsumers + " delay=" + delay);
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        context.getRegistry().bind("sqs", sqs);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-sqs://dst?amazonSQSClient=#sqs&queueUrl=RAW(" + FakeSqs.QUEUE_URL + ")"
                     + "&extendMessageVisibility=true&visibilityTimeout=" + visibilityTimeout
                     + "&maxMessagesPerPoll=" + maxMessagesPerPoll + "&concurrentConsumers=" + concurrentConsumers
                     + "&initialDelay=100&delay=" + delay)
                        .routeId(ROUTE)
                        .process(exchange -> {
                            String id = exchange.getMessage().getHeader(Sqs2Constants.MESSAGE_ID, String.class);
                            ledger.record("routed", "q", id);
                            // usually quick; sometimes longer than the visibility timeout, which the extender covers
                            long work = faults && dst.chance(0.15)
                                    ? dst.between(visibilityTimeout * 500L, 3L * visibilityTimeout * 1000)
                                    : dst.between(1, 200);
                            // a blocking downstream call: the other consumers and the extender run meanwhile
                            dst.block(work, "process " + id);
                            release(id);
                            if (faults && dst.chance(0.05)) {
                                ledger.record("failed", "q", id);
                                throw new IllegalStateException("dst: processing failed for " + id);
                            }
                            ledger.record("completed", "q", id);
                        });
            }
        });
        context.start();
        try {
            planSends();
            dst.runUntil(() -> sendsLeft == 0, "all sends");
            dst.runFor(dst.between(0, 3L * visibilityTimeout * 1000));
            // faults cleared: the queue must drain
            faults = false;
            sqs.faults = false;
            long deadline = dst.now() + 20L * visibilityTimeout * 1000 + 30_000;
            while (!sqs.allDeleted() && dst.now() < deadline) {
                dst.runFor(100);
            }
            dst.check(
                    Invariants.of("no message is delivered again while the consumer holds it",
                            () -> redeliveredWhileHeld.isEmpty() ? null : redeliveredWhileHeld.toString()),
                    Invariants.everyOfferedRoutedAtLeastOnce(ledger, "sent", "completed"),
                    Invariants.of("the queue is drained once the faults cleared",
                            () -> sqs.allDeleted() ? null : "not deleted: " + sqs.notDeleted()));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private void planSends() {
        DstExecutor producer = dst.executor("producer", 1);
        long t = dst.between(0, 500);
        int n = 0;
        int bursts = (int) dst.between(1, 4);
        for (int b = 0; b < bursts; b++) {
            int size = (int) dst.between(1, 15);
            for (int i = 0; i < size; i++) {
                String id = "m" + n++;
                sendsLeft++;
                producer.schedule("send " + id, t, () -> {
                    sqs.send(id);
                    sendsLeft--;
                });
                t += dst.between(0, 50);
            }
            t += dst.between(0, 3L * visibilityTimeout * 1000);
        }
    }

    /** SQS returned the message to the consumer: from now on the consumer holds it. */
    private void received(FakeSqs.Msg m) {
        int holders = held.getOrDefault(m.id, 0);
        if (holders > 0 && !extensionFaulted.contains(m.id)) {
            redeliveredWhileHeld.add(m.id + " (receive #" + m.receives + " at t=" + dst.now() + ", visibilityTimeout="
                                     + visibilityTimeout + "s, maxMessagesPerPoll=" + maxMessagesPerPoll
                                     + ", concurrentConsumers=" + concurrentConsumers + ")");
            dst.trace("VIOLATION " + m.id + " delivered again while the consumer holds it");
        }
        held.put(m.id, holders + 1);
    }

    /** The route has finished with one delivery of the message. */
    private void release(String id) {
        held.computeIfPresent(id, (k, v) -> v > 1 ? v - 1 : null);
    }
}
