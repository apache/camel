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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@CommandDefinition(name = "sanitizer",
                   description = "Get the violations the exchange sanitizer reported in running integrations (camel-sanitizer)",
                   generateHelp = true,
                   footer = {
                           "Lists the violations of the integrations that have camel-sanitizer switched",
                           "on: the rule, the kind, the route, the step, the processor and the exchange",
                           "(for rule R2 the processor is the class of the commit of the consumer).",
                           "With --report it shows the report of each integration instead: the counts per",
                           "rule and per kind, the commits followed, each violation with its threads and",
                           "stacks, and the calls reported still pending (warnings).",
                           "",
                           "Examples:",
                           "  camel get sanitizer",
                           "  camel get sanitizer myApp --report",
                           "  camel get sanitizer --json" })
public class ListSanitizer extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    @Option(name = "report", hasValue = false,
            description = "Show the report: the counts per rule and per kind, the commits followed, each violation with its threads and stacks, and the still pending warnings")
    boolean report;

    @Option(name = "sort", description = "Sort by pid, name or age", defaultValue = "pid",
            completer = PidNameAgeCompletionCandidates.class)
    String sort;

    public ListSanitizer(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> integrations = new ArrayList<>();
        forEachIntegration(name, (ph, root) -> {
            JsonObject context = (JsonObject) root.get("context");
            if (context == null) {
                return;
            }
            Row row = new Row();
            row.name = context.getString("name");
            if ("CamelJBang".equals(row.name)) {
                row.name = ProcessHelper.extractName(root, ph);
            }
            row.pid = Long.toString(ph.pid());
            row.uptime = extractSince(ph);
            // camel-sanitizer is new in Camel 4.24: an older integration cannot have it, and would not answer
            String version = context.getString("version");
            row.beforeSanitizer = version != null && !ActionClient.isAtLeast(version, 4, 24);
            JsonObject answer = row.beforeSanitizer ? new JsonObject() : loadSanitizer(ph.pid());
            row.answered = answer != null;
            row.sanitizer = answer != null && !answer.isEmpty() ? answer : null;
            integrations.add(row);
        });
        integrations.sort(ORDER.comparator(sort));

        if (report) {
            printReports(integrations);
        } else {
            printViolations(integrations);
        }
        return 0;
    }

    /**
     * Asks the integration for the sanitizer dev console: its answer; an empty object when the integration does not
     * have camel-sanitizer, or its CLI connector does not know the action (before Camel 4.24); null when it did not
     * answer. Without --report it asks without the reports of the violations and the still pending warnings, which the
     * table does not show.
     */
    JsonObject loadSanitizer(long pid) {
        JsonObject request = sanitizerRequest();
        try {
            ActionClient.ActionResult result = ActionClient.execute(pid, request, ActionClient.DEFAULT_TIMEOUT);
            if (result.status() == ActionClient.Status.TIMEOUT) {
                return null;
            }
            return result.isOk() && result.reply() != null && result.reply().containsKey("enabled")
                    ? result.reply() : new JsonObject();
        } catch (IOException e) {
            return null;
        }
    }

    JsonObject sanitizerRequest() {
        JsonObject request = new JsonObject();
        request.put("action", "sanitizer");
        if (!report) {
            request.put("report", "false");
        }
        return request;
    }

    private void printViolations(List<Row> integrations) {
        List<ViolationRow> rows = new ArrayList<>();
        for (Row integration : integrations) {
            if (integration.isOn()) {
                for (Object o : integration.violations()) {
                    rows.add(new ViolationRow(integration, (JsonObject) o));
                }
            }
        }
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", JsonValues.pid(r.integration.pid));
                jo.put("name", r.integration.name);
                for (String key : List.of("rule", "kind", "title", "routeId", "stepId", "processor", "exchangeId")) {
                    if (r.violation.get(key) != null) {
                        jo.put(key, r.violation.get(key));
                    }
                }
                return jo;
            }).toList()));
            return;
        }
        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, List.of(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.integration.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT).with(r -> r.integration.name),
                    new Column().header("RULE").dataAlign(HorizontalAlign.LEFT).with(r -> r.violation.getString("rule")),
                    new Column().header("VIOLATION").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(45, OverflowBehaviour.NEWLINE).with(r -> r.violation.getString("title")),
                    new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT).with(r -> r.violation.getString("routeId")),
                    // a violation of R2 found where no step ran has no step
                    new Column().header("STEP").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.violation.getStringOrDefault("stepId", "")),
                    new Column().header("PROCESSOR").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(60, OverflowBehaviour.ELLIPSIS_LEFT)
                            .with(r -> r.violation.getStringOrDefault("processor", "")),
                    new Column().header("EXCHANGE").dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.violation.getString("exchangeId")))));
        }
        printNotes(integrations);
    }

    private void printReports(List<Row> integrations) {
        if (jsonOutput) {
            JsonArray answer = new JsonArray();
            for (Row integration : integrations) {
                if (integration.isOn()) {
                    JsonObject jo = new JsonObject();
                    jo.put("pid", JsonValues.pid(integration.pid));
                    jo.put("name", integration.name);
                    for (String key : List.of("calls", "commits", "total", "rules", "kinds", "violations", "pendingCalls",
                            "stillPending", "stillPendingWarnings")) {
                        // commits only with R2, and the pending calls only with the still pending warning on
                        if (integration.sanitizer.get(key) != null) {
                            jo.put(key, integration.sanitizer.get(key));
                        }
                    }
                    answer.add(jo);
                }
            }
            printer().println(Jsoner.serialize(answer));
            return;
        }
        boolean first = true;
        for (Row integration : integrations) {
            if (!integration.isOn()) {
                continue;
            }
            if (!first) {
                printer().println();
            }
            first = false;
            JsonObject s = integration.sanitizer;
            StringBuilder head = new StringBuilder();
            head.append(integration.name).append(" (pid ").append(integration.pid).append("): ")
                    .append(s.getLongOrDefault("total", 0)).append(" violations in ").append(s.getLongOrDefault("calls", 0))
                    .append(" calls of steps checked");
            // R2 is checked only when the commits are followed (by default only in a test)
            boolean r2 = s.get("commits") != null;
            if (r2) {
                head.append(", ").append(s.getLongOrDefault("commits", 0)).append(" commits followed");
            }
            if (s.get("stillPending") != null) {
                head.append(", ").append(s.getLongOrDefault("stillPending", 0)).append(" calls reported still pending")
                        .append(" (warnings), ").append(s.getLongOrDefault("pendingCalls", 0)).append(" pending now");
            }
            if (!r2) {
                head.append(", R2 not checked (camel.sanitizer.checks=callback,commit)");
            }
            printer().println(head.toString());
            if (s.get("rules") instanceof JsonObject rules) {
                for (Map.Entry<String, Object> e : rules.entrySet()) {
                    if (r2 || !"R2".equals(e.getKey())) {
                        printer().println("  " + e.getKey() + ": " + e.getValue());
                    }
                }
            }
            // the kinds at the level of the rules: under the last rule, they would look as if they were of that rule
            if (s.get("kinds") instanceof JsonObject kinds) {
                for (Map.Entry<String, Object> e : kinds.entrySet()) {
                    if (e.getValue() instanceof Number n && n.longValue() > 0) {
                        printer().println("  " + e.getKey() + ": " + n);
                    }
                }
            }
            for (Object o : integration.violations()) {
                printer().println();
                printer().println(((JsonObject) o).getString("report"));
            }
            if (integration.unlisted() > 0) {
                printer().println();
                printer().println("(" + integration.unlisted() + " more violations are counted, but not listed)");
            }
            for (Object o : integration.stillPendingWarnings()) {
                printer().println();
                printer().println(String.valueOf(o));
            }
        }
        printNotes(integrations);
    }

    /**
     * On stderr: why an integration lists nothing, the violations that are counted but not listed, an integration that
     * did not answer, and when no integration has camel-sanitizer.
     */
    private void printNotes(List<Row> integrations) {
        boolean any = false;
        for (Row integration : integrations) {
            if (!integration.answered) {
                any = true;
                printer().printDiagnostic(integration.name + " (pid " + integration.pid + "): did not answer in time");
                continue;
            }
            if (integration.sanitizer == null) {
                continue;
            }
            any = true;
            String prefix = integration.name + " (pid " + integration.pid + "): ";
            if (!integration.isOn()) {
                printer().printDiagnostic(prefix + integration.sanitizer.getStringOrDefault("reason", "the sanitizer is off"));
            } else if (integration.violations().isEmpty() && !report) {
                long stillPending = integration.sanitizer.getLongOrDefault("stillPending", 0);
                printer().printDiagnostic(prefix + "no violations (" + integration.sanitizer.getLongOrDefault("calls", 0)
                                          + " calls of steps checked)"
                                          + (stillPending > 0
                                                  ? ", " + stillPending + " calls reported still pending (see --report)"
                                                  : ""));
            } else {
                if (!report && integration.unlisted() > 0) {
                    printer().printDiagnostic(
                            prefix + integration.unlisted() + " more violations are counted, but not listed");
                }
                long stillPending = integration.sanitizer.getLongOrDefault("stillPending", 0);
                if (!report && stillPending > 0) {
                    printer().printDiagnostic(prefix + stillPending + " calls reported still pending (see --report)");
                }
            }
        }
        if (!any && !integrations.isEmpty()) {
            if (integrations.stream().allMatch(r -> r.beforeSanitizer)) {
                // camel run --sanitizer would not help them
                printer().printDiagnostic(
                        "No running integration has camel-sanitizer: it is new in Camel 4.24, and they are older");
            } else {
                printer().printDiagnostic("No running integration has camel-sanitizer: run it with camel run --sanitizer");
            }
        }
    }

    private static class Row {
        String pid;
        String name;
        long uptime;
        boolean answered;
        // older than Camel 4.24, which has camel-sanitizer: not asked
        boolean beforeSanitizer;
        JsonObject sanitizer;

        boolean isOn() {
            return sanitizer != null && sanitizer.getBooleanOrDefault("enabled", false);
        }

        JsonArray violations() {
            JsonArray list = sanitizer != null ? sanitizer.getCollection("violations") : null;
            return list != null ? list : new JsonArray();
        }

        JsonArray stillPendingWarnings() {
            JsonArray list = sanitizer != null ? sanitizer.getCollection("stillPendingWarnings") : null;
            return list != null ? list : new JsonArray();
        }

        long unlisted() {
            return Math.max(0, sanitizer.getLongOrDefault("total", 0) - violations().size());
        }
    }

    private record ViolationRow(Row integration, JsonObject violation) {
    }
}
