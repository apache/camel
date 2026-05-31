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

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;
import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

class OverviewTab implements MonitorTab {

    private static final long VANISH_DURATION_MS = 6000;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final String[] SORT_COLUMNS = { "pid", "name", "version", "status", "total", "fail" };

    static final int CHART_ALL = 0;
    static final int CHART_SINGLE = 1;
    static final int CHART_OFF = 2;

    private final MonitorContext ctx;
    private final Map<String, LinkedList<Long>> throughputHistory;
    private final Map<String, LinkedList<Long>> failedHistory;
    private final Map<String, LoadAvg> cpuLoadAvg;
    private final Runnable onPidChanged;

    final TableState tableState = new TableState();
    int dividerIndex = -1;
    int chartMode = CHART_SINGLE;

    private String sort = "name";
    private int sortIndex = 1;
    private boolean sortReversed;

    OverviewTab(
                MonitorContext ctx,
                Map<String, LinkedList<Long>> throughputHistory,
                Map<String, LinkedList<Long>> failedHistory,
                Map<String, LoadAvg> cpuLoadAvg,
                Runnable onPidChanged) {
        this.ctx = ctx;
        this.throughputHistory = throughputHistory;
        this.failedHistory = failedHistory;
        this.cpuLoadAvg = cpuLoadAvg;
        this.onPidChanged = onPidChanged;
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
            chartMode = (chartMode + 1) % 3;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
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

        if (ctx.selectedPid != null) {
            for (int i = 0; i < infos.size(); i++) {
                if (ctx.selectedPid.equals(infos.get(i).pid)) {
                    tableState.select(i);
                    break;
                }
            }
            for (int i = 0; i < infraInfos.size(); i++) {
                if (ctx.selectedPid.equals(infraInfos.get(i).pid)) {
                    int tableIndex = integrationCount + (dividerIndex >= 0 ? 1 : 0) + i;
                    tableState.select(tableIndex);
                    break;
                }
            }
        }

        boolean hasSparkline = chartMode != CHART_OFF && !throughputHistory.isEmpty() && !ctx.isInfraSelected();
        boolean showInfoPanel = ctx.isInfraSelected() && ctx.findSelectedInfra() != null && !hasSparkline;
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.fill());
        if (hasSparkline) {
            constraints.add(Constraint.length(14));
        } else if (showInfoPanel) {
            constraints.add(Constraint.length(10));
        }
        List<Rect> chunks = Layout.vertical()
                .constraints(constraints)
                .split(area);

