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
package org.apache.camel.diagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.Bounds;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;

import static org.apache.camel.diagram.RouteDiagramLayoutEngine.BRANCH_CHILD_TYPES;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.PADDING;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.SCOPE_BOX_PAD;

/**
 * Renders route diagrams as plain ASCII art or Unicode box-drawing text.
 */
public class RouteDiagramAsciiRenderer {

    private static final int Y_SCALE = 20;
    private static final int MIN_BOX_WIDTH = 16;
    private static final int X_DIVISOR = 15;

    // Unicode box-drawing characters
    private static final char UNI_H = '─';     // ─
    private static final char UNI_V = '│';     // │
    private static final char UNI_TL = '┌';    // ┌
    private static final char UNI_TR = '┐';    // ┐
    private static final char UNI_BL = '└';    // └
    private static final char UNI_BR = '┘';    // ┘
    private static final char UNI_T_DOWN = '┬'; // ┬
    private static final char UNI_T_UP = '┴';  // ┴
    private static final char UNI_CROSS = '┼'; // ┼
    private static final char UNI_ARROW = '▼'; // ▼
    private static final char UNI_DASH_H = '╌'; // ╌
    private static final char UNI_DASH_V = '╎'; // ╎

    private final int nodeWidth;
    private final int boxWidth;
    private final boolean unicode;
    private final boolean metrics;
    private final List<CounterPos> counterPositions = new ArrayList<>();

    public enum CounterType {
        OK,
        FAIL,
        HIGHLIGHT_SUCCESS,
        HIGHLIGHT_FAIL,
        EXTERNAL
    }

    public record CounterPos(int row, int col, int length, CounterType type) {
    }

    public RouteDiagramAsciiRenderer(int nodeWidth) {
        this(nodeWidth, false, false);
    }

    public RouteDiagramAsciiRenderer(int nodeWidth, boolean unicode) {
        this(nodeWidth, unicode, false);
    }

    public RouteDiagramAsciiRenderer(int nodeWidth, boolean unicode, boolean metrics) {
        this.nodeWidth = nodeWidth;
        this.boxWidth = Math.max(MIN_BOX_WIDTH, nodeWidth / X_DIVISOR);
        this.unicode = unicode;
        this.metrics = metrics;
    }

    public int getBoxWidth() {
        return boxWidth;
    }

    public List<CounterPos> getCounterPositions() {
        return counterPositions;
    }

    public String renderDiagramAnsi(
            List<LayoutRoute> layoutRoutes, int totalHeight,
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle) {
        String plain = renderDiagram(layoutRoutes, totalHeight, highlightedNodeIds, highlightStyle);
        return applyAnsiColors(plain);
    }

    public String renderDiagramAnsi(List<LayoutRoute> layoutRoutes, int totalHeight) {
        String plain = renderDiagram(layoutRoutes, totalHeight);
        return applyAnsiColors(plain);
    }

    private String applyAnsiColors(String plain) {
        if (counterPositions.isEmpty()) {
            return plain;
        }
        String[] lines = plain.split("\n", -1);
        for (CounterPos cp : counterPositions) {
            if (cp.row >= 0 && cp.row < lines.length) {
                String line = lines[cp.row];
                if (cp.col >= 0 && cp.col + cp.length <= line.length()) {
                    String before = line.substring(0, cp.col);
                    String counter = line.substring(cp.col, cp.col + cp.length);
                    String after = line.substring(cp.col + cp.length);
                    String color;
                    switch (cp.type) {
                        case OK, HIGHLIGHT_SUCCESS -> color = "\033[32m";
                        default -> color = "\033[31m";
                    }
                    lines[cp.row] = before + color + counter + "\033[0m" + after;
                }
            }
        }
        return String.join("\n", lines);
    }

    public String renderDiagram(
            List<LayoutRoute> layoutRoutes, int totalHeight,
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle) {
        counterPositions.clear();
        int maxPixelX = layoutRoutes.stream()
                .mapToInt(lr -> lr.maxX).max().orElse(nodeWidth) + PADDING;
        int gridWidth = toCol(maxPixelX) + boxWidth + 4;
        int gridHeight = totalHeight / Y_SCALE + 20;

        char[][] grid = new char[gridHeight][gridWidth];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }

        for (LayoutRoute lr : layoutRoutes) {
            drawRoute(grid, lr, highlightedNodeIds, highlightStyle);
        }

