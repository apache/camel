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

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;

import static org.apache.camel.diagram.RouteDiagramLayoutEngine.PADDING;

/**
 * Renders route diagrams as plain ASCII art text.
 */
public class RouteDiagramAsciiRenderer {

    private static final int MAX_WRAP_LINES = 3;
    private static final int Y_SCALE = 20;
    private static final int MIN_BOX_WIDTH = 16;
    private static final int X_DIVISOR = 15;

    private final int nodeWidth;
    private final int boxWidth;

    public RouteDiagramAsciiRenderer(int nodeWidth) {
        this.nodeWidth = nodeWidth;
        this.boxWidth = Math.max(MIN_BOX_WIDTH, nodeWidth / X_DIVISOR);
    }

    public int getBoxWidth() {
        return boxWidth;
    }

    public String renderDiagram(List<LayoutRoute> layoutRoutes, int totalHeight) {
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

    private void drawRoute(char[][] grid, LayoutRoute lr) {
        int labelRow = toRow(lr.labelY);
        String label = lr.routeId;
        if (lr.source != null && !lr.source.isEmpty()) {
            label += " (" + lr.source + ")";
        }
        drawText(grid, labelRow, toCol(PADDING), label);

        for (LayoutNode ln : lr.nodes) {
            if (ln.parentNode != null) {
                if (ln.connectFromMerge) {
                    drawMergeArrow(grid, ln);
                } else {
                    drawArrow(grid, ln.parentNode, ln);
                }
            }
        }

        for (LayoutNode ln : lr.nodes) {
            drawNode(grid, ln);
        }
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

        setChar(grid, row, col, '+');
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(grid, row, c, '-');
        }
        setChar(grid, row, col + boxWidth - 1, '+');

        int bottom = row + height - 1;
        setChar(grid, bottom, col, '+');
        for (int c = col + 1; c < col + boxWidth - 1; c++) {
            setChar(grid, bottom, c, '-');
        }
        setChar(grid, bottom, col + boxWidth - 1, '+');

        for (int i = 0; i < lines.size(); i++) {
            int r = row + 1 + i;
            setChar(grid, r, col, '|');
            setChar(grid, r, col + boxWidth - 1, '|');
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
        int fromCx = centerCol(from);
        int fromBottom = toRow(from.y) + boxHeight(from);
        int toCx = centerCol(to);
        int toTop = toRow(to.y);

        drawArrowPath(grid, fromCx, fromBottom, toCx, toTop);
    }

    private void drawMergeArrow(char[][] grid, LayoutNode to) {
        int fromCx = toCol(to.mergeCx);
        int fromRow = toRow(to.mergeY);
        int toCx = centerCol(to);
        int toTop = toRow(to.y);

        drawArrowPath(grid, fromCx, fromRow, toCx, toTop);
    }

    private void drawArrowPath(char[][] grid, int fromCx, int fromRow, int toCx, int toRow) {
        if (fromRow >= toRow) {
            return;
        }

        if (fromCx == toCx) {
            for (int r = fromRow; r < toRow - 1; r++) {
                plotLine(grid, r, fromCx, '|');
            }
            setChar(grid, toRow - 1, toCx, 'v');
        } else {
            int midRow = fromRow + (toRow - fromRow) / 2;

            for (int r = fromRow; r < midRow; r++) {
                plotLine(grid, r, fromCx, '|');
            }

            int minC = Math.min(fromCx, toCx);
            int maxC = Math.max(fromCx, toCx);
            for (int c = minC; c <= maxC; c++) {
                plotLine(grid, midRow, c, '-');
            }
            setChar(grid, midRow, fromCx, '+');
            setChar(grid, midRow, toCx, '+');

            for (int r = midRow + 1; r < toRow - 1; r++) {
                plotLine(grid, r, toCx, '|');
            }
            setChar(grid, toRow - 1, toCx, 'v');
        }
    }

    private int centerCol(LayoutNode node) {
        return toCol(node.x + nodeWidth / 2);
    }

    private int boxHeight(LayoutNode node) {
        return 2 + rewrapText(node, boxWidth - 4).size();
    }

    private List<String> rewrapText(LayoutNode node, int maxWidth) {
        String label = String.join("", node.wrappedLines);
        return wrapText(label, maxWidth);
    }

    static List<String> wrapText(String text, int maxWidth) {
        if (maxWidth <= 0 || text.length() <= maxWidth) {
            return List.of(text);
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
            if (lastLine.length() + remaining.length() <= maxWidth) {
                lines.set(lastIdx, lastLine + remaining);
            } else {
                String combined = lastLine + remaining;
                lines.set(lastIdx, combined.substring(0, Math.max(1, maxWidth - 3)) + "...");
            }
        }

        return lines;
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

    private void plotLine(char[][] grid, int row, int col, char ch) {
        char current = getChar(grid, row, col);
        if ((current == '|' && ch == '-') || (current == '-' && ch == '|')) {
            setChar(grid, row, col, '+');
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
