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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Layout engine that builds a tree from flat route node lists and computes positions for diagram rendering.
 */
public class RouteDiagramLayoutEngine {

    public static final int SCALE = 2;
    public static final int V_GAP = 40 * SCALE;
    public static final int PADDING = 30 * SCALE;
    public static final int SCOPE_BOX_PAD = 14 * SCALE;
    private static final int LABEL_OFFSET = 24 * SCALE;
    private static final int MAX_WRAP_LINES = 3;
    public static final int DEFAULT_FONT_SIZE = 12;
    public static final int DEFAULT_BOX_WIDTH = 180;
    private static final int MIN_BOX_WIDTH = 80;
    private static final int DEFAULT_NODE_HEIGHT = 32;

    public enum NodeLabelMode {
        CODE,
        DESCRIPTION,
        BOTH
    }

    private final int nodeWidth;
    private final int hGap;
    private final int nodeTextPadding;
    private final int baseNodeHeight;
    private final FontMetrics fontMetrics;
    private final NodeLabelMode nodeLabelMode;
    private boolean tableLayout;

    static final Set<String> BRANCHING_EIPS = Set.of(
            "choice", "multicast", "doTry", "loadBalance", "recipientList", "circuitBreaker");

    public static final Set<String> BRANCH_CHILD_TYPES = Set.of(
            "when", "otherwise", "doCatch", "doFinally", "onFallback");

    static final Set<String> STRUCTURAL_TYPES = Set.of(
            "route", "from");

    /**
     * The EIPs drawn as a table, one row per child, with the table layout on: a Switch is a decision table of value and
     * endpoint, its cases are alternatives (not steps after each other) and can be many.
     */
    public static final Set<String> TABLE_EIPS = Set.of("switch");

    /** The rows of a table shown at once; the rest are reached by scrolling the table. */
    public static final int MAX_TABLE_ROWS = 8;

    public static class Bounds {
        public int minX, minY, maxX, maxY;

        public Bounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public void expand(Bounds other) {
            minX = Math.min(minX, other.minX);
            minY = Math.min(minY, other.minY);
            maxX = Math.max(maxX, other.maxX);
            maxY = Math.max(maxY, other.maxY);
        }
    }

    public RouteDiagramLayoutEngine() {
        this(DEFAULT_BOX_WIDTH, DEFAULT_FONT_SIZE);
    }

    /**
     * @param boxWidth logical node width in pixels (before SCALE)
     * @param fontSize font size in logical pixels (before SCALE)
     */
    public RouteDiagramLayoutEngine(int boxWidth, int fontSize) {
        this(boxWidth, fontSize, NodeLabelMode.CODE);
    }

