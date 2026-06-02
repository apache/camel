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

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutEdge;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;

/**
 * Renders topology diagrams as ASCII art or Unicode box-drawing text.
 */
public class TopologyAsciiRenderer {

    private static final int Y_SCALE = 20;
    private static final int MIN_BOX_WIDTH = 16;
    private static final int X_DIVISOR = 15;
    private static final int MAX_WRAP_LINES = 3;

    private static final char UNI_H = '─';
    private static final char UNI_V = '│';
    private static final char UNI_TL = '┌';
    private static final char UNI_TR = '┐';
    private static final char UNI_BL = '└';
    private static final char UNI_BR = '┘';
    private static final char UNI_T_DOWN = '┬';
    private static final char UNI_T_UP = '┴';
    private static final char UNI_CROSS = '┼';
    private static final char UNI_ARROW = '▼';
    private static final char UNI_DASH_V = '┆';
    private static final char UNI_DASH_H = '┄';

    private final int nodeWidth;
    private final int boxWidth;
    private final boolean unicode;
    private final boolean metrics;
    private final boolean showDescription;
    private final List<CounterPos> counterPositions = new ArrayList<>();

    public enum CounterType {
        OK,
        FAIL,
        TRIGGER,
        EXTERNAL
    }

    public record CounterPos(int row, int col, int length, CounterType type) {
    }

    public TopologyAsciiRenderer(int nodeWidth, boolean unicode) {
        this(nodeWidth, unicode, false, false);
    }

    public TopologyAsciiRenderer(int nodeWidth, boolean unicode, boolean metrics, boolean showDescription) {
        this.nodeWidth = nodeWidth;
        this.boxWidth = Math.max(MIN_BOX_WIDTH, nodeWidth / X_DIVISOR);
        this.unicode = unicode;
        this.metrics = metrics;
        this.showDescription = showDescription;
    }

    public int getBoxWidth() {
        return boxWidth;
    }

    public List<CounterPos> getCounterPositions() {
        return counterPositions;
    }

    public String renderDiagram(TopologyLayoutResult result) {
        String plain = renderDiagramPlain(result);
        return applyAnsiColors(plain);
    }

    public String renderDiagramPlain(TopologyLayoutResult result) {
        counterPositions.clear();

        int gridWidth = toCol(result.totalWidth) + boxWidth + 4;
        int gridHeight = toRow(result.totalHeight) + 10;
        gridWidth = Math.max(gridWidth, 40);
        gridHeight = Math.max(gridHeight, 10);

        char[][] grid = new char[gridHeight][gridWidth];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }

        // Draw edges first (behind nodes)
        for (TopologyLayoutEdge edge : result.edges) {
            if (!edge.selfLoop) {
                drawEdge(grid, edge);
            }
        }

        // Draw self-loops
        for (TopologyLayoutEdge edge : result.edges) {
            if (edge.selfLoop) {
                drawSelfLoop(grid, edge);
            }
        }

        // Draw nodes on top
        for (TopologyLayoutNode node : result.nodes) {
            drawNode(grid, node);
        }

