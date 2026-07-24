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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;
import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

class OverviewTab extends AbstractTab {

    /**
     * Callback interface for process control actions triggered from overview key shortcuts.
     */
    interface OverviewActions {
        void sendRouteCommand(String pid, String routeId, String command);

        void stopSelectedProcess(boolean forceKill);

        void restartSelectedProcess();

        void showKillConfirm();

        void openDoc(IntegrationInfo info);

        void openFilesPopup();
    }

    private static final long VANISH_DURATION_MS = 6000;
    private static final int MAX_SPARKLINE_POINTS = 300;
    private static final String[] SORT_COLUMNS = { "pid", "name", "version", "status", "total", "fail" };
    private static final String[] INFRA_SORT_COLUMNS = { "service", "version", "port", "status" };
    private static final String[] TOP_SORT_COLUMNS = { "mean", "max", "min", "last", "delta", "p50", "p95", "p99" };

    static final int CHART_ALL = 0;
    static final int CHART_SINGLE = 1;
    static final int CHART_OFF = 2;

    private final Map<String, LinkedList<Long>> throughputHistory;
    private final Map<String, LinkedList<Long>> failedHistory;
    private final Map<String, LoadAvg> cpuLoadAvg;
    private final Set<String> stoppingPids;
    private final Runnable onPidChanged;
    private OverviewActions actions;

    final TableState tableState = new TableState();
    private final ScrollbarState tableScrollState = new ScrollbarState();
    private Rect lastTableArea;
    final TableState infraTableState = new TableState();
    private final ScrollbarState infraScrollState = new ScrollbarState();
    private Rect lastInfraTableArea;
    boolean infraFocused;
    boolean infraDetailVisible;
    private String lastIntegrationPid;
    int chartMode = CHART_SINGLE;
    private int bottomPanelHeight = 16;
    private final DragSplit vSplit = new DragSplit();

    private String sort = "name";
    private int sortIndex = 1;
    private boolean sortReversed;
    private String infraSort = "service";
    private int infraSortIndex = 0;
    private boolean infraSortReversed;
    boolean topMode;
    private String topSort = "mean";
    private int topSortIndex;
    private boolean topSortReversed;

    OverviewTab(
                MonitorContext ctx,
                MetricsCollector metrics,
                Set<String> stoppingPids,
                Runnable onPidChanged) {
        super(ctx);
        this.throughputHistory = metrics.getThroughputHistory();
        this.failedHistory = metrics.getFailedHistory();
        this.cpuLoadAvg = metrics.getCpuLoadAvg();
        this.stoppingPids = stoppingPids;
        this.onPidChanged = onPidChanged;
    }

