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
package org.apache.camel.component.langchain4j.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;

/**
 * Native tool that allows LLMs to search for available tools without consuming the context window. This tool is
 * automatically exposed when there are searchable (non-exposed) tools registered.
 */
public class ToolSearchTool {

    public static final String TOOL_NAME = "toolSearchTool";
    public static final String TOOL_DESCRIPTION
            = "Search for available tools by tags or keywords. Use this to discover tools that can help accomplish specific tasks.";

    private final CamelToolExecutorCache toolCache;
    private final String[] producerTags;

    public ToolSearchTool(CamelToolExecutorCache toolCache, String[] producerTags) {
        this.toolCache = toolCache;
        this.producerTags = producerTags;
    }

    /**
     * Searches for tools matching the given tags. The search looks for tools in the searchable registry that match any
     * of the provided tags.
     *
     * @param  tags comma-separated list of tags to search for
     * @return      list of matching tool specifications (without duplicates)
     */
    public List<CamelToolSpecification> searchTools(String tags) {
        // Use LinkedHashSet to maintain insertion order while preventing duplicates
        Set<CamelToolSpecification> matchingTools = new LinkedHashSet<>();

        if (tags == null || tags.trim().isEmpty()) {
            // Return all searchable tools if no tags specified
            return getAllSearchableTools();
        }

        String[] searchTags = TagsHelper.splitTags(tags);
        Map<String, Set<CamelToolSpecification>> searchableTools = toolCache.getSearchableTools();

        // Search for tools matching any of the search tags
        // Note: We don't filter by producer tags here - the search should find all tools
        // with matching tags in the searchable registry, regardless of producer tags
        for (String searchTag : searchTags) {
            Set<CamelToolSpecification> toolsForTag = searchableTools.get(searchTag);
            if (toolsForTag != null) {
                matchingTools.addAll(toolsForTag);
            }
        }

        return new ArrayList<>(matchingTools);
    }

    /**
     * Gets all searchable tools for the producer's tags
     *
     * @return list of all searchable tool specifications
     */
    public List<CamelToolSpecification> getAllSearchableTools() {
        List<CamelToolSpecification> allTools = new ArrayList<>();
        Map<String, Set<CamelToolSpecification>> searchableTools = toolCache.getSearchableTools();

        for (String tag : producerTags) {
            Set<CamelToolSpecification> toolsForTag = searchableTools.get(tag);
            if (toolsForTag != null) {
                allTools.addAll(toolsForTag);
            }
        }

        return allTools;
    }

    /**
     * Formats tool specifications as a readable string for the LLM
     *
     * @param  tools list of tool specifications
     * @return       formatted string describing the tools
     */
    public static String formatToolsForLLM(List<CamelToolSpecification> tools) {
        if (tools.isEmpty()) {
            return "No tools found matching the search criteria.";
        }

        StringBuilder result = new StringBuilder();
        result.append("Found ").append(tools.size()).append(" tool(s):\n\n");

        for (int i = 0; i < tools.size(); i++) {
            CamelToolSpecification tool = tools.get(i);
            ToolSpecification spec = tool.getToolSpecification();

            result.append(i + 1).append(". ").append(spec.name()).append("\n");
            result.append("   Description: ").append(spec.description()).append("\n");

            if (spec.parameters() != null && spec.parameters().properties() != null
                    && !spec.parameters().properties().isEmpty()) {
                result.append("   Parameters: ");
                spec.parameters().properties().forEach((name, schema) -> {
                    result.append(name).append(", ");
                });
                // Remove trailing comma and space
                result.setLength(result.length() - 2);
                result.append("\n");
            }

            result.append("\n");
        }

        return result.toString();
    }
}
