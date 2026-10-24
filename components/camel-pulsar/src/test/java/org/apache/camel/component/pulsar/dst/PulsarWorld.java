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
package org.apache.camel.component.pulsar.dst;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pulsar.PulsarComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around a camel-pulsar consumer: a fake broker and client ({@link FakePulsar}), a producer, a route with an
 * asynchronous downstream step that is sometimes slow and sometimes fails, and an operator that starts the route (and
 * starts it again when a start fails, as the supervising route controller does), restarts it, suspends and resumes it.
 * Faults: subscribe failures (broker unreachable for longer than the operation timeout), broker restarts.
 * <p>
 * Properties: every published message completes in the route at least once; the subscription is drained once the faults
 * cleared; a started route holds the configured number of Pulsar consumers (no fewer: it would not consume, no more:
 * they would leak).
 * <p>
 * Limits: messageListener=true and numberOfConsumerThreads=1 only (the receive() loops of messageListener=false block
 * on the one stack until the run ends); no manual acknowledgement, retry or dead letter topic options.
 */
final class PulsarWorld {

    static final String ROUTE = "pulsar";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakePulsar pulsar;
    private final String subscriptionType;
    private final int numberOfConsumers;
    private final boolean messageListener;
    private final int consumerThreads;
    private final int queueSize;
    private final long ackTimeoutMillis;
    private final long nackDelayMillis;
    private final List<String> violations = new ArrayList<>();
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;

    PulsarWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.pulsar = new FakePulsar(dst, ledger);
        this.subscriptionType = dst.pick(List.of("EXCLUSIVE", "SHARED", "FAILOVER", "KEY_SHARED"));
        this.numberOfConsumers = "EXCLUSIVE".equals(subscriptionType) ? 1 : dst.pick(List.of(1, 2, 3));
        this.messageListener = true;
        this.consumerThreads = 1;
        this.queueSize = dst.pick(List.of(1, 5, 10));
        this.ackTimeoutMillis = dst.pick(List.of(0L, 2000L, 10000L));
        this.nackDelayMillis = dst.pick(List.of(100L, 1000L, 60000L));
        this.pulsar.subscribeFailRate = dst.pick(Arrays.stream(System.getProperty("dst.pulsar.subscribeFailRates", "0,0.1,0.3")
                .split(",")).map(String::trim).map(Double::valueOf).toList());
        dst.trace("world: subscriptionType=" + subscriptionType + " numberOfConsumers=" + numberOfConsumers
                  + " messageListener=" + messageListener + " numberOfConsumerThreads=" + consumerThreads
                  + " consumerQueueSize=" + queueSize + " ackTimeoutMillis=" + ackTimeoutMillis
                  + " nackDelayMillis=" + nackDelayMillis + " subscribeFailRate=" + pulsar.subscribeFailRate);
    }

    private String config() {
        return subscriptionType + " consumers=" + numberOfConsumers + " listener=" + messageListener
               + " threads=" + consumerThreads + " queue=" + queueSize + " ackTimeout=" + ackTimeoutMillis
               + " nack=" + nackDelayMillis + " subscribeFail=" + pulsar.subscribeFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        PulsarComponent component = new PulsarComponent(context);
        component.setPulsarClient(pulsar.client());
        context.addComponent("pulsar", component);
        DstExecutor downstream = dst.executor("downstream", 4);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("pulsar:persistent://public/default/dst?subscriptionName=subs"
                     + "&subscriptionType=" + subscriptionType + "&numberOfConsumers=" + numberOfConsumers
                     + "&messageListener=" + messageListener + "&numberOfConsumerThreads=" + consumerThreads
                     + "&consumerQueueSize=" + queueSize + "&ackTimeoutMillis=" + ackTimeoutMillis
                     + "&negativeAckRedeliveryDelayMicros=" + (nackDelayMillis * 1000)
                     + "&subscriptionInitialPosition=EARLIEST")
                        .routeId(ROUTE).autoStartup(false)
                        .process(exchange -> {
                            String id = new String(exchange.getMessage().getBody(byte[].class), StandardCharsets.UTF_8);
                            exchange.setProperty("dstId", id);
                            ledger.record("routed", "t", id);
                        })
                        .process(DstCamel.asyncStep(dst, downstream, 1, 3000, exchange -> {
                            String id = exchange.getProperty("dstId", String.class);
                            if (faults && dst.chance(0.05)) {
                                ledger.record("failed", "t", id);
                                throw Dst.injected(new IllegalStateException("dst: downstream failed for " + id));
                            }
                            ledger.record("completed", "t", id);
                        }));
            }
        });
        context.start();
        DstExecutor operator = dst.executor("operator", 1);
        try {
            operator.schedule("start route", 0, () -> startRoute(context, operator));
            planSends();
            planOperations(context, operator);
            dst.runUntil(() -> sendsLeft == 0 && opsLeft == 0, "all sends and operations");
            dst.runFor(dst.between(0, 5000));
            faults = false;
            pulsar.faults = false;
            long deadline = dst.now() + 5 * 60_000L + 3 * nackDelayMillis;
            // a start that failed while the faults were on is retried by the operator: wait for it as well
            while ((!pulsar.allAcked() || context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started)
                    && dst.now() < deadline) {
                dst.runFor(500);
            }
            ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
            dst.check(
                    Invariants.of("no unexpected client call or consumer count", () -> violations.isEmpty()
                            ? null : violations.toString()),
                    Invariants.of("the route is started", () -> status == ServiceStatus.Started
                            ? null : "route is " + status + " (" + config() + ")"),
                    Invariants.of("a started route holds the configured number of Pulsar consumers",
                            () -> pulsar.openConsumers() == numberOfConsumers
                                    ? null
                                    : "open consumers " + pulsar.openConsumerNames() + ", configured " + numberOfConsumers
                                      + " (" + config() + ")"),
                    Invariants.everyOfferedRoutedAtLeastOnce(ledger, "published", "completed"),
                    Invariants.of("the subscription is drained once the faults cleared",
                            () -> pulsar.allAcked()
                                    ? null : "not acknowledged: " + pulsar.notAcked() + " (" + config()
                                             + ")"));
        } finally {
            // stop the route while the answers of the downstream step can still arrive: an exchange whose answer
            // dropPending() drops would keep the shutdown waiting on the one stack
            try {
                context.getRouteController().stopRoute(ROUTE);
            } catch (Exception e) {
                dst.trace("world: stop route failed (" + e + ")");
            }
            dst.dropPending();
            context.stop();
        }
    }

    /** The operator starts the route; a failed start is retried, as the supervising route controller does. */
    private void startRoute(DefaultCamelContext context, DstExecutor operator) {
        try {
            context.getRouteController().startRoute(ROUTE);
            ledger.record("routeStarted", "r", ROUTE);
            checkConsumerCount("after start");
        } catch (Exception e) {
            ledger.record("routeStartFailed", "r", ROUTE);
            dst.trace("world: start failed (" + e.getMessage() + "), retry in 2 s");
            operator.schedule("retry start route", 2000, () -> startRoute(context, operator));
        }
    }

    private void checkConsumerCount(String when) {
        if (pulsar.openConsumers() > numberOfConsumers) {
            violations.add("more consumers than configured " + when + ": " + pulsar.openConsumerNames() + " ("
                           + config() + ")");
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
                    pulsar.publish(id);
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
                operator.schedule("restart route", t, () -> {
                    try {
                        if (context.getRouteController().getRouteStatus(ROUTE) == ServiceStatus.Started) {
                            context.getRouteController().stopRoute(ROUTE);
                            ledger.record("routeStopped", "r", ROUTE);
                            if (pulsar.openConsumers() > 0) {
                                violations.add("consumers left open after the route stopped: "
                                               + pulsar.openConsumerNames() + " (" + config() + ")");
                            }
                            startRoute(context, operator);
                        }
                    } catch (Exception e) {
                        violations.add("restart failed: " + e);
                    }
                    opsLeft--;
                });
            } else if (kind == 1) {
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
            } else {
                operator.schedule("broker restart", t, () -> {
                    if (faults) {
                        pulsar.brokerRestart();
                    }
                    opsLeft--;
                });
            }
            t += dst.between(500, 8000);
        }
    }
}
