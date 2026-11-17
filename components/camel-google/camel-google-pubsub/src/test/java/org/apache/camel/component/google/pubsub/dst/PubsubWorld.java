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
package org.apache.camel.component.google.pubsub.dst;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubConsumer;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around a camel-google-pubsub consumer with synchronousPull=true: a fake subscription ({@link FakePubsub}),
 * a publisher, a route whose processing is sometimes slower than the ack deadline and sometimes fails, and an operator
 * that stops and starts the route. Faults: pulls, acknowledgements and nacks fail, answers are slow, deadlines expire.
 * <p>
 * The seed picks the ack mode: AUTO (the consumer acks a completed exchange and nacks a failed one), or GROUPED
 * (ackMode=NONE, the route aggregates the messages with GroupedExchangeAggregationStrategy and acks or nacks the
 * aggregated exchange with the GooglePubsubAcknowledge of the header, which must ack every grouped message), and the
 * ack deadline, maxMessagesPerPoll, the dead letter policy of the subscription (with maxDeliveryAttempts set on the
 * endpoint to the same value) and the fault rates.
 * <p>
 * concurrentConsumers is 1: a pull loop runs for ever, and with the one-stack model a second loop nested above the
 * first would never let it resume. The operator stops the route only when no exchange is in the route and the consumer
 * has no pending exchange (the shutdown strategy would wait for it for ever on the one stack), so a stop happens while
 * the consumer waits for a pull. A route suspend stops this consumer (it is not Suspendable), so it is not driven
 * apart. With ackMode AUTO the operator also stops and starts the consumer itself in one step, as a throttling route
 * policy does, so that the pull loop of the previous start is still there when the consumer runs again. The graceful
 * shutdown (deferShutdown) cannot be reached between its stop of the pulls and the stop of the consumer on the one
 * stack, so a pull in that window is not driven.
 * <p>
 * Properties: a delivery is acknowledged only after its exchange completed, and nacked only after it failed or reached
 * maxDeliveryAttempts (or rejected by the stopped route); a delivery at maxDeliveryAttempts is nacked without routing
 * it; a manual ack of a group acknowledges every message of the group; a stopped consumer issues no pull, and the
 * consumer has at most one pull at a time; once the faults cleared every message is acknowledged or dead lettered by
 * the subscription, and a message published then is routed and acknowledged; the consumer uses the subscription as
 * documented. Messages may be delivered more than once (at-least-once: a deadline that expires, a lost
 * acknowledgement).
 * <p>
 * Not explored: ack deadline extensions (synchronousPull does not extend a deadline), the asynchronous subscriber
 * (synchronousPull=false: its lease management and redelivery are inside the client library), concurrentConsumers above
 * 1, message ordering, exactly-once delivery.
 */
@DstWorld(component = "google-pubsub",
          fake = "FakePubsub: a subscription (Pull, Acknowledge, ModifyAckDeadline, ack deadlines, dead letter policy)"
                 + " behind the SubscriberStub of synchronousPull",
          covers = {
                  "synchronousPull with ackMode AUTO, and ackMode NONE with grouped messages acked or nacked together",
                  "ack deadlines that expire while a message is processed",
                  "the max delivery attempts of a subscription with a dead letter policy",
                  "route stop and start while the consumer pulls",
                  "a stop and start of the same consumer in one step, as a throttling route policy does" },
          faults = {
                  "Pull fails", "Acknowledge fails", "ModifyAckDeadline (nack) fails", "calls answer slowly",
                  "Pull returns fewer messages than it could", "processing fails",
                  "processing is slower than the ack deadline" },
          invariants = {
                  "the consumer uses the subscription as documented",
                  "a delivery is acknowledged only after its exchange completed",
                  "a delivery is nacked only after its exchange failed or at the max delivery attempts",
                  "a manual ack of a group acknowledges every message of the group",
                  "a stopped consumer issues no pull, and the consumer has one pull at a time",
                  "a delivery at the max delivery attempts is nacked without routing it",
                  "every message is acknowledged or dead lettered once the faults cleared",
                  "a message published once the faults cleared is routed and acknowledged" },
          issues = { "CAMEL-25712", "CAMEL-25790", "CAMEL-26647" })
final class PubsubWorld {

    static final String ROUTE = "pubsub";
    /** Published once the faults cleared: a consumer that still pulls routes and acknowledges it. */
    static final String END = "end";

