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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.spi.RouteDiagramDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@JdkService(RouteDiagramDumper.FACTORY)
public class DefaultRouteDiagramDumper extends ServiceSupport implements CamelContextAware, RouteDiagramDumper {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void dumpRoutesToFile(String filter, Theme theme, File file) throws IOException {
        BufferedImage image = dumpRoutesAsImage(filter, theme);
        ImageIO.write(image, "png", file);
    }

    @Override
    public void dumpRoutesToFolder(String filter, Theme theme, File folder) throws IOException {
        // use dev-console to render the route structure in json format which the diagram render expects
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-structure");
        JsonObject root = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter));

        // group by source filename / routeId
        Set<String> groups = new LinkedHashSet<>();
        JsonArray arr = root.getJsonArray("routes");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject jo = arr.getJsonObject(i);
            String id = jo.getString("routeId");
            String source = jo.getString("source");
            if (source != null) {
                // favor source over route id
                id = LoggerHelper.sourceNameOnly(source);
            }
            groups.add(id);
        }

        // render by groups
        for (String group : groups) {
            // do not include scheme in filter
            filter = LoggerHelper.stripScheme(group);
            root = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter));
            var routes = RouteDiagramHelper.parseRoutes(root);

            BufferedImage image = renderImage(routes, theme.name(), 12, 180, "CODE", false);
            folder.mkdirs();
            String name = RouteDiagramHelper.extractSourceName(group);
            File f = new File(folder, name + ".png");
            ImageIO.write(image, "png", f);
        }
    }

    @Override
    public BufferedImage dumpRoutesAsImage(
            String filter, Theme theme, boolean metrics, NodeLabelMode nodeLabel, int nodeWidth, int fontSize) {
        // use dev-console to render the route structure in json format which the diagram render expects
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-structure");
        JsonObject root
                = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter, "metric", String.valueOf(metrics)));
        var routes = RouteDiagramHelper.parseRoutes(root);
        return renderImage(routes, theme.name(), fontSize, nodeWidth, nodeLabel.name(), metrics);
    }

    @Override
    public String dumpRoutesAsAsciiArt(
            String filter, RouteDiagramDumper.NodeLabelMode nodeLabel, int nodeWidth, boolean unicode) {
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-structure");
        JsonObject root = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter));
        var routes = RouteDiagramHelper.parseRoutes(root);
        return renderAscii(routes, nodeWidth, nodeLabel.name(), unicode);
    }

    @Override
    public String imageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        baos.flush();
        try {
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } finally {
            IOHelper.close(baos);
        }
    }

    private static BufferedImage renderImage(
            List<RouteDiagramLayoutEngine.RouteInfo> routes, String theme, int fontSize, int nodeWidth,
            String nodeLabel, boolean metrics,
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle) {
        RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, fontSize * RouteDiagramLayoutEngine.SCALE, metrics);
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                nodeWidth, fontSize, RouteDiagramLayoutEngine.NodeLabelMode.valueOf(nodeLabel.toUpperCase()));

        List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
        int currentY = RouteDiagramLayoutEngine.PADDING;
        for (RouteDiagramLayoutEngine.RouteInfo route : routes) {
            RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(route, currentY);
            layoutRoutes.add(lr);
            currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
        }
        if (theme == null) {
            theme = "transparent";
        }
        theme = theme.toLowerCase();
        RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse(theme);
        return renderer.renderDiagram(layoutRoutes, currentY, colors, highlightedNodeIds, highlightStyle);
    }

    private static BufferedImage renderImage(
            List<RouteDiagramLayoutEngine.RouteInfo> routes, String theme, int fontSize, int nodeWidth,
            String nodeLabel, boolean metrics) {
        return renderImage(routes, theme, fontSize, nodeWidth, nodeLabel, metrics, null, null);
    }

    private static String renderAscii(
            List<RouteDiagramLayoutEngine.RouteInfo> routes, int nodeWidth, String nodeLabel, boolean unicode,
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle) {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                nodeWidth, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                RouteDiagramLayoutEngine.NodeLabelMode.valueOf(nodeLabel.toUpperCase()));

        List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
        int currentY = RouteDiagramLayoutEngine.PADDING;
        for (RouteDiagramLayoutEngine.RouteInfo route : routes) {
            RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(route, currentY);
            layoutRoutes.add(lr);
            currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
        }

        RouteDiagramAsciiRenderer renderer = new RouteDiagramAsciiRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, unicode);
        return renderer.renderDiagram(layoutRoutes, currentY, highlightedNodeIds, highlightStyle);
    }

    private static String renderAscii(
            List<RouteDiagramLayoutEngine.RouteInfo> routes, int nodeWidth, String nodeLabel, boolean unicode) {
        return renderAscii(routes, nodeWidth, nodeLabel, unicode, null, null);
    }

    @Override
    public String dumpTopologyAsAsciiArt(int nodeWidth, boolean unicode) {
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-topology");
        if (dc == null) {
            return "";
        }
        JsonObject root = (JsonObject) dc.call(DevConsole.MediaType.JSON);

        var nodes = TopologyHelper.parseNodes(root);
        var edges = TopologyHelper.parseEdges(root);

        TopologyLayoutEngine engine = new TopologyLayoutEngine(nodeWidth);
        TopologyLayoutEngine.TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, unicode);
        return renderer.renderDiagram(result);
    }

    @Override
    public BufferedImage dumpTopologyAsImage(Theme theme, boolean metrics, int nodeWidth, int fontSize) {
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-topology");
        if (dc == null) {
            return null;
        }
        JsonObject root = (JsonObject) dc.call(DevConsole.MediaType.JSON);

        var nodes = TopologyHelper.parseNodes(root);
        var edges = TopologyHelper.parseEdges(root);

        // Enrich with metrics from route-structure console
        if (metrics) {
            DevConsole structureDc = getCamelContext().getCamelContextExtension()
                    .getContextPlugin(DevConsoleRegistry.class)
                    .resolveById("route-structure");
            if (structureDc != null) {
                JsonObject structureJson = (JsonObject) structureDc.call(
                        DevConsole.MediaType.JSON, Map.of("filter", "*", "metric", "true"));
                TopologyHelper.enrichWithMetrics(nodes, structureJson);
            }
        }

        TopologyLayoutEngine engine = new TopologyLayoutEngine(nodeWidth);
        TopologyLayoutEngine.TopologyLayoutResult result = engine.layout(nodes, edges);

        // Render as image using topology PNG renderer
        return renderTopologyImage(result, theme != null ? theme.name() : "dark", fontSize, nodeWidth, metrics);
    }

    private static BufferedImage renderTopologyImage(
            TopologyLayoutEngine.TopologyLayoutResult result, String theme,
            int fontSize, int nodeWidth, boolean metrics) {
        int scale = RouteDiagramLayoutEngine.SCALE;
        int width = result.totalWidth + RouteDiagramLayoutEngine.PADDING;
        int height = result.totalHeight + RouteDiagramLayoutEngine.PADDING;

        width = Math.max(width, nodeWidth * scale + RouteDiagramLayoutEngine.PADDING * 2);
        height = Math.max(height, 100 * scale);

        RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse(theme.toLowerCase());
        java.awt.Color bgColor = colors.getBg();

        int imageType = bgColor != null ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(
                Math.min(width, 16384), Math.min(height, 16384), imageType);

        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (bgColor != null) {
            g.setColor(bgColor);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
        }

        int fontSizeScaled = fontSize * scale;
        java.awt.Font font = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, fontSizeScaled);
        g.setFont(font);
        java.awt.FontMetrics fm = g.getFontMetrics();

        int arc = 14 * scale;
        float strokeWidth = 1.5f * scale;
        int arrowSize = 6 * scale;
        int nw = nodeWidth * scale;

        // Draw edges
        for (TopologyLayoutEngine.TopologyLayoutEdge edge : result.edges) {
            if (edge.selfLoop) {
                continue;
            }

            boolean dashed = "external".equals(edge.connectionType);
            java.awt.Stroke stroke;
            if (dashed) {
                stroke = new java.awt.BasicStroke(
                        strokeWidth, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER,
                        10.0f, new float[] { 10f, 5f }, 0.0f);
            } else {
                stroke = new java.awt.BasicStroke(strokeWidth);
            }
            g.setStroke(stroke);
            g.setColor(colors.getArrow());

            int fromCx = edge.from.x + nw / 2;
            int fromBy = edge.from.y + edge.from.height;
            int toCx = edge.to.x + nw / 2;
            int toTy = edge.to.y;

            if (fromCx == toCx) {
                g.drawLine(fromCx, fromBy, toCx, toTy - arrowSize / 2);
            } else {
                int midY = fromBy + (toTy - fromBy) / 2;
                g.drawLine(fromCx, fromBy, fromCx, midY);
                g.drawLine(fromCx, midY, toCx, midY);
                g.drawLine(toCx, midY, toCx, toTy - arrowSize / 2);
            }

            // Arrow head
            int[] xPoints = { toCx - arrowSize, toCx + arrowSize, toCx };
            int[] yPoints = { toTy - arrowSize, toTy - arrowSize, toTy };
            g.fillPolygon(xPoints, yPoints, 3);

            // Edge label
            if (edge.endpoint != null) {
                g.setColor(colors.getText());
                java.awt.Font labelFont = font.deriveFont((float) (fontSizeScaled * 0.85));
                g.setFont(labelFont);
                int labelX = Math.min(fromCx, toCx) + Math.abs(fromCx - toCx) / 2;
                int labelY = fromBy + (toTy - fromBy) / 2 - fontSizeScaled / 2;
                String label = edge.endpoint;
                int labelWidth = g.getFontMetrics().stringWidth(label);
                g.drawString(label, labelX - labelWidth / 2, labelY);
                g.setFont(font);
            }
        }

        // Draw nodes
        for (TopologyLayoutEngine.TopologyLayoutNode node : result.nodes) {
            java.awt.Color nodeColor;
            if ("trigger".equals(node.nodeType)) {
                nodeColor = colors.getNodeFrom();
            } else {
                nodeColor = colors.getNodeDefault();
            }

            // Node box
            g.setColor(nodeColor);
            g.setStroke(new java.awt.BasicStroke(strokeWidth));
            g.fillRoundRect(node.x, node.y, nw, node.height, arc, arc);

            g.setColor(nodeColor.brighter());
            g.drawRoundRect(node.x, node.y, nw, node.height, arc, arc);

            // Text
            g.setColor(colors.getText());
            String line1 = node.routeId;
            String line2 = "(" + node.from + ")";
            int lineHeight = fm.getHeight();
            int textY = node.y + (node.height - 2 * lineHeight) / 2 + fm.getAscent();

            int line1Width = fm.stringWidth(line1);
            g.drawString(line1, node.x + (nw - line1Width) / 2, textY);

            java.awt.Font smallFont = font.deriveFont((float) (fontSizeScaled * 0.85));
            g.setFont(smallFont);
            int line2Width = g.getFontMetrics().stringWidth(line2);
            g.drawString(line2, node.x + (nw - line2Width) / 2, textY + lineHeight);
            g.setFont(font);

            // Metrics
            if (metrics && node.exchangesTotal > 0) {
                long ok = node.exchangesTotal - node.exchangesFailed;
                java.awt.Font metricsFont = font.deriveFont((float) (fontSizeScaled * 0.75));
                g.setFont(metricsFont);
                if (ok > 0) {
                    g.setColor(new java.awt.Color(0x43a047));
                    String okStr = String.valueOf(ok);
                    g.drawString(okStr, node.x + nw / 2 - g.getFontMetrics().stringWidth(okStr) - 4, textY + 2 * lineHeight);
                }
                if (node.exchangesFailed > 0) {
                    g.setColor(new java.awt.Color(0xe53935));
                    String failStr = String.valueOf(node.exchangesFailed);
                    g.drawString(failStr, node.x + nw / 2 + 4, textY + 2 * lineHeight);
                }
                g.setFont(font);
            }
        }

        g.dispose();
        return image;
    }

}
