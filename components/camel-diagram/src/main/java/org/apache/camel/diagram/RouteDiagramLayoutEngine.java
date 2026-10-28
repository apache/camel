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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** The space between the border of a card and its icon, and its text. */
    public static final int CARD_PAD = 10 * SCALE;
    /** The space above and below the text of a card. */
    public static final int CARD_PAD_Y = 9 * SCALE;
    /** The diameter of the circle of the icon of a card. */
    public static final int CARD_ICON_SIZE = 24 * SCALE;
    /** Where the text of a card starts, from its left border: after the icon. */
    public static final int CARD_TEXT_INSET = CARD_PAD + CARD_ICON_SIZE + CARD_PAD;
    /**
     * How much longer an arrow is, at least, when it carries the body types of the step it goes into: room for their
     * label above the counters of the metrics. More with a larger font ({@link #getBodyTypesGap()}).
     */
    public static final int BODY_TYPES_GAP = 30 * SCALE;
    /** The space between a route and the callouts of its problems. */
    public static final int CALLOUT_GAP = 16 * SCALE;
    /** The space around the text of a callout. */
    public static final int CALLOUT_PAD = 8 * SCALE;
    /** The space before the text of an entry of the list of problems: room for its number. */
    public static final int PROBLEM_NUMBER_WIDTH = 26 * SCALE;
    // the end of a problem is often what to do about it, so a callout has room for most of it
    private static final int MAX_CALLOUT_LINES = 6;
    private static final Pattern CARD_EXPRESSION = Pattern.compile("^([A-Za-z][\\w-]*)\\{(.*)\\}$", Pattern.DOTALL);

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
    private final FontMetrics titleMetrics;
    private final NodeLabelMode nodeLabelMode;
    private boolean tableLayout;
    private boolean cardStyle;
    private boolean bodyTypes;
    private boolean problems;
    private final int bodyTypesGap;
    // the space between the steps of the route being laid out
    private int stepGap = V_GAP;

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
            g.setFont(cardTitleFont(fontSize * SCALE));
            this.titleMetrics = g.getFontMetrics();
            this.bodyTypesGap = bodyTypesGap(
                    fontSize * SCALE, g.getFontMetrics(bodyTypesFont(fontSize * SCALE)), titleMetrics);
        } finally {
            g.dispose();
        }
    }

    /** The font of the label of the body types on an arrow: a size smaller than the text of the nodes. */
    public static Font bodyTypesFont(int fontSizeScaled) {
        return new Font("SansSerif", Font.PLAIN, fontSizeScaled - SCALE);
    }

    /**
     * Where the label of the body types starts on an arrow, below its start: below the bend of an arrow to a branch.
     */
    public static int bodyTypesLabelOffset() {
        return V_GAP / 2 + 4 * SCALE;
    }

    /**
     * The height of the label of the body types.
     *
     * @param labelMetrics the metrics of the {@link #bodyTypesFont}
     */
    public static int bodyTypesLabelHeight(FontMetrics labelMetrics) {
        return labelMetrics.getHeight() + 4 * SCALE;
    }

    /**
     * The room for the label of the body types and, below it, the counters of the metrics: they are at the end of the
     * arrow, in the {@link #cardTitleFont} (the renderer draws the counters in the font of the route label), and both
     * grow with the font size.
     */
    private static int bodyTypesGap(int fontSizeScaled, FontMetrics labelMetrics, FontMetrics counterMetrics) {
        int labelBottom = bodyTypesLabelOffset() + bodyTypesLabelHeight(labelMetrics);
        int counters = 2 + fontSizeScaled + counterMetrics.getAscent();
        return Math.max(BODY_TYPES_GAP, labelBottom + 2 * SCALE + counters - V_GAP);
    }

    /** The font of the title of a card: bold, a size larger than the text of the nodes. */
    public static Font cardTitleFont(int fontSizeScaled) {
        return new Font("SansSerif", Font.BOLD, fontSizeScaled + SCALE);
    }

    public int getNodeWidth() {
        return nodeWidth;
    }

    /**
     * Lays the nodes out as cards: a title (the kind of step, such as setBody) and the detail below it (such as
     * {@code groovy: body.items}), after an icon. The first of the {@link LayoutNode#wrappedLines} is the title. On for
     * an image ({@link RouteDiagramRenderer} draws cards), off (the default) for the ASCII and Unicode diagrams.
     */
    public void setCardStyle(boolean cardStyle) {
        this.cardStyle = cardStyle;
    }

    public boolean isCardStyle() {
        return cardStyle;
    }

    /**
     * Makes room on the arrows for the body types of the steps they go into ({@link NodeInfo#bodyTypes}), in a route
     * that has any: its arrows are {@link #BODY_TYPES_GAP} longer. Off by default.
     */
    public void setBodyTypes(boolean bodyTypes) {
        this.bodyTypes = bodyTypes;
    }

    /** How much longer an arrow is when it carries the body types of the step it goes into. */
    public int getBodyTypesGap() {
        return bodyTypesGap;
    }

    public boolean isBodyTypes() {
        return bodyTypes;
    }

    /**
     * Places the problems of the steps ({@link NodeInfo#problems}): in a callout beside the route, at the height of the
     * step, or in a route with branches (where a callout would cross the steps of another branch) in a numbered list
     * below the steps. Off by default.
     */
    public void setProblems(boolean problems) {
        this.problems = problems;
    }

    public boolean isProblems() {
        return problems;
    }

    /** The width of a callout of the problems of a step. */
    public static int calloutWidth(int nodeWidth) {
        return nodeWidth * 3 / 2;
    }

    /**
     * The widest a label of body types can be: wider than a step, as the steps of two branches are one and a half steps
     * apart, and still apart from the label of a step beside it.
     *
     * @param nodeWidth the width of a step (scaled)
     */
    public static int bodyTypesLabelWidth(int nodeWidth) {
        return nodeWidth * 3 / 2 - 8 * SCALE;
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
        /**
         * The body types the step has been reached with, across the messages, when the message history recorded them:
         * the class names, at most a few.
         */
        public List<String> bodyTypes = List.of();
        /**
         * The problems of the step, as the checks of the source report them, such as that a groovy step reads fields of
         * the file as it was read: drawn in the diagram when it shows problems ({@link #setProblems}).
         */
        public List<String> problems = List.of();
        /**
         * The kinds of those bodies: null, payload, data and collection. More than one is a step that needs attention,
         * as a step that writes the body fails for the data, and one that reads fields fails for the payload.
         */
        public List<String> bodyKinds = List.of();
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
        /** The error handling of the route, in a frame below its steps, when shown ({@link RouteErrorFrame#add}). */
        public RouteErrorFrame errorFrame;
        /** Whether the arrows have room for the body types of the steps ({@link #setBodyTypes}). */
        public boolean bodyTypes;
        /** The problems of the steps, in callouts beside the route or in a list below it ({@link #setProblems}). */
        public final List<Callout> callouts = new ArrayList<>();
        /**
         * Whether the problems are a numbered list below the steps (a route with branches), not callouts beside them.
         */
        public boolean problemList;
    }

    /** The problems of a step: a callout beside the route, or an entry of the list below it. */
    public static class Callout {
        /** The step the problems are of. */
        public LayoutNode node;
        /** The number of the entry in the list of problems, from 1; 0 for a callout. */
        public int number;
        public int x;
        public int y;
        public int width;
        public int height;
        /** The text, wrapped to the width (the last line may need to be cut with an ellipsis). */
        public List<String> lines;
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
        return wrapLabel(label, nodeWidth - nodeTextPadding);
    }

    private List<String> wrapLabel(String label, int maxTextWidth) {
        return wrapLabel(label, maxTextWidth, MAX_WRAP_LINES);
    }

    private List<String> wrapLabel(String label, int maxTextWidth, int maxLines) {
        if (fontMetrics.stringWidth(label) <= maxTextWidth) {
            return List.of(label);
        }
        List<String> lines = new ArrayList<>();
        String remaining = label;
        while (!remaining.isEmpty() && lines.size() < maxLines) {
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

    /** The lines of a card: its title, then its detail wrapped to the width beside the icon, at most three lines. */
    private List<String> cardLines(NodeInfo info) {
        List<String> label = cardLabel(info, nodeLabelMode);
        List<String> lines = new ArrayList<>();
        lines.add(label.get(0));
        int maxTextWidth = nodeWidth - CARD_TEXT_INSET - CARD_PAD;
        List<String> detail = new ArrayList<>();
        for (String text : label.subList(1, label.size())) {
            detail.addAll(wrapLabel(text, maxTextWidth));
        }
        if (detail.size() > MAX_WRAP_LINES) {
            // the renderer cuts the last line with an ellipsis
            String rest = String.join(" ", detail.subList(MAX_WRAP_LINES - 1, detail.size()));
            detail = new ArrayList<>(detail.subList(0, MAX_WRAP_LINES - 1));
            detail.add(rest);
        }
        lines.addAll(detail);
        return lines;
    }

    private int computeCardHeight(List<String> lines) {
        int textHeight = titleMetrics.getHeight() + (lines.size() - 1) * fontMetrics.getHeight();
        return Math.max(baseNodeHeight, Math.max(textHeight, CARD_ICON_SIZE) + 2 * CARD_PAD_Y);
    }

    /**
     * The text of a node as a card: the kind of step first (such as {@code setBody}), then its detail (such as
     * {@code groovy: body.items} for the code {@code setBody[groovy{body.items}]}), or its description, by the label
     * mode. A step without detail (such as {@code choice}) has only its kind.
     */
    public static List<String> cardLabel(NodeInfo info, NodeLabelMode mode) {
        String code = cleanLabel(info.code);
        String kind = code;
        String detail = "";
        int bracket = code.indexOf('[');
        if (bracket > 0 && code.endsWith("]")) {
            kind = code.substring(0, bracket);
            detail = code.substring(bracket + 1, code.length() - 1);
            Matcher m = CARD_EXPRESSION.matcher(detail);
            if (m.matches()) {
                // an expression in its language: groovy{body.items} as groovy: body.items
                detail = m.group(1) + ": " + m.group(2);
            }
        }
        if (kind.isEmpty() && info.type != null) {
            kind = info.type;
        }
        boolean hasDesc = info.description != null && !info.description.isBlank() && !info.description.equals(code);
        List<String> below = switch (mode) {
            case DESCRIPTION -> List.of(hasDesc ? info.description : detail);
            case BOTH -> hasDesc ? List.of(info.description, detail) : List.of(detail);
            default -> List.of(detail);
        };
        List<String> lines = new ArrayList<>();
        lines.add(kind);
        for (String line : below) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
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

        stepGap = V_GAP;
        if (bodyTypes && route.nodes.stream().anyMatch(n -> !n.bodyTypes.isEmpty())) {
            stepGap = V_GAP + bodyTypesGap;
            lr.bodyTypes = true;
        }

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

        // a label of body types can be wider than a step: room for it on both sides
        int overhang = lr.bodyTypes ? (bodyTypesLabelWidth(nodeWidth) - nodeWidth) / 2 : 0;
        if (extent.minX < PADDING + overhang) {
            int shift = PADDING + overhang - extent.minX;
            for (LayoutNode ln : lr.nodes) {
                ln.x += shift;
                if (ln.connectFromMerge) {
                    ln.mergeCx += shift;
                }
            }
            extent.maxX += shift;
        }

        lr.maxX = extent.maxX + overhang;
        lr.maxY = Math.max(lr.maxY, extent.maxY);

        if (problems) {
            layoutProblems(lr);
        }
        return lr;
    }

    private void layoutProblems(LayoutRoute lr) {
        // in the order of the steps in the route (the order of the source)
        List<LayoutNode> withProblems = lr.nodes.stream()
                .filter(ln -> !ln.tableRow && ln.treeNode != null && !ln.treeNode.info.problems.isEmpty())
                .toList();
        if (withProblems.isEmpty()) {
            return;
        }
        int lineHeight = fontMetrics.getHeight();
        lr.problemList = lr.nodes.stream().anyMatch(ln -> isBranchingEip(ln.type));
        if (!lr.problemList) {
            // a column of callouts beside the route, each at the height of its step, below the one above it
            int x = lr.maxX + CALLOUT_GAP;
            int width = calloutWidth(nodeWidth);
            int bottom = Integer.MIN_VALUE;
            // without branches the order of the steps is from the top down
            for (LayoutNode ln : withProblems) {
                Callout c = new Callout();
                c.node = ln;
                c.lines = problemLines(ln.treeNode.info.problems, width - 2 * CALLOUT_PAD);
                c.x = x;
                c.width = width;
                c.height = c.lines.size() * lineHeight + 2 * CALLOUT_PAD;
                c.y = Math.max(ln.y + ln.height / 2 - c.height / 2, bottom + CALLOUT_PAD);
                bottom = c.y + c.height;
                lr.callouts.add(c);
                lr.maxY = Math.max(lr.maxY, bottom);
            }
            lr.maxX = x + width;
        } else {
            // a numbered list below the steps, as wide as the route
            int width = Math.max(lr.maxX - PADDING, nodeWidth * 2);
            int y = lr.maxY + V_GAP / 2;
            int number = 0;
            for (LayoutNode ln : withProblems) {
                Callout c = new Callout();
                c.node = ln;
                c.number = ++number;
                c.lines = problemLines(ln.treeNode.info.problems, width - PROBLEM_NUMBER_WIDTH);
                c.x = PADDING;
                c.y = y;
                c.width = width;
                c.height = c.lines.size() * lineHeight;
                y += c.height + CALLOUT_PAD;
                lr.callouts.add(c);
            }
            lr.maxY = y;
            lr.maxX = Math.max(lr.maxX, PADDING + width);
        }
    }

    /**
     * The problems wrapped to the width, each from a line of its own, at most six lines: a problem that does not fit
     * has the rest of its text on its last line, which the renderer cuts with an ellipsis, and the problems after it
     * are left out, which an ellipsis says.
     */
    List<String> problemLines(List<String> problems, int width) {
        List<String> lines = new ArrayList<>();
        for (String problem : problems) {
            int room = MAX_CALLOUT_LINES - lines.size();
            if (room == 0) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + " …");
                break;
            }
            lines.addAll(wrapLabel(problem, width, room));
        }
        return lines;
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
        if (cardStyle) {
            ln.wrappedLines = cardLines(node.info);
            ln.height = computeCardHeight(ln.wrappedLines);
        } else {
            ln.wrappedLines = resolveLabel(node.info, nodeLabelMode).stream()
                    .flatMap(s -> wrapLabel(s).stream()).toList();
            ln.height = computeNodeHeight(ln.wrappedLines);
        }
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

        int childY = y + ln.height + stepGap;

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
                    curY = cb.maxY + SCOPE_BOX_PAD + stepGap;
                } else {
                    curY = findMaxY(child) + stepGap;
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
