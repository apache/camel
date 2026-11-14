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
package org.apache.camel.component.aws2.kinesis.dst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.Kinesis2Constants;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisConsumerOffsetProcessor;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeStrategy;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeStrategyConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around a camel-aws2-kinesis consumer: a fake Kinesis data stream ({@link FakeKinesis}), a producer, a route
 * whose processing is sometimes slow (now and then longer than the five minutes a shard iterator is valid) and
 * sometimes fails, and an operator that restarts the route, suspends it (for up to eight minutes, so that the cached
 * shard iterators expire) and resumes it, and reshards the stream (a split closes a shard and opens two child shards, a
 * merge closes two and opens one). Faults: GetRecords, GetShardIterator and ListShards fail, calls answer slowly,
 * GetRecords returns fewer records than it could.
 * <p>
 * The seed picks the iterator type (TRIM_HORIZON or LATEST), whether the route checkpoints with the resumable EIP and
 * KinesisResumeStrategy (the resume cache outlives the consumer, so a restarted route resumes from it), the number of
 * shards, maxResultsPerRequest, the poll delay, the shard monitor interval and shardClosed (ignore or silent; fail ends
 * the consumption of the stream by design).
 * <p>
 * A route restart creates a new consumer, which this world counts as an epoch. With the one-stack model, a graceful
 * stop or suspend cannot see a poll that blocks below it finish, and the shutdown strategy waits for a poll in progress
 * (the consumer counts it as a pending exchange), so the operator stops or suspends the route between two polls, after
 * it suspended the consumer (as the shutdown strategy does) so that no poll starts during the stop: a stop while a poll
 * runs is not driven. A stop can happen while the shard monitor lists the shards. The operator starts the route again
 * only once no call of the stopped consumer and no exchange is in progress.
 * <p>
 * Properties: a consumer routes each record at most once (the iterator of an expired shard continues after the last
 * record it routed), apart from a record after its checkpoint (see below); a restarted route with a checkpoint does not
 * route a checkpointed record again; once the faults cleared every record is routed (a record stored before a consumer
 * started a shard of its first shard list with a LATEST iterator is skipped by design, unless the shard has a
 * checkpoint), and the checkpoint of each open shard reaches its last record; the consumer uses the API as the
 * reference requires. Without checkpoints a failed exchange is not routed again: the consumer does not retry, by
 * design.
 * <p>
 * With checkpoints, the resume strategy decides where every new iterator of a shard starts, also after an expiry: after
 * the checkpoint, which is the last completed exchange, so a record routed after it that did not complete (it failed)
 * may be routed again by the same consumer.
 * <p>
 * Also excused, unless {@code -Ddst.kinesis.strictLatest=true}: with iteratorType=LATEST, when the iterator of a shard
 * of the first shard list from which the consumer has not routed a record yet expires, the consumer has no sequence
 * number to continue from and asks for a LATEST iterator again, which skips the records stored since its previous
 * iterator (for example while the route was suspended for more than five minutes). The default set accepts this;
 * {@code -Ddst.kinesis.strictLatest=true} counts those records as missing.
 * <p>
 * Not explored: a route stop while a poll runs (see above), the KCL consumer (useKclConsumers), the asynchronous
 * client, a configured shardId, AT_SEQUENCE_NUMBER, AFTER_SEQUENCE_NUMBER and AT_TIMESTAMP as the configured iterator
 * type.
 */
@DstWorld(component = "aws2-kinesis",
          fake = "FakeKinesis: a data stream with shards that can be split and merged (GetShardIterator, GetRecords,"
                 + " ListShards) bound as amazonKinesisClient",
          covers = {
                  "the iterator types TRIM_HORIZON and LATEST, with one or two shards at the start and more after resharding",
                  "shard iterators that expire between two polls, while the route is suspended or an exchange is slow",
                  "a closed shard and its child shards after a split or a merge (shardClosed ignore and silent)",
                  "checkpoints with the resumable EIP and KinesisResumeStrategy, and the resume after a route restart",
                  "route restart and route suspend and resume between two polls" },
          faults = {
                  "GetRecords fails", "GetShardIterator fails", "ListShards fails", "calls answer slowly",
                  "GetRecords returns fewer records than it could", "processing fails", "processing is slow",
                  "the stream is resharded" },
          invariants = {
                  "the consumer uses the Kinesis API as documented",
                  "a consumer routes a record at most once, apart from a record after its checkpoint",
                  "a restarted route resumes after its checkpoint",
                  "every record is routed once the faults cleared, apart from records skipped by a LATEST iterator",
                  "the checkpoint reaches the last record of each open shard once the faults cleared" },
          issues = { "CAMEL-25569", "CAMEL-25942" })
