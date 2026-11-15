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
package org.apache.camel.component.paho.mqtt5.dst;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Configuration;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5DstComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;

/**
 * The world around the camel-paho-mqtt5 consumer: a fake MQTT 5 broker and the Paho client of the consumer
 * ({@link FakeMqtt}), the client given to the real consumer through {@link PahoMqtt5DstComponent} (the consumer creates
 * its own client in a package-private method), a publisher, a route whose processing is sometimes slow and sometimes
 * fails, and an operator that drops the connection of the client, makes the broker unreachable for a while, and stops
 * and starts the route (only while no message is in the route).
 * <p>
 * The seed picks the QoS, manualAcksEnabled, cleanStart, sessionExpiryInterval, automaticReconnect, receiveMaximum,
 * maxReconnectDelay and the fault rates.
 * <p>
 * Properties: with manualAcksEnabled a QoS 1 message is acknowledged only after its exchange completed, or after it
 * failed when the broker would not send it again; the client drops no message (a message that arrives before the
 * callback is set); once the faults cleared every message that the broker queued for the session (and did not lose with
 * a session that ended) is routed, a QoS 1 message with manual acks on a kept session completes, and nothing stays in
 * flight or queued; after the final stop the client is disconnected and closed; the consumer uses the client as Paho
 * allows.
 * <p>
 * The operator stops the route only while no message is in the route, and a message that arrives while a stop runs (the
 * operator's, or the restart of the consumer after a failed subscribe) is processed at once: on the one stack the
 * shutdown could not see an exchange that blocks below it finish. For the same reason a subscribe fails only while no
 * message is in the route. Without automaticReconnect Paho does not reconnect after the broker closed the connection,
 * so the operator restarts the route then. A message delivered while the route stops is refused by the route; without
 * manual acknowledgements, or when the broker does not keep the session, its acknowledgement leaves it lost (the
 * consumer's Javadoc of rollback describes it), and the world does not count it.
 * <p>
 * Two cases are looked at separately. A connect that comes while the callback thread of Paho is still in messageArrived
 * after the connection closed (the reconnect of the consumer after a failed message, or the automatic reconnect, while
 * another message is in the route) never completes, as read in the byte code of Paho 1.2.5 (not reproduced against a
 * broker yet, and the fake leaves out the quiesce of a disconnect, which changes when the thread is busy): the client
 * stays connecting, the consumer delivers nothing more, and the stop cannot close the client. When the client of the
 * consumer hangs that way at the end of the run, the world does not check that every message is routed, and a hung
 * client is not checked for being closed; {@code -Ddst.paho.strictCallbackThread=true} checks both. A stop while the
 * client connects makes the close of the client fail (Paho throws 32110), and the client goes on connecting: the
 * operator does not stop the route then, and {@code -Ddst.paho.stopWhileConnecting=true} lets it.
 * <p>
 * Not explored: a client given with the client option (shared by several consumers), the persistence of the client,
 * retained messages and wildcards, an acknowledgement that blocks, and a stop while a message is in the route.
 */
@DstWorld(component = "paho-mqtt5",
          fake = "FakeMqtt: an MQTT 5 broker with the session of the client id (Clean Start, Session Expiry, Receive"
                 + " Maximum, resend on reconnect) and a Paho MqttClient subclass on it, given to the consumer by a"
                 + " test component",
          covers = {
                  "QoS 0, 1 and 2 with automatic or manual acknowledgements (manualAcksEnabled)",
                  "a session that the broker keeps (cleanStart=false, sessionExpiryInterval) or not",
                  "the automatic reconnect of Paho and the subscription made again",
                  "the reconnect of the consumer after a failed message, and Receive Maximum",
                  "route stop and start, with messages queued for a kept session" },
          faults = {
                  "the broker closes the connection", "the broker cannot be reached",
                  "the connection is lost during a subscribe",
                  "the broker answers slowly", "processing fails", "processing is slow" },
          invariants = {
                  "the consumer uses the client as Paho allows",
                  "a message is acknowledged only after its exchange completed, or failed for good",
                  "the client drops no message",
                  "every message is routed once the faults cleared",
                  "the stopped consumer disconnects and closes its client" },
          issues = { "CAMEL-25793", "CAMEL-25944" })
final class PahoMqtt5World {

    static final String ROUTE = "mqtt";

