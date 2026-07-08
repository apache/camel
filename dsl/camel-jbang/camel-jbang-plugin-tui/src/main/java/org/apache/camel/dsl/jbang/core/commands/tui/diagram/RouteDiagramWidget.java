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
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.Bounds;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.apache.camel.dsl.jbang.core.commands.tui.Theme;

import static org.apache.camel.diagram.RouteDiagramLayoutEngine.BRANCH_CHILD_TYPES;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.PADDING;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.SCOPE_BOX_PAD;
import static org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors.*;

public class RouteDiagramWidget implements Widget {

    private static final int Y_SCALE = 20;
    private static final int MIN_BOX_WIDTH = 16;
    private static final int X_DIVISOR = 15;
    private static final int MAX_WRAP_LINES = 3;

    // Dashed scope box characters
    private static final char SCOPE_H = '╌';
    private static final char SCOPE_V = '╎';

    private final LayoutRoute layoutRoute;
    private final int nodeWidth;
    private final int boxWidth;
    private final int selectedNodeIndex;
    private final int scrollX;
    private final int scrollY;
    private final boolean showMetrics;
    private final Map<String, String> linkableEndpoints;

    private final boolean showDescription;
    private final Map<String, String> routeDescriptions;
    private final String currentRouteLabel;
    private final Set<String> highlightNodeIds;
    private final boolean highlightFailed;

    private final List<EipNodeBox> nodeBoxes = new ArrayList<>();

    public record EipNodeBox(String nodeId, String type, int startRow, int endRow, int startCol, int endCol,
            LayoutNode layoutNode) {
    }

    public RouteDiagramWidget(
                              LayoutRoute layoutRoute, int nodeWidth,
                              int selectedNodeIndex, int scrollX, int scrollY,
                              boolean showMetrics) {
        this(layoutRoute, nodeWidth, selectedNodeIndex, scrollX, scrollY, showMetrics,
             Collections.emptyMap(), false, Collections.emptyMap());
    }

    public RouteDiagramWidget(
                              LayoutRoute layoutRoute, int nodeWidth,
                              int selectedNodeIndex, int scrollX, int scrollY,
                              boolean showMetrics, Map<String, String> linkableEndpoints) {
        this(layoutRoute, nodeWidth, selectedNodeIndex, scrollX, scrollY, showMetrics,
             linkableEndpoints, false, Collections.emptyMap());
    }

    public RouteDiagramWidget(
                              LayoutRoute layoutRoute, int nodeWidth,
                              int selectedNodeIndex, int scrollX, int scrollY,
                              boolean showMetrics, Map<String, String> linkableEndpoints,
                              boolean showDescription, Map<String, String> routeDescriptions) {
        this(layoutRoute, nodeWidth, selectedNodeIndex, scrollX, scrollY, showMetrics,
             linkableEndpoints, showDescription, routeDescriptions,
             Collections.emptySet(), false);
    }

    public RouteDiagramWidget(
                              LayoutRoute layoutRoute, int nodeWidth,
                              int selectedNodeIndex, int scrollX, int scrollY,
                              boolean showMetrics, Map<String, String> linkableEndpoints,
                              boolean showDescription, Map<String, String> routeDescriptions,
                              Set<String> highlightNodeIds, boolean highlightFailed) {
        this.layoutRoute = layoutRoute;
        this.nodeWidth = nodeWidth;
        this.boxWidth = Math.max(MIN_BOX_WIDTH, nodeWidth / X_DIVISOR);
        this.selectedNodeIndex = selectedNodeIndex;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.showMetrics = showMetrics;
        this.linkableEndpoints = linkableEndpoints;
        this.showDescription = showDescription;
        this.routeDescriptions = routeDescriptions;
        this.highlightNodeIds = highlightNodeIds;
        this.highlightFailed = highlightFailed;
        if (showDescription) {
            String desc = null;
            for (LayoutNode ln : layoutRoute.nodes) {
                if ("route".equals(ln.type) && ln.treeNode != null) {
                    desc = ln.treeNode.info.description;
                    break;
                }
            }
            this.currentRouteLabel = (desc != null && !desc.isBlank()) ? desc : layoutRoute.routeId;
        } else {
            this.currentRouteLabel = null;
        }
    }

