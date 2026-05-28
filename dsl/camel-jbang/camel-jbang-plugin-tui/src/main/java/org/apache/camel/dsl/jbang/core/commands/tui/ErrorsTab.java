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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.image.Image;
import dev.tamboui.image.ImageData;
import dev.tamboui.image.ImageScaling;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import dev.tamboui.image.protocol.ImageProtocol;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;
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

    // Diagram state
    private boolean showDiagram;
    private boolean diagramTextMode;
    private List<RouteDiagramAsciiRenderer.CounterPos> diagramCounterPositions = Collections.emptyList();
    private Set<Integer> diagramRouteTitleRows = Collections.emptySet();
    private List<String> diagramLines = Collections.emptyList();
    private int diagramScroll;
    private int diagramScrollX;
    private final ScrollbarState diagramVScrollState = new ScrollbarState();
    private final ScrollbarState diagramHScrollState = new ScrollbarState();
    private ImageData diagramImageData;
    private ImageData diagramFullImageData;
    private ImageProtocol diagramProtocol;
    private int diagramCropX = -1;
    private int diagramCropY = -1;
    private int diagramCropW = -1;
    private int diagramCropH = -1;
    private final AtomicBoolean diagramLoading = new AtomicBoolean(false);

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
        // Diagram scrolling
        if (showDiagram) {
            if (ke.isUp()) {
                diagramScroll = Math.max(0, diagramScroll - 1);
                return true;
            }
            if (ke.isDown()) {
                diagramScroll++;
                return true;
            }
            if (ke.isPageUp()) {
                diagramScroll = Math.max(0, diagramScroll - 20);
                return true;
            }
            if (ke.isPageDown()) {
                diagramScroll += 20;
                return true;
            }
            if (ke.isLeft()) {
                diagramScrollX = Math.max(0, diagramScrollX - 1);
                return true;
            }
            if (ke.isRight()) {
                diagramScrollX++;
                return true;
            }
            if (ke.isHome()) {
                diagramScroll = 0;
                diagramScrollX = 0;
                return true;
            }
            if (ke.isEnd()) {
                diagramScroll = Integer.MAX_VALUE;
                return true;
            }
        }

        // Image diagram toggle
        if (ke.isChar('d')) {
            if (showDiagram) {
                showDiagram = false;
                diagramImageData = null;
                diagramFullImageData = null;
            } else {
                diagramTextMode = false;
                loadDiagramForSelectedError();
            }
            return true;
        }
        // Text diagram toggle
        if (ke.isChar('D')) {
            if (showDiagram) {
                showDiagram = false;
                diagramImageData = null;
                diagramFullImageData = null;
            } else {
                diagramTextMode = true;
                loadDiagramForSelectedError();
            }
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
        if (showDiagram) {
            showDiagram = false;
            diagramImageData = null;
            diagramFullImageData = null;
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
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        // Fullscreen diagram mode
        if (showDiagram && (diagramTextMode ? !diagramLines.isEmpty() : diagramFullImageData != null)) {
            renderDiagramView(frame, area);
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
        if (showDiagram) {
            String closeKey = diagramTextMode ? "D" : "d";
            hint(spans, closeKey + "/Esc", "close");
            hint(spans, "↑↓←→", "scroll");
            hint(spans, "PgUp/PgDn", "page");
            hintLast(spans, "Home/End", "top/bottom");
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
            hint(spans, "1-0", "tabs");
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
        if (!diagramLoading.compareAndSet(false, true)) {
            return;
        }

        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.errors.isEmpty()) {
            diagramLoading.set(false);
            return;
        }

        List<ErrorInfo> sorted = applyFilter(info.errors);
        Integer sel = tableState.selected();
        ErrorInfo selectedError = null;
        if (sel != null && sel >= 0 && sel < sorted.size()) {
            selectedError = sorted.get(sel);
        }
        if (selectedError == null || selectedError.messageHistory == null || selectedError.messageHistory.length == 0) {
            diagramLoading.set(false);
            return;
        }

        String pid = ctx.selectedPid;
        boolean textMode = diagramTextMode;
        String[] messageHistory = selectedError.messageHistory;

        boolean initialLoad = !showDiagram;
        if (initialLoad) {
            diagramLines = List.of("(Loading diagram...)");
            diagramImageData = null;
            diagramFullImageData = null;
            showDiagram = true;
            diagramScroll = 0;
            diagramScrollX = 0;
        }

        ctx.runner.scheduler().execute(() -> {
            try {
                loadDiagramInBackground(pid, textMode, messageHistory);
            } finally {
                diagramLoading.set(false);
            }
        });
    }

    private void loadDiagramInBackground(String pid, boolean textMode, String[] messageHistory) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", false);
        root.put("metric", false);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applyDiagramResult(List.of("(No response from integration)"), null, null, null);
            return;
        }

        RouteDiagramHelper.HighlightStyle hlStyle = RouteDiagramHelper.HighlightStyle.FAIL;
        RouteDiagramHelper.HighlightInfo highlightInfo = RouteDiagramHelper.parseMessageHistory(messageHistory, hlStyle);
        Set<String> nodeIds = new LinkedHashSet<>(highlightInfo.getNodeIds());

        List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(jo);
        if (routes.isEmpty()) {
            applyDiagramResult(List.of("(No routes in response)"), null, null, null);
            return;
        }

        // add structural parent nodes for highlighted routes
        for (RouteDiagramLayoutEngine.RouteInfo ri : routes) {
            boolean routeHasHighlight = ri.nodes.stream().anyMatch(n -> n.id != null && nodeIds.contains(n.id));
            if (routeHasHighlight) {
                addParentNodes(ri.nodes, nodeIds);
            }
        }

        // filter and order routes by highlighted path
        RouteDiagramHelper.HighlightInfo fullHighlight
                = new RouteDiagramHelper.HighlightInfo(nodeIds, highlightInfo.getRouteOrder(), hlStyle);
        routes = RouteDiagramHelper.filterAndOrderRoutes(routes, fullHighlight);
        if (routes.isEmpty()) {
            applyDiagramResult(List.of("(No routes contain highlighted nodes from error history)"), null, null, null);
            return;
        }

        if (textMode) {
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                    RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                    RouteDiagramLayoutEngine.NodeLabelMode.CODE);

            List<String> result = new ArrayList<>();
            List<RouteDiagramAsciiRenderer.CounterPos> positions = new ArrayList<>();
            Set<Integer> titleRows = new HashSet<>();

            int currentY = RouteDiagramLayoutEngine.PADDING;
            for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
                if (!result.isEmpty()) {
                    result.add("");
                    result.add("");
                }

                int titleRow = result.size();

                RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, currentY);
                currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;

                RouteDiagramAsciiRenderer asciiRenderer = new RouteDiagramAsciiRenderer(
                        RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE, true, false);
                String ascii = asciiRenderer.renderDiagram(List.of(lr),
                        lr.maxY + RouteDiagramLayoutEngine.V_GAP, nodeIds, hlStyle);
                List<RouteDiagramAsciiRenderer.CounterPos> origPositions = asciiRenderer.getCounterPositions();

                String[] rawLines = ascii.split("\n", -1);
                int[] rowMapping = new int[rawLines.length];
                int baseRow = result.size();
                int newRow = baseRow;
                for (int i = 0; i < rawLines.length; i++) {
                    if (!rawLines[i].isEmpty()) {
                        rowMapping[i] = newRow++;
                        result.add(rawLines[i]);
                    } else {
                        rowMapping[i] = -1;
                    }
                }
                for (RouteDiagramAsciiRenderer.CounterPos cp : origPositions) {
                    if (cp.row() >= 0 && cp.row() < rowMapping.length && rowMapping[cp.row()] >= 0) {
                        positions.add(new RouteDiagramAsciiRenderer.CounterPos(
                                rowMapping[cp.row()], cp.col(), cp.length(), cp.type()));
                    }
                }
                titleRows.add(titleRow);
            }

            applyDiagramResult(result, null, null, null, positions, titleRows);
        } else {
            TerminalImageCapabilities caps = TerminalImageCapabilities.detect();
            if (caps.supportsNativeImages()) {
                RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
                List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
                int totalHeight = 0;
                for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
                    RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, totalHeight);
                    layoutRoutes.add(lr);
                    totalHeight = lr.maxY;
                }
                RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                        engine.getNodeWidth(),
                        RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE * RouteDiagramLayoutEngine.SCALE, false);
                RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse("transparent");
                java.awt.image.BufferedImage image
                        = renderer.renderDiagram(layoutRoutes, totalHeight, colors, nodeIds, hlStyle);
                ImageData fullImage = ImageData.fromBufferedImage(image);
                ImageData resized = fullImage.resize(fullImage.width() / 2, fullImage.height() / 2);
                ImageProtocol protocol = caps.bestProtocol();
                applyDiagramResult(Collections.emptyList(), resized, resized, protocol);
            } else {
                applyDiagramResult(List.of(
                        "(Terminal does not support image rendering)",
                        "(Press Shift+D for text diagram)"), null, null, null);
            }
        }
    }

    private void applyDiagramResult(
            List<String> lines, ImageData imageData, ImageData fullImageData, ImageProtocol protocol) {
        applyDiagramResult(lines, imageData, fullImageData, protocol, Collections.emptyList(), Collections.emptySet());
    }

    private void applyDiagramResult(
            List<String> lines, ImageData imageData, ImageData fullImageData, ImageProtocol protocol,
            List<RouteDiagramAsciiRenderer.CounterPos> positions, Set<Integer> titleRows) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            boolean wasShowing = showDiagram;
            diagramLines = lines;
            diagramCounterPositions = positions;
            diagramRouteTitleRows = titleRows;
            diagramImageData = imageData;
            diagramFullImageData = fullImageData;
            diagramProtocol = protocol;
            if (!wasShowing) {
                diagramScroll = 0;
                diagramScrollX = 0;
                diagramCropX = -1;
                diagramCropY = -1;
                diagramCropW = -1;
                diagramCropH = -1;
            }
            if (wasShowing) {
                showDiagram = true;
            }
        });
    }

    private void renderDiagramView(Frame frame, Rect area) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Error Diagram ")
                .build();

        if (diagramFullImageData != null) {
            renderImageDiagram(frame, area, block);
            return;
        }

        int maxWidth = 0;
        for (String line : diagramLines) {
            maxWidth = Math.max(maxWidth, CharWidth.of(line));
        }

        Rect inner = block.inner(area);
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);

        int maxVScroll = Math.max(0, diagramLines.size() - visibleLines);
        int maxHScroll = Math.max(0, maxWidth - visibleCols);
        diagramScroll = Math.min(diagramScroll, maxVScroll);
        diagramScrollX = Math.min(diagramScrollX, maxHScroll);

        List<Line> lines = new ArrayList<>();
        int end = Math.min(diagramScroll + visibleLines, diagramLines.size());
        for (int i = diagramScroll; i < end; i++) {
            String line = diagramLines.get(i);
            if (diagramScrollX > 0) {
                line = diagramScrollX < line.length() ? line.substring(diagramScrollX) : "";
            }
            lines.add(styleDiagramLine(line, i, diagramScrollX));
        }

        frame.renderWidget(block, area);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .build();
        frame.renderWidget(paragraph, hChunks.get(0));

        diagramVScrollState.contentLength(diagramLines.size());
        diagramVScrollState.viewportContentLength(visibleLines);
        diagramVScrollState.position(diagramScroll);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), diagramVScrollState);

        if (maxWidth > visibleCols) {
            diagramHScrollState.contentLength(maxWidth);
            diagramHScrollState.viewportContentLength(visibleCols);
            diagramHScrollState.position(diagramScrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), diagramHScrollState);
        }
    }

    private void renderImageDiagram(Frame frame, Rect area, Block block) {
        int imgW = diagramFullImageData.width();
        int imgH = diagramFullImageData.height();

        Rect inner = block.inner(area);
        int pxPerCol = diagramProtocol.resolution().widthMultiplier();
        int pxPerRow = diagramProtocol.resolution().heightMultiplier();
        int viewCols = Math.max(1, inner.width() - 1);
        int viewRows = Math.max(1, inner.height() - 1);
        int viewW = viewCols * pxPerCol;
        int viewH = viewRows * pxPerRow;

        int maxScrollY = Math.max(0, (imgH - viewH + pxPerRow - 1) / pxPerRow);
        int maxScrollX = Math.max(0, (imgW - viewW + pxPerCol - 1) / pxPerCol);
        diagramScroll = Math.min(diagramScroll, maxScrollY);
        diagramScrollX = Math.min(diagramScrollX, maxScrollX);

        int cropX = Math.min(diagramScrollX * pxPerCol, imgW);
        int cropY = Math.min(diagramScroll * pxPerRow, imgH);
        int cropW = Math.min(viewW, imgW - cropX);
        int cropH = Math.min(viewH, imgH - cropY);

        if (cropW > 0 && cropH > 0) {
            if (cropX != diagramCropX || cropY != diagramCropY
                    || cropW != diagramCropW || cropH != diagramCropH) {
                diagramImageData = diagramFullImageData.crop(cropX, cropY, cropW, cropH);
                diagramCropX = cropX;
                diagramCropY = cropY;
                diagramCropW = cropW;
                diagramCropH = cropH;
            }
        } else if (diagramImageData != diagramFullImageData) {
            diagramImageData = diagramFullImageData;
        }

        frame.renderWidget(block, area);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        Image img = Image.builder()
                .data(diagramImageData)
                .protocol(diagramProtocol)
                .scaling(ImageScaling.FIT)
                .build();
        frame.renderWidget(img, hChunks.get(0));

        int totalRows = (imgH + pxPerRow - 1) / pxPerRow;
        diagramVScrollState.contentLength(totalRows);
        diagramVScrollState.viewportContentLength(viewRows);
        diagramVScrollState.position(diagramScroll);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), diagramVScrollState);

        if (imgW > viewW) {
            int totalCols = (imgW + pxPerCol - 1) / pxPerCol;
            diagramHScrollState.contentLength(totalCols);
            diagramHScrollState.viewportContentLength(viewCols);
            diagramHScrollState.position(diagramScrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), diagramHScrollState);
        }
    }

    // ---- Diagram styling ----

    private Line styleDiagramLine(String text, int row, int scrollX) {
        if (diagramRouteTitleRows.contains(row)) {
            return Line.from(Span.styled(text, Style.EMPTY.fg(Color.WHITE).bold()));
        }

        List<int[]> counterRanges = new ArrayList<>();
        for (RouteDiagramAsciiRenderer.CounterPos cp : diagramCounterPositions) {
            if (cp.row() == row) {
                int start = cp.col() - scrollX;
                int end = start + cp.length();
                if (end > 0 && start < text.length()) {
                    start = Math.max(0, start);
                    end = Math.min(end, text.length());
                    int colorFlag = switch (cp.type()) {
                        case OK, HIGHLIGHT_SUCCESS -> 1; // green
                        default -> 2; // red
                    };
                    counterRanges.add(new int[] { start, end, colorFlag });
                }
            }
        }

        List<Span> spans = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int open = text.indexOf('[', idx);
            if (open < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            int close = text.indexOf(']', open);
            if (close < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            if (open > idx) {
                addStyledSegment(spans, text, idx, open, counterRanges, Color.GRAY);
            }
            String tag = text.substring(open + 1, close);
            Color tagColor = getDiagramNodeColor(tag);
            spans.add(Span.styled("[" + tag + "]", Style.EMPTY.fg(tagColor).bold()));

            int afterTag = close + 1;
            int nextOpen = text.indexOf('[', afterTag);
            int labelEnd = nextOpen >= 0 ? nextOpen : text.length();
            if (afterTag < labelEnd) {
                addStyledSegment(spans, text, afterTag, labelEnd, counterRanges, Color.WHITE);
            }
            idx = labelEnd;
        }
        return Line.from(spans);
    }

    private void addStyledSegment(
            List<Span> spans, String text, int from, int to, List<int[]> counterRanges, Color defaultColor) {
        int pos = from;
        while (pos < to) {
            int[] cr = findNextCounterRange(counterRanges, pos, to);
            if (cr != null) {
                if (pos < cr[0]) {
                    spans.add(Span.styled(text.substring(pos, cr[0]), Style.EMPTY.fg(defaultColor)));
                }
                int counterEnd = Math.min(cr[1], to);
                Color counterColor = cr[2] == 1 ? Color.GREEN : Color.LIGHT_RED;
                spans.add(Span.styled(text.substring(cr[0], counterEnd), Style.EMPTY.fg(counterColor).bold()));
                pos = counterEnd;
            } else {
                spans.add(Span.styled(text.substring(pos, to), Style.EMPTY.fg(defaultColor)));
                pos = to;
            }
        }
    }

    private static int[] findNextCounterRange(List<int[]> ranges, int pos, int limit) {
        int[] best = null;
        for (int[] range : ranges) {
            if (range[1] > pos && range[0] < limit) {
                int start = Math.max(range[0], pos);
                if (best == null || start < best[0]) {
                    best = new int[] { start, range[1], range[2] };
                }
            }
        }
        return best;
    }

    private Color getDiagramNodeColor(String type) {
        if (type == null) {
            return Color.GRAY;
        }
        return switch (type) {
            case "from" -> Color.GREEN;
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> Color.CYAN;
            case "choice", "when", "otherwise" -> Color.YELLOW;
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                Color.CYAN;
            case "bean", "process", "log", "script", "delay" -> Color.MAGENTA;
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                Color.rgb(0x89, 0x57, 0xE5);
            default -> Color.GRAY;
        };
    }

    private static void addParentNodes(List<RouteDiagramLayoutEngine.NodeInfo> nodes, Set<String> nodeIds) {
        for (int i = 0; i < nodes.size(); i++) {
            RouteDiagramLayoutEngine.NodeInfo node = nodes.get(i);
            if (node.id == null || nodeIds.contains(node.id)) {
                continue;
            }
            boolean hasHighlightedChild = false;
            for (int j = i + 1; j < nodes.size(); j++) {
                RouteDiagramLayoutEngine.NodeInfo child = nodes.get(j);
                if (child.level <= node.level) {
                    break;
                }
                if (child.id != null && nodeIds.contains(child.id)) {
                    hasHighlightedChild = true;
                    break;
                }
            }
            if (hasHighlightedChild) {
                nodeIds.add(node.id);
            }
        }
    }
}
