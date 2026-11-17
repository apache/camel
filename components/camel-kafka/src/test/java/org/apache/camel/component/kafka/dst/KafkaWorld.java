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
package org.apache.camel.component.kafka.dst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaClientFactory;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualAsyncCommitFactory;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

/**
 * The world around a camel-kafka consumer: a fake Kafka cluster and consumer group ({@link FakeKafka}), a producer, a
 * route whose processing is sometimes slow and sometimes fails, and an operator that restarts the route, suspends and
 * resumes it (with a second suspend and resume while the consumer still handles the first), and lets a phantom member
 * join and leave the group, so that partitions are revoked and assigned again while records are processed. Faults: the
 * consumer cannot be created, polls fail, commits fail or time out, commits are slow, processing fails.
 * <p>
 * The seed picks the commit mode: Kafka's auto-commit (autoCommitEnable=true), manual synchronous or asynchronous
 * commits in the route (allowManualCommit=true with DefaultKafkaManualCommitFactory or
 * DefaultKafkaManualAsyncCommitFactory, after every record or only for the last record of a poll), or batching
 * (batching=true with autoCommitEnable=false: Camel commits each batch once it was processed), and breakOnFirstError
 * and pollOnError (ERROR_HANDLER, DISCARD, RETRY, RECONNECT; not STOP, which ends the consumer by design).
 * <p>
 * Not explored: batching with Kafka's auto-commit (the auto-commit commits the position of records that Camel still
 * holds in an incomplete batch, which the KafkaConsumer Javadoc warns about), and batching with breakOnFirstError (the
 * failed batch is committed, see "Break on First Error in Batching Mode" in the component documentation); manual
 * commits from another thread; consumersCount above 1; seekTo, offset repositories and resume strategies.
 * <p>
 * Left out of the default set: breakOnFirstError with the synchronous manual commits (SyncCommitManager), where a
 * commitSync that times out in the break path is being looked at separately;
 * {@code -Ddst.kafka.syncBreakOnFirstError=true} puts the combination back.
 * <p>
 * consumersCount is 1: a consumer thread loops for ever, and with the one-stack model a second looping thread nested
 * above the first would never let it resume. For the same reason a route stop that runs while the consumer thread waits
 * in poll cannot see it finish: the stop wakes up the Kafka consumer, waits for its timeouts, and the thread ends after
 * the stop returned, so the operator starts the route again only once the previous Kafka consumer is closed. The
 * operator does not stop or suspend the route while an exchange is in the route (the shutdown strategy would wait for
 * it for ever, as it cannot see it finish) or while the consumer thread waits for the background task that creates its
 * Kafka consumer (a stop interrupts that wait with real threads, which the one-stack model does not do). The lock of
 * KafkaFetchRecords does not exclude on the one stack, so a stop never waits for the processing of the current records.
 * <p>
 * Properties: a committed offset never passes a record the route has not finished with (at-least-once: completed, or
 * failed with breakOnFirstError=false, which skips it by design); a suspended route stops fetching once its consumer
 * handled the suspend; once the faults cleared, every record is finished, the committed offsets reach the end of each
 * partition, and the route holds one open Kafka consumer; the consumer uses the client as the Javadoc requires; no
 * Kafka consumer is left open after the route stopped. Records may be consumed more than once.
 */
@DstWorld(component = "kafka",
          fake = "FakeKafka: a topic with one or two partitions and a consumer group (classic protocol, eager"
                 + " rebalance), behind the Consumer interface of KafkaClientFactory",
          covers = {
                  "commits by Kafka's auto-commit, by manual synchronous or asynchronous commits in the route, and per"
                     + " batch with batching=true",
                  "breakOnFirstError",
                  "pollOnError ERROR_HANDLER, DISCARD, RETRY and RECONNECT",
                  "a poll that fails while a batch is incomplete",
                  "route restart, also while the consumer polls",
                  "route suspend and resume, with a second request while the consumer handles the first",
                  "partitions revoked and assigned again by a rebalance while records are processed" },
          faults = {
                  "the Kafka consumer cannot be created", "poll fails", "commitSync times out",
                  "commitAsync fails", "auto-commit fails", "commits are slow", "committed answers slowly",
                  "processing fails", "processing is slow", "a member joins and leaves the group" },
          invariants = {
                  "the consumer uses the Kafka consumer as documented",
                  "a committed offset never passes a record the route has not finished with",
                  "a suspended route stops fetching",
                  "every record is finished once the faults cleared",
                  "the committed offsets reach the end once the faults cleared",
                  "the route holds one Kafka consumer once the faults cleared",
                  "no Kafka consumer is left open after the route stopped" },
          issues = { "CAMEL-25313", "CAMEL-25756" })
