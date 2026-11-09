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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
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
 * The verdict is {@code FAIL} when an expectation is unmet and {@code PASS} when all are met. While it runs and an
 * expectation is not decided it is {@code PENDING}, and an experiment that ended before an expectation was decided is
 * {@code INCONCLUSIVE}: the messages may still have come. The manager guards it with its lock.
 */
final class ChaosExperiment {

    static final String PASS = "PASS";
    static final String FAIL = "FAIL";
    static final String PENDING = "PENDING";
    static final String INCONCLUSIVE = "INCONCLUSIVE";

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
        JsonArray texts = new JsonArray();
        JsonObject faults = new JsonObject();
        JsonArray expectations = new JsonArray();
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
            faults.put(id, fired);
            Expectation e = r.expectation;
            if (e != null) {
                Expectation.Reading reading = e.read(now);
                // an expectation the experiment did not wait for is undecided, not cancelled
                Outcome outcome = reading.outcome() == Outcome.CANCELLED ? Outcome.PENDING : reading.outcome();
                unmet |= outcome == Outcome.UNMET;
                undecided |= outcome == Outcome.PENDING;
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
        jo.put("verdict", unmet ? FAIL : !undecided ? PASS : ended ? INCONCLUSIVE : PENDING);
        JsonArray routes = new JsonArray();
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
                JsonObject r = new JsonObject();
                r.put("route", route);
                r.put("completed", delta[0]);
                r.put("failed", delta[1]);
                r.put("failedKeptByConsumer", delta[2]);
                r.put("handled", delta[3]);
                r.put("redeliveries", delta[4]);
                routes.add(r);
            }
        });
        jo.put("routes", routes);
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