        List<Row> rows = new ArrayList<>();
        for (IntegrationInfo info : infos) {
            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));

                String vanishName = "🐪 " + (info.name != null ? info.name : "");
                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(vanishName, dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("✖ Stopped", Style.EMPTY.fg(Color.LIGHT_RED).dim())),
                        Cell.from(Span.styled(info.ago != null ? info.ago : "", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))));
            } else {
                Style statusStyle = switch (extractState(info.state)) {
                    case "Started", "Running" -> Style.EMPTY.fg(Color.GREEN);
                    case "Stopped" -> Style.EMPTY.fg(Color.LIGHT_RED);
                    default -> Style.EMPTY.fg(Color.YELLOW);
                };

                Style failStyle = info.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY;

                String sinceLastDisplay = formatSinceLast(info);

                String nameText = "🐪 " + (info.name != null ? info.name : "");
                Line nameLine = info.devMode
                        ? Line.from(
                                Span.styled(nameText, Style.EMPTY.fg(Color.CYAN)),
                                Span.styled(" [dev]", Style.EMPTY.fg(Color.YELLOW).dim()))
                        : Line.from(Span.styled(nameText, Style.EMPTY.fg(Color.CYAN)));
                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(nameLine),
                        Cell.from(info.camelVersion != null ? info.camelVersion : ""),
                        centerCell(info.ready != null ? info.ready : "", 5),
                        Cell.from(Span.styled(extractState(info.state), statusStyle)),
                        Cell.from(info.ago != null ? info.ago : ""),
                        rightCell(info.routeStarted + "/" + info.routeTotal, 7),
                        rightCell(info.throughput != null ? info.throughput : "", 8),
                        rightCell(String.valueOf(info.exchangesTotal), 8),
                        rightCell(String.valueOf(info.failed), 6, failStyle),
                        rightCell(String.valueOf(info.inflight), 8),
                        Cell.from(sinceLastDisplay)));
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
                    Cell.from(Span.styled("─── Infra Services ───", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from("")));
        }

        for (InfraInfo info : infraInfos) {
            if (info.vanishing) {
                long elapsed = System.currentTimeMillis() - info.vanishStart;
                float fade = 1.0f - Math.min(1.0f, (float) elapsed / VANISH_DURATION_MS);
                int gray = (int) (100 * fade);
                Style dimStyle = Style.EMPTY.fg(Color.indexed(232 + Math.min(gray / 4, 23)));
                String vanishAlias = "🔧  " + info.alias;
                rows.add(Row.from(
                        Cell.from(Span.styled(info.pid, dimStyle)),
                        Cell.from(Span.styled(vanishAlias, dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("✖ Stopped", Style.EMPTY.fg(Color.LIGHT_RED).dim())),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle)),
                        Cell.from(Span.styled("", dimStyle))));
            } else {
                Style statusStyle = info.alive ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.fg(Color.LIGHT_RED);
                String statusText = info.alive ? "Running" : "Stopped";
                String infraAlias = "🔧  " + info.alias;
                String version = info.serviceVersion != null ? info.serviceVersion : "";
                rows.add(Row.from(
                        Cell.from(info.pid),
                        Cell.from(Span.styled(infraAlias, Style.EMPTY.fg(Color.MAGENTA))),
                        Cell.from(version),
                        Cell.from(""),
                        Cell.from(Span.styled(statusText, statusStyle)),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from(""),
                        Cell.from("")));
            }
        }

        Style overviewHighlight = Style.EMPTY.fg(Color.WHITE).bold().onBlue();
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
                        Constraint.length(12))
                .highlightStyle(overviewHighlight)
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Overview ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), tableState);

        if (hasSparkline && chunks.size() > 1) {
            Rect chartTotalArea = chunks.get(chunks.size() - 1);

            List<Rect> chartHSplit = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(30))
                    .split(chartTotalArea);
            Rect chartArea = chartHSplit.get(0);
            Rect infoArea = chartHSplit.get(1);

            List<Rect> vChunks = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(chartArea);

            List<Rect> hChunks = Layout.horizontal()
                    .constraints(Constraint.length(4), Constraint.fill())
                    .split(vChunks.get(0));

            Rect barChartArea = hChunks.get(1);

            int innerBarCols = Math.max(2, barChartArea.width() - 2);
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

            long maxTp = 0;
            for (long v : mergedTotal) {
                maxTp = Math.max(maxTp, v);
            }
            long curTp = mergedTotal[renderPoints - 1];
            long curFailed = mergedFailed[renderPoints - 1];
            long curOk = Math.max(0, curTp - curFailed);

            Line titleLine;
            if (chartMode == CHART_SINGLE && ctx.selectedPid != null) {
                IntegrationInfo chartSel = ctx.findSelectedIntegration();
                String chartName = chartSel != null ? TuiHelper.truncate(chartSel.name, 12) : ctx.selectedPid;
                titleLine = Line.from(
                        Span.raw(" ["),
                        Span.styled(chartName, Style.EMPTY.fg(Color.YELLOW)),
                        Span.raw(String.format("] Throughput: %d msg/s  ", curTp)),
                        Span.styled("■", Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))),
                        Span.raw(String.format(" ok:%d  ", curOk)),
                        Span.styled("■", Style.EMPTY.fg(Color.RED)),
                        Span.raw(String.format(" fail:%d ", curFailed)));
            } else {
                titleLine = Line.from(
                        Span.raw(String.format(" [All] Throughput: %d msg/s  ", curTp)),
                        Span.styled("■", Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))),
                        Span.raw(String.format(" ok:%d  ", curOk)),
                        Span.styled("■", Style.EMPTY.fg(Color.RED)),
                        Span.raw(String.format(" fail:%d ", curFailed)));
            }

            List<BarGroup> groups = new ArrayList<>();
            for (int i = 0; i < renderPoints; i++) {
                long failed = Math.min(mergedFailed[i], mergedTotal[i]);
                long ok = Math.max(0, mergedTotal[i] - failed);
                groups.add(BarGroup.of(
                        Bar.builder().value(ok).textValue("").style(Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN)))
                                .build(),
                        Bar.builder().value(failed).textValue("").style(Style.EMPTY.fg(Color.RED)).build()));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .max(maxTp > 0 ? maxTp + 2 : 2)
                    .barWidth(1)
                    .barGap(0)
                    .groupGap(0)
                    .block(Block.builder().borderType(BorderType.ROUNDED)
                            .title(Title.from(titleLine)).build())
                    .build();

            frame.renderWidget(barChart, barChartArea);

            int barRows = vChunks.get(0).height() - 2;
            List<Line> yLines = new ArrayList<>();
            Style dimStyle = Style.EMPTY.dim();
            for (int row = 0; row < vChunks.get(0).height(); row++) {
                int barRow = row - 1;
                if (barRow == 0) {
                    yLines.add(Line.from(Span.styled(String.format("%3d", maxTp), dimStyle)));
                } else if (barRows > 4 && barRow == barRows / 2) {
                    yLines.add(Line.from(Span.styled(String.format("%3d", maxTp / 2), dimStyle)));
                } else if (barRow == barRows - 1) {
                    yLines.add(Line.from(Span.styled("  0", dimStyle)));
                } else {
                    yLines.add(Line.from(""));
                }
            }
            frame.renderWidget(Paragraph.builder().text(Text.from(yLines)).build(), hChunks.get(0));

            if (!vChunks.get(1).isEmpty()) {
                int barInnerStartX = barChartArea.x() + 1;
                int xAxisY = vChunks.get(1).y();
                int[][] markerIndices = {
                        { 0, renderPoints },
                        { renderPoints / 4, renderPoints - renderPoints / 4 },
                        { renderPoints / 2, renderPoints / 2 },
                        { 3 * renderPoints / 4, renderPoints / 4 },
                        { renderPoints - 1, 0 }
                };
                for (int[] m : markerIndices) {
                    int groupIdx = m[0];
                    int secsAgo = m[1];
                    String label = secsAgo == 0 ? "now" : "-" + secsAgo + "s";
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
        Block infoBlock = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(infoBlock, area);
        Rect inner = infoBlock.inner(area);
        List<Line> lines = new ArrayList<>();
        Style dim = Style.EMPTY.dim();
        if (sel != null) {
            if (sel.platform != null) {
                String plat = sel.platformVersion != null
                        ? sel.platform + " v" + sel.platformVersion
                        : sel.platform;
                lines.add(Line.from(
                        Span.styled("Runtime: ", dim),
                        Span.raw(TuiHelper.truncate(plat, inner.width() - 9))));
            }
            if (sel.camelVersion != null) {
                lines.add(Line.from(
                        Span.styled("Version: ", dim),
                        Span.raw(TuiHelper.truncate(sel.camelVersion, inner.width() - 9))));
            }
            if (sel.profile != null || sel.reloaded > 0) {
                List<Span> profileSpans = new ArrayList<>();
                if (sel.profile != null) {
                    profileSpans.add(Span.styled("Profile: ", dim));
                    profileSpans.add(Span.raw(sel.profile));
                }
                if (sel.reloaded > 0) {
                    if (!profileSpans.isEmpty()) {
                        profileSpans.add(Span.raw("    "));
                    }
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
                lines.add(Line.from(
                        Span.styled("      ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVendor, inner.width() - 6))));
            }
            if (sel.javaVmName != null) {
                lines.add(Line.from(
                        Span.styled("      ", dim),
                        Span.raw(TuiHelper.truncate(sel.javaVmName, inner.width() - 6))));
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
        } else {
            lines.add(Line.from(Span.raw("-")));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    private void renderInfraInfoPanel(Frame frame, Rect area, InfraInfo infra) {
        Block infoBlock = Block.builder().borderType(BorderType.ROUNDED).build();
        frame.renderWidget(infoBlock, area);
        Rect inner = infoBlock.inner(area);
        List<Line> lines = new ArrayList<>();
        Style dim = Style.EMPTY.dim();
        lines.add(Line.from(
                Span.styled("Service: ", dim),
                Span.styled(infra.alias, Style.EMPTY.fg(Color.MAGENTA))));
        lines.add(Line.from(Span.raw("")));
        for (Map.Entry<String, Object> e : infra.properties.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("get") && key.length() > 3) {
                key = key.substring(3);
            }
            String value = String.valueOf(e.getValue());
            lines.add(Line.from(
                    Span.styled(key + ": ", dim),
                    Span.raw(TuiHelper.truncate(value, inner.width() - key.length() - 2))));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), inner);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "q", "quit");
        if (ctx.selectedPid != null) {
            hint(spans, "Esc", "unselect");
        }
        hint(spans, "↑↓", "navigate");
        if (!ctx.isInfraSelected()) {
            hint(spans, "s", "sort");
            hint(spans, "a", "chart " + switch (chartMode) {
                case CHART_ALL -> "[all]";
                case CHART_SINGLE -> "[single]";
                default -> "[off]";
            });
        }
        hint(spans, "1-9", "tabs");
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
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }
}
