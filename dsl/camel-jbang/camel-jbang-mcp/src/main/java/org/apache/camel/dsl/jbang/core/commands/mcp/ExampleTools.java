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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tools for browsing Camel CLI examples.
 */
@McpSecured
@ApplicationScoped
public class ExampleTools {

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List available Camel CLI examples. "
                        + "Returns name, title, description, difficulty level, and tags. "
                        + "Use filter to search by name, description, or tag. "
                        + "Use level to filter by difficulty (beginner, intermediate, advanced).")
    public ExampleListResult camel_catalog_examples(
            @ToolArg(description = "Filter examples by name, description, or tag (case-insensitive substring match)") String filter,
            @ToolArg(description = "Filter by difficulty level: beginner, intermediate, or advanced") String level,
            @ToolArg(description = "Maximum number of results to return (default: 50)") Integer limit) {

        int maxResults = limit != null ? limit : 50;

        try {
            List<JsonObject> catalog = ExampleHelper.loadCatalog();
            List<JsonObject> filtered = ExampleHelper.filterExamples(catalog, filter);

            List<ExampleInfo> result = new ArrayList<>();
            for (JsonObject entry : filtered) {
                if (level != null && !level.isBlank()) {
                    String entryLevel = entry.getString("level");
                    if (entryLevel == null || !entryLevel.equalsIgnoreCase(level)) {
                        continue;
                    }
                }

                result.add(toExampleInfo(entry));

                if (result.size() >= maxResults) {
                    break;
                }
            }

            return new ExampleListResult(result.size(), result);
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to list examples (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get the content of a specific file from a Camel CLI example. "
                        + "Use camel_catalog_examples first to find the example name and its files. "
                        + "Only bundled examples can return file contents directly; "
                        + "for non-bundled examples, a GitHub URL is returned instead.")
    public ExampleFileResult camel_catalog_example_file(
            @ToolArg(description = "Example name (e.g., timer-log, rest-api, circuit-breaker)") String example,
            @ToolArg(description = "File name within the example (e.g., route.camel.yaml, application.properties)") String file) {

        if (example == null || example.isBlank()) {
            throw new ToolCallException("Example name is required", null);
        }
        if (file == null || file.isBlank()) {
            throw new ToolCallException("File name is required", null);
        }

        try {
            List<JsonObject> catalog = ExampleHelper.loadCatalog();
            JsonObject entry = ExampleHelper.findExample(catalog, example);
            if (entry == null) {
                throw new ToolCallException("Example not found: " + example, null);
            }

            List<String> files = ExampleHelper.getFiles(entry);
            if (!files.contains(file)) {
                throw new ToolCallException(
                        "File '" + file + "' not found in example '" + example
                                            + "'. Available files: " + files,
                        null);
            }

            if (ExampleHelper.isBundled(entry)) {
                String resourcePath = "examples/" + example + "/" + file;
                try (InputStream is = ExampleHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        String content = IOHelper.loadText(is);
                        return new ExampleFileResult(example, file, content, null);
                    }
                }
                throw new ToolCallException("Could not read bundled file: " + resourcePath, null);
            } else {
                String githubUrl = ExampleHelper.getGithubUrl(entry) + "/" + file;
                return new ExampleFileResult(example, file, null, githubUrl);
            }
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to read example file (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    @SuppressWarnings("unchecked")
    private ExampleInfo toExampleInfo(JsonObject entry) {
        Collection<String> tags = (Collection<String>) entry.get("tags");
        return new ExampleInfo(
                entry.getString("name"),
                entry.getString("title"),
                entry.getString("description"),
                entry.getString("level"),
                tags != null ? new ArrayList<>(tags) : List.of(),
                ExampleHelper.isBundled(entry),
                ExampleHelper.requiresDocker(entry),
                ExampleHelper.getFiles(entry));
    }

    // Result records

    public record ExampleListResult(int count, List<ExampleInfo> examples) {
    }

    public record ExampleInfo(String name, String title, String description, String level,
            List<String> tags, boolean bundled, boolean requiresDocker, List<String> files) {
    }

    public record ExampleFileResult(String example, String file, String content, String githubUrl) {
    }
}