    void setActions(OverviewActions actions) {
        this.actions = actions;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isChar('s')) {
            if (infraFocused) {
                infraSortIndex = (infraSortIndex + 1) % INFRA_SORT_COLUMNS.length;
                infraSort = INFRA_SORT_COLUMNS[infraSortIndex];
                infraSortReversed = false;
            } else if (topMode) {
                topSortIndex = (topSortIndex + 1) % TOP_SORT_COLUMNS.length;
                topSort = TOP_SORT_COLUMNS[topSortIndex];
                topSortReversed = false;
            } else {
                sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
                sort = SORT_COLUMNS[sortIndex];
                sortReversed = false;
            }
            return true;
        }
        if (ke.isChar('S')) {
            if (infraFocused) {
                infraSortReversed = !infraSortReversed;
            } else if (topMode) {
                topSortReversed = !topSortReversed;
            } else {
                sortReversed = !sortReversed;
            }
            return true;
        }
        if (ke.isCharIgnoreCase('t') && !infraFocused) {
            topMode = !topMode;
            return true;
        }
        if (ke.isChar('i') && !ctx.infraData.get().isEmpty()) {
            infraFocused = !infraFocused;
            if (infraFocused && infraTableState.selected() == null) {
                infraTableState.select(0);
            } else if (!infraFocused && tableState.selected() == null) {
                tableState.select(0);
            }
            if (!infraFocused) {
                infraDetailVisible = false;
            }
            syncSelectedPid();
            return true;
        }
        if (ke.isChar('d') && infraFocused) {
            infraDetailVisible = !infraDetailVisible;
            return true;
        }
        if (ke.isCharIgnoreCase('a')) {
            if (ctx.selectedPid == null) {
                // no selection: toggle between all and off (skip single)
                chartMode = chartMode == CHART_ALL ? CHART_OFF : CHART_ALL;
            } else {
                chartMode = (chartMode + 1) % 3;
            }
            return true;
        }
        // Process control keys
        if (actions != null) {
            if (ke.isChar('p') && ctx.selectedPid != null && !ctx.isInfraSelected()) {
                IntegrationInfo selInfo = ctx.findSelectedIntegration();
                if (selInfo != null) {
                    String cmd = selInfo.routeStarted > 0 ? "stop" : "start";
                    actions.sendRouteCommand(ctx.selectedPid, "*", cmd);
                }
                return true;
            }
            if (ke.isChar('x') && ctx.selectedPid != null) {
                actions.stopSelectedProcess(false);
                return true;
            }
            if (ke.isChar('X') && ctx.selectedPid != null) {
                actions.showKillConfirm();
                return true;
            }
            if (ke.isChar('r') && ctx.selectedPid != null && !ctx.isInfraSelected()) {
                actions.restartSelectedProcess();
                return true;
            }
            if (ke.isChar('f') && ctx.selectedPid != null && !ctx.isInfraSelected()) {
                actions.openFilesPopup();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the table area captured during the last render, or {@code null} before the first render. Package-private
     * for click hit-testing in tests.
     */
    Rect getTableArea() {
        return lastTableArea;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (vSplit.handleMouse(me, me.y())) {
            if (vSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                bottomPanelHeight = Math.max(5, Math.min(area.y() + area.height() - me.y(), area.height() - 5));
            }
            return true;
        }
        if (handleTableClick(me, lastTableArea, tableState, sortedInfos().size())) {
            infraFocused = false;
            syncSelectedPid();
            return true;
        }
        if (lastInfraTableArea != null && handleTableClick(me, lastInfraTableArea, infraTableState,
                ctx.infraData.get().size())) {
            infraFocused = true;
            syncSelectedPid();
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (infraFocused) {
            infraTableState.selectPrevious();
        } else {
            tableState.selectPrevious();
        }
        syncSelectedPid();
    }

    @Override
    public void navigateDown() {
        if (infraFocused) {
            infraTableState.selectNext(ctx.infraData.get().size());
        } else {
            tableState.selectNext(sortedInfos().size());
        }
        syncSelectedPid();
    }

    @Override
    public void render(Frame frame, Rect area) {
        List<IntegrationInfo> infos = sortedInfos();
        List<InfraInfo> infraInfos = sortedInfraInfos();

        int integrationCount = infos.size();
        int infraCount = infraInfos.size();

        if (integrationCount == 0 && infraCount == 0) {
            renderEmptyState(frame, area);
            return;
        }

        if (infraCount == 0) {
            infraFocused = false;
        }

        if (ctx.selectedPid != null) {
            for (int i = 0; i < infos.size(); i++) {
                if (ctx.selectedPid.equals(infos.get(i).pid)) {
                    tableState.select(i);
                    break;
                }
            }
            for (int i = 0; i < infraInfos.size(); i++) {
                if (ctx.selectedPid.equals(infraInfos.get(i).pid)) {
                    infraTableState.select(i);
                    break;
                }
            }
        }

        InfraInfo infraSel = infraDetailVisible ? ctx.findSelectedInfra() : null;
        boolean showInfraDetail = infraSel != null;
        boolean hasSparkline = !showInfraDetail && chartMode != CHART_OFF
                && !throughputHistory.isEmpty() && ctx.shellPercent < 50;
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.fill());
        if (infraCount > 0) {
            int infraPanelHeight = Math.max(6, Math.min(infraCount + 3, area.height() / 3));
            constraints.add(Constraint.length(infraPanelHeight));
        }
        if (hasSparkline || showInfraDetail) {
            bottomPanelHeight = Math.max(5, Math.min(bottomPanelHeight, area.height() - 5));
            constraints.add(Constraint.length(bottomPanelHeight));
        }
        List<Rect> chunks = Layout.vertical()
                .constraints(constraints)
                .split(area);

        int bottomPanelIndex = infraCount > 0 ? 2 : 1;
        if (chunks.size() > bottomPanelIndex) {
            vSplit.setBorderPos(chunks.get(bottomPanelIndex).y());
        } else {
            vSplit.clearBorderPos();
        }

        List<Row> rows = new ArrayList<>();
        Row header;
        Constraint[] widths;
        int rowIndex = 0;

        if (topMode) {
            for (IntegrationInfo info : infos) {
                boolean isEven = (rowIndex++ % 2 == 0);
                Style rowBg = isEven ? Style.EMPTY.bg(Theme.zebra()) : Style.EMPTY;

                if (info.vanishing) {
                    long elapsed = System.currentTimeMillis() - info.vanishStart;
                    float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                    int gray = (int) (100 * fade);
                    Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));
                    String vanishName = TuiIcons.labeled(TuiIcons.CAMEL, info.name != null ? info.name : "");
                    rows.add(Row.from(
                            Cell.from(Span.styled(vanishName, dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle))).style(rowBg));
                } else {
                    String platformIcon = TuiIcons.runtimeIcon(info.platform != null ? info.platform : "");
                    String nameText = platformIcon + " " + (info.name != null ? info.name : "");
                    List<Span> nameSpans = new ArrayList<>();
                    nameSpans.add(Span.styled(nameText, Theme.info()));
                    if (info.devMode) {
                        nameSpans.add(Span.styled(" [dev]", Theme.label()));
                    }
                    Line nameLine = Line.from(nameSpans);
                    Style failStyle = info.failed > 0 ? Theme.error().bold() : Style.EMPTY;
                    String throughputDisplay = info.throughput;
                    if (throughputDisplay == null || "0.00".equals(throughputDisplay)) {
                        LinkedList<Long> tpHist = throughputHistory.get(info.pid);
                        if (tpHist != null && !tpHist.isEmpty()) {
                            long tp = tpHist.getLast();
                            if (tp > 0) {
                                throughputDisplay = String.format(java.util.Locale.US, "%.2f", (double) tp);
                            }
                        }
                    }
                    if (ctx.ratePerMinute) {
                        throughputDisplay = TuiHelper.throughputPerMinute(throughputDisplay);
                    }
                    rows.add(Row.from(
                            Cell.from(nameLine),
                            rightCell(info.exchangesTotal > 0 ? formatDurationMs(info.meanTime) : "", 8,
                                    TuiHelper.topTimeStyle(info.meanTime)),
                            rightCell(info.exchangesTotal > 0 ? formatDurationMs(info.maxTime) : "", 8,
                                    TuiHelper.topTimeStyle(info.maxTime)),
                            rightCell(info.exchangesTotal > 0 ? formatDurationMs(info.minTime) : "", 8),
                            rightCell(info.exchangesTotal > 0 ? formatDurationMs(info.lastTime) : "", 8),
                            rightCell(info.deltaTime != 0 ? formatDurationMs(info.deltaTime) : "", 8,
                                    TuiHelper.topDeltaStyle(info.deltaTime)),
                            rightCell(info.p50Time >= 0 ? formatDurationMs(info.p50Time) : "", 8),
                            rightCell(info.p95Time >= 0 ? formatDurationMs(info.p95Time) : "", 8),
                            rightCell(info.p99Time >= 0 ? formatDurationMs(info.p99Time) : "", 8),
                            rightCell(String.valueOf(info.exchangesTotal), 8),
                            rightCell(String.valueOf(info.failed), 6, failStyle),
                            rightCell(String.valueOf(info.inflight), 8),
                            rightCell(throughputDisplay != null ? throughputDisplay : "", 8),
                            rightCell(TuiHelper.formatLoad(
                                    info.inflightLoad01, info.inflightLoad05, info.inflightLoad15), 12))
                            .style(rowBg));
                }
            }

            header = Row.from(
                    Cell.from(Span.styled("NAME", Style.EMPTY.bold())),
                    rightCell(topSortLabel("MEAN", "mean"), 8, topSortStyle("mean")),
                    rightCell(topSortLabel("MAX", "max"), 8, topSortStyle("max")),
                    rightCell(topSortLabel("MIN", "min"), 8, topSortStyle("min")),
                    rightCell(topSortLabel("LAST", "last"), 8, topSortStyle("last")),
                    rightCell(topSortLabel("DELTA", "delta"), 8, topSortStyle("delta")),
                    rightCell(topSortLabel("P50", "p50"), 8, topSortStyle("p50")),
                    rightCell(topSortLabel("P95", "p95"), 8, topSortStyle("p95")),
                    rightCell(topSortLabel("P99", "p99"), 8, topSortStyle("p99")),
                    rightCell("TOTAL", 8, Style.EMPTY.bold()),
                    rightCell("FAIL", 6, Style.EMPTY.bold()),
                    rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                    rightCell(ctx.ratePerMinute ? "MSG/M" : "MSG/S", 8, Style.EMPTY.bold()),
                    rightCell("LOAD", 12, Style.EMPTY.bold()));

            widths = new Constraint[] {
                    Constraint.fill(),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(6),
                    Constraint.length(8),
                    Constraint.length(8),
                    Constraint.length(13)
            };
        } else {
            boolean hasPercentiles = infos.stream().anyMatch(i -> i.p50Time >= 0);
            long maxTotal = infos.stream().mapToLong(i -> i.exchangesTotal).max().orElse(0);
            long maxFailed = infos.stream().mapToLong(i -> i.failed).max().orElse(0);
            int tw = Math.max(numWidth(maxTotal), 6);
            int fw = Math.max(numWidth(maxFailed), 6);
            for (IntegrationInfo info : infos) {
                boolean isEven = (rowIndex++ % 2 == 0);
                Style rowBg = isEven ? Style.EMPTY.bg(Theme.zebra()) : Style.EMPTY;

                if (info.vanishing) {
                    long elapsed = System.currentTimeMillis() - info.vanishStart;
                    float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                    int gray = (int) (100 * fade);
                    Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));

                    String vanishName = TuiIcons.labeled(TuiIcons.CAMEL, info.name != null ? info.name : "");
                    rows.add(Row.from(
                            Cell.from(Span.styled(info.pid, dimStyle)),
                            Cell.from(Span.styled(vanishName, dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled(TuiIcons.STOPPED + " Stopped", Theme.error().dim())),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle)),
                            Cell.from(Span.styled("", dimStyle))).style(rowBg));
                } else {
                    String stateText = extractState(info.state);
                    if (stoppingPids.contains(info.pid) || "Terminating".equals(stateText)) {
                        stateText = "Stopping";
                    } else if ("Terminated".equals(stateText)) {
                        stateText = "Stopped";
                    } else if ("Running".equals(stateText) && info.routeStarted == 0 && info.routeTotal > 0) {
                        stateText = "Stopped";
                    }
                    Style statusStyle = switch (stateText) {
                        case "Started", "Running" -> Theme.success();
                        case "Stopped" -> Theme.error();
                        default -> Theme.warning();
                    };

                    Style failStyle = info.failed > 0 ? Theme.error().bold() : Style.EMPTY;

                    boolean hasDoc = info.readmeFiles != null && !info.readmeFiles.isEmpty();
                    if (!hasDoc) {
                        hasDoc = hasReadmeInSourceDir(info);
                    }
                    String platformIcon = TuiIcons.runtimeIcon(info.platform != null ? info.platform : "");
                    String nameText = platformIcon + " " + (info.name != null ? info.name : "");
                    List<Span> nameSpans = new ArrayList<>();
                    nameSpans.add(Span.styled(nameText, Theme.info()));
                    if (info.devMode) {
                        nameSpans.add(Span.styled(" [dev]", Theme.label()));
                    }
                    if (hasDoc) {
                        nameSpans.add(Span.styled(" " + TuiIcons.README, Style.EMPTY));
                    }
                    Line nameLine = Line.from(nameSpans);
                    String throughputDisplay = info.throughput;
                    if (throughputDisplay == null || "0.00".equals(throughputDisplay)) {
                        LinkedList<Long> tpHist = throughputHistory.get(info.pid);
                        if (tpHist != null && !tpHist.isEmpty()) {
                            long tp = tpHist.getLast();
                            if (tp > 0) {
                                throughputDisplay = String.format(java.util.Locale.US, "%.2f", (double) tp);
                            }
                        }
                    }
                    if (ctx.ratePerMinute) {
                        throughputDisplay = TuiHelper.throughputPerMinute(throughputDisplay);
                    }
                    String timingCol;
                    if (hasPercentiles && info.p50Time >= 0) {
                        timingCol = formatDurationMs(info.p50Time) + "/" + formatDurationMs(info.p95Time) + "/"
                                    + formatDurationMs(info.p99Time);
                    } else if (info.exchangesTotal > 0) {
                        timingCol = formatDurationMs(info.minTime) + "/" + formatDurationMs(info.maxTime) + "/"
                                    + formatDurationMs(info.meanTime);
                    } else {
                        timingCol = "";
                    }
                    Line totalCell = info.sinceLastCompleted != null
                            ? Line.from(Span.raw(String.format("%" + tw + "d", info.exchangesTotal)),
                                    Span.styled(" (" + info.sinceLastCompleted + ")", Theme.muted()))
                            : Line.from(Span.raw(String.format("%" + tw + "d", info.exchangesTotal)));
                    Line failCell = info.sinceLastFailed != null
                            ? Line.from(Span.styled(String.format("%" + fw + "d", info.failed), failStyle),
                                    Span.styled(" (" + info.sinceLastFailed + ")", Theme.muted()))
                            : Line.from(Span.styled(String.format("%" + fw + "d", info.failed), failStyle));

                    rows.add(Row.from(
                            Cell.from(info.pid),
                            Cell.from(nameLine),
                            Cell.from(info.camelVersion != null ? info.camelVersion : ""),
                            centerCell(info.ready != null ? info.ready : "", 7),
                            Cell.from(Span.styled(stateText, statusStyle)),
                            rightCell(info.routeStarted + "/" + info.routeTotal, 7),
                            rightCell(throughputDisplay != null ? throughputDisplay : "", 8),
                            Cell.from(totalCell),
                            Cell.from(failCell),
                            rightCell(timingCol, 14),
                            Cell.from(buildPercentileBarLine(info.p50Time, info.p95Time, info.p99Time, 10))).style(rowBg));
                }
            }

            String timingHeader = hasPercentiles ? "P50/P95/P99" : "MIN/MAX/MEAN";
            header = Row.from(
                    Cell.from(Span.styled(sortLabel("PID", "pid"), sortStyle("pid"))),
                    Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                    Cell.from(Span.styled(sortLabel("VERSION", "version"), sortStyle("version"))),
                    centerCell("READY", 7, Style.EMPTY.bold()),
                    Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                    rightCell("ROUTE", 7, Style.EMPTY.bold()),
                    rightCell(ctx.ratePerMinute ? "MSG/M" : "MSG/S", 8, Style.EMPTY.bold()),
                    centerCell(sortLabel("TOTAL", "total"), 14, sortStyle("total")),
                    centerCell(sortLabel("FAIL", "fail"), 14, sortStyle("fail")),
                    rightCell(timingHeader, 14, Style.EMPTY.bold()),
                    Cell.from(""));

            widths = new Constraint[] {
                    Constraint.length(8),
                    Constraint.fill(),
                    Constraint.length(16),
                    Constraint.length(7),
                    Constraint.length(10),
                    Constraint.length(7),
                    Constraint.length(8),
                    Constraint.length(14),
                    Constraint.length(14),
                    Constraint.length(14),
                    Constraint.fill()
            };
        }

        Table.Builder tableBuilder = Table.builder()
                .rows(rows)
                .header(header)
                .widths(widths)
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(infraCount > 0 ? " Integrations " : " Overview ").build());
        if (!infraFocused) {
            tableBuilder.highlightStyle(Theme.selectionBg());
        }
        Table table = tableBuilder.build();

        lastTableArea = chunks.get(0);
        TableState renderState = infraFocused ? new TableState() : tableState;
        frame.renderStatefulWidget(table, chunks.get(0), renderState);
        renderTableScrollbar(frame, lastTableArea, tableState, tableScrollState, integrationCount);

        if (infraCount > 0) {
            renderInfraTable(frame, chunks.get(1), infraInfos);
        } else {
            lastInfraTableArea = null;
        }

        if (hasSparkline && chunks.size() > 1) {
            Rect chartTotalArea = chunks.get(chunks.size() - 1);

            List<Rect> chartHSplit = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(34))
                    .split(chartTotalArea);
            Rect chartArea = chartHSplit.get(0);
            Rect infoArea = chartHSplit.get(1);

            Rect chartInner = Block.builder().borders(Borders.ALL).build().inner(chartArea);

            List<Rect> vChunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(chartInner);

            List<Rect> hChunks = Layout.horizontal()
                    .constraints(Constraint.length(4), Constraint.fill())
                    .split(vChunks.get(0));

            Rect barChartArea = hChunks.get(1);

            int innerBarCols = Math.max(2, barChartArea.width());
            int renderPoints = Math.min(MAX_SPARKLINE_POINTS, innerBarCols / 2);

            long[] mergedTotal = new long[renderPoints];
            long[] mergedFailed = new long[renderPoints];
            final String chartPid = chartMode == CHART_SINGLE
                    ? (infraFocused ? lastIntegrationPid : ctx.selectedPid) : null;
            for (int i = 0; i < renderPoints; i++) {
                for (Map.Entry<String, LinkedList<Long>> e : throughputHistory.entrySet()) {
                    if (chartPid == null || chartPid.equals(e.getKey())) {
                        int idx = e.getValue().size() - renderPoints + i;
                        if (idx >= 0) {
                            mergedTotal[i] += e.getValue().get(idx);
                        }
                    }
                }
                for (Map.Entry<String, LinkedList<Long>> e : failedHistory.entrySet()) {
                    if (chartPid == null || chartPid.equals(e.getKey())) {
                        int idx = e.getValue().size() - renderPoints + i;
                        if (idx >= 0) {
                            mergedFailed[i] += e.getValue().get(idx);
                        }
                    }
                }
            }

            long rawMax = 0;
            for (long v : mergedTotal) {
                rawMax = Math.max(rawMax, v);
            }
            long maxTp = MetricsCollector.niceMax(rawMax);
            long curTp = mergedTotal[renderPoints - 1];
            // Clamp failed to total so the title matches the chart bars (total comes from
            // EWMA while failed is still delta-based, so failed can momentarily exceed total)
            long curFailed = Math.min(mergedFailed[renderPoints - 1], curTp);
            long curOk = Math.max(0, curTp - curFailed);

            // Format throughput values unscaled for display
            String curTpFmt = MetricsCollector.formatThroughput(curTp);
            String curOkFmt = MetricsCollector.formatThroughput(curOk);
            String curFailFmt = MetricsCollector.formatThroughput(curFailed);

            Line titleLine;
            if (chartMode == CHART_SINGLE && chartPid != null) {
                IntegrationInfo chartSel = ctx.data.get().stream()
                        .filter(ii -> chartPid.equals(ii.pid)).findFirst().orElse(null);
                String chartName = chartSel != null ? TuiHelper.truncate(chartSel.name, 30) : chartPid;
                titleLine = Line.from(
                        Span.raw(" ["),
                        Span.styled(chartName, Theme.label().bold()),
                        Span.raw(String.format("] Rate: %s msg/s  ", curTpFmt)),
                        Span.styled("■", Theme.success()),
                        Span.raw(String.format(" ok:%s  ", curOkFmt)),
                        Span.styled("■", Theme.error()),
                        Span.raw(String.format(" fail:%s ", curFailFmt)));
            } else {
                titleLine = Line.from(
                        Span.raw(String.format(" [All] Rate: %s msg/s  ", curTpFmt)),
                        Span.styled("■", Theme.success()),
                        Span.raw(String.format(" ok:%s  ", curOkFmt)),
                        Span.styled("■", Theme.error()),
                        Span.raw(String.format(" fail:%s ", curFailFmt)));
            }

            Block chartBlock = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                    .title(Title.from(titleLine)).build();
            frame.renderWidget(chartBlock, chartArea);

            List<BarGroup> groups = new ArrayList<>();
            for (int i = 0; i < renderPoints; i++) {
                long failed = Math.min(mergedFailed[i], mergedTotal[i]);
                long ok = Math.max(0, mergedTotal[i] - failed);
                groups.add(BarGroup.of(
                        Bar.builder().value(ok).textValue("").style(Theme.success())
                                .build(),
                        Bar.builder().value(failed).textValue("").style(Theme.error()).build()));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .max(maxTp)
                    .barWidth(1)
                    .barGap(0)
                    .groupGap(0)
                    .build();

            frame.renderWidget(barChart, barChartArea);

            int barRows = vChunks.get(0).height();
            List<Line> yLines = new ArrayList<>();
            Style dimStyle = Style.EMPTY.dim();
            for (int row = 0; row < barRows; row++) {
                if (row == 0) {
                    yLines.add(
                            Line.from(Span.styled(String.format("%3s", MetricsCollector.formatThroughput(maxTp)), dimStyle)));
                } else if (barRows > 4 && row == barRows / 2) {
                    yLines.add(Line
                            .from(Span.styled(String.format("%3s", MetricsCollector.formatThroughput(maxTp / 2)), dimStyle)));
                } else if (row == barRows - 1) {
                    yLines.add(Line.from(Span.styled("  0", dimStyle)));
                } else {
                    yLines.add(Line.from(""));
                }
            }
            frame.renderWidget(Paragraph.builder().text(Text.from(yLines)).build(), hChunks.get(0));

            if (!vChunks.get(1).isEmpty()) {
                int barInnerStartX = barChartArea.x();
                int xAxisY = vChunks.get(1).y();
                int step;
                if (renderPoints <= 20) {
                    step = 5;
                } else if (renderPoints <= 80) {
                    step = 10;
                } else {
                    step = 20;
                }
                // "now" label at the right edge
                int nowX = barInnerStartX + (renderPoints - 1) * 2;
                if (nowX + 3 <= barChartArea.right()) {
                    frame.buffer().setString(nowX, xAxisY, "now", dimStyle);
                }
                // round time markers from right to left
                for (int s = step; s <= renderPoints; s += step) {
                    int groupIdx = renderPoints - 1 - s;
                    if (groupIdx < 0) {
                        break;
                    }
                    String label = "-" + s + "s";
                    int markerX = barInnerStartX + groupIdx * 2;
                    if (markerX + label.length() <= barChartArea.right()) {
                        frame.buffer().setString(markerX, xAxisY, label, dimStyle);
                    }
                }
            }

            renderInfoPanel(frame, infoArea);
        } else if (showInfraDetail) {
            renderInfraInfoPanel(frame, chunks.get(chunks.size() - 1), infraSel);
        }
    }

    private void renderInfoPanel(Frame frame, Rect area) {
        if (!infraFocused) {
            InfraInfo infra = ctx.findSelectedInfra();
            if (infra != null) {
                renderInfraInfoPanel(frame, area, infra);
                return;
            }
        }

        IntegrationInfo sel = null;
        String pidToFind = infraFocused ? lastIntegrationPid : ctx.selectedPid;
        if (pidToFind != null) {
            sel = ctx.data.get().stream()
                    .filter(ii -> pidToFind.equals(ii.pid) && !ii.vanishing)
                    .findFirst().orElse(null);
        }
        if (sel == null) {
            List<IntegrationInfo> active = ctx.data.get().stream().filter(ii -> !ii.vanishing).toList();
            if (active.size() == 1) {
                sel = active.get(0);
            }
        }
        Block infoBlock = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).build();
        frame.renderWidget(infoBlock, area);
        Rect inner = infoBlock.inner(area);
        List<Line> lines = new ArrayList<>();
        Style dim = Theme.muted();
        int jvmDetailStart = -1;
        int jvmDetailCount = 0;
        if (sel != null) {
            if (sel.platform != null) {
                String platEmoji = TuiIcons.platformIcon(sel.platform);
                String plat = sel.platformVersion != null
                        ? platEmoji + sel.platform + " v" + sel.platformVersion
                        : platEmoji + sel.platform;
                lines.add(Line.from(
                        Span.styled("Runtime: ", dim),
                        Span.raw(TuiHelper.truncate(plat, inner.width() - 9))));
            }
            if (sel.camelVersion != null) {
                lines.add(Line.from(
                        Span.styled("Version: ", dim),
                        Span.raw(TuiHelper.truncate(sel.camelVersion, inner.width() - 9))));
            }
            {
                List<Span> profileSpans = new ArrayList<>();
                profileSpans.add(Span.styled("Profile: ", dim));
                String profile = sel.profile != null ? sel.profile : "prod";
                String profileEmoji = TuiIcons.profilePrefix(profile);
                profileSpans.add(Span.raw(profileEmoji + profile));
                if (sel.reloaded > 0) {
                    profileSpans.add(Span.raw("    "));
                    profileSpans.add(Span.styled("Reload: ", dim));
                    profileSpans.add(Span.raw(String.valueOf(sel.reloaded)));
                }
                lines.add(Line.from(profileSpans));
            }
            lines.add(Line.from(Span.raw("")));
            if (sel.javaVersion != null) {
                lines.add(Line.from(
                        Span.styled("JVM:  ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVersion, inner.width() - 6))));
            }
            if (sel.javaVendor != null) {
                jvmDetailStart = lines.size();
                lines.add(Line.from(
                        Span.styled("      ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVendor, inner.width() - 6))));
                jvmDetailCount++;
            }
            if (sel.javaVmName != null) {
                if (jvmDetailStart < 0) {
                    jvmDetailStart = lines.size();
                }
                lines.add(Line.from(
                        Span.styled("      ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVmName, inner.width() - 6))));
                jvmDetailCount++;
            }
            lines.add(Line.from(
                    Span.styled("Uptime: ", dim),
                    Span.raw(sel.ago != null ? sel.ago : "-")));
            if (sel.heapMemUsed > 0) {
                String heap = formatMemory(sel.heapMemUsed, sel.heapMemMax);
                long pct = sel.heapMemMax > 0 ? sel.heapMemUsed * 100 / sel.heapMemMax : 0;
                lines.add(Line.from(
                        Span.styled("Heap: ", dim),
                        Span.raw(heap + " " + pct + "%")));
            }
            if (sel.nonHeapMemUsed > 0) {
                lines.add(Line.from(
                        Span.styled("Meta: ", dim),
                        Span.raw(formatMemory(sel.nonHeapMemUsed, 0))));
            }
            if (sel.threadCount > 0) {
                lines.add(Line.from(
                        Span.styled("Thds: ", dim),
                        Span.raw(sel.threadCount + " / " + sel.peakThreadCount)));
            }
            LoadAvg cpu = cpuLoadAvg.get(sel.pid);
            boolean hasInfl = sel.inflightLoad01 != null && !sel.inflightLoad01.isEmpty();
            if (cpu != null || hasInfl) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(Span.styled("Load (1m/5m/15m):", dim)));
                if (cpu != null) {
                    lines.add(Line.from(
                            Span.styled("CPU:  ", dim),
                            Span.raw(cpu.format("%.1f / %.1f / %.1f %%"))));
                }
                if (hasInfl) {
                    lines.add(Line.from(
                            Span.styled("Infl: ", dim),
                            Span.raw(sel.inflightLoad01 + " / " + sel.inflightLoad05 + " / " + sel.inflightLoad15)));
                }
            }
            // if content exceeds panel height, drop JVM vendor/VM name to make room for load
            if (lines.size() > inner.height() && jvmDetailStart >= 0) {
                lines.subList(jvmDetailStart, jvmDetailStart + jvmDetailCount).clear();
            }
        } else {
            lines.add(Line.from(Span.raw("")));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    private static int countInfraLines(InfraInfo infra) {
        int count = 2; // "Service: ..." + blank line
        for (Map.Entry<String, Object> e : infra.properties.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> map) {
                count += 1 + map.size();
            } else {
                count++;
            }
        }
        return count;
    }

    private void renderInfraInfoPanel(Frame frame, Rect area, InfraInfo infra) {
        Block infoBlock = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).build();
        frame.renderWidget(infoBlock, area);
        Rect inner = infoBlock.inner(area);
        List<Line> lines = new ArrayList<>();
        Style dim = Theme.muted();
        lines.add(Line.from(
                Span.styled("Service: ", dim),
                Span.styled(infra.alias, Theme.notice())));
        lines.add(Line.from(Span.raw("")));
        for (Map.Entry<String, Object> e : infra.properties.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("get") && key.length() > 3) {
                key = key.substring(3);
            }
            Object val = e.getValue();
            if (val instanceof Map<?, ?> map) {
                lines.add(Line.from(Span.styled(key + ":", dim)));
                for (Map.Entry<?, ?> me : map.entrySet()) {
                    String mk = String.valueOf(me.getKey());
                    String mv = String.valueOf(me.getValue());
                    lines.add(Line.from(
                            Span.styled("  " + mk + ": ", dim),
                            Span.raw(TuiHelper.truncate(mv, inner.width() - mk.length() - 4))));
                }
            } else {
                String value = String.valueOf(val);
                lines.add(Line.from(
                        Span.styled(key + ": ", dim),
                        Span.raw(TuiHelper.truncate(value, inner.width() - key.length() - 2))));
            }
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    private void renderInfraTable(Frame frame, Rect area, List<InfraInfo> infraInfos) {
        List<Row> infraRows = new ArrayList<>();
        int rowIndex = 0;
        for (InfraInfo info : infraInfos) {
            boolean isEven = (rowIndex++ % 2 == 0);
            Style rowBg = isEven ? Style.EMPTY.bg(Theme.zebra()) : Style.EMPTY;
            Style statusStyle = info.alive ? Theme.success() : Theme.error();

            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));
                String vanishAlias = TuiIcons.INFRA + "  " + info.alias;
                infraRows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(vanishAlias, dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled(TuiIcons.STOPPED + " Stopped", Theme.error().dim())),
                        Cell.from(Span.styled("", dimStyle))).style(rowBg));
            } else {
                String statusText = info.alive ? "Running" : "Stopped";
                String infraAlias = TuiIcons.INFRA + "  " + info.alias;
                String version = info.serviceVersion != null ? info.serviceVersion : "";
                String port = extractInfraPort(info);
                String desc = info.description != null ? info.description : "";
                infraRows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(infraAlias, Theme.notice())),
                        Cell.from(version),
                        rightCell(port, 7),
                        Cell.from(Span.styled(statusText, statusStyle)),
                        Cell.from(desc)).style(rowBg));
            }
        }

        Row infraHeader = Row.from(
                Cell.from(Span.styled("PID", Style.EMPTY.bold())),
                Cell.from(Span.styled(infraSortLabel("SERVICE", "service"), infraSortStyle("service"))),
                Cell.from(Span.styled(infraSortLabel("VERSION", "version"), infraSortStyle("version"))),
                rightCell(infraSortLabel("PORT", "port"), 7, infraSortStyle("port")),
                Cell.from(Span.styled(infraSortLabel("STATUS", "status"), infraSortStyle("status"))),
                Cell.from(Span.styled("DESCRIPTION", Style.EMPTY.bold())));

        Table.Builder infraBuilder = Table.builder()
                .rows(infraRows)
                .header(infraHeader)
                .widths(
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(16),
                        Constraint.length(7),
                        Constraint.length(10),
                        Constraint.fill())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Dev/Infra Services ").build());
        if (infraFocused) {
            infraBuilder.highlightStyle(Theme.selectionBg());
        }
        Table infraTable = infraBuilder.build();

        lastInfraTableArea = area;
        TableState renderState = infraFocused ? infraTableState : new TableState();
        frame.renderStatefulWidget(infraTable, area, renderState);
        renderTableScrollbar(frame, area, infraTableState, infraScrollState, infraInfos.size());
    }

    private static String extractInfraPort(InfraInfo info) {
        Object port = info.properties.get("port");
        if (port != null) {
            return String.valueOf(port);
        }
        return "";
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "q", "quit");
        if (ctx.selectedPid != null) {
            hint(spans, "Esc", "unselect");
        }
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        if (!ctx.infraData.get().isEmpty()) {
            hint(spans, "i", infraFocused ? "integrations" : "infra");
        }
        if (infraFocused) {
            hint(spans, "d", "details");
        }
        if (!ctx.isInfraSelected()) {
            hint(spans, "s", "sort");
            hint(spans, "t", topMode ? "top [on]" : "top [off]");
            int effectiveMode = (chartMode == CHART_SINGLE && ctx.selectedPid == null) ? CHART_ALL : chartMode;
            hint(spans, "a", "chart " + switch (effectiveMode) {
                case CHART_ALL -> "[all]";
                case CHART_SINGLE -> "[single]";
                default -> "[off]";
            });
        }
    }

    @Override
    public SelectionContext getSelectionContext() {
        if (infraFocused) {
            List<InfraInfo> infras = ctx.infraData.get();
            if (infras.isEmpty()) {
                return null;
            }
            List<String> items = infras.stream().map(i -> i.alias != null ? i.alias : i.pid).toList();
            Integer sel = infraTableState.selected();
            return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Services");
        }
        List<IntegrationInfo> infos = sortedInfos();
        if (infos.isEmpty()) {
            return null;
        }
        List<String> items = infos.stream().map(i -> i.name != null ? i.name : i.pid).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Integrations");
    }

    List<IntegrationInfo> sortedInfos() {
        List<IntegrationInfo> infos = new ArrayList<>(ctx.data.get());
        infos.sort(topMode ? this::topSortCompare : this::sortCompare);
        return infos;
    }

    void selectCurrentIntegration() {
        if (ctx.selectedPid != null) {
            if (ctx.findSelectedIntegration() != null || ctx.findSelectedInfra() != null) {
                return;
            }
            ctx.selectedPid = null;
        }
        if (infraFocused) {
            List<InfraInfo> infras = sortedInfraInfos();
            Integer sel = infraTableState.selected();
            if (sel != null && sel >= 0 && sel < infras.size()) {
                ctx.selectedPid = infras.get(sel).pid;
            }
        } else {
            List<IntegrationInfo> infos = sortedInfos();
            Integer sel = tableState.selected();
            if (sel != null && sel >= 0 && sel < infos.size()) {
                ctx.selectedPid = infos.get(sel).pid;
            } else if (infos.size() == 1) {
                ctx.selectedPid = infos.get(0).pid;
            }
        }
    }

    private void syncSelectedPid() {
        String newPid = null;
        if (infraFocused) {
            List<InfraInfo> infras = sortedInfraInfos();
            Integer sel = infraTableState.selected();
            if (sel != null && sel >= 0 && sel < infras.size()) {
                newPid = infras.get(sel).pid;
            }
        } else {
            List<IntegrationInfo> infos = sortedInfos();
            Integer sel = tableState.selected();
            if (sel != null && sel >= 0 && sel < infos.size()) {
                newPid = infos.get(sel).pid;
            }
            if (newPid == null && infos.size() == 1) {
                newPid = infos.get(0).pid;
            }
            if (newPid != null) {
                lastIntegrationPid = newPid;
            }
        }
        if (newPid != null && !newPid.equals(ctx.selectedPid)) {
            ctx.selectedPid = newPid;
            ctx.lastSelectedName = null;
            onPidChanged.run();
        }
    }

    private int sortCompare(IntegrationInfo a, IntegrationInfo b) {
        if (a.vanishing != b.vanishing) {
            return a.vanishing ? 1 : -1;
        }
        int result = switch (sort) {
            case "pid" -> {
                String pa = a.pid != null ? a.pid : "";
                String pb = b.pid != null ? b.pid : "";
                yield pa.compareTo(pb);
            }
            case "name" -> {
                String na = a.name != null ? a.name : "";
                String nb = b.name != null ? b.name : "";
                yield na.compareToIgnoreCase(nb);
            }
            case "version" -> {
                String va = a.camelVersion != null ? a.camelVersion : "";
                String vb = b.camelVersion != null ? b.camelVersion : "";
                yield va.compareToIgnoreCase(vb);
            }
            case "status" -> Integer.compare(a.state, b.state);
            case "total" -> Long.compare(b.exchangesTotal, a.exchangesTotal);
            case "fail" -> Long.compare(b.failed, a.failed);
            default -> 0;
        };
        return sortReversed ? -result : result;
    }

    private int topSortCompare(IntegrationInfo a, IntegrationInfo b) {
        if (a.vanishing != b.vanishing) {
            return a.vanishing ? 1 : -1;
        }
        int result = switch (topSort) {
            case "mean" -> Long.compare(b.meanTime, a.meanTime);
            case "max" -> Long.compare(b.maxTime, a.maxTime);
            case "min" -> Long.compare(b.minTime, a.minTime);
            case "last" -> Long.compare(b.lastTime, a.lastTime);
            case "delta" -> Long.compare(Math.abs(b.deltaTime), Math.abs(a.deltaTime));
            case "p50" -> Long.compare(b.p50Time, a.p50Time);
            case "p95" -> Long.compare(b.p95Time, a.p95Time);
            case "p99" -> Long.compare(b.p99Time, a.p99Time);
            default -> 0;
        };
        return topSortReversed ? -result : result;
    }

    private String topSortLabel(String label, String column) {
        return sortLabel(label, column, topSort, topSortReversed);
    }

    private Style topSortStyle(String column) {
        return sortStyle(column, topSort);
    }

    private static int numWidth(long... values) {
        long max = 0;
        for (long v : values) {
            max = Math.max(max, Math.abs(v));
        }
        return Math.max(String.valueOf(max).length(), 1);
    }

    private int infraSortCompare(InfraInfo a, InfraInfo b) {
        if (a.vanishing != b.vanishing) {
            return a.vanishing ? 1 : -1;
        }
        int result = switch (infraSort) {
            case "service" -> {
                String sa = a.alias != null ? a.alias : "";
                String sb = b.alias != null ? b.alias : "";
                yield sa.compareToIgnoreCase(sb);
            }
            case "version" -> {
                String va = a.serviceVersion != null ? a.serviceVersion : "";
                String vb = b.serviceVersion != null ? b.serviceVersion : "";
                yield va.compareToIgnoreCase(vb);
            }
            case "port" -> {
                String pa = extractInfraPort(a);
                String pb = extractInfraPort(b);
                yield pa.compareTo(pb);
            }
            case "status" -> Boolean.compare(b.alive, a.alive);
            default -> 0;
        };
        return infraSortReversed ? -result : result;
    }

    private List<InfraInfo> sortedInfraInfos() {
        List<InfraInfo> infras = new ArrayList<>(ctx.infraData.get());
        infras.sort(this::infraSortCompare);
        return infras;
    }

    private String sortLabel(String label, String column) {
        return sortLabel(label, column, sort, sortReversed);
    }

    private String infraSortLabel(String label, String column) {
        return sortLabel(label, column, infraSort, infraSortReversed);
    }

    private Style sortStyle(String column) {
        return sortStyle(column, sort);
    }

    private Style infraSortStyle(String column) {
        return sortStyle(column, infraSort);
    }

    private static boolean hasReadmeInSourceDir(IntegrationInfo info) {
        java.nio.file.Path srcDir = FilesBrowser.resolveSourceDirectory(info);
        if (srcDir != null) {
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(srcDir)) {
                return files.anyMatch(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).startsWith("readme"));
            } catch (Exception e) {
                // ignore
            }
        }
        return false;
    }

    @Override
    public String description() {
        return "Running integrations with PID, uptime, and exchange statistics";
    }

    @Override
    public String getHelpText() {
        return """
                # Overview

                The Overview tab shows all running Camel integrations at a glance.
                Select an integration to monitor it in detail on the other tabs.

                ## Integration List

                Each row represents one running Camel integration:

                - **PID** — Process ID of the JVM running this integration
                - **NAME** — Name of the integration (from the route file or application configuration). Example: `camel-demo`, `my-app`
                - **VERSION** — Camel version the integration is running on (e.g., `4.21.0`)
                - **STATUS** — Current lifecycle state: `Running` (processing messages), `Started` (ready), or `Stopped`
                - **AGE** — How long the integration has been running (e.g., `2m30s`, `1h15m`)
                - **MSG/S** or **MSG/M** — Messages processed per second or per minute (current throughput). The rate unit can be configured in settings. This is the overall rate across all routes
                - **TOTAL** — Total number of exchanges (messages) processed since the integration started
                - **FAIL** — Number of exchanges that ended with an unhandled error
                - **INFLIGHT** — Exchanges currently being processed right now. A consistently high inflight count may indicate slow downstream services
                - **SINCE-LAST** — Time since the last exchange activity, shown as up to three values separated by `/`: started/completed/failed. For example, `1s/3s/1m14s` means the last exchange started 1s ago, the last completed 3s ago, and the last failure was 1m14s ago. Values are omitted when there is no activity of that type

                ## Percentile Latency (P50/P95/P99)

                When **Extended** statistics level is enabled, a timing column shows
                percentile latencies instead of MIN/MAX/MEAN:

                - **P50** — Median processing time (50th percentile). Half of all exchanges completed faster than this
                - **P95** — 95th percentile. 95% of exchanges completed faster than this. Useful for SLA monitoring
                - **P99** — 99th percentile. Only 1% of exchanges were slower. Highlights worst-case tail latency

                Percentiles are computed over a sliding window of recent exchanges, making
                them more meaningful than MIN/MAX for understanding real-world performance.
                With very few messages (e.g., 10), P95 and P99 may equal the MAX value since
                there aren't enough samples to differentiate.

                To enable Extended statistics, set `camel.main.load-statistics-enabled = true`
                in your application configuration. Without Extended statistics, the column
                shows MIN/MAX/MEAN instead.

                ## Example Screen

                ```
                 PID   NAME         VERSION    STATUS   AGE    MSG/S  TOTAL  FAIL  INFLIGHT  P50/P95/P99  SINCE-LAST
                 73136 camel-demo   4.21.0     Running  2m30s  1.00   142    0     0           1/10/31    0s
                 64628 my-routes    4.21.0     Running  1h15m  0.50   2850   3     1           2/15/42    2s
                ```

                ## Sparkline Chart

                The sparkline at the bottom shows message throughput over time.
                Each vertical bar represents one sample interval:

                - **Green bars** — successful messages per second
                - **Red bars** — failed messages per second

                This helps you spot traffic patterns, load spikes, and error bursts
                at a glance. A sudden drop in throughput may indicate a problem with
                an external system. A spike in red bars means errors are occurring.

                ## Info Panel

                When an integration is selected, the right panel shows:

                - **Runtime** — Camel runtime type (e.g., `Camel`, `Spring Boot`, `Quarkus`)
                - **Profile** — Active profile (`dev` for development, `prod` for production)
                - **Reload** — Number of times routes have been live-reloaded (useful in `dev` mode where file changes trigger automatic reload)
                - **JVM** — Java version and vendor (e.g., `21.0.5 Azul Systems`)
                - **Uptime** — Integration uptime
                - **Heap** — JVM heap memory usage (used / committed). See the Memory tab for details
                - **Meta** — Metaspace usage (where Java class definitions are stored)
                - **Threads** — JVM thread count
                - **Load avg** — Three comma-separated load averages over 1-minute, 5-minute, and 15-minute windows. These measure message throughput, not CPU usage — similar concept to Unix load average but for Camel exchanges

                ## Dev/Infra Services

                Dev/Infra Services are backing services (databases, message brokers, etc.)
                running in containers via Docker or Podman. This is similar to Quarkus Dev Services
                and Spring Boot Development-time Services.

                For example, if your integration uses Kafka, you can start a Kafka broker
                directly from the TUI using `F2` → `Run Dev/Infra Service...` → select `kafka`.
                The service starts in the background and appears in a separate panel below
                the integrations list with its own columns:

                - **PID** — Container process ID
                - **SERVICE** — Service alias (e.g., `kafka`, `postgres`)
                - **VERSION** — Service version
                - **PORT** — Primary port number
                - **STATUS** — Running or Stopped

                When running an example that requires infra services, they are started
                automatically before the example launches.

                Press `i` to toggle focus between the integrations and infra panels.
                Each panel remembers its own selection. Press `d` while the infra panel
                is focused to toggle a details panel showing the service's connection
                properties (host, port, etc.).

                Sorting applies to whichever panel is focused. Press `s` to cycle through
                the sort columns of the focused panel (integration columns: PID, NAME,
                VERSION, STATUS, TOTAL, FAIL; infra columns: SERVICE, VERSION, PORT, STATUS).

                ## Keys

                - `Up/Down` — select within the focused panel
                - `i` — toggle focus between integrations and infra panels
                - `d` — toggle infra service details panel (when infra panel is focused)
                - `Enter` — view routes for selected integration
                - `s` — cycle sort column (for the focused panel)
                - `S` — reverse sort order
                - `F2` — actions menu (includes theme toggle, go to tab, etc.)
                - `F3` — switch integration
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<IntegrationInfo> integrations = ctx.data.get();
        if (integrations == null || integrations.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Overview");
        JsonArray rows = new JsonArray();
        for (IntegrationInfo info : integrations) {
            JsonObject row = new JsonObject();
            row.put("pid", info.pid);
            row.put("name", info.name);
            row.put("camelVersion", info.camelVersion);
            row.put("platform", info.platform);
            row.put("state", info.state);
            row.put("ready", info.ready);
            row.put("uptime", info.uptime);
            row.put("exchangesTotal", info.exchangesTotal);
            row.put("failed", info.failed);
            row.put("inflight", info.inflight);
            row.put("throughput", info.throughput);
            row.put("routeStarted", info.routeStarted);
            row.put("routeTotal", info.routeTotal);
            row.put("meanTime", info.meanTime);
            row.put("maxTime", info.maxTime);
            row.put("minTime", info.minTime);
            row.put("lastTime", info.lastTime);
            row.put("deltaTime", info.deltaTime);
            if (info.p50Time >= 0) {
                row.put("p50Time", info.p50Time);
                row.put("p95Time", info.p95Time);
                row.put("p99Time", info.p99Time);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", integrations.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    private void renderEmptyState(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        for (String row : TuiHelper.SMALL_CAMEL) {
            lines.add(Line.from(Span.styled("     " + row, Style.EMPTY.fg(Theme.accent()).bold())));
        }
        lines.add(Line.from(Span.styled("     No Active Camel Integrations Found", Theme.title())));
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled(TuiIcons.indent(TuiIcons.TIP) + "How to monitor integrations:", Style.EMPTY.bold())));
        lines.add(Line.from(Span.raw("     Run a route or integration in another terminal window:")));
        lines.add(Line.from(Span.styled("     > camel run my-route.yaml", Theme.success())));
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled(TuiIcons.indent(TuiIcons.CAMEL) + "Or run a bundled example:", Style.EMPTY.bold())));
        lines.add(Line.from(List.of(
                Span.raw("     Press "),
                Span.styled(" F2 ", Theme.hintKey()),
                Span.raw(" to open Actions and select "),
                Span.styled("Run Example", Style.EMPTY.bold()),
                Span.raw("."))));
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled(TuiIcons.indent(TuiIcons.COMPUTER) + "Or use the embedded JLine shell panel:",
                Style.EMPTY.bold())));
        lines.add(Line.from(List.of(
                Span.raw("     Press "),
                Span.styled(" F6 ", Theme.hintKey()),
                Span.raw(" to open the shell and run commands directly, e.g.:"))));
        lines.add(Line.from(Span.styled("     camel> run examples/demo.java", Theme.success())));
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(List.of(
                Span.styled(TuiIcons.indent(TuiIcons.HELP) + "For shortcut keys and documentation, press ", Theme.muted()),
                Span.styled(" ? ", Theme.hintKey()),
                Span.styled(" or ", Theme.muted()),
                Span.styled(" F1 ", Theme.hintKey()),
                Span.styled(".", Theme.muted()))));

        frame.renderWidget(Clear.INSTANCE, area);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder()
                                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(Title.from(Line.from(
                                        Span.styled(" Camel JBang TUI ", Theme.title()))))
                                .build())
                        .build(),
                area);
    }
}
