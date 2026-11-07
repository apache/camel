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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.camel.component.chaos.ChaosManager.RouteCounts;
import org.apache.camel.component.chaos.ChaosRule.Action;
import org.apache.camel.component.chaos.ChaosRule.Expectation.Outcome;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.State;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * What the chaos rules did, as of the time the report was made: the seed, each rule with its matches and the faults
 * that fired, the counts per route, the expectations and the verdict.
 * <p/>
 * Without an expectation the verdict is PASS, as chaos makes exchanges fail on purpose. An expectation that is unmet
 * (its route did not complete that many exchanges in time) makes it FAIL, and counts messages as lost; so does a rule
 * with {@code at=dlq} that failed or dropped the send of the dead letter channel, which then handled the exchange
 * without delivering it. An expectation that is not decided yet is pending, and so is the report: a PASS with a pending
 * expectation is not final ({@link #isFinal()}). An expectation whose rule was removed or changed, or whose context
 * stopped, before it was decided is cancelled: it is neither met nor unmet, and does not keep the report pending.
 * <p/>
 * A route counts each exchange that goes through it once, with how the route was done with it the last time (an
 * exchange can go through a route more than once, such as a {@code direct} route called in a loop): a message that
 * fails and is read again by its consumer is one failed and one completed exchange. A failed exchange is "kept by the
 * consumer" when the consumer of the route reads the message again (for example a file consumer without
 * {@code moveFailed}).
 *
 * @since 4.24
 */
public final class ChaosReport {

    /** The verdict when no expectation is unmet. */
    public static final String PASS = "PASS";
    /** The verdict when an expectation is unmet. */
    public static final String FAIL = "FAIL";

    private final long seed;
    private final boolean seedGiven;
    // in the order of their ids, and the versions of an id from the latest
    private final List<RuleEntry> rules = new ArrayList<>();
    private final List<RouteEntry> routes = new ArrayList<>();
    private final List<String> reasons = new ArrayList<>();
    private final long lost;
    private final boolean pending;
    private final long now;
    // the last faults, the latest first
    private final List<ChaosRecentFaults.Entry> recentFaults;
    // what has been tried per route, and what not yet (CAMEL-26728)
    private final List<ChaosAdvisor.RouteTried> tried;

    ChaosReport(long seed, boolean seedGiven, long now, List<ChaosRule> current, List<ChaosRule> removed,
                Map<String, RouteCounts> counts, int evictedUnmet, long evictedLost, int evictedPending,
                long evictedLostAtDlq, List<ChaosRecentFaults.Entry> recentFaults, ChaosTargets.Reach reach) {
        this(seed, seedGiven, now, current, removed, counts, evictedUnmet, evictedLost, evictedPending, evictedLostAtDlq,
             recentFaults, reach, List.of());
    }

    ChaosReport(long seed, boolean seedGiven, long now, List<ChaosRule> current, List<ChaosRule> removed,
                Map<String, RouteCounts> counts, int evictedUnmet, long evictedLost, int evictedPending,
                long evictedLostAtDlq, List<ChaosRecentFaults.Entry> recentFaults, ChaosTargets.Reach reach,
                List<ChaosAdvisor.RouteTried> tried) {
        this.tried = List.copyOf(tried);
        this.seed = seed;
        this.seedGiven = seedGiven;
        this.now = now;
        this.recentFaults = List.copyOf(recentFaults);
        long lostCount = 0;
        boolean anyPending = false;
        for (ChaosRule rule : current) {
            rules.add(new RuleEntry(rule, now, true, reach));
        }
        for (ChaosRule rule : removed) {
            rules.add(new RuleEntry(rule, now, false, reach));
        }
        rules.sort(Comparator.comparingInt((RuleEntry r) -> idNumber(r.id))
                .thenComparing(Comparator.comparingInt((RuleEntry r) -> r.version).reversed()));
        for (RuleEntry entry : rules) {
            if (entry.lostAtDlq > 0) {
                lostCount += entry.lostAtDlq;
                reasons.add("rule " + entry.id + " failed or dropped " + entry.lostAtDlq
                            + (entry.lostAtDlq == 1 ? " send" : " sends") + " of the dead letter channel, which handled"
                            + (entry.lostAtDlq == 1 ? " it without delivering it: " : " them without delivering them: ")
                            + entry.lostAtDlq + " lost");
            }
            ChaosRule rule = entry.rule;
            if (entry.outcome == Outcome.UNMET) {
                ChaosRule.Expectation e = rule.expectation;
                lostCount += entry.lost;
                if (e.kind == ChaosRule.Expectation.Kind.COMPLETED && e.op == ChaosRule.Expectation.Op.AT_LEAST) {
                    reasons.add("route " + e.route + " completed " + entry.value + " of " + e.count + " exchanges within "
                                + ChaosRuleParser.printTime(rule.within) + " (rule " + rule.id + "): " + entry.lost
                                + " lost");
                } else if (e.kind.time() || e.kind == ChaosRule.Expectation.Kind.LOST) {
                    // a time, or the messages lost between two routes (CAMEL-26726)
                    String limit = e.kind.time() ? ChaosRuleParser.printTime(e.count) : Long.toString(e.count);
                    reasons.add((e.toRoute != null ? "routes " + e.route + "->" + e.toRoute : "route " + e.route) + ": "
                                + entry.valueText + " within " + ChaosRuleParser.printTime(rule.within) + ", "
                                + e.op.words + " " + limit + " expected (rule " + rule.id + ")"
                                + (entry.lost > 0 ? ": " + entry.lost + " lost" : ""));
                } else {
                    reasons.add("route " + e.route + ": " + entry.value + " " + e.kind.words(entry.value) + " within "
                                + ChaosRuleParser.printTime(rule.within) + ", " + e.op.words + " " + e.count
                                + " expected (rule " + rule.id + ")" + (entry.lost > 0 ? ": " + entry.lost + " lost" : ""));
                }
            } else if (entry.outcome == Outcome.PENDING) {
                anyPending = true;
            }
        }
        if (evictedUnmet > 0) {
            lostCount += evictedLost;
            reasons.add(evictedUnmet + " unmet expectations of rules that are no longer in the report: " + evictedLost
                        + " lost");
        }
        if (evictedLostAtDlq > 0) {
            lostCount += evictedLostAtDlq;
            reasons.add("rules that are no longer in the report kept messages from the dead letter channel: "
                        + evictedLostAtDlq + " lost");
        }
        counts.forEach((route, c) -> routes.add(new RouteEntry(route, c)));
        this.lost = lostCount;
        this.pending = anyPending || evictedPending > 0;
    }

    // the number of an id such as r12, for the order of the rules
    private static int idNumber(String id) {
        try {
            return Integer.parseInt(id.substring(1));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * The verdict: {@link #FAIL} when an expectation is unmet, or a message was lost at the dead letter channel;
     * {@link #PASS} otherwise.
     */
    public String getVerdict() {
        return reasons.isEmpty() ? PASS : FAIL;
    }

    /**
     * Whether the verdict is final: false while an expectation is pending, as it can still become unmet (or met).
     */
    public boolean isFinal() {
        return !pending;
    }

    /**
     * The reasons of a {@link #FAIL} verdict, one per unmet expectation and per rule that lost messages at the dead
     * letter channel.
     */
    public List<String> getReasons() {
        return Collections.unmodifiableList(reasons);
    }

    /**
     * The messages counted as lost: the count of each unmet expectation minus the exchanges its route completed in
     * time, and the messages that a rule with {@code at=dlq} kept from reaching the dead letter channel.
     */
    public long getLost() {
        return lost;
    }

    /**
     * The seed of the random draws of the rules.
     */
    public long getSeed() {
        return seed;
    }

    /**
     * The report as plain text, for a person.
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Chaos report: ").append(getVerdict());
        if (pending) {
            sb.append(" (not final: an expectation is pending)");
        }
        sb.append("\n  seed: ").append(seed).append(seedGiven ? " (camel.chaos.seed)" : " (picked at startup)");
        sb.append("\n  rules:");
        if (rules.isEmpty()) {
            sb.append(" none");
        }
        for (RuleEntry r : rules) {
            sb.append("\n    ").append(r.id);
            if (r.version > 1) {
                // a rule that was changed: its entries in the report differ by version
                sb.append(" v").append(r.version);
            }
            sb.append(' ').append(r.state.key).append(": ").append(r.text);
            sb.append("\n      ").append(r.description);
            sb.append("\n      matches ").append(r.matches).append(", fired ").append(r.firedTotal);
            StringJoiner at = new StringJoiner(", ", " (", ")");
            at.setEmptyValue("");
            for (Hook hook : Hook.values()) {
                if (r.fired[hook.ordinal()] > 0) {
                    at.add(hook.key + " " + r.fired[hook.ordinal()]);
                }
            }
            sb.append(at).append(", skipped ").append(r.skipped);
            if (!r.skipReasons.isEmpty()) {
                StringJoiner why = new StringJoiner("; ", " (", ")");
                r.skipReasons.forEach((reason, count) -> why.add(reason + ": " + count));
                sb.append(why);
            }
            if (r.lostAtDlq > 0) {
                sb.append(", lost at the dead letter channel ").append(r.lostAtDlq);
            }
            if (r.state == State.ACTIVE && r.lifecycle == null) {
                sb.append(", ends in ").append(ChaosRuleParser.printTime(r.endsIn));
            } else if (r.state == State.WAITING && r.lifecycle == null) {
                sb.append(", starts in ").append(ChaosRuleParser.printTime(r.startsIn));
            }
            String live = liveText(r);
            if (!live.isEmpty()) {
                sb.append("\n      ").append(live);
            }
            if (r.faulted > 0) {
                sb.append("\n      ").append(effectText(r));
            }
            if (r.lifecycle != null) {
                sb.append("\n      ").append(lifecycleText(r));
            }
            if (r.outcome != null) {
                sb.append("\n      expect ").append(r.expectSpec).append(" within ")
                        .append(ChaosRuleParser.printTime(r.within)).append(": ").append(r.outcome.key)
                        .append(", ").append(r.valueText);
                if (r.outcome == Outcome.PENDING) {
                    sb.append(", ").append(ChaosRuleParser.printTime(r.timeLeft)).append(" left");
                } else if (r.outcome == Outcome.CANCELLED) {
                    sb.append(" (the rule was removed or changed, or the context stopped, before it was decided)");
                }
            }
        }
        sb.append("\n  routes:");
        if (routes.isEmpty()) {
            sb.append(" none");
        }
        for (RouteEntry r : routes) {
            sb.append("\n    ").append(r.route).append(": completed ").append(r.completed).append(", failed ")
                    .append(r.failed).append(" (kept by the consumer ").append(r.failedKept).append("), handled ")
                    .append(r.handled).append(", redeliveries ").append(r.redeliveries);
        }
        if (!tried.isEmpty()) {
            sb.append("\n  tried, per route:");
            for (ChaosAdvisor.RouteTried r : tried) {
                sb.append("\n    ").append(r.route()).append(':');
                if (r.tried().isEmpty()) {
                    sb.append(" nothing yet");
                }
                for (ChaosAdvisor.KindTried k : r.tried()) {
                    sb.append("\n      ").append(k.kind().key).append(" (").append(String.join(", ", k.rules()))
                            .append("): fired ").append(k.fired());
                    StringJoiner effect = new StringJoiner(", ", ", ", "");
                    effect.setEmptyValue("");
                    if (k.recovered() > 0) {
                        effect.add(k.recovered() + " recovered");
                    }
                    if (k.handled() > 0) {
                        effect.add(k.handled() + " handled");
                    }
                    if (k.failed() > 0) {
                        effect.add(k.failed() + " failed");
                    }
                    sb.append(effect);
                    for (String e : k.expectations()) {
                        sb.append(", expect ").append(e);
                    }
                }
                if (!r.notTried().isEmpty()) {
                    sb.append("\n      not tried yet: ")
                            .append(String.join(", ", r.notTried().stream().map(k -> k.key).toList()));
                }
            }
        }
        if (!recentFaults.isEmpty()) {
            sb.append("\n  recent faults:");
            for (ChaosRecentFaults.Entry f : recentFaults) {
                sb.append("\n    ").append(recentFaultText(f));
            }
        }
        sb.append("\n  lost: ").append(lost);
        for (String reason : reasons) {
            sb.append("\n  FAIL: ").append(reason);
        }
        return sb.toString();
    }

    /**
     * The live state of a rule, for a person: its observed rate next to the one of the rule, the next match an
     * {@code every:N} rule fires at, when it last fired and its faults in the last minute.
     */
    private static String liveText(RuleEntry r) {
        StringJoiner sj = new StringJoiner(", ");
        ChaosRule rule = r.rule;
        if (r.observedRate >= 0 && rule.targetRate() < 1.0d) {
            String target = rule.every > 0 ? "1 in " + rule.every : percent(rule.targetRate());
            if (rule.burstPeriod > 0) {
                target = ChaosRuleParser.printTime(rule.burstOn) + " in " + ChaosRuleParser.printTime(rule.burstPeriod);
            }
            sj.add("rate " + percent(r.observedRate) + " of " + target);
        }
        if (r.burstEndsIn >= 0) {
            sj.add("in a burst for " + ChaosRuleParser.printTime(r.burstEndsIn) + " more");
        } else if (r.nextBurstIn >= 0) {
            sj.add("next burst in " + ChaosRuleParser.printTime(r.nextBurstIn));
        }
        if (r.nextFireIn > 0) {
            sj.add("fires again in " + r.nextFireIn + (r.nextFireIn == 1 ? " match" : " matches"));
        }
        if (r.lastFiredAgo >= 0) {
            sj.add("last fired " + ChaosRuleParser.printTime(r.lastFiredAgo) + " ago");
            sj.add(r.faultsLastMinute + (r.faultsLastMinute == 1 ? " fault" : " faults") + " in the last minute");
        }
        return sj.toString();
    }

    /** What the faults of a rule did to the exchanges, for a person; the counts that are 0 are left out. */
    private static String effectText(RuleEntry r) {
        StringJoiner sj = new StringJoiner(
                ", ", "effect on " + r.faulted + (r.faulted == 1 ? " exchange: " : " exchanges: "),
                "");
        if (r.completed > 0) {
            sj.add(r.completed + " completed");
        }
        if (r.recovered > 0) {
            sj.add(r.recovered + " recovered by redelivery");
        }
        if (r.handled > 0) {
            sj.add(r.handled + " handled by the error handler");
        }
        if (r.failed > 0) {
            sj.add(r.failed + " failed");
        }
        if (r.inFlight > 0) {
            sj.add(r.inFlight + " in flight");
        }
        return sj.toString();
    }

    /**
     * A recent fault, for a person, such as {@code 3s ago r1 error ChaosException at send mock://stock, route orders}.
     */
    private String recentFaultText(ChaosRecentFaults.Entry f) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChaosRuleParser.printTime(Math.max(0, now - f.time()))).append(" ago ").append(f.rule().id);
        if (f.rule().version > 1) {
            sb.append(" v").append(f.rule().version);
        }
        sb.append(' ').append(f.rule().faultText());
        if (f.hook() == null) {
            sb.append(" of route ").append(f.routeId());
            return sb.toString();
        }
        if (f.hook() == Hook.ENTRY) {
            sb.append(" at the entry of route ").append(f.routeId());
        } else {
            sb.append(" at ").append(f.hook().key).append(' ').append(where(f));
            if (f.routeId() != null) {
                sb.append(", route ").append(f.routeId());
            }
        }
        if (f.exchangeId() != null) {
            sb.append(", exchange ").append(f.exchangeId());
        }
        return sb.toString();
    }

    // the endpoint of a fault without its query, which can hold secrets, or the id of its step
    private static String where(ChaosRecentFaults.Entry f) {
        return f.hook() == Hook.STEP ? f.where() : ChaosTargets.withoutQuery(f.where());
    }

    // a share as a percent, such as 5% or 5.1%
    private static String percent(double share) {
        double p = share * 100;
        double tenths = Math.round(p * 10) / 10.0d;
        return (tenths == Math.rint(tenths) ? Long.toString((long) tenths) : Double.toString(tenths)) + "%";
    }

    /** What has been tried per route, and what not yet, as JSON. */
    private JsonArray triedJson() {
        JsonArray answer = new JsonArray();
        for (ChaosAdvisor.RouteTried r : tried) {
            JsonObject jo = new JsonObject();
            jo.put("route", r.route());
            JsonArray kinds = new JsonArray();
            for (ChaosAdvisor.KindTried k : r.tried()) {
                JsonObject ko = new JsonObject();
                ko.put("kind", k.kind().key);
                ko.put("rules", new JsonArray(k.rules()));
                ko.put("fired", k.fired());
                ko.put("recovered", k.recovered());
                ko.put("handled", k.handled());
                ko.put("failed", k.failed());
                if (!k.expectations().isEmpty()) {
                    ko.put("expectations", new JsonArray(k.expectations()));
                }
                kinds.add(ko);
            }
            jo.put("kinds", kinds);
            jo.put("notTried", new JsonArray(r.notTried().stream().map(k -> k.key).toList()));
            answer.add(jo);
        }
        return answer;
    }

    /**
     * The report as JSON, for a tool.
     */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.put("verdict", getVerdict());
        root.put("final", isFinal());
        root.put("seed", seed);
        root.put("seedGiven", seedGiven);
        root.put("lost", lost);
        root.put("reasons", new JsonArray(reasons));
        root.put("rules", rulesJson(false));
        JsonArray routeArray = new JsonArray();
        for (RouteEntry r : routes) {
            JsonObject jo = new JsonObject();
            jo.put("route", r.route);
            jo.put("completed", r.completed);
            jo.put("failed", r.failed);
            jo.put("failedKeptByConsumer", r.failedKept);
            jo.put("handled", r.handled);
            jo.put("redeliveries", r.redeliveries);
            routeArray.add(jo);
        }
        root.put("routes", routeArray);
        root.put("recentFaults", recentFaultsJson());
        root.put("tried", triedJson());
        return root;
    }

    /** The recent faults as JSON, the latest first, as in {@link #toJson()}. */
    JsonArray recentFaultsJson() {
        JsonArray answer = new JsonArray();
        for (ChaosRecentFaults.Entry f : recentFaults) {
            JsonObject jo = new JsonObject();
            jo.put("timestamp", f.time());
            jo.put("ago", Math.max(0, now - f.time()));
            jo.put("rule", f.rule().id);
            jo.put("version", f.rule().version);
            jo.put("fault", f.rule().faultText());
            if (f.hook() != null) {
                jo.put("hook", f.hook().key);
            }
            if (f.routeId() != null) {
                jo.put("route", f.routeId());
            }
            if (f.where() != null) {
                jo.put(f.hook() == Hook.STEP ? "step" : "endpoint", where(f));
            }
            if (f.exchangeId() != null) {
                jo.put("exchangeId", f.exchangeId());
            }
            answer.add(jo);
        }
        return answer;
    }

    /**
     * The rules of the report as JSON, as in {@link #toJson()}: all of them, or only the current ones (not removed,
     * replaced or moved to the history), for the chaos dev console.
     */
    JsonArray rulesJson(boolean currentOnly) {
        JsonArray ruleArray = new JsonArray();
        for (RuleEntry r : rules) {
            if (currentOnly && !r.current) {
                continue;
            }
            JsonObject jo = new JsonObject();
            jo.put("id", r.id);
            jo.put("version", r.version);
            jo.put("rule", r.text);
            jo.put("description", r.description);
            jo.put("state", r.state.key);
            // where it can fire in the context now; fired has each hook of the rule
            JsonArray hooks = new JsonArray();
            for (Hook hook : Hook.values()) {
                if (r.canFire.contains(hook)) {
                    hooks.add(hook.key);
                }
            }
            jo.put("hooks", hooks);
            jo.put("matches", r.matches);
            JsonObject fired = new JsonObject();
            for (Hook hook : Hook.values()) {
                if (r.hooks.contains(hook)) {
                    fired.put(hook.key, r.fired[hook.ordinal()]);
                }
            }
            jo.put("fired", fired);
            jo.put("skipped", r.skipped);
            if (!r.skipReasons.isEmpty()) {
                // why the rule did nothing, per reason
                JsonObject why = new JsonObject();
                r.skipReasons.forEach(why::put);
                jo.put("skippedReasons", why);
            }
            if (r.hooks.contains(Hook.DLQ)) {
                jo.put("lostAtDlq", r.lostAtDlq);
            }
            if (r.state == State.ACTIVE && r.lifecycle == null) {
                jo.put("endsIn", r.endsIn);
            } else if (r.state == State.WAITING && r.lifecycle == null) {
                jo.put("startsIn", r.startsIn);
            }
            liveJson(r, jo);
            if (r.outcome != null) {
                JsonObject e = new JsonObject();
                e.put("route", r.expectRoute);
                e.put("kind", r.expectKind.key);
                e.put("op", r.expectOp.key);
                e.put("count", r.expectCount);
                e.put("within", r.within);
                // the count of its kind so far (completed for an expectation of completed exchanges, as before)
                e.put("value", r.value);
                if (r.expectKind == ChaosRule.Expectation.Kind.COMPLETED) {
                    e.put("completed", r.value);
                }
                // the expectation as the rule writes it, and for a time (count and value in milliseconds) or the
                // messages lost between two routes, the exchanges it counted (CAMEL-26726)
                e.put("spec", r.expectSpec);
                if (r.expectToRoute != null) {
                    e.put("toRoute", r.expectToRoute);
                }
                if (r.expectKind.time() || r.expectToRoute != null) {
                    e.put("samples", r.samples);
                }
                e.put("outcome", r.outcome.key);
                if (r.outcome == Outcome.PENDING) {
                    e.put("timeLeft", r.timeLeft);
                }
                jo.put("expectation", e);
            }
            lifecycleJson(r, jo);
            ruleArray.add(jo);
        }
        return ruleArray;
    }

    @Override
    public String toString() {
        return toText();
    }

    /**
     * The live state of a rule, for a tool: the rate of the rule and the observed one (the share of the matches it
     * fired at), the next match an {@code every:N} rule fires at, when it last fired, its faults per second over the
     * last minute, and the effect of its faults on the exchanges.
     */
    private static void liveJson(RuleEntry r, JsonObject jo) {
        if (r.lifecycle == null) {
            jo.put("rate", r.rule.targetRate());
            if (r.observedRate >= 0) {
                jo.put("observedRate", Math.round(r.observedRate * 10000) / 10000.0d);
            }
        }
        if (r.nextFireIn > 0) {
            jo.put("nextFireIn", r.nextFireIn);
        }
        if (r.burstEndsIn >= 0) {
            jo.put("burstEndsIn", r.burstEndsIn);
        } else if (r.nextBurstIn >= 0) {
            jo.put("nextBurstIn", r.nextBurstIn);
        }
        if (r.lastFiredAgo >= 0) {
            jo.put("lastFiredAgo", r.lastFiredAgo);
        }
        jo.put("faultsLastMinute", r.faultsLastMinute);
        if (r.faultsLastMinute > 0) {
            // the oldest second first; the last one is the current second
            JsonArray perSecond = new JsonArray();
            for (long n : r.faultsPerSecond) {
                perSecond.add(n);
            }
            jo.put("faultsPerSecond", perSecond);
        }
        if (r.faulted > 0) {
            JsonObject e = new JsonObject();
            e.put("faulted", r.faulted);
            e.put("completed", r.completed);
            e.put("recovered", r.recovered);
            e.put("handled", r.handled);
            e.put("failed", r.failed);
            e.put("inFlight", r.inFlight);
            jo.put("effect", e);
        }
    }

    /**
     * What a lifecycle rule did to its route, for a person; the times are after the rule was added, such as
     * {@code suspended route orders at +0ms, resumed at +10m0s}.
     */
    private static String lifecycleText(RuleEntry r) {
        ChaosLifecycle.Fault.Reading l = r.lifecycle;
        // the words of what was done to the route (a remove that failed after the stop left it stopped)
        Action a = l.action();
        String route = "route " + r.lifecycleRoute;
        String down = a.done + " " + route + " at +" + ChaosRuleParser.printTime(l.downAt());
        int repeat = r.rule.repeat;
        // with repeat=: which round the times are of
        String round = repeat > 1 && l.round() > 0 ? "round " + l.round() + " of " + repeat + ": " : "";
        String text = switch (l.state()) {
            case PENDING -> l.round() > 0
                    ? down + ", " + a.back + " at +" + ChaosRuleParser.printTime(l.backAt()) + " (down "
                      + ChaosRuleParser.printTime(l.backAt() - l.downAt()) + "); round " + (l.round() + 1) + " in "
                      + ChaosRuleParser.printTime(Math.max(0, l.actsIn()))
                    : "pending: " + a.key + " " + route + " for " + ChaosRuleParser.printTime(r.duration)
                      + (repeat > 1 ? ", " + repeat + " times every " + ChaosRuleParser.printTime(r.rule.repeatEvery) : "")
                      + (l.actsIn() >= 0
                              ? " in " + ChaosRuleParser.printTime(l.actsIn()) : " when the context has started");
            case ACTING -> a.key + " " + route + ": in progress";
            case DOWN -> down + ", " + (l.endedEarly()
                    ? a.comeback + " now, as the rule was removed"
                    : a.comeback + " in " + ChaosRuleParser.printTime(l.backIn()))
                         + (l.reason() != null ? "; " + l.reason() : "");
            case COMING_BACK -> down + ", " + a.comeback + ": in progress";
            case BACK -> down + ", " + a.back + " at +" + ChaosRuleParser.printTime(l.backAt()) + " (down "
                         + ChaosRuleParser.printTime(l.backAt() - l.downAt())
                         + (l.endedEarly() ? ", back early as the rule was removed)" : ")")
                         + (l.reason() != null ? "; " + l.reason() : "");
            case SKIPPED -> "did nothing: " + l.reason();
            case LEFT -> down + ", not " + a.back + " at +" + ChaosRuleParser.printTime(l.backAt()) + ": " + l.reason();
            case FAILED -> (l.acted() ? down + ", " : "") + l.reason()
                           + (l.backAt() >= 0 ? " (at +" + ChaosRuleParser.printTime(l.backAt()) + ")" : "");
            case CANCELLED -> (l.acted() ? down + ", " : "") + "cancelled: " + l.reason();
        };
        return round + text;
    }

    /** What a lifecycle rule did to its route, for a tool; the times are milliseconds after the rule was added. */
    private static void lifecycleJson(RuleEntry r, JsonObject jo) {
        ChaosLifecycle.Fault.Reading l = r.lifecycle;
        if (l == null) {
            return;
        }
        // the action is what a lifecycle rule fires (it has no hook)
        ((JsonObject) jo.get("fired")).put("lifecycle", l.round());
        JsonObject lj = new JsonObject();
        lj.put("action", r.action.key);
        lj.put("route", r.lifecycleRoute);
        lj.put("duration", r.duration);
        lj.put("state", l.state().key);
        if (r.rule.repeat > 1) {
            lj.put("repeat", r.rule.repeat);
            lj.put("every", r.rule.repeatEvery);
        }
        // the times the rule took its route down, and the time until it acts (again)
        lj.put("round", l.round());
        if (l.actsIn() >= 0) {
            lj.put("actsIn", l.actsIn());
        }
        if (l.downAt() >= 0) {
            lj.put("downAt", l.downAt());
        }
        if (l.backAt() >= 0) {
            lj.put("backAt", l.backAt());
        }
        if (l.backIn() >= 0) {
            lj.put("backIn", l.backIn());
        }
        if (l.endedEarly()) {
            // the rule was removed while the route was down, so it came back then
            lj.put("endedEarly", true);
        }
        if (l.reason() != null) {
            lj.put("reason", l.reason());
        }
        jo.put("lifecycle", lj);
    }

    /** A rule in the report. */
    private static final class RuleEntry {
        final ChaosRule rule;
        // a current rule (not removed, replaced or moved to the history)
        final boolean current;
        final String id;
        final int version;
        final String text;
        final State state;
        // the rule in plain words, at the hooks where it can fire
        final String description;
        // the hooks of the rule, and those where it can fire in the context now
        final Set<Hook> hooks;
        final Set<Hook> canFire;
        final long matches;
        final long[] fired = new long[Hook.values().length];
        final long firedTotal;
        final long skipped;
        final Map<String, Long> skipReasons;
        final long lostAtDlq;
        final long endsIn;
        final long startsIn;
        // a rate=burst: rule that is active: how long its burst lasts, or until the next one (-1 when it does not apply)
        final long burstEndsIn;
        final long nextBurstIn;
        // the live state: the share of the matches the rule fired at (-1 without a match), the matches until an
        // every:N rule fires again (0 when it does not), how long ago it last fired (-1 when it did not), its faults
        // per second over the last minute, and the effect of its faults on the exchanges
        final double observedRate;
        final long nextFireIn;
        final long lastFiredAgo;
        final long[] faultsPerSecond;
        final long faultsLastMinute;
        final long faulted;
        final long completed;
        final long recovered;
        final long handled;
        final long failed;
        final long inFlight;
        final String expectRoute;
        final ChaosRule.Expectation.Kind expectKind;
        final ChaosRule.Expectation.Op expectOp;
        final String expectSpec;
        final long expectCount;
        final long within;
        final Outcome outcome;
        final long value;
        // the value as the report says it, such as 3 completed or mean 1s200ms of 12 exchanges
        final String valueText;
        // the next route of a lost expectation, and the exchanges a time or a lost expectation counted
        final String expectToRoute;
        final long samples;
        final long lost;
        final long timeLeft;
        // a lifecycle rule: its action, route, duration and what it did, else null
        final Action action;
        final String lifecycleRoute;
        final long duration;
        final ChaosLifecycle.Fault.Reading lifecycle;

        RuleEntry(ChaosRule rule, long now, boolean current, ChaosTargets.Reach reach) {
            this.rule = rule;
            this.current = current;
            id = rule.id;
            version = rule.version;
            text = rule.text;
            state = rule.state(now);
            hooks = rule.hooks;
            canFire = ChaosTargets.hooksThatCanFire(reach, rule);
            description = rule.describe(canFire);
            matches = rule.matches();
            long total = 0;
            for (Hook hook : Hook.values()) {
                fired[hook.ordinal()] = rule.firedAt(hook);
                total += fired[hook.ordinal()];
            }
            lifecycle = rule.lifecycle != null ? rule.lifecycle.read(now) : null;
            action = rule.action;
            lifecycleRoute = rule.lifecycle != null ? rule.pattern : null;
            duration = rule.duration;
            // a lifecycle rule fired when it took its route down
            firedTotal = total + (lifecycle != null ? lifecycle.round() : 0);
            skipped = rule.skippedCount();
            skipReasons = rule.skipReasons();
            lostAtDlq = rule.lostAtDlqCount();
            endsIn = Math.max(0, rule.endsAt - now);
            startsIn = Math.max(0, rule.startsAt - now);
            boolean bursting = rule.burstPeriod > 0 && state == State.ACTIVE;
            burstEndsIn = bursting && rule.inBurst(now) ? rule.burstChangesIn(now) : -1;
            nextBurstIn = bursting && !rule.inBurst(now) ? rule.burstChangesIn(now) : -1;
            // a lifecycle rule acts once at its route: it has no rate
            observedRate = matches > 0 && rule.lifecycle == null ? Math.min(1.0d, (double) (total + skipped) / matches) : -1;
            nextFireIn = rule.every > 0 && state == State.ACTIVE ? rule.every - matches % rule.every : 0;
            long last = rule.lastFiredAt();
            lastFiredAgo = last >= 0 ? Math.max(0, now - last) : -1;
            faultsPerSecond = rule.faultsPerSecond(now);
            long minute = 0;
            for (long n : faultsPerSecond) {
                minute += n;
            }
            faultsLastMinute = minute;
            ChaosRule.Effect effect = rule.effect;
            // the done counts first, so an exchange that is done while the report is made is not in flight twice
            completed = effect.count(ChaosRule.Effect.Outcome.COMPLETED);
            recovered = effect.count(ChaosRule.Effect.Outcome.RECOVERED);
            handled = effect.count(ChaosRule.Effect.Outcome.HANDLED);
            failed = effect.count(ChaosRule.Effect.Outcome.FAILED);
            faulted = Math.max(effect.faultedCount(), completed + recovered + handled + failed);
            inFlight = faulted - completed - recovered - handled - failed;
            within = rule.within;
            ChaosRule.Expectation expectation = rule.expectation;
            if (expectation != null) {
                expectRoute = expectation.route;
                expectKind = expectation.kind;
                expectOp = expectation.op;
                expectSpec = expectation.spec();
                expectCount = expectation.count;
                ChaosRule.Expectation.Reading reading = expectation.read(now);
                outcome = reading.outcome();
                value = reading.value();
                valueText = expectation.valueText(reading);
                expectToRoute = expectation.toRoute;
                samples = reading.samples();
                lost = expectation.lost(reading);
                timeLeft = Math.max(0, expectation.deadline() - now);
            } else {
                expectRoute = null;
                expectKind = null;
                expectOp = null;
                expectSpec = null;
                expectCount = 0;
                outcome = null;
                value = 0;
                valueText = null;
                expectToRoute = null;
                samples = 0;
                lost = 0;
                timeLeft = 0;
            }
        }
    }

    /** A route in the report. */
    private static final class RouteEntry {
        final String route;
        final long completed;
        final long failed;
        final long failedKept;
        final long handled;
        final long redeliveries;

        RouteEntry(String route, RouteCounts counts) {
            this.route = route;
            completed = counts.completed.sum();
            failed = counts.failed.sum();
            failedKept = counts.failedKept.sum();
            handled = counts.handled.sum();
            redeliveries = counts.redeliveries.sum();
        }
    }
}
