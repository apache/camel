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

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * MCP Tool for checking Camel project dependency hygiene.
 * <p>
 * Analyzes a project's pom.xml (and optionally route definitions) to detect outdated Camel dependencies, missing
 * dependencies for components used in routes, and version conflicts between the Camel BOM and explicit overrides.
 */
@ApplicationScoped
public class DependencyCheckTools {

    @Inject
    CatalogService catalogService;

    @Inject
    DependencyData dependencyData;

    /**
     * Tool to check Camel dependency hygiene for a project.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Check Camel project dependency hygiene. Given a pom.xml (and optionally route definitions), "
                        + "detects outdated Camel dependencies compared to the latest catalog version, "
                        + "missing Maven dependencies for components used in routes, "
                        + "and version conflicts between the Camel BOM and explicit dependency overrides. "
                        + "Returns actionable recommendations with corrected dependency snippets. "
                        + "POM content is automatically sanitized to mask sensitive data (passwords, tokens, API keys) "
                        + "unless sanitizePom is set to false.")
    public DependencyCheckResult camel_dependency_check(
            @ToolArg(description = "The pom.xml file content. "
                                   + "IMPORTANT: Avoid including sensitive data such as passwords, tokens, or API keys. "
                                   + "Sensitive content is automatically detected and masked.") String pomContent,
            @ToolArg(description = "Route definitions (YAML, XML, or Java DSL) to check for missing component dependencies. "
                                   + "Multiple routes can be provided concatenated.") String routes,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Camel version to use (e.g., 4.17.0). If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom,
            @ToolArg(description = "If true (default), automatically sanitize POM content by masking credentials") Boolean sanitizePom) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pomContent is required", null);
        }

        try {
            PomSanitizer.ProcessedPom processed = PomSanitizer.process(pomContent, sanitizePom);

            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);

            MigrationData.PomAnalysis pom = MigrationData.parsePomContent(processed.content());

            // Sanitization warnings
            List<String> sanitizationWarnings = processed.warnings().isEmpty() ? null : List.copyOf(processed.warnings());

            // Project info
            ProjectInfo projectInfo = new ProjectInfo(
                    pom.camelVersion(), pom.runtimeType(), pom.javaVersion(), pom.dependencies().size());

            // 1. Check for outdated Camel version
            VersionStatus versionStatus = checkVersionStatus(pom, catalog);

            // 2. Check for missing dependencies from routes
            List<MissingDependency> missingDeps = List.of();
            if (routes != null && !routes.isBlank()) {
                missingDeps = checkMissingDependencies(routes, pom.dependencies(), catalog);
            }

            // 3. Check for version conflicts (explicit overrides when BOM is present)
            List<VersionConflict> conflicts = checkVersionConflicts(processed.content(), pom);

            // 4. Build recommendations
            List<Recommendation> recommendations = buildRecommendations(versionStatus, missingDeps, conflicts, pom);

            // Summary
            boolean outdated = versionStatus.outdated() != null && versionStatus.outdated();
            int issueCount = (outdated ? 1 : 0) + missingDeps.size() + conflicts.size();
            DependencySummary summary = new DependencySummary(
                    outdated, missingDeps.size(), conflicts.size(), issueCount, issueCount == 0);

            return new DependencyCheckResult(
                    sanitizationWarnings, projectInfo, versionStatus, missingDeps,
                    conflicts, recommendations, summary);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to check dependencies (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Check if the project's Camel version is outdated compared to the catalog version.
     */
    private VersionStatus checkVersionStatus(MigrationData.PomAnalysis pom, CamelCatalog catalog) {
        String catalogVersion = catalog.getCatalogVersion();

        if (pom.camelVersion() == null) {
            return new VersionStatus(
                    catalogVersion, null, null, "unknown",
                    "Could not detect Camel version from pom.xml. "
                                                           + "Check if the version is defined in a parent POM or BOM.");
        }

        int comparison = compareVersions(pom.camelVersion(), catalogVersion);
        if (comparison < 0) {
            return new VersionStatus(
                    catalogVersion, pom.camelVersion(), true, "outdated",
                    "Project uses Camel " + pom.camelVersion()
                                                                          + " but " + catalogVersion
                                                                          + " is available. "
                                                                          + "Consider upgrading for bug fixes and new features.");
        } else if (comparison == 0) {
            return new VersionStatus(
                    catalogVersion, pom.camelVersion(), false, "current",
                    "Project uses the latest Camel version.");
        } else {
            return new VersionStatus(
                    catalogVersion, pom.camelVersion(), false, "newer",
                    "Project uses Camel " + pom.camelVersion()
                                                                        + " which is newer than catalog version "
                                                                        + catalogVersion + ".");
        }
    }

