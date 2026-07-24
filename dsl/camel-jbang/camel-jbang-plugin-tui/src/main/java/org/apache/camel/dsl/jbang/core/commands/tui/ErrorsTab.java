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
import java.util.Map;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
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
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class ErrorsTab extends AbstractTableTab {

    private final ScrollbarState detailScrollState = new ScrollbarState();
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

    private static final int MOUSE_SCROLL_LINES = 3;

    private static final int INFO_NARROW = 0;
    private static final int INFO_WIDE = 1;
    private static final int INFO_FULL = 2;

    private final DiagramSupport diagram = new DiagramSupport();
    private int infoPanelSize = INFO_NARROW;

    ErrorsTab(MonitorContext ctx) {
        super(ctx, "id", "age", "route", "node", "exception");
    }

    @Override
    protected int getRowCount() {
        return filteredSize();
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.errors.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (diagram.isShowDiagram() && diagram.isHistoryMode() && diagram.hasHistoryData()) {
            if (diagram.isHistoryTopologyMode()) {
                if (ke.isUp()) {
                    diagram.selectNodeUp();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isDown()) {
                    diagram.selectNodeDown();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isLeft()) {
                    diagram.selectNodeLeft();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isRight()) {
                    diagram.selectNodeRight();
                    diagram.scrollToSelectedNode();
                    return true;
                }
                if (ke.isConfirm()) {
                    diagram.historyEnterDrillDown();
                    return true;
                }
            } else {
                if (ke.isUp()) {
                    diagram.historyNavigateUp();
                    return true;
                }
                if (ke.isDown()) {
                    diagram.historyNavigateDown();
                    return true;
                }
                if (ke.isLeft()) {
                    diagram.scrollLeft();
                    return true;
                }
                if (ke.isRight()) {
                    diagram.scrollRight();
                    return true;
                }
                if (ke.isChar('t')) {
                    diagram.historyReturnToTopology();
                    return true;
                }
            }
            if (ke.isHome()) {
                diagram.scrollHome();
                return true;
            }
            if (ke.isCharIgnoreCase('n')) {
                diagram.setShowDescription(!diagram.isShowDescription());
                diagram.endLoad();
                loadDiagramForSelectedError();
                return true;
            }
            if (ke.isChar('i')) {
                infoPanelSize = (infoPanelSize + 1) % 3;
                return true;
            }
            if (ke.isChar('b')) {
                showBody = !showBody;
                return true;
            }
            if (ke.isChar('h')) {
                showHeaders = !showHeaders;
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
            if (ke.isChar('w')) {
                wordWrap = !wordWrap;
                return true;
            }
        }
        if (diagram.handleScrollKeys(ke)) {
            return true;
        }

        if (ke.isCharIgnoreCase('d')) {
            diagram.toggleDiagram(this::loadDiagramForSelectedError);
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
        if (diagram.isShowDiagram() && diagram.isHistoryMode() && !diagram.isHistoryTopologyMode()) {
            diagram.historyGoBack();
            return true;
        }
        return diagram.handleEscape();
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!diagram.isShowDiagram()) {
            if (handleTableClick(me, lastTableArea, tableState, filteredSize())) {
                detailScroll = 0;
                return true;
            }
        }
        if (diagram.isShowDiagram()) {
            if (diagram.handleMouseScroll(me)) {
                return true;
            }
            if (me.isClick()) {
                if (diagram.isHistoryMode() && diagram.isHistoryTopologyMode()) {
                    int clicked = diagram.handleNodeClick(me);
                    if (clicked >= 0) {
                        return true;
                    }
                } else {
                    int clicked = diagram.handleEipNodeClick(me);
                    if (clicked >= 0) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            detailScroll = Math.max(0, detailScroll - MOUSE_SCROLL_LINES);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            detailScroll += MOUSE_SCROLL_LINES;
            return true;
        }
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
        tableState.selectNext(filteredSize());
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (diagram.isShowDiagram() && diagram.isHistoryMode() && diagram.hasHistoryData()) {
            if (infoPanelSize == INFO_FULL) {
                renderDiagramInfoPanel(frame, area, info);
            } else {
                Rect diagramArea = area;
                Rect infoArea = null;
                if (area.width() > 70) {
                    int panelWidth = infoPanelSize == INFO_WIDE ? area.width() / 2 : 35;
                    List<Rect> hParts = Layout.horizontal()
                            .constraints(Constraint.length(panelWidth), Constraint.fill())
                            .split(area);
                    infoArea = hParts.get(0);
                    diagramArea = hParts.get(1);
                }
                boolean hideOverlays = infoPanelSize == INFO_WIDE;
                if (diagram.isHistoryTopologyMode()) {
                    Line title = Line.from(Span.styled(
                            String.format(" Error Topology — step %d/%d ",
                                    diagram.getHistoryStepIndex() + 1, diagram.getHistoryStepCount()),
                            Style.EMPTY.fg(Theme.baseFg())));
                    diagram.renderHistoryTopologyDiagram(frame, diagramArea, title);
                } else {
                    String routeId = diagram.getHistoryDrillDownRouteId();
                    Line title = buildErrorBreadcrumbTitle();
                    diagram.renderHistoryRouteDiagram(frame, diagramArea, title, routeId, hideOverlays);
                }
                if (infoArea != null) {
                    renderDiagramInfoPanel(frame, infoArea, info);
                }
            }
            return;
        }

        List<ErrorInfo> sorted = applyFilter(info.errors);

        List<Row> rows = new ArrayList<>();
        for (ErrorInfo ei : sorted) {
            String ago = ei.timestamp > 0
                    ? org.apache.camel.util.TimeUtils.printSince(ei.timestamp) : "";
            String handledStr = ei.handled ? "true" : "false";
            Style handledStyle = ei.handled
                    ? Theme.success() : Theme.error();
            String shortException = shortExceptionType(ei.exceptionType);

            rows.add(Row.from(
                    Cell.from(ei.exchangeId != null ? ei.exchangeId : ""),
                    Cell.from(ago),
                    Cell.from(Span.styled(ei.routeId != null ? ei.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(ei.nodeId != null ? ei.nodeId : ""),
                    Cell.from(Span.styled(handledStr, handledStyle)),
                    Cell.from(shortException),
                    Cell.from(ei.exceptionMessage != null ? ei.exceptionMessage : "")));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No errors captured", 7));
        }

        ErrorInfo selectedError = null;
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedError = sorted.get(sel);
        }
        boolean showDetail = selectedError != null;
        List<Rect> chunks = showDetail
                ? Layout.vertical()
                        .constraints(Constraint.length(13), Constraint.fill())
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
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Errors [" + sorted.size() + "] ").build())
                .build();

        lastTableArea = chunks.get(0);
        frame.renderStatefulWidget(table, chunks.get(0), tableState);
        renderScrollbar(frame, filteredSize());

        if (showDetail) {
            renderDetail(frame, chunks.get(1), selectedError);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (diagram.isShowDiagram()) {
            if (diagram.isHistoryMode() && diagram.hasHistoryData()) {
                String infoLabel = switch (infoPanelSize) {
                    case INFO_WIDE -> "info [wide]";
                    case INFO_FULL -> "info [full]";
                    default -> "info [narrow]";
                };
                if (diagram.isHistoryTopologyMode()) {
                    hint(spans, "d", "close");
                    hint(spans, TuiIcons.HINT_NAV, "navigate");
                    hint(spans, "Enter", "drill-down");
                    hint(spans, "i", infoLabel);
                    hint(spans, "n", "description" + (diagram.isShowDescription() ? " [on]" : ""));
                    hintShowBhpv(spans, showBody, showHeaders, showProperties, showVariables);
                    hintLast(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
                } else {
                    hint(spans, "d", "close");
                    hint(spans, "Esc", "back");
                    hint(spans, TuiIcons.HINT_SCROLL, "step through path");
                    hint(spans, TuiIcons.HINT_H, "h-scroll");
                    hint(spans, "t", "topology");
                    hint(spans, "i", infoLabel);
                    hint(spans, "n", "description" + (diagram.isShowDescription() ? " [on]" : ""));
                    hintShowBhpv(spans, showBody, showHeaders, showProperties, showVariables);
                    hintLast(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
                }
                return;
            }
            diagram.renderFooterHints(spans);
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hint(spans, "PgUp/Dn", "detail");
        if (!wordWrap) {
            hint(spans, TuiIcons.HINT_H, "h-scroll");
        }
        hint(spans, "Home/End", "top/end");
        hint(spans, "s", "sort");
        hint(spans, "d", "diagram");
        hint(spans, "f", "handled [" + handledFilter + "]");
        hintShowBhpv(spans, showBody, showHeaders, showProperties, showVariables);
        hint(spans, "w", "wrap [" + (wordWrap ? "on" : "off") + "]");
    }

    private void renderDetail(Frame frame, Rect area, ErrorInfo ei) {
        List<Line> lines = new ArrayList<>();

        HistoryTab.addExchangeInfoLines(lines,
                ei.exchangeId, ei.routeId, ei.nodeId, null, ei.location,
                ei.elapsed, ei.threadName, !ei.handled);

        // exception with stack trace
        String exception = null;
        if (ei.exceptionType != null) {
            if (ei.stackTrace != null) {
                String st = ei.stackTrace;
                try {
                    st = Jsoner.unescape(st);
                } catch (Exception e) {
                    // ignore
                }
                if (st.startsWith(ei.exceptionType)) {
                    // stackTrace already contains the exception type and message
                    exception = st;
                } else {
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
                    sb.append("\n").append(st);
                    exception = sb.toString();
                }
            } else {
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
                exception = sb.toString();
            }
        }
        HistoryTab.addExceptionLines(lines, exception);

        // message history
        if (ei.messageHistory != null && ei.messageHistory.length > 0) {
            lines.add(Line.from(Span.styled(" Message History:", Theme.notice().bold())));
            for (int i = 0; i < ei.messageHistory.length; i++) {
                String s = TuiHelper.fixControlChars(ei.messageHistory[i]);
                boolean lastAndFailed = !ei.handled && i == ei.messageHistory.length - 1;
                int bracket = s.indexOf('[');
                if (bracket > 0) {
                    String routeId = s.substring(0, bracket);
                    String rest = s.substring(bracket);
                    if (lastAndFailed) {
                        lines.add(Line.from(
                                Span.styled("   " + routeId, Theme.error()),
                                Span.styled(rest, Theme.error())));
                    } else {
                        lines.add(Line.from(
                                Span.styled("   " + routeId, Style.EMPTY.fg(Theme.accent())),
                                Span.styled(rest, Theme.muted())));
                    }
                } else {
                    lines.add(Line.from(Span.styled("   " + s,
                            lastAndFailed ? Theme.error() : Style.EMPTY)));
                }
            }
            lines.add(Line.from(Span.raw("")));
        }

        // exchange properties, variables, headers, body
        if (showProperties && !ei.properties.isEmpty()) {
            HistoryTab.addKvLines(lines, " Exchange Properties:", ei.properties, ei.propertyTypes, false, null);
        }
        if (showVariables && !ei.variables.isEmpty()) {
            HistoryTab.addKvLines(lines, " Exchange Variables:", ei.variables, ei.variableTypes, false, null);
        }
        if (showHeaders && !ei.headers.isEmpty()) {
            HistoryTab.addKvLines(lines, " Headers:", ei.headers, ei.headerTypes, false, null);
        }
        if (showBody) {
            HistoryTab.addBodyLines(lines, ei.body, ei.bodyType, false);
        }

        int[] scroll = { detailScroll };
        int[] hScroll = { detailHScroll };
        HistoryTab.renderDetailPanel(frame, area, lines, wordWrap, hScroll, scroll, detailScrollState, " Detail ");
        detailScroll = scroll[0];
        detailHScroll = hScroll[0];
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

    private Line buildErrorBreadcrumbTitle() {
        Style nameStyle = Theme.label().bold();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Error [", Style.EMPTY.fg(Theme.baseFg())));
        var stack = diagram.getHistoryNavigationStack();
        if (stack.isEmpty()) {
            spans.add(Span.styled(diagram.getHistoryDrillDownRouteId(), nameStyle));
        } else {
            for (var it = stack.descendingIterator(); it.hasNext();) {
                spans.add(Span.styled(it.next(), nameStyle));
                spans.add(Span.styled(" → ", Theme.muted()));
            }
            spans.add(Span.styled(diagram.getHistoryDrillDownRouteId(), nameStyle));
        }
        spans.add(Span.styled(String.format("] — step %d/%d ",
                diagram.getHistoryStepIndex() + 1, diagram.getHistoryStepCount()),
                Style.EMPTY.fg(Theme.baseFg())));
        return Line.from(spans);
    }

    private void renderDiagramInfoPanel(Frame frame, Rect area, IntegrationInfo info) {
        List<ErrorInfo> sorted = applyFilter(info.errors);
        Integer sel = tableState.selected();
        ErrorInfo ei = null;
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            ei = sorted.get(sel);
        }

        List<Line> lines = new ArrayList<>();
        if (ei == null) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("No error selected", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Info ").build())
                            .build(),
                    area);
            return;
        }

        lines.add(Line.from(
                Span.styled(" Exchange: ", Theme.muted()),
                Span.raw(ei.exchangeId != null ? ei.exchangeId : "")));
        lines.add(Line.from(
                Span.styled(" Route:    ", Theme.muted()),
                Span.styled(ei.routeId != null ? ei.routeId : "", Style.EMPTY.fg(Theme.accent()))));
        lines.add(Line.from(
                Span.styled(" Node:     ", Theme.muted()),
                Span.raw(ei.nodeId != null ? ei.nodeId : "")));
        if (ei.elapsed >= 0) {
            lines.add(Line.from(
                    Span.styled(" Elapsed:  ", Theme.muted()),
                    Span.raw(ei.elapsed + "ms")));
        }
        if (ei.threadName != null) {
            lines.add(Line.from(
                    Span.styled(" Thread:   ", Theme.muted()),
                    Span.raw(ei.threadName)));
        }
        Style handledStyle = ei.handled ? Theme.success() : Theme.error().bold();
        lines.add(Line.from(
                Span.styled(" Handled:  ", Theme.muted()),
                Span.styled(ei.handled ? "true" : "false", handledStyle)));

        if (ei.exceptionType != null) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Exception", Theme.error().bold())));
            lines.add(Line.from(Span.raw(" " + ei.exceptionType)));
            if (ei.exceptionMessage != null) {
                lines.add(Line.from(Span.raw(" " + ei.exceptionMessage)));
            }
        }

        if (showBody && ei.body != null) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Body", Theme.muted()),
                    ei.bodyType != null ? Span.styled(" (" + ei.bodyType + ")", Style.EMPTY.dim()) : Span.raw("")));
            for (String line : ei.body.split("\n")) {
                lines.add(Line.from(Span.raw(" " + line)));
            }
        }

        if (showHeaders && !ei.headers.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Headers", Theme.muted())));
            addKvLines(lines, ei.headers);
        }

        if (showProperties && !ei.properties.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Properties", Theme.muted())));
            addKvLines(lines, ei.properties);
        }

        if (showVariables && !ei.variables.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" Variables", Theme.muted())));
            addKvLines(lines, ei.variables);
        }

        Paragraph.Builder pb = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Info ").build());
        if (wordWrap) {
            pb.overflow(Overflow.WRAP_WORD);
        }
        frame.renderWidget(pb.build(), area);
    }

    private static void addKvLines(List<Line> lines, Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            String val = entry.getValue() != null ? entry.getValue().toString() : "null";
            lines.add(Line.from(
                    Span.styled(" " + entry.getKey(), Theme.muted()),
                    Span.raw(" = " + val)));
        }
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
        String[] messageHistory = selectedError.messageHistory;

        diagram.setLoadingPlaceholder();

        ctx.backgroundExecutor.execute(() -> {
            try {
                diagram.loadHighlightedNativeDiagramInBackground(ctx, pid, messageHistory, true, -1);
            } finally {
                diagram.endLoad();
            }
        });
    }

    @Override
    public String description() {
        return "Routing errors with exception details, stack traces, and exchange context";
    }

    @Override
    public String getHelpText() {
        return """
                # Errors

                The Errors tab shows exchanges that have failed with exceptions. This is
                your primary tool for debugging integration problems — it captures the
                exception, the exchange state at the time of failure, and the full path
                the message took through the route before it failed.

                Camel maintains an error registry that keeps the most recent errors
                (up to a configurable limit). Older errors are automatically purged.

                ## Error List

                - **ID** — Exchange identifier (e.g., `ID-myhost-1234-5`). This uniquely identifies the message that failed
                - **AGO** — How long ago the error occurred (e.g., `5s`, `2m`, `1h`). Recent errors appear first by default
                - **ROUTE** — Route where the error happened
                - **NODE** — The specific processor/node in the route where the exception was thrown (e.g., `to1`, `bean2`, `process3`). This tells you exactly which step failed
                - **HANDLED** — Whether the error was caught by Camel's error handling: `true` (handled) or `false` (unhandled)
                - **EXCEPTION** — Exception class name in short form (e.g., `IOException`, `HttpOperationFailedException`)
                - **MESSAGE** — The exception message text explaining what went wrong

                ## Example Screen

                ```
                 ID                  AGO  ROUTE        NODE   HANDLED  EXCEPTION    MESSAGE
                 ID-myhost-1234-5    5s   timer-route  to1    false    IOException  Connection refused
                 ID-myhost-1234-3    2m   kafka-route  bean2  true     NPE          null reference in processor
                ```

                ## Understanding HANDLED

                - **handled=true**: The error was caught by Camel's error handling
                  mechanism. This includes:
                  - `onException` blocks that handle specific exception types
                  - `errorHandler` configured on the route or context
                  - `deadLetterChannel` that redirects failed messages to an error queue
                  - `doTry/doCatch` blocks in the route

                  The route continued executing (or the message was redirected),
                  so the caller may not even know an error occurred.

                - **handled=false**: The error was not caught by any error handler.
                  The exchange failed completely and the exception propagated back to
                  the caller. For a consumer like Kafka, this might trigger a message
                  redelivery. For an HTTP endpoint, the caller gets a 500 error.

                ## Detail View

                Press `Enter` on an error to see the full details:

                - **Stack trace**: Complete Java exception chain showing exactly where
                  in the code the error occurred. Look for your own classes and Camel
                  component classes — framework internals can usually be skipped
                - **Message History**: Step-by-step trace showing every node the
                  exchange visited before failing. This is invaluable for understanding
                  the path the message took:

                ```
                  RouteId     ProcessorId  Processor       Elapsed
                  my-route    from1        timer://tick    0ms
                  my-route    setBody1     setBody         0ms
                  my-route    to1          http://api      5023ms  <-- failed here
                ```

                  The last entry is where the failure occurred. The elapsed time for
                  that step often reveals the problem (e.g., 5023ms suggests a
                  connection timeout).

                - **Headers**: Exchange message headers at the time of failure
                - **Body**: Message body content
                - **Properties**: Exchange-level properties
                - **Variables**: Exchange variables

                Use `h`, `b`, `p`, `v` keys to toggle each section.

                ## Common Error Patterns

                - **Connection refused / timeout**: Downstream service is down or
                  unreachable. Check network and service status.
                - **HttpOperationFailedException**: HTTP call returned an error
                  status code. Check the response body for details.
                - **TypeConversionException**: Camel cannot convert a message to the
                  required type. Check message format and data types.
                - **NullPointerException**: A processor received unexpected null data.
                  Check if the message body or headers are populated correctly.

                ## Error Diagram

                Press `d` in the detail view to open a visual route diagram showing
                the path the failed exchange took through the route. Visited nodes are
                highlighted in red to trace the error path.

                Use `Up/Down` arrow keys to step through the visited nodes one by one —
                the diagram progressively highlights each step in order, showing exactly
                how the exchange flowed through the route before failing. The current
                step is shown with a selection highlight. Use `Left/Right` to scroll
                the diagram horizontally if it extends beyond the screen.

                **Info Panel** — An info panel on the left side of the diagram shows
                metadata for the selected error: exchange ID, route, node, elapsed
                time, thread, handled status, and the full exception. It also shows
                body, headers, properties, and variables respecting the `b/h/p/v`
                toggles. Press `i` to cycle the panel size: narrow (35 chars),
                wide (half screen), or full (entire area). In wide mode, the minimap
                and tree preview are hidden. Word wrap (`w`) is also supported.

                Press `d` to close the diagram and return to the detail view.
                Press `Esc` to navigate back one route in drill-down mode.

                ## Keys

                - `Up/Down` — select error (list) / navigate path steps (diagram)
                - `Enter` — view error details
                - `d` — toggle error diagram (open and close)
                - `Esc` — back to list / back one route in diagram drill-down
                - `i` — cycle info panel size (narrow / wide / full) in diagram
                - `h` — toggle headers
                - `b` — toggle body
                - `p` — toggle properties
                - `v` — toggle variables
                - `w` — toggle word wrap
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
        result.put("tab", "Errors");
        JsonArray rows = new JsonArray();
        for (ErrorInfo ei : info.errors) {
            JsonObject row = new JsonObject();
            row.put("routeId", ei.routeId);
            row.put("nodeId", ei.nodeId);
            row.put("exchangeId", ei.exchangeId);
            row.put("handled", ei.handled);
            row.put("timestamp", ei.timestamp);
            row.put("elapsed", ei.elapsed);
            if (ei.exceptionType != null) {
                row.put("exceptionType", ei.exceptionType);
            }
            if (ei.exceptionMessage != null) {
                row.put("exceptionMessage", ei.exceptionMessage);
            }
            if (ei.stackTrace != null) {
                row.put("stackTrace", ei.stackTrace);
            }
            if (ei.body != null) {
                row.put("body", ei.body);
            }
            if (!ei.headers.isEmpty()) {
                row.put("headers", new JsonObject(ei.headers));
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.errors.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
