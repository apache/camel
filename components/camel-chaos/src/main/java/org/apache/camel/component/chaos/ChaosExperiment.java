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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.apache.camel.component.chaos.ChaosRule.Expectation;
import org.apache.camel.component.chaos.ChaosRule.Expectation.Outcome;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * An experiment of an exploration (CAMEL-26774): a hypothesis, the rules that test it with their expectations, and what
 * came of it. It begins with its rules added together and the counts per route read, and ends with its rules removed:
 * the difference of the counts, the faults of each rule and the states of the expectations are kept in the journal,
 * with a verdict.
 * <p/>
 * The verdict is {@code FAIL} when an expectation is unmet, or a rule of it failed or dropped the send of a dead letter
 * channel, which then handled the message without delivering it (lost, as the report counts it, CAMEL-26789), and
 * {@code PASS} when all are met. While it runs and an expectation is not decided it is {@code PENDING}, and an
 * experiment that ended before an expectation was decided is {@code INCONCLUSIVE}: the messages may still have come. An
 * experiment whose faults never fired is not a {@code PASS} either, as its expectations say nothing about the faults
 * ({@code INCONCLUSIVE} once it ended, CAMEL-26790). The routes that failed while it ran, and that no expectation of it
 * counts the failures of, are listed ({@code alsoFailed}), so a verdict on another count does not hide them. The
 * manager guards it with its lock.
 */
final class ChaosExperiment {

    static final String PASS = "PASS";
    static final String FAIL = "FAIL";
    static final String PENDING = "PENDING";
    static final String INCONCLUSIVE = "INCONCLUSIVE";

    // why an experiment is INCONCLUSIVE
    static final String NEVER_FIRED = "its faults never fired, so its expectations say nothing about them";
    static final String NOT_DECIDED = "it ended before an expectation was decided, so the messages may still have come";

    /** Why an experiment whose expectation was met on a route without messages is INCONCLUSIVE. */
    static String noMessages(Set<String> routes) {
        return (routes.size() == 1 ? "route " : "routes ") + String.join(", ", routes)
               + " had no messages while it ran, so " + (routes.size() == 1 ? "its expectation says" : "their expectations say")
               + " nothing: feed " + (routes.size() == 1 ? "it" : "them");
    }

    /**
     * Why an experiment is INCONCLUSIVE when the route of an expectation failed exchanges that no expectation counts
     * (CAMEL-26797), such as refunds->payment:lost<=0 while refunds failed: lost counts what one route completed and
     * the next did not, so a failure in the first route is not in it.
     */
    static String uncountedFailures(Map<String, Long> routes) {
        StringJoiner failed = new StringJoiner(", ");
        StringJoiner expect = new StringJoiner(" ");
        routes.forEach((route, n) -> {
            failed.add(routes.size() == 1 ? route + " failed " + n + (n == 1 ? " exchange" : " exchanges") : route + " " + n);
            expect.add("expect=" + route + ":failed<=0");
        });
        return (routes.size() == 1 ? "route " + failed : "routes " + failed + " failed exchanges")
               + " that its expectations do not count, so they say nothing about the failures: " + expect + " counts them";
    }

    /** How an experiment ended. */
    enum End {
        // endExperiment
        ENDED("ended"),
        // the exploration was finished with the experiment open
        FINISHED("finished"),
        // the time of the exploration was up
        DEADLINE("deadline");

        final String key;

        End(String key) {
            this.key = key;
        }
    }

    final int number;
    final String hypothesis;
    final List<String> rules;
    final long startedAt;
    // the counts per route when it began: completed, failed, kept by the consumer, handled, redeliveries
    private final Map<String, long[]> before = new LinkedHashMap<>();
    // the report was reset while it ran: the counts of the routes started again from zero
    private boolean countsReset;
    // what came of it, once it ended
    private JsonObject result;
    private String resultText;

    ChaosExperiment(int number, String hypothesis, List<String> rules, Map<String, ChaosManager.RouteCounts> counts,
                    long startedAt) {
        this.number = number;
        this.hypothesis = hypothesis != null && !hypothesis.isBlank() ? hypothesis.strip() : null;
        this.rules = List.copyOf(rules);
        this.startedAt = startedAt;
        counts.forEach((route, c) -> before.put(route, read(c)));
    }

    boolean isOpen() {
        return result == null;
    }

    /** The counts of the routes were reset (a scenario was loaded): they count from zero now. */
    void reportReset() {
        before.clear();
        countsReset = true;
    }

    /**
     * Ends it: what came of it is read now, before its rules are removed (a removed rule cancels its expectation if not
     * decided).
     */
    void end(
            Function<String, ChaosRule> rule, Map<String, ChaosManager.RouteCounts> counts, long now, End by,
            String note) {
        JsonObject jo = read(rule, counts, now, true);
        jo.put("endedAt", now);
        jo.put("endedBy", by.key);
        if (note != null && !note.isBlank()) {
            jo.put("note", note.strip());
        }
        resultText = text(jo);
        result = jo;
    }

