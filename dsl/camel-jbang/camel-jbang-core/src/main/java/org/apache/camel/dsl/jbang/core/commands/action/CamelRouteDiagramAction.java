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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeLabelMode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramRenderer;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.TerminalGraphics;
import org.jline.terminal.impl.TerminalGraphicsManager;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route-diagram", description = "Display Camel route diagram in the terminal", sortOptions = false,
         showDefaultValues = true)
public class CamelRouteDiagramAction extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Source file name, or name/pid of a running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter route by filename or route id")
    String filter;

    @CommandLine.Option(names = { "--width" },
                        description = "Image width in pixels (0 = auto)", defaultValue = "0")
    int width;

    @CommandLine.Option(names = { "--output" },
                        description = "Save diagram to a file (PNG for image themes, text for ascii theme)")
    String output;

    @CommandLine.Option(names = { "--theme" },
                        description = "Color theme preset (dark, light, transparent, ascii, unicode) or custom colors "
                                      + "(e.g. bg=#1e1e1e:from=#2e7d32:to=#1565c0). Values can be #hex or "
                                      + "ANSI color names (e.g. from=seagreen:to=steelblue). "
                                      + "Use bg= for transparent. Use ascii/unicode for plain text output. "
                                      + "Can also be set via DIAGRAM_COLORS env var.",
                        defaultValue = "transparent")
    String theme;

    @CommandLine.Option(names = { "--font-size" },
                        description = "Font size in logical pixels for node text", defaultValue = "12")
    int fontSize;

    @CommandLine.Option(names = { "--box-width" },
                        description = "Node box width in logical pixels", defaultValue = "180")
    int boxWidth;

    @CommandLine.Option(names = { "--node-label" },
                        description = "What text to display in diagram nodes: code, description, or both (default)",
                        defaultValue = "both")
    String nodeLabel;

    @CommandLine.Option(names = { "--metric" }, defaultValue = "true",
                        description = "Whether to include live metrics (only possible for running Camel application)")
    boolean metric;

    @CommandLine.Option(names = { "--ignore-loading-error" }, defaultValue = "false",
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    boolean ignoreLoadingError;

    private volatile long pid;

    private DiagramColors colors;
    private Terminal terminal;
    private TerminalGraphics terminalGraphics;
    private LineReader lineReader;

    public CamelRouteDiagramAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        System.setProperty("java.awt.headless", "true");

        boolean textMode = isTextTheme();
        if (!textMode) {
            String colorSpec = System.getenv("DIAGRAM_COLORS");
            colors = DiagramColors.parse(colorSpec != null ? colorSpec : theme);
        }

        // if output in terminal then set up terminal
        if (output == null) {
            terminal = TerminalBuilder.builder().system(true).build();
            lineReader = LineReaderBuilder.builder().terminal(terminal).build();
            if (!textMode) {
                terminalGraphics = TerminalGraphicsManager.getBestProtocol(terminal).orElse(null);
                if (terminalGraphics == null) {
                    printer().println("Terminal does not support graphics protocols (Kitty, iTerm2, or Sixel).");
                    printer().println(
                            "Try running in a supported terminal: Kitty, iTerm2, WezTerm, Ghostty, or VS Code.");
                    printer().println("Or use --theme=ascii or --theme=unicode for plain text output.");
                    return 1;
                }
            }
        }

        try {
            return super.doCall();
        } finally {
            if (terminal != null) {
                terminal.close();
            }
        }
    }

    @Override
    protected Integer doWatchCall() throws Exception {
        Path outputFile;
        int exit = 0;
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            // no running so check source files
            outputFile = Path.of(CommandLineHelper.CAMEL_JBANG_WORK_DIR, "/structure-output.json");
            PathUtils.deleteFile(outputFile);
            exit = doCallSource(name);
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        } else {
            // ensure output file is deleted before executing action
            this.pid = pids.get(0);
            outputFile = getOutputFile(Long.toString(this.pid));
            PathUtils.deleteFile(outputFile);
            doCallPid(this.pid);
        }
        if (exit != 0) {
            return exit;
        }

        try {
            JsonObject jo = getJsonObject(outputFile);
            if (jo == null) {
                printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
                return 1;
            }

            List<RouteInfo> routes = parseRoutes(jo);
            if (routes.isEmpty()) {
                printer().println("No routes found");
                return 1;
            }

            if (filter != null) {
                routes = routes.stream()
                        .filter(r -> (r.routeId != null && PatternHelper.matchPattern(r.routeId, filter))
                                || (r.source != null && PatternHelper.matchPattern(r.source, filter)))
                        .toList();
            }

            if (routes.isEmpty()) {
                printer().println("No routes match filter: " + filter);
                return 1;
            }

            NodeLabelMode labelMode = parseNodeLabelMode(nodeLabel);
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(boxWidth, fontSize, labelMode);

            List<LayoutRoute> layoutRoutes = new ArrayList<>();
            int currentY = RouteDiagramLayoutEngine.PADDING;
            for (RouteInfo route : routes) {
                LayoutRoute lr = engine.layoutRoute(route, currentY);
                layoutRoutes.add(lr);
                currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;
            }

            if (isTextTheme()) {
                RouteDiagramAsciiRenderer asciiRenderer
                        = new RouteDiagramAsciiRenderer(engine.getNodeWidth(), isUnicodeTheme(), pid > 0 && metric);
                String ascii = asciiRenderer.renderDiagramAnsi(layoutRoutes, currentY);

                if (output != null) {
                    String fileName = output.endsWith(".png")
                            ? output.substring(0, output.length() - 4) + ".txt" : output;
                    File file = new File(fileName);
                    File parentDir = file.getParentFile();
                    if (parentDir != null) {
                        parentDir.mkdirs();
                    }
                    Files.writeString(file.toPath(), ascii);
                    printer().println("Diagram saved to: " + file.getAbsolutePath());
                } else {
                    if (watch) {
                        clearScreen();
                    }
                    printer().println(ascii);
                }
            } else {
                RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                        engine.getNodeWidth(), fontSize * RouteDiagramLayoutEngine.SCALE, engine.getNodeTextPadding(),
                        pid > 0 && metric);

                BufferedImage image;
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
                    if (watch) {
                        clearScreen();
                    }
                    doDisplayDiagram(image);
                }
            }

            return 0;
        } finally {
            PathUtils.deleteFile(outputFile);
        }
    }

    @Override
    protected boolean watchWait(StopWatch watch) {
        return watch.taken() < 5000;
    }

    @Override
    protected void clearScreen() {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
        }
    }

    @Override
    protected Runnable waitForUserEnter() {
        return () -> {
            if (lineReader != null) {
                try {
                    lineReader.readLine();
                } catch (Exception e) {
                    // ignore
                }
            }
            running.set(false);
        };
    }

    private void doDisplayDiagram(BufferedImage image) throws IOException {
        TerminalGraphics.ImageOptions opts = new TerminalGraphics.ImageOptions()
                .preserveAspectRatio(true);
        if (width > 0) {
            opts.width(width);
        }
        terminalGraphics.displayImage(terminal, image, opts);
        terminal.writer().println();
        terminal.flush();
    }

    private void doCallPid(Long pid) {
        this.pid = pid;

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", false);
        root.put("metric", metric);
        Path file = getActionFile(Long.toString(pid));
        try {
            Files.writeString(file, root.toJson());
        } catch (Exception e) {
            // ignore
        }
    }

    private int doCallSource(String name) throws Exception {
        File f = new File(name);
        if (!f.isFile() || !f.exists()) {
            printer().printErr("File does not exist: " + name);
            return 1;
        }

        final String target = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/structure-output.json";

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", "json");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesOutput", target);
                // turn debug off as this can otherwise include source location in dump
                main.addInitialProperty("camel.debug.enabled", "false");
                main.addInitialProperty(CamelJBangConstants.TRANSFORM, "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                if (ignoreLoadingError) {
                    // turn off bean method validator if ignore loading error
                    main.addInitialProperty("camel.language.bean.validate", "false");
                }
            }
        };
        run.files = List.of(name);
        run.executionLimitOptions.maxSeconds = 1;
        return run.runTransform(ignoreLoadingError);
    }

    List<RouteInfo> parseRoutes(JsonObject jo) {
        return RouteDiagramHelper.parseRoutes(jo);
    }

    private boolean isTextTheme() {
        return "ascii".equalsIgnoreCase(theme) || "unicode".equalsIgnoreCase(theme);
    }

    private boolean isUnicodeTheme() {
        return "unicode".equalsIgnoreCase(theme);
    }

    static NodeLabelMode parseNodeLabelMode(String value) {
        if (value == null || value.isBlank() || "code".equalsIgnoreCase(value)) {
            return NodeLabelMode.CODE;
        } else if ("description".equalsIgnoreCase(value)) {
            return NodeLabelMode.DESCRIPTION;
        } else if ("both".equalsIgnoreCase(value)) {
            return NodeLabelMode.BOTH;
        }
        return NodeLabelMode.CODE;
    }

    /**
     * Used BY MCP tools
     *
     * Renders the routes contained in the given source file as a PNG diagram saved to {@code outputFile}. Convenience
     * entry point for programmatic invocation (e.g. from MCP tools) that always targets a non-running source file and
     * skips the running-PID lookup.
     *
     * @param  sourceFile         path to the route source file (YAML, XML, Java, ...)
     * @param  outputFile         path to the PNG file to write
     * @param  theme              color theme spec (e.g. "dark", "light", "transparent" or custom)
     * @param  filter             optional route filter (route id or filename pattern)
     * @param  width              image width in pixels (0 = auto)
     * @param  ignoreLoadingError whether to ignore route loading and compilation errors
     * @return                    exit code; 0 on success, non-zero otherwise
     * @throws Exception          if the source cannot be read or the diagram cannot be rendered
     */
    public Integer renderSourceToFile(
            String sourceFile, String outputFile, String theme, String filter,
            int width, boolean ignoreLoadingError,
            int fontSize, int boxWidth, String nodeLabel)
            throws Exception {
        this.name = sourceFile;
        this.output = outputFile;
        if (theme != null && !theme.isBlank()) {
            this.theme = theme;
        }
        this.filter = filter;
        this.width = width;
        this.ignoreLoadingError = ignoreLoadingError;
        this.fontSize = fontSize;
        this.boxWidth = boxWidth;
        if (nodeLabel != null && !nodeLabel.isBlank()) {
            this.nodeLabel = nodeLabel;
        }
        this.metric = false;
        return doCall();
    }

}
