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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.jline.jansi.Ansi;
import picocli.CommandLine;

@CommandLine.Command(name = "span",
                     description = "Display OpenTelemetry spans from running Camel integrations",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel cmd span",
                             "  camel cmd span --sort=duration",
                             "  camel cmd span --trace=4bb73039",
                             "  camel cmd span --flat",
                             "  camel cmd span --filter=kafka" })
public class CamelSpanAction extends ActionBaseCommand {

    public static class SortCompletionCandidates implements Iterable<String> {

        public SortCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("trace", "route", "from", "spans", "routes", "status", "duration").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--limit" }, defaultValue = "500",
                        description = "Maximum number of spans to display")
    int limit = 500;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter by trace ID, route, component, or exchange ID (substring match)")
    String filter;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = SortCompletionCandidates.class,
                        description = "Sort by trace, route, from, spans, routes, status, or duration")
    String sort;

    @CommandLine.Option(names = { "--trace" },
                        description = "Show waterfall view for a specific trace ID (substring match)")
    String trace;

    @CommandLine.Option(names = { "--flat" },
                        description = "Show flat list of individual spans instead of grouped traces")
    boolean flat;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true",
                        description = "Use colored logging")
    boolean loggingColor = true;

    private volatile long pid;

    public CamelSpanAction(CamelJBangMain main) {
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

        this.pid = pids.get(0);

        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "span");
        root.put("dump", "true");
        root.put("limit", Integer.toString(limit));

        Path f = getActionFile(Long.toString(pid));
        try {
            PathUtils.writeTextSafely(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = getJsonObject(outputFile);
        if (jo != null) {
            Boolean enabled = jo.getBoolean("enabled");
            if (enabled == null || !enabled) {
                printer().println(
                        "OpenTelemetry in-memory exporter is not enabled. Use --observe flag when running the integration.");
                PathUtils.deleteFile(outputFile);
                return 0;
            }

            JsonArray arr = jo.getCollection("spans");
            if (arr == null || arr.isEmpty()) {
                printer().println("No spans captured yet.");
                PathUtils.deleteFile(outputFile);
                return 0;
            }

            List<Row> rows = new ArrayList<>();
            root = loadStatus(this.pid);
            String integrationName = null;
            String ago = null;
            if (root != null) {
                JsonObject context = (JsonObject) root.get("context");
                if (context != null) {
                    integrationName = context.getString("name");
                    if ("CamelJBang".equals(integrationName)) {
                        ProcessHandle ph = ProcessHandle.of(this.pid).orElse(null);
                        integrationName = ProcessHelper.extractName(root, ph);
                    }
                }
                ProcessHandle ph = ProcessHandle.of(this.pid).orElse(null);
                long uptime = extractSince(ph);
                ago = TimeUtils.printSince(uptime);
            }

            for (int i = 0; i < arr.size(); i++) {
                JsonObject span = (JsonObject) arr.get(i);
                Row row = new Row();
                row.pid = Long.toString(this.pid);
                row.name = integrationName;
                row.ago = ago;
                row.traceId = span.getString("traceId");
                row.spanId = span.getString("spanId");
                row.parentSpanId = span.getString("parentSpanId");
                row.spanName = span.getString("name");
                row.kind = span.getString("kind");
                row.status = span.getString("status");
                Long durationMs = span.getLong("durationMs");
                row.durationMs = durationMs != null ? durationMs : 0;
                row.routeId = span.getString("routeId");
                row.processorId = span.getString("processorId");
                row.startEpochNanos = span.getLongOrDefault("startEpochNanos", 0);
                row.endEpochNanos = span.getLongOrDefault("endEpochNanos", 0);
                JsonObject attrsObj = span.getMap("attributes");
                if (attrsObj != null && !attrsObj.isEmpty()) {
                    row.attributes = attrsObj;
                }

                rows.add(row);
            }

            if (trace != null) {
                printWaterfall(rows, trace);
            } else if (flat) {
                if (filter != null) {
                    rows.removeIf(r -> !matchesFilter(r.spanName, filter));
                }
                if (sort != null) {
                    rows.sort(this::sortRow);
                }
                tableSpans(rows);
            } else {
                List<TraceSummary> summaries = buildTraceSummaries(rows);
                if (filter != null) {
                    summaries.removeIf(ts -> !ts.searchText.contains(filter.toLowerCase()));
                }
                if (sort != null) {
                    summaries.sort((a, b) -> sortTraceSummary(a, b, rows));
                }
                tableTraces(summaries);
            }
        } else {
            printer().printErr("Response from running Camel with PID " + pid + " not received within 10 seconds");
            return 1;
        }

        PathUtils.deleteFile(outputFile);
        return 0;
    }

    // --- Trace-grouped view ---

    private List<TraceSummary> buildTraceSummaries(List<Row> rows) {
        Map<String, TraceSummary> byTrace = new LinkedHashMap<>();

        for (Row row : rows) {
            TraceSummary ts = byTrace.computeIfAbsent(row.traceId, TraceSummary::new);
            if (isRoot(row)) {
                ts.rootRouteId = row.routeId;
                ts.rootName = compactUri(row);
            }
            if ("ERROR".equals(row.status)) {
                ts.hasError = true;
            }
        }

        List<TraceSummary> result = new ArrayList<>(byTrace.values());
        for (TraceSummary ts : result) {
            List<Row> traceRows = rows.stream()
                    .filter(r -> r.traceId.equals(ts.traceId))
                    .toList();
            // Fallback root: use earliest span
            if (ts.rootName == null && !traceRows.isEmpty()) {
                Row earliest = traceRows.stream()
                        .min(Comparator.comparingLong(r -> r.startEpochNanos))
                        .orElse(null);
                if (earliest != null) {
                    ts.rootName = compactUri(earliest);
                    if (ts.rootRouteId == null) {
                        ts.rootRouteId = earliest.routeId;
                    }
                }
            }
            long traceStart = Long.MAX_VALUE;
            long traceEnd = 0;
            Set<String> routes = new HashSet<>();
            Set<String> exchangeIds = new HashSet<>();
            Set<String> remoteSchemes = new LinkedHashSet<>();
            for (Row r : traceRows) {
                traceStart = Math.min(traceStart, r.startEpochNanos);
                traceEnd = Math.max(traceEnd, r.endEpochNanos);
                if (r.routeId != null) {
                    routes.add(r.routeId);
                }
                if (r.attributes != null) {
                    Object eid = r.attributes.get("exchangeId");
                    if (eid != null) {
                        exchangeIds.add(eid.toString());
                    }
                    Object scheme = r.attributes.get("url.scheme");
                    if (scheme != null && isRemoteScheme(scheme.toString())) {
                        remoteSchemes.add(scheme.toString());
                    }
                }
                ts.spanCount++;
            }
            ts.totalDurationMs = traceStart < Long.MAX_VALUE ? (traceEnd - traceStart) / 1_000_000 : 0;
            ts.routeCount = routes.size();
            ts.remoteComponents = remoteSchemes.isEmpty() ? "" : String.join(",", remoteSchemes);
            // Build search text
            StringBuilder sb = new StringBuilder();
            sb.append(ts.traceId).append(' ');
            exchangeIds.forEach(e -> sb.append(e).append(' '));
            routes.forEach(r -> sb.append(r).append(' '));
            if (!ts.remoteComponents.isEmpty()) {
                sb.append(ts.remoteComponents);
            }
            ts.searchText = sb.toString().toLowerCase();
        }

        // Default sort: newest first
        result.sort((a, b) -> {
            long at = rows.stream()
                    .filter(r -> r.traceId.equals(a.traceId))
                    .mapToLong(r -> r.startEpochNanos).max().orElse(0);
            long bt = rows.stream()
                    .filter(r -> r.traceId.equals(b.traceId))
                    .mapToLong(r -> r.startEpochNanos).max().orElse(0);
            return Long.compare(bt, at);
        });

        return result;
    }

    protected void tableTraces(List<TraceSummary> traces) {
        int tw = terminalWidth();
        int fixedWidth = 10 + 8 + 8 + 8 + 8 + 12;
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(8);
        int remaining = tw - fixedWidth - borderOverhead;
        int routeWidth = Math.max(10, Math.min(20, remaining / 3));
        int fromWidth = Math.max(10, Math.min(30, remaining - routeWidth));

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, traces, Arrays.asList(
                new Column().header("TRACE-ID").headerAlign(HorizontalAlign.CENTER)
                        .with(ts -> shortId(ts.traceId)),
                new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(routeWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(ts -> ts.rootRouteId != null ? ts.rootRouteId : ""),
                new Column().header("FROM").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(fromWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(ts -> ts.rootName != null ? ts.rootName : "?"),
                new Column().header("SPANS").headerAlign(HorizontalAlign.RIGHT)
                        .dataAlign(HorizontalAlign.RIGHT)
                        .with(ts -> String.valueOf(ts.spanCount)),
                new Column().header("ROUTES").headerAlign(HorizontalAlign.RIGHT)
                        .dataAlign(HorizontalAlign.RIGHT)
                        .with(ts -> String.valueOf(ts.routeCount)),
                new Column().header("REMOTE").dataAlign(HorizontalAlign.LEFT)
                        .with(ts -> ts.remoteComponents.isEmpty() ? "-" : ts.remoteComponents),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                        .with(ts -> ts.hasError ? "ERROR" : "OK"),
                new Column().header("DURATION").headerAlign(HorizontalAlign.RIGHT)
                        .dataAlign(HorizontalAlign.RIGHT)
                        .with(ts -> ts.totalDurationMs + "ms"))));
    }

    // --- Waterfall view ---

    private void printWaterfall(List<Row> allRows, String traceIdMatch) {
        // Find matching trace by substring
        String matchedTraceId = null;
        for (Row r : allRows) {
            if (r.traceId != null && r.traceId.contains(traceIdMatch)) {
                matchedTraceId = r.traceId;
                break;
            }
        }
        if (matchedTraceId == null) {
            printer().println("No trace found matching '" + traceIdMatch + "'");
            return;
        }

        final String tid = matchedTraceId;
        List<Row> traceRows = allRows.stream()
                .filter(r -> tid.equals(r.traceId))
                .sorted(Comparator.comparingLong(r -> r.startEpochNanos))
                .toList();

        List<WaterfallNode> nodes = buildWaterfallNodes(traceRows);
        if (nodes.isEmpty()) {
            printer().println("No spans in trace " + shortId(tid));
            return;
        }

        long traceStart = Long.MAX_VALUE;
        long traceEnd = 0;
        long minDuration = Long.MAX_VALUE;
        long maxDuration = 0;
        for (WaterfallNode n : nodes) {
            traceStart = Math.min(traceStart, n.row.startEpochNanos);
            traceEnd = Math.max(traceEnd, n.row.endEpochNanos);
            if (n.row.durationMs > 0) {
                minDuration = Math.min(minDuration, n.row.durationMs);
                maxDuration = Math.max(maxDuration, n.row.durationMs);
            }
        }
        if (minDuration == Long.MAX_VALUE) {
            minDuration = 0;
        }
        long traceDuration = (traceEnd - traceStart) / 1_000_000;

        printer().println();
        if (loggingColor) {
            printer().println(Ansi.ansi().bold()
                    .a("Trace ").a(shortId(tid)).a(" — ")
                    .a(nodes.size()).a(" spans, ").a(traceDuration).a("ms")
                    .reset().toString());
        } else {
            printer().println("Trace " + shortId(tid) + " — " + nodes.size() + " spans, " + traceDuration + "ms");
        }
        printer().println();

        int tw = terminalWidth();
        int labelWidth = 0;
        for (WaterfallNode n : nodes) {
            int indent = n.depth * 2;
            labelWidth = Math.max(labelWidth, indent + spanLabel(n.row).length());
        }
        labelWidth = Math.min(labelWidth + 2, tw / 3);
        int barMaxWidth = Math.max(10, tw - labelWidth - 12);

        for (WaterfallNode n : nodes) {
            printWaterfallLine(n, labelWidth, barMaxWidth, traceStart, traceDuration, minDuration, maxDuration);
        }
        printer().println();
    }

    private void printWaterfallLine(
            WaterfallNode node, int labelWidth, int maxBarWidth,
            long traceStart, long traceDuration, long minDuration, long maxDuration) {

        String indent = "  ".repeat(node.depth);
        String label = indent + spanLabel(node.row);
        if (label.length() > labelWidth) {
            label = label.substring(0, labelWidth - 1) + "…";
        } else {
            label = String.format("%-" + labelWidth + "s", label);
        }

        long spanStart = node.row.startEpochNanos - traceStart;
        long spanDuration = node.row.endEpochNanos - node.row.startEpochNanos;

        double offsetRatio = traceDuration > 0 ? (double) (spanStart / 1_000_000) / traceDuration : 0;
        double widthRatio = traceDuration > 0 ? (double) (spanDuration / 1_000_000) / traceDuration : 0;

        int barOffset = (int) Math.round(offsetRatio * maxBarWidth);
        int barWidth = Math.max(1, (int) Math.round(widthRatio * maxBarWidth));
        barOffset = Math.min(barOffset, maxBarWidth - 1);
        barWidth = Math.min(barWidth, maxBarWidth - barOffset);

        String gap = " ".repeat(barOffset);
        String bar = "█".repeat(barWidth);
        String durationStr = node.row.durationMs + "ms";
        int pad = Math.max(1, 8 - durationStr.length());
        boolean error = "ERROR".equals(node.row.status);

        if (loggingColor) {
            Ansi ansi = Ansi.ansi();
            // Label
            if (error) {
                ansi.fgRed().a(label).reset();
            } else {
                ansi.fgCyan().a(label).reset();
            }
            // Gap + bar
            ansi.a(gap);
            if (error) {
                ansi.fgRed().a(bar).reset();
            } else {
                ansi.fg(colorForDuration(node.row.durationMs, minDuration, maxDuration)).a(bar).reset();
            }
            // Error tag
            if (error) {
                ansi.fgBrightRed().bold().a(" ERR").reset();
            }
            // Duration
            ansi.a(" ".repeat(pad));
            if (error) {
                ansi.fgBrightRed().bold().a(durationStr).reset();
            } else {
                ansi.bold().a(durationStr).reset();
            }
            printer().println(ansi.toString());
        } else {
            String errorTag = error ? " ERR" : "";
            printer().println(label + gap + bar + errorTag + " ".repeat(pad) + durationStr);
        }
    }

    private static Ansi.Color colorForDuration(long duration, long minDuration, long maxDuration) {
        if (maxDuration <= minDuration) {
            return Ansi.Color.GREEN;
        }
        double ratio = (double) (duration - minDuration) / (maxDuration - minDuration);
        if (ratio < 0.33) {
            return Ansi.Color.GREEN;
        } else if (ratio < 0.66) {
            return Ansi.Color.YELLOW;
        } else {
            return Ansi.Color.RED;
        }
    }

    private List<WaterfallNode> buildWaterfallNodes(List<Row> traceRows) {
        if (traceRows.isEmpty()) {
            return List.of();
        }

        Map<String, List<Row>> childrenMap = new LinkedHashMap<>();
        Row root = null;
        for (Row row : traceRows) {
            if (isRoot(row)) {
                root = row;
            }
            String parentId = row.parentSpanId;
            if (parentId != null && !parentId.isEmpty()) {
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(row);
            }
        }
        if (root == null) {
            root = traceRows.get(0);
        }

        Set<String> included = new HashSet<>();
        Map<String, Integer> spanIdToDepth = new LinkedHashMap<>();
        List<WaterfallNode> result = new ArrayList<>();
        addToWaterfall(result, root, childrenMap, 0, included, spanIdToDepth);

        // Add orphan spans — insert after their parent when possible
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Row row : traceRows) {
                if (included.contains(row.spanId)) {
                    continue;
                }
                int depth = 0;
                int insertIdx = result.size();
                if (row.parentSpanId != null && spanIdToDepth.containsKey(row.parentSpanId)) {
                    depth = spanIdToDepth.get(row.parentSpanId) + 1;
                    // Find parent position and insert after it and its subtree
                    for (int i = 0; i < result.size(); i++) {
                        if (result.get(i).row.spanId.equals(row.parentSpanId)) {
                            int j = i + 1;
                            while (j < result.size() && result.get(j).depth > result.get(i).depth) {
                                j++;
                            }
                            insertIdx = j;
                            break;
                        }
                    }
                }
                result.add(insertIdx, new WaterfallNode(row, depth));
                included.add(row.spanId);
                spanIdToDepth.put(row.spanId, depth);
                changed = true;
            }
        }
        return result;
    }

    private void addToWaterfall(
            List<WaterfallNode> result, Row row,
            Map<String, List<Row>> childrenMap, int depth,
            Set<String> included, Map<String, Integer> spanIdToDepth) {
        if (!included.add(row.spanId)) {
            return;
        }
        spanIdToDepth.put(row.spanId, depth);

        List<Row> children = childrenMap.get(row.spanId);
        // Collapse EVENT_SENT → EVENT_RECEIVED pairs
        if (isEventSent(row) && !"ERROR".equals(row.status) && children != null && children.size() == 1
                && isEventReceived(children.get(0))
                && row.spanName != null && row.spanName.equals(children.get(0).spanName)) {
            addToWaterfall(result, children.get(0), childrenMap, depth, included, spanIdToDepth);
            return;
        }
        result.add(new WaterfallNode(row, depth));
        if (children != null) {
            for (Row child : children) {
                addToWaterfall(result, child, childrenMap, depth + 1, included, spanIdToDepth);
            }
        }
    }

    // --- Flat span view (original) ---

    protected void tableSpans(List<Row> rows) {
        int tw = terminalWidth();
        int fixedWidth = 10 + 10 + 10 + 12 + 8 + 10;
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(7);
        int nameWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 15, 60);

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("TRACE-ID").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> shortId(r.traceId)),
                new Column().header("SPAN-ID").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> shortId(r.spanId)),
                new Column().header("PARENT").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> shortId(r.parentSpanId)),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(nameWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.spanName),
                new Column().header("KIND").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.kind),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.status),
                new Column().header("DURATION").headerAlign(HorizontalAlign.RIGHT)
                        .dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.durationMs + "ms"))));
    }

    // --- Sort ---

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "name":
                return compareNullSafe(o1.spanName, o2.spanName) * negate;
            case "duration":
                return Long.compare(o1.durationMs, o2.durationMs) * negate;
            case "status":
                return compareNullSafe(o1.status, o2.status) * negate;
            default:
                return 0;
        }
    }

    private int sortTraceSummary(TraceSummary a, TraceSummary b, List<Row> rows) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        int cmp = switch (s) {
            case "route" -> compareNullSafe(a.rootRouteId, b.rootRouteId);
            case "from" -> compareNullSafe(a.rootName, b.rootName);
            case "duration" -> Long.compare(b.totalDurationMs, a.totalDurationMs);
            case "spans" -> Integer.compare(b.spanCount, a.spanCount);
            case "routes" -> Integer.compare(b.routeCount, a.routeCount);
            case "status" -> {
                int as = a.hasError ? 1 : 0;
                int bs = b.hasError ? 1 : 0;
                yield Integer.compare(bs, as);
            }
            default -> {
                // "trace" or unknown = newest first
                long at = rows.stream()
                        .filter(r -> r.traceId.equals(a.traceId))
                        .mapToLong(r -> r.startEpochNanos).max().orElse(0);
                long bt = rows.stream()
                        .filter(r -> r.traceId.equals(b.traceId))
                        .mapToLong(r -> r.startEpochNanos).max().orElse(0);
                yield Long.compare(bt, at);
            }
        };
        return cmp * negate;
    }

    // --- Helpers ---

    private boolean matchesFilter(String spanName, String pattern) {
        if (spanName == null) {
            return false;
        }
        return spanName.toLowerCase().contains(pattern.toLowerCase());
    }

    private static boolean isRoot(Row row) {
        return row.parentSpanId == null || row.parentSpanId.isEmpty();
    }

    private static boolean isEventSent(Row row) {
        return row.attributes != null && "EVENT_SENT".equals(row.attributes.get("op"));
    }

    private static boolean isEventReceived(Row row) {
        return row.attributes != null && "EVENT_RECEIVED".equals(row.attributes.get("op"));
    }

    private static boolean isRemoteScheme(String scheme) {
        return scheme != null
                && !"direct".equals(scheme) && !"seda".equals(scheme)
                && !"mock".equals(scheme) && !"log".equals(scheme)
                && !"bean".equals(scheme) && !"class".equals(scheme);
    }

    private static String spanLabel(Row row) {
        if (row.attributes != null) {
            Object uri = row.attributes.get("camel.uri");
            if (uri != null) {
                String label = uri.toString();
                if (row.routeId != null) {
                    label += " (" + row.routeId + ")";
                }
                return label;
            }
        }
        if (row.processorId != null) {
            String label = row.processorId;
            if (row.routeId != null) {
                label += " (" + row.routeId + ")";
            }
            return label;
        }
        return row.spanName != null ? row.spanName : "";
    }

    private static String compactUri(Row row) {
        if (row.attributes != null) {
            Object uri = row.attributes.get("camel.uri");
            if (uri != null) {
                String s = uri.toString();
                s = s.replace("://", ":");
                int q = s.indexOf('?');
                if (q > 0) {
                    s = s.substring(0, q);
                }
                return s;
            }
        }
        return row.spanName;
    }

    private static int compareNullSafe(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        if (id.length() > 8) {
            return id.substring(0, 8);
        }
        return id;
    }

    // --- Inner classes ---

    private static class Row {
        String pid;
        String name;
        String ago;
        String traceId;
        String spanId;
        String parentSpanId;
        String spanName;
        String kind;
        String status;
        long durationMs;
        String routeId;
        String processorId;
        long startEpochNanos;
        long endEpochNanos;
        Map<String, Object> attributes;
    }

    private static class TraceSummary {
        final String traceId;
        String rootRouteId;
        String rootName;
        int spanCount;
        long totalDurationMs;
        boolean hasError;
        int routeCount;
        String remoteComponents = "";
        String searchText = "";

        TraceSummary(String traceId) {
            this.traceId = traceId;
        }
    }

    private record WaterfallNode(Row row, int depth) {
    }
}