final class KafkaWorld {

    static final String ROUTE = "kafka";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakeKafka kafka;
    private final int partitions;
    private final String mode;
    private final boolean breakOnFirstError;
    private final boolean commitEveryRecord;
    private final String pollOnError;
    private final long pollTimeoutMs;
    private final long shutdownTimeout;
    private final long commitTimeoutMs;
    private final long backoffInterval;
    private final double processFailRate;
    private final List<String> violations = new ArrayList<>();
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;
    // exchanges in the route: a route stop or suspend nested above one on the one stack would wait for it for ever
    // (the shutdown strategy cannot see it finish), so the operator stops and suspends only when none is in the route
    private int inRoute;
    private boolean finished;
    private String finalStatus;
    private String finalOpen;
    private int finalOpenCount;
    private boolean finalDone;
    private boolean finalCommitted;

    KafkaWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.partitions = dst.pick(List.of(1, 2));
        this.kafka = new FakeKafka(dst, ledger, partitions);
        this.mode = dst.pick(Arrays.asList(System.getProperty("dst.kafka.modes", "AUTO,SYNC,ASYNC,BATCH").split(",")));
        // see the class Javadoc: batching with breakOnFirstError is not explored, and the synchronous manual commits
        // with breakOnFirstError are left out unless -Ddst.kafka.syncBreakOnFirstError=true
        this.breakOnFirstError = !"BATCH".equals(mode)
                && (!"SYNC".equals(mode) || Boolean.getBoolean("dst.kafka.syncBreakOnFirstError"))
                && dst.chance(0.5);
        this.commitEveryRecord = dst.chance(0.5);
        this.pollOnError = dst.pick(Arrays.asList(
                System.getProperty("dst.kafka.pollOnError", "ERROR_HANDLER,DISCARD,RETRY,RECONNECT").split(",")));
        this.pollTimeoutMs = dst.pick(List.of(100L, 1000L, 5000L));
        this.shutdownTimeout = dst.pick(List.of(1000L, 30000L));
        this.commitTimeoutMs = dst.pick(List.of(1000L, 5000L));
        this.backoffInterval = dst.pick(List.of(500L, 5000L));
        kafka.autoCommit = "AUTO".equals(mode);
        kafka.autoCommitIntervalMs = dst.pick(List.of(1000L, 5000L));
        kafka.breakOnFirstError = breakOnFirstError;
        kafka.maxPollRecords = "BATCH".equals(mode) ? dst.pick(List.of(3, 10)) : dst.pick(List.of(1, 3, 10));
        kafka.createFailRate = dst.pick(List.of(0.0, 0.1, 0.3));
        kafka.pollFailRate = dst.pick(List.of(0.0, 0.02, 0.1));
        kafka.commitFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        kafka.slowCommits = dst.chance(0.5);
        this.processFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        dst.trace("world: " + config());
    }

    private String config() {
        return "partitions=" + partitions + " mode=" + mode + " breakOnFirstError=" + breakOnFirstError
               + " commitEveryRecord=" + commitEveryRecord + " pollOnError=" + pollOnError + " pollTimeoutMs="
               + pollTimeoutMs + " maxPollRecords=" + kafka.maxPollRecords + " shutdownTimeout=" + shutdownTimeout
               + " commitTimeoutMs=" + commitTimeoutMs + " autoCommitIntervalMs=" + kafka.autoCommitIntervalMs
               + " backoff=" + backoffInterval + " createFail=" + kafka.createFailRate + " pollFail="
               + kafka.pollFailRate + " commitFail=" + kafka.commitFailRate + " slowCommits=" + kafka.slowCommits
               + " processFail=" + processFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        KafkaComponent component = new KafkaComponent();
        component.setCreateConsumerBackoffInterval(backoffInterval);
        if ("SYNC".equals(mode)) {
            component.setKafkaManualCommitFactory(new DefaultKafkaManualCommitFactory());
        } else if ("ASYNC".equals(mode)) {
            component.setKafkaManualCommitFactory(new DefaultKafkaManualAsyncCommitFactory());
        }
        component.setKafkaClientFactory(new KafkaClientFactory() {
            @Override
            public Producer getProducer(Properties kafkaProps) {
                throw new UnsupportedOperationException("the world has no producer endpoint");
            }

            @Override
            public Consumer getConsumer(Properties kafkaProps) {
                return kafka.newConsumer();
            }

            @Override
            public String getBrokers(KafkaConfiguration configuration) {
                return configuration.getBrokers();
            }
        });
        context.addComponent("kafka", component);
        boolean manual = "SYNC".equals(mode) || "ASYNC".equals(mode);
        String uri = "kafka:" + FakeKafka.TOPIC + "?brokers=127.0.0.1:9092&groupId=" + FakeKafka.GROUP
                     + "&consumersCount=1&autoOffsetReset=earliest&pollTimeoutMs=" + pollTimeoutMs + "&maxPollRecords="
                     + kafka.maxPollRecords + "&shutdownTimeout=" + shutdownTimeout + "&commitTimeoutMs="
                     + commitTimeoutMs + "&pollOnError=" + pollOnError + "&breakOnFirstError=" + breakOnFirstError
                     + "&autoCommitEnable=" + "AUTO".equals(mode) + "&autoCommitIntervalMs="
                     + kafka.autoCommitIntervalMs + "&allowManualCommit=" + manual + "&batching=" + "BATCH".equals(mode);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId(ROUTE).autoStartup(false)
                        .process(exchange -> process(exchange, manual));
            }
        });
        context.start();
        DstExecutor operator = dst.executor("operator", 1);
        try {
            operator.schedule("start route", 0, () -> startRoute(context, operator));
            planSends();
            planOperations(context, operator);
            operator.schedule("finisher", 1000, () -> finisher(context, operator, -1));
            dst.runUntil(() -> finished, "the finisher");
            // the consumer thread ends after the stop; let it close its Kafka consumer
            dst.runFor(2 * shutdownTimeout + 10_000);
            dst.check(
                    Invariants.of("the consumer uses the Kafka consumer as documented",
                            () -> kafka.violations.isEmpty() && violations.isEmpty()
                                    ? null : kafka.violations + " " + violations + " (" + config() + ")"),
                    Invariants.of("a committed offset never passes a record the route has not finished with",
                            () -> kafka.lossViolations.isEmpty() ? null : kafka.lossViolations + " (" + config() + ")"),
                    Invariants.of("a suspended route stops fetching",
                            () -> kafka.suspendViolations.isEmpty()
                                    ? null : kafka.suspendViolations + " (" + config() + ")"),
                    Invariants.of("every record is finished once the faults cleared",
                            () -> finalDone ? null : "not finished: " + notDone() + " (" + config() + ")"),
                    Invariants.of("the committed offsets reach the end once the faults cleared",
                            () -> finalCommitted ? null : kafka.notCommitted() + " (" + config() + ")"),
                    Invariants.of("the route holds one Kafka consumer once the faults cleared",
                            () -> "Started".equals(finalStatus) && finalOpenCount == 1
                                    ? null
                                    : "route is " + finalStatus + ", open Kafka consumers " + finalOpen + " ("
                                      + config() + ")"),
                    Invariants.of("no Kafka consumer is left open after the route stopped",
                            () -> kafka.open.isEmpty()
                                    ? null : "open after stop: " + kafka.openNames() + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    /** The route: a record (or each record of a batch) is routed, maybe slowly, then fails or completes. */
    private void process(Exchange exchange, boolean manual) throws Exception {
        List<FakeKafka.Rec> recs = new ArrayList<>();
        Object body = exchange.getMessage().getBody();
        if (!(body instanceof List) && exchange.getMessage().getHeader(KafkaConstants.OFFSET) == null) {
            // not a record: an exception of the consumer bridged to the error handler
            return;
        }
        if (body instanceof List<?> batch) {
            for (Object o : batch) {
                recs.add(recOf(((Exchange) o).getMessage()));
            }
        } else {
            recs.add(recOf(exchange.getMessage()));
        }
        inRoute++;
        try {
            processRecords(exchange, recs, manual);
        } finally {
            inRoute--;
        }
    }

    private void processRecords(Exchange exchange, List<FakeKafka.Rec> recs, boolean manual) {
        String ids = recs.stream().map(r -> r.id).toList().toString();
        for (FakeKafka.Rec r : recs) {
            r.routed++;
            ledger.record("routed", "p" + r.partition, r.id);
        }
        if (faults && dst.chance(0.1)) {
            dst.block(dst.between(1000, 10_000), "slow processing of " + ids);
        } else {
            dst.block(dst.between(0, 100), "processing of " + ids);
        }
        if (faults && dst.chance(processFailRate)) {
            for (FakeKafka.Rec r : recs) {
                r.failed++;
                ledger.record("failed", "p" + r.partition, r.id);
            }
            throw Dst.injected(new IllegalStateException("dst: processing failed for " + ids));
        }
        for (FakeKafka.Rec r : recs) {
            r.completed++;
            ledger.record("completed", "p" + r.partition, r.id);
        }
        if (manual) {
            Message m = exchange.getMessage();
            boolean last = Boolean.TRUE.equals(m.getHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, Boolean.class));
            if (commitEveryRecord || last) {
                // a commit that fails fails the exchange, after the record was processed
                m.getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class).commit();
            }
        }
    }

    private FakeKafka.Rec recOf(Message message) {
        int p = message.getHeader(KafkaConstants.PARTITION, Integer.class);
        long offset = message.getHeader(KafkaConstants.OFFSET, Long.class);
        FakeKafka.Rec r = kafka.rec(p, offset);
        if (!r.id.equals(message.getBody(String.class))) {
            violations.add("record p" + p + "@" + offset + " has the body " + message.getBody() + ", not " + r.id);
        }
        return r;
    }

    private String notDone() {
        List<String> missing = new ArrayList<>();
        for (List<FakeKafka.Rec> partitionLog : kafka.log) {
            for (FakeKafka.Rec r : partitionLog) {
                if (!kafka.done(r)) {
                    missing.add(r.id + "@p" + r.partition + "/" + r.offset + " routed=" + r.routed + " failed=" + r.failed);
                }
            }
        }
        return missing.toString();
    }

    private boolean allDone() {
        for (List<FakeKafka.Rec> partitionLog : kafka.log) {
            for (FakeKafka.Rec r : partitionLog) {
                if (!kafka.done(r)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Waits for the sends and operations, switches the faults off, publishes one more record to each partition (its
     * commit covers the records before it), waits for everything to be finished and committed (or a deadline), takes
     * the final state and stops the route.
     */
    private void finisher(DefaultCamelContext context, DstExecutor operator, long deadline) {
        if (deadline == -1 && (sendsLeft > 0 || opsLeft > 0)) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, -1));
            return;
        }
        if (deadline == -1) {
            long slow = dst.between(0, 10_000);
            dst.trace("world: operations done, faults for another " + slow + " ms");
            operator.schedule("finisher", slow, () -> finisher(context, operator, -2));
            return;
        }
        if (deadline == -2) {
            faults = false;
            kafka.faults = false;
            kafka.phantomLeaves();
            for (int p = 0; p < partitions; p++) {
                kafka.publish(p, "end" + p);
            }
            dst.trace("world: faults cleared");
            long d = dst.now() + 10 * 60_000L;
            operator.schedule("finisher", 500, () -> finisher(context, operator, d));
            return;
        }
        ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
        boolean done = allDone();
        boolean committed = kafka.allCommitted();
        if ((inRoute > 0 || !done || !committed || status != ServiceStatus.Started || kafka.open.size() != 1)
                && dst.now() < deadline) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, deadline));
            return;
        }
        finalStatus = String.valueOf(status);
        finalDone = done;
        finalCommitted = committed;
        finalOpenCount = kafka.open.size();
        finalOpen = kafka.openNames();
        if (inRoute > 0) {
            // a stop would wait for it for ever (one-stack model)
            finalDone = false;
            finished = true;
            return;
        }
        try {
            context.getRouteController().stopRoute(ROUTE);
        } catch (Exception e) {
            violations.add("final stop failed: " + e);
        }
        finished = true;
    }

    /** The operator starts the route; a failed start is retried, as the supervising route controller does. */
    private void startRoute(DefaultCamelContext context, DstExecutor operator) {
        try {
            context.getRouteController().startRoute(ROUTE);
            ledger.record("routeStarted", "r", ROUTE);
        } catch (Exception e) {
            ledger.record("routeStartFailed", "r", ROUTE);
            dst.trace("world: start failed (" + e.getMessage() + "), retry in 2 s");
            operator.schedule("retry start route", 2000, () -> startRoute(context, operator));
        }
    }

    /**
     * Starts the route again once the Kafka consumer of the stopped route is closed: its thread ends only after the
     * stop returned (one-stack model), and a new consumer thread started above it would never let it resume.
     */
    private void startWhenClosed(DefaultCamelContext context, DstExecutor operator, long since) {
        if (!kafka.open.isEmpty() && dst.now() - since < 10 * 60_000L) {
            operator.schedule("start route when the consumer closed", 200,
                    () -> startWhenClosed(context, operator, since));
            return;
        }
        if (!kafka.open.isEmpty()) {
            violations.add("Kafka consumers " + kafka.openNames() + " still open 10 minutes after the route stopped");
        }
        startRoute(context, operator);
        opsLeft--;
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
                int p = partitions == 1 ? 0 : dst.nextInt(partitions);
                sendsLeft++;
                producer.schedule("publish " + id, t, () -> {
                    kafka.publish(p, id);
                    sendsLeft--;
                });
                t += dst.between(0, 100);
            }
            t += dst.between(0, 8000);
        }
    }

    private void planOperations(DefaultCamelContext context, DstExecutor operator) {
        int ops = (int) dst.between(0, 4);
        long t = dst.between(500, 3000);
        for (int i = 0; i < ops; i++) {
            int kind = dst.nextInt(3);
            opsLeft++;
            if (kind == 0) {
                operator.schedule("stop route", t, () -> whenConnected(operator, "stop route", () -> {
                    if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
                        opsLeft--;
                        return;
                    }
                    try {
                        context.getRouteController().stopRoute(ROUTE);
                        ledger.record("routeStopped", "r", ROUTE);
                    } catch (Exception e) {
                        violations.add("stop failed: " + e);
                    }
                    long since = dst.now();
                    operator.schedule("start route when the consumer closed", dst.between(0, 3000),
                            () -> startWhenClosed(context, operator, since));
                }));
            } else if (kind == 1) {
                // a second suspend and resume while the consumer may still handle the first resume (it reads the
                // committed offsets before it resumes its partitions)
                int times = dst.chance(0.5) ? 2 : 1;
                operator.schedule("suspend route", t, () -> suspendAndResume(context, operator, times));
            } else {
                Set<Integer> taken = partitions == 1 ? Set.of(0) : dst.pick(List.of(Set.of(0), Set.of(1), Set.of(0, 1)));
                long held = dst.between(500, 15_000);
                operator.schedule("a member joins the group", t, () -> {
                    kafka.phantomJoins(taken);
                    operator.schedule("the member leaves the group", held, () -> {
                        kafka.phantomLeaves();
                        opsLeft--;
                    });
                });
            }
            t += dst.between(500, 10_000);
        }
    }

    /**
     * Runs an operation of the operator once no exchange is in the route. The operator task can run nested above an
     * exchange that blocks (on the one stack), so it waits in a new task, not in this one.
     */
    private void whenIdle(DstExecutor operator, String what, Runnable operation) {
        if (inRoute > 0) {
            operator.schedule(what + " (an exchange is in the route)", 50, () -> whenIdle(operator, what, operation));
            return;
        }
        operation.run();
    }

    /**
     * Stops the route only while the consumer thread holds a Kafka consumer, not while it waits for the background task
     * that creates one: a stop interrupts that wait with real threads (shutdownNow), which the one-stack model does not
     * do, and the consumer thread would go on after the route was started again.
     */
    private void whenConnected(DstExecutor operator, String what, Runnable operation) {
        if (kafka.open.size() != 1 || inRoute > 0) {
            operator.schedule(what + " (no Kafka consumer, or an exchange is in the route)", 100,
                    () -> whenConnected(operator, what, operation));
            return;
        }
        operation.run();
    }

    private void suspendAndResume(DefaultCamelContext context, DstExecutor operator, int times) {
        whenIdle(operator, "suspend route", () -> {
            if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
                opsLeft--;
                return;
            }
            try {
                suspend(context);
            } catch (Exception e) {
                violations.add("suspend failed: " + e);
                opsLeft--;
                return;
            }
            operator.schedule("resume route", dst.between(100, 5000), () -> {
                try {
                    resume(context);
                } catch (Exception e) {
                    violations.add("resume failed: " + e);
                }
                if (times > 1) {
                    operator.schedule("suspend route again", dst.between(0, pollTimeoutMs + 2000),
                            () -> suspendAndResume(context, operator, times - 1));
                } else {
                    opsLeft--;
                }
            });
        });
    }

    private void suspend(DefaultCamelContext context) throws Exception {
        context.getRouteController().suspendRoute(ROUTE);
        kafka.suspendedSince = dst.now();
        ledger.record("routeSuspended", "r", ROUTE);
    }

    private void resume(DefaultCamelContext context) throws Exception {
        kafka.suspendedSince = -1;
        context.getRouteController().resumeRoute(ROUTE);
        ledger.record("routeResumed", "r", ROUTE);
    }
}
