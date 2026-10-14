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
import org.apache.camel.diagram.TopologyErrorFrame.ErrorPath;
import org.apache.camel.diagram.TopologyErrorFrame.LineStyle;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
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
        dumpRoutesToFile(filter, theme, false, file);
    }

    @Override
    public void dumpRoutesToFile(String filter, Theme theme, boolean errors, File file) throws IOException {
        BufferedImage image = dumpRoutesAsImage(filter, theme, false, NodeLabelMode.CODE, 180, 12, errors);
        ImageIO.write(image, "png", file);
    }

    @Override
    public void dumpRoutesToFolder(String filter, Theme theme, File folder) throws IOException {
        dumpRoutesToFolder(filter, theme, false, folder);
    }

    @Override
    public void dumpRoutesToFolder(String filter, Theme theme, boolean errors, File folder) throws IOException {
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
        List<ErrorPath> paths = errors ? errorPaths() : List.of();
        for (String group : groups) {
            // do not include scheme in filter
            filter = LoggerHelper.stripScheme(group);
            root = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter));
            var routes = RouteDiagramHelper.parseRoutes(root);

            BufferedImage image = renderImage(routes, theme.name(), 12, 180, "CODE", false, null, null, errors, paths);
            folder.mkdirs();
            String name = RouteDiagramHelper.extractSourceName(group);
            File f = new File(folder, name + ".png");
            ImageIO.write(image, "png", f);
        }
    }

    @Override
    public BufferedImage dumpRoutesAsImage(
            String filter, Theme theme, boolean metrics, NodeLabelMode nodeLabel, int nodeWidth, int fontSize) {
        return dumpRoutesAsImage(filter, theme, metrics, nodeLabel, nodeWidth, fontSize, false);
    }

    @Override
    public BufferedImage dumpRoutesAsImage(
            String filter, Theme theme, boolean metrics, NodeLabelMode nodeLabel, int nodeWidth, int fontSize,
            boolean errors) {
        // use dev-console to render the route structure in json format which the diagram render expects
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-structure");
        JsonObject root
                = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter, "metric", String.valueOf(metrics)));
        var routes = RouteDiagramHelper.parseRoutes(root);
        return renderImage(routes, theme.name(), fontSize, nodeWidth, nodeLabel.name(), metrics, null, null,
                errors, errors ? errorPaths() : List.of());
    }

    @Override
    public String dumpRoutesAsAsciiArt(
            String filter, RouteDiagramDumper.NodeLabelMode nodeLabel, int nodeWidth, boolean unicode) {
        return dumpRoutesAsAsciiArt(filter, nodeLabel, nodeWidth, unicode, false);
    }

    @Override
    public String dumpRoutesAsAsciiArt(
            String filter, RouteDiagramDumper.NodeLabelMode nodeLabel, int nodeWidth, boolean unicode, boolean errors) {
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-structure");
        JsonObject root = (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("filter", filter));
        var routes = RouteDiagramHelper.parseRoutes(root);
        return renderAscii(routes, nodeWidth, nodeLabel.name(), unicode, null, null,
                errors, errors ? errorPaths() : List.of());
    }

    /** The error paths of the route topology, for the lines in the error frames. */
    private List<ErrorPath> errorPaths() {
        JsonObject root = routeTopology(false);
        if (root == null) {
            return List.of();
        }
        return TopologyErrorFrame.paths(errorEdges(root));
    }

    private static List<TopologyEdgeInfo> errorEdges(JsonObject root) {
        return TopologyHelper.parseEdges(root, true).stream().filter(TopologyEdgeInfo::isErrorPath).toList();
    }

    private JsonObject routeTopology(boolean external) {
        DevConsole dc = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                .resolveById("route-topology");
        if (dc == null) {
            return null;
        }
        return (JsonObject) dc.call(DevConsole.MediaType.JSON, Map.of("external", String.valueOf(external)));
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
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle,
            boolean errors, List<ErrorPath> paths) {
        RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, fontSize * RouteDiagramLayoutEngine.SCALE, metrics);
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                nodeWidth, fontSize, RouteDiagramLayoutEngine.NodeLabelMode.valueOf(nodeLabel.toUpperCase()));

        List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
        int currentY = RouteDiagramLayoutEngine.PADDING;
        for (RouteDiagramLayoutEngine.RouteInfo route : routes) {
            RouteDiagramLayoutEngine.LayoutRoute lr = RouteErrorFrame.layout(engine, route, currentY, errors,
                    highlightedNodeIds, paths, LineStyle.TEXT, RouteDiagramRenderer.rowHeight(fontSize));
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

    private static String renderAscii(
            List<RouteDiagramLayoutEngine.RouteInfo> routes, int nodeWidth, String nodeLabel, boolean unicode,
            Set<String> highlightedNodeIds, RouteDiagramHelper.HighlightStyle highlightStyle,
            boolean errors, List<ErrorPath> paths) {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                nodeWidth, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                RouteDiagramLayoutEngine.NodeLabelMode.valueOf(nodeLabel.toUpperCase()));

        List<RouteDiagramLayoutEngine.LayoutRoute> layoutRoutes = new ArrayList<>();
        int currentY = RouteDiagramLayoutEngine.PADDING;
        for (RouteDiagramLayoutEngine.RouteInfo route : routes) {
            RouteDiagramLayoutEngine.LayoutRoute lr = RouteErrorFrame.layout(engine, route, currentY, errors,
                    highlightedNodeIds, paths, unicode ? LineStyle.UNICODE : LineStyle.ASCII,
                    RouteDiagramAsciiRenderer.ROW_HEIGHT);
            layoutRoutes.add(lr);
            currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
        }

        RouteDiagramAsciiRenderer renderer = new RouteDiagramAsciiRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, unicode);
        return renderer.renderDiagram(layoutRoutes, currentY, highlightedNodeIds, highlightStyle);
    }

    @Override
    public void dumpTopologyToFolder(Theme theme, boolean external, File folder) throws IOException {
        dumpTopologyToFolder(theme, external, false, folder);
    }

    @Override
    public void dumpTopologyToFolder(Theme theme, boolean external, boolean errors, File folder) throws IOException {
        BufferedImage image = dumpTopologyAsImage(theme, false, 180, 12, external, errors);
        if (image != null) {
            folder.mkdirs();
            String name = camelContext.getName();
            if (name == null || name.isBlank()) {
                name = "camel";
            }
            File f = new File(folder, name + "-topology.png");
            ImageIO.write(image, "png", f);
        }
    }

    @Override
    public String dumpTopologyAsAsciiArt(int nodeWidth, boolean unicode, boolean external) {
        return dumpTopologyAsAsciiArt(nodeWidth, unicode, external, false);
    }

    @Override
    public String dumpTopologyAsAsciiArt(int nodeWidth, boolean unicode, boolean external, boolean errors) {
        JsonObject root = routeTopology(external);
        if (root == null) {
            return "";
        }

        var nodes = TopologyHelper.parseNodes(root);
        var edges = TopologyHelper.parseEdges(root);
        if (external) {
            TopologyHelper.addExternalEndpoints(nodes, edges, root);
            TopologyHelper.expandExternalEdges(nodes, edges);
        }

        TopologyLayoutEngine engine = new TopologyLayoutEngine(nodeWidth);
        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, unicode);
        if (errors) {
            TopologyErrorFrame.Placed placed = TopologyErrorFrame.layout(
                    engine, nodes, edges, errorEdges(root), TopologyAsciiRenderer.ROW_HEIGHT);
            return renderer.renderDiagram(placed.layout(), placed.frame());
        }
        return renderer.renderDiagram(engine.layout(nodes, edges));
    }

    @Override
    public BufferedImage dumpTopologyAsImage(Theme theme, boolean metrics, int nodeWidth, int fontSize, boolean external) {
        return dumpTopologyAsImage(theme, metrics, nodeWidth, fontSize, external, false);
    }

    @Override
    public BufferedImage dumpTopologyAsImage(
            Theme theme, boolean metrics, int nodeWidth, int fontSize, boolean external, boolean errors) {
        JsonObject root = routeTopology(external);
        if (root == null) {
            return null;
        }

        var nodes = TopologyHelper.parseNodes(root);
        var edges = TopologyHelper.parseEdges(root);
        if (external) {
            TopologyHelper.addExternalEndpoints(nodes, edges, root);
            TopologyHelper.expandExternalEdges(nodes, edges);
        }

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
        TopologyLayoutEngine.TopologyLayoutResult result;
        TopologyErrorFrame frame = null;
        if (errors) {
            TopologyErrorFrame.Placed placed = TopologyErrorFrame.layout(
                    engine, nodes, edges, errorEdges(root), TopologyImageRenderer.rowHeight(fontSize));
            result = placed.layout();
            frame = placed.frame();
        } else {
            result = engine.layout(nodes, edges);
        }

        // Render as image
        String themeStr = theme != null ? theme.name() : "dark";
        RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse(themeStr.toLowerCase());
        return TopologyImageRenderer.renderImage(result, colors, fontSize, nodeWidth, metrics, false, frame);
    }

}
