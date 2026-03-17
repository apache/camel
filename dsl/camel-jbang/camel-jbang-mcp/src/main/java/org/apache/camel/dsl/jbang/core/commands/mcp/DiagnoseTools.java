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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tool for diagnosing Camel errors from stack traces or error messages.
 * <p>
 * Accepts a Camel stack trace or error message and returns the likely component/EIP involved, common causes, links to
 * relevant documentation, and suggested fixes.
 */
@ApplicationScoped
public class DiagnoseTools {

    private static final String CAMEL_COMPONENT_DOC = DiagnoseData.CAMEL_COMPONENT_DOC;
    private static final String CAMEL_EIP_DOC = DiagnoseData.CAMEL_EIP_DOC;

    /**
     * Pattern to extract component names from endpoint URIs in error messages (e.g., "kafka:myTopic", "http://host").
     */
    private static final Pattern ENDPOINT_URI_PATTERN = Pattern
            .compile("(?:endpoint|uri)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?[^\\s'\"]*", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to extract component scheme from common error contexts.
     */
    private static final Pattern COMPONENT_SCHEME_PATTERN
            = Pattern.compile("(?:component|scheme)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9+.-]*)['\"]?", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to extract route IDs from error messages.
     */
    private static final Pattern ROUTE_ID_PATTERN
            = Pattern.compile("route[:\\s]+['\"]?([a-zA-Z0-9_-]+)['\"]?", Pattern.CASE_INSENSITIVE);

    @Inject
    CatalogService catalogService;

    @Inject
    DiagnoseData diagnoseData;

    /**
     * Tool to diagnose Camel errors from stack traces or error messages.
     */
    @Tool(description = "Diagnose a Camel error from a stack trace or error message. "
                        + "Returns the identified component/EIP involved, common causes for the error, "
                        + "links to relevant Camel documentation, and suggested fixes. "
                        + "Covers the most common Camel exceptions including NoSuchEndpointException, "
                        + "ResolveEndpointFailedException, FailedToCreateRouteException, and more.")
    public String camel_error_diagnose(
            @ToolArg(description = "The Camel stack trace or error message to diagnose") String error,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Camel version to use (e.g., 4.17.0). If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom) {

        if (error == null || error.isBlank()) {
            throw new ToolCallException("Error message or stack trace is required", null);
        }

        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);

            JsonObject result = new JsonObject();

            // Identify matching exceptions
            List<String> matchedExceptions = identifyExceptions(error);
            JsonArray exceptionsJson = new JsonArray();
            for (String exceptionName : matchedExceptions) {
                DiagnoseData.ExceptionInfo info = diagnoseData.getException(exceptionName);
                if (info != null) {
                    JsonObject exJson = info.toJson();
                    exJson.put("exception", exceptionName);
                    exceptionsJson.add(exJson);
                }
            }
            result.put("identifiedExceptions", exceptionsJson);

            // Identify components from the error
            List<String> componentNames = extractComponentNames(error, catalog);
            JsonArray componentsJson = new JsonArray();
            for (String comp : componentNames) {
                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    JsonObject compJson = new JsonObject();
                    compJson.put("name", comp);
                    compJson.put("title", model.getTitle());
                    compJson.put("description", model.getDescription());
                    compJson.put("documentationUrl", CAMEL_COMPONENT_DOC + comp + "-component.html");
                    componentsJson.add(compJson);
                }
            }
            result.put("identifiedComponents", componentsJson);

            // Identify EIPs from the error
            List<String> eipNames = extractEipNames(error, catalog);
            JsonArray eipsJson = new JsonArray();
            for (String eip : eipNames) {
                EipModel model = catalog.eipModel(eip);
                if (model != null) {
                    JsonObject eipJson = new JsonObject();
                    eipJson.put("name", eip);
                    eipJson.put("title", model.getTitle());
                    eipJson.put("description", model.getDescription());
                    eipJson.put("documentationUrl", CAMEL_EIP_DOC + eip + "-eip.html");
                    eipsJson.add(eipJson);
                }
            }
            result.put("identifiedEips", eipsJson);

            // Extract route ID if present
            Matcher routeMatcher = ROUTE_ID_PATTERN.matcher(error);
            if (routeMatcher.find()) {
                result.put("routeId", routeMatcher.group(1));
            }

            // Summary
            JsonObject summary = new JsonObject();
            summary.put("exceptionCount", exceptionsJson.size());
            summary.put("componentCount", componentsJson.size());
            summary.put("eipCount", eipsJson.size());
            summary.put("diagnosed", !exceptionsJson.isEmpty());
            result.put("summary", summary);

            return result.toJson();
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to diagnose error (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Identify known Camel exceptions in the error text.
     */
    private List<String> identifyExceptions(String error) {
        List<String> matched = new ArrayList<>();
        for (String exceptionName : diagnoseData.getKnownExceptions().keySet()) {
            if (error.contains(exceptionName)) {
                matched.add(exceptionName);
            }
        }
        return matched;
    }

    /**
     * Extract component names from endpoint URIs and other patterns in the error text.
     */
    private List<String> extractComponentNames(String error, CamelCatalog catalog) {
        List<String> found = new ArrayList<>();

        // Try endpoint URI pattern
        Matcher uriMatcher = ENDPOINT_URI_PATTERN.matcher(error);
        while (uriMatcher.find()) {
            String scheme = uriMatcher.group(1).toLowerCase();
            if (catalog.componentModel(scheme) != null && !found.contains(scheme)) {
                found.add(scheme);
            }
        }

        // Try component scheme pattern
        Matcher schemeMatcher = COMPONENT_SCHEME_PATTERN.matcher(error);
        while (schemeMatcher.find()) {
            String scheme = schemeMatcher.group(1).toLowerCase();
            if (catalog.componentModel(scheme) != null && !found.contains(scheme)) {
                found.add(scheme);
            }
        }

        // Scan for known component names in the error text
        String lowerError = error.toLowerCase();
        for (String comp : catalog.findComponentNames()) {
            if (!found.contains(comp) && containsComponent(lowerError, comp)) {
                found.add(comp);
            }
        }

        return found;
    }

    /**
     * Extract EIP names from the error text.
     */
    private List<String> extractEipNames(String error, CamelCatalog catalog) {
        List<String> found = new ArrayList<>();
        String lowerError = error.toLowerCase();

        for (String eip : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(eip);
            if (model != null) {
                String eipLower = eip.toLowerCase();
                if (lowerError.contains(eipLower) && !found.contains(eip)) {
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
}
