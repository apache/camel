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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.DependencyLookup;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteComponents;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * MCP Tool for performing security vulnerability analysis on Camel project dependencies.
 * <p>
 * Distinct from {@link DependencyCheckTools} (dependency hygiene) and {@link AdvisoryTools} (Camel CVE listing), this
 * tool cross-references a project's declared dependencies with the Camel security advisory database and component
 * metadata to produce actionable vulnerability findings per artifact.
 */
@McpSecured
@ApplicationScoped
public class DependencySecurityAuditTools {

    @Inject
    CatalogService catalogService;

    @Inject
    AdvisoryService advisoryService;

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Perform a security vulnerability audit on a Camel project's dependencies. "
                        + "Cross-references the project's pom.xml dependencies (Spring Boot starters and Camel Quarkus "
                        + "extensions count as the component they provide) with the Camel security advisory "
                        + "database to identify CVEs affecting each artifact at the project's Camel version. "
                        + "Reports severity, affected version ranges, fixed versions, and, when routes are given, "
                        + "whether the vulnerable component is used by them (reachable). Advisories that name no "
                        + "component cannot be matched to dependencies and are listed as uncheckedAdvisories. "
                        + "POM content is automatically sanitized to mask sensitive data.")
    public AuditResult camel_dependency_security_audit(
            @ToolArg(description = "The pom.xml file content") String pomContent,
            @ToolArg(description = "Route definitions (YAML, XML, or Java DSL) to determine which components "
                                   + "are actually used (for reachability analysis)",
                     required = false) String routes,
            @ToolArg(description = ToolArgDocs.RUNTIME, required = false) String runtime,
            @ToolArg(description = ToolArgDocs.CAMEL_VERSION
                                   + " Also the version audited when the pom.xml does not set it (parent-managed).",
                     required = false) String camelVersion,
            @ToolArg(description = ToolArgDocs.PLATFORM_BOM, required = false) String platformBom,
            @ToolArg(description = "If true (default), mask credentials in POM content",
                     required = false) Boolean sanitizePom) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pomContent is required", null);
        }

        try {
            PomSanitizer.ProcessedPom processed = PomSanitizer.process(pomContent, sanitizePom);
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);
            MigrationData.PomAnalysis pom = MigrationData.parsePomContent(processed.content());

            List<String> warnings = new ArrayList<>();
            boolean versionDetected = pom.camelVersion() != null;
            String effectiveVersion = pom.camelVersion();
            if (!versionDetected) {
                if (camelVersion != null && !camelVersion.isBlank() && Character.isDigit(camelVersion.trim().charAt(0))) {
                    effectiveVersion = camelVersion.trim();
                    warnings.add("Could not detect the Camel version from pom.xml (it is likely managed by a parent "
                                 + "POM or a platform BOM): audited Camel " + effectiveVersion
                                 + " from the camelVersion argument.");
                } else {
                    warnings.add("Could not detect the Camel version from pom.xml (it is likely managed by a parent "
                                 + "POM or a platform BOM) and no camelVersion was given: every advisory naming a "
                                 + "declared component is listed, whether or not it affects the project's version. "
                                 + "Call again with camelVersion to audit that version.");
                }
            }

            List<SecurityAdvisoryModel> allAdvisories = advisoryService.advisories();

            boolean routesGiven = routes != null && !routes.isBlank();
            List<String> usedSchemes = routesGiven ? extractUsedSchemes(routes, catalog) : List.of();

            Map<String, ArtifactAudit> auditByArtifact = new LinkedHashMap<>();
            Set<String> audited = new HashSet<>();

            for (String dep : pom.dependencies()) {
                // camel-kafka-starter and camel-quarkus-kafka are camel-kafka for the advisories
                String camelArtifact = DependencyLookup.camelArtifactId(dep);
                audited.add(camelArtifact);
                List<AdvisoryService.AdvisoryView> matched
                        = AdvisoryService.query(allAdvisories, effectiveVersion, camelArtifact, null);

                if (!matched.isEmpty()) {
                    Boolean reachable = routesGiven ? isReachable(camelArtifact, usedSchemes, catalog) : null;
                    auditByArtifact.put(dep, new ArtifactAudit(dep, reachable, findings(matched)));
                }
            }

            // Check components used in routes but not declared as POM dependencies (transitively available)
            for (String compName : usedSchemes) {
                ComponentModel model = catalog.componentModel(compName);
                if (model == null || model.getArtifactId() == null) {
                    continue;
                }
                String artifactId = model.getArtifactId();
                if (!audited.add(artifactId)) {
                    continue;
                }
                List<AdvisoryService.AdvisoryView> matched
                        = AdvisoryService.query(allAdvisories, effectiveVersion, artifactId, null);
                if (!matched.isEmpty()) {
                    auditByArtifact.put(artifactId, new ArtifactAudit(artifactId, true, findings(matched)));
                }
            }

            // advisories that name no component can never match a dependency: list those that may affect the version
            List<VulnerabilityFinding> unchecked = new ArrayList<>();
            for (SecurityAdvisoryModel advisory : allAdvisories) {
                if (advisory.getComponents() == null || advisory.getComponents().isEmpty()) {
                    AdvisoryService.AdvisoryView view = AdvisoryService.AdvisoryView.of(advisory, effectiveVersion);
                    if (!Boolean.FALSE.equals(view.affectsGivenVersion())) {
                        unchecked.add(finding(view));
                    }
                }
            }

            List<ArtifactAudit> vulnerableArtifacts = new ArrayList<>(auditByArtifact.values());
            int totalCves = vulnerableArtifacts.stream().mapToInt(a -> a.findings().size()).sum();
            long criticalCount = vulnerableArtifacts.stream()
                    .flatMap(a -> a.findings().stream())
                    .filter(f -> "critical".equalsIgnoreCase(f.severity()) || "high".equalsIgnoreCase(f.severity()))
                    .count();
            long reachableCount = vulnerableArtifacts.stream().filter(a -> Boolean.TRUE.equals(a.reachable())).count();

            List<String> recommendations
                    = buildRecommendations(vulnerableArtifacts, unchecked, effectiveVersion, routesGiven);

            AuditSummary summary = new AuditSummary(
                    effectiveVersion,
                    versionDetected,
                    pom.dependencies().size(),
                    vulnerableArtifacts.size(),
                    totalCves,
                    (int) criticalCount,
                    (int) reachableCount,
                    unchecked.size(),
                    totalCves == 0 && unchecked.isEmpty() && effectiveVersion != null);

            return new AuditResult(
                    processed.warnings().isEmpty() ? null : processed.warnings(),
                    warnings.isEmpty() ? null : warnings,
                    vulnerableArtifacts.isEmpty() ? null : vulnerableArtifacts,
                    unchecked.isEmpty() ? null : unchecked,
                    recommendations.isEmpty() ? null : recommendations,
                    summary);

        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to audit dependencies (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
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

    /**
     * The components the routes use: the components of their endpoints, read as routes of their DSL (CAMEL-25586).
     * Tokenizing the route text instead would treat the DSL's own structural keywords ({@code from:}, {@code to:},
     * {@code steps:}, {@code uri:}) as component schemes.
     */
    private List<String> extractUsedSchemes(String routes, CamelCatalog catalog) {
        return RouteComponents.of(routes, null, catalog);
    }

    /** Whether the routes use the component of the (catalog) artifact; only called when routes were given. */
    private boolean isReachable(String artifactId, List<String> usedSchemes, CamelCatalog catalog) {
        for (String scheme : usedSchemes) {
            ComponentModel model = catalog.componentModel(scheme);
            if (model != null && artifactId.equals(model.getArtifactId())) {
                return true;
            }
        }
        String schemeName = artifactId.replace("camel-", "");
        return usedSchemes.contains(schemeName);
    }

    private List<String> buildRecommendations(
            List<ArtifactAudit> vulnerableArtifacts, List<VulnerabilityFinding> unchecked, String version,
            boolean routesGiven) {
        List<String> recs = new ArrayList<>();

        boolean hasCritical = vulnerableArtifacts.stream()
                .flatMap(a -> a.findings().stream())
                .anyMatch(f -> "critical".equalsIgnoreCase(f.severity()));
        if (hasCritical) {
            recs.add("URGENT: Critical vulnerabilities found. Upgrade Camel version immediately. "
                     + "Use camel_migration_recipes to get the upgrade commands.");
        }

        if (routesGiven) {
            long reachableVulnCount = vulnerableArtifacts.stream().filter(a -> Boolean.TRUE.equals(a.reachable())).count();
            if (reachableVulnCount > 0) {
                recs.add("Found " + reachableVulnCount
                         + " vulnerable artifact(s) that are directly used in routes. "
                         + "These are reachable and should be prioritized for patching.");
            }
            long unreachableCount = vulnerableArtifacts.stream().filter(a -> Boolean.FALSE.equals(a.reachable())).count();
            if (unreachableCount > 0) {
                recs.add("Found " + unreachableCount
                         + " vulnerable artifact(s) not directly used in routes. "
                         + "These are lower priority but should still be evaluated.");
            }
        } else if (!vulnerableArtifacts.isEmpty()) {
            recs.add("Found " + vulnerableArtifacts.size() + " vulnerable artifact(s). Pass the routes to tell which "
                     + "of them the routes use (reachable).");
        }

        String latestVersion = CamelReleases.latest(catalogService.getDefaultCatalog());
        if (version != null && latestVersion != null && DependencyCheckTools.compareVersions(version, latestVersion) < 0) {
            recs.add("Upgrade from Camel " + version + " to " + latestVersion
                     + " to pick up security fixes. Use camel_migration_recipes for automated upgrade.");
        }

        if (!unchecked.isEmpty()) {
            recs.add(unchecked.size() + " advisory(ies) name no component, so they could not be matched to the "
                     + "dependencies; review uncheckedAdvisories against the project.");
        }
        if (vulnerableArtifacts.isEmpty() && unchecked.isEmpty() && version != null) {
            recs.add("No known Camel CVEs affect the declared dependencies at version " + version + ".");
        }

        return recs;
    }

    // ---- Result records ----

    public record AuditResult(
            List<String> sanitizationWarnings,
            List<String> warnings,
            List<ArtifactAudit> vulnerableArtifacts,
            List<VulnerabilityFinding> uncheckedAdvisories,
            List<String> recommendations,
            AuditSummary summary) {
    }

    /**
     * @param reachable whether the routes use the artifact's component; null when no routes were given
     */
    public record ArtifactAudit(
            String artifactId,
            Boolean reachable,
            List<VulnerabilityFinding> findings) {
    }

    public record VulnerabilityFinding(
            String cve,
            String title,
            String severity,
            String affectedVersions,
            String fixedVersion,
            String advisoryUrl) {
    }

    /**
     * @param camelVersion         the audited Camel version: the project's, else the camelVersion argument, else null
     * @param camelVersionDetected whether the version was read from the pom.xml
     * @param uncheckedAdvisories  advisories that may affect the version but name no component to match
     * @param clean                no matched CVE, no unchecked advisory, and a known version
     */
    public record AuditSummary(
            String camelVersion,
            boolean camelVersionDetected,
            int totalDependencies,
            int vulnerableArtifacts,
            int totalCves,
            int criticalAndHighCves,
            int reachableVulnerableArtifacts,
            int uncheckedAdvisories,
            boolean clean) {
    }
}
