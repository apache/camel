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
package org.apache.camel.sanitizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;

/**
 * The calls of steps whose callback has not come yet, per exchange, for the "still pending" warning of rule R1: a
 * bounded map that keeps no exchange. It is keyed by the id of the exchange, so a part of a split, multicast or
 * recipient list (or the copy that an enrich, kamelet, circuit breaker or {@code to("kamelet:...")} step works on)
 * finds the exchange it belongs to by its correlation id, when the innermost pending call of that exchange is such a
 * step; other correlated copies (such as the copy that a seda producer queues) are exchanges of their own, unless the
 * producing exchange is already in such a step when the copy makes its first call (only a warning may then come later).
 * It does not let an exchange that is gone take its entry with it (weak keys): a processor that drops the callback
 * usually leaves nothing that refers to the exchange (the inflight repository keeps it only when browsing is on), so
 * its entry would go exactly when the warning is needed.
 * <p/>
 * An exchange is reported when nothing happened in it for the timeout (no call of a step, return, throw or callback,
 * also in its parts) and no part of it has a pending call: then only its innermost pending call, the one started last,
 * is reported, once. So a split, or a {@code to("direct:...")}, that waits for steps that are still busy is not
 * reported. A call that threw stays pending until its callback comes, or until the exchange is done: Camel catches the
 * throw and fails the exchange, but before those fixes the exchange hung there. An exchange that is done (its unit of
 * work, as the events of the exchange tell) is dropped with its parts.
 * <p/>
 * At most {@link #MAX_PENDING} calls are kept: past it the oldest is dropped and counted. The ids of the parts (the
 * {@code partOf} map, and the {@code partIds} of their exchange) are not counted in that bound: the id of a part goes
 * when the part is done, or when its exchange has no pending call left. All methods take the lock of this table, which
 * is held briefly and never while calling out ({@link #stalled()} scans every exchange of the table under it, once per
 * period of the check).
 */
final class PendingCalls {

    /**
     * The most pending calls kept; a safety net, not a sampling limit.
     */
    static final int MAX_PENDING = 10_000;

    private final long timeoutNanos;
    private final LongSupplier clock;
    // whether a step (route id, step id) runs its work on correlated copies of the exchange and waits for them
    private final BiPredicate<String, String> partStep;
    // guarded by this: the exchanges with pending calls, by the id of the exchange, the oldest first
    private final Map<String, ExchangeCalls> exchanges = new LinkedHashMap<>();
    // the exchange each part belongs to, by the id of the part, while that exchange has pending calls: a part keeps it
    // when its correlation id is changed later (such as by the results of a seda request copied onto it)
    private final Map<String, ExchangeCalls> partOf = new HashMap<>();
    private int size;
    private long dropped;

    PendingCalls(long timeoutNanos, LongSupplier clock, BiPredicate<String, String> partStep) {
        this.timeoutNanos = timeoutNanos;
        this.clock = clock;
        this.partStep = partStep;
    }

    long getTimeoutNanos() {
        return timeoutNanos;
    }

