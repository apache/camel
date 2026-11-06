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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What happened to an integration second by second, for the timeline screen (CAMEL-26691): the counters of each route
 * at each second, from which the exchanges per second that completed, were recovered by redelivery, were handled by the
 * error handler or failed are worked out; the errors per second by kind; and with camel-chaos, the faults of each rule
 * per second and the seconds a rule had a route down. It is recorded at every refresh of an integration, so the screen
 * opens on the last minutes, not on an empty chart.
 */
final class Timeline {

    /** The seconds kept. */
    static final int SECONDS = 300;
    // a gap between two samples of at most this many seconds (a slow refresh) is spread over its seconds
    private static final int SPREAD = 10;

    private static final int TOTAL = 0;
    private static final int FAILED = 1;
    private static final int HANDLED = 2;
    private static final int REDELIVERIES = 3;
    private static final int INFLIGHT = 4;
    private static final int MEAN = 5;
    private static final int ATTEMPTS = 6;

    private static final Pattern STATUS_CODE = Pattern.compile("statusCode: ?(\\d{3})");

    private final Map<String, Recording> byPid = new ConcurrentHashMap<>();

    /**
     * The exchanges of a route per second, oldest first; a second before the first sample is -1. The attempts are the
     * redeliveries of its processors (each attempt), which also show a recovery the route did not count: the route
     * counts an exchange done after a redelivery by the redelivered header, which a removeHeaders("Camel*") takes away.
     */
    record RouteSeries(String route, long[] completed, long[] recovered, long[] handled, long[] failed, long[] inflight,
            long[] mean, long[] down, long[] attempts) {

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

    private static final class Recording {
        // the counters of each route at the end of a second
        final TreeMap<Long, Map<String, long[]>> routes = new TreeMap<>();
        // the new errors per second, by their label
        final TreeMap<Long, Map<String, Long>> errors = new TreeMap<>();
        // the faults per second of each chaos rule, and the routes a rule had down
        final TreeMap<Long, Map<String, Long>> faults = new TreeMap<>();
        final TreeMap<Long, Set<String>> down = new TreeMap<>();
        final Map<String, String> ruleLabels = new LinkedHashMap<>();
        // the highest repeat count seen of each kind of error, and the newest time of its entries
        final Map<String, Long> repeats = new HashMap<>();
        final Map<String, Long> newest = new HashMap<>();
        boolean errorsSeen;

        void trim(long second) {
            long oldest = second - SECONDS;
            routes.headMap(oldest).clear();
            errors.headMap(oldest).clear();
            faults.headMap(oldest).clear();
            down.headMap(oldest).clear();
        }
    }

    /** Records the route counters and the chaos rules of an integration at a refresh. */
    void record(IntegrationInfo info, long now) {
        if (info == null || info.pid == null) {
            return;
        }
        long second = now / 1000;
        Recording rec = byPid.computeIfAbsent(info.pid, k -> new Recording());
        synchronized (rec) {
            Map<String, long[]> counters = new LinkedHashMap<>();
            for (RouteInfo r : info.routes) {
                if (r.routeId != null) {
                    long attempts = 0;
                    for (ProcessorInfo p : r.processors) {
                        attempts += p.redeliveries;
                    }
                    counters.put(r.routeId, new long[] {
                            r.total, r.failed, r.failuresHandled, r.redeliveries, r.inflight, r.meanTime, attempts });
                }
            }
            rec.routes.put(second, counters);
            if (info.chaos != null) {
                recordChaos(rec, info.chaos, second);
            }
            rec.trim(second);
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
            if (before != null && second - before.getKey() <= SPREAD && rec.routes.lowerKey(second) != null
                    && rec.routes.lowerKey(second) <= before.getKey()) {
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

    /**
     * Records the errors of an integration (the error registry, as the Errors tab reads it). An entry is one exchange
     * that failed, and its repeat count says how often its kind (route, node and exception) failed so far, so the new
     * failures of a kind are the rise of its count. The first time, the entries are counted at their own seconds. An
     * error a chaos rule made is left out: the faults of the rule are a layer of their own.
     */
    void recordErrors(String pid, List<ErrorInfo> errors, long now) {
        if (pid == null || errors == null) {
            return;
        }
        Recording rec = byPid.computeIfAbsent(pid, k -> new Recording());
        synchronized (rec) {
            Map<String, List<ErrorInfo>> byKind = new LinkedHashMap<>();
            for (ErrorInfo e : errors) {
                if (e.properties.get(ChaosMarks.PROPERTY) != null) {
                    continue;
                }
                byKind.computeIfAbsent(e.routeId + "|" + e.nodeId + "|" + e.exceptionType, k -> new ArrayList<>()).add(e);
            }
            for (Map.Entry<String, List<ErrorInfo>> en : byKind.entrySet()) {
                recordKind(rec, en.getKey(), en.getValue(), now);
            }
            rec.errorsSeen = true;
            rec.trim(now / 1000);
        }
    }

    private static void recordKind(Recording rec, String kind, List<ErrorInfo> entries, long now) {
        long count = 0;
        long newestTs = 0;
        for (ErrorInfo e : entries) {
            count = Math.max(count, e.repeatCount);
            newestTs = Math.max(newestTs, e.timestamp);
        }
        String label = errorLabel(entries.get(0));
        Long before = rec.repeats.get(kind);
        Long seenTs = rec.newest.get(kind);
        rec.repeats.put(kind, count);
        rec.newest.put(kind, newestTs);
        long oldest = now - SECONDS * 1000L;
        if (!rec.errorsSeen || before == null) {
            // what was there already: each entry kept at its own second, and the failures of the kind that are no
            // longer kept spread from its first failure to the oldest entry kept
            long oldestKept = Long.MAX_VALUE;
            long first = 0;
            for (ErrorInfo e : entries) {
                oldestKept = Math.min(oldestKept, e.timestamp);
                first = first == 0 ? e.repeatFirstTimestamp : Math.min(first, e.repeatFirstTimestamp);
                if (e.timestamp > oldest) {
                    addError(rec, e.timestamp / 1000, label, 1);
                }
            }
            long rest = count - entries.size();
            if (rest > 0 && first > 0 && first <= oldestKept) {
                long from = Math.max(first, oldest) / 1000;
                long to = oldestKept / 1000;
                long span = to - from + 1;
                // only the part of the spread inside the seconds kept
                long inside = first >= oldest ? rest : rest * span / Math.max(1, to - first / 1000 + 1);
                for (long t = from; t <= to; t++) {
                    long n = share(inside, span, t - from);
                    if (n > 0) {
                        addError(rec, t, label, n);
                    }
                }
            }
            return;
        }
        long rise = count >= before ? count - before : count;
        if (rise <= 0) {
            return;
        }
        // the new entries at their own seconds, and the rest of the rise (entries no longer kept) at the newest
        for (ErrorInfo e : entries) {
            if (rise > 1 && e.timestamp > oldest && (seenTs == null || e.timestamp > seenTs)
                    && e.timestamp != newestTs) {
                addError(rec, e.timestamp / 1000, label, 1);
                rise--;
            }
        }
        addError(rec, (newestTs > 0 ? newestTs : now) / 1000, label, rise);
    }

    private static void addError(Recording rec, long second, String label, long n) {
        rec.errors.computeIfAbsent(second, s -> new LinkedHashMap<>()).merge(label, n, Long::sum);
    }

    /** The label of an error in the legend: the simple name of the exception, and the HTTP status if it has one. */
    static String errorLabel(ErrorInfo e) {
        String type = e.exceptionType != null ? e.exceptionType : "Error";
        int dot = type.lastIndexOf('.');
        String label = dot >= 0 ? type.substring(dot + 1) : type;
        if (e.exceptionMessage != null) {
            Matcher m = STATUS_CODE.matcher(e.exceptionMessage);
            if (m.find()) {
                label += " " + m.group(1);
            }
        }
        return label;
    }

    /** The routes in the window that ends at the second, in the order of the integration. */
    List<RouteSeries> routes(String pid, List<String> order, long endSecond, int seconds) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return List.of();
        }
        synchronized (rec) {
            Set<String> names = new LinkedHashSet<>(order);
            rec.routes.values().forEach(m -> names.addAll(m.keySet()));
            List<RouteSeries> answer = new ArrayList<>();
            for (String name : names) {
                answer.add(series(rec, name, endSecond, seconds));
            }
            return answer;
        }
    }

    private static RouteSeries series(Recording rec, String route, long endSecond, int seconds) {
        long start = endSecond - seconds + 1;
        long[] completed = filled(seconds);
        long[] recovered = filled(seconds);
        long[] handled = filled(seconds);
        long[] failed = filled(seconds);
        long[] inflight = filled(seconds);
        long[] mean = filled(seconds);
        long[] attempts = filled(seconds);
        long[] down = new long[seconds];
        long prevSecond = -1;
        long[] prev = null;
        for (Map.Entry<Long, Map<String, long[]>> en : rec.routes.headMap(endSecond, true).entrySet()) {
            long[] cur = en.getValue().get(route);
            if (cur == null) {
                continue;
            }
            long s = en.getKey();
            if (s >= start) {
                inflight[(int) (s - start)] = cur[INFLIGHT];
            }
            if (prev != null && s >= start) {
                long dTotal = Math.max(0, cur[TOTAL] - prev[TOTAL]);
                long dFailed = Math.max(0, cur[FAILED] - prev[FAILED]);
                long dHandled = Math.max(0, cur[HANDLED] - prev[HANDLED]);
                long dRecovered = Math.max(0, cur[REDELIVERIES] - prev[REDELIVERIES]);
                long dOk = Math.max(0, dTotal - dFailed - dHandled - dRecovered);
                long dAttempts = Math.max(0, cur[ATTEMPTS] - prev[ATTEMPTS]);
                // the mean of the exchanges done since the last sample, from the means of all of them
                long dTime = cur[MEAN] * cur[TOTAL] - prev[MEAN] * prev[TOTAL];
                long m = dTotal > 0 ? Math.max(0, dTime / dTotal) : -1;
                long gap = s - prevSecond;
                long from = gap <= SPREAD ? prevSecond + 1 : s;
                long span = s - from + 1;
                for (long t = Math.max(from, start); t <= s; t++) {
                    int i = (int) (t - start);
                    // the later seconds of the gap take the remainder
                    long k = t - from;
                    completed[i] = share(dOk, span, k);
                    recovered[i] = share(dRecovered, span, k);
                    handled[i] = share(dHandled, span, k);
                    failed[i] = share(dFailed, span, k);
                    attempts[i] = share(dAttempts, span, k);
                    mean[i] = m;
                    if (t < s && inflight[i] < 0) {
                        inflight[i] = cur[INFLIGHT];
                    }
                }
            }
            prev = cur;
            prevSecond = s;
        }
        for (Map.Entry<Long, Set<String>> en : rec.down.subMap(start, true, endSecond, true).entrySet()) {
            if (en.getValue().contains(route)) {
                down[(int) (en.getKey() - start)] = 1;
            }
        }
        return new RouteSeries(route, completed, recovered, handled, failed, inflight, mean, down, attempts);
    }

    /** The k-th of n parts of a count: the parts add up to the count, the later ones a little bigger. */
    private static long share(long count, long n, long k) {
        long base = count / n;
        long rest = count % n;
        return base + (k >= n - rest ? 1 : 0);
    }

    private static long[] filled(int seconds) {
        long[] a = new long[seconds];
        Arrays.fill(a, -1);
        return a;
    }

    /**
     * The causes in the window: the errors per second by label, then the faults of each chaos rule. Each series is
     * oldest first. The errors are known only from the first time the Errors tab or the timeline read them.
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
            return answer;
        }
    }

    /** Whether the errors of the integration were read at least once, so an empty cause band means none. */
    boolean errorsSeen(String pid) {
        Recording rec = pid != null ? byPid.get(pid) : null;
        if (rec == null) {
            return false;
        }
        synchronized (rec) {
            return rec.errorsSeen;
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
