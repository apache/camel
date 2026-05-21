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
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class CircuitBreakerTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "route", "id", "component", "state" };
    private static final int MAX_CHART_POINTS = 60;

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final Map<String, LinkedList<Long>> cbSuccessHistory;
    private final Map<String, LinkedList<Long>> cbFailHistory;

    private String sort = "route";
    private int sortIndex;
    private boolean sortReversed;

    CircuitBreakerTab(MonitorContext ctx,
                      Map<String, LinkedList<Long>> cbSuccessHistory,
                      Map<String, LinkedList<Long>> cbFailHistory) {
        this.ctx = ctx;
        this.cbSuccessHistory = cbSuccessHistory;
        this.cbFailHistory = cbFailHistory;
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.circuitBreakers.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }
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
        return false;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        tableState.selectNext(info != null ? info.circuitBreakers.size() : 0);
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<CircuitBreakerInfo> sorted = new ArrayList<>(info.circuitBreakers);
        sorted.sort(this::sortCb);

        List<Row> rows = new ArrayList<>();
        for (CircuitBreakerInfo cb : sorted) {
            Style stateStyle = switch (cb.state != null ? cb.state.toLowerCase() : "") {
                case "closed" -> Style.EMPTY.fg(Color.GREEN);
                case "open", "forced_open" -> Style.EMPTY.fg(Color.LIGHT_RED);
                default -> Style.EMPTY.fg(Color.YELLOW);
            };
            String state = cb.state != null ? cb.state : "";
            String pending = cb.bufferedCalls > 0 ? String.valueOf(cb.bufferedCalls) : "";
            String success = cb.successfulCalls > 0 ? String.valueOf(cb.successfulCalls) : "";
            String failed = cb.failedCalls > 0 ? String.valueOf(cb.failedCalls) : "";
            String reject = cb.notPermittedCalls > 0 ? String.valueOf(cb.notPermittedCalls) : "";
            String rate = cb.failureRate >= 0 ? String.format("%.0f%%", cb.failureRate) : "";
            String inflight = String.valueOf(cb.inflight);
            String sinceLast = formatSinceLast(cb.sinceLastStarted, cb.sinceLastSuccess, cb.sinceLastFail);

            rows.add(Row.from(
                    Cell.from(Span.styled(cb.routeId != null ? cb.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(cb.id != null ? cb.id : ""),
                    Cell.from(cb.component != null ? cb.component : ""),
                    Cell.from(Span.styled(state, stateStyle)),
                    rightCell(pending, 8),
                    rightCell(inflight, 8),
                    rightCell(success, 8),
                    rightCell(failed, 8),
                    rightCell(rate, 6),
                    rightCell(reject, 8),
                    Cell.from(sinceLast)));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No circuit breakers", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from("")));
        }

        CircuitBreakerInfo selectedCb = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedCb = sorted.get(sel);
        }
        boolean showDiagram = selectedCb != null;
        List<Rect> chunks = showDiagram
                ? Layout.vertical()
                        .constraints(Constraint.fill(), Constraint.length(25))
                        .split(area)
                : List.of(area);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("ID", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("COMPONENT", "component"), sortStyle("component"))),
                        Cell.from(Span.styled(sortLabel("STATE", "state"), sortStyle("state"))),
                        rightCell("WINDOW", 8, Style.EMPTY.bold()),
                        rightCell("INFLIGHT", 8, Style.EMPTY.bold()),
                        rightCell("SUCCESS", 8, Style.EMPTY.bold()),
                        rightCell("FAIL", 8, Style.EMPTY.bold()),
                        rightCell("RATE%", 6, Style.EMPTY.bold()),
                        rightCell("REJECT", 8, Style.EMPTY.bold()),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(20),
                        Constraint.length(16),
                        Constraint.length(12),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Circuit Breaker ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), tableState);

        if (showDiagram) {
            renderDiagram(frame, chunks.get(1), selectedCb, info.pid);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "navigate");
        hint(spans, "s", "sort");
        hint(spans, "1-9", "tabs");
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private int sortCb(CircuitBreakerInfo a, CircuitBreakerInfo b) {
        int result = switch (sort) {
            case "id" -> compareStr(a.id, b.id);
            case "component" -> compareStr(a.component, b.component);
            case "state" -> compareStr(a.state, b.state);
            default -> compareStr(a.routeId, b.routeId);
        };
        return sortReversed ? -result : result;
    }

    private void renderDiagram(Frame frame, Rect area, CircuitBreakerInfo cb, String pid) {
        List<Rect> hSplit = Layout.horizontal()
                .constraints(Constraint.length(55), Constraint.fill())
                .split(area);

        renderStateDiagram(frame, hSplit.get(0), cb);
        renderChart(frame, hSplit.get(1), cb, pid);
    }

    private void renderStateDiagram(Frame frame, Rect area, CircuitBreakerInfo cb) {
        String state = cb.state != null ? cb.state.toLowerCase() : "";
        boolean isClosed = state.equals("closed");
        boolean isOpen = state.equals("open") || state.equals("forced_open");

        Style closedBox = isClosed ? Style.EMPTY.fg(Color.GREEN).bold() : Style.EMPTY;
        Style openBox = isOpen ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY;
        Style halfOpenBox = !isClosed && !isOpen ? Style.EMPTY.fg(Color.YELLOW).bold() : Style.EMPTY;
        Style lbl = Style.EMPTY.dim();

        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
                Span.raw("   "),
                Span.styled("┌──────────────┐", closedBox),
                Span.raw("              "),
                Span.styled("┌──────────────┐", openBox)));
        lines.add(Line.from(
                Span.raw("   "),
                Span.styled("│    CLOSED    │", closedBox),
                Span.raw("─────────────►"),
                Span.styled("│     OPEN     │", openBox),
                Span.raw("◄─┐")));
        lines.add(Line.from(
                Span.raw("   "),
                Span.styled("│  (allowed)   │", closedBox),
                Span.styled(" failure rate ", lbl),
                Span.styled("│  (rejected)  │", openBox),
                Span.raw("  │")));
        lines.add(Line.from(
                Span.raw("   "),
                Span.styled("└──────", closedBox),
                Span.raw("▲"),
                Span.styled("───────┘", closedBox),
                Span.raw("              "),
                Span.styled("└───────", openBox),
                Span.raw("┬"),
                Span.styled("──────┘", openBox),
                Span.raw("  │")));
        lines.add(Line.from(Span.raw(
                "          │                              │         │")));
        lines.add(Line.from(
                Span.raw("          │"),
                Span.styled(" success", lbl),
                Span.raw("      "),
                Span.styled("wait timeout", lbl),
                Span.raw("    │         │")));
        lines.add(Line.from(Span.raw(
                "          │                              │         │")));
        lines.add(Line.from(
                Span.raw("          │                      "),
                Span.styled("┌───────", halfOpenBox),
                Span.raw("▼"),
                Span.styled("──────┐", halfOpenBox),
                Span.raw("  │")));
        lines.add(Line.from(
                Span.raw("          └──────────────────────"),
                Span.styled("┤  HALF_OPEN   ├", halfOpenBox),
                Span.raw("──┘")));
        lines.add(Line.from(
                Span.raw("                                 "),
                Span.styled("│   (probe)    │", halfOpenBox)));
        lines.add(Line.from(
                Span.raw("                                 "),
                Span.styled("└──────────────┘", halfOpenBox)));

        String title = " ";
        if (cb.id != null && !cb.id.isEmpty()) {
            title += cb.id;
        }
        if (cb.component != null) {
            title += " (" + cb.component + ")";
        }
        title += " ";

        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build(), area);
    }

    private void renderChart(Frame frame, Rect area, CircuitBreakerInfo cb, String pid) {
        String key = pid + "/" + cb.id;

        List<Rect> vSplit = Layout.vertical()
                .constraints(Constraint.length(3), Constraint.fill(), Constraint.length(6))
                .split(area);

        double rate = cb.failureRate >= 0 ? cb.failureRate : 0;
        Style barColor;
        if (rate >= 80) {
            barColor = Style.EMPTY.fg(Color.LIGHT_RED);
        } else if (rate >= 50) {
            barColor = Style.EMPTY.fg(Color.YELLOW);
        } else {
            barColor = Style.EMPTY.fg(Color.GREEN);
        }
        String rateLabel = String.format(" %.0f%%", Math.max(0, cb.failureRate));
        int barWidth = MAX_CHART_POINTS;
        int usable = barWidth - rateLabel.length();
        int filled = Math.max(0, (int) (usable * rate / 100.0));
        int empty = Math.max(0, usable - filled);
        Line barLine = Line.from(
                Span.raw("    "),
                Span.styled("█".repeat(filled), barColor),
                Span.styled(rateLabel, Style.EMPTY.bold()),
                Span.styled("░".repeat(empty), Style.EMPTY.dim()));
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(barLine))
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Failure Rate ").build())
                .build(), vSplit.get(0));

        LinkedList<Long> successHist = cbSuccessHistory.get(key);
        LinkedList<Long> failHist = cbFailHistory.get(key);
        int renderPoints = MAX_CHART_POINTS;
        long[] successArr = new long[renderPoints];
        long[] failArr = new long[renderPoints];
        if (successHist != null) {
            for (int i = 0; i < renderPoints; i++) {
                int idx = successHist.size() - renderPoints + i;
                if (idx >= 0) {
                    successArr[i] = successHist.get(idx);
                }
            }
        }
        if (failHist != null) {
            for (int i = 0; i < renderPoints; i++) {
                int idx = failHist.size() - renderPoints + i;
                if (idx >= 0) {
                    failArr[i] = failHist.get(idx);
                }
            }
        }
        long curSuccess = successArr[renderPoints - 1];
        long curFail = failArr[renderPoints - 1];
        Line chartTitle = Line.from(
                Span.styled("▬", Style.EMPTY.fg(Color.GREEN)),
                Span.raw(String.format(" ok:%-4d ", curSuccess)),
                Span.styled("▬", Style.EMPTY.fg(Color.LIGHT_RED)),
                Span.raw(String.format(" fail:%-4d msg/s", curFail)));
        frame.renderWidget(MirroredSparkline.builder()
                .topData(successArr)
                .bottomData(failArr)
                .topStyle(Style.EMPTY.fg(Color.GREEN))
                .bottomStyle(Style.EMPTY.fg(Color.LIGHT_RED))
                .xLabels("-60s", "-45s", "-30s", "-15s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(Title.from(chartTitle)).build())
                .build(), vSplit.get(1));

        Style dim = Style.EMPTY.dim();
        Line metricsLine1 = Line.from(
                Span.raw(" "),
                Span.styled("total:", dim), Span.raw(cb.total + " "),
                Span.styled("fail:", dim), Span.raw(cb.totalFailed + " "),
                Span.styled("inflight:", dim), Span.raw(cb.inflight + " "),
                Span.styled("reject:", dim), Span.raw(cb.notPermittedCalls + " "),
                Span.styled("mean:", dim), Span.raw(cb.meanTime + "ms "),
                Span.styled("min:", dim), Span.raw(cb.minTime + "ms "),
                Span.styled("max:", dim), Span.raw(cb.maxTime + "ms"));
        String lastSuccess = cb.sinceLastSuccess != null ? cb.sinceLastSuccess : "-";
        String lastFail = cb.sinceLastFail != null ? cb.sinceLastFail : "-";
        Line metricsLine2 = Line.from(
                Span.raw(" "),
                Span.styled("since last", dim),
                Span.raw(" "),
                Span.styled("success:", dim), Span.raw(" " + lastSuccess + " "),
                Span.styled("fail:", dim), Span.raw(" " + lastFail));
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(Line.from(Span.raw("")), metricsLine1, metricsLine2))
                .block(Block.builder().borderType(BorderType.ROUNDED).build())
                .build(), vSplit.get(2));
    }
}