    /**
     * The processor of a step is called. Only here is the exchange read.
     *
     * @return whether a pending call was dropped for the first time, because of the bound
     */
    boolean called(Exchange exchange, SanitizerStepListener.StepCall call) {
        String id = exchange.getExchangeId();
        // a correlated copy carries the id of the exchange it was copied from
        Object parentId = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID);
        synchronized (this) {
            long now = clock.getAsLong();
            ExchangeCalls calls = exchanges.get(id);
            if (calls == null) {
                ExchangeCalls parent = partOf.get(id);
                if (parent == null && parentId instanceof String p && !p.equals(id)) {
                    parent = exchanges.get(p);
                    // a part only when the innermost pending call of that exchange is a step that waits for its copies
                    // (split, multicast, recipient list, enrich, kamelet, circuit breaker); not the copy that a seda
                    // producer queues, which is an exchange of its own
                    SanitizerStepListener.StepCall waiting
                            = parent != null ? parent.calls.get(parent.calls.size() - 1) : null;
                    if (waiting == null || !partStep.test(waiting.getRouteId(), waiting.getNodeId())) {
                        parent = null;
                    } else {
                        partOf.put(id, parent);
                        parent.partIds().add(id);
                    }
                }
                calls = new ExchangeCalls(id, parent);
                if (parent != null) {
                    parent.parts().add(calls);
                }
                exchanges.put(id, calls);
            }
            call.owner = calls;
            call.calledAt = now;
            call.inProcess = Thread.currentThread();
            calls.calls.add(call);
            size++;
            calls.touch(now);
            if (size > MAX_PENDING) {
                return dropOldest();
            }
            return false;
        }
    }

    /**
     * {@code process} of the call returned.
     */
    synchronized void returned(SanitizerStepListener.StepCall call, boolean doneSync) {
        if (call.owner != null) {
            call.inProcess = null;
            call.returned = doneSync ? Boolean.TRUE : Boolean.FALSE;
            call.owner.touch(clock.getAsLong());
        }
    }

    /**
     * {@code process} of the call threw: the call stays pending until its callback comes or the exchange is done.
     */
    synchronized void threw(SanitizerStepListener.StepCall call, String description) {
        if (call.owner != null) {
            call.inProcess = null;
            call.threw = description;
            call.owner.touch(clock.getAsLong());
        }
    }

    /**
     * The first callback of the call came.
     *
     * @return how long after the call it came, in nanoseconds, when the call had been reported as still pending; else
     *         -1
     */
    synchronized long callback(SanitizerStepListener.StepCall call) {
        ExchangeCalls calls = call.owner;
        if (calls == null) {
            return -1;
        }
        long now = clock.getAsLong();
        calls.touch(now);
        remove(calls, call);
        return call.reported ? now - call.calledAt : -1;
    }

    /**
     * The exchange is done (its unit of work): what is still pending in it and in its parts is no longer waited for.
     */
    synchronized void done(String exchangeId) {
        ExchangeCalls owner = partOf.remove(exchangeId);
        if (owner != null) {
            owner.partIds.remove(exchangeId);
        }
        ExchangeCalls calls = exchanges.get(exchangeId);
        if (calls != null) {
            drop(calls);
        }
    }

    /**
     * The innermost pending call of each exchange in which nothing happened for the timeout and whose parts have no
     * pending call, not reported before; each is marked reported.
     */
    synchronized List<Pending> stalled() {
        long now = clock.getAsLong();
        List<Pending> answer = null;
        for (ExchangeCalls calls : exchanges.values()) {
            if (now - calls.lastActivity < timeoutNanos || calls.parts != null && !calls.parts.isEmpty()) {
                continue;
            }
            SanitizerStepListener.StepCall innermost = calls.calls.get(calls.calls.size() - 1);
            if (!innermost.reported) {
                innermost.reported = true;
                if (answer == null) {
                    answer = new ArrayList<>();
                }
                answer.add(new Pending(innermost, calls, now));
            }
        }
        return answer != null ? answer : List.of();
    }

    /**
     * All the pending calls, the innermost first in each exchange, and clears the table (when the context stops).
     */
    synchronized List<Pending> takeAll() {
        long now = clock.getAsLong();
        List<Pending> answer = new ArrayList<>(size);
        for (ExchangeCalls calls : exchanges.values()) {
            for (int i = calls.calls.size() - 1; i >= 0; i--) {
                answer.add(new Pending(calls.calls.get(i), calls, now));
            }
        }
        for (ExchangeCalls calls : exchanges.values()) {
            for (SanitizerStepListener.StepCall call : calls.calls) {
                call.owner = null;
            }
        }
        exchanges.clear();
        partOf.clear();
        size = 0;
        return answer;
    }

    /**
     * The pending calls of these routes.
     */
    synchronized List<SanitizerStepListener.StepCall> callsOf(Collection<String> routeIds) {
        List<SanitizerStepListener.StepCall> answer = new ArrayList<>();
        for (ExchangeCalls calls : exchanges.values()) {
            for (SanitizerStepListener.StepCall call : calls.calls) {
                if (routeIds.contains(call.getRouteId())) {
                    answer.add(call);
                }
            }
        }
        return answer;
    }

    /**
     * Names the processor of a pending call, when its route is removed and can no longer be looked up.
     */
    synchronized void named(SanitizerStepListener.StepCall call, String processor) {
        if (call.owner != null && processor != null) {
            call.processor = processor;
        }
    }

    /**
     * Whether the call is still in {@code process} on this thread.
     */
    synchronized boolean isInProcess(SanitizerStepListener.StepCall call, Thread thread) {
        return call.owner != null && call.inProcess == thread;
    }

    synchronized int size() {
        return size;
    }

    /**
     * How many pending calls were dropped because of the bound.
     */
    synchronized long getDropped() {
        return dropped;
    }

    private void remove(ExchangeCalls calls, SanitizerStepListener.StepCall call) {
        call.owner = null;
        call.inProcess = null;
        calls.calls.remove(call);
        size--;
        if (calls.calls.isEmpty()) {
            unlink(calls);
        }
    }

    /**
     * Drops the exchange and its parts with all their pending calls.
     */
    private void drop(ExchangeCalls calls) {
        for (SanitizerStepListener.StepCall call : calls.calls) {
            call.owner = null;
            call.inProcess = null;
        }
        size -= calls.calls.size();
        calls.calls.clear();
        unlink(calls);
        if (calls.parts != null) {
            for (ExchangeCalls part : new ArrayList<>(calls.parts)) {
                drop(part);
            }
        }
    }

    private void unlink(ExchangeCalls calls) {
        if (exchanges.get(calls.id) == calls) {
            exchanges.remove(calls.id);
        }
        if (calls.partIds != null) {
            for (String part : calls.partIds) {
                partOf.remove(part, calls);
            }
        }
        if (calls.parent != null && calls.parent.parts != null) {
            calls.parent.parts.remove(calls);
        }
    }

    /**
     * Drops the oldest pending call: the first call of the exchange that has waited longest.
     *
     * @return whether it is the first one dropped
     */
    private boolean dropOldest() {
        Iterator<ExchangeCalls> it = exchanges.values().iterator();
        if (!it.hasNext()) {
            return false;
        }
        ExchangeCalls oldest = it.next();
        remove(oldest, oldest.calls.get(0));
        return ++dropped == 1;
    }

    /**
     * The pending calls of one exchange (not the exchange).
     */
    static final class ExchangeCalls {

        final String id;
        final ExchangeCalls parent;
        // in the order of the calls: the last is the innermost
        final List<SanitizerStepListener.StepCall> calls = new ArrayList<>(4);
        // the parts with pending calls, and the ids of all its parts (created when the first part is)
        Set<ExchangeCalls> parts;
        Set<String> partIds;
        long lastActivity;

        ExchangeCalls(String id, ExchangeCalls parent) {
            this.id = id;
            this.parent = parent;
        }

        Set<ExchangeCalls> parts() {
            if (parts == null) {
                parts = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            return parts;
        }

        Set<String> partIds() {
            if (partIds == null) {
                partIds = new HashSet<>();
            }
            return partIds;
        }

        /**
         * Something happened in this exchange, so also in the exchanges it is a part of.
         */
        void touch(long now) {
            for (ExchangeCalls c = this; c != null; c = c.parent) {
                c.lastActivity = now;
            }
        }
    }

    /**
     * A pending call as it was when it was taken from the table, for a report.
     */
    static final class Pending {

        final SanitizerStepListener.StepCall call;
        final String exchangeId;
        final String routeId;
        final String stepId;
        final long pendingNanos;
        final long idleNanos;
        final Thread inProcess;
        final Boolean returned;
        final String threw;
        // the processor, when it was named as its route was removed
        final String processor;
        // the other pending calls of the exchange (route and step), the innermost first
        final List<String> enclosing;

        Pending(SanitizerStepListener.StepCall call, ExchangeCalls calls, long now) {
            this.call = call;
            this.exchangeId = calls.id;
            this.routeId = call.getRouteId();
            this.stepId = call.getNodeId();
            this.pendingNanos = now - call.calledAt;
            this.idleNanos = now - calls.lastActivity;
            this.inProcess = call.inProcess;
            this.returned = call.returned;
            this.threw = call.threw;
            this.processor = call.processor;
            List<String> others = new ArrayList<>(calls.calls.size());
            for (int i = calls.calls.size() - 1; i >= 0; i--) {
                SanitizerStepListener.StepCall c = calls.calls.get(i);
                if (c != call) {
                    others.add("route " + c.getRouteId() + " step " + c.getNodeId());
                }
            }
            this.enclosing = others;
        }
    }
}
