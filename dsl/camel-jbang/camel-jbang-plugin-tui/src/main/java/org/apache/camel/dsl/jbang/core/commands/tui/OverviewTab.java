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
    int dividerIndex = -1;
    int chartMode = CHART_SINGLE;
    private int bottomPanelHeight = 16;
    private final DragSplit vSplit = new DragSplit();

    private String sort = "name";
    private int sortIndex = 1;
    private boolean sortReversed;

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
            sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
            sort = SORT_COLUMNS[sortIndex];
            sortReversed = false;
            return true;
        }
        if (ke.isChar('S')) {
            sortReversed = !sortReversed;
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
        Integer before = tableState.selected();
        if (handleTableClick(me, lastTableArea, tableState, totalRows())) {
            // The Dev/Infra divider row is not selectable; restore the prior selection when it is clicked.
            Integer sel = tableState.selected();
            if (sel != null && dividerIndex >= 0 && sel == dividerIndex) {
                if (before != null) {
                    tableState.select(before);
                }
            } else {
                syncSelectedPid();
            }
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
        Integer sel = tableState.selected();
        if (sel != null && dividerIndex >= 0 && sel == dividerIndex) {
            tableState.selectPrevious();
        }
        syncSelectedPid();
    }

    @Override
    public void navigateDown() {
        int totalRows = totalRows();
        tableState.selectNext(totalRows);
        Integer sel = tableState.selected();
        if (sel != null && dividerIndex >= 0 && sel == dividerIndex) {
            tableState.selectNext(totalRows);
        }
        syncSelectedPid();
    }

    @Override
    public void render(Frame frame, Rect area) {
        List<IntegrationInfo> infos = sortedInfos();
        List<InfraInfo> infraInfos = ctx.infraData.get();

        int integrationCount = infos.size();
        int infraCount = infraInfos.size();
        dividerIndex = infraCount > 0 ? integrationCount : -1;

        if (integrationCount == 0 && infraCount == 0) {
            renderEmptyState(frame, area);
            return;
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
                    int tableIndex = integrationCount + 1 + i;
                    tableState.select(tableIndex);
                    break;
                }
            }
        }

        boolean hasSparkline = chartMode != CHART_OFF && !throughputHistory.isEmpty() && !ctx.isInfraSelected()
                && ctx.shellPercent < 50;
        InfraInfo infraSel = ctx.isInfraSelected() ? ctx.findSelectedInfra() : null;
        boolean showInfoPanel = infraSel != null && !hasSparkline;
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.fill());
        if (hasSparkline) {
            bottomPanelHeight = Math.max(5, Math.min(bottomPanelHeight, area.height() - 5));
            constraints.add(Constraint.length(bottomPanelHeight));
        } else if (showInfoPanel) {
            int panelH = countInfraLines(infraSel) + 2;
            bottomPanelHeight = Math.max(5, Math.min(Math.min(panelH, bottomPanelHeight), area.height() / 2));
            constraints.add(Constraint.length(bottomPanelHeight));
        }
        List<Rect> chunks = Layout.vertical()
                .constraints(constraints)
                .split(area);
        if (chunks.size() > 1) {
            vSplit.setBorderPos(chunks.get(1).y());
        } else {
            vSplit.clearBorderPos();
        }

        List<Row> rows = new ArrayList<>();
        int rowIndex = 0;
        for (IntegrationInfo info : infos) {
            boolean isEven = (rowIndex++ % 2 == 0);
            // Zebra striping at the row level so the selection highlight (patched on top) always wins.
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
                        Cell.from(Span.styled(info.ago != null ? info.ago : "", dimStyle)),
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

                String sinceLastDisplay = formatSinceLast(info);

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
                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(nameLine),
                        Cell.from(info.camelVersion != null ? info.camelVersion : ""),
                        centerCell(info.ready != null ? info.ready : "", 5),
                        Cell.from(Span.styled(stateText, statusStyle)),
                        Cell.from(info.ago != null ? info.ago : ""),
                        rightCell(info.routeStarted + "/" + info.routeTotal, 7),
                        rightCell(throughputDisplay != null ? throughputDisplay : "", 8),
                        rightCell(String.valueOf(info.exchangesTotal), 8),
                        rightCell(String.valueOf(info.failed), 6, failStyle),
                        rightCell(String.valueOf(info.inflight), 8),
                        Cell.from(sinceLastDisplay)).style(rowBg));
            }
        }

        Row header = Row.from(
                Cell.from(Span.styled(sortLabel("PID", "pid"), sortStyle("pid"))),
                Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                Cell.from(Span.styled(sortLabel("VERSION", "version"), sortStyle("version"))),
                centerCell("READY", 5, Style.EMPTY.bold()),
                Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                rightCell("ROUTE", 7, Style.EMPTY.bold()),
                rightCell("MSG/S", 8, Style.EMPTY.bold()),
                rightCell(sortLabel("TOTAL", "total"), 8, sortStyle("total")),
                rightCell(sortLabel("FAIL", "fail"), 6, sortStyle("fail")),
                rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())));

        if (dividerIndex >= 0) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("─── Dev/Infra Services ───", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from("")));
        }

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
                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(vanishAlias, dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled(TuiIcons.STOPPED + " Stopped", Theme.error().dim())),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))).style(rowBg));
            } else {
                String statusText = info.alive ? "Running" : "Stopped";
                String infraAlias = TuiIcons.INFRA + "  " + info.alias;
                String version = info.serviceVersion != null ? info.serviceVersion : "";
                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(infraAlias, Theme.notice())),
                        Cell.from(version),
                        Cell.from(""),
                        Cell.from(Span.styled(statusText, statusStyle)),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from("")).style(rowBg));
            }
        }

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(16),
                        Constraint.length(5),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(7),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(13))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Overview ").build())
                .build();

        lastTableArea = chunks.get(0);
        frame.renderStatefulWidget(table, chunks.get(0), tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, tableScrollState, totalRows());

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
            String chartPid = (chartMode == CHART_SINGLE && ctx.selectedPid != null) ? ctx.selectedPid : null;
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
            if (chartMode == CHART_SINGLE && ctx.selectedPid != null) {
                IntegrationInfo chartSel = ctx.findSelectedIntegration();
                String chartName = chartSel != null ? TuiHelper.truncate(chartSel.name, 12)
                        : ctx.selectedPid != null ? ctx.selectedPid : "?";
                titleLine = Line.from(
                        Span.raw(" ["),
                        Span.styled(chartName, Theme.label().bold()),
                        Span.raw(String.format("] Throughput: %s msg/s  ", curTpFmt)),
                        Span.styled("■", Theme.success()),
                        Span.raw(String.format(" ok:%s  ", curOkFmt)),
                        Span.styled("■", Theme.error()),
                        Span.raw(String.format(" fail:%s ", curFailFmt)));
            } else {
                titleLine = Line.from(
                        Span.raw(String.format(" [All] Throughput: %s msg/s  ", curTpFmt)),
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
        } else if (showInfoPanel) {
            renderInfoPanel(frame, chunks.get(chunks.size() - 1));
        }
    }

    private void renderInfoPanel(Frame frame, Rect area) {
        InfraInfo infraSel = ctx.findSelectedInfra();
        if (infraSel != null) {
            renderInfraInfoPanel(frame, area, infraSel);
            return;
        }

        IntegrationInfo sel = ctx.findSelectedIntegration();
        if (sel == null) {
            List<IntegrationInfo> active = ctx.data.get().stream().filter(i -> !i.vanishing).toList();
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

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "q", "quit");
        if (ctx.selectedPid != null) {
            hint(spans, "Esc", "unselect");
        }
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        if (!ctx.isInfraSelected()) {
            hint(spans, "s", "sort");
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
        infos.sort(this::sortCompare);
        return infos;
    }

    void selectCurrentIntegration() {
        if (ctx.selectedPid != null) {
            if (ctx.findSelectedIntegration() != null || ctx.findSelectedInfra() != null) {
                return;
            }
            ctx.selectedPid = null;
        }
        List<IntegrationInfo> infos = sortedInfos();
        List<InfraInfo> infras = ctx.infraData.get();
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0) {
            String pid = indexToPid(infos, infras, sel);
            if (pid != null) {
                ctx.selectedPid = pid;
            }
        } else if (infos.size() == 1) {
            ctx.selectedPid = infos.get(0).pid;
        }
    }

    int totalRows() {
        int integrations = sortedInfos().size();
        int infra = ctx.infraData.get().size();
        return integrations + (infra > 0 ? 1 : 0) + infra;
    }

    private void syncSelectedPid() {
        List<IntegrationInfo> infos = sortedInfos();
        List<InfraInfo> infras = ctx.infraData.get();
        Integer sel = tableState.selected();
        String newPid = null;
        if (sel != null && sel >= 0) {
            newPid = indexToPid(infos, infras, sel);
        }
        if (newPid == null && infos.size() == 1) {
            newPid = infos.get(0).pid;
        }
        if (newPid != null && !newPid.equals(ctx.selectedPid)) {
            ctx.selectedPid = newPid;
            ctx.lastSelectedName = null;
            onPidChanged.run();
        }
    }

    String indexToPid(List<IntegrationInfo> infos, List<InfraInfo> infras, int index) {
        if (index < infos.size()) {
            return infos.get(index).pid;
        }
        int infraIndex = index - infos.size() - (dividerIndex >= 0 ? 1 : 0);
        if (infraIndex >= 0 && infraIndex < infras.size()) {
            return infras.get(infraIndex).pid;
        }
        return null;
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

    private String sortLabel(String label, String column) {
        return sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return sortStyle(column, sort);
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
                - **MSG/S** — Messages processed per second (current throughput). This is the overall rate across all routes
                - **TOTAL** — Total number of exchanges (messages) processed since the integration started
                - **FAIL** — Number of exchanges that ended with an unhandled error
                - **INFLIGHT** — Exchanges currently being processed right now. A consistently high inflight count may indicate slow downstream services
                - **SINCE-LAST** — Time since the last exchange activity, shown as up to three values separated by `/`: started/completed/failed. For example, `1s/3s/1m14s` means the last exchange started 1s ago, the last completed 3s ago, and the last failure was 1m14s ago. Values are omitted when there is no activity of that type

                ## Example Screen

                ```
                 PID   NAME         VERSION    STATUS   AGE    MSG/S  TOTAL  FAIL  INFLIGHT  SINCE-LAST
                 73136 camel-demo   4.21.0     Running  2m30s  1.00   142    0     0         0s
                 64628 my-routes    4.21.0     Running  1h15m  0.50   2850   3     1         2s
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
                The service starts in the background and appears below the integrations list,
                separated by a divider line.

                When running an example that requires infra services, they are started
                automatically before the example launches.

                Selecting an infra service in the list shows its connection properties
                (host, port, etc.) in the info panel on the right.

                ## Keys

                - `Up/Down` — select integration
                - `Enter` — view routes for selected integration
                - `s` — cycle sort column
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
