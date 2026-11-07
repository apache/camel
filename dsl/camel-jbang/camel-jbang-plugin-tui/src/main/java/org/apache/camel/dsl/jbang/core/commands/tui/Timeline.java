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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.util.json.JsonObject;

/**
 * What happened to an integration second by second, for the timeline screen (CAMEL-26691). The integration records it
 * itself (the timeline dev console): per second, what each route did (completed, recovered by a redelivery, handled by
 * the error handler, failed), its mean, p95 and max time, the exchanges in flight and the redelivery attempts; the
 * errors by route and exception; and the failed and handled exchanges and the 3 slowest with their calls. The screen
 * reads all of it once and then the last seconds at each refresh, and keeps it here. With camel-chaos, the faults of
 * each rule per second and the seconds a rule had a route down come from the chaos rules in the status.
 */
final class Timeline {

    /** The seconds kept. */
    static final int SECONDS = 300;
    // a gap between two samples of the chaos rules of at most this many seconds is filled in
    private static final int SPREAD = 10;

    private static final int COMPLETED = 0;
    private static final int RECOVERED = 1;
    private static final int HANDLED = 2;
    private static final int FAILED = 3;
    private static final int INFLIGHT = 4;
    private static final int MEAN = 5;
    private static final int P95 = 6;
    private static final int MAX = 7;
    private static final int ATTEMPTS = 8;
    private static final String[] KEYS = {
            "completed", "recovered", "handled", "failed", "inflight", "meanMillis", "p95Millis", "maxMillis",
            "redeliveryAttempts" };

    private final Map<String, Recording> byPid = new ConcurrentHashMap<>();

    /**
     * The exchanges of a route per second, oldest first; a second the integration did not report is -1. A recovered
     * exchange completed after a redelivery in this route; the attempts are the redeliveries tried in it.
     */
    record RouteSeries(String route, long[] completed, long[] recovered, long[] handled, long[] failed, long[] inflight,
            long[] mean, long[] p95, long[] max, long[] down, long[] attempts, String[] events, long[] open) {

        /** The seconds a circuit breaker of the route was open (not half open). */
        long openSeconds() {
            long n = 0;
            for (long v : open) {
                if (v >= 2) {
                    n++;
                }
            }
            return n;
        }

        long total(long[] values) {
            long sum = 0;
            for (long v : values) {
                sum += Math.max(0, v);
            }
            return sum;
        }

        /** Whether nothing happened in the window: no exchange, none in flight, and the route never down. */
        boolean quiet() {
            return total(completed) + total(recovered) + total(handled) + total(failed) + total(inflight)
                   + total(down) == 0;
        }
    }

    /** One kind of cause per second, oldest first: an error kind, or the faults of a chaos rule. */
    record CauseSeries(String label, String rule, long[] values) {
    }

    /** A call an exchange made. */
    record Call(String endpointUri, long elapsed) {
    }

    /** An exchange the integration kept of a second: a failed one, or one of the slowest. */
    record Kept(String exchangeId, String routeId, String fromEndpointUri, long timestamp, long elapsed, boolean failed,
            boolean handled, boolean redelivered, String exception, String chaosRule, List<Call> calls) {

        /** The second the exchange was done in. */
        long second() {
            return (timestamp + elapsed) / 1000;
        }

        String outcome() {
            return failed ? "failed" : handled ? "handled" : redelivered ? "recovered" : "completed";
        }

        Kept withChaosRule(String rule) {
            return rule == null
                    ? this
                    : new Kept(
                            exchangeId, routeId, fromEndpointUri, timestamp, elapsed, failed, handled, redelivered,
                            exception, rule, calls);
        }
    }

