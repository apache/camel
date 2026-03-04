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
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

/**
 * MCP Tool for migrating Camel projects from WildFly or Karaf to modern runtimes.
 * <p>
 * Handles the special case where projects running on legacy runtimes need to be replatformed to Spring Boot or Quarkus.
 * Uses Maven archetypes for new project creation and migration guides for component mapping details.
 */
@ApplicationScoped
public class MigrationWildflyKarafTools {

    @Inject
    MigrationData migrationData;

    /**
     * WildFly/Karaf migration guidance with archetype-based project creation.
     */
    @Tool(description = "Get migration guidance for Camel projects running on WildFly, Karaf, or WAR-based "
                        + "application servers. Returns the Maven archetype command to create a new target project, "
                        + "migration steps, and relevant migration guide URLs. "
                        + "IMPORTANT: When migrating to a different runtime (e.g., WildFly to Quarkus, Karaf to Spring Boot), "
                        + "you MUST use the archetype command returned by this tool to create a new project. "
                        + "Do NOT manually rewrite the pom.xml — always generate a new project with the archetype first, "
                        + "then migrate routes and source files into it.")
    public WildflyKarafMigrationResult camel_migration_wildfly_karaf(
            @ToolArg(description = "The pom.xml file content of the WildFly/Karaf project") String pomContent,
            @ToolArg(description = "Target runtime: spring-boot or quarkus (default: quarkus)") String targetRuntime,
            @ToolArg(description = "Target Camel version (e.g., 4.18.0)") String targetVersion) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pomContent is required", null);
        }

        try {
            MigrationData.PomAnalysis pom = MigrationData.parsePomContent(pomContent);

            String sourceRuntime = pom.isWildfly() ? "wildfly" : pom.isKaraf() ? "karaf" : "unknown";
            String resolvedTarget = targetRuntime != null && !targetRuntime.isBlank()
                    ? targetRuntime.toLowerCase().trim() : "quarkus";

            // Build archetype command for new project creation
            String archetypeCommand = buildArchetypeCommand(resolvedTarget, targetVersion);

            // Collect migration steps
            List<String> migrationSteps = buildMigrationSteps(sourceRuntime, resolvedTarget);

            // Collect relevant migration guides (always include 2→3 and 3→4 for legacy projects)
            List<MigrationData.MigrationGuide> guides = new ArrayList<>();
            guides.add(migrationData.getMigrationGuide("migration-and-upgrade"));
            guides.add(migrationData.getMigrationGuide("camel-3-migration"));
            guides.add(migrationData.getMigrationGuide("camel-4-migration"));
            guides.add(migrationData.getMigrationGuide("camel-4x-upgrade"));

            List<String> guideUrls = guides.stream()
                    .map(MigrationData.MigrationGuide::url)
                    .collect(Collectors.toList());

            // Warnings specific to the source runtime
            List<String> warnings = new ArrayList<>();
            if ("karaf".equals(sourceRuntime)) {
                warnings.add("Blueprint XML is not supported in Camel 3.x+. "
                             + "Routes must be converted to YAML DSL, XML DSL, or Java DSL.");
                warnings.add("OSGi-specific features (bundle classloading, service registry) "
                             + "have no equivalent in Spring Boot or Quarkus.");
            }
            if ("wildfly".equals(sourceRuntime)) {
                warnings.add("WildFly Camel subsystem is discontinued. "
                             + "Migrate to Camel Spring Boot or Camel Quarkus.");
            }

            String nextStep = "Create a new project using the archetype command, migrate routes and source files, "
                              + "then call camel_migration_recipes for OpenRewrite upgrade commands.";

            return new WildflyKarafMigrationResult(
                    sourceRuntime, pom.camelVersion(), resolvedTarget, targetVersion,
                    pom.dependencies(), archetypeCommand, migrationSteps,
                    guideUrls, warnings, nextStep);
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException("Failed to analyze WildFly/Karaf project: " + e.getMessage(), e);
        }
    }

    private String buildArchetypeCommand(String targetRuntime, String targetVersion) {
        if ("spring-boot".equals(targetRuntime)) {
            // Camel Spring Boot archetype from camel-spring-boot repo
            StringBuilder sb = new StringBuilder();
            sb.append("mvn archetype:generate ");
            sb.append("-DarchetypeGroupId=org.apache.camel.springboot ");
            sb.append("-DarchetypeArtifactId=camel-archetype-spring-boot ");
            if (targetVersion != null && !targetVersion.isBlank()) {
                sb.append("-DarchetypeVersion=").append(targetVersion).append(" ");
            }
            sb.append("-DgroupId=com.example ");
            sb.append("-DartifactId=camel-migration-project ");
            sb.append("-Dversion=1.0-SNAPSHOT ");
            sb.append("-DinteractiveMode=false");
            return sb.toString();
        } else {
            // Quarkus project creation with Camel extensions
            StringBuilder sb = new StringBuilder();
            sb.append("mvn io.quarkus.platform:quarkus-maven-plugin:create ");
            sb.append("-DprojectGroupId=com.example ");
            sb.append("-DprojectArtifactId=camel-migration-project ");
            sb.append("-Dextensions=camel-quarkus-core");
            return sb.toString();
        }
    }

    private List<String> buildMigrationSteps(String sourceRuntime, String targetRuntime) {
        List<String> steps = new ArrayList<>();
        steps.add("Create a new " + targetRuntime + " project using the archetype command provided.");
        steps.add("Review the migration guides for component renames between Camel 2.x and 4.x.");

        if ("karaf".equals(sourceRuntime)) {
            steps.add("Convert Blueprint XML routes to YAML DSL, XML DSL, or Java DSL. "
                      + "Use the camel_transform_route tool for YAML/XML conversions.");
            steps.add("Remove OSGi-specific code (bundle activators, service trackers, "
                      + "MANIFEST.MF Import-Package directives).");
        }

        if ("wildfly".equals(sourceRuntime)) {
            steps.add("Remove WildFly Camel subsystem configuration.");
            steps.add("Convert CDI-based Camel configuration to Spring Boot or Quarkus patterns.");
        }

        steps.add("Add required Camel component dependencies to the new project's pom.xml, "
                  + "using updated artifact names from the migration guides.");
        steps.add("Migrate Java source files, updating package imports and API calls "
                  + "per the migration guides.");
        steps.add("Run 'mvn clean compile' to check for build errors.");
        steps.add("Fix any remaining build errors using camel_migration_guide_search for reference.");
        steps.add("Run 'mvn clean test' to validate the migration.");
        return steps;
    }

    // Result record

    public record WildflyKarafMigrationResult(
            String sourceRuntime,
            String sourceCamelVersion,
            String targetRuntime,
            String targetCamelVersion,
            List<String> detectedDependencies,
            String archetypeCommand,
            List<String> migrationSteps,
            List<String> migrationGuideUrls,
            List<String> warnings,
            String nextStep) {
    }
}
