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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.widget.Widget;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutEdge;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.dsl.jbang.core.commands.tui.Theme;

import static org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors.*;

public class TopologyDiagramWidget implements Widget {

    private static final int Y_SCALE = 20;
    private static final int MIN_BOX_WIDTH = 16;
    private static final int X_DIVISOR = 15;
    private static final int MAX_WRAP_LINES = 3;
    private static final String AI_MARK = "\u2726 ";

    private final TopologyLayoutResult layout;
    private final int nodeWidth;
    private final int boxWidth;
    private final int selectedNodeIndex;
    private final int scrollX;
    private final int scrollY;
    private final boolean showMetrics;
    private final boolean showDescription;
    private final Set<String> highlightRouteIds;
    private final boolean highlightFailed;

    private final List<NodeBox> nodeBoxes = new ArrayList<>();
    private Map<String, String> aiDescriptions = Map.of();
    private Map<String, List<NodeLine>> nodeLines = Map.of();
    private Map<String, NodeLine> groupTags = Map.of();
    private Map<String, Color> groupColors = Map.of();
    private Style aiStyle = Style.EMPTY.italic();
    private ErrorLayer errorLayer;
    // the gutter on the left the error paths go down, when the error layer is shown
    private int colOffset;
    private static final int GUTTER = 3;

    public record NodeBox(String routeId, int startRow, int endRow, int startCol, int endCol, int layer) {
    }

    /** A line of a box's own content, in its own style. */
    public record NodeLine(String text, Style style) {
    }

    public TopologyDiagramWidget(
                                 TopologyLayoutResult layout, int nodeWidth,
                                 int selectedNodeIndex, int scrollX, int scrollY,
                                 boolean showMetrics, boolean showDescription) {
        this(layout, nodeWidth, selectedNodeIndex, scrollX, scrollY, showMetrics, showDescription,
             Collections.emptySet(), false);
    }

    public TopologyDiagramWidget(
                                 TopologyLayoutResult layout, int nodeWidth,
                                 int selectedNodeIndex, int scrollX, int scrollY,
                                 boolean showMetrics, boolean showDescription,
                                 Set<String> highlightRouteIds, boolean highlightFailed) {
        this.layout = layout;
        this.nodeWidth = nodeWidth;
        this.boxWidth = Math.max(MIN_BOX_WIDTH, nodeWidth / X_DIVISOR);
        this.selectedNodeIndex = selectedNodeIndex;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.showMetrics = showMetrics;
        this.showDescription = showDescription;
        this.highlightRouteIds = highlightRouteIds;
        this.highlightFailed = highlightFailed;
    }

    /**
     * Descriptions an AI suggested for routes that have none, by route id, shown with a mark in the given style when
     * descriptions are shown, so they are not taken for what the route says.
     */
    public TopologyDiagramWidget withAiDescriptions(Map<String, String> descriptions, Style style) {
        this.aiDescriptions = descriptions != null ? descriptions : Map.of();
        this.aiStyle = style != null ? style : this.aiStyle;
        return this;
    }

    /**
     * The content of the boxes, by node id, instead of the route id and endpoint: for diagrams whose nodes are not
     * routes, such as the architecture view. At most four lines a box.
     */
    public TopologyDiagramWidget withNodeLines(Map<String, List<NodeLine>> lines) {
        this.nodeLines = lines != null ? lines : Map.of();
        return this;
    }

    /**
     * The group of each route (a route group, an AI capability, shared services, utility), shown as a tag line in the
     * box and as the colour of its border.
     */
    public TopologyDiagramWidget withGroups(Map<String, NodeLine> tags, Map<String, Color> colors) {
        this.groupTags = tags != null ? tags : Map.of();
        this.groupColors = colors != null ? colors : Map.of();
        return this;
    }

    /** The AI description a route node shows instead of its id, or null. */
    private String aiDescription(TopologyLayoutNode node) {
        if (!showDescription || isExternal(node) || node.routeId == null
                || node.description != null && !node.description.isBlank()) {
            return null;
        }
        String ai = aiDescriptions.get(node.routeId);
        return ai != null && !ai.isBlank() ? AI_MARK + ai : null;
    }

    /** A description as a box shows it: at most two lines, cut with an ellipsis. */
    private List<String> labelLines(String label) {
        List<String> wrapped = wrapText(label, boxWidth - 4);
        if (wrapped.size() <= 2) {
            return wrapped;
        }
        String second = wrapped.get(1);
        int max = Math.max(1, boxWidth - 4 - 3);
        return List.of(wrapped.get(0), (second.length() > max ? second.substring(0, max) : second) + "...");
    }