    private static final class Recording {
        // the values of each route per second (see KEYS)
        final TreeMap<Long, Map<String, long[]>> routes = new TreeMap<>();
        // the errors per second, by their label (the errors a chaos rule made are the layer of the rule)
        final TreeMap<Long, Map<String, Long>> errors = new TreeMap<>();
        // the exchanges kept per second, and the exchanges done per second in the routes they came in
        final TreeMap<Long, List<Kept>> exchanges = new TreeMap<>();
        final TreeMap<Long, Long> done = new TreeMap<>();
        // what happened to the routes per second: started, stopped, reloaded
        final TreeMap<Long, Map<String, String>> routeEvents = new TreeMap<>();
        // the faults per second of each chaos rule, and the routes a rule had down
        final TreeMap<Long, Map<String, Long>> faults = new TreeMap<>();
        final TreeMap<Long, Set<String>> down = new TreeMap<>();
        // the routes with a circuit breaker not closed per second: 2 open, 1 half open
        final TreeMap<Long, Map<String, Integer>> open = new TreeMap<>();
        // the WARN and ERROR lines of the log per second, as the TUI read them from the log file
        final TreeMap<Long, long[]> logs = new TreeMap<>();
        final Map<String, String> ruleLabels = new LinkedHashMap<>();
        // the last second the integration reported, and whether all of its seconds were read
        long end = -1;
        boolean full;
        // whether the integration records the chaos rules itself (else the TUI samples their state)
        boolean chaosRecorded;

        void trim(long second) {
            long oldest = second - SECONDS;
            routes.headMap(oldest).clear();
            errors.headMap(oldest).clear();
            exchanges.headMap(oldest).clear();
            done.headMap(oldest).clear();
            routeEvents.headMap(oldest).clear();
            faults.headMap(oldest).clear();
            down.headMap(oldest).clear();
            open.headMap(oldest).clear();
            logs.headMap(oldest).clear();
        }
    }

