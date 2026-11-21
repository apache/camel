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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27328: what the CVE Audit tab makes of a scan: the fix of each JAR, the Camel advisories, the chains, whether
 * the routes use a JAR, and the rows by artifact.
 */
class CveScanTest {

    private static final String SNAPPY = "org.xerial.snappy:snappy-java:1.1.10.1";
    private static final String KAFKA = "org.apache.camel:camel-kafka:4.18.1";

    private static OsvClient.Vulnerability snappyVuln(String id, String severity, String fixed) {
        return new OsvClient.Vulnerability(
                id, "snappy " + id, null, severity,
                "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H", "2024-01-01", List.of(), List.of("1.0.5", fixed),
                List.of(new OsvClient.AffectedRange("org.xerial.snappy:snappy-java", null, fixed, null)));
    }

    private static SecurityAdvisoryModel kafkaAdvisory() {
        SecurityAdvisoryModel advisory = new SecurityAdvisoryModel();
        advisory.setCve("CVE-2026-49098");
        advisory.setSeverity("HIGH");
        advisory.setSummary("camel-kafka header injection");
        advisory.setAffected("From 4.0.0 before 4.14.8, from 4.15.0 before 4.18.3, from 4.19.0 before 4.21.0.");
        advisory.setFixed("4.14.8, 4.18.3 and 4.21.0");
        advisory.setMitigation("Upgrade to a fixed release.");
        advisory.setUrl("https://camel.apache.org/security/CVE-2026-49098.html");
        advisory.setComponents(List.of("camel-kafka"));
        return advisory;
    }

    @Test
    void eachJarGetsTheFixOfItsOwnRange() {
        List<CveFinding> groups = CveScan.buildCveFindings(
                Map.of(SNAPPY, List.of(snappyVuln("CVE-2024-1", "HIGH", "1.1.10.4"))), Map.of());

        assertThat(groups).singleElement().satisfies(g -> {
            assertThat(g.fixFor()).containsEntry(SNAPPY, "1.1.10.4");
            assertThat(g.score()).isEqualTo(7.5);
        });
    }

    @Test
    void aCamelAdvisoryOfACamelJarIsAddedWithTheReleaseToUpgradeTo() {
        List<Dependency> deps = List.of(
                new Dependency("org.apache.camel", "camel-kafka", "4.18.1"),
                new Dependency("org.apache.camel", "camel-http", "4.18.1"));

        List<CveFinding> groups = CveScan.applyCamelAdvisories(new ArrayList<>(), deps,
                List.of(kafkaAdvisory()));

        assertThat(groups).singleElement().satisfies(g -> {
            assertThat(g.canonicalId()).isEqualTo("CVE-2026-49098");
            assertThat(g.severity()).isEqualTo("HIGH");
            assertThat(g.affectedGavs()).containsExactly(KAFKA);
            assertThat(g.fixFor()).containsEntry(KAFKA, "4.18.3");
            assertThat(g.camelAdvisory().upgradeTo()).isEqualTo("4.18.3");
            assertThat(g.camelAdvisory().mitigation()).isEqualTo("Upgrade to a fixed release.");
        });
    }

    @Test
    void aCamelAdvisoryJoinsTheCveOsvReported() {
        OsvClient.Vulnerability osv = new OsvClient.Vulnerability(
                "GHSA-kafka", "header injection", null, "HIGH",
                null, null, List.of("CVE-2026-49098"), List.of("4.18.3"),
                List.of(new OsvClient.AffectedRange("org.apache.camel:camel-kafka", "4.15.0", "4.18.3", null)));
        List<CveFinding> groups = CveScan.buildCveFindings(Map.of(KAFKA, List.of(osv)), Map.of());

        groups = CveScan.applyCamelAdvisories(groups,
                List.of(new Dependency("org.apache.camel", "camel-kafka", "4.18.1")),
                List.of(kafkaAdvisory()));

        assertThat(groups).singleElement().satisfies(g -> {
            assertThat(g.canonicalId()).isEqualTo("CVE-2026-49098");
            assertThat(g.affectedGavs()).containsExactly(KAFKA);
            assertThat(g.camelAdvisory()).isNotNull();
        });
    }

