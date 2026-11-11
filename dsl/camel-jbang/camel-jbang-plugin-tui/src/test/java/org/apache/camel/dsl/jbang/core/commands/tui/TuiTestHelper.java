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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;

/**
 * Shared test utilities for TUI rendering tests. Provides helpers for rendering tabs into virtual terminal buffers and
 * inspecting the rendered output.
 */
final class TuiTestHelper {

    private TuiTestHelper() {
    }

    /**
     * Renders a tab into a virtual buffer and extracts the full text content.
     */
    static String renderToString(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
        return bufferToString(buffer);
    }

    /**
     * Extracts all text from a buffer row by row.
     */
    static String bufferToString(Buffer buffer) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                String sym = buffer.get(x, y).symbol();
                sb.append(sym.isEmpty() ? " " : sym);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Searches a buffer for a cell whose symbol matches {@code symbol} and whose foreground color equals
     * {@code expectedFg}.
     */
    static boolean findCellWithColor(Buffer buffer, String symbol, Color expectedFg) {
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                var cell = buffer.get(x, y);
                if (symbol.equals(cell.symbol())) {
                    var fg = cell.style().fg().orElse(null);
                    if (expectedFg.equals(fg)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Renders a tab into a virtual buffer, for the styles of its cells. */
    static Buffer renderToBuffer(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        tab.render(Frame.forTesting(buffer), area);
        return buffer;
    }

    /** The colour of the border of the panel with this title, from its top left corner, or null when none. */
    static Color borderColor(Buffer buffer, String title) {
        for (int y = 0; y < buffer.height(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < buffer.width(); x++) {
                String sym = buffer.get(x, y).symbol();
                row.append(sym.isEmpty() ? " " : sym.substring(0, 1));
            }
            int at = row.indexOf(title);
            if (at >= 0) {
                int corner = row.lastIndexOf("╭", at);
                return corner >= 0 ? buffer.get(corner, y).style().fg().orElse(null) : null;
            }
        }
        return null;
    }
}
