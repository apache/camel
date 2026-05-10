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
            String nodeLabel, boolean metrics) {
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
        return renderer.renderDiagram(layoutRoutes, currentY, colors);
    }

}
