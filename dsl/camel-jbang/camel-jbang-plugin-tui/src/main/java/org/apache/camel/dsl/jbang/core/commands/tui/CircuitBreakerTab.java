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
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.sparkline.DualSparkline;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class CircuitBreakerTab extends AbstractTableTab {

    private static final int MAX_CHART_POINTS = 300;

    private final Map<String, LinkedList<Long>> cbSuccessHistory;
    private final Map<String, LinkedList<Long>> cbFailHistory;

    CircuitBreakerTab(MonitorContext ctx, MetricsCollector metrics) {
        super(ctx, "route", "id", "component", "state");
        this.cbSuccessHistory = metrics.getCbSuccessHistory();
        this.cbFailHistory = metrics.getCbFailHistory();
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.circuitBreakers.size() : 0;
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.circuitBreakers.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<CircuitBreakerInfo> sorted = new ArrayList<>(info.circuitBreakers);
        sorted.sort(this::sortCb);

        List<Row> rows = new ArrayList<>();
        for (CircuitBreakerInfo cb : sorted) {
            Style stateStyle = switch (cb.state != null ? cb.state.toLowerCase() : "") {
                case "closed" -> Theme.success();
                case "open", "forced_open" -> Theme.error();
                default -> Theme.warning();
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
                    Cell.from(Span.styled(cb.routeId != null ? cb.routeId : "", Style.EMPTY.fg(Theme.accent()))),
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
            rows.add(emptyRow("No circuit breakers", 11));
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
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Circuit Breaker ").build())
                .build();

        lastTableArea = chunks.get(0);
        frame.renderStatefulWidget(table, chunks.get(0), tableState);
        renderScrollbar(frame, info.circuitBreakers.size());

        if (showDiagram) {
            renderDiagram(frame, chunks.get(1), selectedCb, info.pid);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hint(spans, "s", "sort");
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

        Style closedBox = isClosed ? Theme.success().bold() : Style.EMPTY;
        Style openBox = isOpen ? Theme.error().bold() : Style.EMPTY;
        Style halfOpenBox = !isClosed && !isOpen ? Theme.warning().bold() : Style.EMPTY;
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
                Span.raw("─────────────" + TuiIcons.POINTER),
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
                Span.raw(TuiIcons.SORT_UP),
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
                Span.raw(TuiIcons.SORT_DOWN),
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
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
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
            barColor = Theme.error();
        } else if (rate >= 50) {
            barColor = Theme.warning();
        } else {
            barColor = Theme.success();
        }
        String rateLabel = String.format(" %.0f%%", Math.max(0, cb.failureRate));
        int barWidth = Math.max(0, vSplit.get(0).width() - 6);
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
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Failure Rate ").build())
                .build(), vSplit.get(0));

        LinkedList<Long> successHist = cbSuccessHistory.get(key);
        LinkedList<Long> failHist = cbFailHistory.get(key);
        int renderPoints = Math.max(20, (Math.min(MAX_CHART_POINTS, vSplit.get(1).width() - 6) / 20) * 20);
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
                Span.styled("▬", Theme.success()),
                Span.raw(String.format(" ok:%-4d ", curSuccess)),
                Span.styled("▬", Theme.error()),
                Span.raw(String.format(" fail:%-4d msg/s ", curFail)));
        frame.renderWidget(DualSparkline.builder()
                .topData(successArr)
                .bottomData(failArr)
                .topStyle(Theme.success())
                .bottomStyle(Theme.error())
                .showYAxis(true)
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(chartTitle)).build())
                .build(), vSplit.get(1));

        Style dim = Theme.muted();
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
                Span.styled("ok:", dim), Span.raw(" " + lastSuccess + " "),
                Span.styled("fail:", dim), Span.raw(" " + lastFail));
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(Line.from(Span.raw("")), metricsLine1, metricsLine2))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).build())
                .build(), vSplit.get(2));
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.circuitBreakers.isEmpty()) {
            return null;
        }
        List<CircuitBreakerInfo> sorted = new ArrayList<>(info.circuitBreakers);
        sorted.sort(this::sortCb);
        List<String> items = sorted.stream().map(cb -> cb.id != null ? cb.id : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Circuit Breakers");
    }

    @Override
    public String description() {
        return "Circuit breaker state and statistics (Resilience4j)";
    }

    @Override
    public String getHelpText() {
        return """
                # Circuit Breakers

                Circuit breakers protect your integration from cascading failures. When a
                downstream service starts failing, the circuit breaker stops sending
                requests to it, giving it time to recover instead of overwhelming it
                with doomed requests.

                ## How It Works

                Imagine calling a REST API that is down. Without a circuit breaker,
                every message would wait for a connection timeout, creating a backlog.
                With a circuit breaker, after detecting enough failures, requests are
                immediately rejected — fast-failing instead of slow-failing.

                ## State Machine

                ```
                CLOSED ──(failures exceed threshold)──> OPEN
                   ^                                      |
                   |                              (wait timeout)
                   |                                      v
                   +────(success in trial)──── HALF_OPEN
                ```

                - **CLOSED** (normal operation): All requests flow through. Failures
                  are counted in a sliding window. When the failure rate exceeds the
                  configured threshold (e.g., 50%), the circuit trips to OPEN.

                - **OPEN** (circuit tripped): All requests are immediately rejected
                  with a fallback response — no call is made to the failing service.
                  After a wait duration (e.g., 20 seconds), the circuit moves to
                  HALF_OPEN to test if the service has recovered.

                - **HALF_OPEN** (testing recovery): A limited number of trial requests
                  are allowed through. If they succeed, the circuit closes and normal
                  operation resumes. If they fail, the circuit opens again for another
                  wait period.

                ## Table Columns

                - **ROUTE** — Route containing this circuit breaker
                - **ID** — Processor ID of the circuit breaker node in the route
                - **COMPONENT** — Implementation library: `resilience4j` (most common) or `fault-tolerance` (MicroProfile)
                - **STATE** — Current breaker state: `CLOSED` (green, normal), `OPEN` (red, rejecting), or `HALF_OPEN` (yellow, testing)
                - **WINDOW** — Number of calls in the current sliding window used to calculate the failure rate
                - **INFLIGHT** — Calls currently in progress inside the circuit breaker
                - **SUCCESS** — Total number of successful calls
                - **FAIL** — Total number of failed calls (exceptions thrown by the protected code)
                - **RATE%** — Current failure rate percentage in the sliding window. When this exceeds the configured threshold, the circuit trips to OPEN
                - **REJECT** — Calls rejected because the circuit is OPEN. These calls never reach the downstream service — they fail fast with a fallback
                - **SINCE-LAST** — Time since the last circuit breaker activity, shown as up to two values separated by `/`: success/failed (e.g., `3s/1m14s`). Values are omitted when there is no activity of that type

                ## Example Screen

                ```
                 ROUTE    ID            COMPONENT     STATE   WINDOW  INFLIGHT  SUCCESS  FAIL  RATE%  REJECT
                 route1   circuitBrk1   resilience4j  CLOSED  10      0         450      5     1.0%   0
                 route2   circuitBrk2   resilience4j  OPEN    10      0         100      8     80.0%  25
                ```

                In this example, `route1` is healthy with a 1% failure rate. `route2`
                has tripped open with an 80% failure rate — 25 calls have been rejected
                since it opened. The circuit will stay open until the wait timeout
                expires, then try a few test calls in HALF_OPEN state.

                ## Detail View

                The bottom panel shows when a circuit breaker is selected:

                - **Failure rate gauge**: Visual bar from 0% to 100%, colored green
                  (low risk), yellow (approaching threshold), or red (above threshold)
                - **Sparkline chart**: Mirrored view showing successful calls (green,
                  upward) vs failed calls (red, downward) over time
                - **Metrics**: Detailed counts for total, fail, inflight, reject,
                  and timing statistics (mean/min/max processing time)

                ## Configuration Tips

                Common Resilience4j settings you can tune in your route:

                - `minimumNumberOfCalls` — Minimum calls before failure rate is
                  calculated (default: 100). Lower this for faster detection
                - `waitDurationInOpenState` — How long to wait before testing
                  recovery (default: 60s)
                - `failureRateThreshold` — Percentage that triggers the circuit
                  to open (default: 50%)

                ## Keys

                - `Up/Down` — select circuit breaker
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "CircuitBreaker");
        JsonArray rows = new JsonArray();
        List<CircuitBreakerInfo> sorted = new ArrayList<>(info.circuitBreakers);
        sorted.sort(this::sortCb);
        for (CircuitBreakerInfo cb : sorted) {
            JsonObject row = new JsonObject();
            row.put("routeId", cb.routeId);
            row.put("id", cb.id);
            row.put("component", cb.component);
            row.put("state", cb.state);
            row.put("bufferedCalls", cb.bufferedCalls);
            row.put("successfulCalls", cb.successfulCalls);
            row.put("failedCalls", cb.failedCalls);
            row.put("notPermittedCalls", cb.notPermittedCalls);
            row.put("failureRate", cb.failureRate);
            row.put("total", cb.total);
            row.put("totalFailed", cb.totalFailed);
            row.put("meanTime", cb.meanTime);
            row.put("minTime", cb.minTime);
            row.put("maxTime", cb.maxTime);
            row.put("inflight", cb.inflight);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.circuitBreakers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
