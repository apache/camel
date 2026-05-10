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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.Bounds;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.jline.utils.Colors;

import static org.apache.camel.diagram.RouteDiagramLayoutEngine.BRANCH_CHILD_TYPES;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.PADDING;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.SCALE;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.SCOPE_BOX_PAD;
import static org.apache.camel.diagram.RouteDiagramLayoutEngine.V_GAP;

/**
 * Renders route diagrams as PNG images or text-based tree representations.
 */
public class RouteDiagramRenderer {

    private static final int ARC = 14 * SCALE;
    private static final int ARROW_SIZE = 6 * SCALE;
    private static final float STROKE_WIDTH = 1.5f * SCALE;
    private static final float BORDER_STROKE_WIDTH = 1.0f * SCALE;
    private static final int LABEL_TEXT_BASELINE = 14 * SCALE;
    public static final int MAX_IMAGE_DIMENSION = 16384;

    private static final float[] DASH_PATTERN = { 10f, 5f }; // 10 pixels on, 5 pixels off
    private static final Stroke DASHED_STROKE = new BasicStroke(
            STROKE_WIDTH,            // line width
            BasicStroke.CAP_BUTT,    // end cap style
            BasicStroke.JOIN_BEVEL,  // join style
            0f,                      // miter limit (not used for bevel)
            DASH_PATTERN,            // dash pattern array
            0f                       // dash phase (offset)
    );

    private final int nodeWidth;
    private final int fontSizeNode;
    private final int fontSizeLabel;
    private final int nodeTextPadding;
    private final boolean metrics;

    private static final String DARK_COLORS
            = "bg=#0d1117:text=#f0f6fc:arrow=#656c76:label=#d1d7e0:from=#238636:to=#1f6feb:eip=#8957e5"
              + ":choice=#d29922:default=#3d444d:transform=#1b7c83:processor=#bf4b8a";
    private static final String LIGHT_COLORS
            = "bg=#f6f8fa:text=#f0f6fc:arrow=#59636e:label=#1f2328:from=#238636:to=#1f6feb:eip=#8957e5"
              + ":choice=#d29922:default=#3d444d:transform=#1b7c83:processor=#bf4b8a";
    private static final String TRANSPARENT_COLORS
            = "bg=:text=#f0f6fc:arrow=#656c76:label=#d1d7e0:from=#238636:to=#1f6feb:eip=#8957e5"
              + ":choice=#d29922:default=#3d444d:transform=#1b7c83:processor=#bf4b8a";

    private static final Map<String, String> COLOR_PRESETS = Map.of(
            "dark", DARK_COLORS,
            "light", LIGHT_COLORS,
            "transparent", TRANSPARENT_COLORS);

    public RouteDiagramRenderer() {
        this(RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * SCALE,
             RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE * SCALE,
             new RouteDiagramLayoutEngine().getNodeTextPadding(),
             false);
    }

    public RouteDiagramRenderer(int nodeWidth, int fontSizeScaled, boolean metrics) {
        this(nodeWidth, fontSizeScaled, new RouteDiagramLayoutEngine(
                nodeWidth / SCALE, fontSizeScaled / SCALE).getNodeTextPadding(),
             metrics);
    }

    public RouteDiagramRenderer(int nodeWidth, int fontSizeScaled, int nodeTextPadding, boolean metrics) {
        this.nodeWidth = nodeWidth;
        this.fontSizeNode = fontSizeScaled;
        this.fontSizeLabel = fontSizeScaled + 1 * SCALE;
        this.nodeTextPadding = nodeTextPadding;
        this.metrics = metrics;
    }

    public static class DiagramColors {
        private Color bg;
        private Color text;
        private Color arrow;
        private Color counter;
        private Color counterFail;
        private Color routeLabel;
        private Color nodeFrom;
        private Color nodeTo;
        private Color nodeEip;
        private Color nodeChoice;
        private Color nodeDefault;
        private Color nodeTransform;
        private Color nodeProcessor;