    public List<EipNodeBox> getNodeBoxes() {
        return nodeBoxes;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        nodeBoxes.clear();

        // Scope boxes (behind everything)
        for (LayoutNode ln : layoutRoute.nodes) {
            if (!"route".equals(ln.type)
                    && ln.treeNode != null && RouteDiagramLayoutEngine.hasScope(ln.treeNode)) {
                drawScopeBox(buffer, area, ln);
            }
        }

        // Edges
        for (LayoutNode ln : layoutRoute.nodes) {
            if (!"route".equals(ln.type) && ln.parentNode != null
                    && !"route".equals(ln.parentNode.type)) {
                if (ln.connectFromMerge) {
                    drawMergeArrow(buffer, area, ln);
                } else {
                    drawArrow(buffer, area, ln.parentNode, ln);
                }
            }
        }

        // Nodes (on top, skip the structural "route" node)
        for (LayoutNode ln : layoutRoute.nodes) {
            if (!"route".equals(ln.type)) {
                drawNode(buffer, area, ln);
            }
        }
    }

    public int getTotalRows() {
        return toRow(layoutRoute.maxY) + 10;
    }

    public int getTotalCols() {
        return toCol(layoutRoute.maxX + PADDING) + boxWidth + 4;
    }

    private void drawNode(Buffer buffer, Rect area, LayoutNode node) {
        int col = toCol(node.x);
        int row = toRow(node.y);
        int innerWidth = boxWidth - 4;
        List<String> lines = rewrapText(node, innerWidth);
        int height = 2 + lines.size();

        int nodeIdx = nodeBoxes.size();
        boolean selected = nodeIdx == selectedNodeIndex;
        boolean external = isExternalEndpoint(node);
        boolean highlighted = node.id != null && highlightNodeIds.contains(node.id);

        Color eipColor;
        if (highlighted) {
            eipColor = highlightFailed ? highlightFailColor() : highlightOkColor();
        } else {
            eipColor = getEipColor(node.type);
        }
        Style borderStyle = Style.EMPTY.fg(eipColor);
        if (selected) {
            borderStyle = borderStyle.patch(selectionStyle());
        }

        char hChar = external ? DASH_H : H;
        char vChar = external ? DASH_V : V;

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
        for (int i = 0; i < lines.size(); i++) {
            int r = row + 1 + i;
            setChar(buffer, area, r, col, vChar, borderStyle);
            setChar(buffer, area, r, col + boxWidth - 1, vChar, borderStyle);

            Style bgStyle = selected ? selectionStyle() : Style.EMPTY;
            for (int c = col + 1; c < col + boxWidth - 1; c++) {
                setChar(buffer, area, r, c, ' ', bgStyle);
            }

            String text = lines.get(i);
            if (text.length() > innerWidth) {
                text = text.substring(0, Math.max(1, innerWidth - 3)) + "...";
            }
            int textCol = col + 2 + Math.max(0, (innerWidth - text.length()) / 2);

            // First line: [type] tag with EIP color, rest: label text
            if (i == 0) {
                writeText(buffer, area, r, textCol, text, style(Style.EMPTY.fg(eipColor).bold(), selected));
            } else {
                writeText(buffer, area, r, textCol, text, style(fromLabelStyle(), selected));
            }
        }

        // Link indicator for nodes that connect to other routes
        String linkedRouteId = findLinkedRouteId(node);
        if (linkedRouteId != null) {
            Style linkStyle = Theme.label().bold();
            writeText(buffer, area, bottom, col + boxWidth, " ↵ " + linkedRouteId, linkStyle);
        }

        nodeBoxes.add(new EipNodeBox(node.id, node.type, row, row + height - 1, col, col + boxWidth - 1, node));
    }