    /** What came of it as it ended, or null while it runs. */
    JsonObject result() {
        return result;
    }

    /** What came of it: as it ended, or as it is now while it runs. */
    JsonObject toJson(Function<String, ChaosRule> rule, Map<String, ChaosManager.RouteCounts> counts, long now) {
        return result != null ? result : read(rule, counts, now, false);
    }

    String text(Function<String, ChaosRule> rule, Map<String, ChaosManager.RouteCounts> counts, long now) {
        return result != null ? resultText : text(read(rule, counts, now, false));
    }

    private JsonObject read(
            Function<String, ChaosRule> rule, Map<String, ChaosManager.RouteCounts> counts, long now, boolean ended) {
        JsonObject jo = new JsonObject();
        jo.put("number", number);
        if (hypothesis != null) {
            jo.put("hypothesis", hypothesis);
        }
        jo.put("open", !ended);
        jo.put("startedAt", startedAt);
        jo.put("elapsed", now - startedAt);
        jo.put("rules", new JsonArray(rules));
        boolean unmet = false;
        boolean undecided = false;
        // the messages its rules kept from a dead letter channel: handled there, so no expectation of a route sees them
        long lost = 0;
        JsonObject lostAtDlq = new JsonObject();
        JsonArray texts = new JsonArray();
        JsonObject faults = new JsonObject();
        JsonArray expectations = new JsonArray();
        long firedAll = 0;
        // the routes whose failures an expectation counts, and the routes of the expectations that are met
        Set<String> failedExpected = new HashSet<>();
        // the routes of the expectations, and the routes whose failures an expectation counts in some way: failed,
        // completed (a failure is not completed), or the next route of lost (what it did not complete)
        Set<String> expectRoutes = new HashSet<>();
        Set<String> failuresCounted = new HashSet<>();
        Set<String> metRoutes = new TreeSet<>();
        Set<String> unmetTimes = new HashSet<>();
        for (String id : rules) {
            ChaosRule r = rule.apply(id);
            if (r == null) {
                continue;
            }
            texts.add(r.text);
            long fired = 0;
            for (ChaosRule.Hook hook : ChaosRule.Hook.values()) {
                fired += r.firedAt(hook);
            }
            if (r.lifecycle != null) {
                // a stall or lifecycle rule fired when it took its route down, as the report counts it
                fired += r.lifecycle.read(now).round();
            }
            faults.put(id, fired);
            firedAll += fired;
            long kept = r.lostAtDlqCount();
            if (kept > 0) {
                lostAtDlq.put(id, kept);
                lost += kept;
            }
            Expectation e = r.expectation;
            if (e != null) {
                if (e.kind == Expectation.Kind.FAILED) {
                    failedExpected.add(e.route);
                }
                expectRoutes.add(e.route);
                if (e.kind == Expectation.Kind.FAILED || e.kind == Expectation.Kind.COMPLETED) {
                    failuresCounted.add(e.route);
                } else if (e.kind == Expectation.Kind.LOST) {
                    failuresCounted.add(e.toRoute);
                }
                Expectation.Reading reading = e.read(now);
                // an expectation the experiment did not wait for is undecided, not cancelled
                Outcome outcome = reading.outcome() == Outcome.CANCELLED ? Outcome.PENDING : reading.outcome();
                if (outcome == Outcome.UNMET && e.kind.time()) {
                    // a time is unmet without an exchange too: decided once the routes are known
                    unmetTimes.add(e.route);
                } else {
                    unmet |= outcome == Outcome.UNMET;
                }
                undecided |= outcome == Outcome.PENDING;
                if (outcome == Outcome.MET) {
                    metRoutes.add(e.route);
                }
                JsonObject x = new JsonObject();
                x.put("rule", id);
                x.put("expect", e.spec());
                x.put("state", outcome.key);
                x.put("actual", reading.value());
                x.put("value", e.valueText(reading));
                expectations.add(x);
            }
        }
        jo.put("ruleTexts", texts);
        jo.put("faults", faults);
        jo.put("expectations", expectations);
        if (lost > 0) {
            jo.put("lostAtDeadLetterChannel", lost);
            jo.put("lostAtDeadLetterChannelPerRule", lostAtDlq);
        }
        JsonArray routes = new JsonArray();
        JsonObject alsoFailed = new JsonObject();
        Set<String> active = new HashSet<>();
        Map<String, Long> uncounted = new TreeMap<>();
        counts.forEach((route, c) -> {
            long[] now0 = read(c);
            long[] then = before.getOrDefault(route, new long[5]);
            long[] delta = new long[5];
            boolean any = false;
            for (int i = 0; i < delta.length; i++) {
                delta[i] = Math.max(0, now0[i] - then[i]);
                any |= delta[i] > 0;
            }
            if (any) {
                active.add(route);
                JsonObject r = new JsonObject();
                r.put("route", route);
                r.put("completed", delta[0]);
                r.put("failed", delta[1]);
                r.put("failedKeptByConsumer", delta[2]);
                r.put("handled", delta[3]);
                r.put("redeliveries", delta[4]);
                routes.add(r);
                if (delta[1] > 0 && !failedExpected.contains(route)) {
                    alsoFailed.put(route, delta[1]);
                }
                if (delta[1] > 0 && expectRoutes.contains(route) && !failuresCounted.contains(route)) {
                    uncounted.put(route, delta[1]);
                }
            }
        });
        jo.put("routes", routes);
        if (!alsoFailed.isEmpty()) {
            jo.put("alsoFailed", alsoFailed);
        }
        // nothing fired: the expectations were not put to the test (an unmet one is still a FAIL, with its evidence).
        // This is for the experiment as a whole: when one of its rules fired, the experiment was put to the test,
        // and the expectations still decide each rule
        boolean neverFired = firedAll == 0;
        // an expectation met while its route had no messages says nothing either, such as refunds:failed<=0 when the
        // traffic went to another route
        for (String route : unmetTimes) {
            if (active.contains(route)) {
                unmet = true;
            } else {
                // maxTime<=10s with no exchange in its route says nothing about the time either
                metRoutes.add(route);
            }
        }
        metRoutes.removeAll(active);
        boolean idle = !metRoutes.isEmpty();
        // an expectation met while its route failed exchanges it does not count says nothing either (CAMEL-26797)
        boolean hidden = !uncounted.isEmpty();
        String verdict = unmet || lost > 0
                ? FAIL : !undecided && !neverFired && !idle && !hidden ? PASS : ended ? INCONCLUSIVE : PENDING;
        jo.put("verdict", verdict);
        if (INCONCLUSIVE.equals(verdict)) {
            // never fired comes first: when no fault ran, the expectations that did not decide are a consequence of it;
            // failures not counted come before not decided, as waiting longer would not count them either
            jo.put("reason", neverFired
                    ? NEVER_FIRED : idle ? noMessages(metRoutes) : hidden ? uncountedFailures(uncounted) : NOT_DECIDED);
        }
        if (countsReset) {
            jo.put("countsReset", true);
        }
        return jo;
    }

