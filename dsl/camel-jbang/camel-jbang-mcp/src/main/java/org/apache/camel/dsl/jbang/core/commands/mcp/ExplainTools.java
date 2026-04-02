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
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;

/**
 * MCP Tool for providing enriched context about Camel routes.
 * <p>
 * This tool extracts components and EIPs used in a route and returns their documentation from the Camel Catalog. The
 * calling LLM can use this context to formulate its own explanation of the route.
 */
@ApplicationScoped
public class ExplainTools {

    @Inject
    CatalogService catalogService;

    /**
     * Tool to get enriched context for a Camel route.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get enriched context for a Camel route including documentation for all components and EIPs used. "
                        +
                        "Returns structured data with component descriptions, EIP explanations, and route structure. " +
                        "Use this context to understand and explain the route.")
    public RouteContextResult camel_route_context(
            @ToolArg(description = "The Camel route content (YAML, XML, or Java DSL)") String route,
            @ToolArg(description = "Route format: yaml, xml, or java (default: yaml)") String format,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Camel version to use (e.g., 4.17.0). If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);

            String resolvedFormat = format != null && !format.isBlank() ? format.toLowerCase() : "yaml";

            // Extract and document components
            List<String> componentNames = extractComponents(route, catalog);
            List<RouteComponent> components = new ArrayList<>();
            for (String comp : componentNames) {
                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    components.add(new RouteComponent(
                            comp, model.getTitle(), model.getDescription(), model.getLabel(),
                            model.getSyntax(), model.isProducerOnly(), model.isConsumerOnly()));
                }
            }

            // Extract and document EIPs
            List<String> eipNames = extractEips(route, catalog);
            List<RouteEip> eips = new ArrayList<>();
            for (String eip : eipNames) {
                EipModel model = catalog.eipModel(eip);
                if (model != null) {
                    eips.add(new RouteEip(eip, model.getTitle(), model.getDescription(), model.getLabel()));
                }
            }

            RouteContextSummary summary = new RouteContextSummary(components.size(), eips.size());

            return new RouteContextResult(resolvedFormat, route, components, eips, summary);
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
    private List<String> extractComponents(String route, CamelCatalog catalog) {
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
    private List<String> extractEips(String route, CamelCatalog catalog) {
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

    // Result records

    public record RouteContextResult(
            String format, String route, List<RouteComponent> components,
            List<RouteEip> eips, RouteContextSummary summary) {
    }

    public record RouteComponent(
            String name, String title, String description, String label,
            String syntax, boolean producerOnly, boolean consumerOnly) {
    }

    public record RouteEip(String name, String title, String description, String label) {
    }

    public record RouteContextSummary(int componentCount, int eipCount) {
    }
}
