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
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Route;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;

/**
 * What chaos keeps of one exchange: how each route it went through is done with it, so a route counts the exchange once
 * however often it enters the route (a {@code direct} route called in a loop, or again by a redelivery), and whether
 * the dead letter channel is delivering it, so the send of the dead letter channel is told apart from the other sends.
 * <p/>
 * It is a {@link SafeCopyProperty} of the exchange: internal to Camel, not one of the properties of the exchange. A
 * copy of the exchange (a wire tap, a split, a seda queue) is another exchange, and starts with a state of its own. An
 * exchange is processed by one thread at a time, so the state needs no lock.
 */
final class ChaosExchangeState implements SafeCopyProperty {

    static final String KEY = "CamelChaosExchangeState";

    /** How a route was done with the exchange the last time, for its counts. */
    enum Outcome {
        // entered the route (the entry faults were asked), not done with yet
        ENTERED,
        COMPLETED,
        // completed after the error handler handled a failure
        HANDLED,
        FAILED,
        // failed, and the consumer of the route reads the message again
        FAILED_KEPT;

        boolean completed() {
            return this == COMPLETED || this == HANDLED;
        }
    }

    /** A route the exchange went through. */
    static final class RouteState {
        Outcome outcome = Outcome.ENTERED;
        // the counts of the route the outcome is counted in: other counts (after a reset of the report) have not
        // counted the exchange
        ChaosManager.RouteCounts counts;
        // the expectations of the route counted the exchange as completed, handled or failed (each once, also when the
        // exchange goes through the route again)
        boolean expectedCompleted;
        boolean expectedHandled;
        boolean expectedFailed;
        // when the exchange entered the route, for the expectations on time (meanTime, maxTime), else -1; and whether
        // they counted its time
        long enteredAt = -1;
        boolean expectedTime;
    }

    private final Map<String, RouteState> routes = new HashMap<>(4);
    // the rules that faulted the exchange, counted in their effect when it is done (null until a rule faults it)
    private Faults faults;
    // the dead letter channel is delivering the exchange
    boolean deadLetter;
    // chaos skipped the send the exchange is in (drop), so afterSend asks no rule with at=reply
    boolean skippedSend;
    // a rule with route= that fired as the exchange entered its route (error or delay), which acts at the next step: the
    // first step of the route, inside its error handler; and that route
    ChaosRule entryFault;
    String entryRoute;
    // the rule that failed or dropped that delivery, which loses the message when the dead letter channel then handles
    // it
    ChaosRule deadLetterFault;

    /** The state of the exchange, created when there is none. */
    static ChaosExchangeState of(Exchange exchange) {
        ExchangeExtension ee = exchange.getExchangeExtension();
        ChaosExchangeState state = ee.getSafeCopyProperty(KEY, ChaosExchangeState.class);
        if (state == null) {
            state = new ChaosExchangeState();
            ee.setSafeCopyProperty(KEY, state);
        }
        return state;
    }

    /** The state of the exchange, or null when chaos has not seen it. */
    static ChaosExchangeState peek(Exchange exchange) {
        return exchange.getExchangeExtension().getSafeCopyProperty(KEY, ChaosExchangeState.class);
    }

    /**
     * The exchange enters the route.
     *
     * @return whether it is the first time, so the entry faults are asked once per exchange
     */
    boolean enter(String routeId) {
        return routes.putIfAbsent(routeId, new RouteState()) == null;
    }

    /** The state of the exchange in the route, created when the exchange enters it. */
    RouteState route(String routeId) {
        return routes.computeIfAbsent(routeId, k -> new RouteState());
    }

    /**
     * The route the exchange is in: the route of its unit of work, else the route that created it.
     */
    static String routeId(Exchange exchange) {
        UnitOfWork uow = exchange.getUnitOfWork();
        Route route = uow != null ? uow.getRoute() : null;
        return route != null ? route.getRouteId() : exchange.getFromRouteId();
    }

    /**
     * A rule faulted the exchange: the exchange property {@link ChaosManager#CHAOS_RULE} names the rule, and the effect
     * of the rule counts the exchange once, when it is done. An exchange without a unit of work (such as one sent with
     * a producer template to an endpoint, not to a route) is not counted in the effect, as nothing tells when it is
     * done.
     */
    static void faulted(ChaosRule rule, Exchange exchange, String routeId) {
        exchange.setProperty(ChaosManager.CHAOS_RULE, rule.id);
        if (exchange.getUnitOfWork() == null) {
            return;
        }
        ChaosExchangeState state = of(exchange);
        if (state.faults == null) {
            state.faults = new Faults();
            exchange.getExchangeExtension().addOnCompletion(state.faults);
        }
        state.faults.add(rule, routeId);
    }

    /** The error handler redelivers the exchange: a rule that faulted it before is recovered when it then completes. */
    void redelivered() {
        if (faults != null) {
            faults.redelivered();
        }
    }