        public static DiagramColors parse(String spec) {
            String resolved = COLOR_PRESETS.getOrDefault(spec, spec);
            Map<String, String> map = new HashMap<>();
            for (String entry : DARK_COLORS.split(":")) {
                int eq = entry.indexOf('=');
                if (eq > 0) {
                    map.put(entry.substring(0, eq), entry.substring(eq + 1));
                }
            }
            for (String entry : resolved.split(":")) {
                int eq = entry.indexOf('=');
                if (eq > 0) {
                    map.put(entry.substring(0, eq), entry.substring(eq + 1));
                }
            }
            DiagramColors c = new DiagramColors();
            c.bg = parseColor(map.get("bg"));
            c.text = parseColor(map.getOrDefault("text", "#ffffff"));
            c.arrow = parseColor(map.getOrDefault("arrow", "#b4b4b4"));
            c.counter = parseColor(map.getOrDefault("counter", "#2e7d32"));
            c.counterFail = parseColor(map.getOrDefault("counter", "#ff0000"));
            c.routeLabel = parseColor(map.getOrDefault("label", "#c8c8c8"));
            c.nodeFrom = parseColor(map.getOrDefault("from", "#2e7d32"));
            c.nodeTo = parseColor(map.getOrDefault("to", "#1565c0"));
            c.nodeEip = parseColor(map.getOrDefault("eip", "#9c27b0"));
            c.nodeChoice = parseColor(map.getOrDefault("choice", "#e65100"));
            c.nodeDefault = parseColor(map.getOrDefault("default", "#455a64"));
            c.nodeTransform = parseColor(map.getOrDefault("transform", "#00838f"));
            c.nodeProcessor = parseColor(map.getOrDefault("processor", "#d84315"));
            return c;
        }

