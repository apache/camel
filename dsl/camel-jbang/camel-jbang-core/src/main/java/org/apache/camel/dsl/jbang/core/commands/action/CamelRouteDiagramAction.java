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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.TerminalGraphics;
import org.jline.terminal.impl.TerminalGraphicsManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route-diagram", description = "Display Camel route diagram in the terminal", sortOptions = false,
         showDefaultValues = true)
public class CamelRouteDiagramAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter route by filename or route id")
    String filter;

    @CommandLine.Option(names = { "--width" },
                        description = "Image width in pixels (0 = auto)", defaultValue = "0")
    int width;

    @CommandLine.Option(names = { "--output" },
                        description = "Save diagram to a PNG file instead of displaying in terminal")
    String output;

    @CommandLine.Option(names = { "--theme", "--colors" },
                        description = "Color theme preset (dark, light, transparent) or custom colors "
                                      + "(e.g. bg=#1e1e1e:from=#2e7d32:to=#1565c0). Values can be #hex or "
                                      + "ANSI color names (e.g. from=seagreen:to=steelblue). "
                                      + "Use bg= for transparent. Can also be set via DIAGRAM_COLORS env var.",
                        defaultValue = "dark")
    String theme;

    public CamelRouteDiagramAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        System.setProperty("java.awt.headless", "true");

        String colorSpec = System.getenv("DIAGRAM_COLORS");
        DiagramColors colors = DiagramColors.parse(colorSpec != null ? colorSpec : theme);

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 1;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        long pid = pids.get(0);

        Path outputFile = prepareAction(Long.toString(pid), "route-structure", root -> {
            root.put("filter", "*");
            root.put("brief", false);
        });

        try {
            JsonObject jo = getJsonObject(outputFile);
            if (jo == null) {
                printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
                return 1;
            }

            List<RouteInfo> routes = parseRoutes(jo);
            if (routes.isEmpty()) {
                printer().println("No routes found");
                return 0;
            }

            if (filter != null) {
                routes = routes.stream()
                        .filter(r -> (r.routeId != null && PatternHelper.matchPattern(r.routeId, filter))
                                || (r.source != null && PatternHelper.matchPattern(r.source, filter)))
                        .toList();
            }

            if (routes.isEmpty()) {
                printer().println("No routes match filter: " + filter);
                return 0;
            }

            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
            RouteDiagramRenderer renderer = new RouteDiagramRenderer();

            List<LayoutRoute> layoutRoutes = new ArrayList<>();
            int currentY = RouteDiagramLayoutEngine.PADDING;
            for (RouteInfo route : routes) {
                LayoutRoute lr = engine.layoutRoute(route, currentY);
                layoutRoutes.add(lr);
                currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
            }

            java.awt.image.BufferedImage image;
            try {
                image = renderer.renderDiagram(layoutRoutes, currentY, colors);
            } catch (IllegalStateException e) {
                printer().println(e.getMessage());
                return 1;
            }

            if (output != null) {
                File file = new File(output);
                File parentDir = file.getParentFile();
                if (parentDir != null) {
                    parentDir.mkdirs();
                }
                ImageIO.write(image, "PNG", file);
                printer().println("Diagram saved to: " + file.getAbsolutePath());
            } else {
                try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
                    try {
                        Optional<TerminalGraphics> protocol = TerminalGraphicsManager.getBestProtocol(terminal);
                        if (protocol.isPresent()) {
                            TerminalGraphics.ImageOptions opts = new TerminalGraphics.ImageOptions()
                                    .preserveAspectRatio(true);
                            if (width > 0) {
                                opts.width(width);
                            }
                            protocol.get().displayImage(terminal, image, opts);
                            terminal.writer().println();
                            terminal.flush();
                        } else {
                            printer().println(
                                    "Terminal does not support graphics protocols (Kitty, iTerm2, or Sixel).");
                            printer().println(
                                    "Try running in a supported terminal: Kitty, iTerm2, WezTerm, Ghostty, or VS Code.");
                            renderer.printTextDiagram(routes, printer());
                        }
                    } catch (IOException | UnsupportedOperationException e) {
                        printer().println("Failed to display diagram in terminal: " + e.getMessage());
                        printer().println("Falling back to text diagram.");
                        renderer.printTextDiagram(routes, printer());
                    }
                }
            }

            return 0;
        } finally {
            PathUtils.deleteFile(outputFile);
        }
    }

    List<RouteInfo> parseRoutes(JsonObject jo) {
        List<RouteInfo> routes = new ArrayList<>();
        JsonArray arr = (JsonArray) jo.get("routes");
        if (arr == null) {
            return routes;
        }

        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            RouteInfo route = new RouteInfo();
            route.routeId = o.getString("routeId");
            route.source = CamelRouteStructureAction.extractSourceName(o.getString("source"));

            List<JsonObject> lines = o.getCollection("code");
            if (lines != null) {
                for (JsonObject line : lines) {
                    NodeInfo node = new NodeInfo();
                    node.type = line.getString("type");
                    node.code = Jsoner.unescape(line.getString("code"));
                    Integer level = line.getInteger("level");
                    node.level = level != null ? level : 0;
                    route.nodes.add(node);
                }
            }
            routes.add(route);
        }
        return routes;
    }
}
