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

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeLabelMode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.action.MessageTableHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.TerminalGraphics;
import org.jline.terminal.impl.TerminalGraphicsManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "error",
         description = "Get captured routing errors of Camel integrations", sortOptions = false, showDefaultValues = true)
public class ListError extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--route" },
                        description = "Filter by route ID")
    String route;

    @CommandLine.Option(names = { "--exception" },
                        description = "Filter by exception type (substring match)")
    String exception;

    @CommandLine.Option(names = { "--ago" },
                        description = "Filter by time window, e.g. 60s, 5m, 1h")
    String ago;

    @CommandLine.Option(names = { "--handled" },
                        description = "Filter by handled status (true or false)")
    String handled;

    @CommandLine.Option(names = { "--id" },
                        description = "Filter by exchange ID")
    String id;

    @CommandLine.Option(names = { "--limit" },
                        description = "Maximum number of entries to display")
    int limit;

    @CommandLine.Option(names = { "--show" },
                        description = "Comma-separated detail sections to show: body, headers, properties, variables, history, stackTrace, or 'all' for all sections")
    String show;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--detail" },
                        description = "Show full details of each error entry")
    boolean detail;

    @CommandLine.Option(names = { "--last" },
                        description = "Show only the last (newest) error with full details")
    boolean last;

    @CommandLine.Option(names = { "--diagram" },
                        description = "Display a route diagram with the error path highlighted")
    boolean diagram;

    @CommandLine.Option(names = { "--theme" }, defaultValue = "unicode",
                        description = "Diagram color theme (ascii, unicode, dark, light, transparent, or custom)")
    String theme = "unicode";

    public ListError(CamelJBangMain main) {
        super(main);
    }

    private static final Set<String> ALL_SECTIONS
            = Set.of("body", "headers", "properties", "variables", "history", "stackTrace");

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        if (diagram) {
            System.setProperty("java.awt.headless", "true");
            last = true;
        }
        if (last) {
            limit = 1;
            detail = true;
        }
        if (detail) {
            show = "all";
        }

        Set<String> showSet;
        if ("all".equals(show)) {
            showSet = ALL_SECTIONS;
        } else if (show != null) {
            showSet = Arrays.stream(show.split(",")).map(String::trim).collect(Collectors.toSet());
        } else {
            showSet = Set.of();
        }

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
                        String pName = context.getString("name");
                        if ("CamelJBang".equals(pName)) {
                            pName = ProcessHelper.extractName(root, ph);
                        }
                        String pid = Long.toString(ph.pid());

                        JsonObject errorRoot = loadErrorFile(ph.pid());
                        if (errorRoot != null) {
                            JsonArray arr = (JsonArray) errorRoot.get("errors");
                            if (arr != null) {
                                for (Object o : arr) {
                                    JsonObject jo = (JsonObject) o;
                                    Row row = new Row();
                                    row.pid = pid;
                                    row.name = pName;
                                    row.routeId = jo.getString("routeId");
                                    row.nodeId = jo.getString("nodeId");
                                    row.exchangeId = jo.getString("exchangeId");
                                    row.handled = jo.getBoolean("handled") != null && jo.getBoolean("handled");
                                    Long ts = jo.getLong("timestamp");
                                    if (ts != null) {
                                        row.timestamp = ts;
                                    }
                                    row.location = jo.getString("location");
                                    row.rawJson = jo;

                                    // extract exception info
                                    JsonObject ex = (JsonObject) jo.get("exception");
                                    if (ex != null) {
                                        row.exceptionType = ex.getString("type");
                                        row.exceptionMessage = ex.getString("message");
                                    }

                                    // message history
                                    Object mhObj = jo.get("messageHistory");
                                    if (mhObj instanceof JsonArray mhArr) {
                                        row.messageHistory = new String[mhArr.size()];
                                        for (int i = 0; i < mhArr.size(); i++) {
                                            row.messageHistory[i] = mhArr.get(i).toString();
                                        }
                                    }

                                    // apply client-side filters
                                    if (matchesFilters(row)) {
                                        rows.add(row);
                                    }
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        // apply limit
        List<Row> display = rows;
        if (limit > 0 && rows.size() > limit) {
            display = rows.subList(0, limit);
        }

        if (!display.isEmpty()) {
            // diagram mode: render route diagram with error path highlighted
            if (diagram) {
                Row row = display.get(0);
                if (row.messageHistory == null || row.messageHistory.length == 0) {
                    printer().println("No message history available for this error (enable message history in Camel)");
                    return 1;
                }
                return doDiagramCall(Long.parseLong(row.pid), row);
            }

            if (jsonOutput) {
                // dump the raw JSON from the error entries
                printer().println(Jsoner.serialize(display.stream().map(r -> r.rawJson).collect(Collectors.toList())));
            } else {
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, display, Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("AGO").dataAlign(HorizontalAlign.RIGHT)
                                .with(this::getAge),
                        new Column().header("ID").dataAlign(HorizontalAlign.LEFT)
                                .with(r -> r.exchangeId),
                        new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.routeId),
                        new Column().header("NODE").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.nodeId),
                        new Column().header("HANDLED").dataAlign(HorizontalAlign.CENTER)
                                .with(r -> r.handled ? "true" : "false"),
                        new Column().header("EXCEPTION").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> shortExceptionType(r.exceptionType)),
                        new Column().header("MESSAGE").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.exceptionMessage))));

                // show detail sections using MessageTableHelper
                if (!showSet.isEmpty()) {
                    MessageTableHelper tableHelper = new MessageTableHelper();
                    tableHelper.setLoggingColor(loggingColor);
                    tableHelper.setPretty(true);
                    tableHelper.setShowExchangeProperties(showSet.contains("properties"));
                    tableHelper.setShowExchangeVariables(showSet.contains("variables"));
                    tableHelper.setShowHeaders(showSet.contains("headers") || showSet.contains("body"));

                    for (Row r : display) {
                        printer().println();
                        // build the message root for MessageTableHelper
                        JsonObject msg = r.rawJson.getMap("message");
                        if (msg == null) {
                            msg = new JsonObject();
                        }
                        // promote exchange properties/variables into the message root
                        Object ep = r.rawJson.get("exchangeProperties");
                        if (ep != null) {
                            msg.put("exchangeProperties", ep);
                        }
                        Object ev = r.rawJson.get("exchangeVariables");
                        if (ev != null) {
                            msg.put("exchangeVariables", ev);
                        }
                        String exchangePattern = msg.getString("exchangePattern");
                        // exception
                        JsonObject cause = r.rawJson.getMap("exception");
                        String data = tableHelper.getDataAsTable(
                                r.exchangeId, exchangePattern, null, null, null, msg, cause);
                        if (data != null && !data.isEmpty()) {
                            printer().print(data);
                        }
                        // message history
                        if (showSet.contains("history") && r.messageHistory != null) {
                            printer().println("History");
                            for (String step : r.messageHistory) {
                                printer().printf("  %s%n", step);
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    private Integer doDiagramCall(long pid, Row row) throws Exception {
        // extract node IDs and route order from message history
        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> routeOrderSet = new LinkedHashSet<>();
        for (String step : row.messageHistory) {
            // format: routeId[nodeId] or routeId[nodeId] (elapsed ms)
            int open = step.indexOf('[');
            int close = step.indexOf(']');
            if (open > 0 && close > open) {
                String routeId = step.substring(0, open);
                String nodeId = step.substring(open + 1, close);
                if (nodeId != null && !nodeId.isEmpty()) {
                    nodeIds.add(nodeId);
                }
                if (routeId != null && !routeId.isEmpty()) {
                    routeOrderSet.add(routeId);
                }
            }
        }

        if (nodeIds.isEmpty()) {
            printer().println("No node IDs found in error message history");
            return 1;
        }

        RouteDiagramHelper.HighlightStyle hlStyle = RouteDiagramHelper.HighlightStyle.FAIL;

        // request route structure from the running process
        String pidStr = Long.toString(pid);
        Path outputFile = getOutputFile(pidStr);
        PathUtils.deleteFile(outputFile);

        JsonObject action = new JsonObject();
        action.put("action", "route-structure");
        action.put("filter", "*");
        action.put("brief", false);
        action.put("metric", false);
        Path actionFile = getActionFile(pidStr);
        try {
            Files.writeString(actionFile, action.toJson());
        } catch (Exception e) {
            // ignore
        }

        JsonObject structureJson = waitForJsonResponse(outputFile);
        if (structureJson == null) {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        try {
            List<RouteInfo> routes = RouteDiagramHelper.parseRoutes(structureJson);
            if (routes.isEmpty()) {
                printer().println("No routes found");
                return 1;
            }

            // add structural parent node IDs for each route that has highlighted nodes
            for (RouteInfo ri : routes) {
                boolean routeHasHighlight = ri.nodes.stream().anyMatch(n -> n.id != null && nodeIds.contains(n.id));
                if (routeHasHighlight) {
                    addParentNodes(ri.nodes, nodeIds);
                }
            }

            // filter and order routes by highlighted path
            RouteDiagramHelper.HighlightInfo highlightInfo
                    = new RouteDiagramHelper.HighlightInfo(nodeIds, new ArrayList<>(routeOrderSet), hlStyle);
            routes = RouteDiagramHelper.filterAndOrderRoutes(routes, highlightInfo);
            if (routes.isEmpty()) {
                printer().println("No routes contain highlighted nodes from error history");
                return 1;
            }

            // layout
            RouteDiagramLayoutEngine engine
                    = new RouteDiagramLayoutEngine(180, 12, NodeLabelMode.BOTH);

            List<LayoutRoute> layoutRoutes = new ArrayList<>();
            int currentY = RouteDiagramLayoutEngine.PADDING;
            for (RouteInfo ri : routes) {
                LayoutRoute lr = engine.layoutRoute(ri, currentY);
                layoutRoutes.add(lr);
                currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
            }

            boolean textMode = isTextTheme();
            if (textMode) {
                RouteDiagramAsciiRenderer asciiRenderer
                        = new RouteDiagramAsciiRenderer(engine.getNodeWidth(), isUnicodeTheme());
                String ascii = asciiRenderer.renderDiagramAnsi(layoutRoutes, currentY, nodeIds, hlStyle);
                printer().println(ascii);
            } else {
                DiagramColors colors = DiagramColors.parse(theme);
                RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                        engine.getNodeWidth(), 12 * RouteDiagramLayoutEngine.SCALE, engine.getNodeTextPadding(), false);

                BufferedImage image;
                try {
                    image = renderer.renderDiagram(layoutRoutes, currentY, colors, nodeIds, hlStyle);
                } catch (IllegalStateException e) {
                    printer().println(e.getMessage());
                    return 1;
                }

                try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
                    TerminalGraphics tg = TerminalGraphicsManager.getBestProtocol(terminal).orElse(null);
                    if (tg == null) {
                        printer().println(
                                "Terminal does not support graphics. Use --theme=ascii or --theme=unicode for text output.");
                        return 1;
                    }
                    TerminalGraphics.ImageOptions opts = new TerminalGraphics.ImageOptions().preserveAspectRatio(true);
                    tg.displayImage(terminal, image, opts);
                    terminal.writer().println();
                    terminal.flush();
                }
            }
            return 0;
        } finally {
            PathUtils.deleteFile(outputFile);
        }
    }

    private static void addParentNodes(List<RouteDiagramLayoutEngine.NodeInfo> nodes, Set<String> nodeIds) {
        for (int i = 0; i < nodes.size(); i++) {
            RouteDiagramLayoutEngine.NodeInfo node = nodes.get(i);
            if (node.id == null || nodeIds.contains(node.id)) {
                continue;
            }
            boolean hasHighlightedChild = false;
            for (int j = i + 1; j < nodes.size(); j++) {
                RouteDiagramLayoutEngine.NodeInfo child = nodes.get(j);
                if (child.level <= node.level) {
                    break;
                }
                if (child.id != null && nodeIds.contains(child.id)) {
                    hasHighlightedChild = true;
                    break;
                }
            }
            if (hasHighlightedChild) {
                nodeIds.add(node.id);
            }
        }
    }

    private static JsonObject waitForJsonResponse(Path outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                Thread.sleep(100);
                if (Files.exists(outputFile) && outputFile.toFile().length() > 0) {
                    String text = Files.readString(outputFile);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private boolean matchesFilters(Row row) {
        if (id != null && !id.equals(row.exchangeId)) {
            return false;
        }
        if (route != null && !route.equals(row.routeId)) {
            return false;
        }
        if (exception != null && (row.exceptionType == null
                || !row.exceptionType.toLowerCase().contains(exception.toLowerCase()))) {
            return false;
        }
        if (handled != null && !handled.equals(String.valueOf(row.handled))) {
            return false;
        }
        if (ago != null) {
            try {
                long millis = TimeUtils.toMilliSeconds(ago);
                long cutoff = System.currentTimeMillis() - millis;
                if (row.timestamp < cutoff) {
                    return false;
                }
            } catch (Exception e) {
                // ignore invalid ago value
            }
        }
        return true;
    }

    private String getAge(Row r) {
        if (r.timestamp > 0) {
            return TimeUtils.printSince(r.timestamp);
        }
        return "";
    }

    private boolean isTextTheme() {
        return "ascii".equalsIgnoreCase(theme) || "unicode".equalsIgnoreCase(theme);
    }

    private boolean isUnicodeTheme() {
        return "unicode".equalsIgnoreCase(theme);
    }

    private static String shortExceptionType(String type) {
        if (type == null) {
            return "";
        }
        int dot = type.lastIndexOf('.');
        if (dot > 0) {
            return type.substring(dot + 1);
        }
        return type;
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
                return Long.compare(o1.timestamp, o2.timestamp) * negate;
            default:
                return 0;
        }
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        long timestamp;
        String routeId;
        String nodeId;
        String exchangeId;
        boolean handled;
        String location;
        String exceptionType;
        String exceptionMessage;
        String[] messageHistory;
        JsonObject rawJson;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