    /** A box's lines and which of them are the AI label, the route id and the group tag (-1 when none). */
    private record TextLines(List<String> lines, int aiLines, int idLine, int tagLine, NodeLine tag) {
    }

    /**
     * The lines of a route box: the label and route id (descriptions on) or the route id and its endpoint, then the
     * group tag when groups are shown, then metrics; at most {@code MAX_WRAP_LINES + 1}. Drawing and the box height
     * both use it, so they agree.
     */
    private TextLines textLines(TopologyLayoutNode node, boolean ext) {
        String line1;
        String ai = aiDescription(node);
        if (ext) {
            line1 = node.from;
        } else if (ai != null) {
            line1 = ai;
        } else if (showDescription && node.description != null && !node.description.isBlank()) {
            line1 = node.description;
        } else {
            line1 = node.routeId;
        }
        // a route in the error frame needs no group tag: the frame says what it is
        NodeLine tag = ext || node.routeId == null || inFrame(node) ? null : groupTags.get(node.routeId);

        List<String> lines = new ArrayList<>(wrapText(line1, boxWidth - 4));
        int aiLines = ai != null ? lines.size() : 0;
        int idLine = -1;
        boolean labelled = !ext && !line1.equals(node.routeId);
        if (labelled) {
            // a description is a label: at most two lines (one when the group is shown), then the route id
            List<String> label = labelLines(line1);
            if (tag != null && label.size() > 1) {
                int max = Math.max(1, boxWidth - 4 - 3);
                String first = label.get(0);
                label = List.of((first.length() > max ? first.substring(0, max) : first) + "...");
            }
            lines = new ArrayList<>(label);
            aiLines = ai != null ? lines.size() : 0;
            idLine = lines.size();
            lines.add(node.routeId);
        }
        if (!ext && !showDescription) {
            String line2 = "(" + node.from + ")";
            List<String> fromLines = wrapText(line2, boxWidth - 4);
            if (tag != null) {
                // room for the group: the endpoint on one line, no spacer
                lines.add(fromLines.size() > 1 ? fromLines.get(0) + "..." : fromLines.get(0));
            } else {
                lines.addAll(fromLines);
                if (fromLines.size() < 2) {
                    lines.add("");
                }
            }
        }
        int tagLine = -1;
        if (tag != null) {
            tagLine = lines.size();
            lines.add(tag.text());
        }

        if (showMetrics) {
            if (node.exchangesTotal > 0 || node.exchangesFailed > 0) {
                long ok = node.exchangesTotal - node.exchangesFailed;
                StringBuilder sb = new StringBuilder();
                if (ok > 0) {
                    sb.append(ok);
                }
                if (node.exchangesFailed > 0) {
                    if (!sb.isEmpty()) {
                        sb.append("/");
                    }
                    sb.append(node.exchangesFailed);
                }
                lines.add(sb.toString());
            } else if (!ext) {
                lines.add("");
            }
        }

        while (lines.size() > MAX_WRAP_LINES + 1) {
            lines.remove(lines.size() - 1);
        }
        return new TextLines(lines, aiLines, idLine, tagLine < lines.size() ? tagLine : -1, tag);
    }

    private List<NodeLine> ownLines(TopologyLayoutNode node) {
        List<NodeLine> own = node.routeId != null ? nodeLines.get(node.routeId) : null;
        return own == null || own.isEmpty() ? null : own.subList(0, Math.min(own.size(), MAX_WRAP_LINES + 1));
    }

    /**
     * The error handling, drawn in a frame of its own below the routes: the routes reached only on error, the dashed
     * error paths into them (down a gutter on the left, clear of the routes), and where each one's failures come from.
     */
    public TopologyDiagramWidget withErrorLayer(ErrorLayer layer) {
        this.errorLayer = layer;
        this.colOffset = layer != null ? GUTTER : 0;
        return this;
    }

    public List<NodeBox> getNodeBoxes() {
        return nodeBoxes;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        nodeBoxes.clear();

        if (errorLayer != null) {
            drawErrorFrame(buffer, area);
            drawErrorPaths(buffer, area);
        }

        for (TopologyLayoutEdge edge : layout.edges) {
            if (!edge.selfLoop) {
                drawEdge(buffer, area, edge);
            }
        }

        for (TopologyLayoutEdge edge : layout.edges) {
            if (edge.selfLoop) {
                drawSelfLoop(buffer, area, edge);
            }
        }

        for (TopologyLayoutNode node : layout.nodes) {
            drawNode(buffer, area, node);
        }

        if (errorLayer != null) {
            drawErrorLabels(buffer, area);
        }
    }