    private void drawArrow(Buffer buffer, Rect area, LayoutNode from, LayoutNode to) {
        int fromCx = centerCol(from);
        int fromBottom = toRow(from.y) + boxHeight(from);
        int toCx = centerCol(to);
        int toTop = getTopRow(to);

        StatInfo stat = resolveStatInfo(to);
        long total = stat != null ? stat.exchangesTotal : 0;
        boolean external = isExternalEndpoint(to);
        boolean dashed = (showMetrics && total == 0) || external;

        boolean bothHighlighted = !highlightNodeIds.isEmpty()
                && from.id != null && highlightNodeIds.contains(from.id)
                && to.id != null && highlightNodeIds.contains(to.id);

        drawArrowPath(buffer, area, fromCx, fromBottom, toCx, toTop, dashed, external, bothHighlighted);
        drawCounters(buffer, area, toCx, toTop, stat);
    }

    private void drawMergeArrow(Buffer buffer, Rect area, LayoutNode to) {
        int fromCx = toCol(to.mergeCx);
        int fromRow = toRow(to.mergeY);
        int toCx = centerCol(to);
        int toTop = getTopRow(to);

        StatInfo stat = showMetrics && to.treeNode != null ? to.treeNode.info.stat : null;
        long total = stat != null ? stat.exchangesTotal : 0;
        boolean dashed = showMetrics && total == 0;

        boolean highlighted = !highlightNodeIds.isEmpty()
                && to.id != null && highlightNodeIds.contains(to.id);

        drawArrowPath(buffer, area, fromCx, fromRow, toCx, toTop, dashed, false, highlighted);
        drawCounters(buffer, area, toCx, toTop, stat);
    }

    private void drawArrowPath(
            Buffer buffer, Rect area, int fromCx, int fromRow, int toCx, int toRow,
            boolean dashed, boolean external, boolean highlighted) {
        if (fromRow >= toRow) {
            return;
        }

        char vChar = dashed ? DASH_V : V;
        char hChar = dashed ? DASH_H : H;
        Style edgeStyle;
        if (highlighted) {
            edgeStyle = Style.EMPTY.fg(highlightFailed ? highlightFailColor() : highlightOkColor());
        } else if (external) {
            edgeStyle = Style.EMPTY.fg(externalColor());
        } else if (dashed) {
            edgeStyle = Theme.muted();
        } else {
            edgeStyle = Theme.muted();
        }

        if (fromCx == toCx) {
            for (int r = fromRow; r < toRow - 1; r++) {
                setChar(buffer, area, r, fromCx, vChar, edgeStyle);
            }
            setChar(buffer, area, toRow - 1, toCx, ARROW, edgeStyle);
        } else {
            int midRow = fromRow + (toRow - fromRow) / 2;

            for (int r = fromRow; r < midRow; r++) {
                setChar(buffer, area, r, fromCx, vChar, edgeStyle);
            }

            int minC = Math.min(fromCx, toCx);
            int maxC = Math.max(fromCx, toCx);
            for (int c = minC; c <= maxC; c++) {
                setChar(buffer, area, midRow, c, hChar, edgeStyle);
            }

            setChar(buffer, area, midRow, fromCx, T_UP, edgeStyle);
            setChar(buffer, area, midRow, toCx, T_DOWN, edgeStyle);

            for (int r = midRow + 1; r < toRow - 1; r++) {
                setChar(buffer, area, r, toCx, vChar, edgeStyle);
            }
            setChar(buffer, area, toRow - 1, toCx, ARROW, edgeStyle);
        }
    }

    private void drawCounters(Buffer buffer, Rect area, int toCx, int toTop, StatInfo stat) {
        if (!showMetrics || stat == null) {
            return;
        }
        long total = stat.exchangesTotal;
        long failed = stat.exchangesFailed;
        long ok = total - failed;
        if (ok > 0) {
            String okStr = String.valueOf(ok);
            writeText(buffer, area, toTop - 1, toCx + 2, okStr, metricsOkStyle());
        }
        if (failed > 0) {
            String failStr = String.valueOf(failed);
            int col = toCx - 1 - failStr.length();
            writeText(buffer, area, toTop - 1, col, failStr, metricsFailStyle());
        }
    }

