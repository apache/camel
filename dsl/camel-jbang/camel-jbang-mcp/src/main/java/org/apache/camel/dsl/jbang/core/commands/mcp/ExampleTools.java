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

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.mcp.AuthoringTools.args;
import static org.apache.camel.dsl.jbang.core.commands.mcp.AuthoringTools.call;

/**
 * MCP Tools for browsing Camel CLI examples: thin wrappers over the example tools of the {@link ToolRegistry}
 * (list_examples and get_example_file), so the MCP server, {@code camel tui} and {@code camel ask} answer from one
 * implementation (CAMEL-25535).
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
    public JsonObject camel_catalog_examples(
            @ToolArg(description = "Filter examples by name, description, or tag (case-insensitive substring match)",
                     required = false) String filter,
            @ToolArg(description = ToolRegistry.EXAMPLE_LEVEL_DESC, required = false) String level,
            @ToolArg(description = "Maximum number of examples to return (default: 50)",
                     required = false) Integer limit) {
        return call("list_examples", args("filter", filter, "level", level, "limit", limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get the content of a specific file from a Camel CLI example. "
                        + "Use camel_catalog_examples first to find the example name and its files. "
                        + "Only bundled examples can return file contents directly; "
                        + "for non-bundled examples, a GitHub URL is returned instead. "
                        + "The answer names the example by its full name (group/name).")
    public JsonObject camel_catalog_example_file(
            @ToolArg(description = ToolRegistry.EXAMPLE_NAME_DESC) String example,
            @ToolArg(description = ToolRegistry.EXAMPLE_FILE_DESC) String file) {
        return call("get_example_file", args("example", example, "file", file));
    }
}
