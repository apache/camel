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

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;

/**
 * MCP tool listing the documentation pages of the Camel catalog. The documentation of the catalog artifacts themselves
 * comes from the shared authoring tools ({@code camel_catalog_doc}, {@code camel_catalog_find}).
 */
@McpSecured
@ApplicationScoped
public class CatalogTools {

    @Inject
    CatalogService catalogService;

    /**
     * Tool to list available AsciiDoc documentation pages.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List available AsciiDoc documentation page names from the catalog. "
                        + "Returns page names (without .adoc extension), e.g. kafka-component or split-eip; "
                        + "camel_catalog_doc with includeDoc=true (name kafka, kind component) returns the page. "
                        + "Unlike the structured JSON of camel_catalog_doc, the AsciiDoc pages contain the full "
                        + "human-readable documentation with usage examples, code snippets, and best practices. "
                        + "Only available from Camel 4.22 onwards.")
    public DocListResult camel_catalog_docs(
            @ToolArg(description = "Filter page names by substring (case-insensitive)", required = false) String filter,
            @ToolArg(description = "Maximum number of results to return (default: 50)", required = false) Integer limit,
            @ToolArg(description = ToolArgDocs.CAMEL_VERSION, required = false) String camelVersion) {

        int maxResults = limit != null ? limit : 50;

        try {
            CamelCatalog cat = catalogService.loadCatalog(null, camelVersion, null);

            List<String> names = cat.findDocNames();
            if (names == null) {
                names = List.of();
            }

            if (filter != null && !filter.isBlank()) {
                String lowerFilter = filter.toLowerCase();
                names = names.stream()
                        .filter(n -> n.toLowerCase().contains(lowerFilter))
                        .collect(Collectors.toList());
            }

            int total = names.size();
            if (names.size() > maxResults) {
                names = names.subList(0, maxResults);
            }

            return new DocListResult(total, names.size(), names);
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to list documentation pages: " + e.getMessage(), e);
        }
    }

    public record DocListResult(int total, int returned, List<String> names) {
    }
}