    /**
     * Check for components used in routes that are missing from the project's dependencies.
     */
    private List<MissingDependency> checkMissingDependencies(
            String routes, List<String> existingDeps, CamelCatalog catalog) {
        List<MissingDependency> missing = new ArrayList<>();
        String lowerRoutes = routes.toLowerCase();

        for (String comp : catalog.findComponentNames()) {
            if (!containsComponent(lowerRoutes, comp)) {
                continue;
            }

            ComponentModel model = catalog.componentModel(comp);
            if (model == null) {
                continue;
            }

            String artifactId = model.getArtifactId();
            if (artifactId == null || artifactId.isBlank()) {
                continue;
            }

            if (dependencyData.isCoreTransitive(artifactId)) {
                continue;
            }

            if (!existingDeps.contains(artifactId)) {
                missing.add(new MissingDependency(
                        comp, artifactId, model.getGroupId(), model.getTitle(),
                        formatDependencySnippet(model.getGroupId(), artifactId)));
            }
        }

        return missing;
    }

    /**
     * Check for version conflicts: explicit version overrides on Camel dependencies when a BOM is present.
     */
    private List<VersionConflict> checkVersionConflicts(String pomContent, MigrationData.PomAnalysis pom) {
        List<VersionConflict> conflicts = new ArrayList<>();

        boolean hasBom = pomContent.contains("camel-bom")
                || pomContent.contains("camel-spring-boot-bom")
                || pomContent.contains("camel-quarkus-bom")
                || pomContent.contains("camel-dependencies");

        if (!hasBom) {
            return conflicts;
        }

        String outsideDm = removeDependencyManagement(pomContent);

        for (String dep : pom.dependencies()) {
            if (dep.endsWith("-bom") || dep.equals("camel-dependencies")) {
                continue;
            }

            if (hasExplicitVersion(outsideDm, dep)) {
                conflicts.add(new VersionConflict(
                        dep, "warning",
                        "Explicit version override on '" + dep + "' while a Camel BOM is present.",
                        "Remove the <version> tag from '" + dep
                                                                                                    + "' and let the BOM manage the version to avoid conflicts."));
            }
        }

        return conflicts;
    }

    /**
     * Build actionable recommendations based on the findings.
     */
    private List<Recommendation> buildRecommendations(
            VersionStatus versionStatus, List<MissingDependency> missingDeps,
            List<VersionConflict> conflicts, MigrationData.PomAnalysis pom) {

        List<Recommendation> recommendations = new ArrayList<>();

        // Version upgrade recommendation
        if (versionStatus.outdated() != null && versionStatus.outdated()) {
            recommendations.add(new Recommendation(
                    "medium", "Version Upgrade",
                    "Upgrade Camel from " + pom.camelVersion() + " to " + versionStatus.catalogVersion(),
                    "Use camel_migration_compatibility and camel_migration_recipes tools "
                                                                                                          + "to check compatibility and get automated upgrade commands.",
                    null));
        }

        // Missing dependency recommendations
        for (MissingDependency dep : missingDeps) {
            recommendations.add(new Recommendation(
                    "high", "Missing Dependency",
                    "Add dependency for " + dep.component() + " component", null, dep.snippet()));
        }

        // Conflict recommendations
        for (VersionConflict conflict : conflicts) {
            recommendations.add(new Recommendation(
                    "medium", "Version Conflict",
                    conflict.recommendation(), null, null));
        }

        // BOM recommendation if not using one
        if (pom.camelVersion() != null && !hasCamelBom(pom)) {
            recommendations.add(new Recommendation(
                    "low", "Best Practice",
                    "Use the Camel BOM for consistent dependency management", null, formatBomSnippet(pom)));
        }

        return recommendations;
    }

