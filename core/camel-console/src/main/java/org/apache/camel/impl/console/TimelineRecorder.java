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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The recording behind the timeline console: for each second of the last minutes, what each route did (completed,
 * recovered by a redelivery, handled by the error handler, failed), its mean, p95 and max time, the exchanges in flight
 * and the redelivery attempts; the errors of that second by exception; and a few exchanges of that second (the failed
 * and handled ones, and the slowest), with the calls they made.
 * <p/>
 * The route outcomes come from the counters of the routes, which {@link #sample(long, Map)} takes once a second. The
 * rest comes from the exchange events, as they happen. An exchange counts as recovered in the route where it was
 * redelivered, when it then completes without the error handler handling it: this is taken from the redelivery events,
 * so it also holds when the route removes the {@code CamelRedelivered} header.
 * <p/>
 * The events come from many threads: the seconds are guarded by this recorder, the exchanges in flight are a concurrent
 * map.
 */
final class TimelineRecorder {

    static final int KEEP_FAILED = 10;
    static final int KEEP_SLOWEST = 3;
    static final int MAX_SENDS = 20;
    // an exchange that has not completed in that time is no longer followed
    private static final long EVICT_MILLIS = 5 * 60 * 1000L;
    // the counters of a sample that comes this late are spread over the seconds since the sample before
    private static final int MAX_SPREAD = 10;
    private static final Pattern STATUS_CODE = Pattern.compile("statusCode: ?(\\d{3})");

    /** The counters of a route, as {@link #sample(long, Map)} takes them. */
    record Counters(long completed, long failed, long handled, long inflight, long processingTime) {
    }

    /** A call an exchange made to a remote endpoint. */
    record Send(String endpointUri, long elapsed) {
    }

    /** An exchange that is kept, as it completed. */
    record Kept(String exchangeId, String routeId, String fromEndpointUri, long timestamp, long elapsed,
            boolean failed, boolean handled, boolean redelivered, String exception, String chaosRule,
            List<Send> sends) {
    }

    /** The kind of error that is counted per second. */
    record ErrorKind(String routeId, String exception, Integer statusCode, String chaosRule) {
    }

    /** What a route did in one second. */
    static final class RouteSecond {
        long completed;
        long recovered;
        long handled;
        long failed;
        long inflight;
        long attempts;
        long processingTime;
        long timed;
        long p95;
        long max;
        // the elapsed times of the exchanges that came in this route, until the second is closed
        long[] elapsed;
        int elapsedCount;

        void addElapsed(long millis) {
            if (elapsed == null) {
                elapsed = new long[8];
            } else if (elapsedCount == elapsed.length) {
                elapsed = Arrays.copyOf(elapsed, elapsedCount * 2);
            }
            elapsed[elapsedCount++] = millis;
        }

        void close() {
            if (elapsed != null) {
                long[] sorted = Arrays.copyOf(elapsed, elapsedCount);
                Arrays.sort(sorted);
                max = sorted[sorted.length - 1];
                p95 = sorted[Math.max(0, (int) Math.ceil(sorted.length * 0.95) - 1)];
                elapsed = null;
                elapsedCount = 0;
            }
        }

        long mean() {
            return timed > 0 ? processingTime / timed : 0;
        }
    }

    /** One second of the recording. */
    static final class Second {
        final long second;
        final Map<String, RouteSecond> routes = new HashMap<>();
        final Map<ErrorKind, long[]> errors = new LinkedHashMap<>();
        final List<Kept> failed = new ArrayList<>();
        final List<Kept> slowest = new ArrayList<>();
        // with camel-chaos: the faults of each rule, and the routes a rule had down
        Map<String, Long> faults;
        Set<String> down;
        long done;
        boolean closed;

        Second(long second) {
            this.second = second;
        }

        RouteSecond route(String routeId) {
            return routes.computeIfAbsent(routeId, k -> new RouteSecond());
        }

        void keep(Kept kept) {
            if (kept.failed() || kept.handled()) {
                if (failed.size() < KEEP_FAILED) {
                    failed.add(kept);
                }
                return;
            }
            int i = 0;
            while (i < slowest.size() && slowest.get(i).elapsed() >= kept.elapsed()) {
                i++;
            }
            if (i < KEEP_SLOWEST) {
                slowest.add(i, kept);
                if (slowest.size() > KEEP_SLOWEST) {
                    slowest.remove(KEEP_SLOWEST);
                }
            }
        }

        void close() {
            routes.values().forEach(RouteSecond::close);
            closed = true;
        }
    }

    /** An exchange in flight: the route it came in (once known), its calls, and the routes it was redelivered in. */
    static final class Inflight {
        final long created;
        volatile String routeId;
        final List<Send> sends = new ArrayList<>();
        Set<String> redeliveredIn;

        Inflight(long created, String routeId) {
            this.created = created;
            this.routeId = routeId;
        }

        void addSends(List<Send> more) {
            synchronized (this) {
                for (Send s : more) {
                    if (sends.size() < MAX_SENDS) {
                        sends.add(s);
                    }
                }
            }
        }
    }

    private final int seconds;
    private final Second[] ring;
    private final Map<String, Inflight> inflight = new ConcurrentHashMap<>();
    private final Map<String, Counters> lastCounters = new HashMap<>();
    // the text of each chaos rule seen, by its id
    private final Map<String, String> chaosRules = new LinkedHashMap<>();
    private long lastSampleSecond = -1;

    TimelineRecorder(int seconds) {
        this.seconds = seconds;
        // one more for the second that is going on
        this.ring = new Second[seconds + 1];
    }

    int getSeconds() {
        return seconds;
    }

    private Second slot(long second) {
        int i = (int) (second % ring.length);
        Second s = ring[i];
        if (s == null || s.second != second) {
            s = new Second(second);
            ring[i] = s;
        }
        return s;
    }

    private Second existing(long second) {
        if (second < 0) {
            return null;
        }
        Second s = ring[(int) (second % ring.length)];
        return s != null && s.second == second ? s : null;
    }

    // ---- the exchange events ----

    void exchangeCreated(String exchangeId, String routeId, long now) {
        inflight.put(exchangeId, new Inflight(now, routeId));
    }

    /**
     * The exchange was sent to an endpoint: a call when the endpoint is remote (endpointUri not null). It also tells
     * the route the exchange came in, when it was not known when it was created.
     */
    void exchangeSent(String exchangeId, String routeId, String endpointUri, long elapsed) {
        Inflight f = inflight.get(exchangeId);
        if (f != null) {
            if (f.routeId == null) {
                f.routeId = routeId;
            }
            if (endpointUri != null) {
                f.addSends(List.of(new Send(endpointUri, elapsed)));
            }
        }
    }

    void redelivery(String exchangeId, String routeId, long now) {
        if (routeId != null) {
            Inflight f = inflight.computeIfAbsent(exchangeId, k -> new Inflight(now, null));
            synchronized (f) {
                if (f.redeliveredIn == null) {
                    f.redeliveredIn = new LinkedHashSet<>();
                }
                f.redeliveredIn.add(routeId);
            }
            synchronized (this) {
                slot(now / 1000).route(routeId).attempts++;
            }
        }
    }

    /** An error handler handled a failure, or an exchange failed: it is counted as an error of its second. */
    void error(ErrorKind kind, long now) {
        synchronized (this) {
            slot(now / 1000).errors.computeIfAbsent(kind, k -> new long[1])[0]++;
        }
    }

    /**
     * An exchange is done.
     *
     * @param correlationId the exchange it is a copy of, if any
     * @param indexed       whether it is a part of a split or multicast (it has the index of the part)
     */
    void exchangeDone(
            String exchangeId, String routeId, String fromEndpointUri, long now, long elapsed, boolean failed,
            boolean handled, String exception, String chaosRule, String correlationId, boolean indexed) {
        // a copy done in the route of the exchange it was copied from (a part of a split or multicast, the copy of a
        // circuit breaker) is a part of that exchange: it is not kept and has no time of its own, its calls are the
        // calls of that exchange, and it counts as recovered where it was redelivered. A copy that a route of its own
        // consumed (seda, wire tap) is an exchange of that route.
        Inflight parent = correlationId != null && !correlationId.equals(exchangeId) ? inflight.get(correlationId) : null;
        boolean copy = correlationId != null && !correlationId.equals(exchangeId)
                && (indexed || parent != null && Objects.equals(parent.routeId, routeId));
        Inflight f = inflight.remove(exchangeId);
        Set<String> redeliveredIn = null;
        List<Send> sends = List.of();
        if (f != null) {
            synchronized (f) {
                redeliveredIn = f.redeliveredIn;
                sends = List.copyOf(f.sends);
            }
        }
        if (copy && parent != null && !sends.isEmpty()) {
            parent.addSends(sends);
        }
        synchronized (this) {
            Second s = slot(now / 1000);
            if (!failed && !handled && redeliveredIn != null) {
                for (String r : redeliveredIn) {
                    s.route(r).recovered++;
                }
            }
            if (copy || routeId == null) {
                return;
            }
            s.done++;
            if (!s.closed) {
                s.route(routeId).addElapsed(elapsed);
            }
            s.keep(new Kept(
                    exchangeId, routeId, fromEndpointUri, now - elapsed, elapsed, failed, handled,
                    redeliveredIn != null, exception, chaosRule, sends));
        }
    }

    // ---- the counters of the routes, once a second ----

    /**
     * Takes the counters of the routes at the given time. The rise since the sample before is what the routes did in
     * the seconds in between, and those seconds are closed. A sample that comes late (up to 10 seconds) spreads the
     * rise evenly over its seconds; a counter that was reset counts as no rise.
     */
    synchronized void sample(long now, Map<String, Counters> counters) {
        long second = now / 1000;
        if (lastSampleSecond >= 0 && second > lastSampleSecond) {
            int n = (int) Math.min(second - lastSampleSecond, MAX_SPREAD);
            long first = second - n;
            for (Map.Entry<String, Counters> e : counters.entrySet()) {
                Counters before = lastCounters.get(e.getKey());
                if (before == null) {
                    continue;
                }
                Counters c = e.getValue();
                long completed = rise(before.completed(), c.completed());
                long failed = rise(before.failed(), c.failed());
                long handled = rise(before.handled(), c.handled());
                long time = rise(before.processingTime(), c.processingTime());
                for (int i = 0; i < n; i++) {
                    RouteSecond rs = slot(first + i).route(e.getKey());
                    long done = share(completed, n, i);
                    rs.handled = share(handled, n, i);
                    rs.failed = share(failed, n, i);
                    rs.completed = Math.max(0, done - rs.handled - rs.recovered);
                    rs.processingTime = share(time, n, i);
                    rs.timed = done;
                    rs.inflight = c.inflight();
                }
            }
            for (int i = 0; i < n; i++) {
                slot(first + i).close();
            }
        }
        if (second != lastSampleSecond) {
            lastSampleSecond = second;
            lastCounters.clear();
            lastCounters.putAll(counters);
        }
        long cutoff = now - EVICT_MILLIS;
        inflight.values().removeIf(f -> f.created < cutoff);
    }

    /**
     * Takes the state of the chaos rules at the given time: the faults per second of each rule (the last seconds, the
     * last one the current second), and the routes a rule has down now. The faults replace the ones of those seconds,
     * as a rule counts the faults of a second until it is over; the routes down are the ones of the second before.
     *
     * @param faults the faults per second of each rule by its id, the oldest first and the current second last
     * @param rules  the text of each rule by its id
     * @param down   the routes a rule has down now
     */
    synchronized void chaos(long now, Map<String, long[]> faults, Map<String, String> rules, Set<String> down) {
        long second = now / 1000;
        chaosRules.putAll(rules);
        for (Map.Entry<String, long[]> e : faults.entrySet()) {
            long[] values = e.getValue();
            // the seconds that are over, at most the window
            int n = Math.min(values.length - 1, seconds);
            for (int i = 1; i <= n; i++) {
                long value = values[values.length - 1 - i];
                Second s = value > 0 ? slot(second - i) : existing(second - i);
                if (s == null) {
                    continue;
                }
                if (value > 0) {
                    if (s.faults == null) {
                        s.faults = new LinkedHashMap<>();
                    }
                    s.faults.put(e.getKey(), value);
                } else if (s.faults != null) {
                    s.faults.remove(e.getKey());
                }
            }
        }
        if (!down.isEmpty()) {
            Second s = slot(second - 1);
            if (s.down == null) {
                s.down = new LinkedHashSet<>();
            }
            s.down.addAll(down);
        }
    }

    Map<String, String> chaosRules() {
        return chaosRules;
    }

    private static long rise(long before, long now) {
        return Math.max(0, now - before);
    }

    /** The part i of n of a value, the remainder going to the last parts. */
    static long share(long value, int n, int i) {
        long part = value / n;
        long rest = value % n;
        return part + (i >= n - rest ? 1 : 0);
    }

    static ErrorKind errorKind(String routeId, Throwable cause, String chaosRule) {
        Integer status = null;
        if (cause.getMessage() != null) {
            Matcher m = STATUS_CODE.matcher(cause.getMessage());
            if (m.find()) {
                status = Integer.valueOf(m.group(1));
            }
        }
        return new ErrorKind(routeId, cause.getClass().getName(), status, chaosRule);
    }

    // ---- reading ----

    /** The last whole second, which ended at the last sample (or before now, when there is no sample yet). */
    synchronized long endSecond(long now) {
        return lastSampleSecond >= 0 ? lastSampleSecond - 1 : now / 1000 - 1;
    }

    /** The seconds from the oldest to the given end second (the last whole second), for at most the given count. */
    synchronized List<Second> seconds(long end, int count) {
        List<Second> answer = new ArrayList<>(count);
        for (long s = end - count + 1; s <= end; s++) {
            answer.add(existing(s));
        }
        return answer;
    }

    /** The routes that did anything in the given seconds, in the order they are first seen. */
    static Set<String> routes(List<Second> seconds, Predicate<String> filter) {
        Set<String> answer = new LinkedHashSet<>();
        for (Second s : seconds) {
            if (s != null) {
                s.routes.keySet().stream().filter(filter).sorted().forEach(answer::add);
            }
        }
        return answer;
    }

    int inflightCount() {
        return inflight.size();
    }
}