    /**
     * Takes the timeline the integration reported (the timeline dev console): its seconds replace the ones kept.
     *
     * @param full whether it is all the seconds the integration has, not only the last ones
     */
    void merge(String pid, JsonObject timeline, boolean full) {
        if (pid == null || timeline == null || !(timeline.get("routes") instanceof List<?>)) {
            return;
        }
        long end = timeline.getLongOrDefault("end", -1);
        int n = timeline.getIntegerOrDefault("seconds", 0);
        if (end < 0 || n <= 0) {
            return;
        }
        long start = end - n + 1;
        Recording rec = byPid.computeIfAbsent(pid, k -> new Recording());
        synchronized (rec) {
            rec.routes.subMap(start, true, end, true).clear();
            rec.errors.subMap(start, true, end, true).clear();
            for (Object o : (List<?>) timeline.get("routes")) {
                if (o instanceof JsonObject r && r.getString("routeId") != null) {
                    long[][] values = new long[KEYS.length][];
                    for (int k = 0; k < KEYS.length; k++) {
                        values[k] = values(r, KEYS[k], n);
                    }
                    for (int i = 0; i < n; i++) {
                        long[] at = new long[KEYS.length];
                        for (int k = 0; k < KEYS.length; k++) {
                            at[k] = values[k][i];
                        }
                        rec.routes.computeIfAbsent(start + i, s -> new LinkedHashMap<>()).put(r.getString("routeId"), at);
                    }
                }
            }
            if (timeline.get("errors") instanceof List<?> errors) {
                for (Object o : errors) {
                    if (o instanceof JsonObject e && e.getString("chaosRule") == null) {
                        String label = errorLabel(e.getString("exception"), e.getInteger("statusCode"));
                        long[] counts = values(e, "counts", n);
                        for (int i = 0; i < n; i++) {
                            if (counts[i] > 0) {
                                rec.errors.computeIfAbsent(start + i, s -> new LinkedHashMap<>())
                                        .merge(label, counts[i], Long::sum);
                            }
                        }
                    }
                }
            }
            // with camel-chaos the routes have their down seconds: the integration records the chaos rules
            boolean chaosRecorded = ((List<?>) timeline.get("routes")).stream()
                    .anyMatch(o -> o instanceof JsonObject r && r.get("down") instanceof List<?>);
            if (chaosRecorded) {
                // the faults of the chaos rules and the routes they had down, as the integration recorded them
                rec.faults.subMap(start, true, end, true).clear();
                rec.down.subMap(start, true, end, true).clear();
                List<?> chaos = timeline.get("chaos") instanceof List<?> l ? l : List.of();
                for (Object o : chaos) {
                    if (o instanceof JsonObject c && c.getString("id") != null) {
                        String id = c.getString("id");
                        if (c.getString("rule") != null) {
                            rec.ruleLabels.putIfAbsent(id, ruleLabel(id, c.getString("rule")));
                        }
                        long[] faults = values(c, "faults", n);
                        for (int i = 0; i < n; i++) {
                            if (faults[i] > 0) {
                                rec.faults.computeIfAbsent(start + i, s -> new LinkedHashMap<>()).put(id, faults[i]);
                            }
                        }
                    }
                }
                for (Object o : (List<?>) timeline.get("routes")) {
                    if (o instanceof JsonObject r && r.get("down") instanceof List<?>) {
                        long[] down = values(r, "down", n);
                        for (int i = 0; i < n; i++) {
                            if (down[i] > 0) {
                                rec.down.computeIfAbsent(start + i, s -> new LinkedHashSet<>()).add(r.getString("routeId"));
                            }
                        }
                    }
                }
                rec.chaosRecorded = true;
            }
            rec.open.subMap(start, true, end, true).clear();
            if (timeline.get("circuitBreakers") instanceof List<?> breakers) {
                for (Object o : breakers) {
                    if (o instanceof JsonObject cb && cb.getString("routeId") != null) {
                        long[] states = values(cb, "states", n);
                        for (int i = 0; i < n; i++) {
                            if (states[i] > 0) {
                                rec.open.computeIfAbsent(start + i, s -> new LinkedHashMap<>())
                                        .merge(cb.getString("routeId"), (int) states[i], Math::max);
                            }
                        }
                    }
                }
            }
            rec.routeEvents.subMap(start, true, end, true).clear();
            if (timeline.get("routeEvents") instanceof List<?> events) {
                for (Object o : events) {
                    if (o instanceof JsonObject e && e.getString("routeId") != null) {
                        rec.routeEvents.computeIfAbsent(e.getLongOrDefault("second", 0), s -> new LinkedHashMap<>())
                                .put(e.getString("routeId"), e.getString("event"));
                    }
                }
            }
            long[] done = values(timeline, "exchangesDone", n);
            for (int i = 0; i < n; i++) {
                rec.done.put(start + i, Math.max(0, done[i]));
            }
            if (timeline.get("exchanges") instanceof List<?> kept) {
                rec.exchanges.subMap(start, true, end, true).clear();
                for (Object o : kept) {
                    if (o instanceof JsonObject e) {
                        Kept k = kept(e);
                        rec.exchanges.computeIfAbsent(k.second(), s -> new ArrayList<>()).add(k);
                    }
                }
            }
            if (full) {
                rec.full = true;
            } else if (rec.end >= 0 && start > rec.end + 1) {
                // seconds were missed (the integration was not the selected one): read all of it again
                rec.full = false;
            }
            rec.end = Math.max(rec.end, end);
            rec.trim(rec.end);
        }
    }

    private static Kept kept(JsonObject e) {
        List<Call> calls = new ArrayList<>();
        if (e.get("endpointSends") instanceof List<?> sends) {
            for (Object o : sends) {
                if (o instanceof JsonObject s) {
                    calls.add(new Call(s.getString("endpointUri"), s.getLongOrDefault("elapsed", 0)));
                }
            }
        }
        return new Kept(
                e.getString("exchangeId"), e.getString("routeId"), e.getString("fromEndpointUri"),
                e.getLongOrDefault("timestamp", 0), e.getLongOrDefault("elapsed", 0), e.getBooleanOrDefault("failed", false),
                e.getBooleanOrDefault("handled", false), e.getBooleanOrDefault("redelivered", false),
                e.getString("exception"), e.getString("chaosRule"), List.copyOf(calls));
    }

