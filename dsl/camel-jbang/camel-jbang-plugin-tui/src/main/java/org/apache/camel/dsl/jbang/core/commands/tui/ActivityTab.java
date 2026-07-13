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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

class ActivityTab extends AbstractTableTab {

    private final ScrollbarState detailScrollState = new ScrollbarState();
    private int detailScroll;
    private int detailHScroll;
    private boolean wordWrap = true;
    private boolean paused;
    private List<ActivityEntry> pausedSnapshot;
    private int timeFilterIndex;
    private static final String[] TIME_FILTERS = { "all", "1m", "5m", "15m", "30m", "1h" };
    private static final long[] TIME_FILTER_MILLIS = { 0, 60_000, 300_000, 900_000, 1_800_000, 3_600_000 };

    ActivityTab(MonitorContext ctx) {
        super(ctx, "exchange", "route", "elapsed", "since");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return 0;
        }
        return filteredActivity(info).size();
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.activity.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isConfirm()) {
            return true;
        }
        if (ke.isChar('w')) {
            wordWrap = !wordWrap;
            return true;
        }
        if (ke.isChar(' ')) {
            paused = !paused;
            if (paused) {
                IntegrationInfo info = ctx.findSelectedIntegration();
                pausedSnapshot = info != null ? new ArrayList<>(info.activity) : List.of();
            } else {
                pausedSnapshot = null;
            }
            return true;
        }
        if (ke.isChar('t')) {
            timeFilterIndex = (timeFilterIndex + 1) % TIME_FILTERS.length;
            return true;
        }
        if (ke.isPageUp()) {
            detailScroll = Math.max(0, detailScroll - 10);
            return true;
        }
        if (ke.isPageDown()) {
            detailScroll += 10;
            return true;
        }
        if (ke.isLeft()) {
            detailHScroll = Math.max(0, detailHScroll - 5);
            return true;
        }
        if (ke.isRight()) {
            detailHScroll += 5;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        detailScroll = 0;
        super.navigateUp();
    }

    @Override
    public void navigateDown() {
        detailScroll = 0;
        super.navigateDown();
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<ActivityEntry> sorted = filteredActivity(info);

        List<Row> rows = new ArrayList<>();
        for (ActivityEntry ae : sorted) {
            String ago = ae.timestamp > 0
                    ? TimeUtils.printSince(ae.timestamp) : "";
            String status = ae.failed ? "FAILED" : "OK";
            Style statusStyle = ae.failed ? Theme.error() : Theme.success();
            String elapsed = ae.elapsed + "ms";

            String sends = ae.endpointSends.isEmpty() ? "" : String.valueOf(ae.endpointSends.size());

            rows.add(Row
                    .from(
                            Cell.from(ae.exchangeId != null ? ae.exchangeId : ""),
                            Cell.from(Span.styled(ae.routeId != null ? ae.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                            Cell.from(Span.styled(status, statusStyle)),
                            Cell.from(elapsed),
                            Cell.from(sends),
                            Cell.from(ago),
                            Cell.from(ae.fromEndpointUri != null ? ae.fromEndpointUri : "")));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No activity captured", 7));
        }

        ActivityEntry selectedEntry = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedEntry = sorted.get(sel);
        }
        boolean showDetail = selectedEntry != null;

        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.length(4));
        if (showDetail) {
            constraints.add(Constraint.length(13));
            constraints.add(Constraint.fill());
        } else {
            constraints.add(Constraint.fill());
        }
        List<Rect> chunks = Layout.vertical()
                .constraints(constraints)
                .split(area);

        renderStatsPanel(frame, chunks.get(0), sorted);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("EXCHANGE", "exchange"), sortStyle("exchange"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("ELAPSED", "elapsed"), sortStyle("elapsed"))),
                        Cell.from(Span.styled("SENDS", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("SINCE", "since"), sortStyle("since"))),
                        Cell.from(Span.styled("ENDPOINT", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(38),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(7),
                        Constraint.length(8),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Activity [" + sorted.size() + "] ").build())
                .build();

        lastTableArea = chunks.get(1);
        frame.renderStatefulWidget(table, chunks.get(1), tableState);
        renderScrollbar(frame, sorted.size());

        if (showDetail) {
            renderDetail(frame, chunks.get(2), selectedEntry);
        }
    }

    private void renderStatsPanel(Frame frame, Rect area, List<ActivityEntry> entries) {
        int total = entries.size();
        int failed = 0;
        long maxElapsed = 0;
        long oldestTs = Long.MAX_VALUE;
        long newestTs = 0;
        long[] elapsedValues = new long[total];

        for (int i = 0; i < total; i++) {
            ActivityEntry ae = entries.get(i);
            elapsedValues[i] = ae.elapsed;
            if (ae.failed) {
                failed++;
            }
            if (ae.elapsed > maxElapsed) {
                maxElapsed = ae.elapsed;
            }
            if (ae.timestamp > 0 && ae.timestamp < oldestTs) {
                oldestTs = ae.timestamp;
            }
            if (ae.timestamp > newestTs) {
                newestTs = ae.timestamp;
            }
        }

        Arrays.sort(elapsedValues);
        long p50 = total > 0 ? elapsedValues[Math.min((int) (total * 0.50), total - 1)] : 0;
        long p95 = total > 0 ? elapsedValues[Math.min((int) (total * 0.95), total - 1)] : 0;

        String errorRate = total > 0
                ? String.format(Locale.US, "%.1f%%", (failed * 100.0) / total) : "0%";
        String rate = "";
        if (oldestTs < Long.MAX_VALUE && newestTs > oldestTs) {
            double minutes = (newestTs - oldestTs) / 60_000.0;
            if (minutes > 0) {
                rate = String.format(Locale.US, "%.1f/min", total / minutes);
            }
        }

        int sends = entries.stream().mapToInt(ae -> ae.endpointSends.size()).sum();

        Style dim = Theme.muted();
        List<Line> lines = new ArrayList<>();

        lines.add(Line.from(
                Span.styled(" Total: ", dim), Span.raw(String.valueOf(total)),
                Span.styled("   OK: ", dim), Span.styled(String.valueOf(total - failed), Theme.success()),
                Span.styled("   Failed: ", dim),
                Span.styled(failed > 0 ? failed + " (" + errorRate + ")" : "0", failed > 0 ? Theme.error() : Style.EMPTY),
                Span.styled("   Rate: ", dim), Span.raw(rate),
                Span.styled("   Sends: ", dim), Span.raw(String.valueOf(sends))));

        lines.add(Line.from(
                Span.styled(" p50: ", dim), Span.raw(p50 + "ms"),
                Span.styled("   p95: ", dim),
                Span.styled(p95 + "ms", TuiHelper.topTimeStyle(p95)),
                Span.styled("   Max: ", dim),
                Span.styled(maxElapsed + "ms", TuiHelper.topTimeStyle(maxElapsed)),
                oldestTs < Long.MAX_VALUE
                        ? Span.styled("   Window: ", dim)
                        : Span.raw(""),
                oldestTs < Long.MAX_VALUE
                        ? Span.raw(TimeUtils.printSince(oldestTs)
                                   + " ... " + TimeUtils.printSince(newestTs))
                        : Span.raw("")));

        String title = paused
                ? " Summary (PAUSED, last " + total + ") "
                : " Summary (last " + total + ") ";
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title).build();
        Paragraph para = Paragraph.builder().text(Text.from(lines)).block(block).build();
        frame.renderWidget(para, area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "Esc", "back");
        TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        TuiHelper.hint(spans, "PgUp/Dn", "detail");
        if (!wordWrap) {
            TuiHelper.hint(spans, TuiIcons.HINT_H, "h-scroll");
        }
        TuiHelper.hint(spans, "Home/End", "top/end");
        TuiHelper.hint(spans, "s", "sort");
        TuiHelper.hint(spans, "Space", paused ? "resume" : "pause");
        TuiHelper.hint(spans, "t", TIME_FILTERS[timeFilterIndex]);
        TuiHelper.hintLast(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
    }

    private void renderDetail(Frame frame, Rect area, ActivityEntry ae) {
        List<Line> lines = new ArrayList<>();

        HistoryTab.addExchangeInfoLines(lines,
                ae.exchangeId, ae.routeId, null, null, "Endpoint", ae.fromEndpointUri,
                ae.elapsed, null, ae.failed);

        if (ae.exceptionMessage != null) {
            HistoryTab.addExceptionLines(lines, ae.exceptionMessage);
        }

        if (!ae.endpointSends.isEmpty()) {
            lines.add(Line.from(Span.styled(" Endpoint Sends:", Style.EMPTY.fg(Theme.accent()).bold())));
            for (ActivityEntry.EndpointSendEntry se : ae.endpointSends) {
                lines.add(Line
                        .from(
                        Span.styled("   " + (se.endpointUri != null ? se.endpointUri : ""), Style.EMPTY),
                        Span.styled("  " + se.elapsed + "ms", Theme.muted())));
            }
        }

        int[] scroll = { detailScroll };
        int[] hScroll = { detailHScroll };
        HistoryTab.renderDetailPanel(frame, area, lines, wordWrap, hScroll, scroll, detailScrollState, " Detail ");
        detailScroll = scroll[0];
        detailHScroll = hScroll[0];
    }

    private List<ActivityEntry> filteredActivity(IntegrationInfo info) {
        List<ActivityEntry> source = paused && pausedSnapshot != null ? pausedSnapshot : info.activity;
        List<ActivityEntry> result = new ArrayList<>(source);
        long filterMillis = TIME_FILTER_MILLIS[timeFilterIndex];
        if (filterMillis > 0) {
            long cutoff = System.currentTimeMillis() - filterMillis;
            result.removeIf(ae -> ae.timestamp > 0 && ae.timestamp < cutoff);
        }
        result.sort(this::sortActivity);
        return result;
    }

    private int sortActivity(ActivityEntry a, ActivityEntry b) {
        int result = switch (sort) {
            case "exchange" -> TuiHelper.compareStr(a.exchangeId, b.exchangeId);
            case "route" -> TuiHelper.compareStr(a.routeId, b.routeId);
            case "elapsed" -> Long.compare(a.elapsed, b.elapsed);
            case "since" -> Long.compare(b.timestamp, a.timestamp);
            default -> 0;
        };
        return sortReversed ? -result : result;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        List<ActivityEntry> sorted = filteredActivity(info);
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            List<String> items = sorted.stream()
                    .map(ae -> ae.exchangeId != null ? ae.exchangeId : "")
                    .toList();
            return new SelectionContext("activity-entry", items, sel, sorted.size(), "Activity");
        }
        return null;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        JsonObject result = new JsonObject();
        result.put("tab", "Activity");
        if (info == null) {
            result.put("rows", new ArrayList<>());
            result.put("totalRows", 0);
            return result;
        }
        List<ActivityEntry> sorted = filteredActivity(info);
        List<JsonObject> jsonRows = new ArrayList<>();
        for (ActivityEntry ae : sorted) {
            JsonObject jo = new JsonObject();
            jo.put("exchangeId", ae.exchangeId);
            jo.put("routeId", ae.routeId);
            jo.put("status", ae.failed ? "FAILED" : "OK");
            jo.put("elapsed", ae.elapsed);
            jo.put("sends", ae.endpointSends.size());
            jo.put("failed", ae.failed);
            jo.put("timestamp", ae.timestamp);
            if (ae.timestamp > 0) {
                jo.put("since", TimeUtils.printSince(ae.timestamp));
            }
            if (ae.fromEndpointUri != null) {
                jo.put("fromEndpointUri", ae.fromEndpointUri);
            }
            jsonRows.add(jo);
        }
        result.put("rows", jsonRows);
        result.put("totalRows", sorted.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    @Override
    public String description() {
        return "Recent completed exchange activity with message details";
    }

    @Override
    public String getHelpText() {
        return """
                # Activity

                The Activity tab shows a live feed of recently completed exchanges.
                It captures the last N exchanges (default 100) with their message
                content, giving you a rolling window of what your integration has
                been processing.

                Unlike the Inspect tab which traces individual processing steps
                within a route, Activity shows one entry per completed exchange
                with a summary of the final state.

                ## Summary Panel

                The top panel shows aggregated stats from the visible activity:

                - **Total** / **OK** / **Failed** — exchange counts
                - **Sends** — total remote endpoint calls across all exchanges
                - **p50** / **p95** / **Max** — elapsed time statistics
                - **Window** — how far back the oldest and newest entries are

                ## Activity List

                - **EXCHANGE** — Exchange identifier
                - **ROUTE** — Route that processed the exchange
                - **STATUS** — `OK` (green) or `FAILED` (red)
                - **ELAPSED** — Total processing time in milliseconds
                - **SENDS** — Number of outbound endpoint calls made during the exchange
                - **SINCE** — How long ago the exchange completed (e.g., `5s`, `2m`)
                - **ENDPOINT** — The consumer endpoint that received the exchange

                ## Detail View

                Select an exchange to see its details in the panel below:

                - **Exchange info**: Exchange ID, route, elapsed time, and failure status
                - **Endpoint Sends**: Remote endpoints called during the exchange,
                  with individual elapsed times
                - **Exception**: If the exchange failed, shows the exception type,
                  message, and stack trace

                ## Keys

                - `Up/Down` — select exchange
                - `Home/End` — jump to first/last exchange
                - `PgUp/PgDn` — scroll the detail panel
                - `Left/Right` — horizontal scroll (when wrap is off)
                - `Space` — pause/resume data feed (freezes the view for inspection)
                - `t` — cycle time filter (all, 1m, 5m, 15m, 30m, 1h)
                - `w` — toggle word wrap
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
    }

}
