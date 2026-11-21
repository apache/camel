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
package org.apache.camel.dsl.jbang.core.common.cve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.run.common.SecurityAdvisoryHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * What the CVE Audit tab makes of a scan: the vulnerabilities OSV.dev reports for the JARs grouped by CVE, the version
 * that fixes each JAR, the Apache Camel advisories of the Camel JARs, whether the routes use the component that brings
 * a JAR in, and the same grouped by JAR (what to upgrade).
 */
public final class CveScan {

    public static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN");
    public static final String CAMEL_GROUP_ID = "org.apache.camel";
    /** Between the artifacts of a dependency chain. */
    public static final String CHAIN_SEPARATOR = " > ";

    // the URI schemes of the components, by artifactId
    private static volatile Map<String, Set<String>> componentSchemes;

    private CveScan() {
    }

    /**
     * The vulnerabilities grouped by CVE: a GHSA and a CVE id of the same vulnerability are one group, under the CVE
     * id.
     *
     * @param vulnMap the vulnerabilities by JAR ({@code groupId:artifactId:version})
     * @param gavVia  the dependency that brings a JAR in, by {@code groupId:artifactId:version} or
     *                {@code groupId:artifactId}
     */
    public static List<CveFinding> buildCveFindings(
            Map<String, List<OsvClient.Vulnerability>> vulnMap, Map<String, String> gavVia) {
        Map<String, String> aliasToCanonical = new HashMap<>();
        Map<String, CveFinding> groups = new LinkedHashMap<>();

        for (Map.Entry<String, List<OsvClient.Vulnerability>> entry : vulnMap.entrySet()) {
            String gav = entry.getKey();
            for (OsvClient.Vulnerability vuln : entry.getValue()) {
                String canonicalId = resolveCanonicalId(vuln, aliasToCanonical);

                CveFinding group = groups.get(canonicalId);
                if (group == null) {
                    group = new CveFinding(
                            canonicalId, normalizeSeverity(vuln.severity()), vuln.cvssVector(), vuln.summary(),
                            vuln.details(), vuln.published(), new ArrayList<>(), new HashMap<>(),
                            new ArrayList<>(), new ArrayList<>());
                    groups.put(canonicalId, group);
                    aliasToCanonical.put(vuln.id(), canonicalId);
                    for (String alias : vuln.aliases()) {
                        aliasToCanonical.put(alias, canonicalId);
                    }
                }

                if (!group.affectedGavs().contains(gav)) {
                    group.affectedGavs().add(gav);
                    String via = gavVia.get(gav);
                    if (via == null) {
                        via = gavVia.get(ga(gav));
                    }
                    if (via != null) {
                        group.gavVia().put(gav, via);
                    }
                }
                String fix = vuln.nearestFix(ga(gav), version(gav));
                String known = group.fixFor().get(gav);
                if (fix != null && (known == null || compare(fix, known) > 0)) {
                    // two records of one CVE: the higher fix is the one that fixes both
                    group.fixFor().put(gav, fix);
                }
                for (String alias : vuln.aliases()) {
                    if (!alias.equals(canonicalId) && !group.aliases().contains(alias)) {
                        group.aliases().add(alias);
                    }
                }
                if (!vuln.id().equals(canonicalId) && !group.aliases().contains(vuln.id())) {
                    group.aliases().add(vuln.id());
                }
                if (vuln.fixedVersions() != null) {
                    for (String fv : vuln.fixedVersions()) {
                        if (!group.fixedVersions().contains(fv)) {
                            group.fixedVersions().add(fv);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * Adds the Apache Camel advisories that affect the Camel JARs on the classpath: to the group of the same CVE when
     * OSV.dev reported it, else as a group of its own. An advisory tells the Camel release to upgrade to, and how to
     * mitigate meanwhile. Advisories that name no component, or whose affected versions cannot be read, are left out.
     *
     * @param deps the JARs on the classpath
     */
    public static List<CveFinding> applyCamelAdvisories(
            List<CveFinding> groups, List<Dependency> deps, List<SecurityAdvisoryModel> advisories) {
        if (advisories == null || advisories.isEmpty()) {
            return groups;
        }
        List<CveFinding> result = new ArrayList<>(groups);
        for (Dependency dep : deps) {
            if (!CAMEL_GROUP_ID.equals(dep.groupId()) || dep.version() == null) {
                continue;
            }
            for (SecurityAdvisoryModel advisory : advisories) {
                if (advisory.getCve() == null || advisory.getComponents() == null
                        || !advisory.getComponents().contains(dep.artifactId())
                        || !Boolean.TRUE.equals(
                                SecurityAdvisoryHelper.affectsVersion(advisory.getAffected(), dep.version()))) {
                    continue;
                }
                String gav = dep.gav();
                CveFinding group = findGroup(result, advisory.getCve());
                if (group == null) {
                    group = new CveFinding(
                            advisory.getCve(), normalizeSeverity(advisory.getSeverity()), null, advisory.getSummary(),
                            null, advisory.getDate(), new ArrayList<>(), new HashMap<>(), new ArrayList<>(),
                            new ArrayList<>());
                    result.add(group);
                }
                if (!group.affectedGavs().contains(gav)) {
                    group.affectedGavs().add(gav);
                }
                String upgradeTo = SecurityAdvisoryHelper.upgradeTo(
                        advisory.getAffected(), advisory.getFixed(), dep.version());
                group.setCamelAdvisory(new CamelAdvisory(
                        advisory.getFixed(), upgradeTo, advisory.getMitigation(), advisory.getUrl()));
                if (upgradeTo != null) {
                    group.fixFor().putIfAbsent(gav, upgradeTo);
                }
            }
        }
        return result;
    }

    private static CveFinding findGroup(List<CveFinding> groups, String cve) {
        for (CveFinding g : groups) {
            if (cve.equals(g.canonicalId()) || g.aliases().contains(cve)) {
                return g;
            }
        }
        return null;
    }

    /**
     * Sets the dependency chain that brings each JAR in, and which JARs the project declares itself.
     *
     * @param chains      the chain to each JAR by {@code groupId:artifactId}, without the JAR itself
     * @param fallbackVia the dependency that brings a JAR in, by {@code groupId:artifactId}, when there are no chains
     * @param declared    the {@code groupId:artifactId} of the dependencies of the project
     */
    public static void attachChains(
            List<CveFinding> groups, Map<String, List<String>> chains, Map<String, String> fallbackVia,
            Set<String> declared) {
        for (CveFinding group : groups) {
            for (String gav : group.affectedGavs()) {
                String ga = ga(gav);
                String via = via(chains.get(ga));
                if (via == null) {
                    via = fallbackVia.get(ga);
                }
                if (via != null) {
                    group.gavVia().put(gav, via);
                }
                if (declared.contains(ga)) {
                    group.declared().add(gav);
                }
            }
        }
    }

    /**
     * Marks whether the routes use each vulnerable JAR: through the Camel component nearest to it in its dependency
     * chain (the JAR itself, when it is one), used when the integration has an endpoint of that component. Unknown
     * (null) when no component brings it in, as for the JARs of the runtime and the libraries the project adds.
     *
     * @param chains             the chain to each JAR, by {@code groupId:artifactId}: the dependencies from the
     *                           project's to the JAR (not included)
     * @param endpointComponents the components of the endpoints of the integration
     * @param schemes            the URI schemes of the Camel components, by artifactId
     */
    public static void markRoutesUsage(
            List<CveFinding> groups, Map<String, List<String>> chains, Collection<String> endpointComponents,
            Map<String, Set<String>> schemes) {
        for (CveFinding group : groups) {
            Boolean used = null;
            for (String gav : group.affectedGavs()) {
                CveUsage usage = usage(gav, chains.getOrDefault(ga(gav), List.of()), endpointComponents, schemes);
                if (usage == null) {
                    continue;
                }
                group.usedBy().put(gav, usage);
                if (usage.used()) {
                    used = true;
                } else if (used == null) {
                    used = false;
                }
            }
            group.setUsedByRoutes(used);
        }
    }

    private static CveUsage usage(
            String gav, List<String> chain, Collection<String> endpointComponents, Map<String, Set<String>> schemes) {
        List<String> path = new ArrayList<>(chain);
        path.add(ga(gav));
        // from the JAR back to the dependency of the project: the nearest component
        for (int i = path.size() - 1; i >= 0; i--) {
            String ga = path.get(i);
            if (!ga.startsWith(CAMEL_GROUP_ID + ":")) {
                continue;
            }
            Set<String> s = schemes.get(shortArtifact(ga));
            if (s != null) {
                boolean used = endpointComponents.stream().anyMatch(s::contains);
                return new CveUsage(shortArtifact(ga), used);
            }
        }
        return null;
    }

    /** The URI schemes of the components of the catalog, by artifactId (camel-http has http and https). */
    public static Map<String, Set<String>> componentSchemes(CamelCatalog catalog) {
        Map<String, Set<String>> result = componentSchemes;
        if (result != null) {
            return result;
        }
        result = new HashMap<>();
        for (String name : catalog.findComponentNames()) {
            ComponentModel model = catalog.componentModel(name);
            if (model != null && model.getArtifactId() != null) {
                result.computeIfAbsent(model.getArtifactId(), k -> new TreeSet<>()).add(name);
            }
        }
        componentSchemes = result;
        return result;
    }

    /**
     * The vulnerabilities grouped by JAR: what to upgrade. Each row has the worst severity and highest score of its
     * CVEs, and the version that fixes all of them (null when one has no known fix).
     */
    public static List<CveArtifact> artifactRows(List<CveFinding> groups) {
        Map<String, CveArtifact> rows = new LinkedHashMap<>();
        for (CveFinding group : groups) {
            for (String gav : group.affectedGavs()) {
                CveArtifact row = rows.computeIfAbsent(gav, k -> new CveArtifact(k, new ArrayList<>()));
                row.cves().add(group);
                if (row.via == null) {
                    row.via = group.gavVia().get(gav);
                }
                if (row.usage == null) {
                    row.usage = group.usedBy().get(gav);
                }
                row.declared |= group.declared().contains(gav);
            }
        }
        for (CveArtifact row : rows.values()) {
            row.cves().sort(Comparator.comparingInt((CveFinding g) -> severityIndex(g.severity()))
                    .thenComparing(g -> g.canonicalId()));
            String fix = null;
            boolean allFixed = true;
            boolean camelOnly = true;
            for (CveFinding g : row.cves()) {
                row.severity = severityIndex(g.severity()) < severityIndex(row.severity) ? g.severity() : row.severity;
                Double score = g.score();
                if (score != null && (row.score == null || score > row.score)) {
                    row.score = score;
                }
                String f = g.fixFor().get(row.gav());
                if (f == null) {
                    allFixed = false;
                } else if (fix == null || compare(f, fix) > 0) {
                    fix = f;
                }
                camelOnly &= g.camelAdvisory() != null;
            }
            row.fix = allFixed ? fix : null;
            row.camelRelease = camelOnly && row.gav().startsWith(CAMEL_GROUP_ID + ":");
        }
        return new ArrayList<>(rows.values());
    }

    public static String resolveCanonicalId(OsvClient.Vulnerability vuln, Map<String, String> aliasToCanonical) {
        String existing = aliasToCanonical.get(vuln.id());
        if (existing != null) {
            return existing;
        }
        for (String alias : vuln.aliases()) {
            existing = aliasToCanonical.get(alias);
            if (existing != null) {
                return existing;
            }
        }
        for (String alias : vuln.aliases()) {
            if (alias.startsWith("CVE-")) {
                return alias;
            }
        }
        return vuln.id();
    }

    public static String normalizeSeverity(String severity) {
        if (severity == null) {
            return "UNKNOWN";
        }
        return switch (severity.toUpperCase()) {
            case "MODERATE" -> "MEDIUM";
            default -> severity.toUpperCase();
        };
    }

    public static int severityIndex(String severity) {
        int idx = SEVERITY_ORDER.indexOf(severity);
        return idx >= 0 ? idx : SEVERITY_ORDER.size();
    }

    /** Compares two Maven versions, as Maven orders them. */
    public static int compare(String a, String b) {
        return new ComparableVersion(a).compareTo(new ComparableVersion(b));
    }

    /** {@code groupId:artifactId} of {@code groupId:artifactId:version}. */
    public static String ga(String gav) {
        int last = gav.lastIndexOf(':');
        return last > 0 && gav.indexOf(':') != last ? gav.substring(0, last) : gav;
    }

    /** The version of {@code groupId:artifactId:version}; null when there is none. */
    public static String version(String gav) {
        int first = gav.indexOf(':');
        int last = gav.lastIndexOf(':');
        return first >= 0 && last > first ? gav.substring(last + 1) : null;
    }

    /** {@code artifactId} of {@code groupId:artifactId}. */
    public static String shortArtifact(String ga) {
        if (ga == null) {
            return "";
        }
        int colon = ga.indexOf(':');
        return colon >= 0 ? ga.substring(colon + 1) : ga;
    }

    /** The chain as it is shown: the artifactIds, from the dependency of the project on. */
    public static String via(List<String> chain) {
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String ga : chain) {
            names.add(shortArtifact(ga));
        }
        return String.join(CHAIN_SEPARATOR, names);
    }
}