    private Style errorStyle() {
        return Theme.warning();
    }

    private boolean inFrame(TopologyLayoutNode node) {
        return errorLayer != null && node.routeId != null && errorLayer.routeIds().contains(node.routeId);
    }

    private TopologyLayoutNode nodeOf(String routeId) {
        for (TopologyLayoutNode n : layout.nodes) {
            if (routeId != null && routeId.equals(n.routeId)) {
                return n;
            }
        }
        return null;
    }

    /** The frame of the error handling: from its top to the bottom of the diagram, as wide as the diagram. */
    private void drawErrorFrame(Buffer buffer, Rect area) {
        int top = toRow(errorLayer.frameTopY());
        int bottom = toRow(layout.totalHeight) - 1;
        int right = getTotalCols() - 2;
        Style s = errorStyle().dim();
        for (int c = 1; c < right; c++) {
            setChar(buffer, area, top, c, DASH_H, s);
            setChar(buffer, area, bottom, c, DASH_H, s);
        }
        for (int r = top + 1; r < bottom; r++) {
            setChar(buffer, area, r, 0, DASH_V, s);
            setChar(buffer, area, r, right, DASH_V, s);
        }
        setChar(buffer, area, top, 0, '\u256d', s);
        setChar(buffer, area, top, right, '\u256e', s);
        setChar(buffer, area, bottom, 0, '\u2570', s);
        setChar(buffer, area, bottom, right, '\u256f', s);
        writeText(buffer, area, top, 2, " Error handling ", errorStyle().bold());
    }

    /**
     * The error paths from the routes above into the routes of the frame: out of the left side of the route, down the
     * gutter, along the channel row of the target, and down into it. A route's own failures coming back to it are not
     * drawn as a loop; its label says so.
     */
    private void drawErrorPaths(Buffer buffer, Rect area) {
        Style s = errorStyle();
        int gutter = 1;
        List<String> targets = new ArrayList<>();
        for (ErrorLayer.ErrorPath p : errorLayer.paths()) {
            if (errorLayer.routeIds().contains(p.toRouteId()) && !errorLayer.routeIds().contains(p.fromRouteId())
                    && !targets.contains(p.toRouteId())) {
                targets.add(p.toRouteId());
            }
        }
        java.util.Set<String> drawn = new java.util.HashSet<>();
        for (ErrorLayer.ErrorPath p : errorLayer.paths()) {
            int t = targets.indexOf(p.toRouteId());
            TopologyLayoutNode from = nodeOf(p.fromRouteId());
            TopologyLayoutNode to = nodeOf(p.toRouteId());
            if (t < 0 || from == null || to == null || inFrame(from)
                    || !drawn.add(p.fromRouteId() + ">" + p.toRouteId())) {
                continue;
            }
            int srcRow = toRow(from.y) + 1;
            int srcCol = toCol(from.x) - 1;
            int channelRow = toRow(errorLayer.channelsY()) + t;
            int toCx = toCol(to.x + to.width / 2);
            int toTop = toRow(to.y);
            for (int c = gutter + 1; c <= srcCol; c++) {
                plotLine(buffer, area, srcRow, c, DASH_H, s);
            }
            setChar(buffer, area, srcRow, gutter, TL, s);
            for (int r = srcRow + 1; r < channelRow; r++) {
                plotLine(buffer, area, r, gutter, DASH_V, s);
            }
            setChar(buffer, area, channelRow, gutter, BL, s);
            for (int c = gutter + 1; c < toCx; c++) {
                plotLine(buffer, area, channelRow, c, DASH_H, s);
            }
            setChar(buffer, area, channelRow, toCx, TR, s);
            for (int r = channelRow + 1; r < toTop - 1; r++) {
                plotLine(buffer, area, r, toCx, DASH_V, s);
            }
            setChar(buffer, area, toTop - 1, toCx, ARROW, s);
        }
    }

