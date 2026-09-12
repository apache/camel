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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Diagnoses a Camel error from a stack trace or error message: the known exceptions it contains with their common
 * causes and suggested fixes ({@link DiagnoseData}), the components and EIPs it mentions, and the route id if any.
 * Shared by the {@code camel_error_diagnose} tool of every Camel MCP server.
 */
public final class ErrorDiagnoser {

    private static final Pattern ENDPOINT_URI_PATTERN = Pattern
            .compile("(?:endpoint|uri)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?[^\\s'\"]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPONENT_SCHEME_PATTERN
            = Pattern.compile("(?:component|scheme)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9+.-]*)['\"]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE_ID_PATTERN
            = Pattern.compile("route[:\\s]+['\"]?([a-zA-Z0-9_-]+)['\"]?", Pattern.CASE_INSENSITIVE);

    private static final DiagnoseData DATA = new DiagnoseData();

    private ErrorDiagnoser() {
    }

    /**
     * @param  error   the stack trace or error message
     * @param  catalog the catalog to resolve component and EIP names with
     * @return         identifiedExceptions, identifiedComponents, identifiedEips, routeId and a summary
     */
    public static JsonObject diagnose(String error, CamelCatalog catalog) {
        JsonArray exceptions = new JsonArray();
        for (Map.Entry<String, DiagnoseData.ExceptionInfo> entry : DATA.getKnownExceptions().entrySet()) {
            if (error.contains(entry.getKey())) {
                JsonObject e = new JsonObject();
                e.put("exception", entry.getKey());
                DiagnoseData.ExceptionInfo info = entry.getValue();
                e.put("description", info.description());
                e.put("commonCauses", new JsonArray(info.commonCauses()));
                e.put("suggestedFixes", new JsonArray(info.suggestedFixes()));
                e.put("documentationLinks", new JsonArray(info.documentationLinks()));
                exceptions.add(e);
            }
        }

        JsonArray components = new JsonArray();
        for (String comp : extractComponentNames(error, catalog)) {
            ComponentModel model = catalog.componentModel(comp);
            if (model != null) {
                JsonObject c = new JsonObject();
                c.put("name", comp);
                c.put("title", model.getTitle());
                c.put("description", model.getDescription());
                c.put("documentationUrl", DiagnoseData.CAMEL_COMPONENT_DOC + comp + "-component.html");
                components.add(c);
            }
        }

        JsonArray eips = new JsonArray();
        String lowerError = error.toLowerCase();
        for (String eip : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(eip);
            if (model != null && lowerError.contains(eip.toLowerCase())) {
                JsonObject e = new JsonObject();
                e.put("name", eip);
                e.put("title", model.getTitle());
                e.put("description", model.getDescription());
                e.put("documentationUrl", DiagnoseData.CAMEL_EIP_DOC + eip + "-eip.html");
                eips.add(e);
            }
        }

        JsonObject result = new JsonObject();
        result.put("identifiedExceptions", exceptions);
        result.put("identifiedComponents", components);
        result.put("identifiedEips", eips);
        Matcher routeMatcher = ROUTE_ID_PATTERN.matcher(error);
        if (routeMatcher.find()) {
            result.put("routeId", routeMatcher.group(1));
        }
        JsonObject summary = new JsonObject();
        summary.put("exceptionCount", exceptions.size());
        summary.put("componentCount", components.size());
        summary.put("eipCount", eips.size());
        summary.put("diagnosed", !exceptions.isEmpty());
        result.put("summary", summary);
        if (exceptions.isEmpty()) {
            result.put("message", "No known Camel exception in the text; the components and EIPs it mentions are"
                                  + " listed, and camel_catalog_doc has their options");
        }
        return result;
    }

    private static List<String> extractComponentNames(String error, CamelCatalog catalog) {
        List<String> found = new ArrayList<>();
        Matcher uriMatcher = ENDPOINT_URI_PATTERN.matcher(error);
        while (uriMatcher.find()) {
            String scheme = uriMatcher.group(1).toLowerCase();
            if (catalog.componentModel(scheme) != null && !found.contains(scheme)) {
                found.add(scheme);
            }
        }
        Matcher schemeMatcher = COMPONENT_SCHEME_PATTERN.matcher(error);
        while (schemeMatcher.find()) {
            String scheme = schemeMatcher.group(1).toLowerCase();
            if (catalog.componentModel(scheme) != null && !found.contains(scheme)) {
                found.add(scheme);
            }
        }
        String lowerError = error.toLowerCase();
        for (String comp : catalog.findComponentNames()) {
            if (!found.contains(comp) && (lowerError.contains(comp + ":")
                    || lowerError.contains("\"" + comp + "\"") || lowerError.contains("'" + comp + "'"))) {
                found.add(comp);
            }
        }
        return found;
    }
}