    /** The rules that faulted an exchange, and whether the error handler redelivered it after each one. */
    static final class Faults extends SynchronizationAdapter {

        private final List<ChaosRule> rules = new ArrayList<>(2);
        private final List<Boolean> redelivered = new ArrayList<>(2);
        // the route each rule first faulted the exchange in, where its outcome counts too (null when not followed)
        private final List<ChaosRule.InRoute> inRoutes = new ArrayList<>(2);

        void add(ChaosRule rule, String routeId) {
            int i = rules.indexOf(rule);
            if (i < 0) {
                rules.add(rule);
                redelivered.add(Boolean.FALSE);
                rule.effect.faulted();
                ChaosRule.InRoute in = rule.inRoute(routeId);
                inRoutes.add(in);
                if (in != null) {
                    in.effect.faulted();
                }
            } else {
                // faulted again (such as at a redelivery): the redelivery that follows counts from now
                redelivered.set(i, Boolean.FALSE);
            }
        }

        void redelivered() {
            for (int i = 0; i < redelivered.size(); i++) {
                redelivered.set(i, Boolean.TRUE);
            }
        }

        /**
         * Takes the faults of another copy of the same exchange: a rule that faulted both counts once, as it is one
         * message.
         */
        void merge(Faults other) {
            for (int i = 0; i < other.rules.size(); i++) {
                ChaosRule rule = other.rules.get(i);
                if (rules.contains(rule)) {
                    rule.effect.unfaulted();
                    ChaosRule.InRoute in = other.inRoutes.get(i);
                    if (in != null) {
                        in.effect.unfaulted();
                    }
                } else {
                    rules.add(rule);
                    redelivered.add(other.redelivered.get(i));
                    inRoutes.add(other.inRoutes.get(i));
                }
            }
        }

        @Override
        public boolean allowHandover() {
            // the effect is how this exchange was done: not a copy that a seda endpoint (such as the one of a dead
            // letter channel) hands the completions over to, which its route completes
            return false;
        }

        @Override
        public void onDone(Exchange exchange) {
            if (waitForOriginal(exchange)) {
                return;
            }
            count(exchange);
        }

        /**
         * A circuit breaker runs its steps on a correlated copy of the exchange, in a try block, with a unit of work of
         * its own (CAMEL-26701). The exchange then goes on with the result, the fallback or the exception, and is the
         * message the faults had an effect on: they count when it is done, with its outcome, once per rule also when
         * the circuit breaker ran again at a redelivery. The exchange is not touched from the thread of the copy; the
         * faults wait under its id until the event that it is done. A copy of a wire tap in a try block is correlated
         * too, and its exchange may be done before it: when no event comes in a minute, its faults count with how the
         * copy was done.
         */
        private boolean waitForOriginal(Exchange copy) {
            String originalId = copy.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
            if (originalId == null || !copy.getProperty(ExchangePropertyKey.TRY_ROUTE_BLOCK, false, boolean.class)) {
                return false;
            }
            ChaosManager manager = ChaosManager.get(copy.getContext());
            return manager != null && manager.waitForOriginal(originalId, this, failed(copy), handled(copy));
        }

        /** Counts the faults in the effect of their rules, with how the exchange was done. */
        void count(Exchange exchange) {
            count(failed(exchange), handled(exchange), ExchangeHelper.isRedelivered(exchange));
        }

        /** Counts the faults, with whether the exchange failed, was handled, or was redelivered. */
        void count(boolean failed, boolean handled, boolean redeliveredExchange) {
            for (int i = 0; i < rules.size(); i++) {
                ChaosRule.Effect.Outcome outcome;
                if (failed) {
                    outcome = ChaosRule.Effect.Outcome.FAILED;
                } else if (handled) {
                    outcome = ChaosRule.Effect.Outcome.HANDLED;
                } else if (redelivered.get(i) || redeliveredExchange) {
                    outcome = ChaosRule.Effect.Outcome.RECOVERED;
                } else {
                    outcome = ChaosRule.Effect.Outcome.COMPLETED;
                }
                rules.get(i).effect.done(outcome);
                ChaosRule.InRoute in = inRoutes.get(i);
                if (in != null) {
                    in.effect.done(outcome);
                }
            }
        }

        private static boolean failed(Exchange exchange) {
            // an exchange marked rollback only has no exception, but its message is not processed either
            return exchange.isFailed() || exchange.isRollbackOnly() || exchange.isRollbackOnlyLast();
        }

        private static boolean handled(Exchange exchange) {
            ExchangeExtension ee = exchange.getExchangeExtension();
            return !failed(exchange) && ee.isErrorHandlerHandledSet() && ee.isErrorHandlerHandled();
        }

        @Override
        public String toString() {
            return "ChaosFaults" + rules;
        }
    }

    @Override
    public SafeCopyProperty safeCopy() {
        // a copy is another exchange: it is counted, and goes through the dead letter channel, on its own
        return new ChaosExchangeState();
    }
}
