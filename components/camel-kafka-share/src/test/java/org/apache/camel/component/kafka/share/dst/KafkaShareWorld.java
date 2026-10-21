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
package org.apache.camel.component.kafka.share.dst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.share.KafkaShareClientFactory;
import org.apache.camel.component.kafka.share.KafkaShareComponent;
import org.apache.camel.component.kafka.share.KafkaShareConfiguration;
import org.apache.camel.component.kafka.share.KafkaShareConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.Invariants;
import org.apache.kafka.clients.consumer.ShareConsumer;

/**
 * The world around a camel-kafka-share consumer: a fake share group ({@link FakeShareGroup}), a producer, a route whose
 * processing is sometimes slow (longer than the acquisition lock) and sometimes fails, and an operator that starts,
 * restarts, suspends and resumes the route. Faults: the share consumer cannot be created, polls fail, commits fail,
 * locks expire.
 * <p>
 * consumersCount is 1: a consumer thread loops for ever, and with the one-stack model a second looping thread nested
 * above the first would never let it resume. For the same reason a route stop that runs while the consumer thread waits
 * (in poll or in the route) cannot see it finish: the stop waits for its timeouts and the thread ends later. A stop
 * above an exchange that blocks in the route would wait for ever, so the processing takes time (and can outlast the
 * acquisition lock) only once the operations are done. The locks of the consumer do not exclude on the one stack, so a
 * stop never waits for the processing lock of the consumer thread.
 * <p>
 * Properties: every published record completes in the route at least once, unless the share group archived it (delivery
 * limit, REJECT); a record is accepted only for a delivery the route completed; the group is drained once the faults
 * cleared; a started route holds one open share consumer, a stopped one none; the consumer uses the client as the
 * Javadoc requires.
 */
final class KafkaShareWorld {

    static final String ROUTE = "share";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakeShareGroup group;
    private final String onFailure;
    private final String commitMode;
    private final String pollOnError;
    private final long pollTimeoutMs;
    private final long shutdownTimeout;
    private final long backoffInterval;
    private final List<String> violations = new ArrayList<>();
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;
    private boolean finished;
    private String finalStatus;
    private String finalOpen;
    private int finalOpenCount;
    private boolean finalSettled;

    KafkaShareWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.group = new FakeShareGroup(dst, ledger);
        this.onFailure = dst.pick(List.of("RELEASE", "REJECT"));
        this.commitMode = dst.pick(List.of("SYNC", "ASYNC"));
        this.pollOnError = dst.pick(Arrays.asList(
                System.getProperty("dst.share.pollOnError", "ERROR_HANDLER,RECONNECT,RETRY,DISCARD").split(",")));
        this.pollTimeoutMs = dst.pick(List.of(100L, 1000L, 5000L));
        this.shutdownTimeout = dst.pick(List.of(1000L, 30000L));
        this.backoffInterval = dst.pick(List.of(500L, 5000L));
        group.maxPollRecords = dst.pick(List.of(1, 3, 10));
        group.lockDurationMillis = dst.pick(List.of(5000L, 15000L, 30000L));
        group.deliveryLimit = dst.pick(List.of(5, 10));
        group.createFailRate = dst.pick(List.of(0.0, 0.2, 0.5));
        group.pollFailRate = dst.pick(List.of(0.0, 0.02, 0.1));
        group.commitFailRate = dst.pick(List.of(0.0, 0.05));
        dst.trace("world: " + config());
    }

    private String config() {
        return "onFailure=" + onFailure + " commitMode=" + commitMode + " pollOnError=" + pollOnError
               + " pollTimeoutMs=" + pollTimeoutMs + " shutdownTimeout=" + shutdownTimeout + " backoff=" + backoffInterval
               + " maxPollRecords=" + group.maxPollRecords + " lock=" + group.lockDurationMillis + " deliveryLimit="
               + group.deliveryLimit + " createFail=" + group.createFailRate + " pollFail=" + group.pollFailRate
               + " commitFail=" + group.commitFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        KafkaShareComponent component = new KafkaShareComponent();
        component.setCreateConsumerBackoffInterval(backoffInterval);
        component.setKafkaShareClientFactory(new KafkaShareClientFactory() {
            @Override
            public ShareConsumer<Object, Object> getShareConsumer(Properties kafkaProps) {
                return group.newConsumer();
            }

            @Override
            public String getBrokers(KafkaShareConfiguration configuration) {
                return configuration.getBrokers();
            }
        });
        context.addComponent("kafka-share", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:" + FakeShareGroup.TOPIC + "?brokers=127.0.0.1:9092&groupId=g&consumersCount=1"
                     + "&pollTimeoutMs=" + pollTimeoutMs + "&maxPollRecords=" + group.maxPollRecords
                     + "&acquireMode=record_limit&onFailure=" + onFailure + "&commitMode=" + commitMode
                     + "&pollOnError=" + pollOnError + "&shutdownTimeout=" + shutdownTimeout)
                        .routeId(ROUTE).autoStartup(false)
                        .process(exchange -> {
                            String id = exchange.getMessage().getBody(String.class);
                            Object d = exchange.getMessage().getHeader(KafkaShareConstants.DELIVERY_COUNT);
                            ledger.record("routed", "t", id);
                            // a route stop cannot see an exchange that blocks below it on the one stack finish (the
                            // shutdown strategy waits for it for ever), so the processing takes time only once the
                            // operations are done
                            if (faults && opsLeft == 0) {
                                long latency = dst.chance(0.1)
                                        ? group.lockDurationMillis + dst.between(0, group.lockDurationMillis)
                                        : dst.between(0, 300);
                                dst.block(latency, "process " + id + "#" + d);
                            }
                            if (faults && dst.chance(0.05)) {
                                ledger.record("failed", "t", id);
                                throw Dst.injected(new IllegalStateException("dst: processing failed for " + id));
                            }
                            group.completedDeliveries.add(id + "#" + d);
                            ledger.record("completed", "t", id);
                        });
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
            // the consumer thread ends after the stop; let it close its share consumer
            dst.runFor(2 * shutdownTimeout + 10_000);
            dst.check(
                    Invariants.of("the consumer uses the share consumer as documented",
                            () -> group.violations.isEmpty() && violations.isEmpty()
                                    ? null : group.violations + " " + violations + " (" + config() + ")"),
                    Invariants.of("the route is started once the faults cleared",
                            () -> "Started".equals(finalStatus) ? null : "route is " + finalStatus + " (" + config() + ")"),
                    Invariants.of("a started route holds one share consumer",
                            () -> finalOpenCount == 1
                                    ? null : "open share consumers " + finalOpen + " (" + config() + ")"),
                    Invariants.of("every published record completes at least once, or the group archived it",
                            this::everyRecordCompletedOrArchived),
                    Invariants.of("the share group is drained once the faults cleared",
                            () -> finalSettled ? null : "not settled at the end (" + config() + ")"),
                    Invariants.of("no share consumer is left open after the route stopped",
                            () -> group.open.isEmpty()
                                    ? null : "open after stop: " + group.openNames() + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private String everyRecordCompletedOrArchived() {
        List<String> missing = new ArrayList<>();
        List<String> completed = ledger.of("completed").stream().map(DstLedger.Entry::key).toList();
        for (FakeShareGroup.Rec r : group.log) {
            if (!completed.contains(r.id) && r.state != FakeShareGroup.State.ARCHIVED) {
                missing.add(r.id + "=" + r.state + "/d" + r.deliveries);
            }
        }
        return missing.isEmpty() ? null : "never completed: " + missing + " (" + config() + ")";
    }

    /**
     * Waits for the sends and operations, switches the faults off, waits for the group to drain (or a deadline), takes
     * the final state and stops the route.
     */
    private void finisher(DefaultCamelContext context, DstExecutor operator, long deadline) {
        if (deadline == -1 && (sendsLeft > 0 || opsLeft > 0)) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, -1));
            return;
        }
        if (deadline == -1) {
            long slow = dst.between(0, 3 * group.lockDurationMillis);
            dst.trace("world: operations done, slow processing for " + slow + " ms");
            operator.schedule("finisher", slow, () -> finisher(context, operator, -2));
            return;
        }
        if (deadline == -2) {
            faults = false;
            group.faults = false;
            dst.trace("world: faults cleared");
            long d = dst.now() + 5 * 60_000L + 3 * group.lockDurationMillis;
            operator.schedule("finisher", 500, () -> finisher(context, operator, d));
            return;
        }
        ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
        boolean settled = group.settled();
        if ((!settled || status != ServiceStatus.Started || group.open.size() != 1) && dst.now() < deadline) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, deadline));
            return;
        }
        finalStatus = String.valueOf(status);
        finalSettled = settled;
        finalOpenCount = group.open.size();
        finalOpen = group.openNames();
        if (!settled) {
            dst.trace("world: unsettled " + group.unsettled());
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

    private void planSends() {
        DstExecutor producer = dst.executor("producer", 1);
        long t = dst.between(0, 500);
        int n = 0;
        int bursts = (int) dst.between(1, 4);
        for (int b = 0; b < bursts; b++) {
            int size = (int) dst.between(1, 12);
            for (int i = 0; i < size; i++) {
                String id = "m" + n++;
                sendsLeft++;
                producer.schedule("publish " + id, t, () -> {
                    group.publish(id);
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
            int kind = dst.nextInt(2);
            opsLeft++;
            if (kind == 0) {
                operator.schedule("restart route", t, () -> {
                    try {
                        if (context.getRouteController().getRouteStatus(ROUTE) == ServiceStatus.Started) {
                            context.getRouteController().stopRoute(ROUTE);
                            ledger.record("routeStopped", "r", ROUTE);
                            startRoute(context, operator);
                        }
                    } catch (Exception e) {
                        violations.add("restart failed: " + e);
                    }
                    opsLeft--;
                });
            } else {
                operator.schedule("suspend and resume route", t, () -> {
                    try {
                        if (context.getRouteController().getRouteStatus(ROUTE) == ServiceStatus.Started) {
                            context.getRouteController().suspendRoute(ROUTE);
                            dst.block(dst.between(100, 3000), "route suspended");
                            context.getRouteController().resumeRoute(ROUTE);
                        }
                    } catch (Exception e) {
                        violations.add("suspend/resume failed: " + e);
                    }
                    opsLeft--;
                });
            }
            t += dst.between(500, 8000);
        }
    }
}
