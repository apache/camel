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
package org.apache.camel.component.nats.dst;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.nats.NatsConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around a camel-nats JetStream pull consumer: a fake NATS server with one stream ({@link FakeJetStream}) on
 * a connection shared through the connection option, a publisher, a route whose processing is sometimes slower than
 * AckWait and sometimes fails, and an operator that stops and starts the route (also while the consumer waits in a
 * fetch) and restarts the server (the ephemeral consumers are deleted). Faults: fetches fail, the flush of the stop
 * times out, acks are lost.
 * <p>
 * The seed picks pullBatchSize, pullFetchTimeout, ackWait, maxDeliver, nackWait, a durable or an ephemeral consumer,
 * and the fault rates.
 * <p>
 * The consumer's fetch loop runs for ever, so it is the one endless task of the run. The operator stops the route only
 * when no exchange is in the route: a stop nested above an exchange that blocks would wait for it for ever on the one
 * stack. The stop of a consumer that waits in a fetch ends that fetch (its subscription is unsubscribed), and the loop
 * ends as soon as the stop returned.
 * <p>
 * Properties: a message is acknowledged only after its exchange completed, and nak'd only after it failed; a stopped
 * consumer leaves no subscription active on the shared connection, and does not close it; once the faults cleared every
 * message is acknowledged, or given up by the server after MaxDeliver (or skipped by a consumer created again after a
 * server restart, when the ack of the message was lost); the consumer uses the connection as documented. Messages may
 * be delivered more than once (AckWait, lost acks, a consumer created again after a server restart).
 * <p>
 * Not explored: push subscriptions and core NATS subjects (their dispatchers deliver on threads of jnats), manualAck,
 * AckAll and AckNone, a connection created by the consumer itself; a stop between two messages of a fetched batch (the
 * operator stops the route only while no exchange is in it); the interrupt of the consumer thread by the stop (the
 * harness interrupts nothing), so only the flush and subscribe parts of CAMEL-26884 are checked; a start that comes
 * right after a stop, while the stopped consumer task still subscribes (the operator starts the route again after 100
 * ms or more).
 */
@DstWorld(component = "nats",
          fake = "FakeJetStream: a server with one JetStream stream and its pull consumers (AckWait, MaxDeliver, Nak),"
                 + " behind a jnats Connection given with the connection option",
          covers = {
                  "the JetStream pull consumer with explicit acks and naks, ephemeral or durable",
                  "AckWait and MaxDeliver",
                  "route stop and start, also while the consumer waits in a fetch",
                  "a server restart that deletes the ephemeral consumer, and the subscription made again" },
          faults = {
                  "fetch fails", "the flush of the stop times out", "an ack is lost", "the server restarts",
                  "processing fails", "processing is slower than AckWait" },
          invariants = {
                  "the consumer uses the connection as documented",
                  "a message is acknowledged only after its exchange completed",
                  "a message is nak'd only after its exchange failed",
                  "a stopped consumer leaves no subscription active",
                  "every message is acknowledged once the faults cleared, or given up after MaxDeliver (or skipped by a"
                                                                      + " consumer made again after its ack was lost)" },
          issues = { "CAMEL-26884" })
final class NatsWorld {

    static final String ROUTE = "nats";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakeJetStream nats;
    private final int batch;
    private final long fetchTimeout;
    private final long nackWait;
    private final boolean durable;
    private final double processFailRate;
    private final List<String> violations = new ArrayList<>();
    private final List<String> wrongAcks = new ArrayList<>();
    private final List<String> leftActive = new ArrayList<>();
    private final Set<String> completed = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;
    private int inRoute;
    private boolean finished;
    private String finalStatus;
    private boolean finalSettled;

    NatsWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.nats = new FakeJetStream(dst, ledger);
        this.batch = dst.pick(List.of(1, 10));
        this.fetchTimeout = dst.pick(List.of(100L, 1000L, 5000L));
        this.nackWait = dst.pick(List.of(0L, 1000L));
        this.durable = dst.chance(0.3);
        nats.ackWaitMillis = dst.pick(List.of(5000L, 30_000L));
        nats.maxDeliver = dst.pick(List.of(-1, 3));
        nats.fetchFailRate = dst.pick(List.of(0.0, 0.05));
        nats.ackLossRate = dst.pick(List.of(0.0, 0.05));
        nats.flushFailRate = dst.pick(List.of(0.0, 0.5));
        this.processFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        nats.onAck = d -> check(d, completed, "acknowledged");
        nats.onNak = d -> check(d, failed, "nak'd");
        dst.trace("world: " + config());
    }

    private String config() {
        return "pullBatchSize=" + batch + " pullFetchTimeout=" + fetchTimeout + " ackWait=" + nats.ackWaitMillis
               + " maxDeliver=" + nats.maxDeliver + " nackWait=" + nackWait + " durable=" + durable + " fetchFail="
               + nats.fetchFailRate + " ackLoss=" + nats.ackLossRate + " flushFail=" + nats.flushFailRate
               + " processFail=" + processFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        context.getRegistry().bind("nats", nats.connection());
        String uri = "nats:" + FakeJetStream.TOPIC + "?connection=#nats&jetstreamEnabled=true&jetstreamName="
                     + FakeJetStream.STREAM + "&pullSubscription=true&ackPolicy=Explicit&ackWait=" + nats.ackWaitMillis
                     + (nats.maxDeliver > 0 ? "&maxDeliver=" + nats.maxDeliver : "") + "&nackWait=" + nackWait
                     + "&pullBatchSize=" + batch + "&pullFetchTimeout=" + fetchTimeout + "&flushConnection=true"
                     + "&flushTimeout=1000" + (durable ? "&durableName=dst" : "");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId(ROUTE).autoStartup(false)
                        .process(exchange -> process(exchange));
            }
        });
        context.start();
        DstExecutor operator = dst.executor("operator", 1);
        try {
            operator.schedule("start route", 0, () -> startRoute(context));
            planSends();
            planOperations(context, operator);
            operator.schedule("finisher", 1000, () -> finisher(context, operator, -1));
            dst.runUntil(() -> finished, "the finisher");
            // the fetch loop ends after the stop returned
            dst.runFor(10_000);
            if (nats.activeSubscriptions() > 0) {
                leftActive.add(nats.activeSubscriptions() + " active after the final stop");
            }
            dst.check(
                    Invariants.of("the consumer uses the connection as documented",
                            () -> nats.violations.isEmpty() && violations.isEmpty()
                                    ? null : nats.violations + " " + violations + " (" + config() + ")"),
                    Invariants.of("a message is acknowledged only after its exchange completed",
                            () -> wrongAcks.stream().noneMatch(s -> s.contains("acknowledged"))
                                    ? null : wrongAcks + " (" + config() + ")"),
                    Invariants.of("a message is nak'd only after its exchange failed",
                            () -> wrongAcks.stream().noneMatch(s -> s.contains("nak'd"))
                                    ? null : wrongAcks + " (" + config() + ")"),
                    Invariants.of("a stopped consumer leaves no subscription active",
                            () -> leftActive.isEmpty() ? null : leftActive + " (" + config() + ")"),
                    Invariants.of("every message is acknowledged once the faults cleared, or given up after MaxDeliver",
                            () -> finalSettled && "Started".equals(finalStatus)
                                    ? null
                                    : "route " + finalStatus + ", unsettled " + nats.unsettled() + " (" + config()
                                      + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private void check(String delivery, Set<String> allowed, String what) {
        if (!allowed.contains(delivery)) {
            wrongAcks.add(delivery + " " + what + " at t=" + dst.now());
        }
    }

    private void process(Exchange exchange) {
        String id = exchange.getMessage().getBody(String.class);
        Long counter = exchange.getMessage().getHeader(NatsConstants.NATS_DELIVERY_COUNTER, Long.class);
        String delivery = id + "#" + counter;
        inRoute++;
        try {
            ledger.record("routed", "s", delivery);
            if (faults && dst.chance(0.1)) {
                dst.block(dst.between(nats.ackWaitMillis / 2, 2 * nats.ackWaitMillis), "slow processing of " + delivery);
            } else {
                dst.block(dst.between(0, 200), "processing of " + delivery);
            }
            if (faults && dst.chance(processFailRate)) {
                failed.add(delivery);
                ledger.record("failed", "s", delivery);
                throw Dst.injected(new IllegalStateException("dst: processing failed for " + delivery));
            }
            completed.add(delivery);
            ledger.record("completed", "s", delivery);
        } finally {
            inRoute--;
        }
    }

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
            nats.faults = false;
            dst.trace("world: faults cleared");
            long d = dst.now() + 30 * 60_000L;
            operator.schedule("finisher", 500, () -> finisher(context, operator, d));
            return;
        }
        ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
        boolean settled = nats.settled();
        if ((inRoute > 0 || !settled || status != ServiceStatus.Started) && dst.now() < deadline) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, deadline));
            return;
        }
        finalStatus = String.valueOf(status);
        finalSettled = settled;
        if (inRoute > 0) {
            violations.add("an exchange is still in the route at the deadline");
        } else {
            try {
                context.getRouteController().stopRoute(ROUTE);
            } catch (Exception e) {
                violations.add("final stop failed: " + e);
            }
        }
        finished = true;
    }

    private void startRoute(DefaultCamelContext context) {
        if (nats.activeSubscriptions() > 0) {
            leftActive.add(nats.activeSubscriptions() + " active when the route starts again at t=" + dst.now());
        }
        try {
            context.getRouteController().startRoute(ROUTE);
            ledger.record("routeStarted", "r", ROUTE);
        } catch (Exception e) {
            violations.add("start failed: " + e);
        }
    }

    private void planSends() {
        DstExecutor publisher = dst.executor("publisher", 1);
        long t = dst.between(0, 500);
        int n = 0;
        int bursts = (int) dst.between(1, 4);
        for (int b = 0; b < bursts; b++) {
            int size = (int) dst.between(1, 15);
            for (int i = 0; i < size; i++) {
                String id = "m" + n++;
                sendsLeft++;
                publisher.schedule("publish " + id, t, () -> {
                    nats.publish(id);
                    sendsLeft--;
                });
                t += dst.between(0, 100);
            }
            t += dst.between(0, 20_000);
        }
    }

    private void planOperations(DefaultCamelContext context, DstExecutor operator) {
        int ops = (int) dst.between(0, 3);
        long t = dst.between(500, 3000);
        for (int i = 0; i < ops; i++) {
            opsLeft++;
            if (dst.chance(0.7)) {
                operator.schedule("stop route", t, () -> stopWhenIdle(context, operator));
            } else {
                operator.schedule("server restart", t, () -> {
                    nats.restartServer();
                    opsLeft--;
                });
            }
            t += dst.between(1000, 20_000);
        }
    }

    /** Stops the route once no exchange is in it, then starts it again after a while. */
    private void stopWhenIdle(DefaultCamelContext context, DstExecutor operator) {
        if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
            opsLeft--;
            return;
        }
        if (inRoute > 0) {
            operator.schedule("stop route (an exchange is in the route)", 50, () -> stopWhenIdle(context, operator));
            return;
        }
        try {
            context.getRouteController().stopRoute(ROUTE);
            ledger.record("routeStopped", "r", ROUTE);
        } catch (Exception e) {
            violations.add("stop failed: " + e);
        }
        operator.schedule("start route", dst.between(100, 5000), () -> {
            startRoute(context);
            opsLeft--;
        });
    }
}
