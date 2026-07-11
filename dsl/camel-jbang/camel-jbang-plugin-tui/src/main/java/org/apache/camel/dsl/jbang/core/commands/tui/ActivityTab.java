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
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

class ActivityTab extends AbstractTableTab {

    private final ScrollbarState detailScrollState = new ScrollbarState();
    private int detailScroll;
    private int detailHScroll;
    private boolean wordWrap = true;
    private boolean showProperties;
    private boolean showVariables;
    private boolean showHeaders = true;
    private boolean showBody = true;
    private String filter;

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
        if (ke.isChar('p')) {
            showProperties = !showProperties;
            return true;
        }
        if (ke.isChar('v')) {
            showVariables = !showVariables;
            return true;
        }
        if (ke.isChar('h')) {
            showHeaders = !showHeaders;
            return true;
        }
        if (ke.isChar('b')) {
            showBody = !showBody;
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
                    ? org.apache.camel.util.TimeUtils.printSince(ae.timestamp) : "";
            String status = ae.failed ? "FAILED" : "OK";
            Style statusStyle = ae.failed ? Theme.error() : Theme.success();
            String elapsed = ae.elapsed + "ms";

            rows.add(Row
                    .from(
                    Cell.from(ae.exchangeId != null ? ae.exchangeId : ""),
                    Cell.from(Span.styled(ae.routeId != null ? ae.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(status, statusStyle)),
                    Cell.from(elapsed),
                    Cell.from(ago),
                    Cell.from(ae.endpointUri != null ? ae.endpointUri : "")));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No activity captured", 6));
        }

        ActivityEntry selectedEntry = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedEntry = sorted.get(sel);
        }
        boolean showDetail = selectedEntry != null;
        List<Rect> chunks = showDetail
                ? Layout.vertical()
                        .constraints(Constraint.length(13), Constraint.fill())
                        .split(area)
                : List.of(area);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("EXCHANGE", "exchange"), sortStyle("exchange"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("ELAPSED", "elapsed"), sortStyle("elapsed"))),
                        Cell.from(Span.styled(sortLabel("SINCE", "since"), sortStyle("since"))),
                        Cell.from(Span.styled("ENDPOINT", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(38),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Activity [" + sorted.size() + "] ").build())
                .build();

        lastTableArea = chunks.get(0);
        frame.renderStatefulWidget(table, chunks.get(0), tableState);
        renderScrollbar(frame, sorted.size());

        if (showDetail) {
            renderDetail(frame, chunks.get(1), selectedEntry);
        }
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
        hintShowBhpv(spans, showBody, showHeaders, showProperties, showVariables);
        TuiHelper.hintLast(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
    }

    private void renderDetail(Frame frame, Rect area, ActivityEntry ae) {
        List<Line> lines = new ArrayList<>();

        HistoryTab.addExchangeInfoLines(lines,
                ae.exchangeId, ae.routeId, null, null, null,
                ae.elapsed, null, ae.failed);

        if (ae.exceptionType != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(ae.exceptionType);
            if (ae.exceptionMessage != null) {
                String msg = ae.exceptionMessage;
                try {
                    msg = Jsoner.unescape(msg);
                } catch (Exception e) {
                    // ignore
                }
                sb.append(": ").append(msg);
            }
            if (ae.stackTrace != null) {
                String st = ae.stackTrace;
                try {
                    st = Jsoner.unescape(st);
                } catch (Exception e) {
                    // ignore
                }
                sb.append("\n").append(st);
            }
            HistoryTab.addExceptionLines(lines, sb.toString());
        }

        if (showProperties && !ae.properties.isEmpty()) {
            HistoryTab.addKvLines(lines, " Exchange Properties:", ae.properties, ae.propertyTypes, false, null);
        }
        if (showVariables && !ae.variables.isEmpty()) {
            HistoryTab.addKvLines(lines, " Exchange Variables:", ae.variables, ae.variableTypes, false, null);
        }
        if (showHeaders && !ae.headers.isEmpty()) {
            HistoryTab.addKvLines(lines, " Headers:", ae.headers, ae.headerTypes, false, null);
        }
        if (showBody) {
            HistoryTab.addBodyLines(lines, ae.body, ae.bodyType, false);
        }

        int[] scroll = { detailScroll };
        int[] hScroll = { detailHScroll };
        HistoryTab.renderDetailPanel(frame, area, lines, wordWrap, hScroll, scroll, detailScrollState);
        detailScroll = scroll[0];
        detailHScroll = hScroll[0];
    }

    private List<ActivityEntry> filteredActivity(IntegrationInfo info) {
        List<ActivityEntry> result = new ArrayList<>(info.activity);
        if (filter != null && !filter.isEmpty()) {
            result.removeIf(ae -> ae.routeId == null
                    || !org.apache.camel.support.PatternHelper.matchPattern(ae.routeId, filter));
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
            jo.put("failed", ae.failed);
            jo.put("timestamp", ae.timestamp);
            if (ae.timestamp > 0) {
                jo.put("since", org.apache.camel.util.TimeUtils.printSince(ae.timestamp));
            }
            if (ae.endpointUri != null) {
                jo.put("endpointUri", ae.endpointUri);
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

                ## Activity List

                - **EXCHANGE** — Exchange identifier
                - **ROUTE** — Route that processed the exchange
                - **STATUS** — `OK` (green) or `FAILED` (red)
                - **ELAPSED** — Total processing time in milliseconds
                - **SINCE** — How long ago the exchange completed (e.g., `5s`, `2m`)
                - **ENDPOINT** — The consumer endpoint that received the exchange

                ## Detail View

                Select an exchange to see its details in the panel below:

                - **Exchange info**: Exchange ID, route, elapsed time, and failure status
                - **Exception**: If the exchange failed, shows the exception type,
                  message, and stack trace
                - **Headers**: Message headers at the time of completion
                - **Properties**: Exchange-level properties
                - **Variables**: Exchange variables
                - **Body**: Message body content with type information

                Use `h`, `b`, `p`, `v` keys to toggle each section on or off.

                ## Keys

                - `Up/Down` — select exchange
                - `Home/End` — jump to first/last exchange
                - `PgUp/PgDn` — scroll the detail panel
                - `Left/Right` — horizontal scroll (when wrap is off)
                - `h` — toggle headers
                - `b` — toggle body
                - `p` — toggle properties
                - `v` — toggle variables
                - `w` — toggle word wrap
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
    }

    private static void hintShowBhpv(List<Span> spans, boolean body, boolean headers, boolean props, boolean vars) {
        spans.add(Span.styled(" show ", Theme.hintKey()));
        spans.add(Span.raw(" "));
        spans.add(Span.styled(body ? "B" : "b", body ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.styled(headers ? "H" : "h", headers ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.styled(props ? "P" : "p", props ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.styled(vars ? "V" : "v", vars ? Style.EMPTY.fg(Theme.baseFg()).bold() : Style.EMPTY.dim()));
        spans.add(Span.raw("  "));
    }
}
