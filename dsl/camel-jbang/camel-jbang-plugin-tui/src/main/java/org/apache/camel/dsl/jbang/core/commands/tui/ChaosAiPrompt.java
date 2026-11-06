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
import java.util.List;

/**
 * The question the AI panel is opened with to explain what a chaos rule did (Shift+F8 on the Chaos screen, and on an
 * error a rule made on the Errors tab), CAMEL-26702: the rule, its live state and the counts of the routes, and what to
 * do with them - say in plain words what the fault simulates and what the routes did with it, then suggest how they
 * should cope, and change the routes only when the user agrees. It is put in the input of the panel, for the user to
 * send with Enter or change first.
 */
final class ChaosAiPrompt {

    /** The most recent faults quoted, so a busy rule does not flood the question. */
    static final int MAX_RECENT = 5;

    private ChaosAiPrompt() {
    }

    /** The question for a rule of the Chaos screen. */
    static String of(IntegrationInfo info, ChaosInfo.Rule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain what chaos rule ").append(rule.id).append(" did to the running integration ")
                .append(name(info)).append(", and what impact it had. The rule (camel-chaos, injected on purpose):\n")
                .append("  ").append(rule.text).append('\n');
        state(sb, info, rule);
        sb.append("Say in plain words what the fault simulates, what the routes did with it (which exchanges recovered,")
                .append(" which the error handler handled, which failed) and whether a message was lost. Then suggest how")
                .append(" the routes should cope with this in production, such as redelivery with a delay, an idempotent")
                .append(" consumer, a circuit breaker or a timeout. ").append(onlyWhenAgreed());
        return sb.toString();
    }

    /** The question for an error a chaos rule made, on the Errors tab: it is not a bug to fix. */
    static String ofError(IntegrationInfo info, ChaosInfo.Rule rule, String ruleId, ErrorInfo error) {
        StringBuilder sb = new StringBuilder();
        sb.append("This error of the running integration ").append(name(info)).append(" was injected on purpose by chaos")
                .append(" rule ").append(ruleId);
        if (rule != null) {
            sb.append(" (camel-chaos): ").append(rule.text);
        }
        sb.append('\n');
        sb.append("  ").append(RuntimeFailures.describe(error));
        if (error.routeId != null) {
            sb.append(" in route ").append(error.routeId);
            if (error.nodeId != null) {
                sb.append(" at ").append(error.nodeId);
            }
        }
        sb.append(error.handled ? " (handled by the error handler)" : " (not handled)").append('\n');
        if (rule != null) {
            state(sb, info, rule);
        }
        sb.append("Do not treat it as a bug to fix. Explain what happened to the exchange and how the route coped with")
                .append(" the fault, and suggest how it should cope in production if it did not. ").append(onlyWhenAgreed());
        return sb.toString();
    }

    /** The live state of the rule, and the counts of the routes its recent faults were in. */
    private static void state(StringBuilder sb, IntegrationInfo info, ChaosInfo.Rule rule) {
        sb.append("Its state now: ").append(rule.state != null ? rule.state : "unknown").append(", fired ")
                .append(rule.fired).append(" times of ").append(rule.matches).append(" matches");
        String rate = ChaosTab.rate(rule);
        if (!rate.isEmpty() && !"all".equals(rate)) {
            sb.append(" (rate ").append(rate).append(')');
        }
        sb.append(".\n");
        if (rule.faulted > 0) {
            sb.append("Effect on ").append(rule.faulted).append(rule.faulted == 1 ? " exchange: " : " exchanges: ")
                    .append(effect(rule)).append(".\n");
        } else if (rule.firedAt.getOrDefault("poll", 0L) > 0) {
            sb.append("It fired at ").append(rule.firedAt.get("poll")).append(" polls of a consumer (no exchange).\n");
        }
        if (rule.expectRoute != null) {
            sb.append("Expectation: ").append(rule.expectRoute).append(':').append(rule.expectKind).append(rule.expectOp)
                    .append(rule.expectCount).append(", ").append(rule.expectOutcome != null ? rule.expectOutcome : "pending")
                    .append(" (").append(rule.expectValue).append(' ').append(rule.expectKind).append(").\n");
        }
        if (rule.lostAtDlq > 0) {
            sb.append("Messages lost at the dead letter channel: ").append(rule.lostAtDlq).append(".\n");
        }
        ChaosInfo chaos = info.chaos;
        if (chaos == null) {
            return;
        }
        List<String> routes = new ArrayList<>();
        int recent = 0;
        StringBuilder faults = new StringBuilder();
        for (ChaosInfo.RecentFault f : chaos.recentFaults) {
            if (rule.id == null || !rule.id.equals(f.rule())) {
                continue;
            }
            if (f.route() != null && !routes.contains(f.route())) {
                routes.add(f.route());
            }
            if (recent++ < MAX_RECENT) {
                faults.append("  ").append(f.fault() != null ? f.fault() : "fault");
                if (f.hook() != null) {
                    faults.append(" at ").append(f.hook());
                    if (f.where() != null) {
                        faults.append(' ').append(f.where());
                    }
                }
                if (f.route() != null) {
                    faults.append(" in route ").append(f.route());
                }
                faults.append('\n');
            }
        }
        if (!faults.isEmpty()) {
            sb.append("Its recent faults, the latest first:\n").append(faults);
        }
        for (ChaosInfo.RouteCounts counts : chaos.routes) {
            if (routes.contains(counts.route())) {
                sb.append("Route ").append(counts.route()).append(" since chaos started: ").append(counts.completed())
                        .append(" completed, ").append(counts.failed()).append(" failed, ").append(counts.handled())
                        .append(" handled by the error handler, ").append(counts.redeliveries()).append(" redeliveries.\n");
            }
        }
    }

    private static String effect(ChaosInfo.Rule rule) {
        List<String> parts = new ArrayList<>();
        add(parts, rule.completed, "completed");
        add(parts, rule.recovered, "recovered by redelivery");
        add(parts, rule.handled, "handled by the error handler");
        add(parts, rule.failed, "failed");
        add(parts, rule.inFlight, "in flight");
        return parts.isEmpty() ? "none yet" : String.join(", ", parts);
    }

    private static void add(List<String> parts, long count, String words) {
        if (count > 0) {
            parts.add(count + " " + words);
        }
    }

    private static String onlyWhenAgreed() {
        return "The camel_runtime_chaos_report tool has the full report. Propose changes to the routes, and make them"
               + " with camel_edit_file only when I agree.";
    }

    private static String name(IntegrationInfo info) {
        return info.name != null ? info.name : info.pid;
    }
}
