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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Stream;

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
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
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
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd route-diagram",
                 "  camel cmd route-diagram hello.yaml",
                 "  camel cmd route-diagram hello.yaml bye.yaml" })
public class CamelRouteDiagramAction extends ActionWatchCommand {

    private static final long DUMP_COMPLETION_TIMEOUT_MILLIS = 10000;

    @CommandLine.Parameters(
                            description = "Source file name(s) (shell-expanded wildcards), or name/pid of a running "
                                          + "Camel integration",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    @SuppressWarnings("unused") // only declared so picocli offers file-path completion; values go into files instead
    Path[] filePaths;

    List<String> files = new ArrayList<>();

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
                        defaultValue = "unicode")
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

    @CommandLine.Option(names = { "--highlight" },
                        description = "Comma-separated node IDs to highlight in the diagram")
    String highlight;

    @CommandLine.Option(names = { "--highlight-style" },
                        description = "Highlight style: success (green) or fail (red)", defaultValue = "success")
    String highlightStyle;

    @CommandLine.Option(names = { "--ignore-loading-error" }, defaultValue = "false",
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    boolean ignoreLoadingError;

    private long pid;

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
        // only attempt to resolve a running integration when a single token is given (or none, defaulting to "*"):
        // multiple tokens can only be a set of source files
        List<Long> pids = files.size() <= 1 ? findPids(files.isEmpty() ? "*" : files.get(0)) : List.of();

        boolean sourceMode = pids.isEmpty() && !files.isEmpty();

        List<RouteInfo> routes;
        if (pids.isEmpty()) {
            if (files.isEmpty()) {
                printer().println("No running Camel integration found");
                return 1;
            }
            routes = doCallSource(files);
            if (routes == null) {
                return 1;
            }
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + files.get(0) + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        } else {
            // ensure output file is deleted before executing action
            this.pid = pids.get(0);
            Path outputFile = getOutputFile(Long.toString(this.pid));
            PathUtils.deleteFile(outputFile);
            doCallPid(this.pid);
            try {
                JsonObject jo = getJsonObject(outputFile);
                if (jo == null) {
                    printer().println("Response from running Camel with PID " + pid + " not received within 10 seconds");
                    return 1;
                }
                routes = parseRoutes(jo);
            } finally {
                PathUtils.deleteFile(outputFile);
            }
        }

        if (routes.isEmpty()) {
            if (sourceMode) {
                printer().println("No routes found in: " + files
                                  + " (if this is unexpected, the source may have failed to fully load within the "
                                  + "timeout; try --ignore-loading-error or check the logs)");
            } else {
                printer().println("No routes found");
            }
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

        // parse highlight info
        Set<String> highlightedNodeIds = null;
        RouteDiagramHelper.HighlightStyle hlStyle = null;
        RouteDiagramHelper.HighlightInfo highlightInfo = null;
        if (highlight != null && !highlight.isBlank()) {
            highlightedNodeIds = new LinkedHashSet<>(Arrays.asList(highlight.split(",")));
            hlStyle = "fail".equalsIgnoreCase(highlightStyle)
                    ? RouteDiagramHelper.HighlightStyle.FAIL
                    : RouteDiagramHelper.HighlightStyle.SUCCESS;
            highlightInfo = new RouteDiagramHelper.HighlightInfo(highlightedNodeIds, List.of(), hlStyle);
            routes = RouteDiagramHelper.filterAndOrderRoutes(routes, highlightInfo);
            if (routes.isEmpty()) {
                printer().println("No routes contain highlighted nodes");
                return 1;
            }
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
            String ascii = asciiRenderer.renderDiagramAnsi(layoutRoutes, currentY, highlightedNodeIds, hlStyle);

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
                image = renderer.renderDiagram(layoutRoutes, currentY, colors, highlightedNodeIds, hlStyle);
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

    /**
     * Loads the given source files via a transient Camel boot (see
     * {@link #dumpRoutesFromSource(List, String, boolean)}) and returns the merged route structure across all of them,
     * or {@code null} if a file does not exist or loading failed (an error is already printed to the user in that
     * case). Package visible for testing.
     */
    List<RouteInfo> doCallSource(List<String> sourceFiles) throws Exception {
        SourceDump dump = dumpRoutesFromSource(sourceFiles, "route-diagram-source-", ignoreLoadingError);
        if (dump == null) {
            return null;
        }

        try {
            return readRoutesFromFolder(dump.workDir(), dump.bootStart());
        } finally {
            PathUtils.deleteDirectory(dump.workDir());
        }
    }

    /**
     * Reads every per-resource route-structure JSON file dumped into the given folder (one file per source file, named
     * after it; {@code route-topology.json} is skipped) and merges their routes. Waits (up to 10 seconds) for
     * {@code route-topology.json} to appear with a timestamp at or after {@code notBefore}, since the dump happens
     * asynchronously as part of the transient Camel boot: that file is always written last by
     * {@code DefaultDumpRoutesStrategy}, once every route-structure file for this batch has already landed, so its
     * fresh appearance (rather than "any file exists") is what actually signals the whole batch is complete and safe to
     * merge, instead of a partial batch mid-write. Returns {@code null}, without merging whatever partial files exist,
     * if the marker never becomes fresh within {@code maxWaitMillis}. Package visible for testing.
     */
    List<RouteInfo> readRoutesFromFolder(Path folder, Instant notBefore) throws Exception {
        return readRoutesFromFolder(folder, notBefore, DUMP_COMPLETION_TIMEOUT_MILLIS);
    }

    List<RouteInfo> readRoutesFromFolder(Path folder, Instant notBefore, long maxWaitMillis) throws Exception {
        Path topologyMarker = folder.resolve("route-topology.json");
        StopWatch watch = new StopWatch();
        while (watch.taken() < maxWaitMillis && !isFreshFile(topologyMarker, notBefore)) {
            Thread.sleep(100);
        }
        if (!isFreshFile(topologyMarker, notBefore)) {
            printer().printErr("Dump did not complete within " + maxWaitMillis + " ms: " + topologyMarker
                               + " was never (freshly) written; the source may have failed to fully load "
                               + "within the timeout, try --ignore-loading-error or check the logs");
            return null;
        }

        List<Path> jsonFiles = List.of();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> stream = Files.list(folder)) {
                jsonFiles = stream
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(p -> !"route-topology.json".equals(p.getFileName().toString()))
                        .sorted()
                        .toList();
            }
        }

        List<RouteInfo> all = new ArrayList<>();
        for (Path p : jsonFiles) {
            JsonObject jo = (JsonObject) Jsoner.deserialize(Files.readString(p));
            all.addAll(parseRoutes(jo));
        }
        return all;
    }

    private static boolean isFreshFile(Path file, Instant notBefore) throws IOException {
        // small tolerance for filesystem mtime granularity / clock skew between capturing notBefore and the write
        return Files.exists(file) && !Files.getLastModifiedTime(file).toInstant().isBefore(notBefore.minusSeconds(1));
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
     * entry point for programmatic invocation (e.g. from MCP tools) that always targets a source file rather than a
     * named/PID diagram theme option. Note: this still goes through the normal single-token dispatch in
     * {@link #doWatchCall()}, which first checks {@code sourceFile} against running Camel integrations; a source file
     * whose name (without extension) happens to match a running integration's name would render that integration
     * instead. In practice this is unlikely since {@code sourceFile} is expected to include a file extension.
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
        this.files = List.of(sourceFile);
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

    static class FilesConsumer extends ParameterConsumer<CamelRouteDiagramAction> {
        @Override
        protected void doConsumeParameters(Stack<String> args, CamelRouteDiagramAction cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

}
