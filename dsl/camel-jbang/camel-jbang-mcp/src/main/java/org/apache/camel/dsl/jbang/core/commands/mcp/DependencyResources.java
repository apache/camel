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

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Resources exposing Camel dependency reference data.
 * <p>
 * These resources provide browseable dependency information that clients can pull into context when helping users
 * manage Camel project dependencies. Includes the set of core transitive artifacts (no explicit declaration needed) and
 * BOM templates for different runtimes.
 */
@ApplicationScoped
public class DependencyResources {

    @Inject
    DependencyData dependencyData;

    /**
     * Core transitive artifacts that do not need explicit declaration in pom.xml.
     */
    @Resource(uri = "camel://dependency/core-transitive-artifacts",
              name = "camel_dependency_core_transitive_artifacts",
              title = "Camel Core Transitive Artifacts",
              description = "Set of Maven artifact IDs that are transitive dependencies of camel-core "
                            + "and do not need to be declared explicitly in a project's pom.xml. "
                            + "Includes core modules (camel-core, camel-api, camel-support, etc.) "
                            + "and built-in components (camel-direct, camel-seda, camel-log, camel-timer, etc.).",
              mimeType = "application/json")
    public TextResourceContents coreTransitiveArtifacts() {
        JsonObject result = new JsonObject();

        List<String> sorted = new ArrayList<>(dependencyData.getCoreTransitiveArtifacts());
        sorted.sort(String::compareTo);

        JsonArray artifacts = new JsonArray();
        for (String artifact : sorted) {
            artifacts.add(artifact);
        }

        result.put("artifacts", artifacts);
        result.put("totalCount", artifacts.size());
        result.put("description",
                "These artifacts are included transitively via camel-core and do not need explicit "
                                  + "<dependency> declarations. Adding them explicitly is harmless but unnecessary.");

        return new TextResourceContents(
                "camel://dependency/core-transitive-artifacts", result.toJson(),
                "application/json");
    }

    /**
     * BOM template for a specific Camel runtime.
     */
    @ResourceTemplate(uriTemplate = "camel://dependency/bom-template/{runtime}",
                      name = "camel_dependency_bom_template",
                      title = "Camel BOM Template",
                      description = "Maven dependencyManagement snippet for importing the Camel BOM "
                                    + "for a specific runtime (main, spring-boot, or quarkus). "
                                    + "Using a BOM ensures consistent versions across all Camel dependencies.",
                      mimeType = "application/json")
    public TextResourceContents bomTemplate(
            @ResourceTemplateArg(name = "runtime") String runtime) {

        String uri = "camel://dependency/bom-template/" + runtime;

        DependencyData.BomTemplate template = dependencyData.getBomTemplate(runtime);
        if (template == null) {
            JsonObject result = new JsonObject();
            result.put("runtime", runtime);
            result.put("found", false);

            JsonArray available = new JsonArray();
            for (DependencyData.BomTemplate t : dependencyData.getBomTemplates()) {
                available.add(t.runtime());
            }
            result.put("availableRuntimes", available);
            result.put("message", "Unknown runtime '" + runtime + "'. "
                                  + "Use one of: main, spring-boot, quarkus.");
            return new TextResourceContents(uri, result.toJson(), "application/json");
        }

        JsonObject result = new JsonObject();
        result.put("runtime", runtime);
        result.put("found", true);
        result.put("groupId", template.groupId());
        result.put("artifactId", template.artifactId());
        result.put("description", template.description());
        result.put("snippet", dependencyData.formatBomSnippet(runtime, null));

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }
}
