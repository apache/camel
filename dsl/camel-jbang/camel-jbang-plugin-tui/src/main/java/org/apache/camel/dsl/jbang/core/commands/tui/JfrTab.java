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
    private Rect lastTableArea;
    private String drillRouteId;

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
        drillRouteId = null;
        tableState.select(0);
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

        if (ke.isKey(KeyCode.ENTER) && activeView == View.ROUTES && snapshotLoaded) {
            int sel = tableState.selected() != null ? tableState.selected() : 0;
            List<RouteStats> sorted = sortedRoutes();
            if (sel >= 0 && sel < sorted.size()) {
                drillRouteId = sorted.get(sel).routeId;
                switchView(View.PROCESSORS);
            }
            return true;
        }

        if (ke.isKey(KeyCode.ESCAPE) && drillRouteId != null) {
            drillRouteId = null;
            switchView(View.ROUTES);
            return true;
        }

        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 20 && tableState.selected() != null && tableState.selected() > 0; i++) {
                tableState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 20; i++) {
                tableState.selectNext(getRowCount());
            }
            return true;
        }
        if (ke.isHome()) {
            tableState.selectFirst();
            return true;
        }
        if (ke.isEnd()) {
            tableState.selectLast(getRowCount());
            return true;
        }

        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        tableState.selectNext(getRowCount());
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return handleTableClick(me, lastTableArea, tableState, getRowCount());
    }

    private void switchView(View view) {
        if (view != activeView) {
            activeView = view;
            tableState.select(0);
        }
    }

    private int getRowCount() {
        return switch (activeView) {
            case ROUTES -> sortedRoutes().size();
            case PROCESSORS -> filteredProcessors().size();
            case ENDPOINTS -> sortedEndpoints().size();
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

        if (loading.get() && !statusLoaded && !snapshotLoaded) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  Loading...", LABEL))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" JFR Runtime Instrumentation ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && !statusLoaded && !snapshotLoaded) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  " + errorMessage, Theme.error()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" JFR Runtime Instrumentation ").build())
                            .build(),
                    area);
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
        line1.add(Span.styled("Runtime Events: ", LABEL));
        line1.add(Span.styled(registered ? "Enabled" : "Disabled", registered ? VALUE : Theme.error()));

        if (!recordings.isEmpty()) {
            line1.add(Span.styled("  Recording: ", LABEL));
            line1.add(Span.styled(recordings.get(0), VALUE));
        } else {
            line1.add(Span.styled("  No Active Recording", LABEL));
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
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(
                                    "  Press F5 to take a JFR snapshot and view runtime data", LABEL))))
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
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            RouteStats r = data.get(i);
            String rate = r.total > 0 ? String.format(Locale.US, "%.1f%%", (r.failed * 100.0 / r.total)) : "0.0%";
            rows.add(Row.from(
                    rightCell(String.valueOf(i + 1), 4, LABEL),
                    Cell.from(Span.styled(r.routeId, Style.EMPTY.fg(Theme.baseFg()))),
                    rightCell(String.valueOf(r.total), 8),
                    rightCell(String.valueOf(r.failed), 8, r.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(rate, 8),
                    rightCell(formatMs(r.minMs), 10),
                    rightCell(formatMs(r.meanMs), 10),
                    rightCell(formatMs(r.maxMs), 10)));
        }

        Style hdr = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 4, hdr),
                        Cell.from(Span.styled("ROUTE", hdr)),
                        rightCell("TOTAL", 8, hdr),
                        rightCell("FAILED", 8, hdr),
                        rightCell("RATE", 8, hdr),
                        rightCell("MIN", 10, hdr),
                        rightCell("MEAN", 10, hdr),
                        rightCell("MAX", 10, hdr)))
                .widths(Constraint.length(4), Constraint.fill(),
                        Constraint.length(8), Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Routes (" + data.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, data.size());
    }

    private void renderProcessorsTable(Frame frame, Rect area) {
        List<ProcessorStats> data = filteredProcessors();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            ProcessorStats p = data.get(i);
            rows.add(Row.from(
                    rightCell(String.valueOf(i + 1), 4, LABEL),
                    Cell.from(Span.styled(p.processorId, Style.EMPTY.fg(Theme.baseFg()))),
                    Cell.from(Span.styled(p.processorType != null ? p.processorType : "", LABEL)),
                    Cell.from(Span.styled(p.routeId, LABEL)),
                    rightCell(String.valueOf(p.total), 8),
                    rightCell(String.valueOf(p.failed), 8, p.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(formatMs(p.minMs), 10),
                    rightCell(formatMs(p.meanMs), 10),
                    rightCell(formatMs(p.maxMs), 10)));
        }

        String title = drillRouteId != null
                ? " Processors [" + drillRouteId + "] (" + data.size() + ") "
                : " Processors (" + data.size() + ") ";
        Style hdr = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 4, hdr),
                        Cell.from(Span.styled("PROCESSOR", hdr)),
                        Cell.from(Span.styled("TYPE", hdr)),
                        Cell.from(Span.styled("ROUTE", hdr)),
                        rightCell("TOTAL", 8, hdr),
                        rightCell("FAILED", 8, hdr),
                        rightCell("MIN", 10, hdr),
                        rightCell("MEAN", 10, hdr),
                        rightCell("MAX", 10, hdr)))
                .widths(Constraint.length(4), Constraint.fill(), Constraint.length(12), Constraint.length(16),
                        Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title).build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, data.size());
    }

    private void renderEndpointsTable(Frame frame, Rect area) {
        List<EndpointStats> data = sortedEndpoints();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            EndpointStats e = data.get(i);
            rows.add(Row
                    .from(
                    rightCell(String.valueOf(i + 1), 4, LABEL),
                    Cell.from(Span.styled(e.endpointUri, Style.EMPTY.fg(Theme.baseFg()))),
                    rightCell(String.valueOf(e.total), 8),
                    rightCell(String.valueOf(e.failed), 8, e.failed > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(formatMs(e.minMs), 10),
                    rightCell(formatMs(e.meanMs), 10),
                    rightCell(formatMs(e.maxMs), 10)));
        }

        Style hdr = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 4, hdr),
                        Cell.from(Span.styled("ENDPOINT", hdr)),
                        rightCell("TOTAL", 8, hdr),
                        rightCell("FAILED", 8, hdr),
                        rightCell("MIN", 10, hdr),
                        rightCell("MEAN", 10, hdr),
                        rightCell("MAX", 10, hdr)))
                .widths(Constraint.length(4), Constraint.fill(),
                        Constraint.length(8), Constraint.length(8),
                        Constraint.length(10), Constraint.length(10), Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Endpoints (" + data.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, data.size());
    }

    private void renderFailuresTable(Frame frame, Rect area) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < failureData.size(); i++) {
            FailureEntry f = failureData.get(i);
            String time = f.timestamp != null && f.timestamp.length() > 19
                    ? f.timestamp.substring(11, 19) : (f.timestamp != null ? f.timestamp : "");
            String msg = f.exceptionMessage != null
                    ? (f.exceptionMessage.length() > 60 ? f.exceptionMessage.substring(0, 60) + "..." : f.exceptionMessage)
                    : "";
            rows.add(Row.from(
                    rightCell(String.valueOf(i + 1), 4, LABEL),
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
                        rightCell("#", 4, hdr),
                        Cell.from(Span.styled("TIME", hdr)),
                        Cell.from(Span.styled("ROUTE", hdr)),
                        Cell.from(Span.styled("EXCEPTION", hdr)),
                        Cell.from(Span.styled("MESSAGE", hdr))))
                .widths(Constraint.length(4), Constraint.length(10), Constraint.length(16),
                        Constraint.length(28), Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Failures (" + failureData.size() + ") ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, scrollState, failureData.size());
    }

    private void renderRedeliveriesTable(Frame frame, Rect area) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < redeliveryData.size(); i++) {
            RedeliveryEntry r = redeliveryData.get(i);
            String time = r.timestamp != null && r.timestamp.length() > 19
                    ? r.timestamp.substring(11, 19) : (r.timestamp != null ? r.timestamp : "");
            boolean exhausted = r.attempt >= r.maxAttempts && r.maxAttempts > 0;
            rows.add(Row.from(
                    rightCell(String.valueOf(i + 1), 4, LABEL),
                    Cell.from(Span.styled(time, LABEL)),
                    Cell.from(Span.styled(r.routeId != null ? r.routeId : "", Style.EMPTY.fg(Theme.baseFg()))),
                    rightCell(String.valueOf(r.attempt), 8),
                    rightCell(String.valueOf(r.maxAttempts), 8, exhausted ? Theme.error() : Style.EMPTY)));
        }

        Style hdr = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 4, hdr),
                        Cell.from(Span.styled("TIME", hdr)),
                        Cell.from(Span.styled("ROUTE", hdr)),
                        rightCell("ATTEMPT", 8, hdr),
                        rightCell("MAX", 8, hdr)))
                .widths(Constraint.length(4), Constraint.length(10), Constraint.fill(),
                        Constraint.length(8), Constraint.length(8))
                .highlightStyle(Theme.selectionBg())
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
        if (snapshotLoaded && activeView == View.ROUTES) {
            hint(spans, "Enter", "drill");
        }
        hintLast(spans, "F5", "snapshot");
    }

    // ---- sorting ----

    private List<RouteStats> sortedRoutes() {
        if (routeData.isEmpty()) {
            return routeData;
        }
        List<RouteStats> sorted = new ArrayList<>(routeData);
        sorted.sort(Comparator.comparingLong(RouteStats::total).reversed());
        return sorted;
    }

    private List<ProcessorStats> filteredProcessors() {
        List<ProcessorStats> filtered = drillRouteId != null
                ? processorData.stream().filter(p -> drillRouteId.equals(p.routeId)).toList()
                : processorData;
        if (filtered.isEmpty()) {
            return filtered;
        }
        List<ProcessorStats> sorted = new ArrayList<>(filtered);
        sorted.sort(Comparator.comparingDouble(ProcessorStats::meanMs).reversed());
        return sorted;
    }

    private List<EndpointStats> sortedEndpoints() {
        if (endpointData.isEmpty()) {
            return endpointData;
        }
        List<EndpointStats> sorted = new ArrayList<>(endpointData);
        sorted.sort(Comparator.comparingLong(EndpointStats::total).reversed());
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
            try {
                JsonObject root = new JsonObject();
                root.put("action", "jfr");
                root.put("command", "status");

                JsonObject jo = ctx.executeAction(pid, root, 5000);
                applyStatus(jo);
            } catch (Exception e) {
                applyError("Error: " + e.getMessage());
            } finally {
                loading.set(false);
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
                List<ProcessorStats> data = filteredProcessors();
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
                    row.put("failRate", r.total() > 0 ? Math.round(r.failed() * 1000.0 / r.total()) / 10.0 : 0);
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

                - **Routes** — per-route exchange count, failures, and timing
                - **Processors** — per-processor invocation count and timing (slowest first)
                - **Endpoints** — per-endpoint send count and timing
                - **Failures** — recent exchange failures with exception details
                - **Redeliveries** — recent redelivery attempts

                Press **Enter** on a route in the Routes view to drill down into its
                processors. Press **Esc** to return from drill-down.

                ## Controls

                - `F5` — take JFR snapshot
                - `Space` — cycle view
                - `Enter` — drill into route processors
                - `s` / `S` — cycle sort / reverse sort
                - `E` — enable all runtime events on every active recording
                - `D` — disable all runtime events on every active recording
                - `J` — generate a `.jfc` overlay with the current event selection
                - `Esc` — back (from drill-down or tab)

                Requires at least one active recording; start one via `--jfr`,
                `jcmd <pid> JFR.start`, or JMX.
                """;
    }
}