        private static Color parseColor(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            if (value.startsWith("#")) {
                try {
                    return new Color(Integer.parseInt(value.substring(1), 16));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            Integer idx = Colors.rgbColor(value);
            if (idx != null) {
                return new Color(Colors.rgbColor(idx.intValue()));
            }
            return null;
        }

        public Color getBg() {
            return bg;
        }

        public Color getText() {
            return text;
        }

        public Color getCounter() {
            return counter;
        }

        public Color getCounterFail() {
            return counterFail;
        }

        public Color getArrow() {
            return arrow;
        }

        public Color getRouteLabel() {
            return routeLabel;
        }

        public Color getNodeFrom() {
            return nodeFrom;
        }

        public Color getNodeTo() {
            return nodeTo;
        }

        public Color getNodeEip() {
            return nodeEip;
        }

        public Color getNodeChoice() {
            return nodeChoice;
        }

        public Color getNodeDefault() {
            return nodeDefault;
        }

        public Color getNodeTransform() {
            return nodeTransform;
        }

        public Color getNodeProcessor() {
            return nodeProcessor;
        }
    }

    public BufferedImage renderDiagram(List<LayoutRoute> layoutRoutes, int totalHeight, DiagramColors colors) {
        int imgWidth = layoutRoutes.stream().mapToInt(lr -> lr.maxX).max().orElse(400) + PADDING;
        int imgHeight = totalHeight + PADDING;

        if (imgWidth > MAX_IMAGE_DIMENSION || imgHeight > MAX_IMAGE_DIMENSION) {
            throw new IllegalStateException(
                    "Diagram too large (" + imgWidth / SCALE + "x" + imgHeight / SCALE
                                            + " logical pixels). Use --filter to narrow the routes displayed.");
        }

        int imageType = colors.getBg() == null ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(imgWidth, imgHeight, imageType);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            if (colors.getBg() != null) {
                g.setColor(colors.getBg());
                g.fillRect(0, 0, imgWidth, imgHeight);
            }

            for (LayoutRoute lr : layoutRoutes) {
                drawRoute(g, lr, colors);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private void drawRoute(Graphics2D g, LayoutRoute lr, DiagramColors colors) {
        g.setColor(colors.getRouteLabel());
        g.setFont(new Font("SansSerif", Font.BOLD, fontSizeLabel));
        String label = lr.routeId;
        if (lr.source != null && !lr.source.isEmpty()) {
            label += " (" + lr.source + ")";
        }
        g.drawString(label, PADDING, lr.labelY + LABEL_TEXT_BASELINE);

        g.setStroke(new BasicStroke(STROKE_WIDTH));

        for (LayoutNode ln : lr.nodes) {
            if (ln.treeNode != null && RouteDiagramLayoutEngine.hasScope(ln.treeNode)) {
                drawScopeBox(g, ln, colors);
            }
        }

        for (LayoutNode ln : lr.nodes) {
            if (ln.parentNode != null) {
                if (ln.connectFromMerge) {
                    drawArrowFromMerge(g, ln, colors);
                } else {
                    drawArrow(g, ln.parentNode, ln, colors);
                }
            }
        }

        for (LayoutNode ln : lr.nodes) {
            drawNode(g, ln, colors);
        }
    }

    private void drawScopeBox(Graphics2D g, LayoutNode scopeNode, DiagramColors colors) {
        TreeNode tn = scopeNode.treeNode;
        Bounds bounds = new Bounds(
                scopeNode.x, scopeNode.y,
                scopeNode.x + nodeWidth, scopeNode.y + scopeNode.height);
        for (TreeNode child : tn.children) {
            RouteDiagramLayoutEngine.expandBoundsForBox(child, bounds, nodeWidth);
        }

        int boxX = bounds.minX - SCOPE_BOX_PAD;
        int boxY = bounds.minY - SCOPE_BOX_PAD;
        int boxW = bounds.maxX - bounds.minX + 2 * SCOPE_BOX_PAD;
        int boxH = bounds.maxY - bounds.minY + 2 * SCOPE_BOX_PAD;

        Color c = colors.getArrow();
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 140));
        g.setStroke(DASHED_STROKE);
        g.drawRoundRect(boxX, boxY, boxW, boxH, ARC, ARC);
    }

    private int getTopY(LayoutNode node) {
        return node.treeNode != null && RouteDiagramLayoutEngine.hasScope(node.treeNode)
                ? node.y - SCOPE_BOX_PAD : node.y;
    }

    private void drawArrowFromMerge(Graphics2D g, LayoutNode to, DiagramColors colors) {
        var stat = to.treeNode.info.stat;
        long total = stat != null ? stat.exchangesTotal : 0;
        long failed = stat != null ? stat.exchangesFailed : 0;
        long ok = total - failed;

        g.setColor(colors.getArrow());
        if (!metrics || total > 0) {
            g.setStroke(new BasicStroke(STROKE_WIDTH));
        } else {
            g.setStroke(DASHED_STROKE);
        }

        int toCx = to.x + nodeWidth / 2;
        int toTy = getTopY(to);
        int mergeCx = to.mergeCx;
        int mergeY = to.mergeY;

        if (mergeCx == toCx) {
            g.drawLine(mergeCx, mergeY, toCx, toTy - ARROW_SIZE / 2);
        } else {
            int midY = mergeY + (toTy - mergeY) / 2;
            g.drawLine(mergeCx, mergeY, mergeCx, midY);
            g.drawLine(mergeCx, midY, toCx, midY);
            g.drawLine(toCx, midY, toCx, toTy - ARROW_SIZE / 2);
        }
        drawArrowHead(g, toCx, toTy);

        if (ok > 0) {
            g.setColor(colors.getCounter());
            g.drawString("" + ok, toCx + 2 + fontSizeNode, toTy - 2 - fontSizeNode);
        }
        if (failed > 0) {
            g.setColor(colors.getCounterFail());
            int width = g.getFontMetrics().stringWidth("" + failed);
            g.drawString("" + failed, toCx - 2 - fontSizeNode - width, toTy - 2 - fontSizeNode);
        }
    }

    private void drawNode(Graphics2D g, LayoutNode node, DiagramColors colors) {
        Color color = getNodeColor(node.type, colors);

        g.setColor(color);
        g.fillRoundRect(node.x, node.y, nodeWidth, node.height, ARC, ARC);

        g.setColor(color.brighter());
        g.setStroke(new BasicStroke(BORDER_STROKE_WIDTH));
        g.drawRoundRect(node.x, node.y, nodeWidth, node.height, ARC, ARC);

        g.setColor(colors.getText());
        g.setFont(new Font("SansSerif", Font.PLAIN, fontSizeNode));
        FontMetrics fm = g.getFontMetrics();

        List<String> lines = node.wrappedLines;

        int lineHeight = fm.getHeight();
        int totalTextHeight = lines.size() * lineHeight;
        int startY = node.y + (node.height - totalTextHeight) / 2 + fm.getAscent();

        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            if (fm.stringWidth(text) > nodeWidth - nodeTextPadding) {
                while (text.length() > 1 && fm.stringWidth(text + "…") > nodeWidth - nodeTextPadding) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "…";
            }
            int textX = node.x + (nodeWidth - fm.stringWidth(text)) / 2;
            g.drawString(text, textX, startY + i * lineHeight);
        }
    }

