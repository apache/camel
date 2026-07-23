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

    public record NodeBox(String routeId, int startRow, int endRow, int startCol, int endCol, int layer) {
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

    public List<NodeBox> getNodeBoxes() {
        return nodeBoxes;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        nodeBoxes.clear();

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
    }

    public int getTotalRows() {
        return toRow(layout.totalHeight) + 10;
    }

    public int getTotalCols() {
        return toCol(layout.totalWidth) + boxWidth + 4;
    }

    private void drawNode(Buffer buffer, Rect area, TopologyLayoutNode node) {
        int col = toCol(node.x);
        int row = toRow(node.y);

        boolean ext = isExternal(node);

        String line1;
        if (ext) {
            line1 = node.from;
        } else if (showDescription && node.description != null && !node.description.isBlank()) {
            line1 = node.description;
        } else {
            line1 = node.routeId;
        }

        List<String> lines = new ArrayList<>(wrapText(line1, boxWidth - 4));
        if (!ext && !showDescription) {
            String line2 = "(" + node.from + ")";
            List<String> fromLines = wrapText(line2, boxWidth - 4);
            lines.addAll(fromLines);
            if (fromLines.size() < 2) {
                lines.add("");
            }
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

            String text = lines.get(i);
            if (text.length() > innerWidth) {
                text = text.substring(0, Math.max(1, innerWidth - 3)) + "...";
            }
            int textCol = col + 2 + Math.max(0, (innerWidth - text.length()) / 2);

            // Choose style based on content type
            if (ext && i == 0) {
                writeText(buffer, area, r, textCol, text, style(dashedBorderStyle(), selected));
            } else if (showMetrics && i == lines.size() - 1 && node.exchangesTotal > 0) {
                drawMetricsLine(buffer, area, r, textCol, text, node, selected);
            } else if (i == 0 && !ext) {
                Style idStyle = highlighted
                        ? Style.EMPTY.fg(highlightFailed ? highlightFailColor() : highlightOkColor()).bold()
                        : routeIdStyle();
                writeText(buffer, area, r, textCol, text, style(idStyle, selected));
            } else {
                writeText(buffer, area, r, textCol, text, style(fromLabelStyle(), selected));
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
        if (isExternal(node)) {
            int lines = 1;
            if (showMetrics && node.exchangesTotal > 0) {
                lines++;
            }
            return 2 + lines;
        }
        String label = showDescription && node.description != null && !node.description.isBlank()
                ? node.description : node.routeId;
        int lines = wrapText(label, boxWidth - 4).size();
        if (!showDescription) {
            List<String> fromLines = wrapText("(" + node.from + ")", boxWidth - 4);
            lines += fromLines.size();
            if (fromLines.size() < 2) {
                lines++;
            }
        }
        if (showMetrics) {
            lines++;
        }
        while (lines > MAX_WRAP_LINES + 1) {
            lines--;
        }
        return 2 + lines;
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
        return pixelX * boxWidth / nodeWidth;
    }

    private int toRow(int pixelY) {
        return pixelY / Y_SCALE;
    }

    private static boolean isExternal(TopologyLayoutNode node) {
        return "external-in".equals(node.nodeType) || "external-out".equals(node.nodeType)
                || "external".equals(node.nodeType);
    }

    static List<String> wrapText(String text, int maxWidth) {
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
            String combined = lastLine + remaining;
            lines.set(lastIdx, combined.substring(0, Math.max(1, maxWidth - 3)) + "...");
        }

        return lines;
    }
}
