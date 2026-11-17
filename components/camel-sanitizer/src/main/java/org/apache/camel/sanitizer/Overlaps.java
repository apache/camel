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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * Rule R3 of the exchange sanitizer, its second part: accesses that overlap in time, as KCSAN does in the Linux kernel.
 * The routing engine uses an exchange at known points: a step is called, the callback of a step continues the exchange,
 * an on completion runs. At every Nth point (a counter of the context, not random, so a rerun samples the same points
 * of the same run) the thread opens a short watch window on the exchange and pauses in it. A thread that comes to a
 * point of the same exchange during the window is reported with the thread that watches, each with its stack. A thread
 * that only waits (such as a caller that waits for its task) is at no point, so it is not reported. The return of
 * {@code process} is not a point: after an asynchronous processor handed the exchange on, its caller only unwinds,
 * while the thread that got the exchange may already call the callback.
 * <p/>
 * The windows are keyed by the id of the exchange and hold no exchange: the id and the identity hash of the exchange
 * are read at the call of a step and at an on completion, on the thread that uses the exchange then. A window holds
 * ids, names and the id of its thread, and lives only while its thread pauses. At most one window is open per id: a
 * thread that comes to a point of the same exchange while another thread watches it is the overlap, and opens no window
 * of its own. A copy that keeps the id of its exchange (a {@code loop} with {@code copy}, an exchange that a transport
 * in the same JVM rebuilds with the id of the sender) is another exchange, told apart by its identity hash, so it is
 * not reported with it.
 * <p/>
 * The pause uses the system clock ({@link System#nanoTime()}), not the {@code TimeSource} of the context, so a virtual
 * clock (camel-test-dst) does not stretch it. An interrupt ends it early (the thread stays interrupted).
 * <p/>
 * Two threads that both continue an exchange because of a violation reported already for it (a callback called twice, a
 * callback after a throw, a value of the callback that differs from the return, a commit run twice) overlap as a
 * consequence of that violation: the overlap is not reported for such an exchange. The thread that continues an
 * exchange a second time reports that violation before it comes to its next point (the step listener of R1, and the
 * contract of R3, are called before this one), so the report is there before the window that it hits closes. For the
 * same reason an exchange gets at most one report of an overlap: the first window that closes with an overlap claims
 * the exchange.
 */
final class Overlaps {

    // the exchanges with a violation or an overlap reported, so an overlap of them is not reported (the last ones)
    static final int MAX_VIOLATED = 1000;

    // the kinds of point
    static final byte STEP_CALL = 0;
    static final byte STEP_CALLBACK = 1;
    static final byte ON_COMPLETION = 2;

    private static final Access CLOSED = new Access(STEP_CALL, null, null, null);

    private final Sanitizer sanitizer;
    private final int sampling;
    private final long windowNanos;
    private final AtomicLong points = new AtomicLong();
    private final LongAdder windows = new LongAdder();
    // the watch windows open now, by the id of the exchange
    private final Map<String, Window> open = new ConcurrentHashMap<>();
    // guarded by itself
    private final Map<String, Boolean> violated = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_VIOLATED;
        }
    };

    /**
     * @param sampling    every how many points a watch window is opened (1 for each point); more than 0
     * @param windowNanos how long a thread pauses in a watch window
     */
    Overlaps(Sanitizer sanitizer, int sampling, long windowNanos) {
        if (sampling <= 0) {
            throw new IllegalArgumentException("sampling must be more than 0: " + sampling);
        }
        this.sanitizer = sanitizer;
        this.sampling = sampling;
        this.windowNanos = windowNanos;
    }

    int getSampling() {
        return sampling;
    }

    long getWindowNanos() {
        return windowNanos;
    }

    /**
     * How many points were seen.
     */
    long getPointCount() {
        return points.get();
    }

    /**
     * How many watch windows were opened.
     */
    long getWindowCount() {
        return windows.sum();
    }

    /**
     * Whether a watch window is open on the exchange now.
     */
    boolean isWatching(String exchangeId) {
        return open.containsKey(exchangeId);
    }

    /**
     * A violation was reported for the exchange: an overlap of it that follows is not reported.
     */
    void violated(String exchangeId) {
        if (exchangeId != null) {
            synchronized (violated) {
                violated.put(exchangeId, Boolean.TRUE);
            }
        }
    }

    /**
     * Claims the report of an overlap of the exchange: false when a violation or an overlap was reported for it
     * already.
     */
    private boolean claim(String exchangeId) {
        synchronized (violated) {
            return violated.putIfAbsent(exchangeId, Boolean.TRUE) == null;
        }
    }

    /**
     * The thread uses the exchange at a point.
     *
     * @param exchangeId the id of the exchange
     * @param identity   the identity hash of the exchange ({@link System#identityHashCode(Object)}), which tells apart
     *                   two exchanges with the same id
     * @param kind       {@link #STEP_CALL}, {@link #STEP_CALLBACK} or {@link #ON_COMPLETION}
     * @param routeId    the route of the step, or the route the exchange came from for an on completion
     * @param name       the id of the step, or the class of the on completion
     */
    void point(String exchangeId, int identity, byte kind, String routeId, String name) {
        if (exchangeId == null) {
            return;
        }
        long thread = Thread.currentThread().threadId();
        boolean watched = false;
        if (!open.isEmpty()) {
            Window w = open.get(exchangeId);
            // the thread of a window pauses in it, so it is never at a point of its own window
            if (w != null && w.thread != thread && w.identity == identity) {
                // the stack is taken only for an overlap, which correct code never has
                w.hit(new Access(kind, routeId, name, sanitizer.trace()));
                watched = true;
            }
        }
        if (points.incrementAndGet() % sampling == 0 && !watched) {
            watch(exchangeId, identity, thread, kind, routeId, name);
        }
    }

    private void watch(String exchangeId, int identity, long thread, byte kind, String routeId, String name) {
        Window w = new Window(identity, thread, kind, routeId, name);
        if (open.putIfAbsent(exchangeId, w) != null) {
            // another thread opened one meanwhile: this thread came to a point at the same time, which that window may
            // have missed; it is not watched twice. Or a copy with the same id is watched: this point is not sampled
            return;
        }
        windows.increment();
        try {
            pause();
        } finally {
            open.remove(exchangeId, w);
        }
        Access other = w.close();
        if (other != null && claim(exchangeId)) {
            report(exchangeId, w, other);
        }
    }

    /**
     * Pauses the thread for the watch window, by the system clock.
     */
    private void pause() {
        long deadline = System.nanoTime() + windowNanos;
        for (long left = windowNanos; left > 0; left = deadline - System.nanoTime()) {
            LockSupport.parkNanos(this, left);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    private void report(String exchangeId, Window w, Access other) {
        // the stack of the thread that watched is taken now: it is still at its point
        Sanitizer.Trace watcher = sanitizer.trace();
        StringBuilder sb = new StringBuilder(512);
        watcher.appendTo(sb, "watched while " + describe(w.kind, w.routeId, w.name));
        other.trace.appendTo(sb, "meanwhile " + describe(other.kind, other.routeId, other.name));
        // the report names the step of the thread that watched, or else the step of the other thread
        boolean ownStep = w.kind != ON_COMPLETION;
        boolean otherStep = other.kind != ON_COMPLETION;
        String routeId = ownStep || !otherStep ? w.routeId : other.routeId;
        String stepId = ownStep ? w.name : otherStep ? other.name : null;
        sanitizer.record(Violation.USED_BY_TWO_THREADS, exchangeId, routeId, stepId, sb.toString());
    }

    private static String describe(byte kind, String routeId, String name) {
        return switch (kind) {
            case STEP_CALL -> "step " + name + " of route " + routeId + " is called";
            case STEP_CALLBACK -> "the callback of step " + name + " of route " + routeId + " continues the exchange";
            default -> "on completion " + name + " runs";
        };
    }

    /**
     * A watch window: the point where its thread pauses, and the first access of another thread during the window.
     */
    private static final class Window {

        final int identity;
        // the id of the thread (a Thread is not kept)
        final long thread;
        final byte kind;
        final String routeId;
        final String name;
        private final AtomicReference<Access> hit = new AtomicReference<>();

        Window(int identity, long thread, byte kind, String routeId, String name) {
            this.identity = identity;
            this.thread = thread;
            this.kind = kind;
            this.routeId = routeId;
            this.name = name;
        }

        void hit(Access access) {
            // the first one is kept; one after the window closed is too late
            hit.compareAndSet(null, access);
        }

        /**
         * Closes the window, and returns the access of another thread during it, or null.
         */
        Access close() {
            Access a = hit.getAndSet(CLOSED);
            return a != CLOSED ? a : null;
        }
    }

    /**
     * An access of another thread to the exchange during a watch window, with its stack.
     */
    private static final class Access {

        final byte kind;
        final String routeId;
        final String name;
        final Sanitizer.Trace trace;

        Access(byte kind, String routeId, String name, Sanitizer.Trace trace) {
            this.kind = kind;
            this.routeId = routeId;
            this.name = name;
            this.trace = trace;
        }
    }
}
