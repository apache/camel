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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * MCP Tool for performing security vulnerability analysis on Camel project dependencies.
 * <p>
 * Distinct from {@link DependencyCheckTools} (dependency hygiene) and {@link AdvisoryTools} (Camel CVE listing), this
 * tool cross-references a project's declared dependencies with the Camel security advisory database and component
 * metadata to produce actionable vulnerability findings per artifact.
 */
@ApplicationScoped
public class DependencySecurityAuditTools {

    @Inject
    CatalogService catalogService;

    @Inject
    AdvisoryService advisoryService;

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Perform a security vulnerability audit on a Camel project's dependencies. "
                        + "Cross-references the project's pom.xml dependencies with the Camel security advisory "
                        + "database to identify CVEs affecting each artifact at the project's Camel version. "
                        + "Reports severity, affected version ranges, fixed versions, and whether the vulnerable "
                        + "component is directly used (reachable) or only a transitive dependency. "
                        + "POM content is automatically sanitized to mask sensitive data.")
    public AuditResult camel_dependency_security_audit(
            @ToolArg(description = "The pom.xml file content") String pomContent,
            @ToolArg(description = "Route definitions (YAML, XML, or Java DSL) to determine which components "
                                   + "are actually used (for reachability analysis)") String routes,
            @ToolArg(description = ToolArgDocs.RUNTIME) String runtime,
            @ToolArg(description = ToolArgDocs.CAMEL_VERSION) String camelVersion,
            @ToolArg(description = ToolArgDocs.PLATFORM_BOM) String platformBom,
            @ToolArg(description = "If true (default), mask credentials in POM content") Boolean sanitizePom) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pomContent is required", null);
        }

        try {
            PomSanitizer.ProcessedPom processed = PomSanitizer.process(pomContent, sanitizePom);
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);
            MigrationData.PomAnalysis pom = MigrationData.parsePomContent(processed.content());

            String effectiveVersion = pom.camelVersion() != null ? pom.camelVersion() : catalog.getCatalogVersion();

            List<SecurityAdvisoryModel> allAdvisories = advisoryService.advisories();

            List<String> usedSchemes = routes != null && !routes.isBlank()
                    ? extractUsedSchemes(routes) : List.of();

            Map<String, ArtifactAudit> auditByArtifact = new LinkedHashMap<>();

            for (String dep : pom.dependencies()) {
                List<AdvisoryService.AdvisoryView> matched
                        = AdvisoryService.query(allAdvisories, effectiveVersion, dep, null);

                if (!matched.isEmpty()) {
                    boolean reachable = isReachable(dep, usedSchemes, catalog);
                    List<VulnerabilityFinding> findings = new ArrayList<>();
                    for (AdvisoryService.AdvisoryView adv : matched) {
                        findings.add(new VulnerabilityFinding(
                                adv.cve(),
                                adv.summary(),
                                adv.severity(),
                                adv.affected(),
                                adv.fixed(),
                                adv.url()));
                    }
                    auditByArtifact.put(dep, new ArtifactAudit(dep, reachable, findings));
                }
            }

            for (String compName : catalog.findComponentNames()) {
                ComponentModel model = catalog.componentModel(compName);
                if (model == null || model.getArtifactId() == null) {
                    continue;
                }
                String artifactId = model.getArtifactId();
                if (auditByArtifact.containsKey(artifactId)) {
                    continue;
                }

                List<AdvisoryService.AdvisoryView> matched
                        = AdvisoryService.query(allAdvisories, effectiveVersion, artifactId, null);
                if (!matched.isEmpty() && usedSchemes.contains(compName)) {
                    List<VulnerabilityFinding> findings = new ArrayList<>();
                    for (AdvisoryService.AdvisoryView adv : matched) {
                        findings.add(new VulnerabilityFinding(
                                adv.cve(), adv.summary(), adv.severity(),
                                adv.affected(), adv.fixed(),
                                adv.url()));
                    }
                    auditByArtifact.put(artifactId, new ArtifactAudit(artifactId, true, findings));
                }
            }

            List<ArtifactAudit> vulnerableArtifacts = new ArrayList<>(auditByArtifact.values());
            int totalCves = vulnerableArtifacts.stream().mapToInt(a -> a.findings().size()).sum();
            long criticalCount = vulnerableArtifacts.stream()
                    .flatMap(a -> a.findings().stream())
                    .filter(f -> "critical".equalsIgnoreCase(f.severity()) || "high".equalsIgnoreCase(f.severity()))
                    .count();
            long reachableCount = vulnerableArtifacts.stream().filter(ArtifactAudit::reachable).count();

            List<String> recommendations = buildRecommendations(vulnerableArtifacts, effectiveVersion, catalog);

            AuditSummary summary = new AuditSummary(
                    effectiveVersion,
                    pom.dependencies().size(),
                    vulnerableArtifacts.size(),
                    totalCves,
                    (int) criticalCount,
                    (int) reachableCount,
                    totalCves == 0);

            return new AuditResult(
                    processed.warnings().isEmpty() ? null : processed.warnings(),
                    vulnerableArtifacts.isEmpty() ? null : vulnerableArtifacts,
                    recommendations.isEmpty() ? null : recommendations,
                    summary);

        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to audit dependencies (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    private List<String> extractUsedSchemes(String routes) {
        List<String> schemes = new ArrayList<>();
        String lower = routes.toLowerCase();
        for (String token : lower.split("[^a-z0-9-]+")) {
            if (token.length() > 2 && lower.contains(token + ":")) {
                if (!schemes.contains(token)) {
                    schemes.add(token);
                }
            }
        }
        return schemes;
    }

    private boolean isReachable(String artifactId, List<String> usedSchemes, CamelCatalog catalog) {
        if (usedSchemes.isEmpty()) {
            return true;
        }
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
            List<ArtifactAudit> vulnerableArtifacts, String version, CamelCatalog catalog) {
        List<String> recs = new ArrayList<>();

        boolean hasCritical = vulnerableArtifacts.stream()
                .flatMap(a -> a.findings().stream())
                .anyMatch(f -> "critical".equalsIgnoreCase(f.severity()));
        if (hasCritical) {
            recs.add("URGENT: Critical vulnerabilities found. Upgrade Camel version immediately. "
                     + "Use camel_migration_compatibility to check upgrade path.");
        }

        long reachableVulnCount = vulnerableArtifacts.stream().filter(ArtifactAudit::reachable).count();
        if (reachableVulnCount > 0) {
            recs.add("Found " + reachableVulnCount
                     + " vulnerable artifact(s) that are directly used in routes. "
                     + "These are reachable and should be prioritized for patching.");
        }

        long unreachableCount = vulnerableArtifacts.stream().filter(a -> !a.reachable()).count();
        if (unreachableCount > 0) {
            recs.add("Found " + unreachableCount
                     + " vulnerable artifact(s) not directly used in routes. "
                     + "These are lower priority but should still be evaluated.");
        }

        String latestVersion = catalog.getCatalogVersion();
        if (DependencyCheckTools.compareVersions(version, latestVersion) < 0) {
            recs.add("Upgrade from Camel " + version + " to " + latestVersion
                     + " to pick up security fixes. Use camel_migration_recipes for automated upgrade.");
        }

        if (vulnerableArtifacts.isEmpty()) {
            recs.add("No known Camel CVEs affect the declared dependencies at version " + version + ".");
        }

        return recs;
    }

    // ---- Result records ----

    public record AuditResult(
            List<String> sanitizationWarnings,
            List<ArtifactAudit> vulnerableArtifacts,
            List<String> recommendations,
            AuditSummary summary) {
    }

    public record ArtifactAudit(
            String artifactId,
            boolean reachable,
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

    public record AuditSummary(
            String camelVersion,
            int totalDependencies,
            int vulnerableArtifacts,
            int totalCves,
            int criticalAndHighCves,
            int reachableVulnerableArtifacts,
            boolean clean) {
    }
}