    private final Dst dst;
    private final FakeMqtt mqtt;
    private final int qos;
    private final boolean manualAcks;
    private final boolean cleanStart;
    private final long sessionExpiry;
    private final boolean automaticReconnect;
    private final int receiveMaximum;
    private final int maxReconnectDelay;
    private final double processFailRate;
    private final List<String> violations = new ArrayList<>();
    private final Set<Long> published = new TreeSet<>();
    private final Set<Long> routed = new HashSet<>();
    private final Set<Long> completed = new HashSet<>();
    private final Set<Long> failed = new HashSet<>();
    private final Set<Long> delivered = new HashSet<>();
    private final Set<Long> deliveredWhileStopping = new HashSet<>();
    private DefaultCamelContext context;
    private boolean faults = true;
    private int sendsLeft;
    private int opsLeft;
    private int inRoute;
    private long nextMessage;
    private boolean finished;
    // whether the finisher made its check at the deadline
    private boolean checked;
    private boolean stopping;
    private boolean restartByConsumer;
    private final boolean stopWhileConnecting = Boolean.getBoolean("dst.paho.stopWhileConnecting");
    private final boolean strictCallbackThread = Boolean.getBoolean("dst.paho.strictCallbackThread");
    private String finalCheck;
    private String afterStop;

    PahoMqtt5World(Dst dst) {
        this.dst = dst;
        this.mqtt = new FakeMqtt(dst);
        this.qos = dst.pick(List.of(0, 1, 1, 1, 2));
        this.manualAcks = dst.chance(0.6);
        this.cleanStart = dst.chance(0.4);
        this.sessionExpiry = dst.pick(List.of(-1L, 60L, 3600L));
        this.automaticReconnect = dst.chance(0.8);
        this.receiveMaximum = dst.pick(List.of(1, 2, 10, 65535));
        this.maxReconnectDelay = dst.pick(List.of(2000, 128000));
        this.processFailRate = dst.pick(List.of(0.0, 0.1, 0.3));
        mqtt.subscribeFailRate = dst.pick(List.of(0.0, 0.1));
        mqtt.slowRate = dst.pick(List.of(0.0, 0.05));
        mqtt.queueQos0 = dst.chance(0.5);
        mqtt.onAck = this::acknowledged;
        mqtt.onDelivered = this::delivered;
        // a subscribe that fails when the client reconnected makes the consumer restart its route; on the one stack that
        // shutdown could not see an exchange that blocks below it finish, so a subscribe fails only while no message is
        // in the route
        mqtt.subscribeFaultAllowed = () -> inRoute == 0 && !stopping;
        // after a failed subscribe the consumer restarts its route (a reconnect) or fails to start: until a subscribe
        // succeeds again the world treats the route as stopping
        mqtt.onSubscribeFailed = () -> restartByConsumer = true;
        mqtt.onSubscribed = () -> restartByConsumer = false;
        mqtt.onDropped = n -> violations.add("the client dropped message " + n + " at t=" + dst.now());
        dst.trace("world: " + config());
    }

    private String config() {
        return "qos=" + qos + " manualAcksEnabled=" + manualAcks + " cleanStart=" + cleanStart + " sessionExpiryInterval="
               + sessionExpiry + " automaticReconnect=" + automaticReconnect + " receiveMaximum=" + receiveMaximum
               + " maxReconnectDelay=" + maxReconnectDelay + " processFail=" + processFailRate + " subscribeFail="
               + mqtt.subscribeFailRate + " slow=" + mqtt.slowRate + " queueQos0=" + mqtt.queueQos0;
    }

