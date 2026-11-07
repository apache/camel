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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The chaos section of the status of an integration (the {@code chaos} dev console of camel-chaos): whether chaos is
 * on, the current rules with their live state, and the report (verdict, counts per route, recent faults). Parsed
 * leniently: a camel-chaos of another version may leave fields out, which then read as unknown (-1 or null).
 */
final class ChaosInfo {

    boolean enabled;
    // why chaos is off
    String reason;
    String verdict;
    boolean isFinal = true;
    long seed;
    boolean seedGiven;
    long lost;
    final List<String> reasons = new ArrayList<>();
    final List<Rule> rules = new ArrayList<>();
    final List<RouteCounts> routes = new ArrayList<>();
    final List<RecentFault> recentFaults = new ArrayList<>();

    /** A current rule, with its live state. */
    static final class Rule {
        String id;
        int version;
        String text;
        String state;
        long matches;
        long fired;
        // the faults it fired per hook, such as poll=39 (CAMEL-26699)
        final Map<String, Long> firedAt = new LinkedHashMap<>();
        long skipped;
        final Map<String, Long> skippedReasons = new LinkedHashMap<>();
        long lostAtDlq = -1;
        long endsIn = -1;
        long startsIn = -1;
        double rate = -1;
        double observedRate = -1;
        long nextFireIn = -1;
        long burstEndsIn = -1;
        long nextBurstIn = -1;
        long lastFiredAgo = -1;
        long faultsLastMinute;
        long[] faultsPerSecond = new long[0];
        // the effect of its faults; faulted is 0 until it faulted an exchange
        long faulted;
        long completed;
        long recovered;
        long handled;
        long failed;
        long inFlight;
        // the expectation, or null
        String expectRoute;
        String expectKind;
        String expectOp;
        long expectCount;
        long expectValue;
        String expectOutcome;
        long expectTimeLeft = -1;
        // what a stall or lifecycle rule did to its route, or null
        String lifecycleAction;
        String lifecycleRoute;
        String lifecycleState;
        int lifecycleRound;
        int lifecycleRepeat;
        long lifecycleActsIn = -1;
        long lifecycleBackIn = -1;
        String lifecycleReason;

        /**
         * The target of the rule as it is written, such as {@code endpoint=kafka:orders*}, with the route it is
         * narrowed to ({@code endpoint=http:* in=orders}).
         */
        String target() {
            String target = targetToken();
            for (String token : tokens()) {
                if (token.startsWith("in=")) {
                    return target.isEmpty() ? token : target + " " + token;
                }
            }
            return target;
        }

        private String targetToken() {
            for (String token : tokens()) {
                if (token.startsWith("endpoint=") || token.startsWith("component=") || token.startsWith("route=")
                        || token.startsWith("step=")) {
                    return token;
                }
            }
            return "";
        }

        /**
         * The fault of the rule with its parameters, as it is written, without the target and the options of the rule
         * (at, in, when, rate, until, from, expect, within), such as {@code error status=503}.
         */
        String fault() {
            StringBuilder sb = new StringBuilder();
            String target = targetToken();
            for (String token : tokens()) {
                int eq = token.indexOf('=');
                String key = eq > 0 ? token.substring(0, eq) : null;
                if (token.equals(target) || key != null && OPTIONS.contains(key)) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(token);
            }
            return sb.toString();
        }

