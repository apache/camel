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
import java.util.StringJoiner;

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
                 "matches, the faults that fired, the observed rate, when a rule last fired, the effect of its faults",
                 "on the exchanges, and the rule with what it does in plain words. With --report it shows the report instead: the verdict, the seed, all rules (also",
                 "the removed and replaced versions), the counts per route, the recent faults and the reasons of a FAIL.",
                 "%nExamples:",
                 "  camel get chaos",
                 "  camel get chaos myApp --report",
                 "  camel get chaos myApp --report --json" })
public class ListChaos extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    // the keys of a rule with its live state, besides its counts, that --json copies when the rule has them
    private static final List<String> LIVE_KEYS = List.of(
            "lostAtDlq", "startsIn", "rate", "observedRate", "nextFireIn", "burstEndsIn", "nextBurstIn", "lastFiredAgo",
            "faultsLastMinute", "faultsPerSecond", "effect");

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--report" },
                        description = "Show the report: verdict, seed, all rules, the counts per route, the recent faults")
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
                if (r.rule.get("description") != null) {
                    jo.put("description", r.rule.getString("description"));
                }
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
                if (r.rule.get("lifecycle") != null) {
                    jo.put("lifecycle", r.rule.get("lifecycle"));
                }
                if (r.rule.get("skippedReasons") != null) {
                    jo.put("skippedReasons", r.rule.get("skippedReasons"));
                }
                // the live state of the rule
                for (String key : LIVE_KEYS) {
                    if (r.rule.get(key) != null) {
                        jo.put(key, r.rule.get(key));
                    }
                }
                return jo;
            }).toList()));
            return;
        }
        if (rows.isEmpty()) {
            printNothing(integrations, false);
            return;
        }
        boolean notes = rows.stream().anyMatch(r -> !note(r.rule).isEmpty());
        boolean rates = rows.stream().anyMatch(r -> !rate(r.rule).isEmpty());
        boolean effects = rows.stream().anyMatch(r -> !effect(r.rule).isEmpty());
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
                new Column().header("RATE").visible(rates).headerAlign(HorizontalAlign.RIGHT)
                        .dataAlign(HorizontalAlign.RIGHT).with(r -> rate(r.rule)),
                new Column().header("LAST").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> lastFired(r.rule)),
                new Column().header("ENDS IN").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> endsIn(r.rule)),
                new Column().header("EFFECT").visible(effects).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(40, OverflowBehaviour.NEWLINE).with(r -> effect(r.rule)),
                new Column().header("NOTE").visible(notes).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(40, OverflowBehaviour.NEWLINE).with(r -> note(r.rule)),
                new Column().header("RULE").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                        .with(r -> ruleText(r.rule)))));
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
            printNothing(integrations, true);
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
            boolean notes = list.stream().anyMatch(r -> !note(r).isEmpty());
            boolean effects = list.stream().anyMatch(r -> !effect(r).isEmpty());
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
                    new Column().header("EFFECT").visible(effects).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.NEWLINE).with(ListChaos::effect),
                    new Column().header("NOTE").visible(notes).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.NEWLINE).with(ListChaos::note),
                    new Column().header("RULE").dataAlign(HorizontalAlign.LEFT).maxWidth(80, OverflowBehaviour.NEWLINE)
                            .with(ListChaos::ruleText))));
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
        JsonArray recent = report.getCollection("recentFaults");
        if (recent != null && !recent.isEmpty()) {
            List<JsonObject> list = recent.stream().map(JsonObject.class::cast).toList();
            printer().println("  recent faults:");
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, list, List.of(
                    new Column().header("AGO").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                            .with(r -> r.get("ago") instanceof Number n ? TimeUtils.printDuration(n.longValue()) : ""),
                    new Column().header("RULE").dataAlign(HorizontalAlign.LEFT).with(r -> r.getString("rule")),
                    new Column().header("FAULT").dataAlign(HorizontalAlign.LEFT).with(r -> r.getString("fault")),
                    new Column().header("HOOK").dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.getStringOrDefault("hook", "")),
                    new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.getStringOrDefault("route", "")),
                    new Column().header("AT").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(50, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.getStringOrDefault("endpoint", r.getStringOrDefault("step", ""))),
                    new Column().header("EXCHANGE").dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.getStringOrDefault("exchangeId", "")))));
        }
    }

    /**
     * Without a rule to show, says why on stderr, so the output stays empty: for each integration where chaos is off
     * the reason, for each one where it is on that it has no rules, and when no integration has camel-chaos at all,
     * that.
     */
    private void printNothing(List<Row> integrations, boolean report) {
        boolean any = false;
        for (Row integration : integrations) {
            if (integration.chaos == null) {
                continue;
            }
            any = true;
            String prefix = integration.name + " (pid " + integration.pid + "): ";
            if (!integration.isOn()) {
                printer().printDiagnostic(prefix + integration.chaos.getStringOrDefault("reason", "chaos is off"));
            } else if (!report) {
                printer().printDiagnostic(prefix + "no chaos rules (add one with camel cmd chaos-add)");
            }
        }
        if (!any && !integrations.isEmpty()) {
            printer().printDiagnostic("No running integration has camel-chaos: run it with camel run --chaos");
        }
    }

    /**
     * What a person should know about a rule, besides its counts: when a rule that waits starts, the bursts of a
     * {@code rate=burst:} rule, what a lifecycle rule did to its route (in which round of {@code repeat=}), and why the
     * rule did nothing when it was skipped.
     */
    static String note(JsonObject rule) {
        StringJoiner sj = new StringJoiner("; ");
        if (rule.get("startsIn") instanceof Number n) {
            sj.add("starts in " + TimeUtils.printDuration(n.longValue()));
        }
        if (rule.get("burstEndsIn") instanceof Number n) {
            sj.add("in a burst for " + TimeUtils.printDuration(n.longValue()));
        } else if (rule.get("nextBurstIn") instanceof Number n) {
            sj.add("next burst in " + TimeUtils.printDuration(n.longValue()));
        }
        if (rule.get("lifecycle") instanceof JsonObject l) {
            String state = l.getString("state");
            StringBuilder sb = new StringBuilder("route ").append(l.getString("route")).append(' ').append(state);
            if (l.get("repeat") instanceof Number repeat && l.get("round") instanceof Number round
                    && round.intValue() > 0) {
                sb.append(" (round ").append(round).append(" of ").append(repeat).append(')');
            }
            if (l.get("actsIn") instanceof Number n) {
                sb.append(", acts in ").append(TimeUtils.printDuration(n.longValue()));
            } else if (l.get("backIn") instanceof Number n) {
                sb.append(", back in ").append(TimeUtils.printDuration(n.longValue()));
            } else if ("back".equals(state) && l.get("downAt") instanceof Number down
                    && l.get("backAt") instanceof Number back) {
                sb.append(" after ").append(TimeUtils.printDuration(back.longValue() - down.longValue()));
            }
            if (l.getString("reason") != null) {
                sb.append(": ").append(l.getString("reason"));
            }
            sj.add(sb);
        }
        if (rule.get("skippedReasons") instanceof Map<?, ?> reasons && rule.get("lifecycle") == null) {
            reasons.forEach((reason, count) -> sj.add("skipped " + count + ": " + reason));
        }
        return sj.toString();
    }

    /** The rule, and below it the rule in plain words (an integration before Camel 4.24 has none). */
    static String ruleText(JsonObject rule) {
        String text = rule.getString("rule");
        String words = rule.getString("description");
        return words != null && !words.isBlank() ? text + "\n" + words : text;
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

    /** The observed rate of a rule next to its own, such as {@code 4.8% of 5%}, for a rule with a rate below 100%. */
    static String rate(JsonObject rule) {
        if (rule.get("rate") instanceof Number rate && rate.doubleValue() < 1.0d
                && rule.get("observedRate") instanceof Number observed) {
            return percent(observed.doubleValue()) + " of " + percent(rate.doubleValue());
        }
        return "";
    }

    private static String percent(double share) {
        double tenths = Math.round(share * 1000) / 10.0d;
        return (tenths == Math.rint(tenths) ? Long.toString((long) tenths) : Double.toString(tenths)) + "%";
    }

    private static String lastFired(JsonObject rule) {
        return rule.get("lastFiredAgo") instanceof Number n ? TimeUtils.printDuration(n.longValue()) + " ago" : "";
    }

    /**
     * What the faults of a rule did to the exchanges, such as {@code 10 recovered, 2 handled, 1 in flight}; the counts
     * that are 0 are left out.
     */
    static String effect(JsonObject rule) {
        if (!(rule.get("effect") instanceof JsonObject e)) {
            return "";
        }
        StringJoiner sj = new StringJoiner(", ");
        for (String[] key : new String[][] {
                { "completed", "completed" }, { "recovered", "recovered" }, { "handled", "handled" },
                { "failed", "failed" }, { "inFlight", "in flight" } }) {
            if (e.get(key[0]) instanceof Number n && n.longValue() > 0) {
                sj.add(n + " " + key[1]);
            }
        }
        return sj.toString();
    }

    private static String endsIn(JsonObject rule) {
        return rule.get("endsIn") instanceof Number n ? TimeUtils.printDuration(n.longValue()) : "";
    }

    private static String expectation(JsonObject rule) {
        if (!(rule.get("expectation") instanceof JsonObject e)) {
            return "";
        }
        // orders:100 counts completed exchanges (at least that many); orders:handled<=0 names its kind and op
        String kind = e.getStringOrDefault("kind", "completed");
        String op = e.getStringOrDefault("op", ">=");
        Object value = e.get("value") != null ? e.get("value") : e.get("completed");
        StringBuilder sb = new StringBuilder();
        String spec = e.getString("spec");
        if (spec != null) {
            // as the rule writes it, such as orders:meanTime<=2s or orders->shipping:lost<=0 (CAMEL-26726)
            sb.append(spec);
        } else {
            sb.append(e.getString("route")).append(':');
            if (!"completed".equals(kind) || !">=".equals(op)) {
                sb.append(kind).append(op);
            }
            sb.append(e.get("count"));
        }
        sb.append(' ').append(e.getString("outcome")).append(" (");
        if (("meanTime".equals(kind) || "maxTime".equals(kind)) && value instanceof Number n) {
            // a time in milliseconds
            sb.append(TimeUtils.printDuration(n.longValue())).append(' ').append(kind);
        } else {
            sb.append(value).append(' ').append(kind);
        }
        if (e.get("timeLeft") instanceof Number n) {
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
