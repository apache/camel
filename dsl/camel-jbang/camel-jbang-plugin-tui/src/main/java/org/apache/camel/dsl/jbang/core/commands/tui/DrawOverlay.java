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
                buffer.setString(cell.x, cell.y, cell.symbol, cell.style);
            }
        }
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