        return gridToString(grid);
    }

    private static boolean isExternalNode(TopologyLayoutNode node) {
        return "external-in".equals(node.nodeType) || "external-out".equals(node.nodeType);
    }

    private void drawNode(char[][] grid, TopologyLayoutNode node) {
        int col = toCol(node.x);
        int row = toRow(node.y);

        String line1;
        if (isExternalNode(node)) {
            line1 = node.from;
        } else if (showDescription && node.description != null && !node.description.isBlank()) {
            line1 = node.description;
        } else {
            line1 = node.routeId;
        }

        List<String> lines = new ArrayList<>();
        lines.addAll(wrapText(line1, boxWidth - 4));
        if (!isExternalNode(node) && !showDescription) {
            String line2 = "(" + node.from + ")";
            List<String> fromLines = wrapText(line2, boxWidth - 4);
            lines.addAll(fromLines);
            // Always reserve 2 lines for from so all boxes have the same height
            if (fromLines.size() < 2) {
                lines.add("");
            }
        }

        if (metrics) {
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
                    sb.append(node.exchangesFailed).append("!");
                }
                lines.add(sb.toString());
            } else if (!isExternalNode(node)) {
                lines.add("");
            }
        }

        // Trim to MAX_WRAP_LINES
        while (lines.size() > MAX_WRAP_LINES + 1) {
            lines.remove(lines.size() - 1);
        }

        int height = 2 + lines.size();
        if (row + height >= grid.length) {
            return;
        }

        char h = unicode ? UNI_H : '-';
        char v = unicode ? UNI_V : '|';

        // Top border
        setChar(grid, row, col, unicode ? UNI_TL : '+');
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(grid, row, c, h);
        }
        setChar(grid, row, col + boxWidth - 1, unicode ? UNI_TR : '+');

        // Bottom border
        int bottom = row + height - 1;
        setChar(grid, bottom, col, unicode ? UNI_BL : '+');
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(grid, bottom, c, h);
        }
        setChar(grid, bottom, col + boxWidth - 1, unicode ? UNI_BR : '+');

        // Content
        int innerWidth = boxWidth - 4;
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

            // Track counter positions for ANSI coloring
            if (isExternalNode(node) && i == 0) {
                counterPositions.add(new CounterPos(r, textCol, text.length(), CounterType.EXTERNAL));
            }
            if (metrics && i == lines.size() - 1 && node.exchangesTotal > 0) {
                long ok = node.exchangesTotal - node.exchangesFailed;
                if (ok > 0) {
                    String okStr = "" + ok;
                    counterPositions.add(new CounterPos(r, textCol, okStr.length(), CounterType.OK));
                }
                if (node.exchangesFailed > 0) {
                    String failStr = node.exchangesFailed + "!";
                    int failCol = textCol + text.length() - failStr.length();
                    counterPositions.add(new CounterPos(r, failCol, failStr.length(), CounterType.FAIL));
                }
            }
        }
    }

    private void drawEdge(char[][] grid, TopologyLayoutEdge edge) {
        int fromCx = toCol(edge.from.x + edge.from.width / 2);
        int fromBottom = toRow(edge.from.y) + boxHeight(edge.from);
        int toCx = toCol(edge.to.x + edge.to.width / 2);
        int toTop = toRow(edge.to.y);

        if (fromBottom >= toTop) {
            return;
        }

        boolean dashed = isExternalNode(edge.from) || isExternalNode(edge.to);
        char v = dashed ? (unicode ? UNI_DASH_V : ':') : (unicode ? UNI_V : '|');
        char h = dashed ? (unicode ? UNI_DASH_H : '-') : (unicode ? UNI_H : '-');
        char arrow = unicode ? UNI_ARROW : 'v';

        if (fromCx == toCx) {
            for (int r = fromBottom; r < toTop - 1; r++) {
                plotLine(grid, r, fromCx, v);
            }
            setChar(grid, toTop - 1, toCx, arrow);
        } else {
            int midRow = fromBottom + (toTop - fromBottom) / 2;

            for (int r = fromBottom; r < midRow; r++) {
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

            for (int r = midRow + 1; r < toTop - 1; r++) {
                plotLine(grid, r, toCx, v);
            }
            setChar(grid, toTop - 1, toCx, arrow);
        }

    }

    private void drawSelfLoop(char[][] grid, TopologyLayoutEdge edge) {
        int col = toCol(edge.from.x) + boxWidth;
        int topRow = toRow(edge.from.y) + 1;
        int botRow = topRow + 2;

        char h = unicode ? UNI_H : '-';
        char v = unicode ? UNI_V : '|';

        // Small loop on right side
        for (int c = col; c < col + 3; c++) {
            setChar(grid, topRow, c, h);
            setChar(grid, botRow, c, h);
        }
        setChar(grid, topRow + 1, col + 2, v);
        setChar(grid, topRow, col + 2, unicode ? UNI_TR : '+');
        setChar(grid, botRow, col + 2, unicode ? UNI_BR : '+');

    }

    private int boxHeight(TopologyLayoutNode node) {
        if (isExternalNode(node)) {
            int lines = 1; // URI
            if (metrics && node.exchangesTotal > 0) {
                lines++;
            }
            return 2 + lines;
        }
        int lines = 3; // routeId + from (2 lines reserved)
        if (metrics) {
            lines++;
        }
        return 2 + Math.min(lines, MAX_WRAP_LINES + 1);
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
                    String color = switch (cp.type) {
                        case OK -> "\033[32m";
                        case FAIL -> "\033[31m";
                        case TRIGGER -> "\033[33m";
                        case EXTERNAL -> "\033[36m";
                    };
                    lines[cp.row] = before + color + counter + "\033[0m" + after;
                }
            }
        }
        return String.join("\n", lines);
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
        return ch == '|' || ch == UNI_V || ch == ':' || ch == UNI_DASH_V;
    }

    private boolean isHorizontal(char ch) {
        return ch == '-' || ch == UNI_H || ch == UNI_DASH_H;
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
