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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyMinimapWidget;

/**
 * The mini panels over the diagram: the map of the topology at the top right of a route's diagram, and the tree of the
 * route at the bottom right. They cover a corner of the diagram, so p hides them ({@link RouteDiagramZoom#isPanels()}),
 * and their titles say so.
 */
final class DiagramPanels {

    private DiagramPanels() {
    }

    /** The title of a mini panel: its name, and the key that hides it. */
    static Title title(String name) {
        return Title.from(Line.from(Span.styled(" " + name + " ", Style.EMPTY.dim()),
                Span.styled("p", Theme.hintKey()), Span.raw(" ")));
    }

    /** The map of the topology at the top right, with the route shown marked. */
    static void renderMinimap(Frame frame, Rect area, TopologyLayoutResult topology, String currentRouteId) {
        if (topology == null || topology.nodes.size() < 2) {
            return;
        }
        int mapW = Math.min(22, area.width() / 3);
        int mapH = Math.min(8, area.height() / 3);
        if (mapW < 6 || mapH < 3 || area.width() < 40 || area.height() < 12) {
            return;
        }
        Rect mapRect = new Rect(area.x() + area.width() - mapW - 1, area.y(), mapW, mapH);
        frame.renderWidget(Clear.INSTANCE, mapRect);
        Block mapBlock = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title("Map"))
                .build();
        frame.renderWidget(mapBlock, mapRect);
        frame.renderWidget(new TopologyMinimapWidget(topology, currentRouteId), mapBlock.inner(mapRect));
    }
}
