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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.camel.component.chaos.ChaosManager.RouteCounts;
import org.apache.camel.component.chaos.ChaosRule.Expectation.Outcome;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.State;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * What the chaos rules did, as of the time the report was made: the seed, each rule with its matches and the faults
 * that fired, the counts per route, the expectations and the verdict.
 * <p/>
 * Without an expectation the verdict is PASS, as chaos makes exchanges fail on purpose. Only an expectation that is
 * unmet (its route did not complete that many exchanges in time) makes it FAIL, and only an unmet expectation counts
 * messages as lost. An expectation that is not decided yet is pending, and so is the report: a PASS with a pending
 * expectation is not final ({@link #isFinal()}).
 * <p/>
 * A route counts each exchange that goes through it once, when the route is done with it: a message that fails and is
 * read again by its consumer is one failed and one completed exchange. A failed exchange is "kept by the consumer" when
 * the consumer of the route reads the message again (for example a file consumer without {@code moveFailed}).
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
    private final List<RuleEntry> rules = new ArrayList<>();
    // the current rules come first in rules, then the removed, replaced and retired ones
    private final int current;
    private final List<RouteEntry> routes = new ArrayList<>();
    private final List<String> reasons = new ArrayList<>();
    private final long lost;
    private final boolean pending;

    ChaosReport(long seed, boolean seedGiven, long now, List<ChaosRule> current, List<ChaosRule> removed,
                Map<String, RouteCounts> counts, int evictedUnmet, long evictedLost, int evictedPending) {
        this.seed = seed;
        this.seedGiven = seedGiven;
        long lostCount = 0;
        boolean anyPending = false;
        this.current = current.size();
        List<ChaosRule> all = new ArrayList<>(current);
        all.addAll(removed);
        for (ChaosRule rule : all) {
            RuleEntry entry = new RuleEntry(rule, now);
            rules.add(entry);
            if (entry.outcome == Outcome.UNMET) {
                long missing = Math.max(0, rule.expectation.count - entry.completed);
                lostCount += missing;
                reasons.add("route " + rule.expectation.route + " completed " + entry.completed + " of "
                            + rule.expectation.count + " exchanges within " + ChaosRuleParser.printTime(rule.within)
                            + " (rule " + rule.id + "): " + missing + " lost");
            } else if (entry.outcome == Outcome.PENDING) {
                anyPending = true;
            }
        }
        if (evictedUnmet > 0) {
            lostCount += evictedLost;
            reasons.add(evictedUnmet + " unmet expectations of rules that are no longer in the report: " + evictedLost
                        + " lost");
        }
        counts.forEach((route, c) -> routes.add(new RouteEntry(route, c)));
        this.lost = lostCount;
        this.pending = anyPending || evictedPending > 0;
    }

    /**
     * The verdict: {@link #FAIL} when an expectation is unmet, {@link #PASS} otherwise.
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
     * The reasons of a {@link #FAIL} verdict, one per unmet expectation.
     */
    public List<String> getReasons() {
        return Collections.unmodifiableList(reasons);
    }

    /**
     * The messages that unmet expectations count as lost: the count of each unmet expectation minus the exchanges its
     * route completed in time.
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
            sb.append("\n      matches ").append(r.matches).append(", fired ").append(r.firedTotal);
            StringJoiner at = new StringJoiner(", ", " (", ")");
            at.setEmptyValue("");
            for (Hook hook : Hook.values()) {
                if (r.fired[hook.ordinal()] > 0) {
                    at.add(hook.key + " " + r.fired[hook.ordinal()]);
                }
            }
            sb.append(at).append(", skipped ").append(r.skipped);
            if (r.state == State.ACTIVE) {
                sb.append(", ends in ").append(ChaosRuleParser.printTime(r.endsIn));
            }
            if (r.outcome != null) {
                sb.append("\n      expect ").append(r.expectRoute).append(':').append(r.expectCount).append(" within ")
                        .append(ChaosRuleParser.printTime(r.within)).append(": ").append(r.outcome.key)
                        .append(", ").append(r.completed).append(" completed");
                if (r.outcome == Outcome.PENDING) {
                    sb.append(r.frozen
                            ? " (not decided: the rule was removed or changed, or the context stopped, first)"
                            : ", " + ChaosRuleParser.printTime(r.timeLeft) + " left");
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
        sb.append("\n  lost: ").append(lost);
        for (String reason : reasons) {
            sb.append("\n  FAIL: ").append(reason);
        }
        return sb.toString();
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
        return root;
    }

    /**
     * The rules of the report as JSON, as in {@link #toJson()}: all of them, or only the current ones (not removed,
     * replaced or moved to the history), for the chaos dev console.
     */
    JsonArray rulesJson(boolean currentOnly) {
        JsonArray ruleArray = new JsonArray();
        for (RuleEntry r : currentOnly ? rules.subList(0, current) : rules) {
            JsonObject jo = new JsonObject();
            jo.put("id", r.id);
            jo.put("version", r.version);
            jo.put("rule", r.text);
            jo.put("state", r.state.key);
            JsonArray hooks = new JsonArray();
            for (Hook hook : Hook.values()) {
                if (r.hooks.contains(hook)) {
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
            if (r.state == State.ACTIVE) {
                jo.put("endsIn", r.endsIn);
            }
            if (r.outcome != null) {
                JsonObject e = new JsonObject();
                e.put("route", r.expectRoute);
                e.put("count", r.expectCount);
                e.put("within", r.within);
                e.put("completed", r.completed);
                e.put("outcome", r.outcome.key);
                if (r.outcome == Outcome.PENDING && !r.frozen) {
                    e.put("timeLeft", r.timeLeft);
                }
                if (r.frozen && r.outcome == Outcome.PENDING) {
                    // the rule was removed or changed, or the context stopped, before it was decided
                    e.put("notDecided", true);
                }
                jo.put("expectation", e);
            }
            ruleArray.add(jo);
        }
        return ruleArray;
    }

    @Override
    public String toString() {
        return toText();
    }

    /** A rule in the report. */
    private static final class RuleEntry {
        final String id;
        final int version;
        final String text;
        final State state;
        final Set<Hook> hooks;
        final long matches;
        final long[] fired = new long[Hook.values().length];
        final long firedTotal;
        final long skipped;
        final long endsIn;
        final String expectRoute;
        final long expectCount;
        final long within;
        final Outcome outcome;
        final long completed;
        final long timeLeft;
        final boolean frozen;

        RuleEntry(ChaosRule rule, long now) {
            id = rule.id;
            version = rule.version;
            text = rule.text;
            state = rule.state(now);
            hooks = rule.hooks;
            matches = rule.matches();
            long total = 0;
            for (Hook hook : Hook.values()) {
                fired[hook.ordinal()] = rule.firedAt(hook);
                total += fired[hook.ordinal()];
            }
            firedTotal = total;
            skipped = rule.skippedCount();
            endsIn = Math.max(0, rule.endsAt - now);
            within = rule.within;
            ChaosRule.Expectation expectation = rule.expectation;
            if (expectation != null) {
                expectRoute = expectation.route;
                expectCount = expectation.count;
                ChaosRule.Expectation.Reading reading = expectation.read(now);
                outcome = reading.outcome();
                completed = reading.completed();
                timeLeft = Math.max(0, expectation.deadline - now);
                frozen = reading.frozen();
            } else {
                expectRoute = null;
                expectCount = 0;
                outcome = null;
                completed = 0;
                timeLeft = 0;
                frozen = false;
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
