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
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.State;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * What the chaos rules did, as of the time the report was made: the seed, and each rule with its state, its matches and
 * the faults that fired at each hook.
 *
 * @since 4.24
 */
public final class ChaosReport {

    private final long seed;
    private final boolean seedGiven;
    private final List<RuleEntry> rules = new ArrayList<>();

    ChaosReport(long seed, boolean seedGiven, long now, List<ChaosRule> current, List<ChaosRule> removed) {
        this.seed = seed;
        this.seedGiven = seedGiven;
        for (ChaosRule rule : current) {
            rules.add(new RuleEntry(rule, now));
        }
        for (ChaosRule rule : removed) {
            rules.add(new RuleEntry(rule, now));
        }
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
        sb.append("Chaos report");
        sb.append("\n  seed: ").append(seed).append(seedGiven ? " (camel.chaos.seed)" : " (picked at startup)");
        sb.append("\n  rules:");
        if (rules.isEmpty()) {
            sb.append(" none");
        }
        for (RuleEntry r : rules) {
            sb.append("\n    ").append(r.id).append(' ').append(r.state.key).append(": ").append(r.text);
            sb.append("\n      matches ").append(r.matches).append(", fired ").append(r.firedTotal);
            StringJoiner at = new StringJoiner(", ", " (", ")");
            at.setEmptyValue("");
            for (Hook hook : Hook.values()) {
                if (r.fired[hook.ordinal()] > 0) {
                    at.add(hook.key + " " + r.fired[hook.ordinal()]);
                }
            }
            sb.append(at);
            if (r.state == State.ACTIVE) {
                sb.append(", ends in ").append(ChaosRuleParser.printTime(r.endsIn));
            }
        }
        return sb.toString();
    }

    /**
     * The report as JSON, for a tool.
     */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.put("seed", seed);
        root.put("seedGiven", seedGiven);
        JsonArray ruleArray = new JsonArray();
        for (RuleEntry r : rules) {
            JsonObject jo = new JsonObject();
            jo.put("id", r.id);
            jo.put("rule", r.text);
            jo.put("state", r.state.key);
            JsonArray hooks = new JsonArray();
            JsonObject fired = new JsonObject();
            for (Hook hook : Hook.values()) {
                if (r.hooks.contains(hook)) {
                    hooks.add(hook.key);
                    fired.put(hook.key, r.fired[hook.ordinal()]);
                }
            }
            jo.put("hooks", hooks);
            jo.put("matches", r.matches);
            jo.put("fired", fired);
            if (r.state == State.ACTIVE) {
                jo.put("endsIn", r.endsIn);
            }
            ruleArray.add(jo);
        }
        root.put("rules", ruleArray);
        return root;
    }

    @Override
    public String toString() {
        return toText();
    }

    /** A rule in the report. */
    private static final class RuleEntry {
        final String id;
        final String text;
        final State state;
        final Set<Hook> hooks;
        final long matches;
        final long[] fired = new long[Hook.values().length];
        final long firedTotal;
        final long endsIn;

        RuleEntry(ChaosRule rule, long now) {
            id = rule.id;
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
            endsIn = Math.max(0, rule.endsAt - now);
        }
    }
}
