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
package org.apache.camel.component.chaos.dst;

import java.util.List;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.ChaosManager;
import org.apache.camel.component.chaos.ChaosReport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.Invariants;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * A route under chaos rules in virtual time: a route consumes a {@link FakeQueue} and calls a downstream system
 * asynchronously ({@code DstCamel.asyncStep}, as the one-stack model of camel-test-dst needs for a route that is
 * suspended or stopped while an exchange is in a downstream call). Two chaos rules, added through the public
 * {@link ChaosManager} API, which {@code camel.chaos.rules} uses too:
 * <ul>
 * <li>a lifecycle rule ({@code stall}, or {@code lifecycle} with {@code action=suspend}, {@code stop} or
 * {@code remove}, for 1 to 10 minutes), added by an operator at a seeded time once an exchange is in flight (so the
 * suspend or stop waits for it), with an expectation that the route completes every message it has not completed yet,
 * and</li>
 * <li>a step rule that fails the downstream call at a seeded rate, which the error handler redelivers.</li>
 * </ul>
 * The seed of the run is {@code camel.chaos.seed}, and the seed picks the rule, its time, the duration, the failure
 * rate, the redeliveries and the messages: a burst before the rule, the others after, one of them halfway through the
 * time the route is down. The checks read the chaos report (no message is lost: the expectation is met; the route
 * completed every message once; the redeliveries are the step faults that did not exhaust the error handler; the route
 * was down for exactly the duration), the fake queue (every message deleted once; none delivered while the route was
 * down, the one published then only after) and the route controller (the route is started).
 * <p/>
 * The thing under test is camel-chaos with the route lifecycle of camel-core, not a component, so the world is not a
 * {@code @DstWorld} of the catalog (which lists the worlds of components by their scheme).
 */
final class ChaosLifecycleWorld {

    static final String ROUTE = "orders";

    private final Dst dst;
    final String fault;
    final long duration;
    final int messages;
    final String failureRate;
    final int maxRedeliveries;
    final long ruleAt;
    final FakeQueue queue;

    private int sendsLeft;
    private String lifecycleId;
    private long ruleAddedAt = -1;
    private int inflightAtRule;
    private DstExecutor producer;
    // the report at the end of the run, as JSON text
    String report;

    ChaosLifecycleWorld(Dst dst) {
        this(dst, null, -1);
    }