    private static long[] read(ChaosManager.RouteCounts c) {
        return new long[] {
                c.completed.sum(), c.failed.sum(), c.failedKept.sum(), c.handled.sum(), c.redeliveries.sum() };
    }

    /** One line for the experiment and one per expectation, as the report prints them. */
    private static String text(JsonObject jo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n      ").append(jo.getInteger("number")).append(' ').append(jo.getString("verdict"));
        String hypothesis = jo.getString("hypothesis");
        if (hypothesis != null) {
            sb.append("  ").append(hypothesis);
        }
        sb.append(" (").append(String.join(", ", jo.<List<String>> getCollection("rules"))).append(", ");
        if (jo.getBooleanOrDefault("open", false)) {
            sb.append("running for ").append(ChaosRuleParser.printTime(jo.getLong("elapsed")));
        } else {
            String by = jo.getString("endedBy");
            sb.append(End.DEADLINE.key.equals(by)
                    ? "ended at the deadline" : End.FINISHED.key.equals(by) ? "ended with the exploration" : "ended")
                    .append(" after ").append(ChaosRuleParser.printTime(jo.getLong("elapsed")));
        }
        sb.append(')');
        for (Object o : jo.getCollection("expectations")) {
            JsonObject x = (JsonObject) o;
            sb.append("\n        expect ").append(x.getString("expect")).append(": ").append(x.getString("state"))
                    .append(", ").append(x.getString("value"));
        }
        String reason = jo.getString("reason");
        if (reason != null) {
            sb.append("\n        inconclusive: ").append(reason);
        }
        long lost = jo.getLongOrDefault("lostAtDeadLetterChannel", 0);
        if (lost > 0) {
            sb.append("\n        lost at the dead letter channel: ").append(lost)
                    .append(lost == 1 ? " message" : " messages").append(", handled there without being delivered");
        }
        JsonObject alsoFailed = (JsonObject) jo.get("alsoFailed");
        if (alsoFailed != null) {
            StringJoiner also = new StringJoiner(", ");
            alsoFailed.forEach((route, n) -> also.add(route + " " + n));
            sb.append("\n        also failed, in no expectation: ").append(also);
        }
        StringJoiner sj = new StringJoiner(", ");
        for (Object o : jo.getCollection("routes")) {
            JsonObject r = (JsonObject) o;
            sj.add(r.getString("route") + " " + r.getLong("completed") + " completed, " + r.getLong("failed") + " failed");
        }
        if (sj.length() > 0) {
            sb.append("\n        routes: ").append(sj);
        }
        String note = jo.getString("note");
        if (note != null) {
            sb.append("\n        note: ").append(note);
        }
        return sb.toString();
    }
}