    private final Dst dst;
    private final DstLedger ledger;
    private final FakePubsub pubsub;
    private final boolean grouped;
    private final int maxMessagesPerPoll;
    private final double processFailRate;
    private final List<String> violations = new ArrayList<>();
    private final List<String> wrongAcks = new ArrayList<>();
    private final List<String> wrongNacks = new ArrayList<>();
    private final List<String> groupAcks = new ArrayList<>();
    private final Set<String> completed = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final Set<String> acked = new HashSet<>();
    private final Set<String> routed = new HashSet<>();
    private final List<String> routedAtMax = new ArrayList<>();
    private DefaultCamelContext context;
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;
    private int inRoute;
    private boolean finished;
    private String finalStatus;
    private boolean finalSettled;
    private boolean finalEndAcked;

    PubsubWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.pubsub = new FakePubsub(dst, ledger);
        this.grouped = dst.chance(0.3);
        this.maxMessagesPerPoll = dst.pick(List.of(1, 3, 10));
        pubsub.ackDeadlineSeconds = dst.pick(List.of(10, 20, 60));
        pubsub.maxDeliveryAttempts = dst.pick(List.of(0, 5));
        pubsub.pullFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        pubsub.ackFailRate = dst.pick(List.of(0.0, 0.05, 0.2));
        this.processFailRate = dst.pick(List.of(0.0, 0.05, 0.2, 0.6));
        pubsub.onAck = this::onAck;
        pubsub.onNack = this::onNack;
        dst.trace("world: " + config());
    }

    private String config() {
        return "mode=" + (grouped ? "GROUPED" : "AUTO") + " maxMessagesPerPoll=" + maxMessagesPerPoll + " ackDeadline="
               + pubsub.ackDeadlineSeconds + " maxDeliveryAttempts=" + pubsub.maxDeliveryAttempts + " pullFail="
               + pubsub.pullFailRate + " ackFail=" + pubsub.ackFailRate + " processFail=" + processFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        this.context = context;
        GooglePubsubComponent component = new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                return pubsub.newStub();
            }

            @Override
            public SubscriptionAdminClient getSubscriptionAdminClient(GooglePubsubEndpoint endpoint) {
                throw new UnsupportedOperationException("maxDeliveryAttempts is set on the endpoint");
            }
        };
        component.setAuthenticate(false);
        context.addComponent("google-pubsub", component);
        String uri = "google-pubsub:" + FakePubsub.PROJECT + ":" + FakePubsub.SUBSCRIPTION
                     + "?synchronousPull=true&concurrentConsumers=1&maxMessagesPerPoll=" + maxMessagesPerPoll
                     + "&ackMode=" + (grouped ? "NONE" : "AUTO") + "&maxDeliveryAttempts=" + pubsub.maxDeliveryAttempts;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (grouped) {
                    from(uri).routeId(ROUTE).autoStartup(false)
                            .aggregate(constant(true), new GroupedExchangeAggregationStrategy())
                            .completionSize(maxMessagesPerPoll).completionTimeout(500)
                            .process(exchange -> processGroup(exchange));
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
                    Invariants.of("the consumer uses the subscription as documented",
                            () -> pubsub.violations.isEmpty() && violations.isEmpty()
                                    ? null : pubsub.violations + " " + violations + " (" + config() + ")"),
                    Invariants.of("a delivery is acknowledged only after its exchange completed",
                            () -> wrongAcks.isEmpty() ? null : wrongAcks + " (" + config() + ")"),
                    Invariants.of("a delivery is nacked only after its exchange failed or at the max delivery attempts",
                            () -> wrongNacks.isEmpty() ? null : wrongNacks + " (" + config() + ")"),
                    Invariants.of("a delivery at the max delivery attempts is nacked without routing it",
                            () -> routedAtMax.isEmpty() ? null : routedAtMax + " (" + config() + ")"),
                    Invariants.of("a manual ack of a group acknowledges every message of the group",
                            () -> groupAcks.isEmpty() ? null : groupAcks + " (" + config() + ")"),
                    Invariants.of("a stopped consumer issues no pull, and the consumer has one pull at a time",
                            () -> pubsub.pullsWhileStopped.isEmpty() && pubsub.maxPullsInFlight <= 1
                                    ? null
                                    : "pulls while stopped " + pubsub.pullsWhileStopped + ", at most "
                                      + pubsub.maxPullsInFlight + " pulls at a time (" + config() + ")"),
                    Invariants.of("every message is acknowledged or dead lettered once the faults cleared, and a"
                                  + " message published then is routed and acknowledged",
                            () -> finalSettled && finalEndAcked && "Started".equals(finalStatus)
                                    ? null
                                    : "route " + finalStatus + ", unsettled " + pubsub.unsettled() + ", end message "
                                      + pubsub.stateOf(END) + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private String deliveryOf(Exchange exchange) {
        Message m = exchange.getMessage();
        String ackId = m.getHeader(GooglePubsubConstants.ACK_ID, String.class);
        return ackId == null ? null : ackId.substring(4);
    }

    /** ackMode AUTO: one message per exchange; the consumer acks or nacks it when the exchange is done. */
    private void process(Exchange exchange) {
        String delivery = deliveryOf(exchange);
        if (delivery == null) {
            violations.add("an exchange without an ack id");
            return;
        }
        inRoute++;
        try {
            ledger.record("routed", "s", delivery);
            routed(delivery);
            work(delivery);
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

    /** ackMode NONE: the aggregated exchange of a group, acked or nacked by the route with the header. */
    private void processGroup(Exchange exchange) {
        List<?> group = exchange.getMessage().getBody(List.class);
        List<String> deliveries = new ArrayList<>();
        GooglePubsubAcknowledge acknowledge = null;
        for (Object o : group) {
            Exchange e = (Exchange) o;
            deliveries.add(deliveryOf(e));
            if (acknowledge == null) {
                acknowledge = e.getMessage().getHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE,
                        GooglePubsubAcknowledge.class);
            }
        }
        if (acknowledge == null) {
            violations.add("a group without the acknowledge header: " + deliveries);
            return;
        }
        inRoute++;
        try {
            ledger.record("routed", "s", deliveries.toString());
            deliveries.forEach(this::routed);
            work(deliveries.toString());
            if (faults && dst.chance(processFailRate)) {
                failed.addAll(deliveries);
                ledger.record("failed", "s", deliveries.toString());
                acknowledge.nack(exchange);
                return;
            }
            completed.addAll(deliveries);
            ledger.record("completed", "s", deliveries.toString());
            acknowledge.ack(exchange);
            // the ack returned: every message of the group was in it
            for (String d : deliveries) {
                if (!acked.contains(d)) {
                    groupAcks.add("the ack of the group " + deliveries + " did not acknowledge " + d);
                }
            }
        } catch (RuntimeException e) {
            if (!injectedCause(e)) {
                throw e;
            }
            // a failed acknowledgement (injected): the messages are delivered again after their deadline
            ledger.record("ackFailed", "s", deliveries.toString());
        } finally {
            inRoute--;
        }
    }

    private void work(String what) {
        if (faults && dst.chance(0.1)) {
            dst.block(dst.between(pubsub.ackDeadlineSeconds * 500L, pubsub.ackDeadlineSeconds * 2000L),
                    "slow processing of " + what);
        } else {
            dst.block(dst.between(0, 200), "processing of " + what);
        }
    }

    private void onAck(String delivery) {
        acked.add(delivery);
        if (!completed.contains(delivery)) {
            wrongAcks.add(delivery + " acked at t=" + dst.now() + " but its exchange did not complete");
        }
    }

    private void onNack(String delivery) {
        int attempt = Integer.parseInt(delivery.substring(delivery.indexOf('#') + 1));
        boolean atMax = pubsub.maxDeliveryAttempts > 0 && attempt >= pubsub.maxDeliveryAttempts;
        // a delivery that a stopping consumer got from its last pull is rejected by the stopped route, so its exchange
        // fails without being routed, and the consumer nacks it
        boolean rejectedAtStop = !routed.contains(delivery)
                && (pubsub.routeStopped || context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started);
        if (!failed.contains(delivery) && !atMax && !rejectedAtStop) {
            wrongNacks.add(delivery + " nacked at t=" + dst.now() + " but its exchange did not fail");
        }
    }

    private void routed(String delivery) {
        routed.add(delivery);
        int attempt = Integer.parseInt(delivery.substring(delivery.indexOf('#') + 1));
        if (pubsub.maxDeliveryAttempts > 0 && attempt >= pubsub.maxDeliveryAttempts) {
            routedAtMax.add(delivery + " routed at delivery attempt " + attempt);
        }
    }

    private static boolean injectedCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().startsWith("dst: ")) {
                return true;
            }
        }
        return false;
    }

    private GooglePubsubConsumer consumer(DefaultCamelContext context) {
        return context.getRoute(ROUTE) != null && context.getRoute(ROUTE).getConsumer() instanceof GooglePubsubConsumer c
                ? c : null;
    }

    private boolean busy(DefaultCamelContext context) {
        GooglePubsubConsumer c = consumer(context);
        return inRoute > 0 || c != null && c.getPendingExchangesSize() > 0;
    }

    /**
     * Waits for the sends and operations, switches the faults off, waits for the subscription to settle (or a
     * deadline), takes the final state and stops the route.
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
            pubsub.faults = false;
            dst.trace("world: faults cleared");
            pubsub.publish(END);
            long d = dst.now() + 30 * 60_000L;
            operator.schedule("finisher", 500, () -> finisher(context, operator, d));
            return;
        }
        ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
        boolean settled = pubsub.settled();
        boolean endAcked = pubsub.stateOf(END).startsWith("ACKED/");
        if ((busy(context) || !settled || !endAcked || status != ServiceStatus.Started) && dst.now() < deadline) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, deadline));
            return;
        }
        finalStatus = String.valueOf(status);
        finalSettled = settled;
        finalEndAcked = endAcked;
        if (busy(context)) {
            violations.add("an exchange is still in the route at the deadline");
        } else {
            // the pull loop runs for ever: the stop cancels its pull, so that it ends and the run can end
            try {
                context.getRouteController().stopRoute(ROUTE);
                pubsub.routeStopped = true;
            } catch (Exception e) {
                violations.add("final stop failed: " + e);
            }
        }
        finished = true;
    }

    private void startRoute(DefaultCamelContext context, DstExecutor operator) {
        try {
            pubsub.routeStopped = false;
            context.getRouteController().startRoute(ROUTE);
            ledger.record("routeStarted", "r", ROUTE);
        } catch (Exception e) {
            ledger.record("routeStartFailed", "r", ROUTE);
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
                    pubsub.publish(id);
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
            if (grouped || dst.chance(0.5)) {
                operator.schedule("stop route", t, () -> stopWhenIdle(context, operator));
            } else {
                operator.schedule("restart consumer", t, () -> restartConsumerWhenIdle(context, operator));
            }
            t += dst.between(1000, 20_000);
        }
    }

    /**
     * Stops the consumer once it is idle and starts the same consumer again at once, in the same task, as a throttling
     * route policy does (RoutePolicySupport.stopConsumer and startConsumer): the pull loop of the previous start sees
     * its cancelled pull only after the start, and must end then instead of pulling next to the loop of the new start.
     * Only with ackMode AUTO: with GROUPED the loop of the new start could run nested above the processing of a group
     * that the aggregator completes later, and on the one stack it would never let that processing resume.
     */
    private void restartConsumerWhenIdle(DefaultCamelContext context, DstExecutor operator) {
        GooglePubsubConsumer c = consumer(context);
        if (c == null || context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
            opsLeft--;
            return;
        }
        if (busy(context)) {
            operator.schedule("restart consumer (busy)", 50, () -> restartConsumerWhenIdle(context, operator));
            return;
        }
        try {
            ServiceHelper.stopService(c);
            ledger.record("consumerStopped", "r", ROUTE);
            ServiceHelper.startService(c);
            ledger.record("consumerStarted", "r", ROUTE);
        } catch (Exception e) {
            violations.add("consumer restart failed: " + e);
        }
        opsLeft--;
    }

    /**
     * Stops the route once no exchange is in it and the consumer has no pending exchange (a stop nested above one on
     * the one stack would wait for it for ever), then starts it again after a while: the pull loop of the stopped
     * consumer ends as soon as the stop returned, when its cancelled pull answers.
     */
    private void stopWhenIdle(DefaultCamelContext context, DstExecutor operator) {
        if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
            opsLeft--;
            return;
        }
        if (busy(context)) {
            operator.schedule("stop route (busy)", 50, () -> stopWhenIdle(context, operator));
            return;
        }
        try {
            context.getRouteController().stopRoute(ROUTE);
            pubsub.routeStopped = true;
            ledger.record("routeStopped", "r", ROUTE);
        } catch (Exception e) {
            violations.add("stop failed: " + e);
        }
        operator.schedule("start route", dst.between(100, 5000), () -> {
            startRoute(context, operator);
            opsLeft--;
        });
    }
}
