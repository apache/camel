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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "processor", description = "Get status of Camel processors",
         sortOptions = false)
public class CamelProcessorStatus extends ProcessWatchCommand {

    public static class PidNameCompletionCandidates implements Iterable<String> {

        public PidNameCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameCompletionCandidates.class,
                        description = "Sort by pid or name", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--source" },
                        description = "Prefer to display source filename/code instead of IDs")
    boolean source;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter routes by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--filter-mean" },
                        description = "Filter processors that must be slower than the given time (ms)")
    long mean;

    public CamelProcessorStatus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        JsonArray array = (JsonArray) root.get("routes");
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject o = (JsonObject) array.get(i);
                            Row row = new Row();
                            row.name = context.getString("name");
                            if ("CamelJBang".equals(row.name)) {
                                row.name = ProcessHelper.extractName(root, ph);
                            }
                            row.pid = Long.toString(ph.pid());
                            row.routeId = o.getString("routeId");
                            row.processor = o.getString("from");
                            row.source = o.getString("source");
                            row.state = o.getString("state");
                            Map<String, ?> stats = o.getMap("statistics");
                            if (stats != null) {
                                row.total = stats.get("exchangesTotal").toString();
                                row.inflight = stats.get("exchangesInflight").toString();
                                row.failed = stats.get("exchangesFailed").toString();
                                row.mean = stats.get("meanProcessingTime").toString();
                                if ("-1".equals(row.mean)) {
                                    row.mean = null;
                                }
                                row.max = stats.get("maxProcessingTime").toString();
                                row.min = stats.get("minProcessingTime").toString();
                                Object last = stats.get("lastProcessingTime");
                                if (last != null) {
                                    row.last = last.toString();
                                }
                                last = stats.get("deltaProcessingTime");
                                if (last != null) {
                                    row.delta = last.toString();
                                }
                                last = stats.get("sinceLastCreatedExchange");
                                if (last != null) {
                                    row.sinceLastStarted = last.toString();
                                }
                                last = stats.get("sinceLastCompletedExchange");
                                if (last != null) {
                                    row.sinceLastCompleted = last.toString();
                                }
                                last = stats.get("sinceLastFailedExchange");
                                if (last != null) {
                                    row.sinceLastFailed = last.toString();
                                }
                            }

                            boolean add = true;
                            if (mean > 0 && (row.mean == null || Long.parseLong(row.mean) < mean)) {
                                add = false;
                            }
                            if (limit > 0 && rows.size() >= limit) {
                                add = false;
                            }
                            if (add) {
                                rows.add(row);
                                List<JsonObject> list = o.getCollection("processors");
                                if (list != null) {
                                    addProcessors(row, rows, list);
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printTable(rows);
        }

        return 0;
    }

    private void addProcessors(Row route, List<Row> rows, List<JsonObject> processors) {
        for (JsonObject o : processors) {
            Row row = new Row();
            row.pid = route.pid;
            row.name = route.name;
            row.routeId = route.routeId;
            rows.add(row);
            row.processorId = o.getString("id");
            row.processor = o.getString("processor");
            row.level = o.getIntegerOrDefault("level", 0);
            row.source = o.getString("source");
            Map<String, ?> stats = o.getMap("statistics");
            if (stats != null) {
                row.total = stats.get("exchangesTotal").toString();
                row.inflight = stats.get("exchangesInflight").toString();
                row.failed = stats.get("exchangesFailed").toString();
                row.mean = stats.get("meanProcessingTime").toString();
                if ("-1".equals(row.mean)) {
                    row.mean = null;
                }
                row.max = stats.get("maxProcessingTime").toString();
                row.min = stats.get("minProcessingTime").toString();
                Object last = stats.get("lastProcessingTime");
                if (last != null) {
                    row.last = last.toString();
                }
                last = stats.get("deltaProcessingTime");
                if (last != null) {
                    row.delta = last.toString();
                }
                last = stats.get("sinceLastCompletedExchange");
                if (last != null) {
                    row.sinceLastCompleted = last.toString();
                }
                last = stats.get("sinceLastFailedExchange");
                if (last != null) {
                    row.sinceLastFailed = last.toString();
                }
            }
            if (source) {
                List<JsonObject> lines = o.getCollection("code");
                if (lines != null) {
                    for (JsonObject line : lines) {
                        Code code = new Code();
                        code.line = line.getInteger("line");
                        code.code = line.getString("code");
                        if (line.getBooleanOrDefault("match", false)) {
                            code.match = true;
                        }
                        row.code.add(code);
                    }
                }
            }
        }
    }

    protected void printTable(List<Row> rows) {
        System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(this::getPid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getName),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getId),
                new Column().header("PROCESSOR").dataAlign(HorizontalAlign.LEFT).minWidth(25)
                        .maxWidth(45, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getProcessor),
                new Column().header("TOTAL").with(r -> r.total),
                new Column().header("FAIL").with(r -> r.failed),
                new Column().header("INFLIGHT").with(r -> r.inflight),
                new Column().header("MEAN").with(r -> r.mean),
                new Column().header("MIN").with(r -> r.min),
                new Column().header("MAX").with(r -> r.max),
                new Column().header("LAST").with(r -> r.last),
                new Column().header("DELTA").with(this::getDelta),
                new Column().header("SINCE-LAST").with(this::getSinceLast))));
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "pid":
                return Long.compare(Long.parseLong(o1.pid), Long.parseLong(o2.pid)) * negate;
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "age":
                return Long.compare(o1.uptime, o2.uptime) * negate;
            default:
                return 0;
        }
    }

    protected String getSinceLast(Row r) {
        String s1 = r.sinceLastCompleted != null ? r.sinceLastCompleted : "-";
        String s2 = r.sinceLastFailed != null ? r.sinceLastFailed : "-";
        return s1 + "/" + s2;
    }

    protected String getDelta(Row r) {
        if (r.delta != null) {
            if (r.delta.startsWith("-")) {
                return r.delta;
            } else if (!"0".equals(r.delta)) {
                // use plus sign to denote slower when positive
                return "+" + r.delta;
            }
        }
        return r.delta;
    }

    protected String getName(Row r) {
        return r.processorId == null ? r.name : "";
    }

    protected String getId(Row r) {
        String answer;
        if (source && r.source != null) {
            answer = sourceLocLine(r.source);
        } else {
            answer = r.processorId != null ? r.processorId : r.routeId;
        }
        return answer;
    }

    protected String getPid(Row r) {
        if (r.processorId == null) {
            return r.pid;
        } else {
            return "";
        }
    }

    protected String getProcessor(Row r) {
        String answer = r.processor;
        if (source) {
            answer = r.code.stream()
                    .filter(l -> l.match)
                    .findFirst()
                    .map(l -> Jsoner.unescape(l.code).trim())
                    .orElse(r.processor);
        }
        String pad = StringHelper.padString(r.level, 2);
        return pad + answer;
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String routeId;
        String processorId;
        String processor;
        int level;
        String source;
        String state;
        String total;
        String failed;
        String inflight;
        String mean;
        String max;
        String min;
        String last;
        String delta;
        String sinceLastStarted;
        String sinceLastCompleted;
        String sinceLastFailed;
        List<Code> code = new ArrayList<>();
    }

    private static class Code {
        int line;
        String code;
        boolean match;
    }

}
