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
package org.apache.camel.support.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Counts;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Engine;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Failure;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Operation;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional authorization events through Camel's started EventNotifiers; observer failures cannot affect routing.
 *
 * @since 4.24
 */
public final class AuthorizationObservation {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationObservation.class);
    private static final AuthorizationObservation DISABLED = new AuthorizationObservation();
    private final CamelContext context;
    private final Engine engine;
    private final Operation operation;
    private final String revision;
    private final String exchangeId;
    private final String routeId;
    private final long started;
    private long poolWaitNanos = -1;
    private int expectedItems;
    private final AtomicLongArray counts;
    private final AtomicBoolean published = new AtomicBoolean();

    private AuthorizationObservation() {
        context = null;
        engine = null;
        operation = null;
        revision = null;
        exchangeId = null;
        routeId = null;
        started = 0;
        counts = null;
    }

    private AuthorizationObservation(Exchange exchange, Engine engine, Operation operation, String revision) {
        this.context = exchange.getContext();
        this.engine = engine;
        this.operation = operation;
        this.revision = revision;
        this.exchangeId = exchange.getExchangeId();
        this.routeId = exchange.getFromRouteId();
        this.started = System.nanoTime();
        this.counts = new AtomicLongArray(Outcome.values().length);
    }

    public static AuthorizationObservation begin(Exchange exchange, Engine engine, Operation operation, String revision) {
        try {
            for (EventNotifier notifier : notifiers(exchange.getContext())) {
                try {
                    if (!notifier.isDisabled()) {
                        return new AuthorizationObservation(exchange, engine, operation, revision);
                    }
                } catch (Throwable failure) {
                    LOG.warn("Error checking authorization event notifier. This exception will be ignored.", failure);
                }
            }
        } catch (Throwable failure) {
            LOG.warn("Error obtaining authorization event notifiers. This exception will be ignored.", failure);
        }
        return DISABLED;
    }

    private static List<EventNotifier> notifiers(CamelContext context) {
        if (context == null || context.getManagementStrategy() == null) {
            return List.of();
        }
        List<EventNotifier> result = context.getManagementStrategy().getStartedEventNotifiers();
        return result != null ? result : List.of();
    }

    public boolean isEnabled() {
        return context != null;
    }

    public void item(Outcome outcome) {
        if (isEnabled()) {
            counts.incrementAndGet(outcome.ordinal());
        }
    }

    public void items(Outcome outcome, int count) {
        if (isEnabled()) {
            counts.addAndGet(outcome.ordinal(), count);
        }
    }

    public void expectedItems(int total) {
        if (isEnabled()) {
            expectedItems = total;
        }
    }

    public void failure(Outcome outcome, Failure failure) {
        if (!claim()) {
            return;
        }
        remainingItems(outcome, expectedItems);
        if (operation != Operation.BATCH) {
            item(outcome);
        }
        publish(outcome, failure);
    }

    public void remainingItems(Outcome outcome, int total) {
        if (isEnabled()) {
            long recorded = 0;
            for (int i = 0; i < counts.length(); i++) {
                recorded += counts.get(i);
            }
            counts.addAndGet(outcome.ordinal(), Math.max(0, total - recorded));
        }
    }

    public void poolWait(long nanos) {
        if (isEnabled() && nanos >= 0) {
            poolWaitNanos = nanos;
        }
    }

    public void complete(Outcome outcome, Failure failure) {
        if (!claim()) {
            return;
        }
        if (operation != Operation.BATCH) {
            item(outcome);
        }
        publish(outcome, failure);
    }

    public void completeBatch() {
        if (!claim()) {
            return;
        }
        Outcome outcome = counts.get(Outcome.FAILED_OPEN.ordinal()) > 0 ? Outcome.FAILED_OPEN
                : counts.get(Outcome.ERROR.ordinal()) > 0 ? Outcome.ERROR
                : counts.get(Outcome.UNAVAILABLE.ordinal()) > 0 ? Outcome.UNAVAILABLE
                : counts.get(Outcome.DENY.ordinal()) > 0 ? Outcome.DENY : Outcome.ALLOW;
        Failure failure = counts.get(Outcome.ERROR.ordinal()) > 0 ? Failure.EVALUATION
                : counts.get(Outcome.UNAVAILABLE.ordinal()) + counts.get(Outcome.FAILED_OPEN.ordinal()) > 0
                        ? Failure.UNAVAILABLE
                : Failure.NONE;
        publish(outcome, failure);
    }

    /**
     * An observation publishes once. The completion that claims it builds the event; a cancellation listener firing
     * after the decision, a late second completion, or a loser in a race between two completions changes nothing, not
     * even the counts the winner is reading.
     */
    private boolean claim() {
        return isEnabled() && published.compareAndSet(false, true);
    }

    private void publish(Outcome outcome, Failure failure) {
        AuthorizationDecisionEvent event = new AuthorizationDecisionEvent(
                engine, operation, outcome, failure,
                Math.max(0, System.nanoTime() - started), poolWaitNanos,
                new Counts(
                        counts.get(Outcome.ALLOW.ordinal()), counts.get(Outcome.DENY.ordinal()),
                        counts.get(Outcome.ERROR.ordinal()),
                        counts.get(Outcome.UNAVAILABLE.ordinal()), counts.get(Outcome.FAILED_OPEN.ordinal())),
                revision, exchangeId, routeId);
        EventHelper.notifyCustomEvent(context, () -> event);
    }

    public static boolean interrupted(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 64; depth++, cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }
}
