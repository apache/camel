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
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shared holder for Camel dependency reference data used by both {@link DependencyCheckTools} (MCP Tool) and
 * {@link DependencyResources} (MCP Resources).
 * <p>
 * Contains the set of core transitive artifacts and BOM template generation for different runtimes.
 */
@ApplicationScoped
public class DependencyData {

    /**
     * Artifacts that are transitive dependencies of camel-core and do not need to be declared explicitly.
     */
    private static final Set<String> CAMEL_CORE_TRANSITIVE_ARTIFACTS = Set.of(
            "camel-core", "camel-core-model", "camel-core-engine", "camel-core-processor",
            "camel-core-reifier", "camel-core-languages", "camel-core-catalog",
            "camel-bean", "camel-browse", "camel-cluster", "camel-controlbus",
            "camel-dataformat", "camel-dataset", "camel-direct", "camel-file",
            "camel-health", "camel-language", "camel-log", "camel-mock", "camel-ref",
            "camel-rest", "camel-saga", "camel-scheduler", "camel-seda", "camel-stub",
            "camel-timer", "camel-validator", "camel-xpath", "camel-xslt",
            "camel-xml-io", "camel-xml-jaxb", "camel-xml-jaxp", "camel-yaml-io",
            "camel-api", "camel-base", "camel-base-engine", "camel-management-api",
            "camel-support", "camel-util");

    private static final List<BomTemplate> BOM_TEMPLATES = List.of(
            new BomTemplate(
                    "main", "org.apache.camel", "camel-bom",
                    "Standard Camel BOM for Camel Main runtime"),
            new BomTemplate(
                    "spring-boot", "org.apache.camel.springboot", "camel-spring-boot-bom",
                    "Camel Spring Boot BOM for Spring Boot runtime"),
            new BomTemplate(
                    "quarkus", "org.apache.camel.quarkus", "camel-quarkus-bom",
                    "Camel Quarkus BOM for Quarkus runtime"));

    /**
     * Get the set of artifacts that are transitive dependencies of camel-core.
     */
    public Set<String> getCoreTransitiveArtifacts() {
        return CAMEL_CORE_TRANSITIVE_ARTIFACTS;
    }

    /**
     * Check if an artifact is a transitive dependency of camel-core.
     */
    public boolean isCoreTransitive(String artifactId) {
        return CAMEL_CORE_TRANSITIVE_ARTIFACTS.contains(artifactId);
    }

    /**
     * Get all available BOM templates.
     */
    public List<BomTemplate> getBomTemplates() {
        return BOM_TEMPLATES;
    }

    /**
     * Get the BOM template for a specific runtime.
     *
     * @return the BOM template, or null if the runtime is not recognized
     */
    public BomTemplate getBomTemplate(String runtime) {
        for (BomTemplate template : BOM_TEMPLATES) {
            if (template.runtime().equals(runtime)) {
                return template;
            }
        }
        return null;
    }

    /**
     * Format a BOM dependency management snippet for a given runtime and version.
     */
    public String formatBomSnippet(String runtime, String version) {
        BomTemplate template = getBomTemplate(runtime);
        if (template == null) {
            template = BOM_TEMPLATES.get(0); // default to main
        }
        String ver = version != null ? version : "${camel.version}";
        return "<dependencyManagement>\n"
               + "    <dependencies>\n"
               + "        <dependency>\n"
               + "            <groupId>" + template.groupId() + "</groupId>\n"
               + "            <artifactId>" + template.artifactId() + "</artifactId>\n"
               + "            <version>" + ver + "</version>\n"
               + "            <type>pom</type>\n"
               + "            <scope>import</scope>\n"
               + "        </dependency>\n"
               + "    </dependencies>\n"
               + "</dependencyManagement>";
    }

    /**
     * Holds BOM template information for a Camel runtime.
     */
    public record BomTemplate(
            String runtime,
            String groupId,
            String artifactId,
            String description) {
    }
}
