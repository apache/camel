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

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tool for providing enriched context about Camel routes.
 * <p>
 * This tool extracts components and EIPs used in a route and returns their documentation from the Camel Catalog. The
 * calling LLM can use this context to formulate its own explanation of the route.
 */
@ApplicationScoped
public class ExplainTools {

    private final CamelCatalog catalog;

    public ExplainTools() {
        this.catalog = new DefaultCamelCatalog();
    }

    /**
     * Tool to get enriched context for a Camel route.
     */
    @Tool(description = "Get enriched context for a Camel route including documentation for all components and EIPs used. "
                        +
                        "Returns structured data with component descriptions, EIP explanations, and route structure. " +
                        "Use this context to understand and explain the route.")
    public String camel_route_context(
            @ToolArg(description = "The Camel route content (YAML, XML, or Java DSL)") String route,
            @ToolArg(description = "Route format: yaml, xml, or java (default: yaml)") String format) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            String resolvedFormat = format != null && !format.isBlank() ? format.toLowerCase() : "yaml";

            JsonObject result = new JsonObject();
            result.put("format", resolvedFormat);
            result.put("route", route);

            // Extract and document components
            List<String> componentNames = extractComponents(route);
            JsonArray components = new JsonArray();
            for (String comp : componentNames) {
                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    JsonObject compJson = new JsonObject();
                    compJson.put("name", comp);
                    compJson.put("title", model.getTitle());
                    compJson.put("description", model.getDescription());
                    compJson.put("label", model.getLabel());
                    compJson.put("syntax", model.getSyntax());
                    compJson.put("producerOnly", model.isProducerOnly());
                    compJson.put("consumerOnly", model.isConsumerOnly());
                    components.add(compJson);
                }
            }
            result.put("components", components);

            // Extract and document EIPs
            List<String> eipNames = extractEips(route);
            JsonArray eips = new JsonArray();
            for (String eip : eipNames) {
                EipModel model = catalog.eipModel(eip);
                if (model != null) {
                    JsonObject eipJson = new JsonObject();
                    eipJson.put("name", eip);
                    eipJson.put("title", model.getTitle());
                    eipJson.put("description", model.getDescription());
                    eipJson.put("label", model.getLabel());
                    eips.add(eipJson);
                }
            }
            result.put("eips", eips);

            // Add summary counts
            JsonObject summary = new JsonObject();
            summary.put("componentCount", components.size());
            summary.put("eipCount", eips.size());
            result.put("summary", summary);

            return result.toJson();
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to get route context (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Extract component names from route content.
     */
    private List<String> extractComponents(String route) {
        List<String> found = new ArrayList<>();
        String lowerRoute = route.toLowerCase();

        for (String comp : catalog.findComponentNames()) {
            if (containsComponent(lowerRoute, comp)) {
                found.add(comp);
            }
        }

        return found;
    }

    /**
     * Extract EIP names from route content.
     */
    private List<String> extractEips(String route) {
        List<String> found = new ArrayList<>();
        String lowerRoute = route.toLowerCase();

        for (String eip : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(eip);
            if (model != null) {
                String eipLower = eip.toLowerCase();
                String eipDash = camelCaseToDash(eip);
                if (lowerRoute.contains(eipLower) || lowerRoute.contains(eipDash)) {
                    found.add(eip);
                }
            }
        }

        return found;
    }

    private boolean containsComponent(String content, String comp) {
        return content.contains(comp + ":")
                || content.contains("\"" + comp + "\"")
                || content.contains("'" + comp + "'");
    }

    private String camelCaseToDash(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
