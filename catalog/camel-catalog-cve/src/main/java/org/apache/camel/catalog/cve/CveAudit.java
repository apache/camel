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
package org.apache.camel.catalog.cve;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * Audits JARs for known vulnerabilities: OSV.dev for every JAR, and the Apache Camel security advisories of the catalog
 * for the Camel JARs. Each CVE gets the version that fixes each JAR, the dependency chain that brings it in and whether
 * the routes use it. Shared by the CVE Audit screen of the TUI and {@code camel dependency audit}.
 */
public final class CveAudit {

    private CveAudit() {
    }

    /**
     * The result of an audit.
     *
     * @param findings  the CVEs, the worst first
     * @param artifacts the same by JAR, with the version that fixes all the CVEs of each
     * @param osvError  why OSV.dev gave no answer, so only the Camel advisories were checked; null when it did
     */
    public record Result(List<CveFinding> findings, List<CveArtifact> artifacts, String osvError) {
    }

    /**
     * Audits the JARs.
     *
     * @param deps               the JARs to audit
     * @param jars               the JAR files by {@code groupId:artifactId}, to tell the chains; may be empty
     * @param declared           the {@code groupId:artifactId} of the dependencies the project declares
     * @param fallbackVia        the dependency that brings a JAR in, by {@code groupId:artifactId}, when there are no
     *                           JAR files
     * @param endpointComponents the components of the endpoints of the routes; null when not known (no usage)
     * @param catalog            the catalog with the Camel advisories and the components
     * @param osv                the OSV.dev API client or the offline OSV database; null to check the Camel advisories
     *                           only
     */
    public static Result audit(
            List<Dependency> deps, Map<String, Path> jars, Set<String> declared, Map<String, String> fallbackVia,
            Collection<String> endpointComponents, CamelCatalog catalog, VulnerabilitySource osv) {
        Map<String, List<OsvClient.Vulnerability>> vulnMap = osv != null ? osv.queryBatch(deps) : Map.of();
        String osvError = osv != null ? osv.lastError() : null;

        List<CveFinding> findings = CveScan.buildCveFindings(vulnMap, Map.of());
        findings = CveScan.applyCamelAdvisories(findings, deps, advisories(catalog));
        if (!findings.isEmpty()) {
            Map<String, List<String>> chains = chains(jars, declared, findings);
            CveScan.attachChains(findings, chains, fallbackVia, declared);
            if (endpointComponents != null) {
                CveScan.markRoutesUsage(findings, chains, endpointComponents, CveScan.componentSchemes(catalog));
            }
        }
        findings.sort(Comparator.comparingInt((CveFinding g) -> CveScan.severityIndex(g.severity()))
                .thenComparing(CveFinding::canonicalId));
        return new Result(findings, CveScan.artifactRows(findings), osvError);
    }

    private static List<SecurityAdvisoryModel> advisories(CamelCatalog catalog) {
        try {
            return catalog.camelSecurityAdvisories();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** The chain to each vulnerable JAR, by {@code groupId:artifactId}, without the JAR itself. */
    private static Map<String, List<String>> chains(
            Map<String, Path> jars, Set<String> declared, List<CveFinding> findings) {
        Map<String, List<String>> result = new HashMap<>();
        if (jars.isEmpty()) {
            return result;
        }
        Map<String, Set<String>> graph = DependencyChains.graph(jars);
        for (CveFinding finding : findings) {
            for (String gav : finding.affectedGavs()) {
                String ga = CveScan.ga(gav);
                if (!result.containsKey(ga)) {
                    List<String> chain = DependencyChains.chain(graph, declared, ga);
                    result.put(ga, chain.isEmpty() ? List.of() : chain.subList(0, chain.size() - 1));
                }
            }
        }
        return result;
    }
}
