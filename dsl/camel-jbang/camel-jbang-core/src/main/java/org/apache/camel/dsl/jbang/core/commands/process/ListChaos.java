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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "chaos",
         description = "Get the chaos rules of running integrations, or their report (camel-chaos)", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nLists the current rules of the integrations that have camel-chaos switched on: id, version, state,",
                 "matches and the faults that fired. With --report it shows the report instead: the verdict, the seed,",
                 "all rules (also the removed and replaced versions), the counts per route and the reasons of a FAIL.",
                 "%nExamples:",
                 "  camel get chaos",
                 "  camel get chaos myApp --report",
                 "  camel get chaos myApp --report --json" })
public class ListChaos extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--report" },
                        description = "Show the report: verdict, seed, all rules, the counts per route")
    boolean report;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public ListChaos(CamelJBangMain main) {
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
            // the chaos dev console, as the CLI connector puts it in the status (only with camel-chaos)
            row.chaos = (JsonObject) root.get("chaos");
            integrations.add(row);
        });
        integrations.sort(ORDER.comparator(sort));

        if (report) {
            printReports(integrations);
        } else {
            printRules(integrations);
        }
        return 0;
    }

    private void printRules(List<Row> integrations) {
        List<RuleRow> rows = new ArrayList<>();
        for (Row integration : integrations) {
            if (!integration.isOn()) {
                continue;
            }
            JsonArray rules = integration.chaos.getCollection("rules");
            if (rules != null) {
                for (Object o : rules) {
                    rows.add(new RuleRow(integration, (JsonObject) o));
                }
            }
        }
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", JsonValues.pid(r.integration.pid));
                jo.put("name", r.integration.name);
                jo.put("id", r.rule.getString("id"));
                jo.put("version", r.rule.get("version"));
                jo.put("state", r.rule.getString("state"));
                jo.put("rule", r.rule.getString("rule"));
                jo.put("hooks", r.rule.get("hooks"));
                jo.put("matches", r.rule.get("matches"));
                jo.put("fired", r.rule.get("fired"));
                jo.put("skipped", r.rule.get("skipped"));
                if (r.rule.get("endsIn") != null) {
                    jo.put("endsIn", r.rule.get("endsIn"));
                }
                if (r.rule.get("expectation") != null) {
                    jo.put("expectation", r.rule.get("expectation"));
                }
                return jo;
            }).toList()));
            return;
        }
        if (rows.isEmpty()) {
            printOff(integrations);
            return;
        }
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, List.of(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.integration.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.integration.name),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).with(r -> r.rule.getString("id")),
                new Column().header("VER").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> String.valueOf(r.rule.get("version"))),
                new Column().header("STATE").dataAlign(HorizontalAlign.LEFT).with(r -> r.rule.getString("state")),
                new Column().header("MATCHES").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> String.valueOf(r.rule.get("matches"))),
                new Column().header("FIRED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Long.toString(fired(r.rule))),
                new Column().header("SKIPPED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> String.valueOf(r.rule.get("skipped"))),
                new Column().header("ENDS IN").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> endsIn(r.rule)),
                new Column().header("RULE").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(r -> r.rule.getString("rule")))));
    }

    private void printReports(List<Row> integrations) {
        if (jsonOutput) {
            JsonArray answer = new JsonArray();
            for (Row integration : integrations) {
                if (integration.isOn()) {
                    JsonObject jo = new JsonObject();
                    jo.put("pid", JsonValues.pid(integration.pid));
                    jo.put("name", integration.name);
                    jo.put("report", integration.chaos.get("report"));
                    answer.add(jo);
                }
            }
            printer().println(Jsoner.serialize(answer));
            return;
        }
        boolean any = false;
        for (Row integration : integrations) {
            JsonObject report = integration.isOn() ? (JsonObject) integration.chaos.get("report") : null;
            if (report == null) {
                continue;
            }
            if (any) {
                printer().println();
            }
            any = true;
            printReport(integration, report);
        }
        if (!any) {
            printOff(integrations);
        }
    }

    private void printReport(Row integration, JsonObject report) {
        StringBuilder head = new StringBuilder();
        head.append(integration.name).append(" (pid ").append(integration.pid).append("): ")
                .append(report.getString("verdict"));
        if (!report.getBooleanOrDefault("final", true)) {
            head.append(" (not final: an expectation is pending)");
        }
        printer().println(head.toString());
        printer().println("  seed: " + report.get("seed")
                          + (report.getBooleanOrDefault("seedGiven", false) ? " (camel.chaos.seed)" : " (picked at startup)"));
        printer().println("  lost: " + report.get("lost"));
        JsonArray reasons = report.getCollection("reasons");
        if (reasons != null) {
            for (Object reason : reasons) {
                printer().println("  FAIL: " + reason);
            }
        }
        JsonArray rules = report.getCollection("rules");
        if (rules != null && !rules.isEmpty()) {
            List<JsonObject> list = rules.stream().map(JsonObject.class::cast).toList();
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, list, List.of(
                    new Column().header("ID").dataAlign(HorizontalAlign.LEFT).with(r -> r.getString("id")),
                    new Column().header("VER").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("version"))),
                    new Column().header("STATE").dataAlign(HorizontalAlign.LEFT).with(r -> r.getString("state")),
                    new Column().header("MATCHES").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("matches"))),
                    new Column().header("FIRED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> Long.toString(fired(r))),
                    new Column().header("SKIPPED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("skipped"))),
                    new Column().header("EXPECT").dataAlign(HorizontalAlign.LEFT).with(ListChaos::expectation),
                    new Column().header("RULE").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(r -> r.getString("rule")))));
        }
        JsonArray routes = report.getCollection("routes");
        if (routes != null && !routes.isEmpty()) {
            List<JsonObject> list = routes.stream().map(JsonObject.class::cast).toList();
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, list, List.of(
                    new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).with(r -> r.getString("route")),
                    new Column().header("COMPLETED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("completed"))),
                    new Column().header("FAILED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("failed"))),
                    new Column().header("KEPT").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("failedKeptByConsumer"))),
                    new Column().header("HANDLED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("handled"))),
                    new Column().header("REDELIVERIES").headerAlign(HorizontalAlign.RIGHT)
                            .dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> String.valueOf(r.get("redeliveries"))))));
        }
    }

    /**
     * Without a rule to show, says why for each integration where chaos is off, on stderr, so the output stays empty.
     */
    private void printOff(List<Row> integrations) {
        for (Row integration : integrations) {
            if (integration.chaos != null && !integration.isOn()) {
                printer().printDiagnostic(integration.name + " (pid " + integration.pid + "): "
                                          + integration.chaos.getStringOrDefault("reason", "chaos is off"));
            }
        }
    }

    private static long fired(JsonObject rule) {
        long total = 0;
        if (rule.get("fired") instanceof Map<?, ?> fired) {
            for (Object value : fired.values()) {
                if (value instanceof Number n) {
                    total += n.longValue();
                }
            }
        }
        return total;
    }

    private static String endsIn(JsonObject rule) {
        return rule.get("endsIn") instanceof Number n ? TimeUtils.printDuration(n.longValue()) : "";
    }

    private static String expectation(JsonObject rule) {
        if (!(rule.get("expectation") instanceof JsonObject e)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.getString("route")).append(':').append(e.get("count")).append(' ').append(e.getString("outcome"))
                .append(" (").append(e.get("completed")).append(" completed");
        if (e.getBooleanOrDefault("notDecided", false)) {
            // the rule was removed or changed, or the context stopped, first: it stays pending for good
            sb.append(", not decided");
        } else if (e.get("timeLeft") instanceof Number n) {
            sb.append(", ").append(TimeUtils.printDuration(n.longValue())).append(" left");
        }
        return sb.append(')').toString();
    }

    private static class Row {
        String pid;
        String name;
        long uptime;
        JsonObject chaos;

        boolean isOn() {
            return chaos != null && chaos.getBooleanOrDefault("enabled", false);
        }
    }

    private record RuleRow(Row integration, JsonObject rule) {
    }
}