    private static long[] values(JsonObject o, String key, int n) {
        long[] answer = new long[n];
        if (o.get(key) instanceof List<?> list) {
            for (int i = 0; i < n && i < list.size(); i++) {
                answer[i] = list.get(i) instanceof Number num ? num.longValue() : 0;
            }
        }
        return answer;
    }

    /** Records the chaos rules of an integration at a refresh: the faults per second, and the routes they have down. */
    void record(IntegrationInfo info, long now) {
        if (info == null || info.pid == null || info.chaos == null) {
            return;
        }
        long second = now / 1000;
        Recording rec = byPid.computeIfAbsent(info.pid, k -> new Recording());
        synchronized (rec) {
            if (rec.chaosRecorded) {
                // the integration records the faults and the routes down: only the labels of the rules from here
                for (ChaosInfo.Rule r : info.chaos.rules) {
                    if (r.id != null) {
                        rec.ruleLabels.put(r.id, ruleLabel(r));
                    }
                }
                return;
            }
            recordChaos(rec, info.chaos, second);
            rec.trim(Math.max(second, rec.end));
        }
    }

    private static void recordChaos(Recording rec, ChaosInfo chaos, long second) {
        Set<String> down = new LinkedHashSet<>();
        for (ChaosInfo.Rule r : chaos.rules) {
            if (r.id == null) {
                continue;
            }
            rec.ruleLabels.put(r.id, ruleLabel(r));
            // the last value is the current second of the integration
            long[] perSecond = r.faultsPerSecond;
            for (int k = 0; k < perSecond.length; k++) {
                long s = second - (perSecond.length - 1) + k;
                Map<String, Long> at = rec.faults.computeIfAbsent(s, x -> new LinkedHashMap<>());
                if (perSecond[k] > 0) {
                    at.put(r.id, perSecond[k]);
                } else {
                    at.remove(r.id);
                }
            }
            if ("down".equals(r.lifecycleState) && r.lifecycleRoute != null) {
                down.add(r.lifecycleRoute);
            }
        }
        if (!down.isEmpty()) {
            rec.down.put(second, down);
            // a route down at the last sample too was down in the seconds between (a status not written each second)
            Map.Entry<Long, Set<String>> before = rec.down.lowerEntry(second);
            if (before != null && second - before.getKey() <= SPREAD) {
                for (long s = before.getKey() + 1; s < second; s++) {
                    for (String route : down) {
                        if (before.getValue().contains(route)) {
                            rec.down.computeIfAbsent(s, x -> new LinkedHashSet<>()).add(route);
                        }
                    }
                }
            }
        } else {
            rec.down.remove(second);
        }
    }

    /** A short label of a chaos rule from its text, when the status no longer has the rule: r1 error (stock). */
    static String ruleLabel(String id, String rule) {
        String fault = "";
        String where = "";
        for (String part : rule.split(" ")) {
            int eq = part.indexOf('=');
            if (eq < 0 && fault.isEmpty()) {
                fault = part;
            } else if (where.isEmpty() && eq > 0
                    && List.of("endpoint", "route", "step", "component", "consumer").contains(part.substring(0, eq))) {
                where = part.substring(eq + 1);
            }
        }
        int colon = where.lastIndexOf(':');
        if (colon >= 0 && colon < where.length() - 1) {
            where = where.substring(colon + 1);
        }
        where = where.replace("*", "").replaceAll("^/+", "");
        int slash = where.lastIndexOf('/');
        if (slash >= 0 && slash < where.length() - 1) {
            where = where.substring(slash + 1);
        }
        return id + (fault.isEmpty() ? "" : " " + fault) + (where.isEmpty() ? "" : " (" + where + ")");
    }

