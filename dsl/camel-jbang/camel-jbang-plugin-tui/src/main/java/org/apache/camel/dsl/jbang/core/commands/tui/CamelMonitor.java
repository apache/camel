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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.gauge.Gauge;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "monitor",
         description = "Live dashboard for monitoring Camel integrations",
         sortOptions = false)
public class CamelMonitor extends CamelCommand {

    private static final long VANISH_DURATION_MS = 6000;
    private static final long DEFAULT_REFRESH_MS = 100;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final int MAX_LOG_LINES = 200;
    private static final int MAX_TRACES = 200;
    private static final int NUM_TABS = 6;

    // Tab indices
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_ROUTES = 1;
    private static final int TAB_HEALTH = 2;
    private static final int TAB_ENDPOINTS = 3;
    private static final int TAB_LOG = 4;
    private static final int TAB_TRACE = 5;

    // Route sort columns
    private static final String[] ROUTE_SORT_COLUMNS = { "mean", "max", "total", "failed", "name" };

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "100")
    long refreshInterval = DEFAULT_REFRESH_MS;

    // State
    private final AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(Collections.emptyList());
    private final Map<String, VanishingInfo> vanishing = new ConcurrentHashMap<>();
    private final TableState overviewTableState = new TableState();
    private final TableState routeTableState = new TableState();
    private final TableState healthTableState = new TableState();
    private final TableState endpointTableState = new TableState();
    private final TabsState tabsState = new TabsState(TAB_OVERVIEW);

    // Sparkline: throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> throughputHistory = new ConcurrentHashMap<>();
    // Sliding window of [timestamp, exchangesTotal] samples for smoothing
    private final Map<String, LinkedList<long[]>> throughputSamples = new ConcurrentHashMap<>();
    // Track last time a sparkline point was recorded
    private final Map<String, Long> previousExchangesTime = new ConcurrentHashMap<>();

    // Route sort state
    private String routeSort = "mean";
    private int routeSortIndex;

    // Health filter state
    private boolean showOnlyDown;

    // Log state
    private final List<String> logLines = new ArrayList<>();
    private final List<LogEntry> filteredLogEntries = new ArrayList<>();
    private final TableState logTableState = new TableState();
    private boolean logFollowMode = true;
    private boolean showLogTrace = true;
    private boolean showLogDebug = true;
    private boolean showLogInfo = true;
    private boolean showLogWarn = true;
    private boolean showLogError = true;

    // Trace state
    private final AtomicReference<List<TraceEntry>> traces = new AtomicReference<>(Collections.emptyList());
    private final TableState traceTableState = new TableState();
    private final Map<String, Long> traceFilePositions = new ConcurrentHashMap<>();
    private boolean showTraceHeaders = true;
    private boolean showTraceBody = true;
    private boolean traceFollowMode = true;

    // Selected integration for detail views
    private String selectedPid;

    private volatile long lastRefresh;

    private ClassLoader classLoader;

    public CamelMonitor(CamelJBangMain main, ClassLoader classLoader) {
        super(main);
        this.classLoader = classLoader;
    }

    @Override
    public Integer doCall() throws Exception {
        // to make ServiceLoader work with tamboui for downloaded JARs
        Thread.currentThread().setContextClassLoader(classLoader);
        TuiHelper.preloadClasses(classLoader);

        // Initial data load
        refreshData();

        try (var tui = TuiRunner.create()) {
            // Intercept Ctrl+C: quit the TUI cleanly instead of letting
            // the JVM tear down the classloader while we're still running
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> tui.quit());
            tui.run(
                    this::handleEvent,
                    this::render);
        }
        return 0;
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            // Global keys
            if (ke.isQuit() || ke.isCharIgnoreCase('q') || ke.isKey(KeyCode.ESCAPE)) {
                // If in a detail tab, go back to overview first
                if (tabsState.selected() != TAB_OVERVIEW) {
                    tabsState.select(TAB_OVERVIEW);
                    selectedPid = null;
                    return true;
                }
                runner.quit();
                return true;
            }
            if (ke.isChar('r')) {
                refreshData();
                return true;
            }

            // Tab switching with number keys
            if (ke.isChar('1')) {
                return handleTabKey(TAB_OVERVIEW);
            }
            if (ke.isChar('2')) {
                return handleTabKey(TAB_ROUTES);
            }
            if (ke.isChar('3')) {
                return handleTabKey(TAB_HEALTH);
            }
            if (ke.isChar('4')) {
                return handleTabKey(TAB_ENDPOINTS);
            }
            if (ke.isChar('5')) {
                return handleTabKey(TAB_LOG);
            }
            if (ke.isChar('6')) {
                return handleTabKey(TAB_TRACE);
            }

            // Tab cycling
            if (ke.isKey(KeyCode.TAB)) {
                int next = (tabsState.selected() + 1) % NUM_TABS;
                if (next != TAB_OVERVIEW) {
                    selectCurrentIntegration();
                }
                tabsState.select(next);
                return true;
            }

            // Tab-specific keys
            int tab = tabsState.selected();

            // Navigation (all tabs)
            if (ke.isUp()) {
                navigateUp();
                return true;
            }
            if (ke.isDown()) {
                navigateDown();
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_UP)) {
                if (tab == TAB_LOG) {
                    logFollowMode = false;
                    for (int i = 0; i < 20; i++) {
                        logTableState.selectPrevious();
                    }
                }
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_DOWN)) {
                if (tab == TAB_LOG) {
                    for (int i = 0; i < 20; i++) {
                        logTableState.selectNext(filteredLogEntries.size());
                    }
                }
                return true;
            }

            // Enter to drill into selected integration
            if (ke.isKey(KeyCode.ENTER) && tab == TAB_OVERVIEW) {
                selectCurrentIntegration();
                if (selectedPid != null) {
                    tabsState.select(TAB_ROUTES);
                }
                return true;
            }

            // Routes tab: sort
            if (tab == TAB_ROUTES && ke.isCharIgnoreCase('s')) {
                routeSortIndex = (routeSortIndex + 1) % ROUTE_SORT_COLUMNS.length;
                routeSort = ROUTE_SORT_COLUMNS[routeSortIndex];
                return true;
            }

            // Health tab: DOWN filter
            if (tab == TAB_HEALTH && ke.isCharIgnoreCase('d')) {
                showOnlyDown = !showOnlyDown;
                return true;
            }

            // Log tab: level filters and follow mode
            if (tab == TAB_LOG) {
                if (ke.isCharIgnoreCase('t')) {
                    showLogTrace = !showLogTrace;
                    applyLogFilters();
                    return true;
                }
                if (ke.isCharIgnoreCase('d')) {
                    showLogDebug = !showLogDebug;
                    applyLogFilters();
                    return true;
                }
                if (ke.isCharIgnoreCase('i')) {
                    showLogInfo = !showLogInfo;
                    applyLogFilters();
                    return true;
                }
                if (ke.isCharIgnoreCase('w')) {
                    showLogWarn = !showLogWarn;
                    applyLogFilters();
                    return true;
                }
                if (ke.isCharIgnoreCase('e')) {
                    showLogError = !showLogError;
                    applyLogFilters();
                    return true;
                }
                if (ke.isCharIgnoreCase('f')) {
                    logFollowMode = !logFollowMode;
                    return true;
                }
                if (ke.isChar('g')) {
                    logFollowMode = false;
                    logTableState.select(0);
                    return true;
                }
                if (ke.isChar('G')) {
                    logFollowMode = true;
                    return true;
                }
            }

            // Trace tab: headers/body toggle and follow mode
            if (tab == TAB_TRACE) {
                if (ke.isCharIgnoreCase('h')) {
                    showTraceHeaders = !showTraceHeaders;
                    return true;
                }
                if (ke.isCharIgnoreCase('b')) {
                    showTraceBody = !showTraceBody;
                    return true;
                }
                if (ke.isCharIgnoreCase('f')) {
                    traceFollowMode = !traceFollowMode;
                    if (traceFollowMode) {
                        List<TraceEntry> current = traces.get();
                        if (!current.isEmpty()) {
                            traceTableState.select(current.size() - 1);
                        }
                    }
                    return true;
                }
            }
        }
        if (event instanceof TickEvent) {
            long now = System.currentTimeMillis();
            if (now - lastRefresh >= refreshInterval) {
                refreshData();
            }
            return true;
        }
        return false;
    }

    private boolean handleTabKey(int tab) {
        if (tab != TAB_OVERVIEW) {
            selectCurrentIntegration();
        } else {
            selectedPid = null;
        }
        tabsState.select(tab);
        return true;
    }

    private void selectCurrentIntegration() {
        if (selectedPid != null) {
            return;
        }
        List<IntegrationInfo> infos = data.get().stream().filter(i -> !i.vanishing).toList();
        Integer sel = overviewTableState.selected();
        if (sel != null && sel >= 0 && sel < infos.size()) {
            selectedPid = infos.get(sel).pid;
        } else if (infos.size() == 1) {
            selectedPid = infos.get(0).pid;
        }
    }

    private void navigateUp() {
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTableState.selectPrevious();
            case TAB_ROUTES -> routeTableState.selectPrevious();
            case TAB_HEALTH -> healthTableState.selectPrevious();
            case TAB_ENDPOINTS -> endpointTableState.selectPrevious();
            case TAB_LOG -> {
                logFollowMode = false;
                logTableState.selectPrevious();
            }
            case TAB_TRACE -> {
                traceFollowMode = false;
                traceTableState.selectPrevious();
            }
        }
    }

    private void navigateDown() {
        List<IntegrationInfo> infos = data.get().stream().filter(i -> !i.vanishing).toList();
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTableState.selectNext(infos.size());
            case TAB_ROUTES -> {
                IntegrationInfo info = findSelectedIntegration();
                routeTableState.selectNext(info != null ? info.routes.size() : 0);
            }
            case TAB_HEALTH -> {
                IntegrationInfo info = findSelectedIntegration();
                healthTableState.selectNext(info != null ? getFilteredHealthChecks(info).size() : 0);
            }
            case TAB_ENDPOINTS -> {
                IntegrationInfo info = findSelectedIntegration();
                endpointTableState.selectNext(info != null ? info.endpoints.size() : 0);
            }
            case TAB_LOG -> logTableState.selectNext(filteredLogEntries.size());
            case TAB_TRACE -> {
                List<TraceEntry> current = traces.get();
                traceTableState.selectNext(current.size());
            }
        }
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (3 rows) + tabs (2 rows) + content (fill) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.length(2),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderTabs(frame, mainChunks.get(1));
        renderContent(frame, mainChunks.get(2));
        renderFooter(frame, mainChunks.get(3));
    }

    private void renderHeader(Frame frame, Rect area) {
        List<IntegrationInfo> infos = data.get();
        String camelVersion = VersionHelper.extractCamelVersion();
        long activeCount = infos.stream().filter(i -> !i.vanishing).count();

        Line titleLine = Line.from(
                Span.styled(" Camel Monitor", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(camelVersion != null ? "v" + camelVersion : "", Style.create().fg(Color.GREEN)),
                Span.raw("  "),
                Span.styled(activeCount + " integration(s)", Style.create().fg(Color.CYAN)));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Apache Camel ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderTabs(Frame frame, Rect area) {
        String sel = selectedPid != null ? " [" + selectedName() + "]" : "";
        Tabs tabs = Tabs.builder()
                .titles(
                        " 1 Overview ",
                        " 2 Routes" + sel + " ",
                        " 3 Health" + sel + " ",
                        " 4 Endpoints" + sel + " ",
                        " 5 Log" + sel + " ",
                        " 6 Trace" + sel + " ")
                .highlightStyle(Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold())
                .divider(Span.styled(" | ", Style.create().dim()))
                .build();

        frame.renderStatefulWidget(tabs, area, tabsState);
    }

    private void renderContent(Frame frame, Rect area) {
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> renderOverview(frame, area);
            case TAB_ROUTES -> renderRoutes(frame, area);
            case TAB_HEALTH -> renderHealth(frame, area);
            case TAB_ENDPOINTS -> renderEndpoints(frame, area);
            case TAB_LOG -> renderLog(frame, area);
            case TAB_TRACE -> renderTrace(frame, area);
        }
    }

    // ---- Tab 1: Overview ----

    private void renderOverview(Frame frame, Rect area) {
        List<IntegrationInfo> infos = data.get();

        // Split: table (fill) + sparkline (height 8) if we have data
        boolean hasSparkline = !throughputHistory.isEmpty();
        List<Rect> chunks;
        if (hasSparkline) {
            chunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(8))
                    .split(area);
        } else {
            chunks = List.of(area);
        }

        // Integration table
        List<Row> rows = new ArrayList<>();
        for (IntegrationInfo info : infos) {
            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.create().fg(Color.indexed(232 + Math.min(gray / 4, 23)));

                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(info.name != null ? info.name : "", dimStyle)),
                        Cell.from(Span.styled(info.platform != null ? info.platform : "", dimStyle)),
                        Cell.from(Span.styled("\u2716 Stopped", Style.create().fg(Color.RED).dim())),
                        Cell.from(Span.styled(info.ago != null ? info.ago : "", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))));
            } else {
                Style statusStyle = switch (extractState(info.state)) {
                    case "Started" -> Style.create().fg(Color.GREEN);
                    case "Stopped" -> Style.create().fg(Color.RED);
                    default -> Style.create().fg(Color.YELLOW);
                };

                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(info.name != null ? info.name : "", Style.create().fg(Color.CYAN))),
                        Cell.from(info.platform != null ? info.platform : ""),
                        Cell.from(Span.styled(extractState(info.state), statusStyle)),
                        Cell.from(info.ago != null ? info.ago : ""),
                        Cell.from(info.throughput != null ? info.throughput : ""),
                        Cell.from(formatMemory(info.heapMemUsed, info.heapMemMax)),
                        Cell.from(formatThreads(info.threadCount, info.peakThreadCount))));
            }
        }

        Row header = Row.from(
                Cell.from(Span.styled("PID", Style.create().bold())),
                Cell.from(Span.styled("NAME", Style.create().bold())),
                Cell.from(Span.styled("PLATFORM", Style.create().bold())),
                Cell.from(Span.styled("STATUS", Style.create().bold())),
                Cell.from(Span.styled("AGE", Style.create().bold())),
                Cell.from(Span.styled("THRUPUT", Style.create().bold())),
                Cell.from(Span.styled("HEAP", Style.create().bold())),
                Cell.from(Span.styled("THREADS", Style.create().bold())));

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(15),
                        Constraint.length(12))
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Integrations ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), overviewTableState);

        // Bar chart for throughput
        if (hasSparkline && chunks.size() > 1) {
            // Merge all throughput histories for overview chart
            LinkedList<Long> merged = new LinkedList<>();
            for (int i = 0; i < MAX_SPARKLINE_POINTS; i++) {
                long sum = 0;
                for (LinkedList<Long> hist : throughputHistory.values()) {
                    if (i < hist.size()) {
                        sum += hist.get(hist.size() - 1 - i);
                    }
                }
                merged.addFirst(sum);
            }

            // Compute stats for title display
            long maxTp = merged.stream().mapToLong(Long::longValue).max().orElse(0);
            long curTp = merged.isEmpty() ? 0 : merged.get(merged.size() - 1);
            String chartTitle = String.format(" Throughput: %d msg/s (peak: %d) ", curTp, maxTp);

            // Build bar groups — one bar per data point
            List<BarGroup> groups = new ArrayList<>();
            for (Long value : merged) {
                groups.add(BarGroup.of(Bar.of(value)));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .barWidth(1)
                    .barGap(0)
                    .barStyle(Style.create().fg(Color.GREEN))
                    .block(Block.builder().borderType(BorderType.ROUNDED).title(chartTitle).build())
                    .build();

            frame.renderWidget(barChart, chunks.get(1));
        }
    }

    // ---- Tab 2: Routes ----

    private void renderRoutes(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Sort routes
        List<RouteInfo> sortedRoutes = new ArrayList<>(info.routes);
        sortedRoutes.sort(this::sortRoute);

        // Split: routes table (top half) + processors table (bottom half)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(45), Constraint.percentage(55))
                .split(area);

        // Routes table
        List<Row> routeRows = new ArrayList<>();
        for (RouteInfo route : sortedRoutes) {
            Style stateStyle = "Started".equals(route.state)
                    ? Style.create().fg(Color.GREEN)
                    : Style.create().fg(Color.RED);

            Style failStyle = route.failed > 0
                    ? Style.create().fg(Color.RED).bold()
                    : Style.create();

            routeRows.add(Row.from(
                    Cell.from(Span.styled(route.routeId != null ? route.routeId : "", Style.create().fg(Color.CYAN))),
                    Cell.from(route.from != null ? route.from : ""),
                    Cell.from(Span.styled(route.state != null ? route.state : "", stateStyle)),
                    Cell.from(route.uptime != null ? route.uptime : ""),
                    Cell.from(route.throughput != null ? route.throughput : ""),
                    Cell.from(String.valueOf(route.total)),
                    Cell.from(Span.styled(String.valueOf(route.failed), failStyle)),
                    Cell.from(route.meanTime + "/" + route.maxTime)));
        }

        Table routeTable = Table.builder()
                .rows(routeRows)
                .header(Row.from(
                        Cell.from(Span.styled("ROUTE", Style.create().bold())),
                        Cell.from(Span.styled("FROM", Style.create().bold())),
                        Cell.from(Span.styled("STATE", Style.create().bold())),
                        Cell.from(Span.styled("UPTIME", Style.create().bold())),
                        Cell.from(Span.styled("THRUPUT", Style.create().bold())),
                        Cell.from(Span.styled(routeSortLabel("TOTAL", "total"), routeSortStyle("total"))),
                        Cell.from(Span.styled(routeSortLabel("FAILED", "failed"), routeSortStyle("failed"))),
                        Cell.from(Span.styled(routeSortLabel("MEAN/MAX", "mean"), routeSortStyle("mean")))))
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(12))
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Routes [" + info.name + "] sort:" + routeSort + " ").build())
                .build();

        frame.renderStatefulWidget(routeTable, chunks.get(0), routeTableState);

        // Processors for selected route
        Integer selectedRoute = routeTableState.selected();
        if (selectedRoute != null && selectedRoute >= 0 && selectedRoute < sortedRoutes.size()) {
            RouteInfo route = sortedRoutes.get(selectedRoute);
            renderProcessors(frame, chunks.get(1), route);
        } else if (!sortedRoutes.isEmpty()) {
            renderProcessors(frame, chunks.get(1), sortedRoutes.get(0));
        } else {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("No routes", Style.create().dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Processors ").build())
                            .build(),
                    chunks.get(1));
        }
    }

    private int sortRoute(RouteInfo a, RouteInfo b) {
        return switch (routeSort) {
            case "mean" -> Long.compare(b.meanTime, a.meanTime);
            case "max" -> Long.compare(b.maxTime, a.maxTime);
            case "total" -> Long.compare(b.total, a.total);
            case "failed" -> Long.compare(b.failed, a.failed);
            case "name" -> {
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
            default -> 0;
        };
    }

    private String routeSortLabel(String label, String column) {
        return routeSort.equals(column) ? label + "\u25BC" : label;
    }

    private Style routeSortStyle(String column) {
        return routeSort.equals(column)
                ? Style.create().fg(Color.YELLOW).bold()
                : Style.create().bold();
    }

    private void renderProcessors(Frame frame, Rect area, RouteInfo route) {
        List<Row> rows = new ArrayList<>();
        for (ProcessorInfo proc : route.processors) {
            String indent = "  ".repeat(proc.level);
            Style nameStyle = proc.failed > 0 ? Style.create().fg(Color.RED) : Style.create().fg(Color.CYAN);

            rows.add(Row.from(
                    Cell.from(Span.styled(indent + (proc.id != null ? proc.id : ""), nameStyle)),
                    Cell.from(proc.processor != null ? proc.processor : ""),
                    Cell.from(String.valueOf(proc.total)),
                    Cell.from(proc.failed > 0
                            ? Span.styled(String.valueOf(proc.failed), Style.create().fg(Color.RED))
                            : Span.raw("0")),
                    Cell.from(proc.meanTime + "ms"),
                    Cell.from(proc.maxTime + "ms"),
                    Cell.from(proc.lastTime + "ms")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("PROCESSOR", Style.create().bold())),
                        Cell.from(Span.styled("TYPE", Style.create().bold())),
                        Cell.from(Span.styled("TOTAL", Style.create().bold())),
                        Cell.from(Span.styled("FAILED", Style.create().bold())),
                        Cell.from(Span.styled("MEAN", Style.create().bold())),
                        Cell.from(Span.styled("MAX", Style.create().bold())),
                        Cell.from(Span.styled("LAST", Style.create().bold()))))
                .widths(
                        Constraint.fill(),
                        Constraint.length(15),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8))
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Processors [" + route.routeId + "] ").build())
                .build();

        frame.renderStatefulWidget(table, area, new TableState());
    }

    // ---- Tab 3: Health ----

    private void renderHealth(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Split: health table (fill) + memory gauge (3 rows)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(3))
                .split(area);

        List<HealthCheckInfo> healthChecks = getFilteredHealthChecks(info);

        List<Row> rows = new ArrayList<>();
        for (HealthCheckInfo hc : healthChecks) {
            Style stateStyle;
            String icon;
            if ("UP".equals(hc.state)) {
                stateStyle = Style.create().fg(Color.GREEN);
                icon = "\u2714 ";
            } else if ("DOWN".equals(hc.state)) {
                stateStyle = Style.create().fg(Color.RED);
                icon = "\u2716 ";
            } else {
                stateStyle = Style.create().fg(Color.YELLOW);
                icon = "\u26A0 ";
            }

            String kind = "";
            if (hc.readiness) {
                kind += "R";
            }
            if (hc.liveness) {
                kind += kind.isEmpty() ? "L" : "/L";
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(hc.group != null ? hc.group : "", Style.create().dim())),
                    Cell.from(Span.styled(hc.name != null ? hc.name : "", Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(icon + hc.state, stateStyle)),
                    Cell.from(kind),
                    Cell.from(hc.message != null ? hc.message : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled(showOnlyDown ? "No DOWN checks" : "No health checks registered",
                            Style.create().dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        String title = showOnlyDown
                ? " Health [" + info.name + "] [DOWN only] "
                : " Health [" + info.name + "] ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("GROUP", Style.create().bold())),
                        Cell.from(Span.styled("NAME", Style.create().bold())),
                        Cell.from(Span.styled("STATUS", Style.create().bold())),
                        Cell.from(Span.styled("KIND", Style.create().bold())),
                        Cell.from(Span.styled("MESSAGE", Style.create().bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(25),
                        Constraint.length(12),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), healthTableState);

        // Memory gauge
        if (info.heapMemMax > 0) {
            int pct = (int) (100.0 * info.heapMemUsed / info.heapMemMax);
            Style gaugeStyle = pct > 80 ? Style.create().fg(Color.RED)
                    : pct > 60 ? Style.create().fg(Color.YELLOW) : Style.create().fg(Color.GREEN);
            Gauge gauge = Gauge.builder()
                    .percent(pct)
                    .label(String.format("Heap: %s / %s (%d%%)", formatBytes(info.heapMemUsed),
                            formatBytes(info.heapMemMax), pct))
                    .gaugeStyle(gaugeStyle)
                    .block(Block.builder().borderType(BorderType.ROUNDED).build())
                    .build();

            frame.renderWidget(gauge, chunks.get(1));
        }
    }

    private List<HealthCheckInfo> getFilteredHealthChecks(IntegrationInfo info) {
        if (showOnlyDown) {
            return info.healthChecks.stream().filter(hc -> "DOWN".equals(hc.state)).toList();
        }
        return info.healthChecks;
    }

    // ---- Tab 4: Endpoints ----

    private void renderEndpoints(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (EndpointInfo ep : info.endpoints) {
            String dir = ep.direction != null ? ep.direction : "";
            Style dirStyle = switch (dir) {
                case "in" -> Style.create().fg(Color.GREEN);
                case "out" -> Style.create().fg(Color.BLUE);
                default -> Style.create().fg(Color.YELLOW);
            };
            String arrow = switch (dir) {
                case "in" -> "\u2192 ";
                case "out" -> "\u2190 ";
                default -> "\u2194 ";
            };

            rows.add(Row.from(
                    Cell.from(Span.styled(ep.component != null ? ep.component : "", Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(arrow + dir, dirStyle)),
                    Cell.from(ep.uri != null ? ep.uri : ""),
                    Cell.from(ep.routeId != null ? ep.routeId : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("No endpoints", Style.create().dim())),
                    Cell.from(""),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("COMPONENT", Style.create().bold())),
                        Cell.from(Span.styled("DIR", Style.create().bold())),
                        Cell.from(Span.styled("URI", Style.create().bold())),
                        Cell.from(Span.styled("ROUTE", Style.create().bold()))))
                .widths(
                        Constraint.length(15),
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(20))
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Endpoints [" + info.name + "] ").build())
                .build();

        frame.renderStatefulWidget(table, area, endpointTableState);
    }

    // ---- Tab 5: Log ----

    private void renderLog(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Log data is refreshed in refreshData() tick handler

        // Auto-follow: select last entry
        if (logFollowMode && !filteredLogEntries.isEmpty()) {
            logTableState.select(filteredLogEntries.size() - 1);
        }

        // Split: log table (60%) + detail (40%)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(60), Constraint.fill())
                .split(area);

        // Log table
        List<Row> rows = new ArrayList<>();
        for (LogEntry entry : filteredLogEntries) {
            Style levelStyle = switch (entry.level) {
                case "ERROR", "FATAL" -> Style.create().fg(Color.RED);
                case "WARN" -> Style.create().fg(Color.YELLOW);
                case "DEBUG", "TRACE" -> Style.create().dim();
                default -> Style.create();
            };

            rows.add(Row.from(
                    Cell.from(Span.styled(entry.time, Style.create().dim())),
                    Cell.from(Span.styled(entry.level, levelStyle)),
                    Cell.from(Span.styled(entry.logger != null ? entry.logger : "", Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(entry.message, levelStyle))));
        }

        String levelTitle = buildLevelFilterTitle();
        Table logTable = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("TIME", Style.create().bold())),
                        Cell.from(Span.styled("LEVEL", Style.create().bold())),
                        Cell.from(Span.styled("LOGGER", Style.create().bold())),
                        Cell.from(Span.styled("MESSAGE", Style.create().bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(6),
                        Constraint.length(20),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Log [" + info.name + "] " + levelTitle).build())
                .build();

        frame.renderStatefulWidget(logTable, chunks.get(0), logTableState);

        // Detail panel for selected log entry
        renderLogDetail(frame, chunks.get(1));
    }

    private void renderLogDetail(Frame frame, Rect area) {
        Integer sel = logTableState.selected();
        if (sel == null || sel < 0 || sel >= filteredLogEntries.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a log entry", Style.create().dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        LogEntry entry = filteredLogEntries.get(sel);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(Line.from(Span.styled(entry.raw, colorStyleForLevel(entry.level)))))
                        .overflow(Overflow.WRAP_WORD)
                        .block(Block.builder().borderType(BorderType.ROUNDED)
                                .title(" " + entry.time + " " + entry.level + " ").build())
                        .build(),
                area);
    }

    private Style colorStyleForLevel(String level) {
        return switch (level) {
            case "ERROR", "FATAL" -> Style.create().fg(Color.RED);
            case "WARN" -> Style.create().fg(Color.YELLOW);
            case "DEBUG", "TRACE" -> Style.create().dim();
            default -> Style.create();
        };
    }

    private String buildLevelFilterTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append(showLogTrace ? "[T] " : "[t] ");
        sb.append(showLogDebug ? "[D] " : "[d] ");
        sb.append(showLogInfo ? "[I] " : "[i] ");
        sb.append(showLogWarn ? "[W] " : "[w] ");
        sb.append(showLogError ? "[E] " : "[e] ");
        if (logFollowMode) {
            sb.append("[FOLLOW]");
        }
        return sb.toString();
    }

    private void readLogFile(String pid) {
        logLines.clear();
        Path logFile = CommandLineHelper.getCamelDir().resolve(pid + ".log");
        if (!Files.exists(logFile)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = raf.length();
            // Read last ~64KB to get recent lines
            long startPos = Math.max(0, length - 64 * 1024);
            raf.seek(startPos);
            if (startPos > 0) {
                raf.readLine(); // skip partial line
            }
            // Read remaining bytes and split into lines using proper encoding
            byte[] remaining = new byte[(int) (length - raf.getFilePointer())];
            raf.readFully(remaining);
            String content = new String(remaining, StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            int start = Math.max(0, lines.length - MAX_LOG_LINES);
            for (int i = start; i < lines.length; i++) {
                String line = lines[i].replaceAll("\u001B\\[[;\\d]*m", "");
                if (!line.isEmpty()) {
                    logLines.add(line);
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private void applyLogFilters() {
        filteredLogEntries.clear();
        for (String line : logLines) {
            LogEntry entry = parseLogLine(line);
            if (!matchesLogLevelFilter(entry.level)) {
                continue;
            }
            filteredLogEntries.add(entry);
        }
    }

    // Regex for Spring Boot / Camel log format:
    // "2026-03-23T21:24:11.705+01:00  WARN 11283 --- [thread] logger : message"
    // "2026-03-23 21:24:11.705  WARN 11283 --- [thread] logger : message"
    private static final java.util.regex.Pattern LOG_PATTERN = java.util.regex.Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\S*\\s+"
                                                                                               + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                                                                                               + "\\d+\\s+---\\s+"
                                                                                               + "\\[([^]]*)]\\s+"
                                                                                               + "(\\S+)\\s*:\\s*(.*)$");

    private static LogEntry parseLogLine(String line) {
        LogEntry entry = new LogEntry();
        entry.raw = line;
        try {
            java.util.regex.Matcher m = LOG_PATTERN.matcher(line);
            if (m.matches()) {
                entry.time = m.group(2); // HH:mm:ss.SSS...
                // Truncate time to 12 chars (HH:mm:ss.SSS)
                if (entry.time.length() > 12) {
                    entry.time = entry.time.substring(0, 12);
                }
                entry.level = m.group(3);
                entry.logger = m.group(5);
                // Shorten logger to simple name
                int lastDot = entry.logger.lastIndexOf('.');
                if (lastDot > 0) {
                    entry.logger = entry.logger.substring(lastDot + 1);
                }
                entry.message = m.group(6);
            } else {
                entry.time = "";
                entry.level = "INFO";
                entry.message = line;
            }
        } catch (Exception e) {
            entry.time = "";
            entry.level = "INFO";
            entry.message = line;
        }
        return entry;
    }

    private boolean matchesLogLevelFilter(String level) {
        return switch (level) {
            case "ERROR", "FATAL" -> showLogError;
            case "WARN" -> showLogWarn;
            case "DEBUG" -> showLogDebug;
            case "TRACE" -> showLogTrace;
            default -> showLogInfo;
        };
    }

    // ---- Tab 6: Trace ----

    private void renderTrace(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<TraceEntry> current = traces.get();

        // Layout: trace list (50%) + detail panel (50%)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(50), Constraint.fill())
                .split(area);

        // Auto-follow: select last entry
        if (traceFollowMode && !current.isEmpty()) {
            traceTableState.select(current.size() - 1);
        }

        // Trace list
        List<Row> rows = new ArrayList<>();
        for (TraceEntry entry : current) {
            String status = entry.status != null ? entry.status : "";
            Style statusStyle = switch (status) {
                case "Done" -> Style.create().fg(Color.GREEN);
                case "Failed" -> Style.create().fg(Color.RED);
                case "Processing" -> Style.create().fg(Color.YELLOW);
                default -> Style.create().fg(Color.WHITE);
            };

            String bodyPreview = entry.bodyPreview != null ? truncate(entry.bodyPreview, 40) : "";

            rows.add(Row.from(
                    Cell.from(entry.timestamp != null ? truncate(entry.timestamp, 12) : ""),
                    Cell.from(entry.pid != null ? entry.pid : ""),
                    Cell.from(Span.styled(
                            entry.routeId != null ? truncate(entry.routeId, 15) : "",
                            Style.create().fg(Color.CYAN))),
                    Cell.from(entry.nodeId != null ? truncate(entry.nodeId, 15) : ""),
                    Cell.from(Span.styled(status, statusStyle)),
                    Cell.from(entry.elapsed + "ms"),
                    Cell.from(bodyPreview)));
        }

        Row header = Row.from(
                Cell.from(Span.styled("TIME", Style.create().bold())),
                Cell.from(Span.styled("PID", Style.create().bold())),
                Cell.from(Span.styled("ROUTE", Style.create().bold())),
                Cell.from(Span.styled("NODE", Style.create().bold())),
                Cell.from(Span.styled("STATUS", Style.create().bold())),
                Cell.from(Span.styled("ELAPSED", Style.create().bold())),
                Cell.from(Span.styled("BODY", Style.create().bold())));

        String traceTitle = String.format(" Traces [%d] %s ",
                current.size(),
                traceFollowMode ? "[FOLLOW]" : "[SCROLL]");

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(12),
                        Constraint.length(8),
                        Constraint.length(15),
                        Constraint.length(15),
                        Constraint.length(12),
                        Constraint.length(10),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(traceTitle).build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), traceTableState);

        // Detail panel
        renderTraceDetail(frame, chunks.get(1), current);
    }

    private void renderTraceDetail(Frame frame, Rect area, List<TraceEntry> current) {
        Integer sel = traceTableState.selected();

        if (sel == null || sel < 0 || sel >= current.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a trace entry to view details",
                                            Style.create().dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        TraceEntry entry = current.get(sel);
        List<Line> lines = new ArrayList<>();

        // Exchange info
        lines.add(Line.from(
                Span.styled(" Exchange: ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.exchangeId != null ? entry.exchangeId : "")));
        lines.add(Line.from(
                Span.styled(" UID:      ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.uid != null ? entry.uid : "")));
        lines.add(Line.from(
                Span.styled(" Location: ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.location != null ? entry.location : "")));
        lines.add(Line.from(
                Span.styled(" Route:    ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.routeId != null ? entry.routeId : ""),
                Span.raw("  Node: "),
                Span.raw(entry.nodeId != null ? entry.nodeId : ""),
                Span.raw(entry.nodeLabel != null ? " (" + entry.nodeLabel + ")" : "")));
        lines.add(Line.from(
                Span.styled(" Status:   ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.status != null ? entry.status : ""),
                Span.raw("  Elapsed: "),
                Span.raw(entry.elapsed + "ms")));
        lines.add(Line.from(Span.raw("")));

        // Headers
        if (showTraceHeaders && entry.headers != null && !entry.headers.isEmpty()) {
            lines.add(Line.from(Span.styled(" Headers:", Style.create().fg(Color.GREEN).bold())));
            for (Map.Entry<String, Object> h : entry.headers.entrySet()) {
                lines.add(Line.from(
                        Span.styled("   " + h.getKey(), Style.create().fg(Color.CYAN)),
                        Span.raw(" = "),
                        Span.raw(h.getValue() != null ? h.getValue().toString() : "null")));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Body
        if (showTraceBody && entry.body != null) {
            lines.add(Line.from(Span.styled(" Body:", Style.create().fg(Color.GREEN).bold())));
            String[] bodyLines = entry.body.split("\n");
            for (String bl : bodyLines) {
                lines.add(Line.from(Span.raw("   " + bl)));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Exchange properties
        if (entry.exchangeProperties != null && !entry.exchangeProperties.isEmpty()) {
            lines.add(Line.from(Span.styled(" Exchange Properties:", Style.create().fg(Color.GREEN).bold())));
            for (Map.Entry<String, Object> p : entry.exchangeProperties.entrySet()) {
                lines.add(Line.from(
                        Span.styled("   " + p.getKey(), Style.create().fg(Color.CYAN)),
                        Span.raw(" = "),
                        Span.raw(p.getValue() != null ? p.getValue().toString() : "null")));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Exchange variables
        if (entry.exchangeVariables != null && !entry.exchangeVariables.isEmpty()) {
            lines.add(Line.from(Span.styled(" Exchange Variables:", Style.create().fg(Color.GREEN).bold())));
            for (Map.Entry<String, Object> v : entry.exchangeVariables.entrySet()) {
                lines.add(Line.from(
                        Span.styled("   " + v.getKey(), Style.create().fg(Color.CYAN)),
                        Span.raw(" = "),
                        Span.raw(v.getValue() != null ? v.getValue().toString() : "null")));
            }
        }

        String title = String.format(" Detail [%s] ", entry.exchangeId != null ? truncate(entry.exchangeId, 30) : "");

        Paragraph detail = Paragraph.builder()
                .text(Text.from(lines))
                .overflow(Overflow.CLIP)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderWidget(detail, area);
    }

    // ---- Shared rendering ----

    private void renderNoSelection(Frame frame, Rect area) {
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(Line.from(
                                Span.styled(" Select an integration from the Overview tab (press 1)",
                                        Style.create().dim()))))
                        .block(Block.builder().borderType(BorderType.ROUNDED)
                                .title(" No integration selected ").build())
                        .build(),
                area);
    }

    private void renderFooter(Frame frame, Rect area) {
        String refreshLabel = refreshInterval >= 1000
                ? (refreshInterval / 1000) + "s"
                : refreshInterval + "ms";
        Line footer;
        int tab = tabsState.selected();
        if (tab == TAB_OVERVIEW) {
            footer = Line.from(
                    Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" quit  "),
                    Span.styled("r", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" refresh  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("Enter", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" details  "),
                    Span.styled("1-6", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" tabs  "),
                    Span.styled("Refresh: " + refreshLabel, Style.create().dim()));
        } else if (tab == TAB_ROUTES) {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("s", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" sort  "),
                    Span.styled("1-6", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" tabs  "),
                    Span.styled("Refresh: " + refreshLabel, Style.create().dim()));
        } else if (tab == TAB_HEALTH) {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("d", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" toggle DOWN  "),
                    Span.styled("1-6", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" tabs  "),
                    Span.styled("Refresh: " + refreshLabel, Style.create().dim()));
        } else if (tab == TAB_LOG) {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" scroll  "),
                    Span.styled("PgUp/PgDn", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" page  "),
                    Span.styled("t/d/i/w/e", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" levels  "),
                    Span.styled("f", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" follow  "),
                    Span.styled("g/G", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" top/end"));
        } else if (tab == TAB_TRACE) {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("h", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" headers" + (showTraceHeaders ? " [on]" : " [off]") + "  "),
                    Span.styled("b", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" body" + (showTraceBody ? " [on]" : " [off]") + "  "),
                    Span.styled("f", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" follow" + (traceFollowMode ? " [on]" : " [off]")));
        } else {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("1-6", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" tabs  "),
                    Span.styled("Refresh: " + refreshLabel, Style.create().dim()));
        }

        frame.renderWidget(Paragraph.from(footer), area);
    }

    // ---- Data Loading ----

    private void refreshData() {
        lastRefresh = System.currentTimeMillis();
        try {
            List<IntegrationInfo> infos = new ArrayList<>();
            List<Long> pids = findPids(name);
            ProcessHandle.allProcesses()
                    .filter(ph -> pids.contains(ph.pid()))
                    .forEach(ph -> {
                        JsonObject root = loadStatus(ph.pid());
                        if (root != null) {
                            IntegrationInfo info = parseIntegration(ph, root);
                            if (info != null) {
                                infos.add(info);
                                // Track throughput for sparkline
                                updateThroughputHistory(info);
                            }
                        }
                    });

            // Detect disappeared integrations and start vanishing
            Set<String> livePids = infos.stream().map(i -> i.pid).collect(Collectors.toSet());
            List<IntegrationInfo> previous = data.get();
            for (IntegrationInfo prev : previous) {
                if (!prev.vanishing && !livePids.contains(prev.pid) && !vanishing.containsKey(prev.pid)) {
                    vanishing.put(prev.pid, new VanishingInfo(prev, System.currentTimeMillis()));
                }
            }

            // Expire old vanishing entries
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, VanishingInfo>> it = vanishing.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, VanishingInfo> entry = it.next();
                if (now - entry.getValue().startTime > VANISH_DURATION_MS) {
                    it.remove();
                    throughputHistory.remove(entry.getKey());
                } else if (!livePids.contains(entry.getKey())) {
                    IntegrationInfo ghost = entry.getValue().info;
                    ghost.vanishing = true;
                    ghost.vanishStart = entry.getValue().startTime;
                    infos.add(ghost);
                } else {
                    it.remove();
                }
            }

            data.set(infos);

            // Refresh log data for the selected integration
            IntegrationInfo selected = findSelectedIntegration();
            if (selected != null) {
                readLogFile(selected.pid);
                applyLogFilters();
            }

            // Refresh trace data
            refreshTraceData(pids);
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    private void updateThroughputHistory(IntegrationInfo info) {
        // Track exchangesTotal over a 1-second sliding window for stable throughput
        long currentTotal = info.exchangesTotal;
        long now = System.currentTimeMillis();

        String pid = info.pid;
        LinkedList<long[]> samples = throughputSamples.computeIfAbsent(pid, k -> new LinkedList<>());
        samples.add(new long[] { now, currentTotal });

        // Remove samples older than 1 second
        while (!samples.isEmpty() && now - samples.get(0)[0] > 1000) {
            samples.remove(0);
        }

        // Compute throughput over the window
        if (samples.size() >= 2) {
            long[] oldest = samples.get(0);
            long[] newest = samples.get(samples.size() - 1);
            long deltaExchanges = newest[1] - oldest[1];
            long deltaTimeMs = newest[0] - oldest[0];
            long tp = deltaTimeMs > 0 ? (deltaExchanges * 1000) / deltaTimeMs : 0;

            LinkedList<Long> hist = throughputHistory.computeIfAbsent(pid, k -> new LinkedList<>());
            // Only add one point per second to keep the sparkline meaningful
            Long lastTime = previousExchangesTime.get(pid);
            if (lastTime == null || now - lastTime >= 1000) {
                previousExchangesTime.put(pid, now);
                hist.add(tp);
                while (hist.size() > MAX_SPARKLINE_POINTS) {
                    hist.remove(0);
                }
            }
        }
    }

    // ---- Trace Data Loading ----

    private void refreshTraceData(List<Long> pids) {
        List<TraceEntry> allTraces = new ArrayList<>(traces.get());

        for (Long pid : pids) {
            readTraceFile(Long.toString(pid), allTraces);
        }

        // Sort by timestamp
        allTraces.sort((a, b) -> {
            if (a.timestamp == null && b.timestamp == null) {
                return 0;
            }
            if (a.timestamp == null) {
                return -1;
            }
            if (b.timestamp == null) {
                return 1;
            }
            return a.timestamp.compareTo(b.timestamp);
        });

        // Keep only last MAX_TRACES
        if (allTraces.size() > MAX_TRACES) {
            allTraces = new ArrayList<>(allTraces.subList(allTraces.size() - MAX_TRACES, allTraces.size()));
        }

        traces.set(allTraces);
    }

    @SuppressWarnings("unchecked")
    private void readTraceFile(String pid, List<TraceEntry> allTraces) {
        Path traceFile = CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
        if (!Files.exists(traceFile)) {
            return;
        }

        long lastPos = traceFilePositions.getOrDefault(pid, 0L);

        try (RandomAccessFile raf = new RandomAccessFile(traceFile.toFile(), "r")) {
            long length = raf.length();
            if (length <= lastPos) {
                return; // no new data
            }

            raf.seek(lastPos);
            // If we're resuming mid-file, skip any partial line
            if (lastPos > 0) {
                raf.readLine();
            }

            // Read remaining bytes
            long startPos = raf.getFilePointer();
            byte[] remaining = new byte[(int) (length - startPos)];
            raf.readFully(remaining);
            String content = new String(remaining, StandardCharsets.UTF_8);

            traceFilePositions.put(pid, length);

            // Each line is a JSON object: {"enabled":true,"traces":[...]}
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JsonObject json = (JsonObject) Jsoner.deserialize(line);
                    Object tracesArray = json.get("traces");
                    if (tracesArray instanceof List<?> traceList) {
                        for (Object traceObj : traceList) {
                            if (traceObj instanceof JsonObject traceJson) {
                                TraceEntry entry = parseTraceEntry(traceJson, pid);
                                if (entry != null) {
                                    allTraces.add(entry);
                                }
                            }
                        }
                    } else {
                        // Fallback: try parsing the line itself as a trace entry
                        TraceEntry entry = parseTraceEntry(json, pid);
                        if (entry != null) {
                            allTraces.add(entry);
                        }
                    }
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    @SuppressWarnings("unchecked")
    private TraceEntry parseTraceEntry(JsonObject json, String pid) {
        TraceEntry entry = new TraceEntry();
        entry.pid = pid;
        entry.uid = stringValue(json.get("uid"));
        entry.exchangeId = json.getString("exchangeId");
        entry.routeId = json.getString("routeId");
        entry.nodeId = json.getString("nodeId");
        entry.location = json.getString("location");
        entry.nodeLabel = json.getString("nodeLabel");

        // timestamp is epoch millis (number)
        Object tsObj = json.get("timestamp");
        if (tsObj instanceof Number n) {
            long epochMs = n.longValue();
            entry.timestamp = java.time.Instant.ofEpochMilli(epochMs)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime().toString();
            // Truncate to HH:mm:ss.SSS
            if (entry.timestamp.length() > 12) {
                entry.timestamp = entry.timestamp.substring(0, 12);
            }
        } else if (tsObj != null) {
            entry.timestamp = tsObj.toString();
        }

        // Derive status from done/failed booleans
        Boolean done = (Boolean) json.get("done");
        Boolean failed = (Boolean) json.get("failed");
        if (Boolean.TRUE.equals(failed)) {
            entry.status = "Failed";
        } else if (Boolean.TRUE.equals(done)) {
            entry.status = "Done";
        } else {
            entry.status = "Processing";
        }

        Object elapsedObj = json.get("elapsed");
        if (elapsedObj instanceof Number n) {
            entry.elapsed = n.longValue();
        } else if (elapsedObj != null) {
            try {
                entry.elapsed = Long.parseLong(elapsedObj.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // Parse message object
        Object msgObj = json.get("message");
        if (msgObj instanceof JsonObject message) {
            // Headers: can be a list of {key, type, value} or a map
            Object headersObj = message.get("headers");
            if (headersObj instanceof List<?> headerList) {
                entry.headers = new LinkedHashMap<>();
                for (Object h : headerList) {
                    if (h instanceof JsonObject hObj) {
                        entry.headers.put(
                                String.valueOf(hObj.get("key")),
                                hObj.get("value"));
                    }
                }
            } else if (headersObj instanceof Map) {
                entry.headers = new LinkedHashMap<>((Map<String, Object>) headersObj);
            }

            // Body: can be {type, value} or a plain string
            Object bodyObj = message.get("body");
            if (bodyObj instanceof JsonObject bodyJson) {
                Object val = bodyJson.get("value");
                entry.body = val != null ? val.toString() : bodyJson.toString();
            } else if (bodyObj != null) {
                entry.body = bodyObj.toString();
            }
            if (entry.body != null) {
                entry.bodyPreview = entry.body.replace("\n", " ").replace("\r", "");
            }

            // Exchange properties: can be a list of {key, type, value} or a map
            Object propsObj = message.get("exchangeProperties");
            if (propsObj instanceof List<?> propList) {
                entry.exchangeProperties = new LinkedHashMap<>();
                for (Object p : propList) {
                    if (p instanceof JsonObject pObj) {
                        entry.exchangeProperties.put(
                                String.valueOf(pObj.get("key")),
                                pObj.get("value"));
                    }
                }
            } else if (propsObj instanceof Map) {
                entry.exchangeProperties = new LinkedHashMap<>((Map<String, Object>) propsObj);
            }

            // Exchange variables: can be a list of {key, type, value} or a map
            Object varsObj = message.get("exchangeVariables");
            if (varsObj instanceof List<?> varList) {
                entry.exchangeVariables = new LinkedHashMap<>();
                for (Object v : varList) {
                    if (v instanceof JsonObject vObj) {
                        entry.exchangeVariables.put(
                                String.valueOf(vObj.get("key")),
                                vObj.get("value"));
                    }
                }
            } else if (varsObj instanceof Map) {
                entry.exchangeVariables = new LinkedHashMap<>((Map<String, Object>) varsObj);
            }
        }

        return entry;
    }

    private static String stringValue(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    // ---- Integration Parsing ----

    @SuppressWarnings("unchecked")
    private IntegrationInfo parseIntegration(ProcessHandle ph, JsonObject root) {
        JsonObject context = (JsonObject) root.get("context");
        if (context == null) {
            return null;
        }

        IntegrationInfo info = new IntegrationInfo();
        info.name = context.getString("name");
        if ("CamelJBang".equals(info.name)) {
            info.name = ProcessHelper.extractName(root, ph);
        }
        info.pid = Long.toString(ph.pid());
        info.uptime = extractSince(ph);
        info.ago = TimeUtils.printSince(info.uptime);
        info.state = context.getIntegerOrDefault("phase", 0);

        JsonObject runtime = (JsonObject) root.get("runtime");
        info.platform = runtime != null ? runtime.getString("platform") : null;

        Map<String, ?> stats = context.getMap("statistics");
        if (stats != null) {
            Object thp = stats.get("exchangesThroughput");
            if (thp != null) {
                info.throughput = thp.toString();
            }
            info.exchangesTotal = objToLong(stats.get("exchangesTotal"));
        }

        JsonObject mem = (JsonObject) root.get("memory");
        if (mem != null) {
            info.heapMemUsed = mem.getLongOrDefault("heapMemoryUsed", 0L);
            info.heapMemMax = mem.getLongOrDefault("heapMemoryMax", 0L);
        }

        JsonObject threads = (JsonObject) root.get("threads");
        if (threads != null) {
            info.threadCount = threads.getIntegerOrDefault("threadCount", 0);
            info.peakThreadCount = threads.getIntegerOrDefault("peakThreadCount", 0);
        }

        // Parse routes
        JsonArray routes = (JsonArray) root.get("routes");
        if (routes != null) {
            for (Object r : routes) {
                JsonObject rj = (JsonObject) r;
                RouteInfo ri = new RouteInfo();
                ri.routeId = rj.getString("routeId");
                ri.from = rj.getString("from");
                ri.state = rj.getString("state");
                ri.uptime = rj.getString("uptime");

                Map<String, ?> rs = rj.getMap("statistics");
                if (rs != null) {
                    ri.throughput = objToString(rs.get("exchangesThroughput"));
                    ri.total = objToLong(rs.get("exchangesTotal"));
                    ri.failed = objToLong(rs.get("exchangesFailed"));
                    ri.meanTime = objToLong(rs.get("meanProcessingTime"));
                    ri.maxTime = objToLong(rs.get("maxProcessingTime"));
                }

                // Parse processors
                JsonArray procs = (JsonArray) rj.get("processors");
                if (procs != null) {
                    for (Object p : procs) {
                        JsonObject pj = (JsonObject) p;
                        ProcessorInfo pi = new ProcessorInfo();
                        pi.id = pj.getString("id");
                        pi.processor = pj.getString("processor");
                        pi.level = pj.getIntegerOrDefault("level", 0);

                        Map<String, ?> ps = pj.getMap("statistics");
                        if (ps != null) {
                            pi.total = objToLong(ps.get("exchangesTotal"));
                            pi.failed = objToLong(ps.get("exchangesFailed"));
                            pi.meanTime = objToLong(ps.get("meanProcessingTime"));
                            pi.maxTime = objToLong(ps.get("maxProcessingTime"));
                            pi.lastTime = objToLong(ps.get("lastProcessingTime"));
                        }

                        ri.processors.add(pi);
                    }
                }

                info.routes.add(ri);
            }
        }

        // Parse health checks
        JsonObject healthChecks = (JsonObject) root.get("healthChecks");
        if (healthChecks != null) {
            JsonArray checks = (JsonArray) healthChecks.get("checks");
            if (checks != null) {
                for (Object c : checks) {
                    JsonObject cj = (JsonObject) c;
                    HealthCheckInfo hc = new HealthCheckInfo();
                    hc.group = cj.getString("group");
                    hc.name = cj.getString("id");
                    hc.state = cj.getString("state");
                    hc.readiness = cj.getBooleanOrDefault("readiness", false);
                    hc.liveness = cj.getBooleanOrDefault("liveness", false);
                    // Extract message from details if available
                    JsonObject details = (JsonObject) cj.get("details");
                    if (details != null && details.containsKey("failure.error.message")) {
                        hc.message = details.getString("failure.error.message");
                    }
                    info.healthChecks.add(hc);
                }
            }
        }

        // Parse endpoints (top-level "endpoints" is a JsonObject with nested "endpoints" array)
        JsonObject endpointsObj = (JsonObject) root.get("endpoints");
        if (endpointsObj != null) {
            JsonArray endpointList = (JsonArray) endpointsObj.get("endpoints");
            if (endpointList != null) {
                for (Object e : endpointList) {
                    JsonObject ej = (JsonObject) e;
                    EndpointInfo ep = new EndpointInfo();
                    ep.uri = ej.getString("uri");
                    ep.direction = ej.getString("direction");
                    ep.routeId = ej.getString("routeId");
                    // Extract component from URI (e.g., "timer://tick" -> "timer")
                    if (ep.uri != null) {
                        int idx = ep.uri.indexOf(':');
                        ep.component = idx > 0 ? ep.uri.substring(0, idx) : ep.uri;
                    }
                    info.endpoints.add(ep);
                }
            }
        }

        return info;
    }

    // ---- Helpers ----

    private IntegrationInfo findSelectedIntegration() {
        if (selectedPid == null) {
            return null;
        }
        return data.get().stream()
                .filter(i -> selectedPid.equals(i.pid) && !i.vanishing)
                .findFirst().orElse(null);
    }

    private String selectedName() {
        IntegrationInfo info = findSelectedIntegration();
        return info != null ? truncate(info.name, 20) : "?";
    }

    private List<Long> findPids(String name) {
        return TuiHelper.findPids(name, this::getStatusFile);
    }

    private JsonObject loadStatus(long pid) {
        return TuiHelper.loadStatus(pid, this::getStatusFile);
    }

    private static long extractSince(ProcessHandle ph) {
        return ph.info().startInstant().map(Instant::toEpochMilli).orElse(0L);
    }

    private static String truncate(String s, int max) {
        return TuiHelper.truncate(s, max);
    }

    private static String formatMemory(long used, long max) {
        if (used <= 0) {
            return "";
        }
        String u = formatBytes(used);
        if (max > 0) {
            return u + "/" + formatBytes(max);
        }
        return u;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "K";
        }
        return (bytes / (1024 * 1024)) + "M";
    }

    private static String formatThreads(int count, int peak) {
        if (count <= 0) {
            return "";
        }
        return count + "/" + peak;
    }

    private static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }

    private static long objToLong(Object o) {
        return TuiHelper.objToLong(o);
    }

    // ---- Data Classes ----

    static class IntegrationInfo {
        String pid;
        String name;
        String platform;
        int state;
        long uptime;
        String ago;
        String throughput;
        long exchangesTotal;
        long heapMemUsed;
        long heapMemMax;
        int threadCount;
        int peakThreadCount;
        boolean vanishing;
        long vanishStart;
        final List<RouteInfo> routes = new ArrayList<>();
        final List<HealthCheckInfo> healthChecks = new ArrayList<>();
        final List<EndpointInfo> endpoints = new ArrayList<>();
    }

    static class RouteInfo {
        String routeId;
        String from;
        String state;
        String uptime;
        String throughput;
        long total;
        long failed;
        long meanTime;
        long maxTime;
        final List<ProcessorInfo> processors = new ArrayList<>();
    }

    static class ProcessorInfo {
        String id;
        String processor;
        int level;
        long total;
        long failed;
        long meanTime;
        long maxTime;
        long lastTime;
    }

    static class HealthCheckInfo {
        String group;
        String name;
        String state;
        boolean readiness;
        boolean liveness;
        String message;
    }

    static class EndpointInfo {
        String uri;
        String component;
        String direction;
        String routeId;
    }

    static class TraceEntry {
        String pid;
        String uid;
        String exchangeId;
        String timestamp;
        String routeId;
        String nodeId;
        String nodeLabel;
        String location;
        String status;
        long elapsed;
        String body;
        String bodyPreview;
        Map<String, Object> headers;
        Map<String, Object> exchangeProperties;
        Map<String, Object> exchangeVariables;
    }

    static class LogEntry {
        String raw;
        String time = "";
        String level = "INFO";
        String logger;
        String message = "";
    }

    record VanishingInfo(IntegrationInfo info, long startTime) {
    }
}