    /**
     * The label lines under each route of the frame: where its failures come from and how they are handled, one line
     * per route that sends there.
     */
    private Map<TopologyLayoutNode, List<String>> errorLabels() {
        Map<TopologyLayoutNode, List<String>> answer = new java.util.LinkedHashMap<>();
        if (errorLayer == null) {
            return answer;
        }
        for (TopologyLayoutNode node : layout.nodes) {
            if (!inFrame(node)) {
                continue;
            }
            Map<String, List<String>> bySource = new java.util.LinkedHashMap<>();
            for (ErrorLayer.ErrorPath p : errorLayer.paths()) {
                if (node.routeId.equals(p.toRouteId())) {
                    bySource.computeIfAbsent(p.fromRouteId(), k -> new ArrayList<>()).add(p.label());
                }
            }
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : bySource.entrySet()) {
                String how = String.join(" \u00b7 ", e.getValue());
                lines.add(e.getKey().equals(node.routeId)
                        ? "\u26a0 own failures: " + how
                        : "\u25c2 " + e.getKey() + ": " + how);
            }
            answer.put(node, lines);
        }
        return answer;
    }

    private void drawErrorLabels(Buffer buffer, Rect area) {
        for (Map.Entry<TopologyLayoutNode, List<String>> e : errorLabels().entrySet()) {
            int row = toRow(e.getKey().y) + boxHeight(e.getKey());
            int col = toCol(e.getKey().x);
            for (String line : e.getValue()) {
                writeText(buffer, area, row++, col, line, errorStyle());
            }
        }
    }

    /** The column right of the widest label of the error frame, so the frame and the scrolling take it in. */
    private int errorLabelsRight() {
        int right = 0;
        for (Map.Entry<TopologyLayoutNode, List<String>> e : errorLabels().entrySet()) {
            for (String line : e.getValue()) {
                right = Math.max(right, toCol(e.getKey().x) + line.length() + 2);
            }
        }
        return right;
    }

    public int getTotalRows() {
        return toRow(layout.totalHeight) + 10;
    }

    public int getTotalCols() {
        return Math.max(toCol(layout.totalWidth) + boxWidth + 4, errorLabelsRight() + 2);
    }

    private void drawNode(Buffer buffer, Rect area, TopologyLayoutNode node) {
        int col = toCol(node.x);
        int row = toRow(node.y);

        boolean ext = isExternal(node);
        List<NodeLine> own = ownLines(node);

        TextLines text = textLines(node, ext);
        List<String> lines = text.lines();
        int aiLines = text.aiLines();
        int idLine = text.idLine();
        int tagLine = text.tagLine();
        NodeLine tag = text.tag();
        if (own != null) {
            lines = new ArrayList<>(own.stream().map(NodeLine::text).toList());
            tagLine = -1;
        }

        int height = 2 + lines.size();
        int nodeIdx = nodeBoxes.size();
        boolean selected = nodeIdx == selectedNodeIndex;

        boolean highlighted = !ext && node.routeId != null && highlightRouteIds.contains(node.routeId);

        char hChar = ext ? DASH_H : H;
        char vChar = ext ? DASH_V : V;
        Style borderStyle;
        if (highlighted) {
            Color hlColor = highlightFailed ? highlightFailColor() : highlightOkColor();
            borderStyle = Style.EMPTY.fg(hlColor).bold();
        } else if (!ext && groupColors.containsKey(node.routeId)) {
            // the colour of the route's group, when groups are shown
            borderStyle = Style.EMPTY.fg(groupColors.get(node.routeId));
        } else {
            borderStyle = ext ? dashedBorderStyle() : borderStyle();
        }
        if (selected) {
            borderStyle = borderStyle.patch(selectionStyle());
        }

        // Top border
        setChar(buffer, area, row, col, TL, borderStyle);
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(buffer, area, row, c, hChar, borderStyle);
        }
        setChar(buffer, area, row, col + boxWidth - 1, TR, borderStyle);

        // Bottom border
        int bottom = row + height - 1;
        setChar(buffer, area, bottom, col, BL, borderStyle);
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(buffer, area, bottom, c, hChar, borderStyle);
        }
        setChar(buffer, area, bottom, col + boxWidth - 1, BR, borderStyle);

        // Content rows
        int innerWidth = boxWidth - 4;
        for (int i = 0; i < lines.size(); i++) {
            int r = row + 1 + i;
            setChar(buffer, area, r, col, vChar, borderStyle);
            setChar(buffer, area, r, col + boxWidth - 1, vChar, borderStyle);

            // Clear interior
            Style bgStyle = selected ? selectionStyle() : Style.EMPTY;
            for (int c = col + 1; c < col + boxWidth - 1; c++) {
                setChar(buffer, area, r, c, ' ', bgStyle);
            }

            String lineText = lines.get(i);
            if (lineText.length() > innerWidth) {
                lineText = lineText.substring(0, Math.max(1, innerWidth - 3)) + "...";
            }
            int textCol = col + 2 + Math.max(0, (innerWidth - lineText.length()) / 2);

            // Choose style based on content type
            if (own != null) {
                writeText(buffer, area, r, textCol, lineText, style(own.get(i).style(), selected));
            } else if (ext && i == 0) {
                writeText(buffer, area, r, textCol, lineText, style(dashedBorderStyle(), selected));
            } else if (showMetrics && i == lines.size() - 1 && node.exchangesTotal > 0) {
                drawMetricsLine(buffer, area, r, textCol, lineText, node, selected);
            } else if (i == tagLine) {
                writeText(buffer, area, r, textCol, lineText, style(tag.style(), selected));
            } else if (i < aiLines) {
                writeText(buffer, area, r, textCol, lineText, style(aiStyle, selected));
            } else if (i == idLine) {
                writeText(buffer, area, r, textCol, lineText, style(fromLabelStyle(), selected));
            } else if (i == 0 && !ext) {
                Style idStyle = highlighted
                        ? Style.EMPTY.fg(highlightFailed ? highlightFailColor() : highlightOkColor()).bold()
                        : routeIdStyle();
                writeText(buffer, area, r, textCol, lineText, style(idStyle, selected));
            } else {
                writeText(buffer, area, r, textCol, lineText, style(fromLabelStyle(), selected));
            }
        }

        nodeBoxes.add(new NodeBox(node.routeId, row, row + height - 1, col, col + boxWidth - 1, node.layer));
    }

    private void drawMetricsLine(
            Buffer buffer, Rect area, int row, int col, String text,
            TopologyLayoutNode node, boolean selected) {
        long ok = node.exchangesTotal - node.exchangesFailed;
        if (ok > 0 && node.exchangesFailed > 0) {
            String okStr = String.valueOf(ok);
            String failStr = String.valueOf(node.exchangesFailed);
            writeText(buffer, area, row, col, okStr, style(metricsOkStyle(), selected));
            int slashCol = col + okStr.length();
            writeText(buffer, area, row, slashCol, "/", style(Style.EMPTY.fg(Theme.diagramBorder()), selected));
            writeText(buffer, area, row, slashCol + 1, failStr, style(metricsFailStyle(), selected));
        } else if (ok > 0) {
            writeText(buffer, area, row, col, text, style(metricsOkStyle(), selected));
        } else if (node.exchangesFailed > 0) {
            writeText(buffer, area, row, col, text, style(metricsFailStyle(), selected));
        }
    }

    private void drawEdge(Buffer buffer, Rect area, TopologyLayoutEdge edge) {
        int fromCx = toCol(edge.from.x + edge.from.width / 2);
        int fromBottom = toRow(edge.from.y) + boxHeight(edge.from);
        int toCx = toCol(edge.to.x + edge.to.width / 2);
        int toTop = toRow(edge.to.y);

        if (fromBottom >= toTop) {
            return;
        }

        boolean dashed = isExternal(edge.from) || isExternal(edge.to);
        char vChar = dashed ? DASH_V : V;
        char hChar = dashed ? DASH_H : H;
        boolean edgeHighlighted = !dashed
                && edge.from.routeId != null && highlightRouteIds.contains(edge.from.routeId)
                && edge.to.routeId != null && highlightRouteIds.contains(edge.to.routeId);
        Style edgeStyle;
        if (edgeHighlighted) {
            edgeStyle = Style.EMPTY.fg(highlightFailed ? highlightFailColor() : highlightOkColor());
        } else {
            edgeStyle = dashed ? dashedBorderStyle() : Style.EMPTY.fg(Theme.diagramBorder());
        }

        if (fromCx == toCx) {
            for (int r = fromBottom; r < toTop - 1; r++) {
                plotLine(buffer, area, r, fromCx, vChar, edgeStyle);
            }
            setChar(buffer, area, toTop - 1, toCx, ARROW, edgeStyle);
        } else {
            int midRow = fromBottom + (toTop - fromBottom) / 2;

            for (int r = fromBottom; r < midRow; r++) {
                plotLine(buffer, area, r, fromCx, vChar, edgeStyle);
            }

            int minC = Math.min(fromCx, toCx);
            int maxC = Math.max(fromCx, toCx);
            for (int c = minC; c <= maxC; c++) {
                plotLine(buffer, area, midRow, c, hChar, edgeStyle);
            }

            setChar(buffer, area, midRow, fromCx, T_UP, edgeStyle);
            setChar(buffer, area, midRow, toCx, T_DOWN, edgeStyle);

            for (int r = midRow + 1; r < toTop - 1; r++) {
                plotLine(buffer, area, r, toCx, vChar, edgeStyle);
            }
            setChar(buffer, area, toTop - 1, toCx, ARROW, edgeStyle);
        }
    }

    private void drawSelfLoop(Buffer buffer, Rect area, TopologyLayoutEdge edge) {
        int col = toCol(edge.from.x) + boxWidth;
        int topRow = toRow(edge.from.y) + 1;
        int botRow = topRow + 2;

        Style s = Style.EMPTY.fg(Theme.diagramBorder());
        for (int c = col; c < col + 3; c++) {
            setChar(buffer, area, topRow, c, H, s);
            setChar(buffer, area, botRow, c, H, s);
        }
        setChar(buffer, area, topRow + 1, col + 2, V, s);
        setChar(buffer, area, topRow, col + 2, TR, s);
        setChar(buffer, area, botRow, col + 2, BR, s);
    }

    private int boxHeight(TopologyLayoutNode node) {
        List<NodeLine> own = ownLines(node);
        if (own != null) {
            return 2 + own.size();
        }
        if (isExternal(node)) {
            int lines = 1;
            if (showMetrics && node.exchangesTotal > 0) {
                lines++;
            }
            return 2 + lines;
        }
        return 2 + textLines(node, false).lines().size();
    }

    private void setChar(Buffer buffer, Rect area, int gridRow, int gridCol, char ch, Style style) {
        int x = area.x() + gridCol - scrollX;
        int y = area.y() + gridRow - scrollY;
        if (x >= area.left() && x < area.right() && y >= area.top() && y < area.bottom()) {
            buffer.setString(x, y, String.valueOf(ch), style);
        }
    }

    private void plotLine(Buffer buffer, Rect area, int gridRow, int gridCol, char ch, Style style) {
        setChar(buffer, area, gridRow, gridCol, ch, style);
    }

    private void writeText(Buffer buffer, Rect area, int gridRow, int gridCol, String text, Style style) {
        int x = area.x() + gridCol - scrollX;
        int y = area.y() + gridRow - scrollY;
        if (y >= area.top() && y < area.bottom() && x < area.right()) {
            int startIdx = 0;
            if (x < area.left()) {
                startIdx = area.left() - x;
                x = area.left();
            }
            if (startIdx < text.length()) {
                int maxLen = area.right() - x;
                String visible = text.substring(startIdx, Math.min(text.length(), startIdx + maxLen));
                buffer.setString(x, y, visible, style);
            }
        }
    }

    private Style style(Style base, boolean selected) {
        return selected ? base.patch(selectionStyle()) : base;
    }

    private int toCol(int pixelX) {
        if (nodeWidth == 0) {
            return 0;
        }
        return pixelX * boxWidth / nodeWidth + colOffset;
    }

    private int toRow(int pixelY) {
        return pixelY / Y_SCALE;
    }

    private static boolean isExternal(TopologyLayoutNode node) {
        return "external-in".equals(node.nodeType) || "external-out".equals(node.nodeType)
                || "external".equals(node.nodeType);
    }

    public static List<String> wrapText(String text, int maxWidth) {
        if (maxWidth <= 0 || text.length() <= maxWidth) {
            return new ArrayList<>(List.of(text));
        }

        List<String> lines = new ArrayList<>();
        String remaining = text;

        while (!remaining.isEmpty() && lines.size() < MAX_WRAP_LINES) {
            if (remaining.length() <= maxWidth) {
                lines.add(remaining);
                remaining = "";
                break;
            }

            int breakAt = -1;
            for (int i = 0; i < maxWidth && i < remaining.length(); i++) {
                char c = remaining.charAt(i);
                if (c == ' ' || c == ':' || c == '/' || c == '.' || c == ',' || c == '&' || c == '?') {
                    breakAt = i + 1;
                }
            }
            if (breakAt <= 0) {
                breakAt = maxWidth;
            }

            lines.add(remaining.substring(0, breakAt).stripTrailing());
            remaining = remaining.substring(breakAt).stripLeading();
        }

        if (!remaining.isEmpty()) {
            int lastIdx = lines.size() - 1;
            String lastLine = lines.get(lastIdx);
            String combined = lastLine + " " + remaining;
            lines.set(lastIdx, combined.substring(0, Math.max(1, maxWidth - 3)) + "...");
        }

        return lines;
    }
}
