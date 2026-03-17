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
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

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
    @Tool(description = "Check Camel project dependency hygiene. Given a pom.xml (and optionally route definitions), "
                        + "detects outdated Camel dependencies compared to the latest catalog version, "
                        + "missing Maven dependencies for components used in routes, "
                        + "and version conflicts between the Camel BOM and explicit dependency overrides. "
                        + "Returns actionable recommendations with corrected dependency snippets.")
    public String camel_dependency_check(
            @ToolArg(description = "The pom.xml file content") String pomContent,
            @ToolArg(description = "Route definitions (YAML, XML, or Java DSL) to check for missing component dependencies. "
                                   + "Multiple routes can be provided concatenated.") String routes,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Camel version to use (e.g., 4.17.0). If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pomContent is required", null);
        }

        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);

            MigrationData.PomAnalysis pom = MigrationData.parsePomContent(pomContent);

            JsonObject result = new JsonObject();

            // Project info
            JsonObject projectInfo = new JsonObject();
            projectInfo.put("camelVersion", pom.camelVersion());
            projectInfo.put("runtimeType", pom.runtimeType());
            projectInfo.put("javaVersion", pom.javaVersion());
            projectInfo.put("dependencyCount", pom.dependencies().size());
            result.put("projectInfo", projectInfo);

            // 1. Check for outdated Camel version
            JsonObject versionCheck = checkVersionStatus(pom, catalog);
            result.put("versionStatus", versionCheck);

            // 2. Check for missing dependencies from routes
            JsonArray missingDeps = new JsonArray();
            if (routes != null && !routes.isBlank()) {
                missingDeps = checkMissingDependencies(routes, pom.dependencies(), catalog);
            }
            result.put("missingDependencies", missingDeps);

            // 3. Check for version conflicts (explicit overrides when BOM is present)
            JsonArray conflicts = checkVersionConflicts(pomContent, pom);
            result.put("versionConflicts", conflicts);

            // 4. Build recommendations
            JsonArray recommendations = buildRecommendations(versionCheck, missingDeps, conflicts, pom);
            result.put("recommendations", recommendations);

            // Summary
            JsonObject summary = new JsonObject();
            boolean outdated = versionCheck.containsKey("outdated") && Boolean.TRUE.equals(versionCheck.get("outdated"));
            summary.put("outdated", outdated);
            summary.put("missingDependencyCount", missingDeps.size());
            summary.put("versionConflictCount", conflicts.size());
            int issueCount = (outdated ? 1 : 0) + missingDeps.size() + conflicts.size();
            summary.put("totalIssueCount", issueCount);
            summary.put("healthy", issueCount == 0);
            result.put("summary", summary);

            return result.toJson();
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
    private JsonObject checkVersionStatus(MigrationData.PomAnalysis pom, CamelCatalog catalog) {
        JsonObject check = new JsonObject();
        String catalogVersion = catalog.getCatalogVersion();
        check.put("catalogVersion", catalogVersion);

        if (pom.camelVersion() == null) {
            check.put("status", "unknown");
            check.put("message", "Could not detect Camel version from pom.xml. "
                                 + "Check if the version is defined in a parent POM or BOM.");
            return check;
        }

        check.put("projectVersion", pom.camelVersion());

        int comparison = compareVersions(pom.camelVersion(), catalogVersion);
        if (comparison < 0) {
            check.put("outdated", true);
            check.put("status", "outdated");
            check.put("message", "Project uses Camel " + pom.camelVersion()
                                 + " but " + catalogVersion + " is available. "
                                 + "Consider upgrading for bug fixes and new features.");
        } else if (comparison == 0) {
            check.put("outdated", false);
            check.put("status", "current");
            check.put("message", "Project uses the latest Camel version.");
        } else {
            check.put("outdated", false);
            check.put("status", "newer");
            check.put("message", "Project uses Camel " + pom.camelVersion()
                                 + " which is newer than catalog version " + catalogVersion + ".");
        }

        return check;
    }

    /**
     * Check for components used in routes that are missing from the project's dependencies.
     */
    private JsonArray checkMissingDependencies(String routes, List<String> existingDeps, CamelCatalog catalog) {
        JsonArray missing = new JsonArray();
        String lowerRoutes = routes.toLowerCase();

        for (String comp : catalog.findComponentNames()) {
            if (!containsComponent(lowerRoutes, comp)) {
                continue;
            }

            // Components that are part of camel-core don't need a separate dependency
            ComponentModel model = catalog.componentModel(comp);
            if (model == null) {
                continue;
            }

            String artifactId = model.getArtifactId();
            if (artifactId == null || artifactId.isBlank()) {
                continue;
            }

            // Skip core components — they are transitive dependencies of camel-core
            if (dependencyData.isCoreTransitive(artifactId)) {
                continue;
            }

            // Check if the artifact is in the existing dependencies
            if (!existingDeps.contains(artifactId)) {
                JsonObject dep = new JsonObject();
                dep.put("component", comp);
                dep.put("artifactId", artifactId);
                dep.put("groupId", model.getGroupId());
                dep.put("title", model.getTitle());
                dep.put("snippet", formatDependencySnippet(model.getGroupId(), artifactId));
                missing.add(dep);
            }
        }

        return missing;
    }

    /**
     * Check for version conflicts: explicit version overrides on Camel dependencies when a BOM is present.
     */
    private JsonArray checkVersionConflicts(String pomContent, MigrationData.PomAnalysis pom) {
        JsonArray conflicts = new JsonArray();

        // Detect if a BOM is used
        boolean hasBom = pomContent.contains("camel-bom")
                || pomContent.contains("camel-spring-boot-bom")
                || pomContent.contains("camel-quarkus-bom")
                || pomContent.contains("camel-dependencies");

        if (!hasBom) {
            return conflicts;
        }

        // Look for Camel dependencies with explicit <version> tags
        // Parse the raw XML to detect explicit versions on camel- dependencies
        // We look for patterns like:
        //   <dependency>
        //     <groupId>org.apache.camel</groupId>
        //     <artifactId>camel-kafka</artifactId>
        //     <version>...</version>
        //   </dependency>
        // outside of <dependencyManagement>

        // Simple approach: find dependency blocks with both camel- artifactId and explicit version
        // that are NOT inside dependencyManagement
        String outsideDm = removeDependencyManagement(pomContent);

        for (String dep : pom.dependencies()) {
            // Skip BOM artifacts themselves
            if (dep.endsWith("-bom") || dep.equals("camel-dependencies")) {
                continue;
            }

            // Check if this dependency has an explicit version in the non-DM section
            if (hasExplicitVersion(outsideDm, dep)) {
                JsonObject conflict = new JsonObject();
                conflict.put("artifactId", dep);
                conflict.put("severity", "warning");
                conflict.put("issue", "Explicit version override on '" + dep + "' while a Camel BOM is present.");
                conflict.put("recommendation", "Remove the <version> tag from '" + dep
                                               + "' and let the BOM manage the version to avoid conflicts.");
                conflicts.add(conflict);
            }
        }

        return conflicts;
    }

    /**
     * Build actionable recommendations based on the findings.
     */
    private JsonArray buildRecommendations(
            JsonObject versionCheck, JsonArray missingDeps, JsonArray conflicts,
            MigrationData.PomAnalysis pom) {

        JsonArray recommendations = new JsonArray();

        // Version upgrade recommendation
        if (versionCheck.containsKey("outdated") && Boolean.TRUE.equals(versionCheck.get("outdated"))) {
            JsonObject rec = new JsonObject();
            rec.put("priority", "medium");
            rec.put("category", "Version Upgrade");
            rec.put("action", "Upgrade Camel from " + pom.camelVersion()
                              + " to " + versionCheck.getString("catalogVersion"));
            rec.put("detail", "Use camel_migration_compatibility and camel_migration_recipes tools "
                              + "to check compatibility and get automated upgrade commands.");
            recommendations.add(rec);
        }

        // Missing dependency recommendations
        for (Object obj : missingDeps) {
            JsonObject dep = (JsonObject) obj;
            JsonObject rec = new JsonObject();
            rec.put("priority", "high");
            rec.put("category", "Missing Dependency");
            rec.put("action", "Add dependency for " + dep.getString("component") + " component");
            rec.put("snippet", dep.getString("snippet"));
            recommendations.add(rec);
        }

        // Conflict recommendations
        for (Object obj : conflicts) {
            JsonObject conflict = (JsonObject) obj;
            JsonObject rec = new JsonObject();
            rec.put("priority", "medium");
            rec.put("category", "Version Conflict");
            rec.put("action", conflict.getString("recommendation"));
            recommendations.add(rec);
        }

        // BOM recommendation if not using one
        if (pom.camelVersion() != null && !hasCamelBom(pom)) {
            JsonObject rec = new JsonObject();
            rec.put("priority", "low");
            rec.put("category", "Best Practice");
            rec.put("action", "Use the Camel BOM for consistent dependency management");
            rec.put("snippet", formatBomSnippet(pom));
            recommendations.add(rec);
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
}