    /**
     * @param boxWidth      logical node width in pixels (before SCALE)
     * @param fontSize      font size in logical pixels (before SCALE)
     * @param nodeLabelMode controls what text is displayed in node labels
     */
    public RouteDiagramLayoutEngine(int boxWidth, int fontSize, NodeLabelMode nodeLabelMode) {
        int clampedWidth = Math.max(boxWidth, MIN_BOX_WIDTH);
        this.nodeWidth = clampedWidth * SCALE;
        this.hGap = nodeWidth / 2;
        this.nodeTextPadding = Math.max(nodeWidth * 16 / (DEFAULT_BOX_WIDTH * SCALE), 8 * SCALE);
        this.baseNodeHeight = Math.max(DEFAULT_NODE_HEIGHT, fontSize * DEFAULT_NODE_HEIGHT / DEFAULT_FONT_SIZE) * SCALE;
        this.nodeLabelMode = nodeLabelMode != null ? nodeLabelMode : NodeLabelMode.CODE;
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            g.setFont(new Font("SansSerif", Font.PLAIN, fontSize * SCALE));
            this.fontMetrics = g.getFontMetrics();
        } finally {
            g.dispose();
        }
    }

    public int getNodeWidth() {
        return nodeWidth;
    }

    /**
     * Draws the {@link #TABLE_EIPS} as one wide node with a row per child instead of a chain of boxes. Off by default:
     * a renderer turns it on when it draws the rows ({@link LayoutNode#tableRow}).
     */
    public void setTableLayout(boolean tableLayout) {
        this.tableLayout = tableLayout;
    }

    /** The width of a table node: two common nodes and the gap between them. */
    public int getTableWidth() {
        return nodeWidth * 2 + hGap;
    }

    public int getBaseNodeHeight() {
        return baseNodeHeight;
    }

    public int getNodeTextPadding() {
        return nodeTextPadding;
    }

    public static class RouteStatInfo extends StatInfo {
        public String coverage;
        public String load01;
        public String load05;
        public String load15;
    }

    public static class StatInfo {
        public long idleSince;
        public long exchangesTotal;
        public String exchangesThroughput;
        public long exchangesFailed;
        public long exchangesInflight;
        public long meanProcessingTime;
        public long maxProcessingTime;
        public long minProcessingTime;
        public long lastProcessingTime;
        public long deltaProcessingTime;
        public long p50ProcessingTime = -1;
        public long p95ProcessingTime = -1;
        public long p99ProcessingTime = -1;
        public long lastCreatedExchangeTimestamp;
        public long lastCompletedExchangeTimestamp;
        public long lastFailedExchangeTimestamp;
    }

    public static class NodeInfo {
        public String type;
        public String id;
        public String code;
        public String uri;
        public String description;
        public int level;
        public int line;
        public boolean remote;
        public StatInfo stat;
    }

    public static class RouteInfo {
        public String routeId;
        public String source;
        public RouteStatInfo stat;
        public final List<NodeInfo> nodes = new ArrayList<>();
    }

    public static class TreeNode {
        public final NodeInfo info;
        public TreeNode parent;
        public final List<TreeNode> children = new ArrayList<>();
        public int subtreeWidth;
        public LayoutNode layoutNode;
        /** Drawn as a table of its children (the table layout is on and it is one of {@link #TABLE_EIPS}). */
        public boolean table;

        public TreeNode(NodeInfo info) {
            this.info = info;
        }
    }

    public static class LayoutNode {
        public String type;
        public String id;
        public int x;
        public int y;
        public int height;
        public List<String> wrappedLines;
        public LayoutNode parentNode;
        public TreeNode treeNode;
        public boolean connectFromMerge;
        public int mergeY;
        public int mergeCx;
        /** The width of the node when it is wider than the others (a table); 0 for the common width. */
        public int width;
        /** A row of a table node: drawn inside its parent's box, at its index among the rows. */
        public boolean tableRow;
        public int tableRowIndex;
    }

    public static class LayoutRoute {
        public String routeId;
        public String source;
        public int labelY;
        public int maxX;
        public int maxY;
        public final List<LayoutNode> nodes = new ArrayList<>();
    }

    public static TreeNode buildTree(List<NodeInfo> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        TreeNode root = new TreeNode(nodes.get(0));
        TreeNode current = root;

        for (int i = 1; i < nodes.size(); i++) {
            NodeInfo ni = nodes.get(i);
            TreeNode tn = new TreeNode(ni);

            if (ni.level > current.info.level) {
                current.children.add(tn);
                tn.parent = current;
            } else if (ni.level == current.info.level) {
                TreeNode parent = current.parent;
                if (parent != null) {
                    parent.children.add(tn);
                    tn.parent = parent;
                } else {
                    root.children.add(tn);
                    tn.parent = root;
                }
            } else {
                TreeNode ancestor = current.parent;
                while (ancestor != null && ancestor.info.level >= ni.level) {
                    ancestor = ancestor.parent;
                }
                if (ancestor != null) {
                    ancestor.children.add(tn);
                    tn.parent = ancestor;
                } else {
                    root.children.add(tn);
                    tn.parent = root;
                }
            }
            current = tn;
        }
        return root;
    }

    public static String cleanLabel(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceFirst("^\\.", "");
    }

    private List<String> wrapLabel(String label) {
        int maxTextWidth = nodeWidth - nodeTextPadding;
        if (fontMetrics.stringWidth(label) <= maxTextWidth) {
            return List.of(label);
        }
        List<String> lines = new ArrayList<>();
        String remaining = label;
        while (!remaining.isEmpty() && lines.size() < MAX_WRAP_LINES) {
            if (fontMetrics.stringWidth(remaining) <= maxTextWidth) {
                lines.add(remaining);
                remaining = "";
                break;
            }
            int breakAt = -1;
            BreakIterator bi = BreakIterator.getCharacterInstance();
            bi.setText(remaining);
            int start = bi.first();
            int end = bi.next();
            while (end != BreakIterator.DONE) {
                String candidate = remaining.substring(0, end);
                if (fontMetrics.stringWidth(candidate) > maxTextWidth) {
                    break;
                }
                char c = remaining.charAt(end - 1);
                if (c == ' ' || c == ':' || c == '/' || c == '.' || c == ',' || c == '&' || c == '?') {
                    breakAt = end;
                }
                start = end;
                end = bi.next();
            }
            if (breakAt <= 0) {
                breakAt = 0;
                bi.setText(remaining);
                start = bi.first();
                end = bi.next();
                while (end != BreakIterator.DONE) {
                    String candidate = remaining.substring(0, end);
                    if (fontMetrics.stringWidth(candidate) > maxTextWidth) {
                        break;
                    }
                    breakAt = end;
                    start = end;
                    end = bi.next();
                }
                if (breakAt <= 0) {
                    breakAt = bi.first();
                    end = bi.next();
                    if (end != BreakIterator.DONE) {
                        breakAt = end;
                    }
                }
            }
            lines.add(remaining.substring(0, breakAt));
            remaining = remaining.substring(breakAt).stripLeading();
        }
        if (!remaining.isEmpty()) {
            int lastIdx = lines.size() - 1;
            lines.set(lastIdx, lines.get(lastIdx) + remaining);
        }
        return lines;
    }

    private int computeNodeHeight(List<String> lines) {
        int lineCount = Math.min(lines.size(), MAX_WRAP_LINES);
        if (lineCount <= 1) {
            return baseNodeHeight;
        }
        int lineSpacing = fontMetrics.getHeight();
        return baseNodeHeight + (lineCount - 1) * lineSpacing;
    }

    public static List<String> resolveLabel(NodeInfo info, NodeLabelMode mode) {
        String code = cleanLabel(info.code);
        boolean hasDesc = info.description != null && !info.description.isBlank();
        switch (mode) {
            case DESCRIPTION:
                return List.of(hasDesc ? info.description : code);
            case BOTH:
                if (hasDesc && !info.description.equals(code)) {
                    return List.of(info.description, code);
                }
                return List.of(code);
            default:
                return List.of(code);
        }
    }

    public LayoutRoute layoutRoute(RouteInfo route, int startY) {
        LayoutRoute lr = new LayoutRoute();
        lr.routeId = route.routeId;
        lr.source = route.source;
        lr.labelY = startY;

        TreeNode tree = buildTree(route.nodes);
        if (tree != null && tableLayout) {
            markTables(tree);
        }
        if (tree == null) {
            lr.maxX = PADDING + nodeWidth;
            lr.maxY = startY + LABEL_OFFSET;
            return lr;
        }

        computeSubtreeWidth(tree);
        assignPositions(tree, PADDING, startY + LABEL_OFFSET, tree.subtreeWidth, lr);

        Bounds extent = new Bounds(
                tree.layoutNode.x, tree.layoutNode.y,
                tree.layoutNode.x + nodeWidth, tree.layoutNode.y + tree.layoutNode.height);
        for (TreeNode child : tree.children) {
            expandBoundsForBox(child, extent, nodeWidth);
        }

        if (extent.minX < PADDING) {
            int shift = PADDING - extent.minX;
            for (LayoutNode ln : lr.nodes) {
                ln.x += shift;
                if (ln.connectFromMerge) {
                    ln.mergeCx += shift;
                }
            }
            extent.maxX += shift;
        }

        lr.maxX = extent.maxX;
        lr.maxY = Math.max(lr.maxY, extent.maxY);

        return lr;
    }

    private static void markTables(TreeNode node) {
        node.table = TABLE_EIPS.contains(node.info.type) && !node.children.isEmpty();
        for (TreeNode child : node.children) {
            markTables(child);
        }
    }

    private int computeSubtreeWidth(TreeNode node) {
        if (node.table) {
            node.subtreeWidth = getTableWidth();
            return node.subtreeWidth;
        }
        if (node.children.isEmpty()) {
            node.subtreeWidth = nodeWidth;
            return node.subtreeWidth;
        }

        if (isBranchingEip(node.info.type)) {
            int totalWidth = 0;
            for (int i = 0; i < node.children.size(); i++) {
                if (i > 0) {
                    totalWidth += hGap;
                }
                totalWidth += computeSubtreeWidth(node.children.get(i));
            }
            node.subtreeWidth = Math.max(nodeWidth, totalWidth);
        } else {
            int maxChildWidth = nodeWidth;
            for (TreeNode child : node.children) {
                maxChildWidth = Math.max(maxChildWidth, computeSubtreeWidth(child));
            }
            node.subtreeWidth = maxChildWidth;
        }
        return node.subtreeWidth;
    }

    private void assignPositions(TreeNode node, int x, int y, int parentWidth, LayoutRoute lr) {
        int availableWidth = Math.max(node.subtreeWidth, parentWidth);
        int ownWidth = node.table ? getTableWidth() : nodeWidth;
        // a table stands centered where a common node would, so the arrows in and out stay straight
        int nodeX = x + (availableWidth - nodeWidth) / 2 - (ownWidth - nodeWidth) / 2;

        LayoutNode ln = new LayoutNode();
        ln.type = node.info.type;
        ln.id = node.info.id;
        ln.x = nodeX;
        ln.y = y;
        ln.wrappedLines = resolveLabel(node.info, nodeLabelMode).stream()
                .flatMap(s -> wrapLabel(s).stream()).toList();
        ln.height = computeNodeHeight(ln.wrappedLines);
        ln.treeNode = node;
        node.layoutNode = ln;
        lr.nodes.add(ln);

        if (node.parent != null && node.parent.layoutNode != null) {
            TreeNode parentNode = node.parent;
            if (!isBranchingEip(parentNode.info.type)) {
                int myIndex = parentNode.children.indexOf(node);
                if (myIndex > 0) {
                    TreeNode prevSibling = parentNode.children.get(myIndex - 1);
                    if (hasScope(prevSibling)) {
                        ln.connectFromMerge = true;
                        Bounds boxBounds = new Bounds(
                                prevSibling.layoutNode.x, prevSibling.layoutNode.y,
                                prevSibling.layoutNode.x + nodeWidth,
                                prevSibling.layoutNode.y + prevSibling.layoutNode.height);
                        for (TreeNode c : prevSibling.children) {
                            expandBoundsForBox(c, boxBounds, nodeWidth);
                        }
                        ln.mergeY = boxBounds.maxY + SCOPE_BOX_PAD;
                        ln.mergeCx = prevSibling.layoutNode.x + nodeWidth / 2;
                        ln.parentNode = prevSibling.layoutNode;
                    } else {
                        ln.parentNode = findLastLayoutNode(prevSibling);
                    }
                } else {
                    ln.parentNode = parentNode.layoutNode;
                }
            } else {
                ln.parentNode = parentNode.layoutNode;
            }
        }

        if (node.table) {
            ln.width = ownWidth;
            int rows = Math.min(node.children.size(), MAX_TABLE_ROWS)
                       + (node.children.size() > MAX_TABLE_ROWS ? 1 : 0);
            ln.height = Math.max(ln.height, baseNodeHeight + (rows - 1) * fontMetrics.getHeight());
            for (int i = 0; i < node.children.size(); i++) {
                TreeNode child = node.children.get(i);
                LayoutNode row = new LayoutNode();
                row.type = child.info.type;
                row.id = child.info.id;
                row.x = nodeX;
                row.y = y;
                row.wrappedLines = resolveLabel(child.info, nodeLabelMode);
                row.treeNode = child;
                row.tableRow = true;
                row.tableRowIndex = i;
                child.layoutNode = row;
                lr.nodes.add(row);
            }
        }

        lr.maxY = Math.max(lr.maxY, y + ln.height);

        if (node.children.isEmpty() || node.table) {
            return;
        }

        int childY = y + ln.height + V_GAP;

        if (isBranchingEip(node.info.type)) {
            int childX = x + (availableWidth - node.subtreeWidth) / 2;
            for (TreeNode child : node.children) {
                int adjustedY = childY;
                if (!child.children.isEmpty() && !BRANCH_CHILD_TYPES.contains(child.info.type)) {
                    adjustedY += SCOPE_BOX_PAD;
                }
                assignPositions(child, childX, adjustedY, child.subtreeWidth, lr);
                childX += child.subtreeWidth + hGap;
            }
        } else {
            int curY = childY;
            for (int i = 0; i < node.children.size(); i++) {
                TreeNode child = node.children.get(i);
                int adjustedY = hasScope(child) ? curY + SCOPE_BOX_PAD : curY;
                assignPositions(child, x, adjustedY, availableWidth, lr);
                if (hasScope(child)) {
                    Bounds cb = new Bounds(
                            child.layoutNode.x, child.layoutNode.y,
                            child.layoutNode.x + nodeWidth, child.layoutNode.y + child.layoutNode.height);
                    for (TreeNode c : child.children) {
                        expandBoundsForBox(c, cb, nodeWidth);
                    }
                    curY = cb.maxY + SCOPE_BOX_PAD + V_GAP;
                } else {
                    curY = findMaxY(child) + V_GAP;
                }
            }
        }
    }

    private static LayoutNode findLastLayoutNode(TreeNode node) {
        if (node.children.isEmpty() || node.table) {
            return node.layoutNode;
        }
        if (isBranchingEip(node.info.type)) {
            return node.layoutNode;
        }
        return findLastLayoutNode(node.children.get(node.children.size() - 1));
    }

    private static int findMaxY(TreeNode node) {
        int maxY = node.layoutNode != null ? node.layoutNode.y + node.layoutNode.height : 0;
        if (node.table) {
            return maxY;
        }
        for (TreeNode child : node.children) {
            maxY = Math.max(maxY, findMaxY(child));
        }
        return maxY;
    }

    public static boolean isBranchingEip(String type) {
        return type != null && BRANCHING_EIPS.contains(type);
    }

    public static boolean hasScope(TreeNode node) {
        return node.parent != null
                && !node.children.isEmpty()
                && !node.table
                && !BRANCH_CHILD_TYPES.contains(node.info.type)
                && !STRUCTURAL_TYPES.contains(node.info.type);
    }

    public static void expandBoundsForBox(TreeNode node, Bounds bounds, int nodeWidth) {
        boolean hasOwnBox = hasScope(node);

        if (hasOwnBox) {
            Bounds inner = new Bounds(
                    node.layoutNode.x, node.layoutNode.y,
                    node.layoutNode.x + nodeWidth, node.layoutNode.y + node.layoutNode.height);
            for (TreeNode child : node.children) {
                expandBoundsForBox(child, inner, nodeWidth);
            }
            bounds.minX = Math.min(bounds.minX, inner.minX - SCOPE_BOX_PAD);
            bounds.minY = Math.min(bounds.minY, inner.minY - SCOPE_BOX_PAD);
            bounds.maxX = Math.max(bounds.maxX, inner.maxX + SCOPE_BOX_PAD);
            bounds.maxY = Math.max(bounds.maxY, inner.maxY + SCOPE_BOX_PAD);
        } else {
            if (node.layoutNode != null) {
                int w = node.layoutNode.width > 0 ? node.layoutNode.width : nodeWidth;
                bounds.minX = Math.min(bounds.minX, node.layoutNode.x);
                bounds.minY = Math.min(bounds.minY, node.layoutNode.y);
                bounds.maxX = Math.max(bounds.maxX, node.layoutNode.x + w);
                bounds.maxY = Math.max(bounds.maxY, node.layoutNode.y + node.layoutNode.height);
            }
            if (node.table) {
                // the rows are inside the table's box
                return;
            }
            for (TreeNode child : node.children) {
                expandBoundsForBox(child, bounds, nodeWidth);
            }
        }
    }
}
