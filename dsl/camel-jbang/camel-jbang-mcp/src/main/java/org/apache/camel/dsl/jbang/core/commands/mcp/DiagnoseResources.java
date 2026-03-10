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

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Resources exposing Camel exception diagnostic reference data.
 * <p>
 * These resources provide browseable exception catalog data that clients can pull into context independently of error
 * diagnosis. This allows LLM clients to proactively load exception reference data without needing a stack trace to
 * trigger the diagnose tool.
 */
@ApplicationScoped
public class DiagnoseResources {

    @Inject
    DiagnoseData diagnoseData;

    /**
     * All known Camel exceptions with descriptions and documentation links.
     */
    @Resource(uri = "camel://error/exception-catalog",
              name = "camel_error_exception_catalog",
              title = "Camel Exception Catalog",
              description = "Registry of all known Camel exceptions with descriptions, common causes, "
                            + "suggested fixes, and links to relevant documentation. Covers exceptions like "
                            + "NoSuchEndpointException, ResolveEndpointFailedException, FailedToCreateRouteException, "
                            + "and more.",
              mimeType = "application/json")
    public TextResourceContents exceptionCatalog() {
        JsonObject result = new JsonObject();

        JsonArray exceptions = new JsonArray();
        for (Map.Entry<String, DiagnoseData.ExceptionInfo> entry : diagnoseData.getKnownExceptions().entrySet()) {
            JsonObject exJson = entry.getValue().toSummaryJson();
            exJson.put("name", entry.getKey());
            exceptions.add(exJson);
        }

        result.put("exceptions", exceptions);
        result.put("totalCount", exceptions.size());

        return new TextResourceContents("camel://error/exception-catalog", result.toJson(), "application/json");
    }

    /**
     * All known Camel exceptions with version-specific documentation links.
     */
    @ResourceTemplate(uriTemplate = "camel://error/exception-catalog/{version}",
                      name = "camel_error_exception_catalog_versioned",
                      title = "Camel Exception Catalog (Versioned)",
                      description = "Registry of all known Camel exceptions with documentation links resolved "
                                    + "for a specific Camel version (e.g., '4.18.x', '4.14.x'). "
                                    + "Use 'next' for the latest development docs.",
                      mimeType = "application/json")
    public TextResourceContents exceptionCatalogVersioned(
            @ResourceTemplateArg(name = "version") String version) {

        String uri = "camel://error/exception-catalog/" + version;

        JsonObject result = new JsonObject();
        result.put("version", version);

        JsonArray exceptions = new JsonArray();
        for (Map.Entry<String, DiagnoseData.ExceptionInfo> entry : diagnoseData.getKnownExceptions().entrySet()) {
            JsonObject exJson = entry.getValue().toSummaryJson(version);
            exJson.put("name", entry.getKey());
            exceptions.add(exJson);
        }

        result.put("exceptions", exceptions);
        result.put("totalCount", exceptions.size());

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }

    /**
     * Detail for a specific Camel exception by name.
     */
    @ResourceTemplate(uriTemplate = "camel://error/exception/{name}",
                      name = "camel_error_exception_detail",
                      title = "Exception Detail",
                      description = "Full diagnostic detail for a specific Camel exception including description, "
                                    + "common causes, suggested fixes, and documentation links.",
                      mimeType = "application/json")
    public TextResourceContents exceptionDetail(
            @ResourceTemplateArg(name = "name") String name) {

        String uri = "camel://error/exception/" + name;

        DiagnoseData.ExceptionInfo info = diagnoseData.getException(name);
        if (info == null) {
            JsonObject result = new JsonObject();
            result.put("name", name);
            result.put("found", false);
            result.put("message", "Exception '" + name + "' is not in the known exceptions catalog. "
                                  + "Use the camel://error/exception-catalog resource to see all known exceptions.");
            return new TextResourceContents(uri, result.toJson(), "application/json");
        }

        JsonObject result = info.toJson();
        result.put("name", name);
        result.put("found", true);

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }

    /**
     * Detail for a specific Camel exception with version-specific documentation links.
     */
    @ResourceTemplate(uriTemplate = "camel://error/exception/{name}/{version}",
                      name = "camel_error_exception_detail_versioned",
                      title = "Exception Detail (Versioned)",
                      description = "Full diagnostic detail for a specific Camel exception with documentation links "
                                    + "resolved for a specific Camel version (e.g., '4.18.x', '4.14.x'). "
                                    + "Use 'next' for the latest development docs.",
                      mimeType = "application/json")
    public TextResourceContents exceptionDetailVersioned(
            @ResourceTemplateArg(name = "name") String name,
            @ResourceTemplateArg(name = "version") String version) {

        String uri = "camel://error/exception/" + name + "/" + version;

        DiagnoseData.ExceptionInfo info = diagnoseData.getException(name);
        if (info == null) {
            JsonObject result = new JsonObject();
            result.put("name", name);
            result.put("version", version);
            result.put("found", false);
            result.put("message", "Exception '" + name + "' is not in the known exceptions catalog. "
                                  + "Use the camel://error/exception-catalog resource to see all known exceptions.");
            return new TextResourceContents(uri, result.toJson(), "application/json");
        }

        JsonObject result = info.toJson(version);
        result.put("name", name);
        result.put("version", version);
        result.put("found", true);

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }
}
