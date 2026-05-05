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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "diagram", description = "Display route diagram")
public class DiagramDevConsole extends AbstractDevConsole {

    public static final String FILTER = "filter";
    public static final String LIMIT = "limit";
    public static final String THEME = "theme";

    public DiagramDevConsole() {
        super("camel", "diagram", "Route Diagram", "Display route diagram");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        JsonObject structure = buildRouteStructure(options);
        List<RouteInfo> routes = RouteDiagramHelper.parseRoutes(structure);
        if (routes.isEmpty()) {
            return "";
        }

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        List<String> lines = renderer.printTextDiagram(routes);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject structure = buildRouteStructure(options);
        List<RouteInfo> routes = RouteDiagramHelper.parseRoutes(structure);

        JsonObject root = new JsonObject();
        root.put("routes", structure.get("routes"));

        if (!routes.isEmpty()) {
            String theme = (String) options.getOrDefault(THEME, "dark");
            String image = renderAsBase64(routes, theme);
            if (image != null) {
                root.put("imageType", "png");
                root.put("image", image);
            }
        }

        return root;
    }

    private JsonObject buildRouteStructure(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();

        Function<ManagedRouteMBean, Object> task = mrb -> {
            JsonObject jo = new JsonObject();
            list.add(jo);

            jo.put("routeId", mrb.getRouteId());
            jo.put("from", mrb.getEndpointUri());
            if (mrb.getSourceLocation() != null) {
                jo.put("source", mrb.getSourceLocation());
            }

            try {
                ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(getCamelContext());
                List<ModelDumpLine> lines = dumper.dumpStructure(getCamelContext(), mrb.getRouteId(), false);
                List<JsonObject> code = dumpAsJson(lines);
                jo.put("code", code);
            } catch (Exception e) {
                // ignore
            }
            return null;
        };
        doCall(options, task);
        root.put("routes", list);
        return root;
    }

    private void doCall(Map<String, Object> options, Function<ManagedRouteMBean, Object> task) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            List<Route> routes = getCamelContext().getRoutes();
            routes.sort((o1, o2) -> o1.getRouteId().compareToIgnoreCase(o2.getRouteId()));
            routes.stream()
                    .map(route -> mcc.getManagedRoute(route.getRouteId()))
                    .filter(Objects::nonNull)
                    .filter(r -> accept(r, filter))
                    .filter(r -> accept(r, subPath))
                    .sorted(DiagramDevConsole::sort)
                    .limit(max)
                    .forEach(task::apply);
        }
    }

    private static boolean accept(ManagedRouteMBean mrb, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String onlyName = LoggerHelper.sourceNameOnly(mrb.getSourceLocation());
        return PatternHelper.matchPattern(mrb.getRouteId(), filter)
                || PatternHelper.matchPattern(mrb.getEndpointUri(), filter)
                || PatternHelper.matchPattern(mrb.getSourceLocationShort(), filter)
                || PatternHelper.matchPattern(onlyName, filter);
    }

    private static int sort(ManagedRouteMBean o1, ManagedRouteMBean o2) {
        return o1.getRouteId().compareTo(o2.getRouteId());
    }

    private static List<JsonObject> dumpAsJson(List<ModelDumpLine> lines) {
        List<JsonObject> code = new ArrayList<>();
        int counter = 0;
        for (ModelDumpLine line : lines) {
            counter++;
            JsonObject c = new JsonObject();
            Integer idx = extractSourceLineNumber(line.location());
            if (idx == null) {
                idx = counter;
            }
            c.put("line", idx);
            c.put("type", line.type());
            c.put("id", line.id());
            c.put("level", line.level());
            if (line.description() != null) {
                c.put("description", line.description());
            }
            c.put("code", Jsoner.escape(line.code()));
            code.add(c);
        }
        return code;
    }

    private static Integer extractSourceLineNumber(String location) {
        if (location == null) {
            return null;
        }
        int pos = location.lastIndexOf(':');
        if (pos > 0) {
            try {
                return Integer.parseInt(location.substring(pos + 1));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return null;
    }

    private static String renderAsBase64(List<RouteInfo> routes, String theme) {
        try {
            System.setProperty("java.awt.headless", "true");

            DiagramColors colors = DiagramColors.parse(theme);
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
            RouteDiagramRenderer renderer = new RouteDiagramRenderer();

            List<LayoutRoute> layoutRoutes = new ArrayList<>();
            int currentY = RouteDiagramLayoutEngine.PADDING;
            for (RouteInfo route : routes) {
                LayoutRoute lr = engine.layoutRoute(route, currentY);
                layoutRoutes.add(lr);
                currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
            }

            BufferedImage image = renderer.renderDiagram(layoutRoutes, currentY, colors);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
