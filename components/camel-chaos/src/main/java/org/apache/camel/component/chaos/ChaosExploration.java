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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The journal of an exploration (CAMEL-26773): an agent tries chaos rules on the integration for a time it was given,
 * and camel-chaos keeps what it did, so the report, the CLI and the TUI read it, and it survives the agent losing its
 * context. camel-chaos also enforces the time: a rule added while it runs ends at the deadline at the latest, and at
 * the deadline the rules are removed and the exploration is finished, whatever the agent does.
 * <p/>
 * It has the baseline, the counts per route when it started, so the failures that were there before are not blamed on
 * chaos, the rules added while it ran, and its experiments (CAMEL-26774): a hypothesis and the rules that test it, one
 * at a time. The manager guards it with its lock.
 */
final class ChaosExploration {

    static final long DEFAULT_BUDGET = 10 * 60_000L;
    static final long MIN_BUDGET = 10_000L;
    static final long MAX_BUDGET = 2 * 60 * 60_000L;

    /** How an exploration finished. */
    enum Finish {
        // finished before the deadline (finishExploration)
        FINISHED("finished"),
        // the time was up
        DEADLINE("deadline");

        final String key;

        Finish(String key) {
            this.key = key;
        }
    }

    final long startedAt;
    final long budget;
    final long deadline;
    final String focus;
    // the counts per route before its first rule (the run without faults), taken when the first rule is added:
    // completed, failed, kept by the consumer, handled, redeliveries
    private final Map<String, long[]> baseline = new LinkedHashMap<>();
    private long baselineAt;
    // the report was reset while it ran: the counts of the routes started again from zero
    private boolean baselineReset;
    // the ids of the rules added while it ran (a changed rule keeps its id)
    private final List<String> rules = new ArrayList<>();
    // the experiments, the open one last
    private final List<ChaosExperiment> experiments = new ArrayList<>();
    private long finishedAt;
    private Finish finishedBy;
    private int removed;

    ChaosExploration(long startedAt, long budget, String focus) {
        this.startedAt = startedAt;
        this.budget = budget;
        this.deadline = startedAt + budget;
        this.focus = focus != null && !focus.isBlank() ? focus.strip() : null;
    }

    boolean isRunning() {
        return finishedBy == null;
    }

    /** A rule added while it runs: the first one takes the baseline, the counts of the run without faults. */
    void ruleAdded(String id, Map<String, ChaosManager.RouteCounts> counts, long now) {
        if (baselineAt == 0) {
            baselineAt = now;
            counts.forEach((route, c) -> baseline.put(route, new long[] {
                    c.completed.sum(), c.failed.sum(), c.failedKept.sum(), c.handled.sum(), c.redeliveries.sum() }));
        }
        if (!rules.contains(id)) {
            rules.add(id);
        }
    }

    /** The counts of the routes were reset (a scenario was loaded): the baseline is zero from now on. */
    void reportReset() {
        baseline.clear();
        baselineReset = true;
        ChaosExperiment open = openExperiment();
        if (open != null) {
            open.reportReset();
        }
    }

    /** The experiment that is open, or null. */
    ChaosExperiment openExperiment() {
        ChaosExperiment last = experiments.isEmpty() ? null : experiments.get(experiments.size() - 1);
        return last != null && last.isOpen() ? last : null;
    }

    /** Begins an experiment with the rules just added for it (and taken in the rules tried). */
    ChaosExperiment beginExperiment(
            String hypothesis, List<String> ruleIds, Map<String, ChaosManager.RouteCounts> counts, long now) {
        ChaosExperiment experiment = new ChaosExperiment(experiments.size() + 1, hypothesis, ruleIds, counts, now);
        experiments.add(experiment);
        return experiment;
    }

    int experimentCount() {
        return experiments.size();
    }

    void finished(long now, Finish by, int removedRules) {
        finishedAt = now;
        finishedBy = by;
        removed = removedRules;
    }