    private void drawArrow(Graphics2D g, LayoutNode from, LayoutNode to, DiagramColors colors) {
        var stat = metrics ? to.treeNode.info.stat : null;
        if (metrics && BRANCH_CHILD_TYPES.contains(to.type) && !to.treeNode.children.isEmpty()) {
            // grab stat from first child (for example choice to have counters for when/otherwise)
            stat = to.treeNode.children.get(0).info.stat;
        }
        long total = stat != null ? stat.exchangesTotal : 0;
        long failed = stat != null ? stat.exchangesFailed : 0;
        long ok = total - failed;

        g.setColor(colors.getArrow());
        if (!metrics || total > 0) {
            g.setStroke(new BasicStroke(STROKE_WIDTH));
        } else {
            g.setStroke(DASHED_STROKE);
        }

        int fromCx = from.x + nodeWidth / 2;
        int fromBy = from.y + from.height;
        int toCx = to.x + nodeWidth / 2;
        int toTy = getTopY(to);

        if (fromCx == toCx) {
            g.drawLine(fromCx, fromBy, toCx, toTy - ARROW_SIZE / 2);
        } else {
            int midY = fromBy + V_GAP / 2;
            g.drawLine(fromCx, fromBy, fromCx, midY);
            g.drawLine(fromCx, midY, toCx, midY);
            g.drawLine(toCx, midY, toCx, toTy - ARROW_SIZE / 2);
        }
        drawArrowHead(g, toCx, toTy);

        if (ok > 0) {
            g.setColor(colors.getCounter());
            g.drawString("" + ok, toCx + 2 + fontSizeNode, toTy - 2 - fontSizeNode);
        }
        if (failed > 0) {
            g.setColor(colors.getCounterFail());
            int width = g.getFontMetrics().stringWidth("" + failed);
            g.drawString("" + failed, toCx - 2 - fontSizeNode - width, toTy - 2 - fontSizeNode);
        }
    }

    private void drawArrowHead(Graphics2D g, int x, int y) {
        int[] xPoints = { x - ARROW_SIZE, x, x + ARROW_SIZE };
        int[] yPoints = { y - ARROW_SIZE, y, y - ARROW_SIZE };
        g.fillPolygon(xPoints, yPoints, 3);
    }

    private Color getNodeColor(String type, DiagramColors colors) {
        if (type == null) {
            return colors.getNodeDefault();
        }
        return switch (type) {
            case "from" -> colors.getNodeFrom();
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> colors.getNodeTo();
            case "choice", "when", "otherwise" -> colors.getNodeChoice();
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                colors.getNodeTransform();
            case "bean", "process", "log", "script", "delay" -> colors.getNodeProcessor();
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                colors.getNodeEip();
            default -> colors.getNodeDefault();
        };
    }

    public List<String> printTextDiagram(List<RouteInfo> routes) {
        return printTextDiagram(routes, RouteDiagramLayoutEngine.NodeLabelMode.CODE);
    }

    public List<String> printTextDiagram(List<RouteInfo> routes, RouteDiagramLayoutEngine.NodeLabelMode mode) {
        List<String> lines = new ArrayList<>();
        for (RouteInfo route : routes) {
            lines.add("");
            String header = "Route: " + route.routeId;
            if (route.source != null && !route.source.isEmpty()) {
                header += " (" + route.source + ")";
            }
            lines.add(header);

            TreeNode tree = RouteDiagramLayoutEngine.buildTree(route.nodes);
            if (tree != null) {
                printTreeNode(tree, "", true, true, lines, mode);
            }
            lines.add("");
        }
        return lines;
    }

    private void printTreeNode(
            TreeNode node, String prefix, boolean isLast, boolean isRoot, List<String> lines,
            RouteDiagramLayoutEngine.NodeLabelMode mode) {
        String connector;
        if (isRoot) {
            connector = "  ";
        } else if (isLast) {
            connector = "└── ";
        } else {
            connector = "├── ";
        }

        String typeTag = node.info.type != null ? "[" + node.info.type + "] " : "";
        String label = String.join(" | ", RouteDiagramLayoutEngine.resolveLabel(node.info, mode));

        lines.add(prefix + connector + typeTag + label);

        String childPrefix;
        if (isRoot) {
            childPrefix = prefix + "  ";
        } else if (isLast) {
            childPrefix = prefix + "    ";
        } else {
            childPrefix = prefix + "│   ";
        }

        for (int i = 0; i < node.children.size(); i++) {
            printTreeNode(node.children.get(i), childPrefix, i == node.children.size() - 1, false, lines, mode);
        }
    }
}