    @Test
    void aFixedCamelVersionGetsNoAdvisory() {
        List<CveFinding> groups = CveScan.applyCamelAdvisories(new ArrayList<>(),
                List.of(new Dependency("org.apache.camel", "camel-kafka", "4.18.3")),
                List.of(kafkaAdvisory()));
        assertThat(groups).isEmpty();
    }

    @Test
    void theChainAndWhetherTheRoutesUseTheComponentThatBringsItIn() {
        List<CveFinding> groups = CveScan.buildCveFindings(
                Map.of(SNAPPY, List.of(snappyVuln("CVE-2024-1", "HIGH", "1.1.10.4"))), Map.of());
        Map<String, List<String>> chains = Map.of("org.xerial.snappy:snappy-java",
                List.of("org.apache.camel:camel-kafka", "org.apache.kafka:kafka-clients"));
        Map<String, Set<String>> schemes = Map.of("camel-kafka", Set.of("kafka"));

        CveScan.attachChains(groups, chains, Map.of(), Set.of("org.apache.camel:camel-kafka"));
        assertThat(groups.get(0).gavVia()).containsEntry(SNAPPY, "camel-kafka > kafka-clients");
        assertThat(groups.get(0).declared()).isEmpty();

        CveScan.markRoutesUsage(groups, chains, Set.of("timer", "kafka"), schemes);
        assertThat(groups.get(0).usedByRoutes()).isTrue();
        assertThat(groups.get(0).usedBy().get(SNAPPY)).isEqualTo(new CveUsage("camel-kafka", true));

        CveScan.markRoutesUsage(groups, chains, Set.of("timer"), schemes);
        assertThat(groups.get(0).usedByRoutes()).isFalse();

        // a library of the runtime: no component brings it in
        CveScan.markRoutesUsage(groups, Map.of(), Set.of("kafka"), schemes);
        assertThat(groups.get(0).usedByRoutes()).isNull();
    }

    @Test
    void byArtifactTheVersionThatFixesAllItsCves() {
        List<CveFinding> groups = CveScan.buildCveFindings(Map.of(SNAPPY, List.of(
                snappyVuln("CVE-2024-1", "MEDIUM", "1.1.10.4"),
                snappyVuln("CVE-2024-2", "HIGH", "1.1.10.6"))), Map.of());

        List<CveArtifact> rows = CveScan.artifactRows(groups);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.gav()).isEqualTo(SNAPPY);
            assertThat(r.cves()).extracting(CveFinding::canonicalId).containsExactly("CVE-2024-2", "CVE-2024-1");
            assertThat(r.severity()).isEqualTo("HIGH");
            assertThat(r.fix()).isEqualTo("1.1.10.6");
            assertThat(r.camelRelease()).isFalse();
        });
    }

    @Test
    void byArtifactNoFixWhenOneCveHasNone() {
        OsvClient.Vulnerability unfixed = new OsvClient.Vulnerability(
                "CVE-2024-3", "no fix", null, "LOW", null,
                null, List.of(), List.of());
        List<CveFinding> groups = CveScan.buildCveFindings(Map.of(SNAPPY, List.of(
                snappyVuln("CVE-2024-1", "MEDIUM", "1.1.10.4"), unfixed)), new HashMap<>());

        assertThat(CveScan.artifactRows(groups)).singleElement().satisfies(r -> assertThat(r.fix()).isNull());
    }

    @Test
    void aCamelJarWithOnlyCamelAdvisoriesIsFixedByACamelRelease() {
        List<CveFinding> groups = CveScan.applyCamelAdvisories(new ArrayList<>(),
                List.of(new Dependency("org.apache.camel", "camel-kafka", "4.18.1")),
                List.of(kafkaAdvisory()));

        assertThat(CveScan.artifactRows(groups)).singleElement().satisfies(r -> {
            assertThat(r.fix()).isEqualTo("4.18.3");
            assertThat(r.camelRelease()).isTrue();
        });
    }

    @Test
    void componentSchemesOfTheCatalog() {
        Map<String, Set<String>> schemes = CveScan.componentSchemes(new DefaultCamelCatalog());
        assertThat(schemes.get("camel-kafka")).contains("kafka");
        assertThat(schemes.get("camel-http")).contains("http", "https");
    }
}
