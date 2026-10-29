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

import java.util.TreeSet;

import org.apache.camel.diagram.TopologyErrorFrame;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;

/**
 * The topology at a {@link RouteDensity}: the compact one draws a box of one line, the bare one a mark and a name on a
 * row, so the layers come closer together and the boxes narrower. The engine lays the routes out in layers a box and a
 * gap apart; {@link #apply} moves the layers (and the frame of the error handling below them) up to the rows of the
 * density, keeping their order and the wider gap before a band.
 */
public final class TopologyZoom {

    /** The rows from one layer to the next in the engine's layout: its box and the gap below it. */
    private static final int ENGINE_STEP = 9;
    /** The widest a box is drawn at the compact density, and the width of a bare step. */
    private static final int COMPACT_COLUMNS = 26;
    private static final int BARE_COLUMNS = 16;

    private TopologyZoom() {
    }

    /** The rows of a box at the density; the detailed box has as many as its lines need. */
    public static int boxRows(RouteDensity density) {
        return density.boxed() ? 3 : 1;
    }

    /** The rows from one layer to the next: a box, and room for the arrows that branch between them. */
    private static int step(RouteDensity density) {
        return density.boxed() ? 6 : 4;
    }

    /** How many columns wide the boxes are laid out: as wide as wanted when detailed, narrower when zoomed out. */
    public static int columns(RouteDensity density, int wanted) {
        return switch (density) {
            case DETAILED -> wanted;
            case COMPACT -> Math.min(wanted, COMPACT_COLUMNS);
            case BARE, OVERVIEW -> BARE_COLUMNS;
        };
    }

    /** A topology at a density: its layout, and the frame of its error handling (or null). */
    public record Zoomed(TopologyLayoutResult layout, TopologyErrorFrame frame) {
    }

    /**
     * Moves the layers of a topology laid out by the engine up to the rows of the density, the error frame with them.
     */
    public static Zoomed apply(RouteDensity density, TopologyLayoutResult layout, TopologyErrorFrame frame) {
        if (density == RouteDensity.DETAILED || layout == null || layout.nodes.isEmpty()) {
            return new Zoomed(layout, frame);
        }
        TreeSet<Integer> ys = new TreeSet<>();
        int engineBottom = 0;
        for (TopologyLayoutNode n : layout.nodes) {
            ys.add(n.y);
            engineBottom = Math.max(engineBottom, n.y + n.height);
        }
        // the padding below the last layer, as the engine leaves it
        int padding = Math.max(0, layout.totalHeight - engineBottom);
        int top = ys.first();
        double ratio = (double) step(density) / ENGINE_STEP;
        int bottom = 0;
        for (TopologyLayoutNode n : layout.nodes) {
            n.y = place(n.y, top, ratio);
            bottom = Math.max(bottom, n.y + boxRows(density) * RouteDensity.ROW);
        }
        TopologyErrorFrame moved = frame != null
                ? new TopologyErrorFrame(place(frame.topY(), top, ratio), frame.routeIds(), frame.paths()) : null;
        return new Zoomed(
                new TopologyLayoutResult(layout.nodes, layout.edges, layout.totalWidth, bottom + padding), moved);
    }

    /** Whether a topology fits the rows and columns of the screen, so fit need not zoom out further. */
    public static boolean fits(TopologyLayoutResult layout, int nodeWidth, int rows, int cols) {
        if (layout == null || nodeWidth == 0 || rows <= 0 || cols <= 0) {
            return true;
        }
        // the widget draws a box nodeWidth / 15 columns wide, and at least 16
        int boxWidth = Math.max(16, nodeWidth / 15);
        return layout.totalHeight / RouteDensity.ROW <= rows && layout.totalWidth * boxWidth / nodeWidth <= cols;
    }

    /** A row of the engine's layout on the rows of the density, at the top of a row. */
    private static int place(int y, int top, double ratio) {
        int rows = (int) Math.round((y - top) * ratio / RouteDensity.ROW);
        return top + rows * RouteDensity.ROW;
    }
}
