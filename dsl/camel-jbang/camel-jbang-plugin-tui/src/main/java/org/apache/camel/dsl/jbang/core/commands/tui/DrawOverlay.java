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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;

class DrawOverlay {

    record DrawCell(int x, int y, String symbol, Style style) {
    }

    private List<DrawCell> cells;
    private long autoDismissTime;

    boolean isVisible() {
        return cells != null && !cells.isEmpty();
    }

    void setDrawing(List<DrawCell> newCells, int durationSeconds) {
        this.cells = new ArrayList<>(newCells);
        if (durationSeconds > 0) {
            this.autoDismissTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        } else {
            this.autoDismissTime = 0;
        }
    }

    void appendDrawing(List<DrawCell> newCells) {
        if (this.cells == null) {
            this.cells = new ArrayList<>(newCells);
        } else {
            this.cells.addAll(newCells);
        }
    }

    void clear() {
        cells = null;
        autoDismissTime = 0;
    }

    void tick(long now) {
        if (autoDismissTime > 0 && now > autoDismissTime) {
            clear();
        }
    }

    void render(Frame frame, Rect area) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        Buffer buffer = frame.buffer();
        Rect screenArea = buffer.area();
        for (DrawCell cell : cells) {
            if (cell.x >= 0 && cell.y >= 0
                    && cell.x < screenArea.width() && cell.y < screenArea.height()) {
                if (cell.symbol == null) {
                    // highlight mode: keep existing symbol, apply style as background
                    var existing = buffer.get(cell.x, cell.y);
                    if (existing != null && existing != dev.tamboui.buffer.Cell.CONTINUATION) {
                        Style merged = existing.style().bg(cell.style.bg().orElse(Color.YELLOW));
                        buffer.setString(cell.x, cell.y, existing.symbol(), merged);
                    }
                } else {
                    buffer.setString(cell.x, cell.y, cell.symbol, cell.style);
                }
            }
        }
    }

    static List<DrawCell> generateShape(String shape, int x, int y, int width, int height, int length, Color color) {
        return switch (shape) {
            case "box" -> generateBox(x, y, width, height, color);
            case "highlight" -> generateHighlight(x, y, width, height, color);
            case "underline" -> generateUnderline(x, y, width, color);
            case "arrow-down" -> generateArrow(x, y, length, 0, 1, color);
            case "arrow-up" -> generateArrow(x, y, length, 0, -1, color);
            case "arrow-right" -> generateArrow(x, y, length, 1, 0, color);
            case "arrow-left" -> generateArrow(x, y, length, -1, 0, color);
            default -> List.of();
        };
    }

    private static List<DrawCell> generateBox(int x, int y, int w, int h, Color color) {
        List<DrawCell> cells = new ArrayList<>();
        Style s = Style.EMPTY.fg(color).bold();
        cells.add(new DrawCell(x, y, "┌", s));
        cells.add(new DrawCell(x + w - 1, y, "┐", s));
        cells.add(new DrawCell(x, y + h - 1, "└", s));
        cells.add(new DrawCell(x + w - 1, y + h - 1, "┘", s));
        for (int i = 1; i < w - 1; i++) {
            cells.add(new DrawCell(x + i, y, "─", s));
            cells.add(new DrawCell(x + i, y + h - 1, "─", s));
        }
        for (int j = 1; j < h - 1; j++) {
            cells.add(new DrawCell(x, y + j, "│", s));
            cells.add(new DrawCell(x + w - 1, y + j, "│", s));
        }
        return cells;
    }

    private static List<DrawCell> generateHighlight(int x, int y, int w, int h, Color color) {
        List<DrawCell> cells = new ArrayList<>();
        Style s = Style.EMPTY.bg(color);
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                cells.add(new DrawCell(x + col, y + row, null, s));
            }
        }
        return cells;
    }

    private static List<DrawCell> generateUnderline(int x, int y, int w, Color color) {
        List<DrawCell> cells = new ArrayList<>();
        Style s = Style.EMPTY.fg(color).bold();
        for (int i = 0; i < w; i++) {
            cells.add(new DrawCell(x + i, y, "─", s));
        }
        return cells;
    }

    private static List<DrawCell> generateArrow(int x, int y, int length, int dx, int dy, Color color) {
        List<DrawCell> cells = new ArrayList<>();
        Style s = Style.EMPTY.fg(color).bold();
        String shaft = dy != 0 ? "│" : "─";
        String head;
        if (dx > 0) {
            head = TuiIcons.ARROW_RIGHT;
        } else if (dx < 0) {
            head = TuiIcons.ARROW_LEFT;
        } else if (dy > 0) {
            head = TuiIcons.SORT_DOWN;
        } else {
            head = TuiIcons.SORT_UP;
        }
        for (int i = 0; i < length - 1; i++) {
            cells.add(new DrawCell(x + i * dx, y + i * dy, shaft, s));
        }
        cells.add(new DrawCell(x + (length - 1) * dx, y + (length - 1) * dy, head, s));
        return cells;
    }

    static List<DrawCell> generateText(int x, int y, String text, Color color) {
        List<DrawCell> cells = new ArrayList<>();
        Style s = Style.EMPTY.fg(color).bold();
        int col = x;
        int row = y;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            if (cp == '\n') {
                row++;
                col = x;
                i++;
                continue;
            }
            String ch = new String(Character.toChars(cp));
            cells.add(new DrawCell(col, row, ch, s));
            col += Math.max(1, dev.tamboui.text.CharWidth.of(cp));
            i += Character.charCount(cp);
        }
        return cells;
    }

    static Color parseColor(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return switch (name.toLowerCase().trim()) {
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "blue" -> Color.BLUE;
            case "yellow" -> Color.YELLOW;
            case "cyan" -> Color.CYAN;
            case "magenta" -> Color.MAGENTA;
            case "white" -> Color.WHITE;
            case "gray", "grey" -> Color.GRAY;
            case "dark_gray", "dark_grey", "darkgray", "darkgrey" -> Color.DARK_GRAY;
            case "light_red", "lightred" -> Color.LIGHT_RED;
            case "light_green", "lightgreen" -> Color.LIGHT_GREEN;
            case "light_blue", "lightblue" -> Color.LIGHT_BLUE;
            case "light_yellow", "lightyellow" -> Color.LIGHT_YELLOW;
            case "light_cyan", "lightcyan" -> Color.LIGHT_CYAN;
            case "light_magenta", "lightmagenta" -> Color.LIGHT_MAGENTA;
            case "black" -> Color.BLACK;
            default -> null;
        };
    }
}
