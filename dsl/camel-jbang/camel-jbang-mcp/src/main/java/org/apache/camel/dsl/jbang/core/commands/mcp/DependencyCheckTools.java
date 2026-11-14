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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.DependencyLookup;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteComponents;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * MCP Tool for checking Camel project dependency hygiene.
 * <p>
 * Analyzes a project's pom.xml (and optionally route definitions) to detect outdated Camel dependencies, missing
 * dependencies for components used in routes, version conflicts between the Camel BOM and explicit overrides, and the
 * published Camel CVE advisories that affect the declared dependencies. {@link AdvisoryTools} lists the advisories by
 * version and component without a project.
 */
@McpSecured
@ApplicationScoped
public class DependencyCheckTools {

    @Inject
    CatalogService catalogService;

    @Inject
    DependencyData dependencyData;

    @Inject
    AdvisoryService advisoryService;

    /**
     * Tool to check Camel dependency hygiene for a project.
     */
    @ToolGuardrails(input = McpArgumentGuardrail.class, output = McpArgumentGuardrail.class)
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Check Camel project dependency hygiene. Given a pom.xml (and optionally route definitions), "
                        + "detects an outdated Camel version compared to the latest Camel release, "
                        + "missing Maven dependencies for components used in routes (Spring Boot starters and "
                        + "Camel Quarkus extensions count as the component they provide), "
                        + "version conflicts between the Camel BOM and explicit dependency overrides, "
                        + "and the published Camel CVE advisories that affect the declared dependencies at the "
                        + "project's Camel version (security: severity, fixed version and, when routes are given, "
                        + "whether the routes use the vulnerable component). "
                        + "Returns actionable recommendations with corrected dependency snippets. "
                        + "POM content is automatically sanitized to mask sensitive data (passwords, tokens, API keys) "
                        + "unless sanitizePom is set to false.")
    public DependencyCheckResult camel_dependency_check(
            @ToolArg(description = "The pom.xml file content. "
                                   + "IMPORTANT: Avoid including sensitive data such as passwords, tokens, or API keys. "
                                   + "Sensitive content is automatically detected and masked.") String pomContent,
            @ToolArg(description = "Route definitions (YAML, XML, or Java DSL) to check for missing component dependencies "
                                   + "and to tell which vulnerable components they use. "
                                   + "Multiple routes can be provided concatenated.",
                     required = false) String routes,
            @ToolArg(description = ToolArgDocs.RUNTIME, required = false) String runtime,
            @ToolArg(description = ToolArgDocs.CAMEL_VERSION
                                   + " Also the version checked for CVEs when the pom.xml does not set it (parent-managed).",
                     required = false) String camelVersion,
            @ToolArg(description = ToolArgDocs.PLATFORM_BOM, required = false) String platformBom,
            @ToolArg(description = "If true (default), automatically sanitize POM content by masking credentials",
                     required = false) Boolean sanitizePom) {

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
            VersionStatus versionStatus = checkVersionStatus(pom, catalog, catalogService.getDefaultCatalog());

            // the components of the routes' endpoints, not every name in the text (CAMEL-25586)
            boolean routesGiven = routes != null && !routes.isBlank();
            List<String> usedComponents = routesGiven ? RouteComponents.of(routes, null, catalog) : List.of();

            // 2. Check for missing dependencies from routes
            List<MissingDependency> missingDeps = checkMissingDependencies(usedComponents, pom, catalog);

            // 3. Check for version conflicts (explicit overrides when BOM is present)
            List<VersionConflict> conflicts = checkVersionConflicts(processed.content(), pom);

            // 4. Check the declared dependencies against the published CVE advisories
            SecurityAudit security = auditSecurity(pom, camelVersion, routesGiven, usedComponents, catalog);

            // 5. Build recommendations
            List<Recommendation> recommendations
                    = buildRecommendations(versionStatus, missingDeps, conflicts, security, routesGiven, pom);

            // Summary: a vulnerable artifact is an issue only at a known version, as without one every advisory of a
            // declared component is listed whether or not it affects the project
            boolean outdated = versionStatus.outdated() != null && versionStatus.outdated();
            int vulnerableCount = security.vulnerableArtifacts() != null && security.camelVersion() != null
                    ? security.vulnerableArtifacts().size() : 0;
            int issueCount = (outdated ? 1 : 0) + missingDeps.size() + conflicts.size() + vulnerableCount;
            DependencySummary summary = new DependencySummary(
                    outdated, missingDeps.size(), conflicts.size(), vulnerableCount, issueCount, issueCount == 0);

            return new DependencyCheckResult(
                    sanitizationWarnings, projectInfo, versionStatus, missingDeps,
                    conflicts, security, recommendations, summary);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to check dependencies (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Check if the project's Camel version is outdated compared to the latest Camel release known to the catalog (not
     * the catalog version, which is a SNAPSHOT on a development build).
     */
    private VersionStatus checkVersionStatus(MigrationData.PomAnalysis pom, CamelCatalog catalog, CamelCatalog releases) {
        String catalogVersion = catalog.getCatalogVersion();
        String latest = CamelReleases.latest(releases);
        if (latest == null) {
            latest = catalogVersion;
        }

        if (pom.camelVersion() == null) {
            return new VersionStatus(
                    catalogVersion, latest, null, null, "unknown",
                    "Could not detect Camel version from pom.xml. "
                                                                   + "Check if the version is defined in a parent POM or BOM.");
        }

        int comparison = compareVersions(pom.camelVersion(), latest);
        if (comparison < 0) {
            return new VersionStatus(
                    catalogVersion, latest, pom.camelVersion(), true, "outdated",
                    "Project uses Camel " + pom.camelVersion() + " but " + latest + " is the latest release. "
                                                                                  + "Consider upgrading for bug fixes and new features.");
        } else if (comparison == 0) {
            return new VersionStatus(
                    catalogVersion, latest, pom.camelVersion(), false, "current",
                    "Project uses the latest Camel release.");
        } else {
            return new VersionStatus(
                    catalogVersion, latest, pom.camelVersion(), false, "newer",
                    "Project uses Camel " + pom.camelVersion() + " which is newer than the latest release "
                                                                                + latest + ".");
        }
    }

    /**
     * Check for components used in routes that are missing from the project's dependencies.
     */
    private List<MissingDependency> checkMissingDependencies(
            List<String> usedComponents, MigrationData.PomAnalysis pom, CamelCatalog catalog) {
        List<MissingDependency> missing = new ArrayList<>();
        String runtime = pom.runtimeType();

        // a Spring Boot starter or Camel Quarkus extension provides the component of the same name
        Set<String> declared = new HashSet<>();
        for (String dep : pom.dependencies()) {
            declared.add(dep);
            declared.add(DependencyLookup.camelArtifactId(dep));
        }

        for (String comp : usedComponents) {
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

            if (!declared.contains(artifactId)) {
                DependencyLookup.RuntimeArtifact ga = "spring-boot".equals(runtime) || "quarkus".equals(runtime)
                        ? DependencyLookup.runtimeArtifact(artifactId, runtime)
                        : new DependencyLookup.RuntimeArtifact(model.getGroupId(), artifactId);
                missing.add(new MissingDependency(
                        comp, ga.artifactId(), ga.groupId(), model.getTitle(),
                        formatDependencySnippet(ga.groupId(), ga.artifactId())));
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
                || pomContent.contains("quarkus-camel-bom")
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
     * Check the declared dependencies, and the components the routes use, against the published Camel CVE advisories at
     * the project's Camel version: the one of the pom.xml, else the camelVersion argument, else none, and then every
     * advisory of a declared component is listed. The advisories ship with the catalog, so no network is used.
     */
    private SecurityAudit auditSecurity(
            MigrationData.PomAnalysis pom, String camelVersion, boolean routesGiven, List<String> usedComponents,
            CamelCatalog catalog) {
        List<String> warnings = new ArrayList<>();
        boolean versionDetected = pom.camelVersion() != null;
        String version = pom.camelVersion();
        if (!versionDetected) {
            if (camelVersion != null && !camelVersion.isBlank() && Character.isDigit(camelVersion.trim().charAt(0))) {
                version = camelVersion.trim();
                warnings.add("Could not detect the Camel version from pom.xml (it is likely managed by a parent "
                             + "POM or a platform BOM): checked the CVEs of Camel " + version
                             + " from the camelVersion argument.");
            } else {
                warnings.add("Could not detect the Camel version from pom.xml (it is likely managed by a parent "
                             + "POM or a platform BOM) and no camelVersion was given: every advisory naming a "
                             + "declared component is listed, whether or not it affects the project's version. "
                             + "Call again with camelVersion to check that version.");
            }
        }

        List<SecurityAdvisoryModel> advisories;
        try {
            advisories = advisoryService.advisories();
        } catch (AdvisoryService.AdvisoriesUnavailableException e) {
            warnings.add(e.getMessage());
            return new SecurityAudit(version, versionDetected, warnings, null, null, 0, 0, 0, false);
        }

        Map<String, ArtifactAudit> byArtifact = new LinkedHashMap<>();
        Set<String> audited = new HashSet<>();
        for (String dep : pom.dependencies()) {
            // camel-kafka-starter and camel-quarkus-kafka are camel-kafka for the advisories
            String camelArtifact = DependencyLookup.camelArtifactId(dep);
            audited.add(camelArtifact);
            List<AdvisoryService.AdvisoryView> matched = AdvisoryService.query(advisories, version, camelArtifact, null);
            if (!matched.isEmpty()) {
                Boolean reachable = routesGiven ? isReachable(camelArtifact, usedComponents, catalog) : null;
                byArtifact.put(dep, new ArtifactAudit(dep, reachable, findings(matched)));
            }
        }

        // the components the routes use without a declared dependency (available transitively)
        for (String comp : usedComponents) {
            ComponentModel model = catalog.componentModel(comp);
            if (model == null || model.getArtifactId() == null || !audited.add(model.getArtifactId())) {
                continue;
            }
            String artifactId = model.getArtifactId();
            List<AdvisoryService.AdvisoryView> matched = AdvisoryService.query(advisories, version, artifactId, null);
            if (!matched.isEmpty()) {
                byArtifact.put(artifactId, new ArtifactAudit(artifactId, true, findings(matched)));
            }
        }

        // advisories that name no component can never match a dependency: list those that may affect the version
        List<VulnerabilityFinding> unchecked = new ArrayList<>();
        for (SecurityAdvisoryModel advisory : advisories) {
            if (advisory.getComponents() == null || advisory.getComponents().isEmpty()) {
                AdvisoryService.AdvisoryView view = AdvisoryService.AdvisoryView.of(advisory, version);
                if (!Boolean.FALSE.equals(view.affectsGivenVersion())) {
                    unchecked.add(finding(view));
                }
            }
        }

        List<ArtifactAudit> vulnerable = new ArrayList<>(byArtifact.values());
        int totalCves = vulnerable.stream().mapToInt(a -> a.findings().size()).sum();
        int criticalAndHigh = (int) vulnerable.stream()
                .flatMap(a -> a.findings().stream())
                .filter(f -> "critical".equalsIgnoreCase(f.severity()) || "high".equalsIgnoreCase(f.severity()))
                .count();
        int reachable = (int) vulnerable.stream().filter(a -> Boolean.TRUE.equals(a.reachable())).count();

        return new SecurityAudit(
                version, versionDetected, warnings.isEmpty() ? null : warnings,
                vulnerable.isEmpty() ? null : vulnerable, unchecked.isEmpty() ? null : unchecked,
                totalCves, criticalAndHigh, reachable,
                totalCves == 0 && unchecked.isEmpty() && version != null);
    }

    private static List<VulnerabilityFinding> findings(List<AdvisoryService.AdvisoryView> matched) {
        List<VulnerabilityFinding> findings = new ArrayList<>();
        for (AdvisoryService.AdvisoryView adv : matched) {
            findings.add(finding(adv));
        }
        return findings;
    }

    private static VulnerabilityFinding finding(AdvisoryService.AdvisoryView adv) {
        return new VulnerabilityFinding(adv.cve(), adv.summary(), adv.severity(), adv.affected(), adv.fixed(), adv.url());
    }

    /** Whether the routes use the component of the (catalog) artifact; only called when routes were given. */
    private static boolean isReachable(String artifactId, List<String> usedComponents, CamelCatalog catalog) {
        for (String comp : usedComponents) {
            ComponentModel model = catalog.componentModel(comp);
            if (model != null && artifactId.equals(model.getArtifactId())) {
                return true;
            }
        }
        return usedComponents.contains(artifactId.replace("camel-", ""));
    }

    /** Which of the vulnerable artifacts to patch first: those the routes use. */
    private static String securityDetail(SecurityAudit security, int count, boolean routesGiven) {
        if (!routesGiven) {
            return "Pass the routes to tell which of them the routes use (reachable).";
        }
        int reachable = security.reachableVulnerableArtifacts();
        if (reachable == 0) {
            return "None of them is used by the routes; they are lower priority but should still be evaluated.";
        }
        String detail = reachable + " of them are used by the routes (reachable): patch those first.";
        if (reachable < count) {
            detail += " The others are lower priority but should still be evaluated.";
        }
        return detail;
    }

    /**
     * Build actionable recommendations based on the findings.
     */
    private List<Recommendation> buildRecommendations(
            VersionStatus versionStatus, List<MissingDependency> missingDeps,
            List<VersionConflict> conflicts, SecurityAudit security, boolean routesGiven,
            MigrationData.PomAnalysis pom) {

        List<Recommendation> recommendations = new ArrayList<>();

        // Version upgrade recommendation
        if (versionStatus.outdated() != null && versionStatus.outdated()) {
            recommendations.add(new Recommendation(
                    "medium", "Version Upgrade",
                    "Upgrade Camel from " + pom.camelVersion() + " to " + versionStatus.latestVersion(),
                    "Use the camel_migration_recipes tool to get automated upgrade commands.",
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

        // Security recommendations
        if (security.vulnerableArtifacts() != null) {
            int count = security.vulnerableArtifacts().size();
            if (security.camelVersion() == null) {
                recommendations.add(new Recommendation(
                        "medium", "Security",
                        "Call again with camelVersion: without the project's Camel version, the advisories of " + count
                                              + " artifact(s) are listed whether or not they affect it.",
                        null, null));
            } else {
                String detail = securityDetail(security, count, routesGiven);
                recommendations.add(new Recommendation(
                        security.criticalAndHighCves() > 0 ? "high" : "medium", "Security",
                        "Upgrade Camel to a release that fixes the " + security.totalCves() + " CVE(s) of " + count
                                                                                            + " artifact(s): each finding gives its fixed version. "
                                                                                            + "Use the camel_migration_recipes tool to get automated upgrade commands.",
                        detail, null));
            }
        }
        if (security.uncheckedAdvisories() != null) {
            recommendations.add(new Recommendation(
                    "low", "Security",
                    "Review security.uncheckedAdvisories against the project: " + security.uncheckedAdvisories().size()
                                       + " advisory(ies) name no component, so they could not be matched to the dependencies.",
                    null, null));
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
        return Stream.concat(pom.dependencies().stream(), pom.managedDependencies().stream())
                .anyMatch(d -> d.startsWith("camel-") && d.endsWith("-bom") || d.equals("camel-dependencies")
                        || d.equals("quarkus-camel-bom"));
    }

    private String formatDependencySnippet(String groupId, String artifactId) {
        return "<dependency>\n"
               + "    <groupId>" + groupId + "</groupId>\n"
               + "    <artifactId>" + artifactId + "</artifactId>\n"
               + "</dependency>";
    }

    private String formatBomSnippet(MigrationData.PomAnalysis pom) {
        return dependencyData.formatBomSnippet(
                pom.runtimeType(), pom.camelVersion(), pom.quarkusVersion(), pom.camelQuarkusVersion());
    }

    // Result records

    public record DependencyCheckResult(
            List<String> sanitizationWarnings, ProjectInfo projectInfo, VersionStatus versionStatus,
            List<MissingDependency> missingDependencies, List<VersionConflict> versionConflicts,
            SecurityAudit security, List<Recommendation> recommendations, DependencySummary summary) {
    }

    /**
     * The published Camel CVE advisories that affect the declared dependencies.
     *
     * @param camelVersion                 the checked Camel version: the project's, else the camelVersion argument,
     *                                     else null
     * @param camelVersionDetected         whether the version was read from the pom.xml
     * @param warnings                     what limits the check, such as a version that could not be detected
     * @param vulnerableArtifacts          the artifacts with advisories, null when none
     * @param uncheckedAdvisories          advisories that may affect the version but name no component to match, null
     *                                     when none
     * @param reachableVulnerableArtifacts the vulnerable artifacts the routes use
     * @param clean                        no matched CVE, no unchecked advisory, and a known version
     */
    public record SecurityAudit(
            String camelVersion, boolean camelVersionDetected, List<String> warnings,
            List<ArtifactAudit> vulnerableArtifacts, List<VulnerabilityFinding> uncheckedAdvisories,
            int totalCves, int criticalAndHighCves, int reachableVulnerableArtifacts, boolean clean) {
    }

    /**
     * @param reachable whether the routes use the artifact's component; null when no routes were given
     */
    public record ArtifactAudit(String artifactId, Boolean reachable, List<VulnerabilityFinding> findings) {
    }

    public record VulnerabilityFinding(
            String cve, String title, String severity, String affectedVersions, String fixedVersion,
            String advisoryUrl) {
    }

    public record ProjectInfo(String camelVersion, String runtimeType, String javaVersion, int dependencyCount) {
    }

    /**
     * @param catalogVersion the version of the catalog this server answers from
     * @param latestVersion  the latest Camel release, what the project version is compared with
     */
    public record VersionStatus(
            String catalogVersion, String latestVersion, String projectVersion, Boolean outdated,
            String status, String message) {
    }

    public record MissingDependency(
            String component, String artifactId, String groupId, String title, String snippet) {
    }

    public record VersionConflict(String artifactId, String severity, String issue, String recommendation) {
    }

    public record Recommendation(String priority, String category, String action, String detail, String snippet) {
    }

    /**
     * @param vulnerableArtifactCount the artifacts with CVEs at a known Camel version
     */
    public record DependencySummary(
            boolean outdated, int missingDependencyCount, int versionConflictCount,
            int vulnerableArtifactCount, int totalIssueCount, boolean healthy) {
    }
}
