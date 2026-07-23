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
            @PromptArg(name = "pomContent",
                       description = "Optional: project's pom.xml content. If omitted, the LLM uses the pom.xml already in its conversation context.",
                       required = false) String pomContent,
            @PromptArg(name = "targetVersion", description = "Target Camel version to migrate to (e.g., 4.18.0)",
                       required = false) String targetVersion) {

        String versionNote = targetVersion != null && !targetVersion.isBlank()
                ? "Target version: " + targetVersion
                : "Target version: latest stable (determine from camel_version_list)";

        String pomNote = pomContent != null && !pomContent.isBlank()
                ? "A pom.xml has been supplied as the `pomContent` argument to this prompt."
                : "Use the project's pom.xml from your conversation context.";

        String instructions
                = """
                        You are migrating a Camel project to a newer version.

                        %s
                        %s

                        ## Workflow

                        1. **Analyze**: call `camel_migration_analyze` with the pom.xml to detect runtime, \
                        Camel version, Java version, and component dependencies.
                        2. **Target version**: if not specified, call `camel_version_list` with the detected runtime \
                        (use `lts=true` for LTS releases).
                        3. **Compatibility** (based on detected runtime):
                           - **wildfly** or **karaf**: call `camel_migration_wildfly_karaf` with the pom, target runtime, and target version.
                           - **main**, **spring-boot**, or **quarkus**: call `camel_migration_compatibility` \
                        with components, current/target version, runtime, and Java version.
                        4. **Recipes**: call `camel_migration_recipes` with runtime, versions, Java version, and `dryRun=true` \
                        to get the OpenRewrite Maven commands.
                        5. **Breaking changes**: for each detected component, call `camel_migration_guide_search` \
                        to find renames and API changes.
                        6. **Summary**: report current state, target state, blockers, breaking changes, \
                        migration commands, and manual steps that OpenRewrite cannot automate.
                        """
                        .formatted(versionNote, pomNote);

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

    /**
     * Guided workflow for diagnosing issues with a running Camel route.
     */
    @Prompt(name = "camel_diagnose_route",
            description = "Guided workflow to diagnose issues with a running Camel route: "
                          + "gather runtime state, errors, health, message history, "
                          + "and produce a root cause analysis with actionable fixes.")
    public List<PromptMessage> diagnoseRoute(
            @PromptArg(name = "routeId",
                       description = "Route ID to diagnose (optional — if omitted, diagnoses all routes with issues)",
                       required = false) String routeId,
            @PromptArg(name = "symptom",
                       description = "Description of the observed problem (e.g., 'messages are not being processed', "
                                     + "'high error rate', 'route is suspended')",
                       required = false) String symptom) {

        String routeFilter = routeId != null && !routeId.isBlank() ? routeId : "*";
        String symptomNote = symptom != null && !symptom.isBlank()
                ? "Reported symptom: " + symptom
                : "No specific symptom reported — perform a general health diagnosis.";

        String instructions = """
                You are diagnosing a running Camel application.

                %s
                Target route(s): %s

                ## Workflow

                Follow these steps in order:

                ### Step 1: Gather context
                Call `get_context` to understand the application state (version, uptime, total exchanges).
                Call `get_routes` to see all routes, their states, and error counts.

                ### Step 2: Check health
                Call `get_health` to see if any health checks are failing.
                Note any DOWN or UNKNOWN health statuses.

                ### Step 3: Collect errors
                Call `get_errors` to get captured routing errors with stack traces.
                Call `get_history` to see the message history trace of the last completed exchange.

                ### Step 4: Inspect route details
                Call `get_route_source` with filter="%s" to see the route definition.
                Call `get_top_processors` to identify slow or failing processors.
                Call `get_inflight` to check for stuck exchanges.
                Call `get_blocked` to check for blocked exchanges.

                ### Step 5: Check infrastructure
                Call `get_memory` to check for memory pressure or excessive GC.
                Call `get_endpoints` to verify all endpoints are reachable.

                ### Step 6: Advanced diagnostics (if needed)
                If circuit breakers are involved, call `get_circuit_breakers`.
                If datasources are involved, call `get_datasources`.
                If the issue involves timing, call `get_spans` for trace data.
                If the issue involves metrics, call `get_metrics`.

                ### Step 7: Root cause analysis
                Synthesize the gathered data into a structured diagnosis:

                **Summary**: One-sentence description of the root cause.

                **Evidence**: List the specific data points that led to this conclusion:
                - Which tools provided the key evidence
                - Specific values that indicate the problem (error counts, timing, states)

                **Root Cause**: Detailed explanation of what is going wrong and why.

                **Immediate Actions**: Steps to take right now to mitigate:
                - Route actions (stop, restart, suspend)
                - Configuration changes
                - Resource adjustments

                **Permanent Fix**: What needs to change in the route definition or configuration \
                to prevent recurrence. Include specific code examples if applicable.

                **Monitoring**: What to watch going forward to detect this issue early.
                """.formatted(symptomNote, routeFilter, routeFilter);

        return List.of(PromptMessage.withUserRole(instructions));
    }

    /**
     * Guided workflow for optimizing a running Camel application's performance.
     */
    @Prompt(name = "camel_optimize_route",
            description = "Guided workflow to optimize a Camel application's performance: "
                          + "analyze throughput, identify bottlenecks, review resource usage, "
                          + "and produce prioritized optimization recommendations.")
    public List<PromptMessage> optimizeRoute(
            @PromptArg(name = "routeId",
                       description = "Route ID to optimize (optional — if omitted, analyzes all routes)",
                       required = false) String routeId,
            @PromptArg(name = "goal",
                       description = "Optimization goal: throughput, latency, memory, or general (default: general)",
                       required = false) String goal) {

        String routeFilter = routeId != null && !routeId.isBlank() ? routeId : "*";
        String resolvedGoal = goal != null && !goal.isBlank() ? goal : "general";

        String instructions = """
                You are optimizing a running Camel application for: **%s**.

                Target route(s): %s

                ## Workflow

                Follow these steps in order:

                ### Step 1: Baseline performance
                Call `get_context` to get overall exchange statistics (total, completed, failed, mean time).
                Call `get_routes` to get per-route throughput and timing data.
                Call `get_top_processors` to identify the slowest processors.

                ### Step 2: Resource analysis
                Call `get_memory` to assess JVM memory usage, GC frequency, and thread counts.
                Call `get_consumers` to check consumer configuration and polling strategies.
                Call `get_endpoints` to review endpoint usage patterns.

                ### Step 3: Route structure analysis
                Call `get_route_analysis` to get route structure and anti-pattern hints.
                Call `get_eip_stats` to understand EIP usage and processor performance distribution.

                ### Step 4: Infrastructure checks
                Call `get_metrics` for detailed Micrometer metrics (if available).
                Call `get_datasources` if database access is involved.
                Call `get_circuit_breakers` if resilience patterns are in use.

                ### Step 5: Route inspection
                Call `get_route_source` with filter="%s" to review the route code.
                Call `get_route_dump` with routeId="%s" and format="yaml" to see the full definition.

                ### Step 6: Optimization report
                Produce a structured optimization report:

                **Current Performance Baseline**:
                - Overall throughput (exchanges/sec)
                - Mean and max processing time
                - Error rate
                - Resource utilization (memory, threads)

                **Bottlenecks Identified** (ordered by impact):
                For each bottleneck:
                - What: the specific processor, endpoint, or pattern
                - Impact: how much time or throughput it costs
                - Evidence: the specific metrics that revealed it

                **Optimization Recommendations** (ordered by priority):

                For each recommendation:
                1. **Change**: What to modify (with specific code examples)
                2. **Expected Impact**: Estimated improvement
                3. **Risk**: What could go wrong
                4. **Effort**: Low/Medium/High

                Common optimizations to evaluate:
                - **Parallelism**: Can `split` with `parallelProcessing` or `threads` EIP help?
                - **Batching**: Should messages be batched with `aggregate`?
                - **Caching**: Can frequently-accessed data be cached?
                - **Connection pooling**: Are connection pools properly sized?
                - **Async processing**: Can synchronous calls be made asynchronous?
                - **Backpressure**: Is `throttle` or `circuitBreaker` needed?
                - **Serialization**: Is a more efficient data format available?

                **Trade-offs**: Note any trade-offs between the optimization goal \
                and other qualities (e.g., throughput vs. latency, performance vs. reliability).
                """.formatted(resolvedGoal, routeFilter, routeFilter, routeFilter);

        return List.of(PromptMessage.withUserRole(instructions));
    }
}
