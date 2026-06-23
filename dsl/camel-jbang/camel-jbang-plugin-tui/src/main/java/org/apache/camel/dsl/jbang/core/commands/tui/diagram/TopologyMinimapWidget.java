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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.widget.Widget;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;

public class TopologyMinimapWidget implements Widget {

    private static final Style CURRENT_STYLE = Style.EMPTY.fg(Color.YELLOW).bold();
    private static final Style OTHER_STYLE = Style.EMPTY.fg(Color.DARK_GRAY);
    private static final Style EXTERNAL_STYLE = Style.EMPTY.fg(Color.CYAN).dim();

    private final TopologyLayoutResult layout;
    private final String currentRouteId;

    public TopologyMinimapWidget(TopologyLayoutResult layout, String currentRouteId) {
        this.layout = layout;
        this.currentRouteId = currentRouteId;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        if (layout == null || layout.nodes.isEmpty() || area.width() < 2 || area.height() < 1) {
            return;
        }

        int mapW = area.width();
        int mapH = area.height();
        int totalW = layout.totalWidth;
        int totalH = layout.totalHeight;

        if (totalW <= 0 || totalH <= 0) {
            return;
        }

        for (TopologyLayoutNode node : layout.nodes) {
            if (node.nodeType != null && node.nodeType.startsWith("external")) {
                continue;
            }
            boolean isCurrent = node.routeId != null && node.routeId.equals(currentRouteId);

            int col = node.x * mapW / totalW;
            int row = node.y * mapH / totalH;
            int nodeW = Math.min(3, Math.max(1, node.width * mapW / totalW));
            int nodeH = Math.max(1, node.height * mapH / totalH);

            col = Math.min(col, mapW - nodeW);
            row = Math.min(row, mapH - nodeH);
            col = Math.max(0, col);
            row = Math.max(0, row);

            Style style = isCurrent ? CURRENT_STYLE : OTHER_STYLE;

            drawMiniBox(buffer, area, col, row, nodeW, nodeH, style, isCurrent);
        }
    }

    private void drawMiniBox(Buffer buffer, Rect area, int col, int row, int w, int h, Style style, boolean current) {
        int sx = area.x() + col;
        int sy = area.y() + row;

        String fill = current ? "█" : "▪";

        for (int r = 0; r < h; r++) {
            int y = sy + r;
            if (y >= area.y() + area.height()) {
                break;
            }
            for (int c = 0; c < w; c++) {
                int x = sx + c;
                if (x >= area.x() + area.width()) {
                    break;
                }
                buffer.setString(x, y, fill, style);
            }
        }
    }
}