    /** Whether the broker keeps the session after the connection closes, so that it sends a QoS 1 message again. */
    private boolean sessionKept() {
        return !cleanStart && sessionExpiry > 0;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        this.context = context;
        PahoMqtt5DstComponent component = new PahoMqtt5DstComponent(context, mqtt::newClient);
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setBrokerUrl(FakeMqtt.URI);
        configuration.setClientId("dst-consumer");
        configuration.setQos(qos);
        configuration.setManualAcksEnabled(manualAcks);
        configuration.setCleanStart(cleanStart);
        configuration.setSessionExpiryInterval(sessionExpiry);
        configuration.setAutomaticReconnect(automaticReconnect);
        configuration.setReceiveMaximum(receiveMaximum);
        configuration.setMaxReconnectDelay(maxReconnectDelay);
        component.setConfiguration(configuration);
        context.addComponent("paho-mqtt5", component);
        String uri = "paho-mqtt5:" + FakeMqtt.TOPIC;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId(ROUTE).autoStartup(false).process(exchange -> process(exchange));
            }
        });
        context.start();
        DstExecutor operator = dst.executor("operator", 1);
        try {
            operator.schedule("start route", 0, () -> startRoute(context));
            planSends();
            planOperations(context, operator);
            // the finisher runs on its own executor, so that it never waits behind an operation of the operator
            DstExecutor finisher = dst.executor("finisher", 1);
            finisher.schedule("finisher", 1000, () -> finisher(context, finisher, -1));
            dst.runUntil(() -> finished, "the finisher");
            dst.check(
                    Invariants.of("the consumer uses the client as Paho allows",
                            () -> mqtt.violations.isEmpty() ? null : mqtt.violations + " (" + config() + ")"),
                    Invariants.of("a message is acknowledged only after its exchange completed, or failed for good",
                            () -> violations.stream().noneMatch(v -> v.startsWith("acknowledged"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("the client drops no message",
                            () -> violations.stream().noneMatch(v -> v.startsWith("the client dropped"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("every message is routed once the faults cleared",
                            () -> finalCheck == null ? null : finalCheck + " (" + config() + ")"),
                    Invariants.of("the stopped consumer disconnects and closes its client",
                            () -> afterStop == null ? null : afterStop + " (" + config() + ")"),
                    Invariants.of("the world itself ran as planned",
                            () -> violations.stream().noneMatch(v -> v.startsWith("world"))
                                    ? null : violations + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private void process(Exchange exchange) {
        String body = exchange.getMessage().getBody(String.class);
        long n = Long.parseLong(body.substring(1));
        inRoute++;
        try {
            routed.add(n);
            if (stopping || restartByConsumer) {
                // the route is being stopped: on the one stack the shutdown could not see an exchange that blocks below
                // it finish, so an exchange that starts now takes no time
                dst.trace("world: message " + n + " processed at once, the route is stopping");
            } else if (faults && dst.chance(0.05)) {
                dst.block(dst.between(1000, 10_000), "slow processing of " + n);
            } else {
                dst.block(dst.between(0, 100), "processing of " + n);
            }
            if (faults && dst.chance(processFailRate)) {
                failed.add(n);
                dst.trace("world: message " + n + " failed");
                throw Dst.injected(new IllegalStateException("dst: processing failed for message " + n));
            }
            completed.add(n);
            dst.trace("world: message " + n + " completed");
        } finally {
            inRoute--;
        }
    }

    private void delivered(long n) {
        delivered.add(n);
        if (stopping || restartByConsumer
                || context != null && context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
            // the route stops: the exchange of this message fails, and without manual acks (or on a session the broker
            // does not keep) its acknowledgement leaves it lost
            deliveredWhileStopping.add(n);
        }
    }

    private void acknowledged(long n) {
        if (!manualAcks || qos != 1) {
            return;
        }
        if (completed.contains(n)) {
            return;
        }
        if (!sessionKept() && (failed.contains(n) || deliveredWhileStopping.contains(n) && !routed.contains(n))) {
            // the exchange failed (or was refused by a route that stops), and the broker would never send the message
            // again: the consumer acknowledges it
            return;
        }
        violations.add("acknowledged message " + n + " before its exchange completed (failed=" + failed.contains(n)
                       + ") at t=" + dst.now());
    }

    // ------------------------------------------------------------------------------------------------------------
    // the load and the operator

    private void planSends() {
        DstExecutor publisher = dst.executor("publisher", 1);
        long t = dst.between(200, 1000);
        int bursts = (int) dst.between(1, 4);
        for (int b = 0; b < bursts; b++) {
            int size = (int) dst.between(1, 15);
            for (int i = 0; i < size; i++) {
                long n = nextMessage++;
                sendsLeft++;
                publisher.schedule("publish " + n, t, () -> {
                    if (mqtt.isSubscribed()) {
                        published.add(n);
                    }
                    mqtt.publish(n, qos);
                    sendsLeft--;
                });
                t += dst.between(0, 300);
            }
            t += dst.between(0, 20_000);
        }
    }

    private void planOperations(DefaultCamelContext context, DstExecutor operator) {
        int ops = (int) dst.between(0, 4);
        long t = dst.between(1000, 5000);
        for (int i = 0; i < ops; i++) {
            opsLeft++;
            int kind = (int) dst.between(0, 3);
            if (kind == 0) {
                operator.schedule("drop connection", t, () -> {
                    mqtt.dropConnection();
                    opsLeft--;
                });
            } else if (kind == 1) {
                long length = dst.between(500, 20_000);
                operator.schedule("broker unreachable", t, () -> {
                    mqtt.unreachable(length);
                    opsLeft--;
                });
            } else {
                operator.schedule("stop route", t, () -> stopWhenIdle(context, operator));
            }
            t += dst.between(1000, 20_000);
        }
    }

    private void startRoute(DefaultCamelContext context) {
        try {
            context.getRouteController().startRoute(ROUTE);
        } catch (Exception e) {
            // the broker cannot be reached: the route stays stopped, and the operator starts it again later
            dst.trace("world: route start failed: " + e.getMessage());
            DstExecutor operator = dst.executor("operator", 1);
            operator.schedule("start route again", 2000, () -> startRoute(context));
        }
    }

    /** Stops the route once no message is in it, then starts it again after a while. */
    private void stopWhenIdle(DefaultCamelContext context, DstExecutor operator) {
        if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
            opsLeft--;
            return;
        }
        if (mqtt.anyHanging()) {
            // a hung connect keeps the message below it on the one stack in the route until the end of the run: no stop
            dst.trace("world: no route stop, as a connect hangs");
            opsLeft--;
            return;
        }
        if (inRoute > 0 || !stopWhileConnecting && mqtt.anyConnecting()) {
            operator.schedule("stop route (later)", 50, () -> stopWhenIdle(context, operator));
            return;
        }
        stopRoute(context);
        operator.schedule("start route", dst.between(100, 10_000), () -> {
            startRoute(context);
            opsLeft--;
        });
    }

    private void restart(DefaultCamelContext context) {
        dst.trace("world: restart the route, as the client is disconnected and does not reconnect");
        stopRoute(context);
        startRoute(context);
    }

    private void stopRoute(DefaultCamelContext context) {
        stopping = true;
        try {
            context.getRouteController().stopRoute(ROUTE);
        } catch (Exception e) {
            violations.add("world: stop failed: " + e);
        } finally {
            stopping = false;
        }
    }

    private Set<Long> missing() {
        Set<Long> missing = new TreeSet<>();
        for (long n : published) {
            if (mqtt.lostWithSession.contains(n) || mqtt.lostInClient.contains(n)) {
                continue;
            }
            if (deliveredWhileStopping.contains(n) && !routed.contains(n) && (qos != 1 || !manualAcks || !sessionKept())) {
                // delivered while the route stopped, refused and acknowledged (see delivered)
                continue;
            }
            if (qos == 0 && !delivered.contains(n)) {
                // at most once: a QoS 0 message that the client never got is lost
                continue;
            }
            boolean mustComplete = qos == 1 && manualAcks && sessionKept();
            if (mustComplete ? !completed.contains(n) : !routed.contains(n)) {
                missing.add(n);
            }
        }
        return missing;
    }

    private void finisher(DefaultCamelContext context, DstExecutor self, long deadline) {
        if (deadline == -1 && (sendsLeft > 0 || opsLeft > 0)) {
            self.schedule("finisher", 500, () -> finisher(context, self, -1));
            return;
        }
        if (deadline == -1) {
            faults = false;
            mqtt.faults = false;
            dst.trace("world: faults cleared");
            long d = dst.now() + 10 * 60_000L;
            self.schedule("finisher", 500, () -> finisher(context, self, d));
            return;
        }
        ServiceStatus status = context.getRouteController().getRouteStatus(ROUTE);
        Set<Long> missing = missing();
        boolean settled = status == ServiceStatus.Started && mqtt.isConnected() && missing.isEmpty() && inRoute == 0
                && (qos == 0 || mqtt.pending() == 0);
        if (!settled && dst.now() < deadline) {
            if (status == ServiceStatus.Stopped && dst.now() > deadline - 9 * 60_000L) {
                // a route that stopped on its own (a failed start) is started again
                startRoute(context);
            } else if (status == ServiceStatus.Started && !automaticReconnect && !mqtt.isConnected() && inRoute == 0
                    && !mqtt.anyConnecting()) {
                // without automaticReconnect Paho does not reconnect after the broker closed the
                // connection: the operator restarts the route
                restart(context);
            }
            self.schedule("finisher", 500, () -> finisher(context, self, deadline));
            return;
        }
        boolean excuseHung = !strictCallbackThread;
        if (!checked) {
            checked = true;
            if (!settled && excuseHung && mqtt.currentHung()) {
                // the client hangs in a connect (see the class Javadoc): counted, its messages are not checked
                dst.trace("world: the client hangs in a connect (" + mqtt.hungConnects
                          + "); the messages are not checked");
            } else if (!settled) {
                finalCheck = "at the deadline: route " + status + ", connected=" + mqtt.isConnected() + ", subscribed="
                             + mqtt.isSubscribed() + ", never routed (or not completed) " + missing
                             + ", in flight or queued at the broker " + mqtt.pending();
            }
            // the hung connects end here (they fail), so that the messages below them on the one stack can complete
            // and the run can end; Paho would wait for ever
            mqtt.releaseHung = true;
        }
        if (inRoute > 0 || !stopWhileConnecting && mqtt.anyConnecting()) {
            self.schedule("finisher (later)", 50, () -> finisher(context, self, deadline));
            return;
        }
        stopRoute(context);
        List<String> open = new ArrayList<>();
        for (FakeMqtt.Client c : mqtt.clients()) {
            if (excuseHung && c.isHung()) {
                // a hung client cannot be closed (32110), see the class Javadoc
                continue;
            }
            if (c.isConnected()) {
                open.add("a client is still connected");
            } else if (!c.isClosed()) {
                open.add("a client is not closed");
            }
        }
        if (mqtt.isConnected()) {
            open.add("the broker still has a connection");
        }
        if (!open.isEmpty()) {
            afterStop = String.join(", ", open);
        }
        finished = true;
    }
}
