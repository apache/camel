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

import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;

import static org.apache.camel.diagram.RouteDiagramLayoutEngine.BRANCH_CHILD_TYPES;

/**
 * How much room a step of the route diagram takes: the zoom levels of a route. A terminal cannot make its font smaller,
 * so zooming out draws a step with fewer rows and puts the steps closer together.
 * <ul>
 * <li>{@link #DETAILED}: a box with up to three lines of text, as the layout engine places it</li>
 * <li>{@link #COMPACT}: a box with one line of text, an arrow of a row between the boxes</li>
 * <li>{@link #BARE}: no box, a step is a mark and its text on one row</li>
 * </ul>
 * The engine keeps the columns of the steps; {@link #apply} places them again on the rows of the density, top down as
 * the engine does, in its pixels (a row is {@link #ROW} of them). A block (a step with steps in it) takes a row above
 * it for the top of its frame (compact), and a row below it for the bottom of its frame (compact) or a blank row
 * (bare).
 */
public enum RouteDensity {

    DETAILED("detailed", 0, 0, 0, 0, 0),
    COMPACT("compact", 3, 1, 2, Px.ROW, Px.ROW),
    BARE("bare", 1, 1, 2, 0, Px.ROW);

    /** The pixels of the layout in a row of the terminal (the widget divides by it). */
    public static final int ROW = Px.ROW;

    private final String label;
    private final int nodeRows;
    private final int gap;
    private final int branchGap;
    private final int scopeTop;
    private final int scopeBottom;

    RouteDensity(String label, int nodeRows, int gapRows, int branchGapRows, int scopeTop, int scopeBottom) {
        this.label = label;
        this.nodeRows = nodeRows;
        this.gap = gapRows * Px.ROW;
        this.branchGap = branchGapRows * Px.ROW;
        this.scopeTop = scopeTop;
        this.scopeBottom = scopeBottom;
    }

    public String label() {
        return label;
    }

    /**
     * The width the engine lays a step out with (its box width, before its scale): a compact box has one line, so it is
     * wider than a detailed one, which wraps its text on three.
     */
    public int boxWidth() {
        return this == COMPACT ? 220 : RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH;
    }

    /** Where the density is among the levels, counted up as + zooms in: bare 1/3, compact 2/3, detailed 3/3. */
    public String position() {
        return (values().length - ordinal()) + "/" + values().length;
    }

    /** One level more detail, or this one when it is the most. */
    public RouteDensity zoomIn() {
        return this == DETAILED ? DETAILED : values()[ordinal() - 1];
    }

    /** One level less detail, or this one when it is the least. */
    public RouteDensity zoomOut() {
        return this == BARE ? BARE : values()[ordinal() + 1];
    }

    /** Whether the steps are drawn in boxes; the bare steps are a mark and a text, with no box around their blocks. */
    public boolean boxed() {
        return this != BARE;
    }

    /** The rows the diagram of the route takes. */
    public static int rows(LayoutRoute lr) {
        return lr.maxY / ROW;
    }

    /** Places the steps of a route laid out by the engine on the rows of this density; the detailed is the engine's. */
    public void apply(LayoutRoute lr) {
        if (this == DETAILED || lr.nodes.isEmpty() || lr.nodes.get(0).treeNode == null) {
            return;
        }
        lr.maxY = 0;
        LayoutNode root = lr.nodes.get(0);
        place(root.treeNode, root.y, lr);
    }

    private void place(TreeNode node, int y, LayoutRoute lr) {
        LayoutNode ln = node.layoutNode;
        ln.y = y;
        ln.height = height(node);
        lr.maxY = Math.max(lr.maxY, y + ln.height);
        if (node.table) {
            // the rows are inside the table's box
            for (TreeNode row : node.children) {
                row.layoutNode.y = y;
            }
            return;
        }
        if (node.children.isEmpty()) {
            return;
        }
        boolean branching = RouteDiagramLayoutEngine.isBranchingEip(node.info.type);
        int childY = y + ln.height + (branching ? branchGap : gap);
        if (branching) {
            for (TreeNode child : node.children) {
                boolean scope = !child.children.isEmpty() && !BRANCH_CHILD_TYPES.contains(child.info.type);
                place(child, scope ? childY + scopeTop : childY, lr);
            }
            return;
        }
        int curY = childY;
        for (int i = 0; i < node.children.size(); i++) {
            TreeNode child = node.children.get(i);
            boolean scope = RouteDiagramLayoutEngine.hasScope(child);
            place(child, scope ? curY + scopeTop : curY, lr);
            int bottom = scope ? blockBottom(child) + scopeBottom : bottom(child);
            if (scope && i + 1 < node.children.size()) {
                // the next step is reached from the bottom of the block's frame, not from its last step
                LayoutNode next = node.children.get(i + 1).layoutNode;
                if (next != null && next.connectFromMerge) {
                    next.mergeY = blockBottom(child);
                }
            }
            curY = bottom + gap;
        }
    }

    private int height(TreeNode node) {
        if (node.table) {
            int rows = node.children.size();
            int shown = Math.min(rows, RouteDiagramLayoutEngine.MAX_TABLE_ROWS)
                        + (rows > RouteDiagramLayoutEngine.MAX_TABLE_ROWS ? 1 : 0);
            return (2 + shown) * ROW;
        }
        return nodeRows * ROW;
    }

    /**
     * The bottom of a step and of all the steps in it, with the frames of the blocks in it: the row of the bottom of
     * its own frame.
     */
    public int blockBottom(TreeNode node) {
        int max = node.layoutNode.y + node.layoutNode.height;
        if (node.table) {
            return max;
        }
        for (TreeNode child : node.children) {
            int bottom = RouteDiagramLayoutEngine.hasScope(child) ? blockBottom(child) + scopeBottom : blockBottom(child);
            max = Math.max(max, bottom);
        }
        return max;
    }

    /** The bottom of a step that has no block of its own, and of the steps under it. */
    private static int bottom(TreeNode node) {
        int max = node.layoutNode != null ? node.layoutNode.y + node.layoutNode.height : 0;
        if (node.table) {
            return max;
        }
        for (TreeNode child : node.children) {
            max = Math.max(max, bottom(child));
        }
        return max;
    }

    // an enum constant cannot refer to a static field of its own enum in its constructor
    private static final class Px {
        static final int ROW = 20;
    }
}
