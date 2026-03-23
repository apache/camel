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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "monitor",
         description = "Live TUI dashboard for monitoring Camel integrations",
         sortOptions = false)
public class CamelMonitor extends CamelCommand {

    private static final long VANISH_DURATION_MS = 6000;
    private static final long DEFAULT_REFRESH_MS = 100;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final int MAX_LOG_LINES = 200;

    // Tab indices
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_ROUTES = 1;
    private static final int TAB_HEALTH = 2;
    private static final int TAB_ENDPOINTS = 3;
    private static final int TAB_LOG = 4;

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

    // Log state
    private final List<String> logLines = new ArrayList<>();
    private int logScroll;

    // Selected integration for detail views
    private String selectedPid;

    private volatile long lastRefresh;

    public CamelMonitor(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Eagerly load classes used by the input reader thread and picocli
        // post-processing to avoid ClassNotFoundException during shutdown
        // when the Spring Boot LaunchedClassLoader may already be closing
        try {
            Class.forName("dev.tamboui.tui.event.KeyModifiers");
            Class.forName("dev.tamboui.tui.event.KeyEvent");
            Class.forName("dev.tamboui.tui.event.KeyCode");
            Class.forName("picocli.CommandLine$IExitCodeGenerator");
        } catch (ClassNotFoundException e) {
            // ignore
        }

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
                tabsState.select(TAB_OVERVIEW);
                selectedPid = null;
                return true;
            }
            if (ke.isChar('2')) {
                selectCurrentIntegration();
                tabsState.select(TAB_ROUTES);
                return true;
            }
            if (ke.isChar('3')) {
                selectCurrentIntegration();
                tabsState.select(TAB_HEALTH);
                return true;
            }
            if (ke.isChar('4')) {
                selectCurrentIntegration();
                tabsState.select(TAB_ENDPOINTS);
                return true;
            }
            if (ke.isChar('5')) {
                selectCurrentIntegration();
                tabsState.select(TAB_LOG);
                logScroll = 0;
                return true;
            }

            // Tab cycling
            if (ke.isKey(KeyCode.TAB)) {
                int next = (tabsState.selected() + 1) % 5;
                if (next != TAB_OVERVIEW) {
                    selectCurrentIntegration();
                }
                tabsState.select(next);
                if (next == TAB_LOG) {
                    logScroll = 0;
                }
                return true;
            }