    // --- Helpers ---

    private boolean containsComponent(String content, String comp) {
        return content.contains(comp + ":")
                || content.contains("\"" + comp + "\"")
                || content.contains("'" + comp + "'");
    }

    /**
     * Compare two version strings. Returns negative if v1 < v2, 0 if equal, positive if v1 > v2.
     */
    static int compareVersions(String v1, String v2) {
        // Strip -SNAPSHOT suffix for comparison
        String clean1 = v1.replace("-SNAPSHOT", "");
        String clean2 = v2.replace("-SNAPSHOT", "");

        String[] parts1 = clean1.split("\\.");
        String[] parts2 = clean2.split("\\.");

        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int p1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Remove dependencyManagement sections from pom content for conflict detection.
     */
    private String removeDependencyManagement(String pomContent) {
        String result = pomContent;
        int start;
        while ((start = result.indexOf("<dependencyManagement>")) >= 0) {
            int end = result.indexOf("</dependencyManagement>", start);
            if (end < 0) {
                break;
            }
            result = result.substring(0, start) + result.substring(end + "</dependencyManagement>".length());
        }
        return result;
    }

    /**
     * Check if a dependency has an explicit version in the POM content.
     */
    private boolean hasExplicitVersion(String pomContent, String artifactId) {
        // Look for the artifactId in a dependency block with a version element
        int idx = pomContent.indexOf("<artifactId>" + artifactId + "</artifactId>");
        if (idx < 0) {
            return false;
        }

        // Find the enclosing <dependency> block
        int depStart = pomContent.lastIndexOf("<dependency>", idx);
        int depEnd = pomContent.indexOf("</dependency>", idx);
        if (depStart < 0 || depEnd < 0) {
            return false;
        }

        String depBlock = pomContent.substring(depStart, depEnd);

        // Check for a <version> element that is not a property placeholder
        // Placeholders like ${camel.version} are fine — they're managed centrally
        return depBlock.contains("<version>") && !depBlock.matches("(?s).*<version>\\$\\{[^}]+}</version>.*");
    }

    private boolean hasCamelBom(MigrationData.PomAnalysis pom) {
        return pom.dependencies().stream()
                .anyMatch(d -> d.endsWith("-bom") || d.equals("camel-dependencies"));
    }

    private String formatDependencySnippet(String groupId, String artifactId) {
        return "<dependency>\n"
               + "    <groupId>" + groupId + "</groupId>\n"
               + "    <artifactId>" + artifactId + "</artifactId>\n"
               + "</dependency>";
    }

    private String formatBomSnippet(MigrationData.PomAnalysis pom) {
        return dependencyData.formatBomSnippet(pom.runtimeType(), pom.camelVersion());
    }

    // Result records

    public record DependencyCheckResult(
            List<String> sanitizationWarnings, ProjectInfo projectInfo, VersionStatus versionStatus,
            List<MissingDependency> missingDependencies, List<VersionConflict> versionConflicts,
            List<Recommendation> recommendations, DependencySummary summary) {
    }

    public record ProjectInfo(String camelVersion, String runtimeType, String javaVersion, int dependencyCount) {
    }

    public record VersionStatus(
            String catalogVersion, String projectVersion, Boolean outdated,
            String status, String message) {
    }

    public record MissingDependency(
            String component, String artifactId, String groupId, String title, String snippet) {
    }

    public record VersionConflict(String artifactId, String severity, String issue, String recommendation) {
    }

    public record Recommendation(String priority, String category, String action, String detail, String snippet) {
    }

    public record DependencySummary(
            boolean outdated, int missingDependencyCount, int versionConflictCount,
            int totalIssueCount, boolean healthy) {
    }
}
