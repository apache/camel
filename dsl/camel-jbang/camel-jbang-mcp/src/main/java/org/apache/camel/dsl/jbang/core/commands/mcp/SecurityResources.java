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
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Resources exposing security reference data for Camel components.
 * <p>
 * These resources provide browseable security data that clients can pull into context independently of route analysis.
 */
@ApplicationScoped
public class SecurityResources {

    @Inject
    SecurityData securityData;

    private final CamelCatalog catalog;

    public SecurityResources() {
        this.catalog = new DefaultCamelCatalog();
    }

    /**
     * All security-sensitive Camel components grouped by category with risk levels and security considerations.
     */
    @Resource(uri = "camel://security/components",
              name = "camel_security_components",
              title = "Security-Sensitive Camel Components",
              description = "Registry of all security-sensitive Camel components grouped by category "
                            + "(Network/API, Messaging, File/Storage, Database, Email, Remote Execution, "
                            + "Directory Services, Secrets Management) with risk levels and security considerations.",
              mimeType = "application/json")
    public TextResourceContents securityComponents() {
        JsonObject result = new JsonObject();

        Map<String, List<String>> byCategory = securityData.getComponentsByCategory();
        JsonArray categories = new JsonArray();

        for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
            JsonObject categoryJson = new JsonObject();
            categoryJson.put("category", entry.getKey());

            JsonArray components = new JsonArray();
            for (String comp : entry.getValue()) {
                JsonObject compJson = new JsonObject();
                compJson.put("name", comp);
                compJson.put("riskLevel", securityData.getRiskLevel(comp));
                compJson.put("securityConsiderations", securityData.getSecurityConsiderations(comp));

                ComponentModel model = catalog.componentModel(comp);
                if (model != null) {
                    compJson.put("title", model.getTitle());
                    compJson.put("description", model.getDescription());
                }

                components.add(compJson);
            }
            categoryJson.put("components", components);
            categories.add(categoryJson);
        }

        result.put("categories", categories);
        result.put("totalComponents", securityData.getSecuritySensitiveComponents().size());

        return new TextResourceContents("camel://security/components", result.toJson(), "application/json");
    }

    /**
     * Security best practices for Camel routes.
     */
    @Resource(uri = "camel://security/best-practices",
              name = "camel_security_best_practices",
              title = "Camel Security Best Practices",
              description = "List of security best practices for Camel routes covering TLS, secrets management, "
                            + "authentication, input validation, and more.",
              mimeType = "application/json")
    public TextResourceContents securityBestPractices() {
        JsonObject result = new JsonObject();

        JsonArray practices = new JsonArray();
        for (String practice : securityData.getBestPractices()) {
            practices.add(practice);
        }

        result.put("bestPractices", practices);
        result.put("totalCount", practices.size());

        return new TextResourceContents("camel://security/best-practices", result.toJson(), "application/json");
    }

    /**
     * Security detail for a specific Camel component.
     */
    @ResourceTemplate(uriTemplate = "camel://security/component/{name}",
                      name = "camel_security_component_detail",
                      title = "Component Security Detail",
                      description = "Security detail for a specific Camel component including risk level, "
                                    + "security considerations, category, and catalog information.",
                      mimeType = "application/json")
    public TextResourceContents securityComponentDetail(
            @ResourceTemplateArg(name = "name") String name) {

        String uri = "camel://security/component/" + name;

        if (!securityData.getSecuritySensitiveComponents().contains(name)) {
            JsonObject result = new JsonObject();
            result.put("name", name);
            result.put("securitySensitive", false);
            result.put("message", "Component '" + name + "' is not in the security-sensitive components registry. "
                                  + "It may still require security configuration depending on use case.");
            return new TextResourceContents(uri, result.toJson(), "application/json");
        }

        JsonObject result = new JsonObject();
        result.put("name", name);
        result.put("securitySensitive", true);
        result.put("riskLevel", securityData.getRiskLevel(name));
        result.put("securityConsiderations", securityData.getSecurityConsiderations(name));
        result.put("category", securityData.getCategory(name));

        ComponentModel model = catalog.componentModel(name);
        if (model != null) {
            result.put("title", model.getTitle());
            result.put("description", model.getDescription());
            result.put("label", model.getLabel());
        }

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }
}
