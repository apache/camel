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
import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.diagram.TopologyAsciiRenderer;
import org.apache.camel.diagram.TopologyHelper;
import org.apache.camel.diagram.TopologyImageRenderer;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class DiagramSupport {

    private boolean showDiagram;
    private boolean diagramTextMode;
    private boolean topologyMode;
    private boolean showDescription;
    private List<RouteDiagramAsciiRenderer.CounterPos> counterPositions = Collections.emptyList();
    private Set<Integer> routeTitleRows = Collections.emptySet();
    private List<String> lines = Collections.emptyList();
    private int scrollY;
    private int scrollX;
    private final ScrollbarState vScrollState = new ScrollbarState();
    private final ScrollbarState hScrollState = new ScrollbarState();
    private ImageData imageData;
    private ImageData fullImageData;
    private ImageProtocol protocol;
    private int cropX = -1;
    private int cropY = -1;
    private int cropW = -1;
    private int cropH = -1;
    private final AtomicBoolean loading = new AtomicBoolean(false);

    boolean isShowDiagram() {
        return showDiagram;
    }

    boolean isDiagramTextMode() {
        return diagramTextMode;
    }

    boolean isTopologyMode() {
        return topologyMode;
    }

    void setTopologyMode(boolean topologyMode) {
        this.topologyMode = topologyMode;
    }

    boolean isShowDescription() {
        return showDescription;
    }

    void setShowDescription(boolean showDescription) {
        this.showDescription = showDescription;
    }

    boolean hasDiagramData() {
        return diagramTextMode ? !lines.isEmpty() : fullImageData != null;
    }

    ImageData getFullImageData() {
        return fullImageData;
    }

    boolean handleScrollKeys(KeyEvent ke) {
        if (!showDiagram) {
            return false;
        }
        if (ke.isUp()) {
            scrollY = Math.max(0, scrollY - 1);
            return true;
        }
        if (ke.isDown()) {
            scrollY++;
            return true;
        }
        if (ke.isPageUp()) {
            scrollY = Math.max(0, scrollY - 20);
            return true;
        }
        if (ke.isPageDown()) {
            scrollY += 20;
            return true;
        }
        if (ke.isLeft()) {
            scrollX = Math.max(0, scrollX - 1);
            return true;
        }
        if (ke.isRight()) {
            scrollX++;
            return true;
        }
        if (ke.isHome()) {
            scrollY = 0;
            scrollX = 0;
            return true;
        }
        if (ke.isEnd()) {
            scrollY = Integer.MAX_VALUE;
            return true;
        }
        return false;
    }

    void toggleImageDiagram(Runnable loadTrigger) {
        if (showDiagram) {
            close();
        } else {
            diagramTextMode = false;
            loadTrigger.run();
        }
    }

    void toggleTextDiagram(Runnable loadTrigger) {
        if (showDiagram) {
            close();
        } else {
            diagramTextMode = true;
            loadTrigger.run();
        }
    }

    void switchToImageMode() {
        diagramTextMode = false;
        showDiagram = true;
    }

    void switchToTextMode() {
        diagramTextMode = true;
        showDiagram = true;
    }

    boolean handleEscape() {
        if (showDiagram) {
            close();
            return true;
        }
        return false;
    }

    void close() {
        showDiagram = false;
        imageData = null;
        fullImageData = null;
    }

    void reset() {
        close();
        lines = Collections.emptyList();
        scrollY = 0;
        scrollX = 0;
    }

    void renderFooterHints(List<Span> spans) {
        String closeKey = diagramTextMode ? "D" : "d";
        hint(spans, closeKey + "/Esc", "close");
        hint(spans, "↑↓←→", "scroll");
        hint(spans, "PgUp/PgDn", "page");
        hint(spans, "Home/End", "top/end");
    }

    // ---- Rendering ----

    void renderDiagram(Frame frame, Rect area, String title) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .build();

        if (fullImageData != null) {
            renderImageDiagram(frame, area, block);
            return;
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, CharWidth.of(line));
        }

        Rect inner = block.inner(area);
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);

        int maxVScroll = Math.max(0, lines.size() - visibleLines);
        int maxHScroll = Math.max(0, maxWidth - visibleCols);
        scrollY = Math.min(scrollY, maxVScroll);
        scrollX = Math.min(scrollX, maxHScroll);

        List<Line> rendered = new ArrayList<>();
        int end = Math.min(scrollY + visibleLines, lines.size());
        for (int i = scrollY; i < end; i++) {
            String line = lines.get(i);
            if (scrollX > 0) {
                line = scrollX < line.length() ? line.substring(scrollX) : "";
            }
            rendered.add(styleDiagramLine(line, i, scrollX));
        }

        frame.renderWidget(block, area);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(rendered))
                .build();
        frame.renderWidget(paragraph, hChunks.get(0));

        vScrollState.contentLength(lines.size());
        vScrollState.viewportContentLength(visibleLines);
        vScrollState.position(scrollY);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), vScrollState);

        if (maxWidth > visibleCols) {
            hScrollState.contentLength(maxWidth);
            hScrollState.viewportContentLength(visibleCols);
            hScrollState.position(scrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), hScrollState);
        }
    }

    private void renderImageDiagram(Frame frame, Rect area, Block block) {
        int imgW = fullImageData.width();
        int imgH = fullImageData.height();

        Rect inner = block.inner(area);
        int pxPerCol = protocol.resolution().widthMultiplier();
        int pxPerRow = protocol.resolution().heightMultiplier();
        int viewCols = Math.max(1, inner.width() - 1);
        int viewRows = Math.max(1, inner.height() - 1);
        int viewW = viewCols * pxPerCol;
        int viewH = viewRows * pxPerRow;

        int maxScrollY = Math.max(0, (imgH - viewH + pxPerRow - 1) / pxPerRow);
        int maxScrollX = Math.max(0, (imgW - viewW + pxPerCol - 1) / pxPerCol);
        scrollY = Math.min(scrollY, maxScrollY);
        scrollX = Math.min(scrollX, maxScrollX);

        int cx = Math.min(scrollX * pxPerCol, imgW);
        int cy = Math.min(scrollY * pxPerRow, imgH);
        int cw = Math.min(viewW, imgW - cx);
        int ch = Math.min(viewH, imgH - cy);

        if (cw > 0 && ch > 0) {
            if (cx != cropX || cy != cropY || cw != cropW || ch != cropH) {
                imageData = fullImageData.crop(cx, cy, cw, ch);
                cropX = cx;
                cropY = cy;
                cropW = cw;
                cropH = ch;
            }
        } else if (imageData != fullImageData) {
            imageData = fullImageData;
        }

        frame.renderWidget(block, area);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        Image img = Image.builder()
                .data(imageData)
                .protocol(protocol)
                .scaling(ImageScaling.FIT)
                .build();
        frame.renderWidget(img, hChunks.get(0));

        int totalRows = (imgH + pxPerRow - 1) / pxPerRow;
        vScrollState.contentLength(totalRows);
        vScrollState.viewportContentLength(viewRows);
        vScrollState.position(scrollY);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), vScrollState);

        if (imgW > viewW) {
            int totalCols = (imgW + pxPerCol - 1) / pxPerCol;
            hScrollState.contentLength(totalCols);
            hScrollState.viewportContentLength(viewCols);
            hScrollState.position(scrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), hScrollState);
        }
    }

    // ---- Async loading ----

    boolean beginLoad() {
        return loading.compareAndSet(false, true);
    }

    void endLoad() {
        loading.set(false);
    }

    void setLoadingPlaceholder() {
        lines = List.of("(Loading diagram...)");
        imageData = null;
        fullImageData = null;
        showDiagram = true;
        scrollY = 0;
        scrollX = 0;
    }

    void loadHighlightedDiagramInBackground(
            MonitorContext ctx, String pid, boolean textMode,
            String[] messageHistory, RouteDiagramHelper.HighlightStyle hlStyle) {
        JsonObject jo = requestRouteStructure(ctx, pid);
        if (jo == null) {
            applyResult(ctx, List.of("(No response from integration)"), null, null, null);
            return;
        }

        RouteDiagramHelper.HighlightInfo highlightInfo
                = RouteDiagramHelper.parseMessageHistory(messageHistory, hlStyle);
        Set<String> nodeIds = new LinkedHashSet<>(highlightInfo.getNodeIds());

        List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(jo);
        if (routes.isEmpty()) {
            applyResult(ctx, List.of("(No routes in response)"), null, null, null);
            return;
        }

        for (RouteDiagramLayoutEngine.RouteInfo ri : routes) {
            boolean routeHasHighlight = ri.nodes.stream().anyMatch(n -> n.id != null && nodeIds.contains(n.id));
            if (routeHasHighlight) {
                addParentNodes(ri.nodes, nodeIds);
            }
        }

        RouteDiagramHelper.HighlightInfo fullHighlight
                = new RouteDiagramHelper.HighlightInfo(nodeIds, highlightInfo.getRouteOrder(), hlStyle);
        routes = RouteDiagramHelper.filterAndOrderRoutes(routes, fullHighlight);
        if (routes.isEmpty()) {
            applyResult(ctx, List.of("(No routes contain highlighted nodes)"), null, null, null);
            return;
        }

        renderRoutes(ctx, textMode, routes, false, nodeIds, hlStyle);
    }

    void loadRouteDiagramInBackground(
            MonitorContext ctx, String pid, boolean textMode,
            String routeId, boolean metrics) {
        JsonObject jo = requestRouteStructure(ctx, pid);
        if (jo == null) {
            applyResult(ctx, List.of("(No response from integration)"), null, null, null);
            return;
        }

        List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(jo);
        if (routes.isEmpty()) {
            applyResult(ctx, List.of("(No routes in response)"), null, null, null);
            return;
        }

        if (routeId != null) {
            routes.removeIf(r -> !routeId.equals(r.routeId));
        }

        renderRoutes(ctx, textMode, routes, metrics, null, null);
    }

    void loadTopologyDiagramInBackground(
            MonitorContext ctx, String pid, boolean textMode, boolean metrics) {
        JsonObject jo = requestRouteTopology(ctx, pid);
        if (jo == null) {
            applyResult(ctx, List.of("(No response from integration)"), null, null, null);
            return;
        }

        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(jo);
        if (nodes.isEmpty()) {
            applyResult(ctx, List.of("(No routes in response)"), null, null, null);
            return;
        }

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        if (textMode) {
            TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                    engine.getNodeWidth(), true, metrics, showDescription);
            String text = renderer.renderDiagramPlain(result);

            List<String> resultLines = new ArrayList<>();
            List<RouteDiagramAsciiRenderer.CounterPos> positions = new ArrayList<>();
            String[] rawLines = text.split("\n", -1);
            int[] rowMapping = new int[rawLines.length];
            int newRow = 0;
            for (int i = 0; i < rawLines.length; i++) {
                if (!rawLines[i].isEmpty()) {
                    rowMapping[i] = newRow++;
                    resultLines.add(rawLines[i]);
                } else {
                    rowMapping[i] = -1;
                }
            }

            for (TopologyAsciiRenderer.CounterPos cp : renderer.getCounterPositions()) {
                if (cp.row() >= 0 && cp.row() < rowMapping.length && rowMapping[cp.row()] >= 0) {
                    RouteDiagramAsciiRenderer.CounterType mapped = switch (cp.type()) {
                        case OK -> RouteDiagramAsciiRenderer.CounterType.OK;
                        case FAIL -> RouteDiagramAsciiRenderer.CounterType.FAIL;
                        case TRIGGER -> RouteDiagramAsciiRenderer.CounterType.HIGHLIGHT_SUCCESS;
                        case EXTERNAL -> RouteDiagramAsciiRenderer.CounterType.OK;
                    };
                    positions.add(new RouteDiagramAsciiRenderer.CounterPos(
                            rowMapping[cp.row()], cp.col(), cp.length(), mapped));
                }
            }

            applyResult(ctx, resultLines, null, null, null, positions, Collections.emptySet());
        } else {
            TerminalImageCapabilities caps = TerminalImageCapabilities.detect();
            if (caps.supportsNativeImages()) {
                RouteDiagramRenderer.DiagramColors colors
                        = RouteDiagramRenderer.DiagramColors.parse("transparent");
                java.awt.image.BufferedImage image = TopologyImageRenderer.renderImage(
                        result, colors, TopologyLayoutEngine.DEFAULT_FONT_SIZE,
                        TopologyLayoutEngine.DEFAULT_NODE_WIDTH, metrics, false);
                ImageData full = ImageData.fromBufferedImage(image);
                ImageData resized = full.resize(full.width() / 2, full.height() / 2);
                ImageProtocol proto = caps.bestProtocol();
                applyResult(ctx, Collections.emptyList(), resized, resized, proto);
            } else {
                applyResult(ctx, List.of(
                        "(Terminal does not support image rendering)",
                        "(Press Shift+D for text diagram)"), null, null, null);
            }
        }
    }

    private JsonObject requestRouteTopology(MonitorContext ctx, String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-topology");
        root.put("metric", "true");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);
        return jo;
    }

    private void renderRoutes(
            MonitorContext ctx, boolean textMode,
            List<RouteDiagramLayoutEngine.RouteInfo> routes, boolean metrics,
            Set<String> highlightNodeIds, RouteDiagramHelper.HighlightStyle hlStyle) {
        if (textMode) {
            RouteDiagramLayoutEngine.NodeLabelMode labelMode = showDescription
                    ? RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION
                    : RouteDiagramLayoutEngine.NodeLabelMode.CODE;
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                    RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                    labelMode);

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
                        RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE, true, metrics);
                String ascii;
                if (highlightNodeIds != null && hlStyle != null) {
                    ascii = asciiRenderer.renderDiagram(List.of(lr),
                            lr.maxY + RouteDiagramLayoutEngine.V_GAP, highlightNodeIds, hlStyle);
                } else {
                    ascii = asciiRenderer.renderDiagram(List.of(lr),
                            lr.maxY + RouteDiagramLayoutEngine.V_GAP);
                }
                List<RouteDiagramAsciiRenderer.CounterPos> origPositions = asciiRenderer.getCounterPositions();

                String[] rawLines = ascii.split("\n", -1);
                int[] rowMapping = new int[rawLines.length];
                int newRow = result.size();
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

            applyResult(ctx, result, null, null, null, positions, titleRows);
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
                        RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE * RouteDiagramLayoutEngine.SCALE, metrics);
                RouteDiagramRenderer.DiagramColors colors
                        = RouteDiagramRenderer.DiagramColors.parse("transparent");
                java.awt.image.BufferedImage image;
                if (highlightNodeIds != null && hlStyle != null) {
                    image = renderer.renderDiagram(layoutRoutes, totalHeight, colors, highlightNodeIds, hlStyle);
                } else {
                    image = renderer.renderDiagram(layoutRoutes, totalHeight, colors);
                }
                ImageData full = ImageData.fromBufferedImage(image);
                ImageData resized = full.resize(full.width() / 2, full.height() / 2);
                ImageProtocol proto = caps.bestProtocol();
                applyResult(ctx, Collections.emptyList(), resized, resized, proto);
            } else {
                applyResult(ctx, List.of(
                        "(Terminal does not support image rendering)",
                        "(Press Shift+D for text diagram)"), null, null, null);
            }
        }
    }

    private JsonObject requestRouteStructure(MonitorContext ctx, String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", false);
        root.put("metric", true);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);
        return jo;
    }

    private void applyResult(
            MonitorContext ctx,
            List<String> resultLines, ImageData resultImageData, ImageData resultFullImageData,
            ImageProtocol resultProtocol) {
        applyResult(ctx, resultLines, resultImageData, resultFullImageData, resultProtocol,
                Collections.emptyList(), Collections.emptySet());
    }

    private void applyResult(
            MonitorContext ctx,
            List<String> resultLines, ImageData resultImageData, ImageData resultFullImageData,
            ImageProtocol resultProtocol,
            List<RouteDiagramAsciiRenderer.CounterPos> positions, Set<Integer> titleRows) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            boolean wasShowing = showDiagram;
            lines = resultLines;
            counterPositions = positions;
            routeTitleRows = titleRows;
            fullImageData = resultFullImageData;
            protocol = resultProtocol;
            if (!wasShowing) {
                imageData = resultImageData;
                scrollY = 0;
                scrollX = 0;
                cropX = -1;
                cropY = -1;
                cropW = -1;
                cropH = -1;
            } else {
                showDiagram = true;
                // invalidate crop cache so next render re-crops at current scroll position
                cropX = -1;
                cropY = -1;
                cropW = -1;
                cropH = -1;
            }
        });
    }

    // ---- Diagram styling ----

    private Line styleDiagramLine(String text, int row, int hScrollX) {
        if (routeTitleRows.contains(row)) {
            return Line.from(Span.styled(text, Style.EMPTY.fg(Color.WHITE).bold()));
        }

        List<int[]> counterRanges = new ArrayList<>();
        for (RouteDiagramAsciiRenderer.CounterPos cp : counterPositions) {
            if (cp.row() == row) {
                int start = cp.col() - hScrollX;
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

    private static void addStyledSegment(
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

    static Color getDiagramNodeColor(String type) {
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

    static void addParentNodes(List<RouteDiagramLayoutEngine.NodeInfo> nodes, Set<String> nodeIds) {
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