        return gridToString(grid);
    }

    public String renderDiagram(List<LayoutRoute> layoutRoutes, int totalHeight) {
        counterPositions.clear();
        int maxPixelX = layoutRoutes.stream()
                .mapToInt(lr -> lr.maxX).max().orElse(nodeWidth) + PADDING;
        int gridWidth = toCol(maxPixelX) + boxWidth + 4;
        int gridHeight = totalHeight / Y_SCALE + 20;

        char[][] grid = new char[gridHeight][gridWidth];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }

        for (LayoutRoute lr : layoutRoutes) {
            drawRoute(grid, lr);
        }

        return gridToString(grid);
    }

    private void drawRoute(
            char[][] grid, LayoutRoute lr,
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle) {
        int labelRow = toRow(lr.labelY);
        String label = lr.routeId;
        if (lr.source != null && !lr.source.isEmpty()) {
            label += " (" + lr.source + ")";
        }
        drawText(grid, labelRow, toCol(PADDING), label);

        // find the last highlighted node (for FAIL mode box highlighting)
        LayoutNode lastHighlightedNode = null;
        if (highlightedNodeIds != null && highlightStyle == RouteDiagramHelper.HighlightStyle.FAIL) {
            for (LayoutNode ln : lr.nodes) {
                if (isHighlighted(ln, highlightedNodeIds)) {
                    lastHighlightedNode = ln;
                }
            }
        }

        for (LayoutNode ln : lr.nodes) {
            if (ln.treeNode != null && RouteDiagramLayoutEngine.hasScope(ln.treeNode)) {
                drawScopeBox(grid, ln);
            }
        }

        for (LayoutNode ln : lr.nodes) {
            if (ln.parentNode != null) {
                boolean highlightArrow = highlightedNodeIds != null
                        && isHighlighted(ln, highlightedNodeIds)
                        && isHighlighted(ln.parentNode, highlightedNodeIds);
                if (ln.connectFromMerge) {
                    drawMergeArrow(grid, ln, highlightArrow, highlightStyle);
                } else {
                    drawArrow(grid, ln.parentNode, ln, highlightArrow, highlightStyle);
                }
            }
        }

        for (LayoutNode ln : lr.nodes) {
            drawNode(grid, ln);
            if (ln == lastHighlightedNode) {
                recordNodeHighlight(grid, ln);
            }
        }
    }

    private void recordNodeHighlight(char[][] grid, LayoutNode node) {
        int col = toCol(node.x);
        int row = toRow(node.y);
        int height = 2 + rewrapText(node, boxWidth - 4).size();
        // highlight the entire box (top border, sides, bottom border)
        for (int r = row; r < row + height && r < grid.length; r++) {
            counterPositions.add(new CounterPos(r, col, boxWidth, CounterType.HIGHLIGHT_FAIL));
        }
    }

    private static boolean isHighlighted(LayoutNode node, Set<String> highlightedNodeIds) {
        return node.id != null && highlightedNodeIds.contains(node.id);
    }

    private void drawRoute(char[][] grid, LayoutRoute lr) {
        drawRoute(grid, lr, null, null);
    }

    private void drawNode(char[][] grid, LayoutNode node) {
        int col = toCol(node.x);
        int row = toRow(node.y);
        int innerWidth = boxWidth - 4;
        List<String> lines = rewrapText(node, innerWidth);
        int height = 2 + lines.size();

        if (row + height >= grid.length) {
            return;
        }

        char h = unicode ? UNI_H : '-';
        char v = unicode ? UNI_V : '|';

        setChar(grid, row, col, unicode ? UNI_TL : '+');
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(grid, row, c, h);
        }
        setChar(grid, row, col + boxWidth - 1, unicode ? UNI_TR : '+');

        int bottom = row + height - 1;
        setChar(grid, bottom, col, unicode ? UNI_BL : '+');
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(grid, bottom, c, h);
        }
        setChar(grid, bottom, col + boxWidth - 1, unicode ? UNI_BR : '+');

        for (int i = 0; i < lines.size(); i++) {
            int r = row + 1 + i;
            setChar(grid, r, col, v);
            setChar(grid, r, col + boxWidth - 1, v);
            for (int c = col + 1; c < col + boxWidth - 1; c++) {
                setChar(grid, r, c, ' ');
            }
            String text = lines.get(i);
            if (text.length() > innerWidth) {
                text = text.substring(0, Math.max(1, innerWidth - 3)) + "...";
            }
            int textCol = col + 2 + Math.max(0, (innerWidth - text.length()) / 2);
            drawText(grid, r, textCol, text);
        }
    }

    private void drawArrow(char[][] grid, LayoutNode from, LayoutNode to) {
        drawArrow(grid, from, to, false, null);
    }

    private void drawArrow(
            char[][] grid, LayoutNode from, LayoutNode to,
            boolean highlighted, RouteDiagramHelper.HighlightStyle highlightStyle) {
        int fromCx = centerCol(from);
        int fromBottom = toRow(from.y) + boxHeight(from);
        int toCx = centerCol(to);
        int toTop = getTopRow(to);

        StatInfo stat = resolveStatInfo(to);
        long total = stat != null ? stat.exchangesTotal : 0;
        boolean dashed = metrics && total == 0;

        drawArrowPath(grid, fromCx, fromBottom, toCx, toTop, dashed);
        if (highlighted) {
            recordArrowHighlight(fromCx, fromBottom, toCx, toTop, highlightStyle);
        }
        drawCounters(grid, toCx, toTop, stat);
    }

    private void drawMergeArrow(char[][] grid, LayoutNode to) {
        drawMergeArrow(grid, to, false, null);
    }

    private void drawMergeArrow(
            char[][] grid, LayoutNode to,
            boolean highlighted, RouteDiagramHelper.HighlightStyle highlightStyle) {
        int fromCx = toCol(to.mergeCx);
        int fromRow = toRow(to.mergeY);
        int toCx = centerCol(to);
        int toTop = getTopRow(to);

        StatInfo stat = metrics ? to.treeNode.info.stat : null;
        long total = stat != null ? stat.exchangesTotal : 0;
        boolean dashed = metrics && total == 0;

        drawArrowPath(grid, fromCx, fromRow, toCx, toTop, dashed);
        if (highlighted) {
            recordArrowHighlight(fromCx, fromRow, toCx, toTop, highlightStyle);
        }
        drawCounters(grid, toCx, toTop, stat);
    }

    private void recordArrowHighlight(
            int fromCx, int fromRow, int toCx, int toRow,
            RouteDiagramHelper.HighlightStyle style) {
        CounterType ct = style == RouteDiagramHelper.HighlightStyle.FAIL
                ? CounterType.HIGHLIGHT_FAIL
                : CounterType.HIGHLIGHT_SUCCESS;

        if (fromCx == toCx) {
            for (int r = fromRow; r < toRow; r++) {
                counterPositions.add(new CounterPos(r, fromCx, 1, ct));
            }
        } else {
            int midRow = fromRow + (toRow - fromRow) / 2;
            for (int r = fromRow; r <= midRow; r++) {
                counterPositions.add(new CounterPos(r, fromCx, 1, ct));
            }
            int minC = Math.min(fromCx, toCx);
            int maxC = Math.max(fromCx, toCx);
            counterPositions.add(new CounterPos(midRow, minC, maxC - minC + 1, ct));
            for (int r = midRow; r < toRow; r++) {
                counterPositions.add(new CounterPos(r, toCx, 1, ct));
            }
        }
    }

    private StatInfo resolveStatInfo(LayoutNode to) {
        if (!metrics) {
            return null;
        }
        StatInfo stat = to.treeNode.info.stat;
        if (BRANCH_CHILD_TYPES.contains(to.type) && !to.treeNode.children.isEmpty()) {
            stat = to.treeNode.children.get(0).info.stat;
        }
        return stat;
    }

    private void drawCounters(char[][] grid, int toCx, int toTop, StatInfo stat) {
        if (!metrics || stat == null) {
            return;
        }
        long total = stat.exchangesTotal;
        long failed = stat.exchangesFailed;
        long ok = total - failed;
        if (ok > 0) {
            String okStr = "" + ok;
            int col = toCx + 2;
            drawText(grid, toTop - 1, col, okStr);
            counterPositions.add(new CounterPos(toTop - 1, col, okStr.length(), CounterType.OK));
        }
        if (failed > 0) {
            String failStr = "" + failed;
            int col = toCx - 1 - failStr.length();
            drawText(grid, toTop - 1, col, failStr);
            counterPositions.add(new CounterPos(toTop - 1, col, failStr.length(), CounterType.FAIL));
        }
    }

    private void drawArrowPath(char[][] grid, int fromCx, int fromRow, int toCx, int toRow, boolean dashed) {
        if (fromRow >= toRow) {
            return;
        }

        char v = dashed ? (unicode ? UNI_DASH_V : ':') : (unicode ? UNI_V : '|');
        char h = dashed ? (unicode ? UNI_DASH_H : '.') : (unicode ? UNI_H : '-');
        char arrow = unicode ? UNI_ARROW : 'v';

        if (fromCx == toCx) {
            for (int r = fromRow; r < toRow - 1; r++) {
                plotLine(grid, r, fromCx, v);
            }
            setChar(grid, toRow - 1, toCx, arrow);
        } else {
            int midRow = fromRow + (toRow - fromRow) / 2;

            for (int r = fromRow; r < midRow; r++) {
                plotLine(grid, r, fromCx, v);
            }

            int minC = Math.min(fromCx, toCx);
            int maxC = Math.max(fromCx, toCx);
            for (int c = minC; c <= maxC; c++) {
                plotLine(grid, midRow, c, h);
            }

            if (unicode) {
                setChar(grid, midRow, fromCx, UNI_T_UP);
                setChar(grid, midRow, toCx, UNI_T_DOWN);
            } else {
                setChar(grid, midRow, fromCx, '+');
                setChar(grid, midRow, toCx, '+');
            }

            for (int r = midRow + 1; r < toRow - 1; r++) {
                plotLine(grid, r, toCx, v);
            }
            setChar(grid, toRow - 1, toCx, arrow);
        }
    }

    private void drawScopeBox(char[][] grid, LayoutNode scopeNode) {
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

        if (unicode) {
            for (int c = col1; c <= col2; c++) {
                setChar(grid, row1, c, UNI_DASH_H);
                setChar(grid, row2, c, UNI_DASH_H);
            }
            for (int r = row1 + 1; r < row2; r++) {
                setChar(grid, r, col1, UNI_DASH_V);
                setChar(grid, r, col2, UNI_DASH_V);
            }
        } else {
            drawDashedHLine(grid, row1, col1, col2);
            drawDashedHLine(grid, row2, col1, col2);
            for (int r = row1 + 1; r < row2; r++) {
                setChar(grid, r, col1, ':');
                setChar(grid, r, col2, ':');
            }
        }
    }

    private void drawDashedHLine(char[][] grid, int row, int col1, int col2) {
        for (int c = col1; c <= col2; c++) {
            if (c == col1 || c == col2) {
                setChar(grid, row, c, '+');
            } else if ((c - col1) % 2 == 0) {
                setChar(grid, row, c, '-');
            }
        }
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
        return RouteDiagramHelper.wrapText(label, maxWidth);
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

    private void setChar(char[][] grid, int row, int col, char ch) {
        if (row >= 0 && row < grid.length && col >= 0 && col < grid[0].length) {
            grid[row][col] = ch;
        }
    }

    private char getChar(char[][] grid, int row, int col) {
        if (row >= 0 && row < grid.length && col >= 0 && col < grid[0].length) {
            return grid[row][col];
        }
        return ' ';
    }

    private boolean isVertical(char ch) {
        return ch == '|' || ch == UNI_V;
    }

    private boolean isHorizontal(char ch) {
        return ch == '-' || ch == UNI_H;
    }

    private void plotLine(char[][] grid, int row, int col, char ch) {
        char current = getChar(grid, row, col);
        if ((isVertical(current) && isHorizontal(ch)) || (isHorizontal(current) && isVertical(ch))) {
            setChar(grid, row, col, unicode ? UNI_CROSS : '+');
        } else {
            setChar(grid, row, col, ch);
        }
    }

    private void drawText(char[][] grid, int row, int col, String text) {
        for (int i = 0; i < text.length(); i++) {
            setChar(grid, row, col + i, text.charAt(i));
        }
    }

    private String gridToString(char[][] grid) {
        int lastRow = 0;
        for (int r = 0; r < grid.length; r++) {
            if (!new String(grid[r]).isBlank()) {
                lastRow = r;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= lastRow; r++) {
            sb.append(new String(grid[r]).stripTrailing());
            sb.append('\n');
        }
        return sb.toString();
    }
}