final class KinesisWorld {

    static final String ROUTE = "kinesis";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakeKinesis kinesis;
    private final String iteratorType;
    private final boolean checkpoints;
    private final int maxResults;
    private final long delay;
    private final long shardMonitorInterval;
    private final String shardClosed;
    private final double processFailRate;
    private final MapResumeCache resumeCache = new MapResumeCache();
    private final List<String> violations = new ArrayList<>();
    private final List<String> duplicates = new ArrayList<>();
    private final List<String> resumedBeforeCheckpoint = new ArrayList<>();
    /** The checkpoint of each shard when the consumer of an epoch started. */
    private final Map<Integer, Map<String, String>> checkpointsAtStart = new HashMap<>();
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;
    private int inRoute;
    private boolean finished;
    private String finalStatus;
    private boolean finalRouted;
    private boolean finalCheckpointed;

    KinesisWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.kinesis = new FakeKinesis(dst, ledger, dst.pick(List.of(1, 2)));
        this.iteratorType = dst.pick(List.of("TRIM_HORIZON", "LATEST"));
        this.checkpoints = dst.chance(0.5);
        kinesis.hasCheckpoint = shard -> checkpointNow().containsKey(shard);
        this.maxResults = dst.pick(List.of(1, 3, 10, 1000));
        this.delay = dst.pick(List.of(100L, 500L, 2000L));
        this.shardMonitorInterval = dst.pick(List.of(1000L, 10_000L));
        this.shardClosed = dst.pick(List.of("ignore", "silent"));
        kinesis.getRecordsFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        kinesis.getIteratorFailRate = dst.pick(List.of(0.0, 0.05));
        kinesis.listShardsFailRate = dst.pick(List.of(0.0, 0.1));
        this.processFailRate = dst.pick(List.of(0.0, 0.05));
        dst.trace("world: " + config());
    }

    private String config() {
        return "shards=" + kinesis.shards.size() + " iteratorType=" + iteratorType + " checkpoints=" + checkpoints
               + " maxResultsPerRequest=" + maxResults + " delay=" + delay + " shardMonitorInterval="
               + shardMonitorInterval + " shardClosed=" + shardClosed + " getRecordsFail=" + kinesis.getRecordsFailRate
               + " getIteratorFail=" + kinesis.getIteratorFailRate + " listShardsFail=" + kinesis.listShardsFailRate
               + " processFail=" + processFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        context.getRegistry().bind("kinesis", kinesis);
        KinesisResumeStrategy strategy = new KinesisResumeStrategy();
        strategy.setResumeStrategyConfiguration(
                KinesisResumeStrategyConfiguration.builder().withResumeCache(resumeCache).build());
        String uri = "aws2-kinesis:" + FakeKinesis.STREAM + "?amazonKinesisClient=#kinesis&iteratorType=" + iteratorType
                     + "&maxResultsPerRequest=" + maxResults + "&shardClosed=" + shardClosed + "&shardMonitorInterval="
                     + shardMonitorInterval + "&initialDelay=100&delay=" + delay;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (checkpoints) {
                    // the checkpoint of a completed exchange goes to the resume cache, which the next consumer reads
                    from(uri).routeId(ROUTE).autoStartup(false)
                            .resumable(strategy)
                            .process(new KinesisConsumerOffsetProcessor())
                            .process(exchange -> process(exchange));
                } else {
                    from(uri).routeId(ROUTE).autoStartup(false)
                            .process(exchange -> process(exchange));
                }
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
            dst.check(
                    Invariants.of("the consumer uses the Kinesis API as documented",
                            () -> kinesis.violations.isEmpty() && violations.isEmpty()
                                    ? null : kinesis.violations + " " + violations + " (" + config() + ")"),
                    Invariants.of("a consumer routes a record at most once, apart from a record after its checkpoint",
                            () -> duplicates.isEmpty() ? null : duplicates + " (" + config() + ")"),
                    Invariants.of("a restarted route resumes after its checkpoint",
                            () -> resumedBeforeCheckpoint.isEmpty()
                                    ? null : resumedBeforeCheckpoint + " (" + config() + ")"),
                    Invariants.of("every record is routed once the faults cleared",
                            () -> finalRouted ? null : "not routed: " + notRouted() + " (" + config() + ")"),
                    Invariants.of("the checkpoint reaches the last record of each open shard once the faults cleared",
                            () -> finalCheckpointed
                                    ? null : "checkpoints " + checkpointNow() + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private void process(Exchange exchange) {
        String shard = exchange.getMessage().getHeader(Kinesis2Constants.SHARD_ID, String.class);
        String seq = exchange.getMessage().getHeader(Kinesis2Constants.SEQUENCE_NUMBER, String.class);
        String id = exchange.getMessage().getBody(String.class);
        FakeKinesis.Rec r = kinesis.rec(shard, seq);
        if (r == null || !r.id.equals(id)) {
            violations.add("routed " + id + " from " + shard + " at " + seq + ", which is " + (r == null ? "no record" : r.id));
            return;
        }
        int epoch = kinesis.epoch;
        // with checkpoints, a new iterator of the shard (after an expiry) starts after the checkpoint, which the resume
        // strategy keeps for the last completed exchange: a record after it that did not complete may be routed again
        String now = checkpointNow().get(shard);
        boolean afterCheckpoint = checkpoints && now != null && Long.parseLong(seq) > Long.parseLong(now)
                && r.completed == 0;
        if (r.routedIn.contains(epoch) && !afterCheckpoint) {
            duplicates.add(r.id + " of " + shard + " routed again by consumer " + epoch + " at t=" + dst.now());
        }
        String checkpoint = checkpointsAtStart.getOrDefault(epoch, Map.of()).get(shard);
        if (checkpoints && checkpoint != null && Long.parseLong(seq) <= Long.parseLong(checkpoint)) {
            resumedBeforeCheckpoint.add(r.id + " of " + shard + " (" + seq + ") routed by consumer " + epoch
                                        + ", which started with the checkpoint " + checkpoint);
        }
        r.routedIn.add(epoch);
        ledger.record("routed", shard, r.id);
        inRoute++;
        try {
            if (faults && dst.chance(0.02)) {
                // longer than a shard iterator is valid: the cached iterators of the other shards expire meanwhile
                dst.block(dst.between(4 * 60_000L, 7 * 60_000L), "very slow processing of " + r.id);
            } else if (faults && dst.chance(0.1)) {
                dst.block(dst.between(1000, 10_000), "slow processing of " + r.id);
            } else {
                dst.block(dst.between(0, 100), "processing of " + r.id);
            }
        } finally {
            inRoute--;
        }
        if (faults && dst.chance(processFailRate)) {
            ledger.record("failed", shard, r.id);
            throw Dst.injected(new IllegalStateException("dst: processing failed for " + r.id));
        }
        r.completed++;
        ledger.record("completed", shard, r.id);
    }

    private Map<String, String> checkpointNow() {
        Map<String, String> answer = new HashMap<>();
        for (String shard : kinesis.shards.keySet()) {
            Object value = resumeCache.get(shard);
            if (value instanceof Offset<?> offset && offset.getValue() != null) {
                answer.put(shard, offset.getValue().toString());
            }
        }
        return answer;
    }

    private boolean routedOrSkipped(FakeKinesis.Rec r) {
        return !r.routedIn.isEmpty() || kinesis.skippedByLatest.contains(r);
    }

    private boolean allRouted() {
        for (FakeKinesis.FakeShard s : kinesis.shards.values()) {
            for (FakeKinesis.Rec r : s.records) {
                if (!routedOrSkipped(r)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String notRouted() {
        List<String> missing = new ArrayList<>();
        for (FakeKinesis.FakeShard s : kinesis.shards.values()) {
            for (FakeKinesis.Rec r : s.records) {
                if (!routedOrSkipped(r)) {
                    missing.add(r.id + "@" + s.id + "/" + r.index);
                }
            }
        }
        return missing.toString();
    }

    private boolean allCheckpointed() {
        if (!checkpoints) {
            return true;
        }
        Map<String, String> now = checkpointNow();
        for (FakeKinesis.FakeShard s : kinesis.openShards()) {
            if (s.records.isEmpty()) {
                continue;
            }
            // the last record the consumer had to route (a LATEST start skips records by design)
            String last = null;
            for (FakeKinesis.Rec r : s.records) {
                if (!kinesis.skippedByLatest.contains(r)) {
                    last = r.seq;
                }
            }
            if (last != null && !last.equals(now.get(s.id))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Waits for the sends and operations, switches the faults off, publishes one more record to each open shard, waits
     * for every record to be routed and checkpointed (or a deadline), takes the final state and stops the route.
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
            kinesis.faults = false;
            for (FakeKinesis.FakeShard s : kinesis.openShards()) {
                kinesis.publish(s, "end-" + s.id);
            }
            dst.trace("world: faults cleared");
            long d = dst.now() + 30 * 60_000L;
            operator.schedule("finisher", 500, () -> finisher(context, operator, d));
            return;
        }
        ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
        boolean routed = allRouted();
        boolean checkpointed = allCheckpointed();
        if ((!routed || !checkpointed || status != ServiceStatus.Started) && dst.now() < deadline) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, deadline));
            return;
        }
        finalStatus = String.valueOf(status);
        finalRouted = routed;
        finalCheckpointed = checkpointed;
        if (!routed) {
            dst.trace("world: route " + finalStatus + ", not routed " + notRouted());
        }
        // the run then drops its pending tasks and stops the CamelContext
        finished = true;
    }

    /** The operator starts the route, with a new consumer (an epoch); a failed start is retried. */
    private void startRoute(DefaultCamelContext context, DstExecutor operator) {
        kinesis.epoch++;
        checkpointsAtStart.put(kinesis.epoch, checkpointNow());
        dst.trace("world: consumer " + kinesis.epoch + " starts with the checkpoints " + checkpointNow());
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
     * Runs an operation of the operator once the consumer does not poll: the graceful shutdown waits for a poll in
     * progress (the consumer counts it as a pending exchange), and a route stop or suspend nested above a poll that
     * blocks (on the one stack) would wait for it for ever, as it cannot see the poll finish. The operator task may
     * itself run nested above such a poll, so it waits in a new task, not in this one.
     */
    private void whenIdle(DefaultCamelContext context, DstExecutor operator, String what, Runnable operation) {
        if (inRoute > 0 || polling(context)) {
            operator.schedule(what + " (the consumer polls)", 50, () -> whenIdle(context, operator, what, operation));
            return;
        }
        operation.run();
    }

    /**
     * Suspends the consumer, as the shutdown strategy does when it stops or suspends the route, before the operator
     * calls it: a poll that would start while the shutdown strategy waits would block nested above that wait (one-stack
     * model), and the wait could not see it finish. A suspended consumer skips its polls.
     */
    private static void suspendPolls(DefaultCamelContext context) throws Exception {
        if (context.getRoute(ROUTE).getConsumer() instanceof ScheduledPollConsumer consumer) {
            consumer.suspend();
        }
    }

    private static boolean polling(DefaultCamelContext context) {
        return context.getRoute(ROUTE) != null
                && context.getRoute(ROUTE).getConsumer() instanceof ScheduledPollConsumer consumer
                && consumer.isPolling();
    }

    /** Starts the route again once no call of the stopped consumer and no exchange is in progress. */
    private void startWhenIdle(DefaultCamelContext context, DstExecutor operator) {
        if (kinesis.callsInFlight > 0 || inRoute > 0) {
            operator.schedule("start route when idle", 200, () -> startWhenIdle(context, operator));
            return;
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
                sendsLeft++;
                producer.schedule("publish " + id, t, () -> {
                    List<FakeKinesis.FakeShard> open = kinesis.openShards();
                    kinesis.publish(open.get(dst.nextInt(open.size())), id);
                    sendsLeft--;
                });
                t += dst.between(0, 100);
            }
            t += dst.between(0, 60_000);
        }
    }

    private void planOperations(DefaultCamelContext context, DstExecutor operator) {
        int ops = (int) dst.between(0, 4);
        long t = dst.between(500, 3000);
        for (int i = 0; i < ops; i++) {
            int kind = dst.nextInt(3);
            opsLeft++;
            if (kind == 0) {
                operator.schedule("stop route", t, () -> whenIdle(context, operator, "stop route", () -> {
                    if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
                        opsLeft--;
                        return;
                    }
                    try {
                        suspendPolls(context);
                        context.getRouteController().stopRoute(ROUTE);
                        ledger.record("routeStopped", "r", ROUTE);
                    } catch (Exception e) {
                        violations.add("stop failed: " + e);
                    }
                    operator.schedule("start route when idle", dst.between(0, 60_000),
                            () -> startWhenIdle(context, operator));
                }));
            } else if (kind == 1) {
                operator.schedule("suspend and resume route", t, () -> whenIdle(context, operator, "suspend route", () -> {
                    try {
                        if (context.getRouteController().getRouteStatus(ROUTE) == ServiceStatus.Started) {
                            suspendPolls(context);
                            context.getRouteController().suspendRoute(ROUTE);
                            ledger.record("routeSuspended", "r", ROUTE);
                            // half of the time longer than a shard iterator is valid
                            long suspended = dst.chance(0.5)
                                    ? dst.between(5 * 60_000L, 8 * 60_000L) : dst.between(1000, 60_000);
                            dst.block(suspended, "route suspended");
                            context.getRouteController().resumeRoute(ROUTE);
                            ledger.record("routeResumed", "r", ROUTE);
                        }
                    } catch (Exception e) {
                        violations.add("suspend/resume failed: " + e);
                    }
                    opsLeft--;
                }));
            } else {
                operator.schedule("reshard", t, () -> {
                    List<FakeKinesis.FakeShard> open = kinesis.openShards();
                    if (open.size() >= 2 && kinesis.shards.size() < 6 && dst.chance(0.5)) {
                        kinesis.merge(open.get(0), open.get(1));
                    } else if (kinesis.shards.size() < 6) {
                        kinesis.split(open.get(dst.nextInt(open.size())));
                    }
                    opsLeft--;
                });
            }
            t += dst.between(1000, 120_000);
        }
    }

    /**
     * An in-memory resume cache with the contract of {@link ResumeCache#forEach} that camel-caffeine's CaffeineCache
     * has: the entry is removed when the action returns true. It outlives the consumers of the route.
     */
    static final class MapResumeCache implements ResumeCache<Object> {
        private final Map<Object, Object> entries = new HashMap<>();

        @Override
        public Object computeIfAbsent(Object key, Function<? super Object, ? super Object> mapping) {
            return entries.computeIfAbsent(key, mapping);
        }

        @Override
        public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ? super Object> remapping) {
            return entries.computeIfPresent(key, remapping);
        }

        @Override
        public boolean contains(Object key, Object entry) {
            return entry.equals(entries.get(key));
        }

        @Override
        public void add(Object key, Object offsetValue) {
            entries.put(key, offsetValue);
        }

        @Override
        public boolean isFull() {
            return false;
        }

        @Override
        public long capacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public <T> T get(Object key, Class<T> clazz) {
            return clazz.cast(entries.get(key));
        }

        @Override
        public Object get(Object key) {
            return entries.get(key);
        }

        @Override
        public void forEach(BiFunction<? super Object, ? super Object, Boolean> action) {
            // in key order, so that a run replays exactly
            List<Object> keys = new ArrayList<>(entries.keySet());
            keys.sort((a, b) -> a.toString().compareTo(b.toString()));
            for (Object key : keys) {
                if (action.apply(key, entries.get(key))) {
                    entries.remove(key);
                }
            }
        }
    }
}