        /**
         * The fault in a few words, as the report says it: {@code error status 503}, {@code error ConnectException},
         * {@code delay 500ms..1s}, {@code stop 5s ×3 every 20s}.
         */
        String faultShort() {
            List<String> tokens = new ArrayList<>(List.of(fault().split(" ")));
            if (tokens.isEmpty() || tokens.get(0).isEmpty()) {
                return "";
            }
            String kind = tokens.remove(0);
            Map<String, String> params = new LinkedHashMap<>();
            for (String token : tokens) {
                int eq = token.indexOf('=');
                params.put(eq > 0 ? token.substring(0, eq) : token, eq > 0 ? token.substring(eq + 1) : "");
            }
            StringBuilder sb = new StringBuilder();
            switch (kind) {
                case "error" -> {
                    sb.append("error");
                    String exception = params.get("exception");
                    if (params.containsKey("status")) {
                        sb.append(" status ").append(params.get("status"));
                    } else {
                        // the exception camel-chaos throws when the rule names none
                        String type = exception != null ? exception : "ChaosException";
                        sb.append(' ').append(type.substring(type.lastIndexOf('.') + 1));
                    }
                    if (params.containsKey("after")) {
                        sb.append(" after ").append(params.get("after"));
                    }
                }
                case "delay", "stall" -> sb.append(kind).append(' ').append(params.getOrDefault("duration", ""));
                case "corrupt" -> sb.append("corrupt ").append(params.getOrDefault("mode", ""))
                        .append(params.containsKey("header") ? " " + params.get("header") : "");
                case "lifecycle" -> {
                    sb.append(params.getOrDefault("action", "lifecycle")).append(' ')
                            .append(params.getOrDefault("duration", ""));
                    if (params.containsKey("repeat")) {
                        sb.append(" ×").append(params.get("repeat"));
                    }
                    if (params.containsKey("every")) {
                        sb.append(" every ").append(params.get("every"));
                    }
                }
                default -> {
                    return fault();
                }
            }
            return sb.toString().strip();
        }

        /** The faults it fired at the polls of a consumer: a poll rule faults no exchange. */
        long firedAtPoll() {
            return firedAt.getOrDefault("poll", 0L);
        }

        /** The options at, rate, until and from as they are written, for the RATE column. */
        String option(String name) {
            for (String token : tokens()) {
                if (token.startsWith(name + "=")) {
                    return token.substring(name.length() + 1);
                }
            }
            return null;
        }

        /** The rule split at the spaces that are not inside a quoted value. */
        List<String> tokens() {
            List<String> answer = new ArrayList<>();
            if (text == null) {
                return answer;
            }
            StringBuilder current = new StringBuilder();
            char quote = 0;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (quote != 0) {
                    current.append(ch);
                    if (ch == quote) {
                        quote = 0;
                    }
                } else if (ch == ' ' || ch == '\t') {
                    if (!current.isEmpty()) {
                        answer.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    if (ch == '"' || ch == '\'') {
                        quote = ch;
                    }
                    current.append(ch);
                }
            }
            if (!current.isEmpty()) {
                answer.add(current.toString());
            }
            return answer;
        }

        boolean isActive() {
            return "active".equals(state) || "waiting".equals(state);
        }
    }

    // the options of a rule besides its target and its fault
    private static final List<String> OPTIONS = List.of("at", "in", "when", "rate", "until", "from", "expect", "within");

    /** The counts of a route in the report. */
    record RouteCounts(String route, long completed, long failed, long failedKept, long handled, long redeliveries) {
    }

    /** A recent fault of a rule. */
    record RecentFault(long ago, String rule, String fault, String hook, String route, String where, String exchangeId) {
    }

    /** The active (and waiting) rules. */
    long activeRules() {
        return rules.stream().filter(Rule::isActive).count();
    }

    /** The faults of all rules in the last minute. */
    long faultsLastMinute() {
        return rules.stream().mapToLong(r -> r.faultsLastMinute).sum();
    }