    /** A short label of a chaos rule for the legend: r1 error 503 (stock). */
    static String ruleLabel(ChaosInfo.Rule r) {
        String fault = r.faultShort();
        String target = ChaosTab.target(r);
        int eq = target.indexOf('=');
        String where = eq > 0 ? target.substring(eq + 1) : target;
        int colon = where.lastIndexOf(':');
        if (colon >= 0 && colon < where.length() - 1) {
            where = where.substring(colon + 1);
        }
        where = where.replace("*", "").replaceAll("^/+", "");
        int slash = where.lastIndexOf('/');
        if (slash >= 0 && slash < where.length() - 1) {
            where = where.substring(slash + 1);
        }
        return r.id + " " + fault + (where.isEmpty() ? "" : " (" + where + ")");
    }

    /** The label of an error in the legend: the simple name of the exception, and the HTTP status if it has one. */
    static String errorLabel(String exception, Integer statusCode) {
        String type = exception != null ? exception : "Error";
        int dot = type.lastIndexOf('.');
        String label = dot >= 0 ? type.substring(dot + 1) : type;
        return statusCode != null ? label + " " + statusCode : label;
    }

    /** The routes in the window that ends at the second, in the order of the integration. */
    List<RouteSeries> routes(String pid, List<String> order, long endSecond, int seconds) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return List.of();
        }
        synchronized (rec) {
            Set<String> names = new LinkedHashSet<>(order);
            rec.routes.subMap(endSecond - seconds + 1, true, endSecond, true).values()
                    .forEach(m -> names.addAll(m.keySet()));
            List<RouteSeries> answer = new ArrayList<>();
            for (String name : names) {
                answer.add(series(rec, name, endSecond, seconds));
            }
            return answer;
        }
    }

    private static RouteSeries series(Recording rec, String route, long endSecond, int seconds) {
        long start = endSecond - seconds + 1;
        long[][] values = new long[KEYS.length][];
        for (int k = 0; k < KEYS.length; k++) {
            values[k] = filled(seconds);
        }
        long[] down = new long[seconds];
        for (Map.Entry<Long, Map<String, long[]>> en : rec.routes.subMap(start, true, endSecond, true).entrySet()) {
            long[] at = en.getValue().get(route);
            int i = (int) (en.getKey() - start);
            for (int k = 0; k < KEYS.length; k++) {
                // a second the integration reported without this route: the route did nothing
                values[k][i] = at != null ? at[k] : 0;
            }
        }
        for (Map.Entry<Long, Set<String>> en : rec.down.subMap(start, true, endSecond, true).entrySet()) {
            if (en.getValue().contains(route)) {
                down[(int) (en.getKey() - start)] = 1;
            }
        }
        long[] open = new long[seconds];
        for (Map.Entry<Long, Map<String, Integer>> en : rec.open.subMap(start, true, endSecond, true).entrySet()) {
            Integer state = en.getValue().get(route);
            if (state != null) {
                open[(int) (en.getKey() - start)] = state;
            }
        }
        // what happened to the route: started, stopped, reloaded, removed, restartFailed
        String[] events = new String[seconds];
        for (Map.Entry<Long, Map<String, String>> en : rec.routeEvents.subMap(start, true, endSecond, true).entrySet()) {
            String event = en.getValue().get(route);
            if (event != null) {
                events[(int) (en.getKey() - start)] = event;
            }
        }
        return new RouteSeries(
                route, values[COMPLETED], values[RECOVERED], values[HANDLED], values[FAILED],
                values[INFLIGHT], values[MEAN], values[P95], values[MAX], down, values[ATTEMPTS], events, open);
    }

    private static long[] filled(int seconds) {
        long[] a = new long[seconds];
        Arrays.fill(a, -1);
        return a;
    }

    /** A WARN or ERROR line in the log of the integration, at the second of its time. */
    void logLine(String pid, long second, boolean error) {
        Recording rec = byPid.computeIfAbsent(pid, k -> new Recording());
        synchronized (rec) {
            if (rec.end < 0 || second > rec.end - SECONDS) {
                rec.logs.computeIfAbsent(second, s -> new long[2])[error ? 1 : 0]++;
            }
        }
    }

    /**
     * The causes in the window: the errors per second by label, then the faults of each chaos rule. Each series is
     * oldest first.
     */
    List<CauseSeries> causes(String pid, long endSecond, int seconds) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return List.of();
        }
        long start = endSecond - seconds + 1;
        synchronized (rec) {
            Map<String, long[]> errors = new LinkedHashMap<>();
            for (Map.Entry<Long, Map<String, Long>> en : rec.errors.subMap(start, true, endSecond, true).entrySet()) {
                int i = (int) (en.getKey() - start);
                en.getValue().forEach((label, n) -> errors.computeIfAbsent(label, l -> new long[seconds])[i] += n);
            }
            Map<String, long[]> faults = new LinkedHashMap<>();
            for (Map.Entry<Long, Map<String, Long>> en : rec.faults.subMap(start, true, endSecond, true).entrySet()) {
                int i = (int) (en.getKey() - start);
                en.getValue().forEach((rule, n) -> faults.computeIfAbsent(rule, r -> new long[seconds])[i] += n);
            }
            List<CauseSeries> answer = new ArrayList<>();
            errors.forEach((label, values) -> answer.add(new CauseSeries(label, null, values)));
            faults.forEach((rule, values) -> answer
                    .add(new CauseSeries(rec.ruleLabels.getOrDefault(rule, rule), rule, values)));
            if (answer.isEmpty()) {
                // nothing failed in an exchange: what the log said went wrong (an exception there is logged too)
                long[] warn = new long[seconds];
                long[] error = new long[seconds];
                for (Map.Entry<Long, long[]> en : rec.logs.subMap(start, true, endSecond, true).entrySet()) {
                    int i = (int) (en.getKey() - start);
                    warn[i] = en.getValue()[0];
                    error[i] = en.getValue()[1];
                }
                if (Arrays.stream(error).sum() > 0) {
                    answer.add(new CauseSeries("log ERROR", null, error));
                }
                if (Arrays.stream(warn).sum() > 0) {
                    answer.add(new CauseSeries("log WARN", null, warn));
                }
            }
            return answer;
        }
    }

    /** The exchanges the integration kept of the seconds from and to (inclusive), oldest first. */
    List<Kept> exchanges(String pid, long from, long to) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return List.of();
        }
        synchronized (rec) {
            List<Kept> answer = new ArrayList<>();
            rec.exchanges.subMap(from, true, to, true).values().forEach(answer::addAll);
            answer.sort((a, b) -> Long.compare(a.timestamp() + a.elapsed(), b.timestamp() + b.elapsed()));
            return answer;
        }
    }

    /** The exchanges done in the seconds from and to (inclusive), in the routes they came in. */
    long done(String pid, long from, long to) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return 0;
        }
        synchronized (rec) {
            return rec.done.subMap(from, true, to, true).values().stream().mapToLong(Long::longValue).sum();
        }
    }

    /** The last second an exchange was done in, or -1 when none in the seconds kept. */
    long lastActive(String pid) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return -1;
        }
        synchronized (rec) {
            for (Map.Entry<Long, Long> e : rec.done.descendingMap().entrySet()) {
                if (e.getValue() > 0) {
                    return e.getKey();
                }
            }
            return -1;
        }
    }

    /** Whether the integration reported its timeline, so an empty cause band means no errors. */
    boolean errorsSeen(String pid) {
        return endSecond(pid) >= 0;
    }

    /** Whether all the seconds the integration has were read, not only the last ones. */
    boolean isFull(String pid) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return false;
        }
        synchronized (rec) {
            return rec.full;
        }
    }

    /** The last second the integration reported, or -1. */
    long endSecond(String pid) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return -1;
        }
        synchronized (rec) {
            return rec.end;
        }
    }

    /** The seconds recorded of the integration, the oldest first: how far back the screen can go. */
    long firstSecond(String pid) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return -1;
        }
        synchronized (rec) {
            return rec.routes.isEmpty() ? -1 : rec.routes.firstKey();
        }
    }

    void retain(Set<String> pids) {
        byPid.keySet().retainAll(pids);
    }
}
