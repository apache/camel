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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class JfrTab extends AbstractTab {

    private static final Style LABEL = Theme.muted();
    private static final Style VALUE = Style.EMPTY.fg(Theme.baseFg()).bold();

    enum View {
        ROUTES("Routes"),
        PROCESSORS("Processors"),
        ENDPOINTS("Endpoints"),
        FAILURES("Failures"),
        REDELIVERIES("Redeliveries");

        final String label;

        View(String label) {
            this.label = label;
        }
    }

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final Consumer<Runnable> renderThreadExecutor;

    // status state
    private boolean registered;
    private List<String> recordings = List.of();
    private String errorMessage;
    private boolean statusLoaded;

    // snapshot data
    private List<RouteStats> routeData = List.of();
    private List<ProcessorStats> processorData = List.of();
    private List<EndpointStats> endpointData = List.of();
    private List<FailureEntry> failureData = List.of();
    private List<RedeliveryEntry> redeliveryData = List.of();
    private int snapshotEventCount;
    private long snapshotTime;
    private boolean snapshotLoaded;

    // view state
    private View activeView = View.ROUTES;
    private final TableState tableState = new TableState();
    private final ScrollbarState scrollState = new ScrollbarState();
    private final TableState processorTableState = new TableState();
    private final ScrollbarState processorScrollState = new ScrollbarState();
    private Rect lastTableArea;
    private int topPanelHeight = -1;

    // sort state per view
    private static final String[] ROUTE_SORT_COLUMNS = { "route", "total", "failed", "min", "mean", "max" };
    private static final String[] PROCESSOR_SORT_COLUMNS = { "processor", "total", "failed", "min", "mean", "max" };
    private static final String[] ENDPOINT_SORT_COLUMNS = { "endpoint", "total", "failed", "min", "mean", "max" };
    private String routeSort = "route";
    private int routeSortIndex;
    private boolean routeSortReversed;
    private String processorSort = "processor";
    private int processorSortIndex;
    private boolean processorSortReversed;
    private String endpointSort = "endpoint";
    private int endpointSortIndex;
    private boolean endpointSortReversed;

    record RouteStats(String routeId, long total, long failed, double minMs, double meanMs, double maxMs) {
    }

    record ProcessorStats(String processorId, String processorType, String routeId,
            long total, long failed, double minMs, double meanMs, double maxMs) {
    }

    record EndpointStats(String endpointUri, long total, long failed, double minMs, double meanMs, double maxMs) {
    }

    record FailureEntry(String timestamp, String exchangeId, String routeId,
            String exceptionType, String exceptionMessage) {
    }

    record RedeliveryEntry(String timestamp, String exchangeId, String routeId, int attempt, int maxAttempts) {
    }

    JfrTab(MonitorContext ctx) {
        this(ctx, action -> {
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(action);
            }
        });
    }

    JfrTab(MonitorContext ctx, Consumer<Runnable> renderThreadExecutor) {
        super(ctx);
        this.renderThreadExecutor = renderThreadExecutor;
    }

    @Override
    public void onTabSelected() {
        if (!statusLoaded) {
            refreshStatus();
        }
    }

    @Override
    public void onIntegrationChanged() {
        registered = false;
        recordings = List.of();
        errorMessage = null;
        statusLoaded = false;
        clearSnapshotData();
    }

    private void clearSnapshotData() {
        routeData = List.of();
        processorData = List.of();
        endpointData = List.of();
        failureData = List.of();
        redeliveryData = List.of();
        snapshotEventCount = 0;
        snapshotLoaded = false;
        topPanelHeight = -1;
        tableState.select(0);
        processorTableState.select(0);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.F5)) {
            takeSnapshot();
            return true;
        }
        if (ke.isChar(' ')) {
            View[] views = View.values();
            switchView(views[(activeView.ordinal() + 1) % views.length]);
            return true;
        }

        if (ke.isChar('s')) {
            cycleSortForward();
            return true;
        }
        if (ke.isChar('S')) {
            cycleSortReverse();
            return true;
        }

        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            TableState ts = activeTableState();
            for (int i = 0; i < 20 && ts.selected() != null && ts.selected() > 0; i++) {
                ts.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            TableState ts = activeTableState();
            int count = getRowCount();
            for (int i = 0; i < 20; i++) {
                ts.selectNext(count);
            }
            return true;
        }
        if (ke.isHome()) {
            activeTableState().selectFirst();
            return true;
        }
        if (ke.isEnd()) {
            activeTableState().selectLast(getRowCount());
            return true;
        }

        return false;
    }

    @Override
    public void navigateUp() {
        activeTableState().selectPrevious();
    }

    @Override
    public void navigateDown() {
        activeTableState().selectNext(getRowCount());
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return handleTableClick(me, lastTableArea, activeTableState(), getRowCount());
    }

    private TableState activeTableState() {
        return tableState;
    }

    private void switchView(View view) {
        if (view != activeView) {
            activeView = view;
            tableState.select(0);
        }
    }

    private int getRowCount() {
        return switch (activeView) {
            case ROUTES -> routeData.size();
            case PROCESSORS -> processorData.size();
            case ENDPOINTS -> endpointData.size();
            case FAILURES -> failureData.size();
            case REDELIVERIES -> redeliveryData.size();
        };
    }

    // ---- rendering ----

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(4), Constraint.fill())
                .split(area);

        renderStatusHeader(frame, rows.get(0));
        renderDataTable(frame, rows.get(1));
    }

    private void renderStatusHeader(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();

        List<Span> line1 = new ArrayList<>();
        if (!statusLoaded) {
            line1.add(Span.styled("Loading...", LABEL));
        } else {
            line1.add(Span.styled("Runtime Events: ", LABEL));
            line1.add(Span.styled(registered ? "Enabled" : "Disabled", registered ? VALUE : Theme.error()));

            if (!recordings.isEmpty()) {
                line1.add(Span.styled("  Recording: ", LABEL));
                line1.add(Span.styled(recordings.get(0), Theme.success()));
            } else {
                line1.add(Span.styled("  No Active Recording", LABEL));
            }
        }

        if (snapshotLoaded) {
            line1.add(Span.styled("  Snapshot: ", LABEL));
            String ageLabel = "";
            if (snapshotTime > 0) {
                long agoSec = (System.currentTimeMillis() - snapshotTime) / 1000;
                if (agoSec >= 60) {
                    ageLabel = " (" + (agoSec / 60) + "m ago)";
                } else if (agoSec >= 5) {
                    ageLabel = " (" + agoSec + "s ago)";
                }
            }
            line1.add(Span.styled(snapshotEventCount + " events" + ageLabel, VALUE));
        }
        lines.add(Line.from(line1));

        List<Span> line2 = new ArrayList<>();
        for (View v : View.values()) {
            if (v.ordinal() > 0) {
                line2.add(Span.styled(" | ", LABEL));
            }
            boolean active = v == activeView;
            line2.add(Span.styled(v.label, active ? VALUE : LABEL));
        }
        if (errorMessage != null) {
            line2.add(Span.styled("  " + errorMessage, Theme.error()));
        }
        lines.add(Line.from(line2));

        frame.renderWidget(
                Paragraph.builder().text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(" JFR ").build())
                        .build(),
                area);
    }

    private void renderDataTable(Frame frame, Rect area) {
        if (!snapshotLoaded) {
            String msg = loading.get()
                    ? "  Loading..."
                    : "  Press F5 to refresh JFR snapshot data";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(msg, LABEL))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" " + activeView.label + " ").build())
                            .build(),
                    area);
            return;
        }

        switch (activeView) {
            case ROUTES -> renderRoutesTable(frame, area);
            case PROCESSORS -> renderProcessorsTable(frame, area);
            case ENDPOINTS -> renderEndpointsTable(frame, area);
            case FAILURES -> renderFailuresTable(frame, area);
            case REDELIVERIES -> renderRedeliveriesTable(frame, area);
        }
    }

    private void renderRoutesTable(Frame frame, Rect area) {
        List<RouteStats> data = sortedRoutes();

        if (topPanelHeight < 0) {
            topPanelHeight = area.height() * 45 / 100;
        }
        topPanelHeight = Math.max(3, Math.min(topPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(topPanelHeight), Constraint.fill())
                .split(area);

        long maxMean = data.stream().mapToLong(r -> (long) r.meanMs).max().orElse(1);
        if (maxMean <= 0) {
            maxMean = 1;
        }

        List<Row> rows = new ArrayList<>();
        for (RouteStats r : data) {
            Style nameStyle = r.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());
            String bar = buildBar((long) r.meanMs, maxMean, 20);
            Style barStyle = topTimeStyle((long) r.meanMs);
            if (barStyle == Style.EMPTY) {
                barStyle = Style.EMPTY.fg(Theme.accent());
            }
            rows.add(Row.from(
                    Cell.from(Span.styled(r.routeId, nameStyle)),
                    Cell.from(Span.styled(bar, barStyle)),
                    rightCell(String.valueOf(r.total), 8),
                    rightCell(String.valueOf(r.failed), 8, r.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(formatMs(r.minMs), 10),
                    rightCell(formatMs(r.meanMs), 10, topTimeStyle((long) r.meanMs)),
                    rightCell(formatMs(r.maxMs), 10, topTimeStyle((long) r.maxMs))));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(routeSortLabel("ROUTE", "route"), routeSortStyle("route"))),
                        Cell.from(""),
                        rightCell(routeSortLabel("TOTAL", "total"), 8, routeSortStyle("total")),
                        rightCell(routeSortLabel("FAILED", "failed"), 8, routeSortStyle("failed")),
                        rightCell(routeSortLabel("MIN", "min"), 10, routeSortStyle("min")),
                        rightCell(routeSortLabel("MEAN", "mean"), 10, routeSortStyle("mean")),
                        rightCell(routeSortLabel("MAX", "max"), 10, routeSortStyle("max"))))
                .widths(Constraint.length(24), Constraint.fill(),
                        Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Routes (" + data.size() + ") ").build())
                .build();
        lastTableArea = chunks.get(0);
        frame.renderStatefulWidget(table, chunks.get(0), tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, data.size());

        Integer selectedRoute = tableState.selected();
        if (selectedRoute != null && selectedRoute >= 0 && selectedRoute < data.size()) {
            RouteStats route = data.get(selectedRoute);
            renderProcessorPanel(frame, chunks.get(1), route.routeId);
        } else if (!data.isEmpty()) {
            renderProcessorPanel(frame, chunks.get(1), data.get(0).routeId);
        } else {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("No routes", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Processors ").build())
                            .build(),
                    chunks.get(1));
        }
    }

    private void renderProcessorPanel(Frame frame, Rect area, String routeId) {
        List<ProcessorStats> data = processorData.stream()
                .filter(p -> routeId.equals(p.routeId))
                .toList();
        data = sortProcessors(data);

        long maxMean = data.stream().mapToLong(p -> (long) p.meanMs).max().orElse(1);
        if (maxMean <= 0) {
            maxMean = 1;
        }

        List<Row> rows = new ArrayList<>();
        for (ProcessorStats p : data) {
            Style nameStyle = p.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());
            String bar = buildBar((long) p.meanMs, maxMean, 20);
            Style barStyle = topTimeStyle((long) p.meanMs);
            if (barStyle == Style.EMPTY) {
                barStyle = Style.EMPTY.fg(Theme.accent());
            }
            rows.add(Row.from(
                    Cell.from(Span.styled(p.processorId, nameStyle)),
                    Cell.from(Span.styled(p.processorType != null ? p.processorType : "", LABEL)),
                    Cell.from(Span.styled(bar, barStyle)),
                    rightCell(String.valueOf(p.total), 8),
                    rightCell(String.valueOf(p.failed), 8, p.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(formatMs(p.minMs), 10),
                    rightCell(formatMs(p.meanMs), 10, topTimeStyle((long) p.meanMs)),
                    rightCell(formatMs(p.maxMs), 10, topTimeStyle((long) p.maxMs))));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("PROCESSOR", Style.EMPTY.bold())),
                        Cell.from(Span.styled("TYPE", Style.EMPTY.bold())),
                        Cell.from(""),
                        rightCell("TOTAL", 8, Style.EMPTY.bold()),
                        rightCell("FAILED", 8, Style.EMPTY.bold()),
                        rightCell("MIN", 10, Style.EMPTY.bold()),
                        rightCell("MEAN", 10, Style.EMPTY.bold()),
                        rightCell("MAX", 10, Style.EMPTY.bold())))
                .widths(Constraint.fill(), Constraint.length(12), Constraint.length(22),
                        Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Processors [" + routeId + "] (" + data.size() + ") ").build())
                .build();
        frame.renderStatefulWidget(table, area, processorTableState);
        renderTableScrollbar(frame, area, processorTableState, processorScrollState, data.size());
    }

    private void renderProcessorsTable(Frame frame, Rect area) {
        List<ProcessorStats> data = sortProcessors(processorData);

        long maxMean = data.stream().mapToLong(p -> (long) p.meanMs).max().orElse(1);
        if (maxMean <= 0) {
            maxMean = 1;
        }

        List<Row> rows = new ArrayList<>();
        for (ProcessorStats p : data) {
            Style nameStyle = p.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());
            String bar = buildBar((long) p.meanMs, maxMean, 20);
            Style barStyle = topTimeStyle((long) p.meanMs);
            if (barStyle == Style.EMPTY) {
                barStyle = Style.EMPTY.fg(Theme.accent());
            }
            rows.add(Row.from(
                    Cell.from(Span.styled(p.processorId, nameStyle)),
                    Cell.from(Span.styled(p.processorType != null ? p.processorType : "", LABEL)),
                    Cell.from(Span.styled(p.routeId, LABEL)),
                    Cell.from(Span.styled(bar, barStyle)),
                    rightCell(String.valueOf(p.total), 8),
                    rightCell(String.valueOf(p.failed), 8, p.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(formatMs(p.minMs), 10),
                    rightCell(formatMs(p.meanMs), 10, topTimeStyle((long) p.meanMs)),
                    rightCell(formatMs(p.maxMs), 10, topTimeStyle((long) p.maxMs))));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(processorSortLabel("PROCESSOR", "processor"),
                                processorSortStyle("processor"))),
                        Cell.from(Span.styled("TYPE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("ROUTE", Style.EMPTY.bold())),
                        Cell.from(""),
                        rightCell(processorSortLabel("TOTAL", "total"), 8, processorSortStyle("total")),
                        rightCell(processorSortLabel("FAILED", "failed"), 8, processorSortStyle("failed")),
                        rightCell(processorSortLabel("MIN", "min"), 10, processorSortStyle("min")),
                        rightCell(processorSortLabel("MEAN", "mean"), 10, processorSortStyle("mean")),
                        rightCell(processorSortLabel("MAX", "max"), 10, processorSortStyle("max"))))
                .widths(Constraint.fill(), Constraint.length(12), Constraint.length(16),
                        Constraint.length(22),
                        Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Processors (" + data.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, data.size());
    }

    private void renderEndpointsTable(Frame frame, Rect area) {
        List<EndpointStats> data = sortedEndpoints();

        long maxMean = data.stream().mapToLong(e -> (long) e.meanMs).max().orElse(1);
        if (maxMean <= 0) {
            maxMean = 1;
        }

        List<Row> rows = new ArrayList<>();
        for (EndpointStats e : data) {
            Style nameStyle = e.failed > 0 ? Theme.error() : Style.EMPTY.fg(Theme.accent());
            String bar = buildBar((long) e.meanMs, maxMean, 20);
            Style barStyle = topTimeStyle((long) e.meanMs);
            if (barStyle == Style.EMPTY) {
                barStyle = Style.EMPTY.fg(Theme.accent());
            }
            rows.add(Row
                    .from(
                    Cell.from(Span.styled(e.endpointUri, nameStyle)),
                    Cell.from(Span.styled(bar, barStyle)),
                    rightCell(String.valueOf(e.total), 8),
                    rightCell(String.valueOf(e.failed), 8, e.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(formatMs(e.minMs), 10),
                    rightCell(formatMs(e.meanMs), 10, topTimeStyle((long) e.meanMs)),
                    rightCell(formatMs(e.maxMs), 10, topTimeStyle((long) e.maxMs))));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(endpointSortLabel("ENDPOINT", "endpoint"),
                                endpointSortStyle("endpoint"))),
                        Cell.from(""),
                        rightCell(endpointSortLabel("TOTAL", "total"), 8, endpointSortStyle("total")),
                        rightCell(endpointSortLabel("FAILED", "failed"), 8, endpointSortStyle("failed")),
                        rightCell(endpointSortLabel("MIN", "min"), 10, endpointSortStyle("min")),
                        rightCell(endpointSortLabel("MEAN", "mean"), 10, endpointSortStyle("mean")),
                        rightCell(endpointSortLabel("MAX", "max"), 10, endpointSortStyle("max"))))
                .widths(Constraint.length(30), Constraint.fill(),
                        Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Endpoints (" + data.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, data.size());
    }

    private void renderFailuresTable(Frame frame, Rect area) {
        List<Row> rows = new ArrayList<>();
        for (FailureEntry f : failureData) {
            String time = f.timestamp != null && f.timestamp.length() > 19
                    ? f.timestamp.substring(11, 19) : (f.timestamp != null ? f.timestamp : "");
            String msg = f.exceptionMessage != null
                    ? (f.exceptionMessage.length() > 60 ? f.exceptionMessage.substring(0, 60) + "..." : f.exceptionMessage)
                    : "";
            rows.add(Row.from(
                    Cell.from(Span.styled(time, LABEL)),
                    Cell.from(Span.styled(f.routeId != null ? f.routeId : "", Style.EMPTY.fg(Theme.baseFg()))),
                    Cell.from(Span.styled(
                            f.exceptionType != null ? shortClassName(f.exceptionType) : "", Theme.error())),
                    Cell.from(Span.styled(msg, LABEL))));
        }

        Style hdr = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("TIME", hdr)),
                        Cell.from(Span.styled("ROUTE", hdr)),
                        Cell.from(Span.styled("EXCEPTION", hdr)),
                        Cell.from(Span.styled("MESSAGE", hdr))))
                .widths(Constraint.length(10), Constraint.length(16),
                        Constraint.length(28), Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Failures (" + failureData.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, failureData.size());
    }

    private void renderRedeliveriesTable(Frame frame, Rect area) {
        List<Row> rows = new ArrayList<>();
        for (RedeliveryEntry r : redeliveryData) {
            String time = r.timestamp != null && r.timestamp.length() > 19
                    ? r.timestamp.substring(11, 19) : (r.timestamp != null ? r.timestamp : "");
            boolean exhausted = r.attempt >= r.maxAttempts && r.maxAttempts > 0;
            rows.add(Row.from(
                    Cell.from(Span.styled(time, LABEL)),
                    Cell.from(Span.styled(r.routeId != null ? r.routeId : "", Style.EMPTY.fg(Theme.baseFg()))),
                    rightCell(String.valueOf(r.attempt), 8),
                    rightCell(String.valueOf(r.maxAttempts), 8, exhausted ? Theme.error() : Style.EMPTY)));
        }

        Style hdr = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("TIME", hdr)),
                        Cell.from(Span.styled("ROUTE", hdr)),
                        rightCell("ATTEMPT", 8, hdr),
                        rightCell("MAX", 8, hdr)))
                .widths(Constraint.length(10), Constraint.fill(),
                        Constraint.length(8), Constraint.length(8))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Redeliveries (" + redeliveryData.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, redeliveryData.size());
    }

    private static String formatMs(double ms) {
        if (ms < 1.0) {
            return String.format(Locale.US, "%.1fms", ms);
        } else if (ms < 1000.0) {
            return String.format(Locale.US, "%.0fms", ms);
        } else {
            return String.format(Locale.US, "%.1fs", ms / 1000.0);
        }
    }

    private static String shortClassName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "Space", "view");
        if (snapshotLoaded && hasSortColumns()) {
            hint(spans, "s", "sort");
        }
        hintLast(spans, "F5", "refresh");
    }

    // ---- sorting ----

    private void cycleSortForward() {
        switch (activeView) {
            case ROUTES -> {
                routeSortIndex = (routeSortIndex + 1) % ROUTE_SORT_COLUMNS.length;
                routeSort = ROUTE_SORT_COLUMNS[routeSortIndex];
                routeSortReversed = false;
            }
            case PROCESSORS -> {
                processorSortIndex = (processorSortIndex + 1) % PROCESSOR_SORT_COLUMNS.length;
                processorSort = PROCESSOR_SORT_COLUMNS[processorSortIndex];
                processorSortReversed = false;
            }
            case ENDPOINTS -> {
                endpointSortIndex = (endpointSortIndex + 1) % ENDPOINT_SORT_COLUMNS.length;
                endpointSort = ENDPOINT_SORT_COLUMNS[endpointSortIndex];
                endpointSortReversed = false;
            }
            default -> {
            }
        }
    }

    private void cycleSortReverse() {
        switch (activeView) {
            case ROUTES -> routeSortReversed = !routeSortReversed;
            case PROCESSORS -> processorSortReversed = !processorSortReversed;
            case ENDPOINTS -> endpointSortReversed = !endpointSortReversed;
            default -> {
            }
        }
    }

    private boolean hasSortColumns() {
        return activeView == View.ROUTES || activeView == View.PROCESSORS || activeView == View.ENDPOINTS;
    }

    private String routeSortLabel(String label, String column) {
        return sortLabel(label, column, routeSort, routeSortReversed);
    }

    private Style routeSortStyle(String column) {
        return sortStyle(column, routeSort);
    }

    private String processorSortLabel(String label, String column) {
        return sortLabel(label, column, processorSort, processorSortReversed);
    }

    private Style processorSortStyle(String column) {
        return sortStyle(column, processorSort);
    }

    private String endpointSortLabel(String label, String column) {
        return sortLabel(label, column, endpointSort, endpointSortReversed);
    }

    private Style endpointSortStyle(String column) {
        return sortStyle(column, endpointSort);
    }

    private List<RouteStats> sortedRoutes() {
        if (routeData.isEmpty()) {
            return routeData;
        }
        List<RouteStats> sorted = new ArrayList<>(routeData);
        if ("route".equals(routeSort)) {
            sorted.sort(routeSortReversed
                    ? Comparator.comparing(RouteStats::routeId, String.CASE_INSENSITIVE_ORDER).reversed()
                    : Comparator.comparing(RouteStats::routeId, String.CASE_INSENSITIVE_ORDER));
        } else {
            Comparator<RouteStats> cmp = switch (routeSort) {
                case "failed" -> Comparator.comparingLong(RouteStats::failed);
                case "min" -> Comparator.comparingDouble(RouteStats::minMs);
                case "mean" -> Comparator.comparingDouble(RouteStats::meanMs);
                case "max" -> Comparator.comparingDouble(RouteStats::maxMs);
                default -> Comparator.comparingLong(RouteStats::total);
            };
            sorted.sort(routeSortReversed ? cmp : cmp.reversed());
        }
        return sorted;
    }

    private List<ProcessorStats> sortProcessors(List<ProcessorStats> input) {
        if (input.isEmpty()) {
            return input;
        }
        List<ProcessorStats> sorted = new ArrayList<>(input);
        if ("processor".equals(processorSort)) {
            sorted.sort(processorSortReversed
                    ? Comparator.comparing(ProcessorStats::processorId, String.CASE_INSENSITIVE_ORDER).reversed()
                    : Comparator.comparing(ProcessorStats::processorId, String.CASE_INSENSITIVE_ORDER));
        } else {
            Comparator<ProcessorStats> cmp = switch (processorSort) {
                case "total" -> Comparator.comparingLong(ProcessorStats::total);
                case "failed" -> Comparator.comparingLong(ProcessorStats::failed);
                case "min" -> Comparator.comparingDouble(ProcessorStats::minMs);
                case "max" -> Comparator.comparingDouble(ProcessorStats::maxMs);
                default -> Comparator.comparingDouble(ProcessorStats::meanMs);
            };
            sorted.sort(processorSortReversed ? cmp : cmp.reversed());
        }
        return sorted;
    }

    private List<EndpointStats> sortedEndpoints() {
        if (endpointData.isEmpty()) {
            return endpointData;
        }
        List<EndpointStats> sorted = new ArrayList<>(endpointData);
        if ("endpoint".equals(endpointSort)) {
            sorted.sort(endpointSortReversed
                    ? Comparator.comparing(EndpointStats::endpointUri, String.CASE_INSENSITIVE_ORDER).reversed()
                    : Comparator.comparing(EndpointStats::endpointUri, String.CASE_INSENSITIVE_ORDER));
        } else {
            Comparator<EndpointStats> cmp = switch (endpointSort) {
                case "failed" -> Comparator.comparingLong(EndpointStats::failed);
                case "min" -> Comparator.comparingDouble(EndpointStats::minMs);
                case "mean" -> Comparator.comparingDouble(EndpointStats::meanMs);
                case "max" -> Comparator.comparingDouble(EndpointStats::maxMs);
                default -> Comparator.comparingLong(EndpointStats::total);
            };
            sorted.sort(endpointSortReversed ? cmp : cmp.reversed());
        }
        return sorted;
    }

    // ---- server communication ----

    private void refreshStatus() {
        if (ctx.selectedPid == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            boolean chained = false;
            try {
                JsonObject root = new JsonObject();
                root.put("action", "jfr");
                root.put("command", "status");

                JsonObject jo = ctx.executeAction(pid, root, 5000);
                applyStatus(jo);

                if (jo != null && jo.get("recordings") instanceof JsonArray recs && !recs.isEmpty()
                        && !snapshotLoaded) {
                    chained = true;
                    doTakeSnapshot();
                    return;
                }
            } catch (Exception e) {
                applyError("Error: " + e.getMessage());
            } finally {
                if (!chained) {
                    loading.set(false);
                }
            }
        });
    }

    private void takeSnapshot() {
        if (ctx.selectedPid == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        doTakeSnapshot();
    }

    private void doTakeSnapshot() {
        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                JsonObject root = new JsonObject();
                root.put("action", "jfr");
                root.put("command", "snapshot");

                JsonObject jo = ctx.executeAction(pid, root, 30000);
                applySnapshot(jo);
            } catch (Exception e) {
                applyError("Error taking snapshot: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    // ---- apply results on render thread ----

    private void applyStatus(JsonObject jo) {
        renderThreadExecutor.accept(() -> {
            if (jo == null) {
                errorMessage = "No response from integration";
                statusLoaded = true;
                return;
            }
            String error = jo.getString("error");
            if (error != null) {
                errorMessage = error;
                statusLoaded = true;
                return;
            }
            registered = Boolean.TRUE.equals(jo.getBoolean("runtimeEvents"));
            List<String> recs = new ArrayList<>();
            if (jo.get("recordings") instanceof JsonArray recordingsArr) {
                for (Object o : recordingsArr) {
                    if (o instanceof JsonObject rec) {
                        recs.add(rec.getString("name") + " (" + rec.getString("state") + ")");
                    }
                }
            }
            recordings = recs;
            errorMessage = null;
            statusLoaded = true;
        });
    }

    private void applySnapshot(JsonObject jo) {
        renderThreadExecutor.accept(() -> {
            if (jo == null) {
                errorMessage = "No snapshot response from integration";
                return;
            }
            String error = jo.getString("error");
            if (error != null) {
                errorMessage = error;
                return;
            }

            snapshotEventCount = jo.getInteger("eventCount") != null ? jo.getInteger("eventCount") : 0;

            routeData = parseRoutes(jo);
            processorData = parseProcessors(jo);
            endpointData = parseEndpoints(jo);
            failureData = parseFailures(jo);
            redeliveryData = parseRedeliveries(jo);

            errorMessage = null;
            Long ts = jo.getLong("snapshotTimestamp");
            snapshotTime = ts != null ? ts : System.currentTimeMillis();
            snapshotLoaded = true;
            tableState.select(0);
        });
    }

    private static List<RouteStats> parseRoutes(JsonObject jo) {
        List<RouteStats> result = new ArrayList<>();
        if (jo.get("routes") instanceof JsonArray arr) {
            for (Object o : arr) {
                if (o instanceof JsonObject r) {
                    result.add(new RouteStats(
                            r.getString("routeId"),
                            longVal(r, "total"),
                            longVal(r, "failed"),
                            doubleVal(r, "minMs"),
                            doubleVal(r, "meanMs"),
                            doubleVal(r, "maxMs")));
                }
            }
        }
        return result;
    }

    private static List<ProcessorStats> parseProcessors(JsonObject jo) {
        List<ProcessorStats> result = new ArrayList<>();
        if (jo.get("processors") instanceof JsonArray arr) {
            for (Object o : arr) {
                if (o instanceof JsonObject p) {
                    result.add(new ProcessorStats(
                            p.getString("processorId"),
                            p.getString("processorType"),
                            p.getString("routeId"),
                            longVal(p, "total"),
                            longVal(p, "failed"),
                            doubleVal(p, "minMs"),
                            doubleVal(p, "meanMs"),
                            doubleVal(p, "maxMs")));
                }
            }
        }
        return result;
    }

    private static List<EndpointStats> parseEndpoints(JsonObject jo) {
        List<EndpointStats> result = new ArrayList<>();
        if (jo.get("endpoints") instanceof JsonArray arr) {
            for (Object o : arr) {
                if (o instanceof JsonObject e) {
                    result.add(new EndpointStats(
                            e.getString("endpointUri"),
                            longVal(e, "total"),
                            longVal(e, "failed"),
                            doubleVal(e, "minMs"),
                            doubleVal(e, "meanMs"),
                            doubleVal(e, "maxMs")));
                }
            }
        }
        return result;
    }

    private static List<FailureEntry> parseFailures(JsonObject jo) {
        List<FailureEntry> result = new ArrayList<>();
        if (jo.get("failures") instanceof JsonArray arr) {
            for (Object o : arr) {
                if (o instanceof JsonObject f) {
                    result.add(new FailureEntry(
                            f.getString("timestamp"),
                            f.getString("exchangeId"),
                            f.getString("routeId"),
                            f.getString("exceptionType"),
                            f.getString("exceptionMessage")));
                }
            }
        }
        return result;
    }

    private static List<RedeliveryEntry> parseRedeliveries(JsonObject jo) {
        List<RedeliveryEntry> result = new ArrayList<>();
        if (jo.get("redeliveries") instanceof JsonArray arr) {
            for (Object o : arr) {
                if (o instanceof JsonObject r) {
                    result.add(new RedeliveryEntry(
                            r.getString("timestamp"),
                            r.getString("exchangeId"),
                            r.getString("routeId"),
                            intVal(r, "attempt"),
                            intVal(r, "maxAttempts")));
                }
            }
        }
        return result;
    }

    private static long longVal(JsonObject jo, String key) {
        Object v = jo.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }

    private static double doubleVal(JsonObject jo, String key) {
        Object v = jo.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return 0;
    }

    private static int intVal(JsonObject jo, String key) {
        Object v = jo.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private void applyError(String error) {
        renderThreadExecutor.accept(() -> {
            errorMessage = error;
        });
    }

    @Override
    public SelectionContext getSelectionContext() {
        if (!snapshotLoaded) {
            return null;
        }
        int sel = tableState.selected() != null ? tableState.selected() : -1;
        return switch (activeView) {
            case ROUTES -> {
                List<RouteStats> data = sortedRoutes();
                yield sel >= 0 && sel < data.size()
                        ? new SelectionContext(
                                "route",
                                data.stream().map(RouteStats::routeId).toList(),
                                sel, data.size(), data.get(sel).routeId)
                        : null;
            }
            case PROCESSORS -> {
                List<ProcessorStats> data = sortProcessors(processorData);
                yield sel >= 0 && sel < data.size()
                        ? new SelectionContext(
                                "processor",
                                data.stream().map(ProcessorStats::processorId).toList(),
                                sel, data.size(), data.get(sel).processorId)
                        : null;
            }
            case ENDPOINTS -> {
                List<EndpointStats> data = sortedEndpoints();
                yield sel >= 0 && sel < data.size()
                        ? new SelectionContext(
                                "endpoint",
                                data.stream().map(EndpointStats::endpointUri).toList(),
                                sel, data.size(), data.get(sel).endpointUri)
                        : null;
            }
            default -> null;
        };
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (!snapshotLoaded) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "JFR");
        result.put("view", activeView.label);
        result.put("eventCount", snapshotEventCount);

        JsonArray rows = new JsonArray();
        switch (activeView) {
            case ROUTES -> {
                for (RouteStats r : routeData) {
                    JsonObject row = new JsonObject();
                    row.put("routeId", r.routeId());
                    row.put("total", r.total());
                    row.put("failed", r.failed());
                    row.put("minMs", r.minMs());
                    row.put("meanMs", r.meanMs());
                    row.put("maxMs", r.maxMs());
                    rows.add(row);
                }
            }
            case PROCESSORS -> {
                for (ProcessorStats p : processorData) {
                    JsonObject row = new JsonObject();
                    row.put("processorId", p.processorId());
                    row.put("processorType", p.processorType());
                    row.put("routeId", p.routeId());
                    row.put("total", p.total());
                    row.put("failed", p.failed());
                    row.put("minMs", p.minMs());
                    row.put("meanMs", p.meanMs());
                    row.put("maxMs", p.maxMs());
                    rows.add(row);
                }
            }
            case ENDPOINTS -> {
                for (EndpointStats e : endpointData) {
                    JsonObject row = new JsonObject();
                    row.put("endpointUri", e.endpointUri());
                    row.put("total", e.total());
                    row.put("failed", e.failed());
                    row.put("minMs", e.minMs());
                    row.put("meanMs", e.meanMs());
                    row.put("maxMs", e.maxMs());
                    rows.add(row);
                }
            }
            case FAILURES -> {
                for (FailureEntry f : failureData) {
                    JsonObject row = new JsonObject();
                    row.put("timestamp", f.timestamp());
                    row.put("exchangeId", f.exchangeId());
                    row.put("routeId", f.routeId());
                    row.put("exceptionType", f.exceptionType());
                    row.put("exceptionMessage", f.exceptionMessage());
                    rows.add(row);
                }
            }
            case REDELIVERIES -> {
                for (RedeliveryEntry r : redeliveryData) {
                    JsonObject row = new JsonObject();
                    row.put("timestamp", r.timestamp());
                    row.put("exchangeId", r.exchangeId());
                    row.put("routeId", r.routeId());
                    row.put("attempt", r.attempt());
                    row.put("maxAttempts", r.maxAttempts());
                    rows.add(row);
                }
            }
        }
        result.put("rows", rows);
        result.put("totalRows", rows.size());
        int sel = tableState.selected() != null ? tableState.selected() : -1;
        result.put("selectedIndex", sel);
        return result;
    }

    @Override
    public String description() {
        return "JFR runtime profiling with route, processor, and endpoint statistics";
    }

    @Override
    public String getHelpText() {
        return """
                # JFR

                The JFR tab shows the live status of camel-jfr's runtime instrumentation
                and lets you view aggregated runtime data from JFR recordings.

                ## Data Views

                Press **F5** to take a snapshot of the active JFR recording. The snapshot
                data is aggregated into five views:

                - **Routes** — per-route exchange count, failures, and timing with
                  a processor panel below showing processors for the selected route
                - **Processors** — per-processor invocation count and timing across all routes
                - **Endpoints** — per-endpoint send count and timing
                - **Failures** — recent exchange failures with exception details
                - **Redeliveries** — recent redelivery attempts

                ## Controls

                - `F5` — refresh (take new JFR snapshot)
                - `Space` — cycle view
                - `s` / `S` — cycle sort column / reverse sort direction
                - `Esc` — back

                Requires at least one active recording; start one via `--jfr`,
                `jcmd <pid> JFR.start`, or JMX.
                """;
    }
}
