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

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;

/**
 * MCP Prompt definitions that provide structured multi-step workflows for LLMs.
 * <p>
 * Prompts guide the LLM through orchestrating multiple existing tools in the correct sequence, rather than requiring it
 * to discover the workflow on its own.
 */
@ApplicationScoped
public class PromptDefinitions {

    /**
     * Guided workflow for building a Camel integration from requirements.
     */
    @Prompt(name = "camel_build_integration",
            description = "Guided workflow to build a Camel integration: "
                          + "analyze requirements, discover components and EIPs, "
                          + "generate a YAML route, validate it, and run a security check.")
    public List<PromptMessage> buildIntegration(
            @PromptArg(name = "requirements",
                       description = "Natural-language description of what the integration should do") String requirements,
            @PromptArg(name = "runtime", description = "Target runtime: main, spring-boot, or quarkus (default: main)",
                       required = false) String runtime) {

        String resolvedRuntime = runtime != null && !runtime.isBlank() ? runtime : "main";

        String instructions = """
                You are building a Camel integration for the "%s" runtime.

                ## Requirements
                %s

                ## Workflow

                Follow these steps in order:

                ### Step 1: Identify components
                Analyze the requirements above and identify the Camel components needed.
                Call `camel_catalog_components` with a relevant filter and runtime="%s" to find matching components.

                ### Step 2: Identify EIPs
                Determine which Enterprise Integration Patterns are needed (e.g., split, aggregate, filter, choice).
                Call `camel_catalog_eips` with a relevant filter to find matching patterns.

                ### Step 3: Get component details
                For each component you selected, call `camel_catalog_component_doc` with the component name \
                and runtime="%s" to get its endpoint options, required parameters, and URI syntax.

                ### Step 4: Build the route
                Using the gathered information, write a complete YAML route definition. \
                Use correct component URI syntax and required options from the documentation.

                ### Step 5: Validate
                Call `camel_validate_yaml_dsl` with the generated YAML route to check for syntax errors.
                If validation fails, fix the issues and re-validate.

                ### Step 6: Security review
                Call `camel_route_harden_context` with the generated route and format="yaml" \
                to identify security concerns. Address any critical or high-severity findings.

                ### Step 7: Present result
                Present the final YAML route along with:
                - A brief explanation of each component and EIP used
                - Any security recommendations from Step 6
                - Instructions for running the route (e.g., with camel-jbang)
                """.formatted(resolvedRuntime, requirements, resolvedRuntime, resolvedRuntime);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    /**
     * Guided workflow for migrating a Camel project to a new version.
     */
    @Prompt(name = "camel_migrate_project",
            description = "Guided workflow to migrate a Camel project: "
                          + "analyze the pom.xml, check compatibility, "
                          + "get OpenRewrite recipes, search migration guides, "
                          + "and produce a migration summary.")
    public List<PromptMessage> migrateProject(
            @PromptArg(name = "pomContent", description = "The project's pom.xml file content") String pomContent,
            @PromptArg(name = "targetVersion", description = "Target Camel version to migrate to (e.g., 4.18.0)",
                       required = false) String targetVersion) {

        String versionNote = targetVersion != null && !targetVersion.isBlank()
                ? "Target version: " + targetVersion
                : "Target version: latest stable (determine from camel_version_list)";

        String instructions = """
                You are migrating a Camel project to a newer version.

                ## %s

                ## Project pom.xml
                ```xml
                %s
                ```

                ## Workflow

                Follow these steps in order:

                ### Step 1: Analyze the project
                Call `camel_migration_analyze` with the pom.xml content above.
                This detects the current runtime, Camel version, Java version, and component dependencies.

                ### Step 2: Determine target version
                If no target version was specified, call `camel_version_list` with the detected runtime \
                to find the latest stable version. For LTS releases, filter with lts=true.

                ### Step 3: Check compatibility
                Based on the detected runtime from Step 1:
                - For **wildfly** or **karaf** runtimes: call `camel_migration_wildfly_karaf` with the pom.xml \
                content, target runtime, and target version.
                - For **main**, **spring-boot**, or **quarkus** runtimes: call `camel_migration_compatibility` \
                with the detected components, current version, target version, runtime, and Java version.

                Review any blockers (e.g., Java version too old) and warnings.

                ### Step 4: Get migration recipes
                Call `camel_migration_recipes` with the runtime, current version, target version, \
                Java version, and dryRun=true to get the OpenRewrite Maven commands.

                ### Step 5: Search for breaking changes
                For each component detected in Step 1, call `camel_migration_guide_search` \
                with the component name to find relevant breaking changes and rename mappings.

                ### Step 6: Produce migration summary
                Present a structured summary:
                - **Current state**: runtime, Camel version, Java version, component count
                - **Target state**: target version, required Java version
                - **Blockers**: issues that must be resolved before migration
                - **Breaking changes**: component renames, API changes found in guides
                - **Migration commands**: the OpenRewrite commands from Step 4
                - **Manual steps**: any changes that OpenRewrite cannot automate
                """.formatted(versionNote, pomContent);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    /**
     * Guided workflow for a security review of a Camel route.
     */
    @Prompt(name = "camel_security_review",
            description = "Guided workflow to perform a security audit of a Camel route: "
                          + "analyze security-sensitive components, check for vulnerabilities, "
                          + "and produce an actionable audit checklist.")
    public List<PromptMessage> securityReview(
            @PromptArg(name = "route", description = "The Camel route content to review") String route,
            @PromptArg(name = "format", description = "Route format: yaml, xml, or java (default: yaml)",
                       required = false) String format) {

        String resolvedFormat = format != null && !format.isBlank() ? format : "yaml";

        String instructions = """
                You are performing a security audit of a Camel route.

                ## Route (format: %s)
                ```
                %s
                ```

                ## Workflow

                Follow these steps in order:

                ### Step 1: Analyze security
                Call `camel_route_harden_context` with the route above and format="%s".
                This returns security-sensitive components, vulnerabilities, and risk levels.

                ### Step 2: Understand route structure
                Call `camel_route_context` with the route and format="%s".
                This returns the components and EIPs used, helping you understand the full data flow.

                ### Step 3: Produce audit checklist
                Using the results from Steps 1 and 2, produce a structured security audit report:

                **Critical Issues** (must fix before production):
                - List all critical-severity concerns from the security analysis
                - For each: describe the issue, the affected component, and the specific fix

                **Warnings** (should fix):
                - List all high and medium-severity concerns
                - For each: describe the risk and the recommended mitigation

                **Positive Findings** (already secured):
                - List all positive security findings (TLS enabled, property placeholders used, etc.)

                **Recommendations**:
                - Provide actionable, prioritized recommendations based on the specific components used
                - Reference the relevant security best practices for each component
                - Include specific configuration examples where applicable

                **Compliance Notes**:
                - Note any components that handle PII or sensitive data
                - Flag any components that communicate over the network without encryption
                """.formatted(resolvedFormat, route, resolvedFormat, resolvedFormat);

        return List.of(PromptMessage.withUserRole(instructions));
    }
}
