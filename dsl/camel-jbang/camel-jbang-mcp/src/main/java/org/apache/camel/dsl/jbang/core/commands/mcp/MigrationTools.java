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
 * MCP Tools for Camel migration and upgrade workflows.
 * <p>
 * Provides a guided, step-by-step migration pipeline: analyze → compatibility → recipes → guide search. Each tool
 * returns a {@code nextStep} hint that guides the LLM to the next action in the workflow. For migration summaries, use
 * {@code git diff --shortstat} directly.
 */
@ApplicationScoped
public class MigrationTools {

    private static final String OPENREWRITE_VERSION = "6.29.0";
    private static final String CAMEL_UPGRADE_RECIPES_ARTIFACT = "camel-upgrade-recipes";
    private static final String CAMEL_SPRING_BOOT_UPGRADE_RECIPES_ARTIFACT = "camel-spring-boot-upgrade-recipes";

    @Inject
    MigrationData migrationData;

    /**
     * Step 1: Analyze a project's pom.xml to detect runtime, Camel version, Java version, and components.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Analyze a Camel project's pom.xml to detect the runtime type (main, spring-boot, quarkus, "
                        + "wildfly, karaf), Camel version, Java version, and Camel component dependencies. "
                        + "This is the first step in a migration workflow. "
                        + "POM content is automatically sanitized to mask sensitive data (passwords, tokens, API keys) "
                        + "unless sanitizePom is set to false.")
    public ProjectAnalysisResult camel_migration_analyze(
            @ToolArg(description = "The pom.xml file content. "
                                   + "IMPORTANT: Avoid including sensitive data such as passwords, tokens, or API keys. "
                                   + "Sensitive content is automatically detected and masked.") String pomContent,
            @ToolArg(description = "If true (default), automatically sanitize POM content by masking credentials") Boolean sanitizePom) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pomContent is required", null);
        }

        try {
            PomSanitizer.ProcessedPom processed = PomSanitizer.process(pomContent, sanitizePom);

            MigrationData.PomAnalysis pom = MigrationData.parsePomContent(processed.content());

            String runtimeType = pom.runtimeType();
            int majorVersion = pom.majorVersion();

            List<String> warnings = new ArrayList<>(processed.warnings());
            if (pom.camelVersion() == null) {
                warnings.add("Could not detect Camel version from pom.xml. "
                             + "Check if the version is defined in a parent POM.");
            }
            if (pom.javaVersion() == null) {
                warnings.add("Could not detect Java version from pom.xml.");
            }

            List<String> guideUrls = migrationData.getGuidesForVersion(majorVersion).stream()
                    .map(MigrationData.MigrationGuide::url)
                    .collect(Collectors.toList());

            String nextStep;
            if ("wildfly".equals(runtimeType) || "karaf".equals(runtimeType)) {
                nextStep = "Call camel_migration_wildfly_karaf to get migration guidance for " + runtimeType + ".";
            } else {
                nextStep = "Call camel_migration_compatibility with the detected components and target version.";
            }

            return new ProjectAnalysisResult(
                    pom.camelVersion(), majorVersion, runtimeType, pom.javaVersion(),
                    pom.dependencies(), warnings, guideUrls, nextStep);
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException("Failed to parse pom.xml: " + e.getMessage(), e);
        }
    }

    /**
     * Step 2: Check compatibility and provide relevant migration guide references.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Check migration compatibility for Camel components by providing relevant migration guide "
                        + "URLs and Java version requirements. The LLM should consult the migration guides for "
                        + "detailed component rename mappings and API changes.")
    public CompatibilityResult camel_migration_compatibility(
            @ToolArg(description = "Comma-separated list of Camel component artifactIds") String camelComponents,
            @ToolArg(description = "Current Camel version (e.g., 3.20.0)") String currentVersion,
            @ToolArg(description = "Target Camel version (e.g., 4.18.0)") String targetVersion,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus") String runtime,
            @ToolArg(description = "Current Java version (e.g., 11, 17, 21)") String javaVersion) {

        if (camelComponents == null || camelComponents.isBlank()) {
            throw new ToolCallException("camelComponents is required", null);
        }
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new ToolCallException("currentVersion is required", null);
        }
        if (targetVersion == null || targetVersion.isBlank()) {
            throw new ToolCallException("targetVersion is required", null);
        }

        int currentMajor = parseMajorVersion(currentVersion);
        int targetMajor = parseMajorVersion(targetVersion);
        boolean crossesMajorVersion = currentMajor != targetMajor;

        // Java version check
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JavaCompatibility javaCompat = null;

        if (javaVersion != null && !javaVersion.isBlank()) {
            int javaVer = parseJavaVersion(javaVersion);
            boolean javaCompatible = true;
            String requiredVersion;

            if (targetMajor >= 4) {
                int targetMinor = parseMinorVersion(targetVersion);
                if (targetMajor > 4 || targetMinor >= 19) {
                    requiredVersion = "21";
                    if (javaVer < 21) {
                        javaCompatible = false;
                        blockers.add("Camel 4.19+ requires Java 21+. Current Java version is " + javaVersion + ".");
                    }
                } else {
                    requiredVersion = "17";
                    if (javaVer < 17) {
                        javaCompatible = false;
                        blockers.add("Camel 4.x requires Java 17+. Current Java version is " + javaVersion + ".");
                    }
                }
            } else {
                requiredVersion = "11";
            }

            javaCompat = new JavaCompatibility(javaVersion, requiredVersion, javaCompatible);
        }

        // Collect relevant migration guides
        List<MigrationData.MigrationGuide> guides = new ArrayList<>();
        guides.add(migrationData.getMigrationGuide("migration-and-upgrade"));
        if (crossesMajorVersion) {
            if (currentMajor <= 2 && targetMajor >= 3) {
                guides.add(migrationData.getMigrationGuide("camel-3-migration"));
            }
            if (currentMajor <= 3 && targetMajor >= 4) {
                guides.add(migrationData.getMigrationGuide("camel-4-migration"));
            }
        } else {
            if (targetMajor == 3) {
                guides.add(migrationData.getMigrationGuide("camel-3x-upgrade"));
            } else if (targetMajor >= 4) {
                guides.add(migrationData.getMigrationGuide("camel-4x-upgrade"));
            }
        }

        List<String> guideUrls = guides.stream()
                .map(MigrationData.MigrationGuide::url)
                .collect(Collectors.toList());

        if (crossesMajorVersion) {
            warnings.add("This is a major version upgrade (" + currentMajor + ".x → " + targetMajor
                         + ".x). Review the migration guides for component renames, "
                         + "discontinued components, and API changes.");
        }

        String nextStep;
        if (!blockers.isEmpty()) {
            nextStep = "Resolve blockers, then call camel_migration_recipes for OpenRewrite commands.";
        } else {
            nextStep = "Call camel_migration_recipes to get the OpenRewrite upgrade commands.";
        }

        return new CompatibilityResult(
                blockers.isEmpty(), guideUrls, javaCompat, warnings, blockers, nextStep);
    }

    /**
     * Step 3: Get Maven commands to run OpenRewrite migration recipes.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Get Maven commands to run Camel OpenRewrite migration recipes for upgrading between versions. "
                        + "Returns the exact Maven commands to execute on the project. "
                        + "PREREQUISITE: The project MUST compile successfully ('mvn clean compile' must pass) "
                        + "BEFORE running the OpenRewrite recipes. If the project does not compile, fix the build "
                        + "errors first. OpenRewrite requires a compilable project to parse and transform the code.")
    public MigrationRecipesResult camel_migration_recipes(
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus") String runtime,
            @ToolArg(description = "Current Camel version (e.g., 4.4.0)") String currentVersion,
            @ToolArg(description = "Target Camel version (e.g., 4.18.0)") String targetVersion,
            @ToolArg(description = "Current Java version (e.g., 11, 17)") String javaVersion,
            @ToolArg(description = "If true, perform a dry run without making changes (default: true)") Boolean dryRun) {

        if (runtime == null || runtime.isBlank()) {
            throw new ToolCallException("runtime is required", null);
        }
        if (targetVersion == null || targetVersion.isBlank()) {
            throw new ToolCallException("targetVersion is required", null);
        }

        boolean isDryRun = dryRun == null || dryRun;
        String runMode = isDryRun ? "dryRun" : "run";
        List<String> mavenCommands = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        String resolvedRuntime = runtime.toLowerCase().trim();

        if ("quarkus".equals(resolvedRuntime)) {
            // The quarkus-maven-plugin:update command internally uses OpenRewrite recipes
            // including Camel-specific recipes (e.g., io.quarkus.updates.camel.camel44.CamelQuarkusMigrationRecipe)
            // to handle both Quarkus platform and Camel Quarkus upgrades.
            String quarkusCommand;
            if (targetVersion != null && !targetVersion.isBlank()) {
                quarkusCommand = String.format(
                        "mvn --no-transfer-progress io.quarkus.platform:quarkus-maven-plugin:%s:update %s",
                        targetVersion, isDryRun ? "-DrewriteDryRun" : "-DrewriteFullRun");
            } else {
                quarkusCommand = String.format(
                        "mvn --no-transfer-progress io.quarkus.platform:quarkus-maven-plugin:update %s",
                        isDryRun ? "-DrewriteDryRun" : "-DrewriteFullRun");
            }
            mavenCommands.add(quarkusCommand);
            notes.add("The quarkus-maven-plugin:update command uses OpenRewrite under the hood. "
                      + "It automatically applies both Quarkus platform upgrade recipes and Camel Quarkus "
                      + "migration recipes (e.g., CamelQuarkusMigrationRecipe). "
                      + "Do NOT use the standalone camel-upgrade-recipes artifact for Camel Quarkus projects — "
                      + "the Quarkus update command already includes the appropriate Camel recipes.");
            notes.add("IMPORTANT: This command only works on existing Quarkus projects (version 2.13+). "
                      + "If migrating from a non-Quarkus runtime (WildFly, Karaf, WAR), "
                      + "you must first create a new Quarkus project using the archetype from "
                      + "camel_migration_wildfly_karaf, then run this update command.");
        } else {
            String artifact = "spring-boot".equals(resolvedRuntime)
                    ? CAMEL_SPRING_BOOT_UPGRADE_RECIPES_ARTIFACT
                    : CAMEL_UPGRADE_RECIPES_ARTIFACT;

            String command = String.format(
                    "mvn --no-transfer-progress org.openrewrite.maven:rewrite-maven-plugin:%s:%s "
                                           + "-Drewrite.recipeArtifactCoordinates=org.apache.camel.upgrade:%s:%s "
                                           + "-Drewrite.activeRecipes=org.apache.camel.upgrade.CamelMigrationRecipe",
                    OPENREWRITE_VERSION, runMode, artifact, targetVersion);
            mavenCommands.add(command);
        }

        // Java upgrade suggestion
        List<String> javaUpgradeSuggestions = new ArrayList<>();
        if (javaVersion != null && !javaVersion.isBlank()) {
            int javaVer = parseJavaVersion(javaVersion);
            if (javaVer < 21) {
                javaUpgradeSuggestions.add(
                        "Camel 4.19+ requires Java 21+. "
                                           + "OpenRewrite recipe: org.openrewrite.java.migrate.UpgradeToJava21");
            }
        }

        String nextStep = "Verify the project compiles with 'mvn clean compile' before and after running the recipes.";

        return new MigrationRecipesResult(
                resolvedRuntime, currentVersion, targetVersion,
                mavenCommands, javaUpgradeSuggestions, notes, isDryRun, nextStep);
    }

    /**
     * Search migration guides for a specific term.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Search Camel migration and upgrade guides for a specific term or component name. "
                        + "Returns matching snippets from the official guides with version info and URLs. "
                        + "Supports fuzzy matching for typo tolerance. "
                        + "Use this instead of web search when looking up migration-related changes, "
                        + "removed components, API renames, or breaking changes.")
    public GuideSearchResult camel_migration_guide_search(
            @ToolArg(description = "Search query — component name, API class, method, or keyword "
                                   + "(e.g., direct-vm, getOut, camel-http4, ExchangePattern)") String query,
            @ToolArg(description = "Maximum number of results to return (default: 3)") Integer limit) {

        if (query == null || query.isBlank()) {
            throw new ToolCallException("query is required", null);
        }

        int maxResults = limit != null && limit > 0 ? limit : 3;
        List<MigrationData.GuideSection> matches = migrationData.searchGuides(query, maxResults);

        List<GuideSnippet> snippets = new ArrayList<>();
        for (MigrationData.GuideSection section : matches) {
            // Truncate content to avoid huge responses
            String snippet = truncateSnippet(section.content(), 30);
            snippets.add(new GuideSnippet(
                    section.guide(), section.version(), section.sectionTitle(),
                    snippet, section.url()));
        }

        String nextStep = "Call camel_migration_guide_search again for other terms, or call camel_migration_recipes.";

        return new GuideSearchResult(query, snippets.size(), snippets, nextStep);
    }

    private String truncateSnippet(String content, int maxLines) {
        String[] lines = content.split("\n");
        if (lines.length <= maxLines) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("... (truncated, ").append(lines.length - maxLines).append(" more lines)");
        return sb.toString();
    }

    // Helpers

    private int parseMajorVersion(String version) {
        if (version == null) {
            return 0;
        }
        try {
            return Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseMinorVersion(String version) {
        if (version == null) {
            return 0;
        }
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseJavaVersion(String version) {
        if (version == null) {
            return 0;
        }
        try {
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.substring(2));
            }
            return Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Result records

    public record ProjectAnalysisResult(
            String camelVersion,
            int majorVersion,
            String runtimeType,
            String javaVersion,
            List<String> camelComponents,
            List<String> warnings,
            List<String> migrationGuideUrls,
            String nextStep) {
    }

    public record CompatibilityResult(
            boolean compatible,
            List<String> migrationGuideUrls,
            JavaCompatibility javaCompatibility,
            List<String> warnings,
            List<String> blockers,
            String nextStep) {
    }

    public record JavaCompatibility(
            String currentVersion,
            String requiredVersion,
            boolean compatible) {
    }

    public record MigrationRecipesResult(
            String runtime,
            String currentVersion,
            String targetVersion,
            List<String> mavenCommands,
            List<String> javaUpgradeSuggestions,
            List<String> notes,
            boolean dryRun,
            String nextStep) {
    }

    public record GuideSearchResult(
            String query,
            int resultCount,
            List<GuideSnippet> results,
            String nextStep) {
    }

    public record GuideSnippet(
            String guide,
            String version,
            String sectionTitle,
            String snippet,
            String url) {
    }
}
