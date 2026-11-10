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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * also in its parts) and it does not wait for a part with a pending call: then only its innermost pending call, the one
 * started last, is reported, once. So a split, or a {@code to("direct:...")}, that waits for steps that are still busy
 * is not reported. A call that threw stays pending until its callback comes, or until the exchange is done: Camel
 * catches the throw and fails the exchange, but before those fixes the exchange hung there. An exchange that is done
 * (its unit of work, as the events of the exchange tell) is dropped, with the calls of its parts that threw. An
 * exchange stays in the table while one of its parts has a pending call, also when its own calls have called back: the
 * parts of a split with {@code shareUnitOfWork()} have no done event of their own, so the done event of the exchange
 * drops what they threw. A call of a part that did not throw stays, as a call of an exchange of its own: the part may
 * hang after the step that waited for it gave up (a timeout). An exchange whose parts have a pending call is not
 * reported while it waits for them (its innermost call is the step that runs them).
 * <p/>
 * The table is split in stripes, each with its own lock, so that routing threads that work on unrelated exchanges do
 * not wait for each other. An exchange stays in the stripe it is in; a new one goes to the stripe of the exchange it
 * was copied from (its correlation id) when that exchange is in the table, else to the stripe its id hashes to. So an
 * exchange, its parts, their parts and the other copies made while it is in the table are in one stripe (that of the
 * outermost exchange), and each method for one exchange or call changes one stripe under its lock, as the single lock
 * did before. Only the checker ({@link #stalled()}), the counts and the methods used when the context stops or a route
 * is removed look at all the stripes, one at a time.
 * <p/>
 * Each stripe keeps at most {@link #MAX_PENDING} divided by the number of stripes calls, so the table keeps at most
 * {@link #MAX_PENDING}: past it the oldest call of the stripe is dropped and counted. The ids of the parts (the
 * {@code partOf} map, and the {@code partIds} of their exchange) are not counted in that bound: the id of a part goes
 * when the part is done, or when its exchange leaves the table. The locks are held briefly and never while calling out.
 */
final class PendingCalls {

    /**
     * The most pending calls kept, in all the stripes; a safety net, not a sampling limit.
     */
    static final int MAX_PENDING = 10_000;

    /**
     * The most stripes.
     */
    static final int MAX_STRIPES = 32;

    private final long timeoutNanos;
    private final LongSupplier clock;
    // whether a step (route id, step id) runs its work on correlated copies of the exchange and waits for them
    private final BiPredicate<String, String> partStep;
    private final Stripe[] stripes;
    // the stripe of each exchange that has an entry in a stripe, or is a part there (its partOf); an entry is added and
    // removed only under the lock of its stripe, and read without a lock to find that stripe
    private final ConcurrentMap<String, Stripe> stripeOf = new ConcurrentHashMap<>();
    private final AtomicBoolean droppedOnce = new AtomicBoolean();

    PendingCalls(long timeoutNanos, LongSupplier clock, BiPredicate<String, String> partStep) {
        this(timeoutNanos, clock, partStep, defaultStripes());
    }

    /**
     * @param stripes the number of stripes, a power of two
     */
    PendingCalls(long timeoutNanos, LongSupplier clock, BiPredicate<String, String> partStep, int stripes) {
        if (stripes < 1 || Integer.bitCount(stripes) != 1) {
            throw new IllegalArgumentException("The number of stripes must be a power of two: " + stripes);
        }
        this.timeoutNanos = timeoutNanos;
        this.clock = clock;
        this.partStep = partStep;
        this.stripes = new Stripe[stripes];
        for (int i = 0; i < stripes; i++) {
            this.stripes[i] = new Stripe(MAX_PENDING / stripes);
        }
    }

    /**
     * Twice the number of processors, rounded up to a power of two, at most {@link #MAX_STRIPES}.
     */
    static int defaultStripes() {
        int twice = 2 * Runtime.getRuntime().availableProcessors();
        return Math.min(MAX_STRIPES, Integer.highestOneBit(twice - 1) << 1);
    }

    long getTimeoutNanos() {
        return timeoutNanos;
    }

    int getStripes() {
        return stripes.length;
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
        while (true) {
            Stripe stripe = stripeOf.get(id);
            if (stripe == null && parentId instanceof String p) {
                stripe = stripeOf.get(p);
            }
            if (stripe == null) {
                int h = id.hashCode();
                stripe = stripes[(h ^ (h >>> 16)) & (stripes.length - 1)];
            }
            synchronized (stripe) {
                // the exchange may have come into another stripe meanwhile: then it goes there
                Stripe other = stripeOf.putIfAbsent(id, stripe);
                if (other == null || other == stripe) {
                    call.stripe = stripe;
                    return stripe.called(id, parentId, call) && droppedOnce.compareAndSet(false, true);
                }
            }
        }
    }

    /**
     * {@code process} of the call returned.
     */
    void returned(SanitizerStepListener.StepCall call, boolean doneSync) {
        Stripe stripe = call.stripe;
        if (stripe != null) {
            synchronized (stripe) {
                if (call.owner != null) {
                    call.inProcess = null;
                    call.returned = doneSync ? Boolean.TRUE : Boolean.FALSE;
                    call.owner.touch(clock.getAsLong());
                }
            }
        }
    }

    /**
     * {@code process} of the call threw: the call stays pending until its callback comes or the exchange is done.
     */
    void threw(SanitizerStepListener.StepCall call, String description) {
        Stripe stripe = call.stripe;
        if (stripe != null) {
            synchronized (stripe) {
                if (call.owner != null) {
                    call.inProcess = null;
                    call.threw = description;
                    call.owner.touch(clock.getAsLong());
                }
            }
        }
    }

    /**
     * The first callback of the call came.
     *
     * @return how long after the call it came, in nanoseconds, when the call had been reported as still pending; else
     *         -1
     */
    long callback(SanitizerStepListener.StepCall call) {
        Stripe stripe = call.stripe;
        if (stripe == null) {
            return -1;
        }
        synchronized (stripe) {
            ExchangeCalls calls = call.owner;
            if (calls == null) {
                return -1;
            }
            long now = clock.getAsLong();
            calls.touch(now);
            stripe.remove(calls, call);
            return call.reported ? now - call.calledAt : -1;
        }
    }

    /**
     * The exchange is done (its unit of work): what is still pending in it, and what its parts threw, is no longer
     * waited for.
     */
    void done(String exchangeId) {
        for (Stripe stripe = stripeOf.get(exchangeId); stripe != null; stripe = stripeOf.get(exchangeId)) {
            synchronized (stripe) {
                if (stripeOf.get(exchangeId) == stripe) {
                    stripe.done(exchangeId);
                    return;
                }
            }
        }
    }

    /**
     * The innermost pending call of each exchange in which nothing happened for the timeout and that does not wait for
     * parts with a pending call, not reported before; each is marked reported. The stripes are scanned one at a time.
     * An exchange waits for its parts while its innermost call is a step that runs parts; a part left behind by an
     * earlier such step (one that threw in a split with {@code shareUnitOfWork()}, kept until the exchange is done)
     * then also keeps the exchange from being reported.
     */
    List<Pending> stalled() {
        List<Pending> answer = new ArrayList<>();
        for (Stripe stripe : stripes) {
            synchronized (stripe) {
                stripe.stalled(clock.getAsLong(), answer);
            }
        }
        return answer;
    }

    /**
     * All the pending calls, the innermost first in each exchange, and clears the table (when the context stops).
     */
    List<Pending> takeAll() {
        List<Pending> answer = new ArrayList<>();
        for (Stripe stripe : stripes) {
            synchronized (stripe) {
                stripe.takeAll(clock.getAsLong(), answer);
            }
        }
        return answer;
    }

    /**
     * The pending calls of these routes.
     */
    List<SanitizerStepListener.StepCall> callsOf(Collection<String> routeIds) {
        List<SanitizerStepListener.StepCall> answer = new ArrayList<>();
        for (Stripe stripe : stripes) {
            synchronized (stripe) {
                for (ExchangeCalls calls : stripe.exchanges.values()) {
                    for (SanitizerStepListener.StepCall call : calls.calls) {
                        if (routeIds.contains(call.getRouteId())) {
                            answer.add(call);
                        }
                    }
                }
            }
        }
        return answer;
    }

    /**
     * Names the processor of a pending call, when its route is removed and can no longer be looked up.
     */
    void named(SanitizerStepListener.StepCall call, String processor) {
        Stripe stripe = call.stripe;
        if (stripe != null) {
            synchronized (stripe) {
                if (call.owner != null && processor != null) {
                    call.processor = processor;
                }
            }
        }
    }

    /**
     * Whether the call is still in {@code process} on this thread.
     */
    boolean isInProcess(SanitizerStepListener.StepCall call, Thread thread) {
        Stripe stripe = call.stripe;
        if (stripe == null) {
            return false;
        }
        synchronized (stripe) {
            return call.owner != null && call.inProcess == thread;
        }
    }

    int size() {
        int size = 0;
        for (Stripe stripe : stripes) {
            synchronized (stripe) {
                size += stripe.size;
            }
        }
        return size;
    }

    /**
     * How many exchange ids the table knows the stripe of (those with an entry, and the parts of those).
     */
    int getKnownIds() {
        return stripeOf.size();
    }

    /**
     * How many pending calls were dropped because of the bound.
     */
    long getDropped() {
        long n = 0;
        for (Stripe stripe : stripes) {
            synchronized (stripe) {
                n += stripe.dropped;
            }
        }
        return n;
    }

    /**
     * One stripe of the table: the exchanges of the stripe with their pending calls. Guarded by the lock of the stripe.
     */
    final class Stripe {

        private final int bound;
        // the exchanges with pending calls (or with parts that have), by the id of the exchange, the oldest first
        private final Map<String, ExchangeCalls> exchanges = new LinkedHashMap<>();
        // the exchange each part belongs to, by the id of the part, while that exchange is in the table: a part keeps
        // it when its correlation id is changed later (such as by the results of a seda request copied onto it)
        private final Map<String, ExchangeCalls> partOf = new HashMap<>();
        private int size;
        private long dropped;

        Stripe(int bound) {
            this.bound = bound;
        }

        /**
         * @return whether a pending call was dropped because of the bound
         */
        private boolean called(String id, Object parentId, SanitizerStepListener.StepCall call) {
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
                            = parent != null && !parent.calls.isEmpty() ? parent.calls.get(parent.calls.size() - 1) : null;
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
            return size > bound && dropOldest();
        }

        private void done(String exchangeId) {
            ExchangeCalls owner = partOf.remove(exchangeId);
            if (owner != null) {
                owner.partIds.remove(exchangeId);
            }
            ExchangeCalls calls = exchanges.get(exchangeId);
            if (calls != null) {
                for (SanitizerStepListener.StepCall call : calls.calls) {
                    call.owner = null;
                    call.inProcess = null;
                }
                size -= calls.calls.size();
                calls.calls.clear();
                unlink(calls);
                if (calls.parts != null) {
                    for (ExchangeCalls part : new ArrayList<>(calls.parts)) {
                        dropThrown(part);
                        // what the part still waits for is its own now
                        part.parent = null;
                    }
                }
            }
            release(exchangeId);
        }

        private void stalled(long now, List<Pending> answer) {
            for (ExchangeCalls calls : exchanges.values()) {
                // an exchange without calls of its own is kept for its parts, which are reported instead
                if (now - calls.lastActivity < timeoutNanos || calls.calls.isEmpty()) {
                    continue;
                }
                SanitizerStepListener.StepCall innermost = calls.calls.get(calls.calls.size() - 1);
                // while the exchange waits for its parts (its innermost call is the step that runs them), they are
                // reported, not the exchange; once that step called back, a part left behind does not hide it
                if (calls.parts != null && !calls.parts.isEmpty()
                        && partStep.test(innermost.getRouteId(), innermost.getNodeId())) {
                    continue;
                }
                if (!innermost.reported) {
                    innermost.reported = true;
                    answer.add(new Pending(innermost, calls, now));
                }
            }
        }

        private void takeAll(long now, List<Pending> answer) {
            for (ExchangeCalls calls : exchanges.values()) {
                for (int i = calls.calls.size() - 1; i >= 0; i--) {
                    answer.add(new Pending(calls.calls.get(i), calls, now));
                }
                for (SanitizerStepListener.StepCall call : calls.calls) {
                    call.owner = null;
                }
            }
            exchanges.keySet().forEach(this::forget);
            partOf.keySet().forEach(this::forget);
            exchanges.clear();
            partOf.clear();
            size = 0;
        }

        private void remove(ExchangeCalls calls, SanitizerStepListener.StepCall call) {
            call.owner = null;
            call.inProcess = null;
            calls.calls.remove(call);
            size--;
            unlinkIfEmpty(calls);
        }

        /**
         * A part of an exchange that is done: its calls that threw go, and those of its parts. Camel handled those
         * throws, and the parts of a split with {@code shareUnitOfWork()} have no done event of their own. A call that
         * did not throw stays, with its part as an exchange of its own: a part can hang after the step that waited for
         * it gave up (a timeout), and that is still reported.
         */
        private void dropThrown(ExchangeCalls part) {
            if (part.parts != null) {
                for (ExchangeCalls inner : new ArrayList<>(part.parts)) {
                    dropThrown(inner);
                }
            }
            for (Iterator<SanitizerStepListener.StepCall> it = part.calls.iterator(); it.hasNext();) {
                SanitizerStepListener.StepCall call = it.next();
                if (call.threw != null) {
                    call.owner = null;
                    call.inProcess = null;
                    it.remove();
                    size--;
                }
            }
            unlinkIfEmpty(part);
        }

        /**
         * The exchange leaves the table when it has no pending call left, in it and in its parts.
         */
        private void unlinkIfEmpty(ExchangeCalls calls) {
            if (calls.calls.isEmpty() && (calls.parts == null || calls.parts.isEmpty())) {
                unlink(calls);
            }
        }

        private void unlink(ExchangeCalls calls) {
            if (exchanges.remove(calls.id, calls)) {
                release(calls.id);
            }
            if (calls.partIds != null) {
                for (String part : calls.partIds) {
                    if (partOf.remove(part, calls)) {
                        release(part);
                    }
                }
            }
            ExchangeCalls parent = calls.parent;
            if (parent != null && parent.parts != null && parent.parts.remove(calls)
                    && exchanges.get(parent.id) == parent) {
                // the exchange it is a part of may have waited only for this part
                unlinkIfEmpty(parent);
            }
        }

        /**
         * The id is no longer in this stripe, when it has no entry and is no part here.
         */
        private void release(String id) {
            if (!exchanges.containsKey(id) && !partOf.containsKey(id)) {
                forget(id);
            }
        }

        private void forget(String id) {
            stripeOf.remove(id, this);
        }

        /**
         * Drops the oldest pending call: the first call of the exchange that has waited longest.
         */
        private boolean dropOldest() {
            for (ExchangeCalls oldest : exchanges.values()) {
                if (!oldest.calls.isEmpty()) {
                    remove(oldest, oldest.calls.get(0));
                    dropped++;
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The pending calls of one exchange (not the exchange).
     */
    static final class ExchangeCalls {

        final String id;
        // the exchange it is a part of, until that one is done
        ExchangeCalls parent;
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
