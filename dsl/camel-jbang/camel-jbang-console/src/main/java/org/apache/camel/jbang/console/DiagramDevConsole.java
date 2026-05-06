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
package org.apache.camel.jbang.console;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.imageio.ImageIO;

import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "route-diagram", group = "camel-jbang", displayName = "Route Diagram", description = "Visual route diagrams")
public class DiagramDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Theme to use: dark, light, or text
     */
    public static final String THEME = "theme";

    /**
     * The size of the font (default 12)
     */
    public static final String FONT_SIZE = "fontSize";

    /**
     * The node width (default 180 pixels)
     */
    public static final String NODE_WIDTH = "nodeWidth";

    /**
     * Node label mode (code, description, both). Is default code.
     */
    public static final String NODE_LABEL = "nodeLabel";

    public DiagramDevConsole() {
        super("camel-jbang", "route-diagram", "Route Diagram", "Visual route diagrams");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final StringJoiner sj = new StringJoiner("\n");

        String filter = (String) options.getOrDefault(FILTER, "*");
        String theme = (String) options.get(THEME);
        int fontSize
                = Integer.parseInt(options.getOrDefault(FONT_SIZE, "" + RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE).toString());
        int nodeWidth = Integer
                .parseInt(options.getOrDefault(NODE_WIDTH, "" + RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH).toString());
        String nodeLabel = (String) options.getOrDefault(NODE_LABEL, "CODE");

        org.apache.camel.console.DevConsole dc
                = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                        .resolveById("route-structure");
        JsonObject root = (JsonObject) dc.call(MediaType.JSON, Map.of("filter", filter));
        // parseRoutes expect a JsonArray and not ArrayList
        JsonArray arr = new JsonArray(root.getCollection("routes"));
        root.put("routes", arr);
        var routes = RouteDiagramHelper.parseRoutes(root);
        if ("text".equals(theme)) {
            RouteDiagramRenderer renderer = new RouteDiagramRenderer();
            var lines = renderer.printTextDiagram(routes);
            lines.forEach(sj::add);
            sj.add("");
        } else {
            try {
                BufferedImage image = renderImage(routes, theme, fontSize, nodeWidth, nodeLabel);
                String base64 = imageToBase64(image, "png");
                // For HTML embedding:
                String html = String.format(
                        "<html>\n<body>\n<img src=\"data:image/png;base64,%s\" alt=\"Route Diagram\">\n</body>\n</html>",
                        base64);
                sj.add(html);
            } catch (Exception e) {
                // ignore
            }
        }
        return sj.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String filter = (String) options.getOrDefault(FILTER, "*");
        String theme = (String) options.get(THEME);
        int fontSize
                = Integer.parseInt(options.getOrDefault(FONT_SIZE, "" + RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE).toString());
        int nodeWidth = Integer
                .parseInt(options.getOrDefault(NODE_WIDTH, "" + RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH).toString());
        String nodeLabel = (String) options.getOrDefault(NODE_LABEL, "CODE");

        org.apache.camel.console.DevConsole dc
                = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class)
                        .resolveById("route-structure");
        JsonObject root = (JsonObject) dc.call(MediaType.JSON, Map.of("filter", filter));
        // parseRoutes expect a JsonArray and not ArrayList
        JsonArray arr = new JsonArray(root.getCollection("routes"));
        root.put("routes", arr);
        var routes = RouteDiagramHelper.parseRoutes(root);

        root = new JsonObject();
        try {
            BufferedImage image = renderImage(routes, theme, fontSize, nodeWidth, nodeLabel);
            String base64 = imageToBase64(image, "png");
            root.put("image", base64);
        } catch (Exception e) {
            // ignore
        }

        return root;
    }

    private static BufferedImage renderImage(
            List<RouteDiagramLayoutEngine.RouteInfo> routes, String theme, int fontSize, int nodeWidth, String nodeLabel) {
        RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                nodeWidth * RouteDiagramLayoutEngine.SCALE, fontSize * RouteDiagramLayoutEngine.SCALE);
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
        RouteDiagramRenderer.DiagramColors colors = RouteDiagramRenderer.DiagramColors.parse(theme);
        return renderer.renderDiagram(layoutRoutes, currentY, colors);
    }

    private static String imageToBase64(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        baos.flush();
        try {
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } finally {
            IOHelper.close(baos);
        }
    }
}