    JsonObject toJson(long now) {
        return toJson(now, id -> null, Map.of());
    }

    /**
     * As JSON, with the experiments: the rules of an open one are looked up by their id, and the counts of the routes
     * now give what it did so far.
     */
    JsonObject toJson(long now, Function<String, ChaosRule> rule, Map<String, ChaosManager.RouteCounts> counts) {
        JsonObject jo = new JsonObject();
        jo.put("running", isRunning());
        jo.put("startedAt", startedAt);
        jo.put("budget", budget);
        jo.put("deadline", deadline);
        if (isRunning()) {
            jo.put("timeLeft", Math.max(0, deadline - now));
        }
        if (focus != null) {
            jo.put("focus", focus);
        }
        if (baselineAt > 0) {
            jo.put("baselineAt", baselineAt);
        }
        JsonArray list = new JsonArray();
        baseline.forEach((route, c) -> {
            JsonObject r = new JsonObject();
            r.put("route", route);
            r.put("completed", c[0]);
            r.put("failed", c[1]);
            r.put("failedKeptByConsumer", c[2]);
            r.put("handled", c[3]);
            r.put("redeliveries", c[4]);
            list.add(r);
        });
        jo.put("baseline", list);
        if (baselineReset) {
            jo.put("baselineReset", true);
        }
        jo.put("rules", new JsonArray(rules));
        if (!isRunning()) {
            jo.put("finishedAt", finishedAt);
            jo.put("finishedBy", finishedBy.key);
            jo.put("removed", removed);
        }
        JsonArray tried = new JsonArray();
        for (ChaosExperiment experiment : experiments) {
            tried.add(experiment.toJson(rule, counts, now));
        }
        jo.put("experiments", tried);
        return jo;
    }

    int ruleCount() {
        return rules.size();
    }

    String text(long now) {
        return text(now, id -> null, Map.of());
    }

    String text(long now, Function<String, ChaosRule> rule, Map<String, ChaosManager.RouteCounts> counts) {
        StringBuilder sb = new StringBuilder();
        appendText(sb, now);
        if (!experiments.isEmpty()) {
            sb.append("\n    experiments:");
            for (ChaosExperiment experiment : experiments) {
                sb.append(experiment.text(rule, counts, now));
            }
        }
        return sb.toString();
    }

    void appendText(StringBuilder sb, long now) {
        sb.append("\n  exploration: ");
        if (isRunning()) {
            sb.append("running, ").append(ChaosRuleParser.printTime(Math.max(0, deadline - now))).append(" left of ")
                    .append(ChaosRuleParser.printTime(budget));
        } else {
            sb.append(finishedBy == Finish.DEADLINE ? "finished at the deadline" : "finished")
                    .append(" after ").append(ChaosRuleParser.printTime(finishedAt - startedAt)).append(" of ")
                    .append(ChaosRuleParser.printTime(budget));
            if (removed > 0) {
                sb.append(", ").append(removed).append(removed == 1 ? " rule removed" : " rules removed");
            }
        }
        if (focus != null) {
            sb.append(" (focus: ").append(focus).append(')');
        }
        sb.append("\n    rules tried: ").append(rules.isEmpty() ? "none yet" : String.join(", ", rules));
        StringJoiner sj = new StringJoiner(", ");
        baseline.forEach((route, c) -> sj.add(route + " " + c[0] + " completed, " + c[1] + " failed"));
        sb.append("\n    baseline: ");
        if (baselineAt == 0) {
            sb.append("taken when the first rule is added");
        } else {
            sb.append(sj.length() == 0 ? "no exchanges" : sj.toString()).append(" in the ")
                    .append(ChaosRuleParser.printTime(baselineAt - startedAt)).append(" before the first rule");
        }
        if (baselineReset) {
            sb.append(" (the report was reset while it ran)");
        }
    }
}