            // Navigation
            if (ke.isUp()) {
                navigateUp();
                return true;
            }
            if (ke.isDown()) {
                navigateDown();
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_UP)) {
                if (tabsState.selected() == TAB_LOG) {
                    logScroll = Math.max(0, logScroll - 20);
                }
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_DOWN)) {
                if (tabsState.selected() == TAB_LOG) {
                    logScroll += 20;
                }
                return true;
            }

            // Enter to drill into selected integration
            if (ke.isKey(KeyCode.ENTER) && tabsState.selected() == TAB_OVERVIEW) {
                selectCurrentIntegration();
                if (selectedPid != null) {
                    tabsState.select(TAB_ROUTES);
                }
                return true;
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
            case TAB_LOG -> logScroll = Math.max(0, logScroll - 1);
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
                healthTableState.selectNext(info != null ? info.healthChecks.size() : 0);
            }
            case TAB_ENDPOINTS -> {
                IntegrationInfo info = findSelectedIntegration();
                endpointTableState.selectNext(info != null ? info.endpoints.size() : 0);
            }
            case TAB_LOG -> logScroll++;
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
                        " 5 Log" + sel + " ")
                .highlightStyle(Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold())
                .divider(Span.styled(" | ", Style.create().dim()))
                .build();

        frame.renderStatefulWidget(tabs, area, tabsState);
    }

    private void renderContent(Frame frame, Rect area) {
        // Clear the content area to prevent artifacts when switching tabs
        frame.buffer().clear(area);
        switch (tabsState.selected()) {
            case TAB_OVERVIEW -> renderOverview(frame, area);
            case TAB_ROUTES -> renderRoutes(frame, area);
            case TAB_HEALTH -> renderHealth(frame, area);
            case TAB_ENDPOINTS -> renderEndpoints(frame, area);
            case TAB_LOG -> renderLog(frame, area);
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
                        Cell.from(Span.styled(truncate(info.name, 25), dimStyle)),
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
                        Cell.from(Span.styled(truncate(info.name, 25), Style.create().fg(Color.CYAN))),
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

        // Split: routes table (top half) + processors table (bottom half)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(45), Constraint.percentage(55))
                .split(area);

        // Routes table
        List<Row> routeRows = new ArrayList<>();
        for (RouteInfo route : info.routes) {
            Style stateStyle = "Started".equals(route.state)
                    ? Style.create().fg(Color.GREEN)
                    : Style.create().fg(Color.RED);

            routeRows.add(Row.from(
                    Cell.from(Span.styled(truncate(route.routeId, 12), Style.create().fg(Color.CYAN))),
                    Cell.from(truncate(route.from, 30)),
                    Cell.from(Span.styled(route.state, stateStyle)),
                    Cell.from(route.uptime != null ? route.uptime : ""),
                    Cell.from(route.throughput != null ? route.throughput : ""),
                    Cell.from(String.valueOf(route.total)),
                    Cell.from(route.failed > 0
                            ? Span.styled(String.valueOf(route.failed), Style.create().fg(Color.RED))
                            : Span.raw("0")),
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
                        Cell.from(Span.styled("TOTAL", Style.create().bold())),
                        Cell.from(Span.styled("FAILED", Style.create().bold())),
                        Cell.from(Span.styled("MEAN/MAX", Style.create().bold()))))
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
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Routes [" + info.name + "] ").build())
                .build();

        frame.renderStatefulWidget(routeTable, chunks.get(0), routeTableState);

        // Processors for selected route
        Integer selectedRoute = routeTableState.selected();
        if (selectedRoute != null && selectedRoute >= 0 && selectedRoute < info.routes.size()) {
            RouteInfo route = info.routes.get(selectedRoute);
            renderProcessors(frame, chunks.get(1), route);
        } else if (!info.routes.isEmpty()) {
            renderProcessors(frame, chunks.get(1), info.routes.get(0));
        } else {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("No routes", Style.create().dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Processors ").build())
                            .build(),
                    chunks.get(1));
        }
    }

    private void renderProcessors(Frame frame, Rect area, RouteInfo route) {
        List<Row> rows = new ArrayList<>();
        for (ProcessorInfo proc : route.processors) {
            String indent = "  ".repeat(proc.level);
            Style nameStyle = proc.failed > 0 ? Style.create().fg(Color.RED) : Style.create().fg(Color.CYAN);

            rows.add(Row.from(
                    Cell.from(Span.styled(indent + proc.id, nameStyle)),
                    Cell.from(proc.processor),
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

        List<Row> rows = new ArrayList<>();
        for (HealthCheckInfo hc : info.healthChecks) {
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

            rows.add(Row.from(
                    Cell.from(Span.styled(truncate(hc.group != null ? hc.group : "", 12), Style.create().dim())),
                    Cell.from(Span.styled(truncate(hc.name, 30), Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(icon + hc.state, stateStyle)),
                    Cell.from(hc.message != null ? truncate(hc.message, 50) : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("No health checks registered", Style.create().dim())),
                    Cell.from(""),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("GROUP", Style.create().bold())),
                        Cell.from(Span.styled("NAME", Style.create().bold())),
                        Cell.from(Span.styled("STATUS", Style.create().bold())),
                        Cell.from(Span.styled("MESSAGE", Style.create().bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(30),
                        Constraint.length(12),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Health [" + info.name + "] ").build())
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

    // ---- Tab 4: Endpoints ----

    private void renderEndpoints(Frame frame, Rect area) {
        IntegrationInfo info = findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (EndpointInfo ep : info.endpoints) {
            Style dirStyle = switch (ep.direction) {
                case "in" -> Style.create().fg(Color.GREEN);
                case "out" -> Style.create().fg(Color.BLUE);
                default -> Style.create().fg(Color.YELLOW);
            };
            String arrow = switch (ep.direction) {
                case "in" -> "\u2192 ";
                case "out" -> "\u2190 ";
                default -> "\u2194 ";
            };

            rows.add(Row.from(
                    Cell.from(Span.styled(ep.component, Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(arrow + ep.direction, dirStyle)),
                    Cell.from(truncate(ep.uri, 60)),
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
                        Constraint.length(12))
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
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

        // Read log lines
        readLogFile(info.pid);

        Block logBlock = Block.builder().borderType(BorderType.ROUNDED)
                .title(" Log [" + info.name + "] ")
                .build();

        int innerHeight = Math.max(1, area.height() - 2); // account for border
        int totalLines = logLines.size();

        // Auto-scroll to bottom if logScroll is 0 (default)
        int startLine;
        if (logScroll == 0) {
            startLine = Math.max(0, totalLines - innerHeight);
        } else {
            startLine = Math.max(0, Math.min(logScroll, totalLines - innerHeight));
        }

        List<Line> visibleLines = new ArrayList<>();
        for (int i = startLine; i < Math.min(startLine + innerHeight, totalLines); i++) {
            visibleLines.add(colorizeLogLine(logLines.get(i)));
        }

        // Fill remaining space
        while (visibleLines.size() < innerHeight) {
            visibleLines.add(Line.from(Span.raw("")));
        }

        Paragraph logParagraph = Paragraph.builder()
                .text(Text.from(visibleLines))
                .overflow(Overflow.CLIP)
                .block(logBlock)
                .build();

        frame.renderWidget(logParagraph, area);
    }

    private Line colorizeLogLine(String line) {
        if (line.contains(" ERROR ") || line.contains(" FATAL ")) {
            return Line.from(Span.styled(line, Style.create().fg(Color.RED)));
        } else if (line.contains(" WARN ")) {
            return Line.from(Span.styled(line, Style.create().fg(Color.YELLOW)));
        } else if (line.contains(" DEBUG ") || line.contains(" TRACE ")) {
            return Line.from(Span.styled(line, Style.create().dim()));
        }
        return Line.from(Span.raw(line));
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
            String content = new String(remaining, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            int start = Math.max(0, lines.length - MAX_LOG_LINES);
            for (int i = start; i < lines.length; i++) {
                String line = lines[i];
                if (!line.isEmpty()) {
                    logLines.add(line);
                }
            }
        } catch (IOException e) {
            // ignore
        }
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
        if (tabsState.selected() == TAB_OVERVIEW) {
            footer = Line.from(
                    Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" quit  "),
                    Span.styled("r", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" refresh  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("Enter", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" details  "),
                    Span.styled("1-5", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" tabs  "),
                    Span.styled("Refresh: " + refreshLabel, Style.create().dim()));
        } else if (tabsState.selected() == TAB_LOG) {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" scroll  "),
                    Span.styled("PgUp/PgDn", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" page  "),
                    Span.styled("1-5", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" tabs  "),
                    Span.styled("Refresh: " + refreshLabel, Style.create().dim()));
        } else {
            footer = Line.from(
                    Span.styled(" Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" back  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("1-5", Style.create().fg(Color.YELLOW).bold()),
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
        info.state = context.getInteger("phase");

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
            info.heapMemUsed = mem.getLong("heapMemoryUsed");
            info.heapMemMax = mem.getLong("heapMemoryMax");
        }

        JsonObject threads = (JsonObject) root.get("threads");
        if (threads != null) {
            info.threadCount = threads.getInteger("threadCount");
            info.peakThreadCount = threads.getInteger("peakThreadCount");
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
        List<Long> pids = new ArrayList<>();
        final long cur = ProcessHandle.current().pid();
        String pattern = name;
        if (!pattern.matches("\\d+") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        final String pat = pattern;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                            pids.add(ph.pid());
                        } else {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });
        return pids;
    }

    private JsonObject loadStatus(long pid) {
        try {
            Path f = getStatusFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static long extractSince(ProcessHandle ph) {
        return ph.info().startInstant().map(Instant::toEpochMilli).orElse(0L);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
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
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
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
        String message;
    }

    static class EndpointInfo {
        String uri;
        String component;
        String direction;
        String routeId;
    }

    record VanishingInfo(IntegrationInfo info, long startTime) {
    }
}