    /** The current rule with the id, or null. */
    Rule rule(String id) {
        for (Rule r : rules) {
            if (r.id != null && r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Parses the chaos section of the status.
     *
     * @return the chaos info, or null when there is no section (the integration has no camel-chaos)
     */
    static ChaosInfo parse(JsonObject chaos) {
        if (chaos == null) {
            return null;
        }
        ChaosInfo info = new ChaosInfo();
        info.enabled = chaos.getBooleanOrDefault("enabled", false);
        info.reason = chaos.getString("reason");
        if (chaos.get("rules") instanceof JsonArray rules) {
            for (Object o : rules) {
                if (o instanceof JsonObject jo) {
                    info.rules.add(rule(jo));
                }
            }
        }
        if (chaos.get("report") instanceof JsonObject report) {
            info.verdict = report.getString("verdict");
            info.isFinal = report.getBooleanOrDefault("final", true);
            info.seed = num(report.get("seed"), 0);
            info.seedGiven = report.getBooleanOrDefault("seedGiven", false);
            info.lost = num(report.get("lost"), 0);
            if (report.get("reasons") instanceof JsonArray reasons) {
                reasons.forEach(r -> info.reasons.add(String.valueOf(r)));
            }
            if (report.get("routes") instanceof JsonArray routes) {
                for (Object o : routes) {
                    if (o instanceof JsonObject r) {
                        info.routes.add(new RouteCounts(
                                r.getString("route"), num(r.get("completed"), 0), num(r.get("failed"), 0),
                                num(r.get("failedKeptByConsumer"), 0), num(r.get("handled"), 0),
                                num(r.get("redeliveries"), 0)));
                    }
                }
            }
            if (report.get("recentFaults") instanceof JsonArray faults) {
                for (Object o : faults) {
                    if (o instanceof JsonObject f) {
                        String where = f.getString("endpoint") != null ? f.getString("endpoint") : f.getString("step");
                        info.recentFaults.add(new RecentFault(
                                num(f.get("ago"), -1), f.getString("rule"), f.getString("fault"), f.getString("hook"),
                                f.getString("route"), where, f.getString("exchangeId")));
                    }
                }
            }
        }
        return info;
    }

    private static Rule rule(JsonObject jo) {
        Rule r = new Rule();
        r.id = jo.getString("id");
        r.version = (int) num(jo.get("version"), 1);
        r.text = jo.getString("rule");
        r.state = jo.getString("state");
        r.matches = num(jo.get("matches"), 0);
        if (jo.get("fired") instanceof Map<?, ?> fired) {
            fired.forEach((k, v) -> {
                r.fired += num(v, 0);
                r.firedAt.put(String.valueOf(k), num(v, 0));
            });
        }
        r.skipped = num(jo.get("skipped"), 0);
        if (jo.get("skippedReasons") instanceof Map<?, ?> reasons) {
            reasons.forEach((k, v) -> r.skippedReasons.put(String.valueOf(k), num(v, 0)));
        }
        r.lostAtDlq = num(jo.get("lostAtDlq"), -1);
        r.endsIn = num(jo.get("endsIn"), -1);
        r.startsIn = num(jo.get("startsIn"), -1);
        r.rate = dbl(jo.get("rate"));
        r.observedRate = dbl(jo.get("observedRate"));
        r.nextFireIn = num(jo.get("nextFireIn"), -1);
        r.burstEndsIn = num(jo.get("burstEndsIn"), -1);
        r.nextBurstIn = num(jo.get("nextBurstIn"), -1);
        r.lastFiredAgo = num(jo.get("lastFiredAgo"), -1);
        r.faultsLastMinute = num(jo.get("faultsLastMinute"), 0);
        if (jo.get("faultsPerSecond") instanceof List<?> perSecond) {
            r.faultsPerSecond = new long[perSecond.size()];
            for (int i = 0; i < perSecond.size(); i++) {
                r.faultsPerSecond[i] = num(perSecond.get(i), 0);
            }
        }
        if (jo.get("effect") instanceof JsonObject e) {
            r.faulted = num(e.get("faulted"), 0);
            r.completed = num(e.get("completed"), 0);
            r.recovered = num(e.get("recovered"), 0);
            r.handled = num(e.get("handled"), 0);
            r.failed = num(e.get("failed"), 0);
            r.inFlight = num(e.get("inFlight"), 0);
        }
        if (jo.get("expectation") instanceof JsonObject e) {
            r.expectRoute = e.getString("route");
            r.expectKind = e.getStringOrDefault("kind", "completed");
            r.expectOp = e.getStringOrDefault("op", ">=");
            r.expectCount = num(e.get("count"), 0);
            r.expectValue = num(e.get("value") != null ? e.get("value") : e.get("completed"), 0);
            r.expectOutcome = e.getString("outcome");
            r.expectTimeLeft = num(e.get("timeLeft"), -1);
        }
        if (jo.get("lifecycle") instanceof JsonObject l) {
            r.lifecycleAction = l.getString("action");
            r.lifecycleRoute = l.getString("route");
            r.lifecycleState = l.getString("state");
            r.lifecycleRound = (int) num(l.get("round"), 0);
            r.lifecycleRepeat = (int) num(l.get("repeat"), 1);
            r.lifecycleActsIn = num(l.get("actsIn"), -1);
            r.lifecycleBackIn = num(l.get("backIn"), -1);
            r.lifecycleReason = l.getString("reason");
        }
        return r;
    }

    private static long num(Object value, long otherwise) {
        return value instanceof Number n ? n.longValue() : otherwise;
    }

    private static double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : -1;
    }
}
