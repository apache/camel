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
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ErrorsTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "id", "age", "route", "node", "exception" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final ScrollbarState detailScrollState = new ScrollbarState();
    private String sort = "id";
    private int sortIndex;
    private boolean sortReversed;
    private static final String[] HANDLED_FILTER = { "all", "true", "false" };
    private int handledIndex;
    private String handledFilter = "all";
    private int detailScroll;
    private int detailHScroll;
    private boolean wordWrap = true;
    private boolean showProperties;
    private boolean showVariables;
    private boolean showHeaders = true;
    private boolean showBody = true;

    private final DiagramSupport diagram = new DiagramSupport();

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
        if (diagram.handleScrollKeys(ke)) {
            return true;
        }

        if (ke.isChar('d')) {
            diagram.toggleImageDiagram(this::loadDiagramForSelectedError);
            return true;
        }
        if (ke.isChar('D')) {
            diagram.toggleTextDiagram(this::loadDiagramForSelectedError);
            return true;
        }

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
        if (ke.isCharIgnoreCase('f')) {
            handledIndex = (handledIndex + 1) % HANDLED_FILTER.length;
            handledFilter = HANDLED_FILTER[handledIndex];
            return true;
        }
        if (ke.isCharIgnoreCase('p')) {
            showProperties = !showProperties;
            return true;
        }
        if (ke.isCharIgnoreCase('v')) {
            showVariables = !showVariables;
            return true;
        }
        if (ke.isCharIgnoreCase('h')) {
            showHeaders = !showHeaders;
            return true;
        }
        if (ke.isCharIgnoreCase('b')) {
            showBody = !showBody;
            return true;
        }
        if (ke.isHome()) {
            detailScroll = 0;
            return true;
        }
        if (ke.isEnd()) {
            detailScroll = Integer.MAX_VALUE;
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
        return diagram.handleEscape();
    }

    @Override
    public void navigateUp() {
        detailScroll = 0;
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        detailScroll = 0;
        tableState.selectNext(filteredSize());
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            diagram.renderDiagram(frame, area, " Error Diagram ");
            return;
        }

        List<ErrorInfo> sorted = applyFilter(info.errors);

        List<Row> rows = new ArrayList<>();
        for (ErrorInfo ei : sorted) {
            String ago = ei.timestamp > 0
                    ? org.apache.camel.util.TimeUtils.printSince(ei.timestamp) : "";
            String handledStr = ei.handled ? "true" : "false";
            Style handledStyle = ei.handled
                    ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.fg(Color.LIGHT_RED);
            String shortException = shortExceptionType(ei.exceptionType);

            rows.add(Row.from(
                    Cell.from(ei.exchangeId != null ? ei.exchangeId : ""),
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
                    Cell.from(""), Cell.from(""), Cell.from("")));
        }

        ErrorInfo selectedError = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedError = sorted.get(sel);
        }
        boolean showDetail = selectedError != null;
        List<Rect> chunks = showDetail
                ? Layout.vertical()
                        .constraints(Constraint.length(13), Constraint.length(1), Constraint.fill())
                        .split(area)
                : List.of(area);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("ID", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("AGO", "age"), sortStyle("age"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("NODE", "node"), sortStyle("node"))),
                        Cell.from(Span.styled("HANDLED", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("EXCEPTION", "exception"), sortStyle("exception"))),
                        Cell.from(Span.styled("MESSAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(38),
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
            renderDetail(frame, chunks.get(2), selectedError);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (diagram.isShowDiagram()) {
            diagram.renderFooterHints(spans);
        } else {
            hint(spans, "Esc", "back");
            hint(spans, "↑↓", "navigate");
            hint(spans, "PgUp/Dn", "scroll detail");
            if (!wordWrap) {
                hint(spans, "←→", "h-scroll");
            }
            hint(spans, "Home/End", "top/end");
            hint(spans, "s", "sort");
            hint(spans, "d", "diagram");
            hint(spans, "D", "text diagram");
            hint(spans, "f", "handled [" + handledFilter + "]");
            hint(spans, "p", "properties [" + (showProperties ? "on" : "off") + "]");
            hint(spans, "v", "variables [" + (showVariables ? "on" : "off") + "]");
            hint(spans, "h", "headers [" + (showHeaders ? "on" : "off") + "]");
            hint(spans, "b", "body [" + (showBody ? "on" : "off") + "]");
            hint(spans, "w", "wrap [" + (wordWrap ? "on" : "off") + "]");
        }
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
                String msg = ei.exceptionMessage;
                try {
                    msg = Jsoner.unescape(msg);
                } catch (Exception e) {
                    // ignore
                }
                sb.append(": ").append(msg);
            }
            if (ei.stackTrace != null) {
                String st = ei.stackTrace;
                try {
                    st = Jsoner.unescape(st);
                } catch (Exception e) {
                    // ignore
                }
                sb.append("\n").append(st);
            }
            exception = sb.toString();
        }
        HistoryTab.addExceptionLines(lines, exception);

        // message history
        if (ei.messageHistory != null && ei.messageHistory.length > 0) {
            lines.add(Line.from(Span.styled(" Message History:", Style.EMPTY.fg(Color.MAGENTA).bold())));
            for (String step : ei.messageHistory) {
                lines.add(Line.from(Span.raw("   " + TuiHelper.fixControlChars(step))));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // exchange properties, variables, headers, body
        if (showProperties && !ei.properties.isEmpty()) {
            HistoryTab.addKvLines(lines, " Exchange Properties:", ei.properties, ei.propertyTypes);
        }
        if (showVariables && !ei.variables.isEmpty()) {
            HistoryTab.addKvLines(lines, " Exchange Variables:", ei.variables, ei.variableTypes);
        }
        if (showHeaders && !ei.headers.isEmpty()) {
            HistoryTab.addKvLines(lines, " Headers:", ei.headers, ei.headerTypes);
        }
        if (showBody) {
            HistoryTab.addBodyLines(lines, ei.body, ei.bodyType);
        }

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
            case "id" -> compareStr(a.exchangeId, b.exchangeId);
            case "route" -> compareStr(a.routeId, b.routeId);
            case "node" -> compareStr(a.nodeId, b.nodeId);
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
        List<ErrorInfo> filtered = applyFilter(info.errors);
        List<String> items = filtered.stream()
                .map(e -> e.exchangeId != null ? e.exchangeId : "")
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Errors");
    }

    private List<ErrorInfo> applyFilter(List<ErrorInfo> errors) {
        List<ErrorInfo> list = new ArrayList<>(errors);
        list.sort(this::sortError);
        if (!"all".equals(handledFilter)) {
            boolean filterVal = "true".equals(handledFilter);
            list.removeIf(e -> e.handled != filterVal);
        }
        return list;
    }

    private int filteredSize() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return 0;
        }
        if ("all".equals(handledFilter)) {
            return info.errors.size();
        }
        boolean filterVal = "true".equals(handledFilter);
        return (int) info.errors.stream().filter(e -> e.handled == filterVal).count();
    }

    // ---- Diagram ----

    private void loadDiagramForSelectedError() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!diagram.beginLoad()) {
            return;
        }

        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.errors.isEmpty()) {
            diagram.endLoad();
            return;
        }

        List<ErrorInfo> sorted = applyFilter(info.errors);
        Integer sel = tableState.selected();
        ErrorInfo selectedError = null;
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedError = sorted.get(sel);
        }
        if (selectedError == null || selectedError.messageHistory == null || selectedError.messageHistory.length == 0) {
            diagram.endLoad();
            return;
        }

        String pid = ctx.selectedPid;
        boolean textMode = diagram.isDiagramTextMode();
        String[] messageHistory = selectedError.messageHistory;

        diagram.setLoadingPlaceholder();

        ctx.runner.scheduler().execute(() -> {
            try {
                diagram.loadHighlightedDiagramInBackground(
                        ctx, pid, textMode, messageHistory, RouteDiagramHelper.HighlightStyle.FAIL);
            } finally {
                diagram.endLoad();
            }
        });
    }
}
