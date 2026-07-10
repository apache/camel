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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class SqlTraceTab extends AbstractTableTab {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ScrollbarState detailScrollState = new ScrollbarState();
    private int detailScroll;
    private boolean wordWrap = true;
    private String selectedKey;
    private Consumer<String> editSqlAction;

    SqlTraceTab(MonitorContext ctx) {
        super(ctx, "time", "type", "sql", "route", "duration", "rows");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.sqlTraceStatements.size() : 0;
    }

    void setEditSqlAction(Consumer<String> editSqlAction) {
        this.editSqlAction = editSqlAction;
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.sqlTraceStatements.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isCharIgnoreCase('w')) {
            wordWrap = !wordWrap;
            return true;
        }
        if (ke.isCharIgnoreCase('e') && editSqlAction != null) {
            String sql = getSelectedQuery();
            if (sql != null) {
                editSqlAction.accept(sql);
                return true;
            }
        }
        if (ke.isHome()) {
            detailScroll = 0;
            selectedKey = null;
            tableState.select(0);
            return true;
        }
        if (ke.isEnd()) {
            detailScroll = 0;
            selectedKey = null;
            IntegrationInfo ei = ctx.findSelectedIntegration();
            if (ei != null && !ei.sqlTraceStatements.isEmpty()) {
                tableState.select(ei.sqlTraceStatements.size() - 1);
            }
            return true;
        }
        if (ke.isPageUp()) {
            detailScroll = Math.max(0, detailScroll - 5);
            return true;
        }
        if (ke.isPageDown()) {
            detailScroll += 5;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        detailScroll = 0;
        selectedKey = null;
        super.navigateUp();
    }

    @Override
    public void navigateDown() {
        detailScroll = 0;
        selectedKey = null;
        super.navigateDown();
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<Rect> layout = Layout.vertical()
                .constraints(Constraint.length(3), Constraint.fill())
                .split(area);

        renderKpiStrip(frame, layout.get(0), info);
        renderMasterDetail(frame, layout.get(1), info);
    }

    private void renderKpiStrip(Frame frame, Rect area, IntegrationInfo info) {
        Style labelStyle = Theme.muted();
        Style valueStyle = Style.EMPTY.fg(Theme.accent()).bold();
        Style warnStyle = Theme.warning().bold();
        Style errorStyle = Theme.error().bold();

        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled("  Total: ", labelStyle));
        spans.add(Span.styled(String.valueOf(info.sqlTraceTotal), valueStyle));
        spans.add(Span.styled("  Avg: ", labelStyle));
        spans.add(Span.styled(info.sqlTraceAvgTime + " ms", valueStyle));
        spans.add(Span.styled("  Slowest: ", labelStyle));
        Style slowestStyle = info.sqlTraceSlowestTime >= 100 ? warnStyle : valueStyle;
        spans.add(Span.styled(info.sqlTraceSlowestTime + " ms", slowestStyle));
        spans.add(Span.styled("  Slow(>=100ms): ", labelStyle));
        Style slowStyle = info.sqlTraceSlowCount > 0 ? warnStyle : valueStyle;
        spans.add(Span.styled(String.valueOf(info.sqlTraceSlowCount), slowStyle));
        spans.add(Span.styled("  Failed: ", labelStyle));
        Style failStyle = info.sqlTraceFailedCount > 0 ? errorStyle : valueStyle;
        spans.add(Span.styled(String.valueOf(info.sqlTraceFailedCount), failStyle));

        Paragraph kpi = Paragraph.builder()
                .text(Text.from(Line.from(spans)))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" SQL Trace ").build())
                .build();
        frame.renderWidget(kpi, area);
    }

    private String getSelectedQuery() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        List<SqlTraceInfo> sorted = new ArrayList<>(info.sqlTraceStatements);
        sorted.sort(this::sortTrace);
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            return sorted.get(sel).query;
        }
        return null;
    }

    private static String traceKey(SqlTraceInfo si) {
        return si.exchangeId + "@" + si.timestamp;
    }

    private void renderMasterDetail(Frame frame, Rect area, IntegrationInfo info) {
        List<SqlTraceInfo> sorted = new ArrayList<>(info.sqlTraceStatements);
        sorted.sort(this::sortTrace);

        // follow the previously selected row when new data shifts indices
        if (selectedKey != null) {
            for (int i = 0; i < sorted.size(); i++) {
                if (selectedKey.equals(traceKey(sorted.get(i)))) {
                    tableState.select(i);
                    break;
                }
            }
        }

        // auto-select first row when data arrives
        if (!sorted.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }

        SqlTraceInfo selected = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selected = sorted.get(sel);
            selectedKey = traceKey(selected);
        }
        boolean showDetail = selected != null;

        List<Rect> chunks = showDetail
                ? Layout.vertical()
                        .constraints(Constraint.length(13), Constraint.length(1), Constraint.fill())
                        .split(area)
                : List.of(area);

        renderTable(frame, chunks.get(0), sorted);

        if (showDetail) {
            renderDetail(frame, chunks.get(2), selected);
        }
    }

    private void renderTable(Frame frame, Rect area, List<SqlTraceInfo> sorted) {
        List<Row> rows = new ArrayList<>();
        for (SqlTraceInfo si : sorted) {
            Style durStyle = si.duration >= 100 ? Theme.warning() : Style.EMPTY;
            Style statusStyle = si.failed ? Theme.error() : Theme.success();
            String status = si.failed ? "FAIL" : "OK";

            String time = "";
            if (si.timestamp > 0) {
                time = LocalDateTime.ofInstant(Instant.ofEpochMilli(si.timestamp), ZoneId.systemDefault())
                        .format(TIME_FMT);
            }

            String rowsStr = "";
            if (si.rowCount > 0) {
                rowsStr = String.valueOf(si.rowCount);
            } else if (si.updateCount > 0) {
                rowsStr = String.valueOf(si.updateCount);
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(time, Style.EMPTY.dim())),
                    Cell.from(Span.styled(si.category != null ? si.category : "", categoryStyle(si.category))),
                    Cell.from(si.query != null ? si.query : ""),
                    Cell.from(Span.styled(si.routeId != null ? si.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                    rightCell(String.valueOf(si.duration), 10, durStyle),
                    rightCell(rowsStr, 8),
                    Cell.from(Span.styled(status, statusStyle))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""), Cell.from(""),
                    Cell.from(Span.styled("No SQL statements traced", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("TIME", "time"), sortStyle("time"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled(sortLabel("SQL", "sql"), sortStyle("sql"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        rightCell(sortLabel("DURATION", "duration"), 10, sortStyle("duration")),
                        rightCell(sortLabel("ROWS", "rows"), 8, sortStyle("rows")),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(14),
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(9))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Statements ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null) {
            if (handleTableClick(me, lastTableArea, tableState, info.sqlTraceStatements.size())) {
                detailScroll = 0;
                selectedKey = null;
                return true;
            }
        }
        return false;
    }

    private void renderDetail(Frame frame, Rect area, SqlTraceInfo si) {
        List<Line> lines = new ArrayList<>();
        Style labelStyle = Theme.muted();
        Style valueStyle = Style.EMPTY;

        lines.add(Line.from(
                Span.styled(" Category: ", labelStyle),
                Span.styled(si.category != null ? si.category : "", categoryStyle(si.category))));
        lines.add(Line.from(
                Span.styled(" Route: ", labelStyle),
                Span.styled(si.routeId != null ? si.routeId : "", valueStyle)));
        if (si.nodeId != null) {
            List<Span> nodeSpans = new ArrayList<>();
            nodeSpans.add(Span.styled(" Node: ", labelStyle));
            nodeSpans.add(Span.styled(si.nodeId, valueStyle));
            if (si.location != null) {
                nodeSpans.add(Span.styled("  Source: ", labelStyle));
                nodeSpans.add(Span.styled(si.location, valueStyle));
            }
            lines.add(Line.from(nodeSpans));
        }
        lines.add(Line.from(
                Span.styled(" Exchange: ", labelStyle),
                Span.styled(si.exchangeId != null ? si.exchangeId : "", valueStyle)));

        String time = "";
        if (si.timestamp > 0) {
            time = LocalDateTime.ofInstant(Instant.ofEpochMilli(si.timestamp), ZoneId.systemDefault())
                    .format(TIME_FMT);
        }
        Style durStyle = si.duration >= 100 ? Theme.warning() : Style.EMPTY;
        Style statusStyle = si.failed ? Theme.error() : Theme.success();
        lines.add(Line.from(
                Span.styled(" Time: ", labelStyle),
                Span.styled(time, valueStyle),
                Span.styled("  Duration: ", labelStyle),
                Span.styled(si.duration + " ms", durStyle),
                Span.styled("  Status: ", labelStyle),
                Span.styled(si.failed ? "FAILED" : "OK", statusStyle)));

        String rowsStr = "";
        if (si.rowCount > 0) {
            rowsStr = si.rowCount + " rows";
        } else if (si.updateCount > 0) {
            rowsStr = si.updateCount + " updated";
        }
        if (!rowsStr.isEmpty()) {
            lines.add(Line.from(
                    Span.styled(" Rows: ", labelStyle),
                    Span.styled(rowsStr, valueStyle)));
        }

        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled(" SQL:", labelStyle)));
        lines.add(Line.from(Span.styled("   " + (si.query != null ? si.query : ""), valueStyle)));

        int[] scroll = { detailScroll };
        int[] hScroll = { 0 };
        HistoryTab.renderDetailPanel(frame, area, lines, wordWrap, hScroll, scroll, detailScrollState);
        detailScroll = scroll[0];
    }

    private static Style categoryStyle(String category) {
        if (category == null) {
            return Style.EMPTY;
        }
        return switch (category) {
            case "SELECT" -> Style.EMPTY.fg(Theme.accent());
            case "INSERT" -> Theme.success();
            case "UPDATE" -> Theme.label();
            case "DELETE" -> Theme.error();
            default -> Style.EMPTY;
        };
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hint(spans, "Home/End", "top/end");
        hint(spans, "PgUp/Dn", "scroll detail");
        hint(spans, "e", "edit SQL");
        hint(spans, "s", "sort");
        hint(spans, "w", "wrap [" + (wordWrap ? "on" : "off") + "]");
    }

    private int sortTrace(SqlTraceInfo a, SqlTraceInfo b) {
        int result = switch (sort) {
            case "type" -> {
                String ca = a.category != null ? a.category : "";
                String cb = b.category != null ? b.category : "";
                yield ca.compareToIgnoreCase(cb);
            }
            case "sql" -> {
                String qa = a.query != null ? a.query : "";
                String qb = b.query != null ? b.query : "";
                yield qa.compareToIgnoreCase(qb);
            }
            case "route" -> {
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
            case "duration" -> Long.compare(b.duration, a.duration);
            case "rows" -> {
                int ra = a.rowCount > 0 ? a.rowCount : a.updateCount;
                int rb = b.rowCount > 0 ? b.rowCount : b.updateCount;
                yield Integer.compare(rb, ra);
            }
            default -> Long.compare(b.timestamp, a.timestamp); // "time" — newest first
        };
        return sortReversed ? -result : result;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.sqlTraceStatements.isEmpty()) {
            return null;
        }
        List<SqlTraceInfo> sorted = new ArrayList<>(info.sqlTraceStatements);
        sorted.sort(this::sortTrace);
        List<String> items = sorted.stream()
                .map(s -> s.query != null ? s.query : s.endpoint)
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "SQL Trace");
    }

    @Override
    public String description() {
        return "Trace SQL query executions through camel-sql and camel-jdbc components";
    }

    @Override
    public String getHelpText() {
        return """
                # SQL Trace

                Traces SQL query executions flowing through `camel-sql` and `camel-jdbc`
                components. Captures individual executions with timing, row counts,
                and failure status.

                ## KPI Strip

                The top bar shows aggregate statistics:
                - **Total** — Total number of SQL statements traced
                - **Avg** — Average execution time in milliseconds
                - **Slowest** — Longest single execution (yellow when >= 100ms)
                - **Slow(>=100ms)** — Count of slow queries (yellow when > 0)
                - **Failed** — Count of failed executions (red when > 0)

                ## Table Columns

                - **TIME** — Timestamp of the execution
                - **TYPE** — SQL type: SELECT, INSERT, UPDATE, DELETE, CALL, or OTHER
                - **SQL** — The SQL query text
                - **ROUTE** — The Camel route ID that executed the query
                - **DURATION** — Execution time in ms (yellow when >= 100ms)
                - **ROWS** — Row count (for SELECT) or update count (for INSERT/UPDATE/DELETE)
                - **STATUS** — OK (green) or FAIL (red)

                ## Detail Panel

                Select a statement with Up/Down to see full details below the table:
                SQL text, endpoint URI, route, exchange ID, timing, and row counts.

                ## Keys

                - `Up/Down` — select statement
                - `PgUp/PgDn` — scroll detail panel
                - `Home/End` — jump to top/end of detail
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `w` — toggle word wrap
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "SQL Trace");
        JsonArray rows = new JsonArray();
        List<SqlTraceInfo> sorted = new ArrayList<>(info.sqlTraceStatements);
        sorted.sort(this::sortTrace);
        for (SqlTraceInfo si : sorted) {
            JsonObject row = new JsonObject();
            row.put("timestamp", si.timestamp);
            row.put("exchangeId", si.exchangeId);
            row.put("routeId", si.routeId);
            row.put("query", si.query);
            row.put("type", si.category);
            row.put("endpoint", si.endpoint);
            row.put("duration", si.duration);
            row.put("rowCount", si.rowCount);
            row.put("updateCount", si.updateCount);
            row.put("failed", si.failed);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.sqlTraceStatements.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
