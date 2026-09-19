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
import java.util.Map;

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
          description = "List the Camel CLI examples, grouped as the ladder of the examples: "
                        + "quick-start, run, transform, route, fail-well, connect, connect-service, contracts, ai, "
                        + "cloud and showcase, in that reading order. Returns the groups (level, title, introduction) "
                        + "and the examples in reading order with name, title, description, level, order, tags, "
                        + "what they teach (components, EIPs, languages, data formats), the infra services they need "
                        + "(start them with camel infra run), whether they are bundled and their files. "
                        + "Call it without arguments for the whole ladder, with level for one group, "
                        + "or with filter to search by name, description or tag. "
                        + "Use camel_catalog_example_file to read a file of an example.")
    public ExampleListResult camel_catalog_examples(
            @ToolArg(description = "Filter examples by name, description, or tag (case-insensitive substring match)",
                     required = false) String filter,
            @ToolArg(description = "Only the examples of one group (level): quick-start, run, transform, route, "
                                   + "fail-well, connect, connect-service, contracts, ai, cloud or showcase",
                     required = false) String level,
            @ToolArg(description = "Maximum number of examples to return (default: 50)",
                     required = false) Integer limit) {

        int maxResults = limit != null && limit > 0 ? limit : 50;

        try {
            List<JsonObject> catalog = ExampleHelper.loadCatalog();
            List<JsonObject> filtered = ExampleHelper.filterExamples(catalog, filter);

            List<GroupInfo> groups = new ArrayList<>();
            List<ExampleInfo> result = new ArrayList<>();
            int total = 0;
            for (Map.Entry<String, List<JsonObject>> group : ExampleHelper.groupByLevel(filtered).entrySet()) {
                if (level != null && !level.isBlank() && !group.getKey().equalsIgnoreCase(level)) {
                    continue;
                }
                total += group.getValue().size();
                groups.add(new GroupInfo(
                        group.getKey(),
                        ExampleHelper.getGroupTitle(group.getKey()),
                        ExampleHelper.getGroupIntro(group.getKey()),
                        group.getValue().size()));
                for (JsonObject entry : group.getValue()) {
                    if (result.size() < maxResults) {
                        result.add(toExampleInfo(entry));
                    }
                }
            }

            return new ExampleListResult(result.size(), total, groups, result);
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
                ExampleHelper.getOrder(entry) == Integer.MAX_VALUE ? null : ExampleHelper.getOrder(entry),
                tags != null ? new ArrayList<>(tags) : List.of(),
                ExampleHelper.getTeaches(entry),
                ExampleHelper.getInfraServices(entry),
                ExampleHelper.isBundled(entry),
                ExampleHelper.requiresDocker(entry),
                ExampleHelper.getFiles(entry));
    }

    // Result records

    public record ExampleListResult(int count, int total, List<GroupInfo> groups, List<ExampleInfo> examples) {
    }

    public record GroupInfo(String level, String title, String intro, int count) {
    }

    public record ExampleInfo(String name, String title, String description, String level, Integer order,
            List<String> tags, Map<String, List<String>> teaches, List<String> infraServices,
            boolean bundled, boolean requiresDocker, List<String> files) {
    }

    public record ExampleFileResult(String example, String file, String content, String githubUrl) {
    }
}
