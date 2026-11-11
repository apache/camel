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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The question the AI panel is opened with to explain what a chaos rule did (Shift+F8 on the Chaos screen, and on an
 * error a rule made on the Errors tab), CAMEL-26702: the rule, its live state and the counts of the routes, and what to
 * do with them - say in plain words what the fault simulates and what the routes did with it, then suggest how they
 * should cope, and change the routes only when the user agrees. It is put in the input of the panel, for the user to
 * send with Enter or change first. The question to make a test of a rule ({@code t}, CAMEL-26725) is put there too.
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

    /**
     * The question to make a test of a rule ({@code t} on the Chaos screen, CAMEL-26725), so the finding runs again
     * with the same faults: the rule, the seed and what the rule did, then a JUnit test with {@code ChaosAssertions}
     * for a Maven project, or a scenario with an expectation and the run whose exit code is the verdict for a folder
     * that runs with {@code camel run}. The AI writes the file with the file tools of the panel, which ask first.
     */
    static String ofTest(IntegrationInfo info, ChaosInfo.Rule rule) {
        long seed = info.chaos != null ? info.chaos.seed : 0;
        // a rule draws its random choices by a key: its id here, its place in a scenario or camel.chaos.rules there, so
        // the test names it with draw= to fail the same messages (CAMEL-26732)
        String replay = replayRule(rule);
        StringBuilder sb = new StringBuilder();
        sb.append("Make a test of chaos rule ").append(rule.id).append(" of the running integration ").append(name(info))
                .append(", so this finding runs again with the same faults. The rule (camel-chaos, injected on purpose):\n")
                .append("  ").append(rule.text).append('\n').append("The seed of its random draws: ").append(seed)
                .append(".\n");
        state(sb, info, rule);
        String expectation = rule.expectRoute != null
                ? "The rule has an expectation: keep it."
                : "The rule has no expectation (expect=): add one that says what must hold, such as"
                  + " expect=<route>:<count> within=10s (at least that many exchanges complete in the route) or"
                  + " expect=<route>:handled<=0 (no message goes to the dead letter channel).";
        if (isMavenProject(info)) {
            sb.append("The integration is a Maven project. Read its routes first, then write a JUnit test in src/test/java")
                    .append(" that runs them with chaos switched on: add org.apache.camel:camel-chaos as a test dependency")
                    .append(" when the pom.xml does not have it, and set the properties camel.chaos.enabled=true,")
                    .append(" camel.chaos.seed=").append(seed).append(" and camel.chaos.rules=").append(replay)
                    .append(" (draw=").append(rule.id).append(" makes it fail the same messages as rule ").append(rule.id)
                    .append(" did; CamelTestSupport:")
                    .append(" override configureContext and call config.withUseOverridePropertiesWithPropertiesComponent(props);")
                    .append(" a camel-test-main-junit6 test sets the same properties in its configuration). ")
                    .append(expectation)
                    .append(" Send the messages the test needs to the route (replace a consumer that needs a broker or a")
                    .append(" server with a direct endpoint, using AdviceWith), and assert with")
                    .append(" org.apache.camel.component.chaos.ChaosAssertions: assertFired(context, \"").append(rule.id)
                    .append("\", <at least>), assertExpectationMet(context, \"").append(rule.id)
                    .append("\", Duration.ofSeconds(10)) and assertVerdict(context, ChaosReport.PASS, Duration.ofSeconds(10)).");
        } else {
            sb.append("The integration runs with camel run, without a Maven project, so the test is a scenario with an")
                    .append(" expectation, and a run whose exit code is the verdict. Write the scenario test/chaos/")
                    .append(rule.id).append("-test.chaos: a comment line that says in words what it tests (not the rule")
                    .append(" again), seed=").append(seed).append(", and the rule as the line ").append(replay)
                    .append(" (draw=").append(rule.id).append(" makes it fail the same messages as rule ").append(rule.id)
                    .append(" did). ").append(expectation)
                    .append(" Then give the command that runs it, with --max-seconds longer than the within= of the")
                    .append(" expectation: camel run * --chaos=test/chaos/").append(rule.id)
                    .append("-test.chaos --max-seconds=<seconds> --chaos-report=target/chaos-report.json. It ends with exit")
                    .append(" code 1 when the verdict is FAIL, so it can run in a pipeline.");
        }
        sb.append(" Write the file with camel_write_file, which asks me first, and change no route.");
        return sb.toString();
    }

    /** The rule as a test writes it: with draw=<its id>, unless it has a draw= already. */
    static String replayRule(ChaosInfo.Rule rule) {
        String text = rule.text.strip();
        return text.contains(" draw=") ? text : text + " draw=" + rule.id;
    }

    /** Whether the folder of the integration is a Maven project, which can have a JUnit test. */
    static boolean isMavenProject(IntegrationInfo info) {
        return info.directory != null && Files.isRegularFile(Path.of(info.directory, "pom.xml"));
    }

    /** The most routes and sends quoted in a request, so a big integration does not flood it. */
    static final int MAX_LISTED = 20;

    /**
     * The question for a request in the user's own words ({@code d} on the Chaos screen, CAMEL-26721), such as "the
     * order route should fail 5% of the time": the request, the routes and the sends of the integration and its rules
     * now, the rule syntax in short, and what to do - write the rules, check them with tui_chaos_check (which says what
     * each would hit, in plain words), show them, and add them only with tui_chaos_add (or change one with
     * tui_chaos_change), which ask the user first.
     */
    static String ofRequest(IntegrationInfo info, String request) {
        return ofRequest(info, request, null);
    }

    /**
     * The question for a request in the user's own words, with the known rules of camel-chaos (the recipes of the chaos
     * model, a line each with its rule), which show the forms a short syntax leaves out: a timeout, a stalled route,
     * the polls of a consumer, the dead letter channel.
     *
     * @param recipes the known rules, or null when the catalog has none
     */
    static String ofRequest(IntegrationInfo info, String request, ChaosRecipes recipes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Turn my request into camel-chaos rules for the running integration ").append(name(info))
                .append(" (development only, faults injected on purpose). My request: ").append(request.strip())
                .append('\n');
        int listed = 0;
        StringBuilder routes = new StringBuilder();
        for (RouteInfo route : info.routes) {
            if (route.routeId != null && listed++ < MAX_LISTED) {
                routes.append("  ").append(route.routeId);
                if (route.from != null) {
                    routes.append(" from ").append(route.from);
                }
                routes.append('\n');
            }
        }
        if (!routes.isEmpty()) {
            sb.append("Its routes:\n").append(routes);
        }
        // the endpoint a route consumes is not one of its sends (a send to it from another route is)
        List<String> consumed = new ArrayList<>();
        info.routes.forEach(r -> {
            if (r.from != null && r.routeId != null) {
                consumed.add(r.routeId + " " + sameUri(r.from));
            }
        });
        List<String> sends = new ArrayList<>();
        for (EndpointInfo endpoint : info.endpoints) {
            if ("out".equals(endpoint.direction) && endpoint.uri != null && sends.size() < MAX_LISTED
                    && !consumed.contains(endpoint.routeId + " " + sameUri(endpoint.uri))) {
                String send = withoutQuery(endpoint.uri) + (endpoint.routeId != null ? " (from " + endpoint.routeId + ")" : "");
                if (!sends.contains(send)) {
                    sends.add(send);
                }
            }
        }
        if (!sends.isEmpty()) {
            sb.append("It sends to:\n");
            sends.forEach(s -> sb.append("  ").append(s).append('\n'));
        }
        ChaosInfo chaos = info.chaos;
        if (chaos != null && !chaos.rules.isEmpty()) {
            sb.append("Its chaos rules now, which are active already (add only new rules; change one of these with ")
                    .append(TuiToolGroups.CHAOS_CHANGE_TOOL).append(" and its id):\n");
            chaos.rules.forEach(r -> sb.append("  ").append(r.id).append(": ").append(r.text).append('\n'));
        }
        sb.append("A rule is one line: <target> <fault> [<parameter>=<value> ...] [<option>=<value> ...]. Targets:")
                .append(" endpoint=<uri pattern>, component=<scheme>, route=<route id>, step=<step id>, with * for any")
                .append(" characters. Faults: error (exception=<class>, message=, status=<http code> for http),")
                .append(" delay (duration=500ms or 100ms..2s), drop, corrupt (mode=headers|charset|truncate|empty),")
                .append(" duplicate, stall (route=, duration=). Options: rate=0.05 (or every:N), until=10m,")
                .append(" in=<route id> for the sends and steps of a route, when=\"${header.country} == 'ES'\" for")
                .append(" the messages that meet a condition (simple language). route=<id> error fails 5% of the")
                .append(" exchanges of a route with rate=0.05; \"or\" is two rules. Each rule has its own rate: without")
                .append(" rate= a rule fails every message it matches.\n");
        if (recipes != null && !recipes.recipes().isEmpty()) {
            sb.append("Known rules, with ${...} to fill in:\n");
            recipes.recipes().forEach(r -> sb.append("  ").append(r.title()).append(": ").append(r.rule()).append('\n'));
        }
        sb.append("The routes and sends above are all you need: do not look around with other tools. First call ")
                .append(TuiToolGroups.CHAOS_CHECK_TOOL).append(" with the rules (several separated by ;): it says each")
                .append(" rule in plain words and what it would hit, or why it is not valid. Fix the rules until it")
                .append(" accepts them and they hit what I asked for. Then show me each rule with its plain words and")
                .append(" what it hits, and add them with ").append(TuiToolGroups.CHAOS_ADD_TOOL)
                .append(" (or change a rule above with ").append(TuiToolGroups.CHAOS_CHANGE_TOOL)
                .append("), which asks me to confirm first. Do not change the routes.");
        return sb.toString();
    }

    // a URI without its query and the // after its scheme, to tell the endpoint a route consumes
    private static String sameUri(String uri) {
        return withoutQuery(uri).replaceFirst(":/+", ":");
    }

    private static String withoutQuery(String uri) {
        int query = uri.indexOf('?');
        return query > 0 ? uri.substring(0, query) : uri;
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
            sb.append("Expectation: ").append(rule.expectSpec != null
                    ? rule.expectSpec : rule.expectRoute + ":" + rule.expectKind + rule.expectOp + rule.expectCount)
                    .append(", ").append(rule.expectOutcome != null ? rule.expectOutcome : "pending")
                    .append(" (").append("meanTime".equals(rule.expectKind) || "maxTime".equals(rule.expectKind)
                            // a time is in milliseconds
                            ? ChaosTab.shortTime(rule.expectValue) : String.valueOf(rule.expectValue))
                    .append(' ').append(rule.expectKind).append(").\n");
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
