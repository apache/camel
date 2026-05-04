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

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteDiagramAction;
import org.apache.camel.dsl.jbang.core.common.Printer;

/**
 * MCP Tool for generating a visual PNG diagram of Camel routes from a source file (non-running integration). Wraps the
 * {@code camel route-diagram} jbang command.
 */
@ApplicationScoped
public class RouteDiagramTools {

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Generate a visual PNG diagram of Camel routes from a route source file (YAML, XML, Java, ...). "
                        + "The source file is parsed without running it. The resulting PNG is written to disk and the "
                        + "absolute path is returned so it can be displayed or shared. Useful to visualize a route "
                        + "structure for review, documentation, or troubleshooting.")
    public RouteDiagramResult camel_render_route_diagram(
            @ToolArg(description = "Absolute or relative path to the Camel route source file (YAML, XML, Java, ...)") String sourceFile,
            @ToolArg(description = "Optional output PNG file path. If not specified, a temporary file is created.") String outputFile,
            @ToolArg(description = "Color theme: 'dark' (default), 'light', 'transparent', or a custom spec like "
                                   + "'bg=#1e1e1e:from=#2e7d32:to=#1565c0'") String theme,
            @ToolArg(description = "Optional filter to limit the diagram to routes whose route id or source filename "
                                   + "matches the given pattern (supports wildcards)") String filter,
            @ToolArg(description = "Image width in pixels; 0 (or unset) = auto") Integer width,
            @ToolArg(description = "Whether to ignore route loading and compilation errors (use with care)") Boolean ignoreLoadingError) {

        if (sourceFile == null || sourceFile.isBlank()) {
            throw new ToolCallException("'sourceFile' parameter is required", null);
        }

        File source = new File(sourceFile);
        if (!source.isFile()) {
            throw new ToolCallException("Source file does not exist: " + sourceFile, null);
        }

        String resolvedOutput;
        try {
            if (outputFile == null || outputFile.isBlank()) {
                Path tmp = Files.createTempFile("camel-route-diagram-", ".png");
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
                    ignoreLoadingError != null && ignoreLoadingError);

            File out = new File(resolvedOutput);
            boolean success = exit == 0 && out.isFile() && out.length() > 0;
            long size = out.exists() ? out.length() : 0L;

            String message = success
                    ? "Diagram saved to: " + resolvedOutput
                    : "Failed to render diagram (exit code " + exit + ")";

            return new RouteDiagramResult(success, resolvedOutput, size, message);
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to render route diagram (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    public record RouteDiagramResult(boolean success, String outputFile, long sizeBytes, String message) {
    }
}
