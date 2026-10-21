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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CopyableRow;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "metric",
         description = "Get metrics (micrometer) of running Camel integrations", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get metric",
                 "  camel get metric --filter=timer*",
                 "  camel get metric --watch" })
public class ListMetric extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);
    // the metrics of an integration by name and id
    private static final Comparator<Row> BY_METRIC
            = Comparator.comparing((Row r) -> r.metricName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(r -> r.metricId, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));

    @CommandLine.Parameters(description = "Name or pid of running Camel integration",
                            arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter metric by type, name or tags")
    String filter;

    @CommandLine.Option(names = { "--tags" },
                        description = "Show metric tags", defaultValue = "false")
    boolean tags;

    @CommandLine.Option(names = { "--custom" },
                        description = "Only show custom metrics", defaultValue = "false")
    boolean custom;

    @CommandLine.Option(names = { "--all" },
                        description = "Whether to show all metrics (also unused with counter being 0)", defaultValue = "false")
    boolean all;

    public ListMetric(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        forEachIntegration(name, (ph, root) -> {
            // there must be a status file for the running Camel integration
            Row row = new Row();
            JsonObject context = (JsonObject) root.get("context");
            if (context == null) {
                return;
            }
            row.name = context.getString("name");
            if ("CamelJBang".equals(row.name)) {
                row.name = ProcessHelper.extractName(root, ph);
            }
            row.pid = Long.toString(ph.pid());
            row.uptime = extractSince(ph);
            row.age = TimeUtils.printSince(row.uptime);
            Row baseRow = row.copy();

            JsonObject mo = (JsonObject) root.get("micrometer");
            if (mo != null) {
                JsonArray arr = (JsonArray) mo.get("counters");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        row = baseRow.copy();
                        JsonObject jo = (JsonObject) arr.get(i);
                        row.type = "counter";
                        row.metricName = jo.getString("name");
                        row.metricDescription = jo.getString("description");
                        row.metricId = extractId(jo);
                        row.tags = extractTags(jo);
                        row.count = jo.getDouble("count");

                        if (accept(row)) {
                            rows.add(row);
                        }
                    }
                }
                arr = (JsonArray) mo.get("timers");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        row = baseRow.copy();
                        JsonObject jo = (JsonObject) arr.get(i);
                        row.type = "timer";
                        row.metricName = jo.getString("name");
                        row.metricDescription = jo.getString("description");
                        row.metricId = extractId(jo);
                        row.tags = extractTags(jo);
                        row.count = jo.getDouble("count");
                        row.mean = jo.getDouble("mean");
                        row.max = jo.getDouble("max");
                        row.total = jo.getDouble("total");

                        if (accept(row)) {
                            rows.add(row);
                        }
                    }
                }
                arr = (JsonArray) mo.get("gauges");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        row = baseRow.copy();
                        JsonObject jo = (JsonObject) arr.get(i);
                        row.type = "gauge";
                        row.metricName = jo.getString("name");
                        if ("app.info".equals(row.metricName)) {
                            continue; // special camel info that hides in the metrics
                        }
                        row.metricDescription = jo.getString("description");
                        row.metricId = extractId(jo);
                        row.tags = extractTags(jo);
                        row.count = jo.getDouble("value");

                        if (accept(row)) {
                            rows.add(row);
                        }
                    }
                }
                arr = (JsonArray) mo.get("distribution");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        row = baseRow.copy();
                        JsonObject jo = (JsonObject) arr.get(i);
                        row.type = "distribution";
                        row.metricName = jo.getString("name");
                        row.metricDescription = jo.getString("description");
                        row.metricId = extractId(jo);
                        row.tags = extractTags(jo);
                        row.count = jo.getDouble("count");
                        row.mean = jo.getDouble("mean");
                        row.max = jo.getDouble("max");
                        row.total = jo.getDouble("totalAmount");

                        if (accept(row)) {
                            rows.add(row);
                        }
                    }
                }
            }
        });

        // sort rows
        rows.sort(ORDER.comparator(sort).thenComparing(
                sort != null && sort.startsWith("-") ? BY_METRIC.reversed() : BY_METRIC));

        if (!rows.isEmpty() || jsonOutput) {
            if (jsonOutput) {
                printer().println(Jsoner.serialize(rows.stream().map(r -> {
                    JsonObject jo = new JsonObject();
                    jo.put("pid", JsonValues.pid(r.pid));
                    jo.put("name", r.name);
                    jo.put("type", r.type);
                    jo.put("metric", r.metricName);
                    jo.put("id", r.metricId);
                    jo.put("value", r.count);
                    jo.put("mean", r.mean);
                    jo.put("max", r.max);
                    jo.put("total", r.total);
                    jo.put("tags", r.tags);
                    return jo;
                }).collect(Collectors.toList())));
            } else {
                // Flexible columns: METRIC (40), ID (40), TAGS (60)
                // Fixed columns: PID(8)+NAME(30)+TYPE(12)+VALUE(8)+MEAN(6)+MAX(6)+TOTAL(6) ~= 76
                int tw = terminalWidth();
                int metricW = TerminalWidthHelper.flexWidth(tw, 76 + 40 + 60, TerminalWidthHelper.noBorderOverhead(10), 15, 40);
                int metricIdW
                        = TerminalWidthHelper.flexWidth(tw, 76 + 40 + 60, TerminalWidthHelper.noBorderOverhead(10), 15, 40);
                int tagsW = TerminalWidthHelper.flexWidth(tw, 76 + metricW + metricIdW,
                        TerminalWidthHelper.noBorderOverhead(10), 15, 60);
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).with(r -> r.type),
                        new Column().header("METRIC").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(metricW, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.metricName),
                        new Column().header("ID").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(metricIdW, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.metricId),
                        new Column().header("VALUE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                                .with(r -> getNumber(r.count)),
                        new Column().header("MEAN").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                                .with(r -> getNumber(r.mean)),
                        new Column().header("MAX").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                                .with(r -> getNumber(r.max)),
                        new Column().header("TOTAL").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                                .with(r -> getNumber(r.total)),
                        new Column().header("TAGS").visible(tags).dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(tagsW, OverflowBehaviour.NEWLINE)
                                .with(r -> r.tags))));
            }
        }

        return 0;
    }

    // whether the metric is listed
    private boolean accept(Row row) {
        if (custom && (row.metricName.startsWith("Camel") || row.metricName.startsWith("camel."))) {
            return false; // skip internal camel metrics (in the classic and the micrometer naming)
        }
        if (!all && getNumber(row.count).isEmpty()) {
            return false;
        }
        return filter == null || PatternHelper.matchPattern(row.type, filter) || matches(row.metricName)
                || matches(row.tags);
    }

    // the filter is a part of the text, or a pattern such as timer*
    private boolean matches(String text) {
        return text.contains(filter) || PatternHelper.matchPattern(text, filter);
    }

    private String getNumber(double d) {
        String s = Double.toString(d);
        if (s.equals("0.0") || s.equals("0,0")) {
            return "";
        } else if (s.endsWith(".0") || s.endsWith(",0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private String extractId(JsonObject jo) {
        List<JsonObject> tags = jo.getCollection("tags");
        String routeId = null;
        String nodeId = null;
        if (tags != null) {
            for (JsonObject t : tags) {
                String k = t.getString("key");
                if ("routeId".equals(k)) {
                    routeId = t.getString("value");
                } else if ("nodeId".equals(k)) {
                    nodeId = t.getString("value");
                }
            }
        }
        if (routeId != null && nodeId != null) {
            return routeId + "/" + nodeId;
        } else if (routeId != null) {
            return routeId;
        } else if (nodeId != null) {
            return nodeId;
        }
        return "";
    }

    private String extractTags(JsonObject jo) {
        StringJoiner sj = new StringJoiner(" ");
        List<JsonObject> tags = jo.getCollection("tags");
        if (tags != null) {
            for (JsonObject t : tags) {
                String k = t.getString("key");
                String v = t.getString("value");
                sj.add(k + "=" + v);
            }
        }
        return sj.toString();
    }

    private static class Row extends CopyableRow<Row> {
        String pid;
        String name;
        String age;
        long uptime;
        String type;
        String metricName;
        String metricDescription;
        String metricId;
        String tags;
        double count;
        double mean;
        double max;
        double total;
    }

}
