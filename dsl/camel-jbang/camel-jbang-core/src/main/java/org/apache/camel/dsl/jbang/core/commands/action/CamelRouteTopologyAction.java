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

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.diagram.TopologyAsciiRenderer;
import org.apache.camel.diagram.TopologyHelper;
import org.apache.camel.diagram.TopologyImageRenderer;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.TerminalGraphics;
import org.jline.terminal.impl.TerminalGraphicsManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route-topology", description = "Display inter-route topology connections", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd route-topology",
                 "  camel cmd route-topology --json",
                 "  camel cmd route-topology --theme=unicode",
                 "  camel cmd route-topology --theme=dark",
                 "  camel cmd route-topology --theme=dark --output=topology.png" })
public class CamelRouteTopologyAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of a running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Option(names = { "--theme" },
                        description = "Color theme preset (dark, light, transparent, ascii, unicode) or custom colors "
                                      + "(e.g. bg=#1e1e1e:from=#2e7d32:to=#1565c0). "
                                      + "Use ascii/unicode for plain text output.")
    String theme;

    @CommandLine.Option(names = { "--output" },
                        description = "Save diagram to a file (PNG for image themes, text for ascii theme)")
    String output;

    @CommandLine.Option(names = { "--box-width" }, defaultValue = "180",
                        description = "Width of diagram node boxes")
    int boxWidth = 180;

    @CommandLine.Option(names = { "--font-size" },
                        description = "Font size in logical pixels for node text", defaultValue = "12")
    int fontSize;

    @CommandLine.Option(names = { "--description" },
                        description = "Prefer route description over route id in node labels")
    boolean description;

    @CommandLine.Option(names = { "--metric" }, defaultValue = "true",
                        description = "Whether to include live metrics")
    boolean metric;

    @CommandLine.Option(names = { "--external" },
                        description = "Include external systems (consumers at top, producers at bottom)")
    boolean external;

    public CamelRouteTopologyAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            printer().println("No running Camel integration found");
            return 1;
        }
        if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        long pid = pids.get(0);
        Path outputFile = prepareAction(Long.toString(pid), "route-topology", root -> {
            root.put("metric", String.valueOf(metric));
            root.put("external", String.valueOf(external));
        });

        JsonObject jo = getJsonObject(outputFile);
        if (jo != null) {
            if (jsonOutput) {
                String dump = Jsoner.prettyPrint(jo.toJson(), 2);
                printer().println(dump);
            } else if (theme != null) {
                if (isTextTheme()) {
                    printTextDiagram(jo);
                } else {
                    printImageDiagram(jo);
                }
            } else {
                printTopology(jo);
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        PathUtils.deleteFile(outputFile);
        return 0;
    }

    private void printTopology(JsonObject jo) {
        JsonArray nodes = (JsonArray) jo.get("nodes");
        JsonArray edges = (JsonArray) jo.get("edges");

        int nodeCount = nodes != null ? nodes.size() : 0;
        int edgeCount = edges != null ? edges.size() : 0;
        printer().printf("Route Topology (%d routes, %d connections)%n%n", nodeCount, edgeCount);

        if (nodes != null) {
            for (Object n : nodes) {
                JsonObject node = (JsonObject) n;
                printer().printf("  %s (%s) type=%s%n",
                        node.getString("routeId"),
                        node.getString("from"),
                        node.getString("nodeType"));

                if (edges != null) {
                    for (Object e : edges) {
                        JsonObject edge = (JsonObject) e;
                        if (node.getString("routeId").equals(edge.getString("fromRouteId"))) {
                            printer().printf("    --> %s via %s [%s]%n",
                                    edge.getString("toRouteId"),
                                    edge.getString("endpoint"),
                                    edge.getString("connectionType"));
                        }
                    }
                }
            }
        }
    }

    private void printTextDiagram(JsonObject jo) throws Exception {
        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(jo);
        TopologyHelper.addExternalEndpoints(nodes, edges, jo);

        TopologyLayoutEngine engine = new TopologyLayoutEngine(boxWidth);
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                engine.getNodeWidth(), isUnicodeTheme(), metric, description);
        String text = renderer.renderDiagram(result);

        if (output != null) {
            String fileName = output.endsWith(".png")
                    ? output.substring(0, output.length() - 4) + ".txt" : output;
            File file = new File(fileName);
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            Files.writeString(file.toPath(), text);
            printer().println("Diagram saved to: " + file.getAbsolutePath());
        } else {
            printer().print(text);
        }
    }

    private void printImageDiagram(JsonObject jo) throws Exception {
        System.setProperty("java.awt.headless", "true");

        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(jo);
        TopologyHelper.addExternalEndpoints(nodes, edges, jo);

        TopologyLayoutEngine engine = new TopologyLayoutEngine(boxWidth);
        TopologyLayoutResult result = engine.layout(nodes, edges);

        String colorSpec = System.getenv("DIAGRAM_COLORS");
        DiagramColors colors = DiagramColors.parse(colorSpec != null ? colorSpec : theme);

        BufferedImage image = TopologyImageRenderer.renderImage(
                result, colors, fontSize, boxWidth, metric, description);

        if (output != null) {
            File file = new File(output);
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            ImageIO.write(image, "PNG", file);
            printer().println("Diagram saved to: " + file.getAbsolutePath());
        } else {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            try {
                TerminalGraphics terminalGraphics = TerminalGraphicsManager.getBestProtocol(terminal).orElse(null);
                if (terminalGraphics == null) {
                    printer().println("Terminal does not support graphics protocols (Kitty, iTerm2, or Sixel).");
                    printer().println(
                            "Try running in a supported terminal: Kitty, iTerm2, WezTerm, Ghostty, or VS Code.");
                    printer().println("Or use --theme=ascii or --theme=unicode for plain text output.");
                    return;
                }
                TerminalGraphics.ImageOptions opts = new TerminalGraphics.ImageOptions()
                        .preserveAspectRatio(true);
                terminalGraphics.displayImage(terminal, image, opts);
                terminal.writer().println();
                terminal.flush();
            } finally {
                terminal.close();
            }
        }
    }

    private boolean isTextTheme() {
        return "ascii".equalsIgnoreCase(theme) || "unicode".equalsIgnoreCase(theme);
    }

    private boolean isUnicodeTheme() {
        return "unicode".equalsIgnoreCase(theme);
    }

}