    /** A world with the given lifecycle fault and duration, or the seed's pick for null and -1. */
    ChaosLifecycleWorld(Dst dst, String fault, long duration) {
        this.dst = dst;
        this.fault = fault != null
                ? fault : dst.pick(List.of("stall", "lifecycle action=suspend", "lifecycle action=stop",
                        "lifecycle action=remove"));
        this.duration = duration > 0 ? duration : dst.pick(List.of(60_000L, 300_000L, 600_000L));
        this.messages = (int) dst.between(5, 30);
        // a step rule from the start also makes the report count the route from the start
        this.failureRate = dst.pick(List.of("0.1", "0.3"));
        this.maxRedeliveries = dst.pick(List.of(1, 2, 5));
        this.ruleAt = dst.between(2_000, 10_000);
        this.queue = new FakeQueue(dst);
        dst.trace("world: fault=" + this.fault + " duration=" + this.duration + " messages=" + messages
                  + " failureRate=" + failureRate + " maxRedeliveries=" + maxRedeliveries + " ruleAt=" + ruleAt);
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        // the exchanges in flight finish while a route is suspended or stopped: give them time (virtual)
        context.getShutdownStrategy().setTimeout(300);
        context.getPropertiesComponent().addInitialProperty("camel.chaos.enabled", "true");
        context.getPropertiesComponent().addInitialProperty("camel.chaos.seed", String.valueOf(dst.seed()));
        context.getPropertiesComponent().addInitialProperty("camel.chaos.maxDuration", "1h");
        context.getPropertiesComponent().addInitialProperty("camel.chaos.reportOnShutdown", "false");
        context.addComponent("queue", queue.component());
        DstExecutor downstream = dst.executor("downstream", 1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // redeliveries without a delay: a delay would sleep on the thread of the exchange, and an exchange that
                // sleeps below the route stop on the one stack of the run could not finish (test-dst.adoc); with
                // asyncDelayedRedelivery the delay runs on a thread of the error handler outside the harness
                errorHandler(defaultErrorHandler().maximumRedeliveries(maxRedeliveries).redeliveryDelay(0));
                from("queue:orders?initialDelay=0&delay=500&maxMessagesPerPoll=5").routeId(ROUTE)
                        .process(DstCamel.asyncStep(dst, downstream, 100, 2_000, exchange -> {
                        })).id("downstream");
            }
        });
        try {
            context.start();
            ChaosManager chaos = ChaosManager.get(context);
            String stepRule = chaos.addRule("step=downstream error rate=" + failureRate + " until=50m");
            DstExecutor operator = dst.executor("operator", 1);
            operator.schedule("add the lifecycle rule", ruleAt, () -> addLifecycleRule(context, chaos, operator));
            planSends();

            long cap = 3_600_000L;
            dst.runUntil(() -> sendsLeft == 0 && queue.drained() && lifecycleDone(chaos) || dst.now() > cap,
                    "all messages completed and the route back");
            ChaosReport end = chaos.report();
            report = end.toJson().toJson();
            dst.trace("world: report " + report);
            check(end, stepRule, context.getRouteController().getRouteStatus(ROUTE));
        } finally {
            // after a run that drained the queue the stop waits for no exchange; then the polls left are dropped
            context.stop();
            dst.dropPending();
        }
    }

    /**
     * The operator adds the lifecycle rule once an exchange is in flight in the route (it retries every 50 ms, until
     * the last message is published), and the producer publishes m0 halfway through the time the route is down.
     */
    private void addLifecycleRule(DefaultCamelContext context, ChaosManager chaos, DstExecutor operator) {
        inflightAtRule = context.getInflightRepository().size(ROUTE);
        if (inflightAtRule == 0 && dst.now() < ruleAt + duration + 60_000) {
            operator.schedule("add the lifecycle rule", 50, () -> addLifecycleRule(context, chaos, operator));
            return;
        }
        ruleAddedAt = dst.now();
        // the expectation counts the exchanges the route completes from now: those of the messages not deleted yet
        int remaining = messages - queue.deleted;
        dst.trace("world: lifecycle rule with " + inflightAtRule + " exchanges in flight, " + remaining + " to complete");
        lifecycleId = chaos.addRule("route=" + ROUTE + " " + fault + " duration=" + duration + "ms expect=" + ROUTE + ":"
                                    + remaining + " within=50m");
        producer.schedule("publish m0", duration / 2, () -> {
            queue.publish("m0");
            sendsLeft--;
        });
    }

    private void planSends() {
        producer = dst.executor("producer", 1);
        // a burst before the rule, so an exchange is in flight when the route goes down; m0 is published by the
        // operator while the route is down; the others after the rule time, while the route is down and after
        int burst = (int) dst.between(2, Math.min(6, messages - 1));
        long span = duration + 60_000;
        sendsLeft++;
        for (int i = 1; i < messages; i++) {
            String id = "m" + i;
            sendsLeft++;
            long at = i <= burst ? ruleAt - dst.between(1, 1_500) : ruleAt + dst.between(1, span);
            producer.schedule("publish " + id, at, () -> {
                queue.publish(id);
                sendsLeft--;
            });
        }
    }

    private boolean lifecycleDone(ChaosManager chaos) {
        JsonObject l = lifecycle(chaos.report().toJson());
        return l != null && List.of("back", "skipped", "left", "failed", "cancelled").contains(l.getString("state"));
    }

    private JsonObject rule(JsonObject report, String id) {
        for (Object o : (JsonArray) report.get("rules")) {
            JsonObject rule = (JsonObject) o;
            if (rule.getString("id").equals(id)) {
                return rule;
            }
        }
        return null;
    }

    private JsonObject lifecycle(JsonObject report) {
        JsonObject rule = lifecycleId != null ? rule(report, lifecycleId) : null;
        return rule != null ? (JsonObject) rule.get("lifecycle") : null;
    }

    private void check(ChaosReport end, String stepRule, ServiceStatus status) {
        JsonObject json = end.toJson();
        JsonObject routes = null;
        for (Object o : (JsonArray) json.get("routes")) {
            if (ROUTE.equals(((JsonObject) o).getString("route"))) {
                routes = (JsonObject) o;
            }
        }
        JsonObject counts = routes;
        JsonObject lifecycle = lifecycle(json);
        String config = " (fault=" + fault + " duration=" + duration + " messages=" + messages + " failureRate="
                        + failureRate + " maxRedeliveries=" + maxRedeliveries + ")";
        dst.check(
                Invariants.of("no message is lost: the expectation of the lifecycle rule is met, the verdict PASS",
                        () -> ChaosReport.PASS.equals(end.getVerdict()) && end.isFinal() && end.getLost() == 0
                                && "met".equals(((JsonObject) rule(json, lifecycleId).get("expectation"))
                                        .getString("outcome"))
                                                ? null : end.toText() + config),
                Invariants.of("the route completed every message once",
                        () -> counts != null && counts.getLong("completed") == messages
                                ? null : "routes " + counts + config),
                Invariants.of("the redeliveries are the step faults that did not exhaust the error handler",
                        () -> {
                            long fired = ((JsonObject) rule(json, stepRule).get("fired")).getLong("step");
                            long expected = fired - counts.getLong("failed");
                            return counts.getLong("redeliveries") == expected
                                    && counts.getLong("failed") == queue.failedBack
                                            ? null
                                            : "fired " + fired + ", routes " + counts + ", failed back to the queue "
                                              + queue.failedBack + config;
                        }),
                Invariants.of("the route was down for the duration, and is back",
                        () -> lifecycle != null && "back".equals(lifecycle.getString("state"))
                                && lifecycle.getLong("backAt") - lifecycle.getLong("downAt") == duration
                                        ? null : "lifecycle " + lifecycle + config),
                Invariants.of("the route took no message while it was down",
                        () -> {
                            if (lifecycle == null || !lifecycle.containsKey("backAt")) {
                                return "the route did not come back: lifecycle " + lifecycle + config;
                            }
                            long from = ruleAddedAt + lifecycle.getLong("downAt");
                            long to = ruleAddedAt + lifecycle.getLong("backAt");
                            List<Long> during = queue.deliveries.stream().filter(t -> t > from && t < to).toList();
                            return during.isEmpty()
                                    ? null : "deliveries at " + during + " while down from " + from + " to " + to + config;
                        }),
                Invariants.of("the message published while the route was down was delivered after it came back",
                        () -> {
                            if (lifecycle == null || !lifecycle.containsKey("backAt")) {
                                return "the route did not come back: lifecycle " + lifecycle + config;
                            }
                            Long first = queue.firstDelivery.get("m0");
                            long back = ruleAddedAt + lifecycle.getLong("backAt");
                            return first != null && first >= back
                                    ? null : "m0 first delivered at " + first + ", back at " + back + config;
                        }),
                Invariants.of("the queue deleted every message once, and is drained",
                        () -> queue.drained() && queue.deleted == messages
                                ? null : queue.size() + " messages left, " + queue.deleted + " deleted" + config),
                Invariants.of("an exchange was in flight when the lifecycle rule was added",
                        () -> inflightAtRule > 0 ? null : "no exchange in flight" + config),
                Invariants.of("the route is started at the end",
                        () -> status == ServiceStatus.Started ? null : "route " + ROUTE + " is " + status + config));
    }
}
