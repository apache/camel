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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.chaos.ChaosRule.Action;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RouteController;
import org.apache.camel.util.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The route lifecycle faults ({@code stall} and {@code lifecycle}): each rule acts once on its route, and brings the
 * route back after its duration.
 * <p/>
 * The action and the comeback are tasks of one scheduled executor of the {@code ExecutorServiceManager}, created when
 * the first lifecycle rule acts and shut down when the context stops, so they run one at a time, in the order they were
 * scheduled, and in a {@code camel-test-dst} world on the loop of the run, in virtual time. The route is suspended,
 * stopped, started and resumed through the route controller of the context (also the supervising one), and removed with
 * {@code CamelContext.removeRoute}; a removed route is added back with its route definition. Chaos only brings back
 * what it took down: a route that is not started when the rule acts is left alone (unless another lifecycle rule has it
 * down and brings it back now: then the rule acts after that comeback), and at the comeback a route that someone else
 * changed meanwhile (its status, or a new route with the same id) is left as it is. None of this runs under a lock: a
 * route operation can take as long as the shutdown strategy waits for the exchanges in flight.
 * <p/>
 * Lock ordering: a {@link Fault}'s lock is taken first and this class's lock inside it, never the other way round.
 */
final class ChaosLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosLifecycle.class);

    private final CamelContext camelContext;

    // guarded by this: whether the context has started (rules act only then), the executor, the faults in progress
    private boolean started;
    private ScheduledExecutorService executor;
    private final Set<Fault> active = new LinkedHashSet<>();
    // the fault that has each route down (from its action until it is done)
    private final Map<String, Fault> holders = new HashMap<>();

    ChaosLifecycle(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /** The context has started: the lifecycle rules that wait for it act now. */
    synchronized void contextStarted() {
        started = true;
    }

    /**
     * Schedules the action of a lifecycle rule, once, when the context has started (else it waits for
     * {@link #contextStarted()}, after which the manager calls this again).
     */
    void schedule(ChaosRule rule) {
        Fault fault = rule.lifecycle;
        synchronized (fault) {
            if (fault.state != FaultState.PENDING || fault.task != null) {
                // acted, or scheduled, already: no executor is created for it
                return;
            }
        }
        ScheduledExecutorService ex;
        synchronized (this) {
            if (!started) {
                return;
            }
            ex = executor();
        }
        synchronized (fault) {
            if (fault.state != FaultState.PENDING || fault.task != null) {
                return;
            }
            fault.owner = this;
            fault.scheduleAct(ex);
        }
    }

    /**
     * The context stops: a rule that did not act yet does not act, and a route that is down is not brought back (the
     * report says so). The executor is shut down, so no task of chaos is left behind.
     */
    void contextStopping() {
        ScheduledExecutorService ex;
        List<Fault> faults;
        synchronized (this) {
            started = false;
            ex = executor;
            executor = null;
            faults = new ArrayList<>(active);
            active.clear();
            holders.clear();
        }
        for (Fault fault : faults) {
            fault.contextStopping();
        }
        if (ex != null) {
            camelContext.getExecutorServiceManager().shutdownNow(ex);
        }
    }

    /** The executor, created on first use. Called with the lock held. */
    private ScheduledExecutorService executor() {
        if (executor == null) {
            executor = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "Chaos");
        }
        return executor;
    }

    /** Whether the CamelContext is stopping (or has stopped). */
    private boolean isContextStopping() {
        return camelContext.isStopping() || camelContext.isStopped();
    }

    /** The executor for a comeback, or null when the context stopped. */
    private synchronized ScheduledExecutorService executorIfStarted() {
        return started ? executor() : null;
    }

    private synchronized void track(Fault fault, boolean add) {
        if (add) {
            active.add(fault);
        } else {
            active.remove(fault);
            holders.remove(fault.rule.pattern, fault);
        }
    }

    private synchronized void hold(Fault fault) {
        holders.put(fault.rule.pattern, fault);
    }

    private synchronized Fault holder(String routeId) {
        return holders.get(routeId);
    }

    /** Whether a lifecycle rule has the route of this id down (so a rule for it is valid while it is removed). */
    synchronized boolean isHeld(String routeId) {
        return holders.containsKey(routeId);
    }

    // for the tests
    synchronized boolean hasExecutor() {
        return executor != null;
    }

    synchronized int activeCount() {
        return active.size();
    }

    /**
     * Whether the context has a route of this id: a route, or a route definition of the model whose route is not
     * created (yet).
     */
    static boolean routeExists(CamelContext camelContext, String routeId) {
        if (camelContext.getRoute(routeId) != null) {
            return true;
        }
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        return model != null && model.getRouteDefinition(routeId) != null;
    }

    /** Why a route of this id cannot be removed and added back by chaos, or null when it can. */
    static String notRemovable(CamelContext camelContext, String routeId) {
        Route route = camelContext.getRoute(routeId);
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        RouteDefinition definition = null;
        if (model != null) {
            definition = route != null
                    ? (route.getRoute() instanceof RouteDefinition rd ? rd : null) : model.getRouteDefinition(routeId);
        }
        if (definition == null) {
            return "route " + routeId + " has no route definition, so chaos could not add it back";
        }
        if (definition.isCreatedFromTemplate() || route != null && (route.isCreatedByRouteTemplate()
                || route.isCreatedByKamelet())) {
            // removing the route closes the local beans of its template, which the definition still refers to
            return "route " + routeId + " is created from a route template or a kamelet, whose local beans go with the"
                   + " route, so chaos could not add it back; use action=stop or stall";
        }
        return null;
    }

    /** Takes the route of the rule down: runs on the executor. */
    private void act(Fault fault) {
        ChaosRule rule = fault.rule;
        synchronized (fault) {
            if (fault.state != FaultState.PENDING) {
                return;
            }
            fault.state = FaultState.ACTING;
        }
        String routeId = rule.pattern;
        Action action = rule.action;
        RouteController controller = camelContext.getRouteController();
        Route route = camelContext.getRoute(routeId);
        ServiceStatus status = route != null ? controller.getRouteStatus(routeId) : null;
        String skip = null;
        NamedNode definition = null;
        Exception failure = null;
        Fault holder = route == null || status != ServiceStatus.Started ? holder(routeId) : null;
        if (holder != null && holder != fault && holder.isEnding()) {
            // another rule took the route down and brings it back now (it was removed or changed): act after that
            synchronized (fault) {
                if (fault.endRequested) {
                    // removed or changed again meanwhile
                    fault.finish(FaultState.CANCELLED, null, "the rule was removed before it acted");
                    return;
                }
                fault.state = FaultState.PENDING;
                ScheduledExecutorService ex = executorIfStarted();
                if (ex != null) {
                    fault.scheduleAct(ex);
                } else {
                    fault.finish(FaultState.CANCELLED, null, "the context stopped before it acted");
                }
            }
            return;
        }
        if (route != null) {
            rule.matched();
        }
        if (route == null) {
            skip = "there is no route " + routeId
                   + (holder != null && holder != fault && holder.rule.action == Action.REMOVE
                           ? " (chaos rule " + holder.rule.id + " removed it)" : "");
        } else if (status != ServiceStatus.Started) {
            // chaos only brings back what it took down, so it leaves a route that someone stopped or suspended
            skip = "route " + routeId + " was " + name(status)
                   + (holder != null && holder != fault && holder.rule.action != Action.REMOVE
                           ? " by chaos rule " + holder.rule.id : "");
        } else {
            try {
                switch (action) {
                    case SUSPEND -> controller.suspendRoute(routeId);
                    case STOP -> controller.stopRoute(routeId);
                    case REMOVE -> {
                        skip = notRemovable(camelContext, routeId);
                        if (skip == null) {
                            definition = route.getRoute();
                            controller.stopRoute(routeId);
                            if (!camelContext.removeRoute(routeId)) {
                                failure = new IllegalStateException(
                                        "route " + routeId + " was " + name(controller.getRouteStatus(routeId))
                                                                    + " after it was stopped, so it was not removed");
                            }
                        }
                    }
                    default -> throw new IllegalStateException("Unknown action " + action);
                }
            } catch (Exception e) {
                failure = e;
            }
        }
        long now = TimeSource.currentTimeMillis();
        Action comebackAs = action;
        ServiceStatus left = route != null && failure != null ? controller.getRouteStatus(routeId) : null;
        String failed = failure != null ? "could not " + action.key + " route " + routeId + ": " + failure : null;
        if (left == ServiceStatus.Stopped || left == ServiceStatus.Suspended) {
            // the action failed half way (a remove after the stop, a stop that fell back from a suspend): chaos took
            // the route down, so it brings it back as it is now
            comebackAs = left == ServiceStatus.Suspended ? Action.SUSPEND : Action.STOP;
            failed += "; route " + routeId + " was left " + name(left) + ", so chaos " + comebackAs.comeback
                      + "s it after the duration";
            failure = null;
        }
        synchronized (fault) {
            if (skip != null) {
                rule.skipped();
                LOG.info("Chaos rule {} did nothing: {}", rule.id, skip);
                fault.finish(FaultState.SKIPPED, null, skip);
                return;
            }
            if (failure != null) {
                LOG.warn("Chaos rule {}: {}", rule.id, failed);
                fault.finish(FaultState.FAILED, null, failed);
                return;
            }
            if (failed != null) {
                LOG.warn("Chaos rule {}: {}", rule.id, failed);
                fault.reason = failed;
            }
            fault.state = FaultState.DOWN;
            fault.downAt = now;
            fault.route = route;
            fault.definition = definition;
            fault.comebackAs = comebackAs;
            fault.statusAfter = comebackAs == Action.REMOVE ? null : controller.getRouteStatus(routeId);
            hold(fault);
            LOG.info("Chaos rule {} {} route {} for {}", rule.id, comebackAs.done, routeId,
                    ChaosRuleParser.printTime(rule.duration));
            if (fault.endRequested) {
                // the rule was removed while it acted
                fault.endedEarly = true;
            }
            fault.scheduleComeback(fault.endedEarly ? 0 : rule.duration);
        }
    }

    /** Brings the route of the rule back: runs on the executor. */
    private void comeback(Fault fault) {
        ChaosRule rule = fault.rule;
        synchronized (fault) {
            if (fault.state != FaultState.DOWN) {
                return;
            }
            fault.state = FaultState.COMING_BACK;
        }
        String routeId = rule.pattern;
        Action action = fault.comebackAs;
        RouteController controller = camelContext.getRouteController();
        Route current = camelContext.getRoute(routeId);
        String left = null;
        String note = fault.reason;
        Exception failure = null;
        try {
            if (action == Action.REMOVE) {
                if (current != null) {
                    left = "a route " + routeId + " was added meanwhile";
                } else {
                    Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
                    // the definition that was removed, as it was prepared (with the route configurations it had)
                    model.addRouteDefinition((RouteDefinition) fault.definition);
                    if (controller.getRouteStatus(routeId) == ServiceStatus.Stopped && camelContext.isStarted()) {
                        // the supervising route controller does not start a route that is added again by itself
                        controller.startRoute(routeId);
                    }
                    ServiceStatus status = controller.getRouteStatus(routeId);
                    if (status != ServiceStatus.Started) {
                        note = "route " + routeId + " was added back, and is " + name(status);
                    }
                }
            } else if (current != fault.route) {
                left = current == null
                        ? "route " + routeId + " was removed meanwhile" : "route " + routeId + " was replaced meanwhile";
            } else {
                ServiceStatus status = controller.getRouteStatus(routeId);
                if (status != fault.statusAfter) {
                    left = "route " + routeId + " is " + name(status) + " now";
                } else if (action == Action.SUSPEND) {
                    controller.resumeRoute(routeId);
                } else {
                    controller.startRoute(routeId);
                }
            }
        } catch (Exception e) {
            failure = e;
        }
        long now = TimeSource.currentTimeMillis();
        synchronized (fault) {
            if (left != null) {
                LOG.info("Chaos rule {} did not {} route {}: {}, so chaos left it", rule.id, action.comeback, routeId, left);
                fault.finish(FaultState.LEFT, now, left + ", so chaos left it");
            } else if (failure != null) {
                LOG.warn("Chaos rule {} could not {} route {}: {}", rule.id, action.comeback, routeId, failure.toString());
                fault.finish(FaultState.FAILED, now,
                        "could not " + action.comeback + " route " + routeId + ": " + failure);
            } else {
                LOG.info("Chaos rule {} {} route {}", rule.id, action.back, routeId);
                fault.finish(FaultState.BACK, now, note);
            }
        }
    }

    private static String name(ServiceStatus status) {
        return status == null ? "gone" : status.name().toLowerCase(Locale.ENGLISH);
    }

    /** The states of a lifecycle fault. */
    enum FaultState {
        // waiting to act (for the context to start, or for the executor)
        PENDING("pending"),
        ACTING("acting"),
        // the route is down, and comes back after the duration
        DOWN("down"),
        COMING_BACK("coming back"),
        // done: brought back, did nothing, left as someone else changed it, failed, or cancelled by a stop
        BACK("back"),
        SKIPPED("skipped"),
        LEFT("left"),
        FAILED("failed"),
        CANCELLED("cancelled");

        final String key;

        FaultState(String key) {
            this.key = key;
        }
    }

    /**
     * What a lifecycle rule did to its route: its state, the times (read through {@code TimeSource}) and why it did not
     * go as planned. Guarded by itself; it takes the lock of its {@link ChaosLifecycle} inside its own, never the other
     * way round.
     */
    static final class Fault {

        final ChaosRule rule;
        private ChaosLifecycle owner;
        private FaultState state = FaultState.PENDING;
        private Future<?> task;
        private boolean endRequested;
        private boolean endedEarly;
        private long downAt = -1;
        private long backAt = -1;
        private String reason;
        private Route route;
        private NamedNode definition;
        private ServiceStatus statusAfter;
        // how the route comes back: the action of the rule, or how a half failed action left the route
        private Action comebackAs;

        Fault(ChaosRule rule) {
            this.rule = rule;
        }

        /** Whether the fault brings its route back now (the rule was removed, or the comeback runs). */
        synchronized boolean isEnding() {
            return endRequested || endedEarly || state == FaultState.COMING_BACK;
        }

        /** How the route comes back, in words (such as "resumed"). Called with the lock held. */
        private String backWord() {
            return (comebackAs != null ? comebackAs : rule.action).back;
        }

        /** Schedules the action. Called with the lock held, in state PENDING. */
        private void scheduleAct(ScheduledExecutorService ex) {
            ChaosLifecycle lifecycle = owner;
            try {
                task = ex.schedule(() -> lifecycle.act(this), 0, TimeUnit.MILLISECONDS);
                lifecycle.track(this, true);
            } catch (RejectedExecutionException e) {
                finish(FaultState.CANCELLED, null, "the context stopped before it acted");
            }
        }

        synchronized boolean isPending() {
            return state == FaultState.PENDING;
        }

        synchronized boolean isDone() {
            return switch (state) {
                case BACK, SKIPPED, LEFT, FAILED, CANCELLED -> true;
                default -> false;
            };
        }

        /** The rule was removed or replaced: the fault ends now, and a route that is down comes back now. */
        synchronized void ruleRemoved() {
            switch (state) {
                case PENDING -> {
                    if (task != null) {
                        task.cancel(false);
                    }
                    finish(FaultState.CANCELLED, null, "the rule was removed before it acted");
                }
                case ACTING -> endRequested = true;
                case DOWN -> {
                    if (!endedEarly && task != null && task.cancel(false)) {
                        endedEarly = true;
                        scheduleComeback(0);
                    }
                }
                default -> {
                    // coming back already, or done
                }
            }
        }

        private synchronized void contextStopping() {
            if (task != null) {
                task.cancel(false);
            }
            switch (state) {
                case PENDING -> finish(FaultState.CANCELLED, null, "the context stopped before it acted");
                case DOWN -> finish(FaultState.CANCELLED, null,
                        "the context stopped, so route " + rule.pattern + " was not " + backWord());
                default -> {
                    // acting or coming back: the shutdown of the executor interrupts the task, but a route operation
                    // in act() can still finish first (it waits for the exchanges in flight); scheduleComeback then
                    // finds the context stopping and cancels the fault. A comeback in progress ends the fault itself
                }
            }
        }

        /** Schedules the comeback. Called with the lock held, in state DOWN. */
        private void scheduleComeback(long delay) {
            if (owner != null && owner.isContextStopping()) {
                // the route is not brought back while the context stops, such as after an action that was in
                // progress when the context began to stop
                LOG.info("Chaos rule {} did not bring route {} back: the CamelContext is stopping", rule.id, rule.pattern);
                finish(FaultState.CANCELLED, null,
                        "the CamelContext is stopping, so route " + rule.pattern + " was not " + backWord());
                return;
            }
            ScheduledExecutorService ex = owner != null ? owner.executorIfStarted() : null;
            if (ex == null) {
                finish(FaultState.CANCELLED, null,
                        "the context stopped, so route " + rule.pattern + " was not " + backWord());
                return;
            }
            try {
                ChaosLifecycle lifecycle = owner;
                task = ex.schedule(() -> lifecycle.comeback(this), delay, TimeUnit.MILLISECONDS);
                // tracked again, for a comeback scheduled after a stop and a start of the context
                lifecycle.track(this, true);
            } catch (RejectedExecutionException e) {
                finish(FaultState.CANCELLED, null,
                        "the context stopped, so route " + rule.pattern + " was not " + backWord());
            }
        }

        /** Ends the fault. Called with the lock held. */
        private void finish(FaultState end, Long at, String why) {
            state = end;
            if (at != null) {
                backAt = at;
            }
            reason = why;
            task = null;
            route = null;
            definition = null;
            if (owner != null) {
                owner.track(this, false);
            }
        }

        /** What the report reads of the fault, at one time. */
        synchronized Reading read(long now) {
            long down = downAt >= 0 ? downAt - rule.addedAt : -1;
            long back = backAt >= 0 ? backAt - rule.addedAt : -1;
            long backIn = state == FaultState.DOWN && !endedEarly && downAt >= 0
                    ? Math.max(0, downAt + rule.duration - now) : -1;
            boolean acted = downAt >= 0;
            return new Reading(
                    state, comebackAs != null ? comebackAs : rule.action, acted, down, back, backIn, endedEarly,
                    reason);
        }

        /** What the report reads of a lifecycle fault; the times are milliseconds after the rule was added, or -1. */
        record Reading(
                FaultState state, Action action, boolean acted, long downAt, long backAt, long backIn,
                boolean endedEarly, String reason) {
        }
    }
}
