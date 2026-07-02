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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "memory-leak",
         description = "Diagnose memory leaks in a running Camel integration",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nNote: Sizes are sampled during the recording window, not total heap usage.",
                 "A longer recording captures more samples and shows larger totals for the same leak.",
                 "Use values to compare classes relative to each other, not as absolute heap numbers.",
                 "%nExamples:",
                 "  camel cmd memory-leak --start",
                 "  camel cmd memory-leak --start --duration 60",
                 "  camel cmd memory-leak --stop",
                 "  camel cmd memory-leak --status",
                 "  camel cmd memory-leak --query",
                 "  camel cmd memory-leak --query --min-size 1MB",
                 "  camel cmd memory-leak --query --stacktrace",
                 "  camel cmd memory-leak --start --mode dual" })
public class CamelMemoryLeak extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--start" }, description = "Start a JFR recording for OldObjectSample events")
    boolean start;

    @CommandLine.Option(names = { "--stop" }, description = "Stop the active recording and display results")
    boolean stop;

    @CommandLine.Option(names = { "--status" }, description = "Check recording status")
    boolean status;

    @CommandLine.Option(names = { "--query" }, description = "Query cached results from the last recording")
    boolean query;

    @CommandLine.Option(names = { "--mode" },
                        description = "Recording mode: dual (default, two recordings with trend comparison) or single (one recording)",
                        defaultValue = "dual")
    String mode;

    @CommandLine.Option(names = { "--duration" },
                        description = "Recording duration in seconds (auto-stops after this time)", defaultValue = "60")
    int duration;

    @CommandLine.Option(names = { "--top" },
                        description = "Show only the top N samples", defaultValue = "50")
    int top;

    @CommandLine.Option(names = { "--min-size" },
                        description = "Only show samples with total size above this value (e.g. 1024, 10KB, 1MB). Default 1KB in dual mode to filter noise",
                        defaultValue = "0")
    String minSize;

    @CommandLine.Option(names = { "--stacktrace" },
                        description = "Show allocation stack trace for each sample")
    boolean stacktrace;

    public CamelMemoryLeak(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 1;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        long pid = pids.get(0);

        if (start && "dual".equalsIgnoreCase(mode)) {
            return doDualRecording(pid);
        }

        String command;
        if (start) {
            command = "start";
        } else if (stop) {
            command = "stop";
        } else if (query) {
            command = "query";
        } else {
            command = "status";
        }

        JsonObject jo = sendAction(pid, command, start ? duration : 0);
        if (jo == null) {
            printer().println("Response from running Camel with PID " + pid + " not received within timeout");
            return 1;
        }

        return handleResponse(pid, jo);
    }

    private int doDualRecording(long pid) throws Exception {
        int dur1 = duration;
        int dur2 = duration * 2;

        // run 1
        printer().printf("Recording 1 of 2 (%ds)...%n", dur1);
        JsonObject startResp = sendAction(pid, "start", dur1);
        if (startResp == null || !"recording".equals(startResp.getString("status"))) {
            printer().println("Error: Failed to start recording 1");
            return 1;
        }

        Thread.sleep((dur1 + 3) * 1000L);

        JsonObject stopResp = sendAction(pid, "stop", 0);
        if (stopResp == null || !"completed".equals(stopResp.getString("status"))) {
            printer().println("Error: Recording 1 did not complete");
            return 1;
        }

        // run 2
        printer().printf("Recording 2 of 2 (%ds)...%n", dur2);
        startResp = sendAction(pid, "start", dur2);
        if (startResp == null || !"recording".equals(startResp.getString("status"))) {
            printer().println("Error: Failed to start recording 2");
            return 1;
        }

        Thread.sleep((dur2 + 3) * 1000L);

        stopResp = sendAction(pid, "stop", 0);
        if (stopResp == null || !"completed".equals(stopResp.getString("status"))) {
            printer().println("Error: Recording 2 did not complete");
            return 1;
        }

        // compare
        JsonObject cmpResp = sendAction(pid, "compare", 0);
        if (cmpResp == null) {
            printer().println("Error: Failed to get comparison data");
            return 1;
        }

        return handleResponse(pid, cmpResp);
    }

    private JsonObject sendAction(long pid, String command, int dur) {
        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "jfr-memory-leak");
        root.put("command", command);
        if ("start".equals(command) && dur > 0) {
            root.put("duration", String.valueOf(dur));
        }
        if (stacktrace) {
            root.put("stacktrace", "true");
        }
        long minBytes = parseSize(minSize);
        if (minBytes > 0) {
            root.put("minSize", String.valueOf(minBytes));
        }

        Path f = getActionFile(Long.toString(pid));
        try {
            Files.writeString(f, root.toJson());
        } catch (Exception e) {
            // ignore
        }

        int timeout = "start".equals(command) ? 15000 : 30000;
        JsonObject jo = getJsonObject(outputFile, timeout);
        PathUtils.deleteFile(outputFile);
        return jo;
    }

    private int handleResponse(long pid, JsonObject jo) {
        String responseStatus = jo.getString("status");

        if ("error".equals(responseStatus)) {
            printer().println("Error: " + jo.getString("error"));
            return 1;
        }

        if ("recording".equals(responseStatus)) {
            printer().println("JFR recording started for PID: " + pid);
            if (jo.containsKey("durationSeconds")) {
                printer().println("Duration: " + jo.getInteger("durationSeconds") + " seconds (auto-stop)");
            } else {
                printer().println("Duration: manual (use --stop to end recording)");
            }
            return 0;
        }

        if ("idle".equals(responseStatus)) {
            printer().println("No active JFR recording for PID: " + pid);
            if (jo.containsKey("note")) {
                printer().println(jo.getString("note"));
            }
            return 0;
        }

        if ("completed".equals(responseStatus)) {
            int sampleCount = jo.getIntegerOrDefault("sampleCount", 0);
            long durationMs = jo.getLongOrDefault("recordingDurationMs", 0);
            printer().printf("PID: %s\tSamples: %d\tRecording duration: %s%n",
                    pid, sampleCount, formatDuration(durationMs));

            JsonArray samples = (JsonArray) jo.get("samples");
            if (samples != null && !samples.isEmpty()) {
                List<Row> rows = new ArrayList<>();
                int num = 0;
                for (int i = 0; i < samples.size(); i++) {
                    JsonObject sample = (JsonObject) samples.get(i);
                    long sampledSize = sample.getLongOrDefault("sampledSize", 0);
                    num++;
                    if (num > top) {
                        break;
                    }
                    Row row = new Row();
                    row.num = num;
                    row.className = sample.getStringOrDefault("allocationClass", "unknown");
                    row.count = sample.getIntegerOrDefault("count", 1);
                    row.sampledSize = sampledSize;
                    row.objectAge = sample.getLongOrDefault("objectAge", 0);
                    row.chainSummary = buildChainSummary(sample);
                    row.stackTrace = (JsonArray) sample.get("stackTrace");
                    rows.add(row);
                }
                printTable(rows);
            } else {
                printer().println("No old object samples captured.");
                printer().println("Tip: Try a longer recording duration or ensure GC occurs during recording.");
            }
            return 0;
        }

        if ("compared".equals(responseStatus)) {
            JsonObject baselineInfo = (JsonObject) jo.get("baseline");
            JsonObject currentInfo = (JsonObject) jo.get("current");
            long baseDur = baselineInfo != null ? baselineInfo.getLongOrDefault("recordingDurationMs", 0) : 0;
            long curDur = currentInfo != null ? currentInfo.getLongOrDefault("recordingDurationMs", 0) : 0;
            double durRatio = jo.getDoubleOrDefault("durationRatio", 1.0);
            printer().printf("PID: %s\tRun 1: %s\tRun 2: %s\tDuration ratio: %.1fx%n",
                    pid, formatDuration(baseDur), formatDuration(curDur), durRatio);

            JsonArray comps = (JsonArray) jo.get("comparisons");
            if (comps != null && !comps.isEmpty()) {
                List<CompRow> compRows = new ArrayList<>();
                int num = 0;
                for (int i = 0; i < comps.size(); i++) {
                    JsonObject comp = (JsonObject) comps.get(i);
                    num++;
                    if (num > top) {
                        break;
                    }
                    CompRow cr = new CompRow();
                    cr.num = num;
                    cr.className = comp.getStringOrDefault("allocationClass", "unknown");
                    cr.baselineSampledSize = comp.getLongOrDefault("baselineSampledSize", 0);
                    cr.currentSampledSize = comp.getLongOrDefault("currentSampledSize", 0);
                    cr.growthRatio = comp.getDoubleOrDefault("growthRatio", 0);
                    cr.trend = comp.getStringOrDefault("trend", "stable");
                    cr.chainSummary = buildChainSummary(comp);
                    cr.stackTrace = (JsonArray) comp.get("stackTrace");
                    compRows.add(cr);
                }
                if (stacktrace) {
                    printComparisonTableWithStacktrace(compRows);
                } else {
                    printComparisonTable(compRows);
                }
            } else {
                printer().println("No comparison data available.");
            }
            return 0;
        }

        // status response with cached results info
        if (jo.containsKey("hasCachedResults") && jo.getBooleanOrDefault("hasCachedResults", false)) {
            printer().println("JFR recording completed. Use --query to view results.");
            printer().println("Cached samples: " + jo.getIntegerOrDefault("sampleCount", 0));
        } else if (jo.containsKey("elapsedMs")) {
            long elapsed = jo.getLongOrDefault("elapsedMs", 0);
            printer().println("JFR recording in progress for PID: " + pid);
            printer().println("Elapsed: " + formatDuration(elapsed));
            if (jo.containsKey("remainingMs")) {
                printer().println("Remaining: " + formatDuration(jo.getLongOrDefault("remainingMs", 0)));
            }
        }

        return 0;
    }

    private void printTable(List<Row> rows) {
        if (stacktrace) {
            printTableWithStacktrace(rows);
        } else {
            printTableCompact(rows);
        }
    }

    private void printTableCompact(List<Row> rows) {
        int tw = terminalWidth();
        int fixedWidth = 6 + 8 + 12 + 12;
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(5);
        int classWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth + 30, borderOverhead, 20, 50);
        int chainWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth + classWidth, borderOverhead, 20, 60);

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("#").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.num)),
                new Column().header("CLASS").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(classWidth, OverflowBehaviour.ELLIPSIS_LEFT)
                        .with(r -> r.className),
                new Column().header("COUNT").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.count)),
                new Column().header("SAMPLED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.sampledSize > 0 ? formatBytes(r.sampledSize) : "-"),
                new Column().header("AGE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> formatDuration(r.objectAge)),
                new Column().header("REFERENCE CHAIN").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(chainWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.chainSummary))));
    }

    private void printTableWithStacktrace(List<Row> rows) {
        for (Row r : rows) {
            String sizeStr = r.sampledSize > 0 ? formatBytes(r.sampledSize) : "-";
            printer().printf("%d) %s  count:%d  sampled:%s  age:%s%n",
                    r.num, r.className, r.count, sizeStr, formatDuration(r.objectAge));
            if (r.chainSummary != null && !r.chainSummary.isEmpty()) {
                printer().println("   chain: " + r.chainSummary);
            }
            if (r.stackTrace != null) {
                for (int i = 0; i < r.stackTrace.size(); i++) {
                    JsonObject frame = (JsonObject) r.stackTrace.get(i);
                    printer().printf("     at %s:%s%n",
                            frame.getStringOrDefault("method", "?"),
                            frame.getIntegerOrDefault("line", 0));
                }
            }
            printer().println();
        }
    }

    private static String buildChainSummary(JsonObject sample) {
        JsonArray chain = (JsonArray) sample.get("referenceChain");
        if (chain == null || chain.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size() && i < 3; i++) {
            JsonObject link = (JsonObject) chain.get(i);
            if (i > 0) {
                sb.append(" -> ");
            }
            String type = link.getString("type");
            if (type != null) {
                int dot = type.lastIndexOf('.');
                sb.append(dot >= 0 ? type.substring(dot + 1) : type);
            }
            String field = link.getString("field");
            if (field != null) {
                sb.append('.').append(field);
            }
        }
        if (chain.size() > 3) {
            sb.append(" -> ...");
        }
        return sb.toString();
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format(Locale.US, "%.1fs", ms / 1000.0);
        } else {
            long min = ms / 60000;
            long sec = (ms % 60000) / 1000;
            return min + "m " + sec + "s";
        }
    }

    static long parseSize(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return 0;
        }
        String v = value.trim().toUpperCase(Locale.US);
        long multiplier = 1;
        if (v.endsWith("GB") || v.endsWith("G")) {
            multiplier = 1024L * 1024 * 1024;
            v = v.replaceAll("[GMK]B?$", "");
        } else if (v.endsWith("MB") || v.endsWith("M")) {
            multiplier = 1024L * 1024;
            v = v.replaceAll("[GMK]B?$", "");
        } else if (v.endsWith("KB") || v.endsWith("K")) {
            multiplier = 1024;
            v = v.replaceAll("[GMK]B?$", "");
        }
        try {
            return (long) (Double.parseDouble(v) * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void printComparisonTable(List<CompRow> rows) {
        int tw = terminalWidth();
        int fixedWidth = 6 + 12 + 12 + 10 + 10;
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(6);
        int classWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 20, 50);

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("#").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.num)),
                new Column().header("CLASS").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(classWidth, OverflowBehaviour.ELLIPSIS_LEFT)
                        .with(r -> r.className),
                new Column().header("RUN1").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.baselineSampledSize > 0 ? formatBytes(r.baselineSampledSize) : "-"),
                new Column().header("RUN2").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.currentSampledSize > 0 ? formatBytes(r.currentSampledSize) : "-"),
                new Column().header("GROWTH").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.growthRatio > 0 ? String.format(Locale.US, "%.1fx", r.growthRatio) : "-"),
                new Column().header("TREND").dataAlign(HorizontalAlign.LEFT)
                        .with(r -> trendLabel(r.trend)))));
    }

    private void printComparisonTableWithStacktrace(List<CompRow> rows) {
        for (CompRow r : rows) {
            String run1 = r.baselineSampledSize > 0 ? formatBytes(r.baselineSampledSize) : "-";
            String run2 = r.currentSampledSize > 0 ? formatBytes(r.currentSampledSize) : "-";
            String growth = r.growthRatio > 0 ? String.format(Locale.US, "%.1fx", r.growthRatio) : "-";
            printer().printf("%d) %s  run1:%s  run2:%s  growth:%s  %s%n",
                    r.num, r.className, run1, run2, growth, trendLabel(r.trend));
            if (r.chainSummary != null && !r.chainSummary.isEmpty()) {
                printer().println("   chain: " + r.chainSummary);
            }
            if (r.stackTrace != null) {
                for (int i = 0; i < r.stackTrace.size(); i++) {
                    JsonObject frame = (JsonObject) r.stackTrace.get(i);
                    printer().printf("     at %s:%s%n",
                            frame.getStringOrDefault("method", "?"),
                            frame.getIntegerOrDefault("line", 0));
                }
            }
            printer().println();
        }
    }

    private static String trendLabel(String trend) {
        if (trend == null) {
            return "-";
        }
        return switch (trend) {
            case "growing" -> "↑ leak!";
            case "suspicious" -> "↑ leak?";
            case "stable" -> "→ stable";
            case "shrinking" -> "↓";
            case "new" -> "new";
            case "gone" -> "gone";
            default -> trend;
        };
    }

    private static class CompRow {
        int num;
        String className;
        long baselineSampledSize;
        long currentSampledSize;
        double growthRatio;
        String trend;
        String chainSummary;
        JsonArray stackTrace;
    }

    private static class Row {
        int num;
        String className;
        int count;
        long sampledSize;
        long objectAge;
        String chainSummary;
        JsonArray stackTrace;
    }
}