    private void drawScopeBox(Buffer buffer, Rect area, LayoutNode scopeNode) {
        TreeNode tn = scopeNode.treeNode;
        Bounds bounds = new Bounds(
                scopeNode.x, scopeNode.y,
                scopeNode.x + nodeWidth, scopeNode.y + scopeNode.height);
        for (TreeNode child : tn.children) {
            RouteDiagramLayoutEngine.expandBoundsForBox(child, bounds, nodeWidth);
        }

        int col1 = toCol(bounds.minX - SCOPE_BOX_PAD);
        int row1 = toRow(bounds.minY - SCOPE_BOX_PAD);
        int col2 = toCol(bounds.maxX + SCOPE_BOX_PAD);
        int row2 = toRow(bounds.maxY + SCOPE_BOX_PAD);

        Color scopeColor = getEipColor(scopeNode.type);
        Style scopeStyle = Style.EMPTY.fg(scopeColor).dim();

        for (int c = col1; c <= col2; c++) {
            setChar(buffer, area, row1, c, SCOPE_H, scopeStyle);
            setChar(buffer, area, row2, c, SCOPE_H, scopeStyle);
        }
        for (int r = row1 + 1; r < row2; r++) {
            setChar(buffer, area, r, col1, SCOPE_V, scopeStyle);
            setChar(buffer, area, r, col2, SCOPE_V, scopeStyle);
        }
    }

    private StatInfo resolveStatInfo(LayoutNode to) {
        if (!showMetrics || to.treeNode == null) {
            return null;
        }
        StatInfo stat = to.treeNode.info.stat;
        if (BRANCH_CHILD_TYPES.contains(to.type) && !to.treeNode.children.isEmpty()) {
            stat = to.treeNode.children.get(0).info.stat;
        }
        return stat;
    }

    private int getTopRow(LayoutNode node) {
        if (node.treeNode != null && RouteDiagramLayoutEngine.hasScope(node.treeNode)) {
            return toRow(node.y - SCOPE_BOX_PAD);
        }
        return toRow(node.y);
    }

    private int centerCol(LayoutNode node) {
        return toCol(node.x + nodeWidth / 2);
    }

    private int boxHeight(LayoutNode node) {
        return 2 + rewrapText(node, boxWidth - 4).size();
    }

    private List<String> rewrapText(LayoutNode node, int maxWidth) {
        String label = String.join("", node.wrappedLines);
        if (showDescription) {
            if ("from".equals(node.type) && currentRouteLabel != null) {
                label = currentRouteLabel;
            } else {
                String linkedRouteId = findLinkedRouteId(node);
                if (linkedRouteId != null) {
                    String desc = routeDescriptions.get(linkedRouteId);
                    if (desc != null && !desc.isBlank()) {
                        label = desc;
                    }
                }
            }
        }
        return wrapText(label, maxWidth);
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

    private void setChar(Buffer buffer, Rect area, int gridRow, int gridCol, char ch, Style style) {
        int x = area.x() + gridCol - scrollX;
        int y = area.y() + gridRow - scrollY;
        if (x >= area.left() && x < area.right() && y >= area.top() && y < area.bottom()) {
            buffer.setString(x, y, String.valueOf(ch), style);
        }
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

    private boolean isExternalEndpoint(LayoutNode node) {
        if (node.treeNode == null) {
            return false;
        }
        return node.treeNode.info.remote;
    }

    private String findLinkedRouteId(LayoutNode node) {
        if (linkableEndpoints.isEmpty() || node.treeNode == null) {
            return null;
        }
        String type = node.type;
        if (!"to".equals(type) && !"toD".equals(type) && !"wireTap".equals(type)
                && !"enrich".equals(type) && !"pollEnrich".equals(type)
                && !"from".equals(type)) {
            return null;
        }
        String baseUri = getBaseUri(node.treeNode.info);
        return baseUri != null ? linkableEndpoints.get(baseUri) : null;
    }

    private static String getBaseUri(RouteDiagramLayoutEngine.NodeInfo info) {
        String uri = info.uri;
        if (uri == null) {
            String code = info.code;
            if (code == null) {
                return null;
            }
            int open = code.indexOf('[');
            int close = code.lastIndexOf(']');
            uri = (open >= 0 && close > open) ? code.substring(open + 1, close) : code;
        }
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    private int toRow(int pixelY) {
        return pixelY / Y_SCALE;
    }
}
