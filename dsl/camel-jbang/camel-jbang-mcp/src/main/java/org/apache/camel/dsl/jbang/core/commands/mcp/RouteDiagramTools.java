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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteDiagramAction;
import org.apache.camel.dsl.jbang.core.common.Printer;

/**
 * MCP Tool for generating a visual diagram of Camel routes from a source file (non-running integration). Wraps the
 * {@code camel route-diagram} jbang command. Supports both PNG image output and plain-text ASCII/Unicode output.
 */
@ApplicationScoped
public class RouteDiagramTools {

    private static final Set<String> TEXT_THEMES = Set.of("ascii", "unicode");

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Generate a diagram of Camel routes from a route source file (YAML, XML, Java, ...). "
                        + "The source file is parsed without running it. "
                        + "For image themes (dark, light, transparent) the PNG is written to disk and the absolute path "
                        + "is returned. For text themes (ascii, unicode) the diagram is returned directly as text so "
                        + "it can be read and understood by the caller. "
                        + "Useful to visualize a route structure for review, documentation, or troubleshooting.")
    public RouteDiagramResult camel_render_route_diagram(
            @ToolArg(description = "Absolute or relative path to the Camel route source file (YAML, XML, Java, ...)") String sourceFile,
            @ToolArg(description = "Optional output file path. For image themes a PNG is written; for text themes a .txt "
                                   + "file is written. If not specified, a temporary file is created.") String outputFile,
            @ToolArg(description = "Color theme: 'dark' (default), 'light', 'transparent', 'ascii' (plain ASCII art), "
                                   + "'unicode' (box-drawing characters), or a custom spec like "
                                   + "'bg=#1e1e1e:from=#2e7d32:to=#1565c0'. "
                                   + "Use 'ascii' or 'unicode' to get a text diagram that can be read directly.") String theme,
            @ToolArg(description = "Optional filter to limit the diagram to routes whose route id or source filename "
                                   + "matches the given pattern (supports wildcards)") String filter,
            @ToolArg(description = "Image width in pixels; 0 (or unset) = auto (only used for image themes)") Integer width,
            @ToolArg(description = "Font size in logical pixels for node text (default 12)") Integer fontSize,
            @ToolArg(description = "Node box width in logical pixels (default 180)") Integer boxWidth,
            @ToolArg(description = "What text to display in diagram nodes: 'code' (default), 'description' (prefer "
                                   + "description over code if available), or 'both' (show description and code)") String nodeLabel,
            @ToolArg(description = "Whether to ignore route loading and compilation errors (use with care)") Boolean ignoreLoadingError) {

        if (sourceFile == null || sourceFile.isBlank()) {
            throw new ToolCallException("'sourceFile' parameter is required", null);
        }

        File source = new File(sourceFile);
        if (!source.isFile()) {
            throw new ToolCallException("Source file does not exist: " + sourceFile, null);
        }

        boolean textMode = theme != null && TEXT_THEMES.contains(theme.toLowerCase());

        String resolvedOutput;
        try {
            if (outputFile == null || outputFile.isBlank()) {
                String suffix = textMode ? ".txt" : ".png";
                Path tmp = Files.createTempFile("camel-route-diagram-", suffix);
                resolvedOutput = tmp.toAbsolutePath().toString();
            } else {
                resolvedOutput = new File(outputFile).getAbsolutePath();
            }
        } catch (Exception e) {
            throw new ToolCallException(
                    "Failed to resolve output file (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }

        // headless rendering — required when running inside the MCP server
        System.setProperty("java.awt.headless", "true");

        CamelJBangMain main = new CamelJBangMain()
                .withPrinter(new Printer.QuietPrinter(new Printer.SystemOutPrinter()));
        CamelRouteDiagramAction action = new CamelRouteDiagramAction(main);

        try {
            int exit = action.renderSourceToFile(
                    sourceFile, resolvedOutput, theme, filter,
                    width != null ? width : 0,
                    ignoreLoadingError != null && ignoreLoadingError,
                    fontSize != null ? fontSize : 12,
                    boxWidth != null ? boxWidth : 180,
                    nodeLabel);

            File out = new File(resolvedOutput);
            boolean success = exit == 0 && out.isFile() && out.length() > 0;
            long size = out.exists() ? out.length() : 0L;

            if (textMode) {
                String asciiContent = success ? Files.readString(out.toPath()) : null;
                String message = success
                        ? "ASCII diagram generated (" + size + " bytes)"
                        : "Failed to render diagram (exit code " + exit + ")";
                return new RouteDiagramResult(success, resolvedOutput, size, message, asciiContent);
            }

            String message = success
                    ? "Diagram saved to: " + resolvedOutput
                    : "Failed to render diagram (exit code " + exit + ")";
            return new RouteDiagramResult(success, resolvedOutput, size, message, null);
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to render route diagram (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * @param asciiDiagram plain-text diagram content for ascii/unicode themes; null for image themes
     */
    public record RouteDiagramResult(boolean success, String outputFile, long sizeBytes, String message,
            String asciiDiagram) {
    }
}
