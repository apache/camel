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
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ErrorsTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "age", "route", "exception" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final ScrollbarState detailScrollState = new ScrollbarState();
    private String sort = "age";
    private int sortIndex;
    private boolean sortReversed;
    private int detailScroll;
    private int detailHScroll;
    private boolean wordWrap = true;

    ErrorsTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.errors.isEmpty() && tableState.selected() == null) {
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
        if (ke.isCharIgnoreCase('w')) {
            wordWrap = !wordWrap;
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
        if (ke.isLeft() && !wordWrap) {
            detailHScroll = Math.max(0, detailHScroll - 4);
            return true;
        }
        if (ke.isRight() && !wordWrap) {
            detailHScroll += 4;
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
        detailScroll = 0;
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        detailScroll = 0;
        IntegrationInfo info = ctx.findSelectedIntegration();
        tableState.selectNext(info != null ? info.errors.size() : 0);
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<ErrorInfo> sorted = new ArrayList<>(info.errors);
        sorted.sort(this::sortError);

        List<Row> rows = new ArrayList<>();
        for (ErrorInfo ei : sorted) {
            String ago = ei.timestamp > 0
                    ? org.apache.camel.util.TimeUtils.printSince(ei.timestamp) : "";
            String handledStr = ei.handled ? "true" : "false";
            Style handledStyle = ei.handled
                    ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.fg(Color.LIGHT_RED);
            String shortException = shortExceptionType(ei.exceptionType);

            rows.add(Row.from(
                    Cell.from(ago),
                    Cell.from(Span.styled(ei.routeId != null ? ei.routeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(ei.nodeId != null ? ei.nodeId : ""),
                    Cell.from(Span.styled(handledStr, handledStyle)),
                    Cell.from(shortException),
                    Cell.from(ei.exceptionMessage != null ? ei.exceptionMessage : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No errors captured", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from("")));
        }

        ErrorInfo selectedError = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedError = sorted.get(sel);
        }
        boolean showDetail = selectedError != null;
        List<Rect> chunks = showDetail
                ? Layout.vertical()
                        .constraints(Constraint.length(Math.min(sorted.size() + 3, 12)), Constraint.fill())
                        .split(area)
                : List.of(area);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("AGO", "age"), sortStyle("age"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled("NODE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("HANDLED", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("EXCEPTION", "exception"), sortStyle("exception"))),
                        Cell.from(Span.styled("MESSAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(8),
                        Constraint.length(20),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(30),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Errors ").build())
                .build();

        frame.renderStatefulWidget(table, chunks.get(0), tableState);

        if (showDetail) {
            renderDetail(frame, chunks.get(1), selectedError);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "navigate");
        hint(spans, "s", "sort");
        hint(spans, "w", "wrap");
        hint(spans, "PgUp/Dn", "scroll");
        hint(spans, "1-0", "tabs");
    }

    private void renderDetail(Frame frame, Rect area, ErrorInfo ei) {
        List<Line> lines = new ArrayList<>();

        HistoryTab.addExchangeInfoLines(lines,
                ei.exchangeId, ei.routeId, ei.nodeId, null, ei.location,
                ei.elapsed, ei.threadName, !ei.handled);

        // exception with stack trace
        String exception = null;
        if (ei.exceptionType != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(ei.exceptionType);
            if (ei.exceptionMessage != null) {
                sb.append(": ").append(ei.exceptionMessage);
            }
            if (ei.stackTrace != null) {
                sb.append("\n").append(ei.stackTrace);
            }
            exception = sb.toString();
        }
        HistoryTab.addExceptionLines(lines, exception);

        // message history
        if (ei.messageHistory != null && ei.messageHistory.length > 0) {
            lines.add(Line.from(Span.styled(" Message History:", Style.EMPTY.fg(Color.MAGENTA).bold())));
            for (String step : ei.messageHistory) {
                lines.add(Line.from(Span.raw("   " + step)));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // variables, properties, headers
        if (!ei.variables.isEmpty()) {
            HistoryTab.addKvLines(lines, " Variables:", ei.variables, ei.variableTypes);
        }
        if (!ei.properties.isEmpty()) {
            HistoryTab.addKvLines(lines, " Properties:", ei.properties, ei.propertyTypes);
        }
        if (!ei.headers.isEmpty()) {
            HistoryTab.addKvLines(lines, " Headers:", ei.headers, ei.headerTypes);
        }

        // body
        HistoryTab.addBodyLines(lines, ei.body, ei.bodyType);

        int[] scroll = { detailScroll };
        int[] hScroll = { detailHScroll };
        HistoryTab.renderDetailPanel(frame, area, lines, wordWrap, hScroll, scroll, detailScrollState);
        detailScroll = scroll[0];
        detailHScroll = hScroll[0];
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private int sortError(ErrorInfo a, ErrorInfo b) {
        int result = switch (sort) {
            case "route" -> compareStr(a.routeId, b.routeId);
            case "exception" -> compareStr(a.exceptionType, b.exceptionType);
            default -> Long.compare(b.timestamp, a.timestamp); // newest first
        };
        return sortReversed ? -result : result;
    }

    private static String shortExceptionType(String type) {
        if (type == null) {
            return "";
        }
        int dot = type.lastIndexOf('.');
        if (dot > 0) {
            return type.substring(dot + 1);
        }
        return type;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.errors.isEmpty()) {
            return null;
        }
        List<ErrorInfo> sorted = new ArrayList<>(info.errors);
        sorted.sort(this::sortError);
        List<String> items = sorted.stream()
                .map(e -> e.exchangeId != null ? e.exchangeId : "")
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Errors");
    }
}
