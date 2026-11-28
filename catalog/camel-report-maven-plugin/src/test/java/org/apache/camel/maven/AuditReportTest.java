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
package org.apache.camel.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-27345: camel-report:audit, the parts that need no Maven build or network.
 */
public class AuditReportTest {

    @TempDir
    Path dir;

    private static CveFinding finding(String id, String severity, String gav, String fix, String... aliases) {
        CveFinding f = new CveFinding(
                id, severity, "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H", "summary of " + id,
                null, null, new ArrayList<>(List.of(gav)), new HashMap<>(), new ArrayList<>(List.of(aliases)),
                new ArrayList<>());
        if (fix != null) {
            f.fixFor().put(gav, fix);
        }
        return f;
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String... trail) {
        DefaultArtifact a = new DefaultArtifact(
                groupId, artifactId, version, scope, "jar", null,
                new DefaultArtifactHandler("jar"));
        List<String> ids = new ArrayList<>();
        ids.add("com.acme:app:jar:1.0");
        ids.addAll(List.of(trail));
        ids.add(groupId + ":" + artifactId + ":jar:" + version);
        a.setDependencyTrail(ids);
        return a;
    }

    @Test
    void collectsTheDependenciesWithTheirChain() {
        List<Artifact> artifacts = List.of(
                artifact("org.apache.camel", "camel-kafka", "4.24.0", "compile"),
                artifact("org.apache.kafka", "kafka-clients", "3.9.0", "compile", "org.apache.camel:camel-kafka:jar:4.24.0"),
                artifact("org.xerial.snappy", "snappy-java", "1.1.10.1", "runtime",
                        "org.apache.camel:camel-kafka:jar:4.24.0", "org.apache.kafka:kafka-clients:jar:3.9.0"),
                artifact("org.junit.jupiter", "junit-jupiter", "5.11.0", "test"),
                // a module of the build, and an internal library
                artifact("com.acme", "shared", "1.0", "compile"),
                artifact("com.acme.billing", "billing-core", "2.0", "compile"));

        AuditMojo.Collected c = AuditMojo.collect(artifacts, Set.of("compile", "runtime"), Set.of("com.acme:shared"),
                Set.of("com.acme"));

        assertEquals(List.of("org.apache.camel:camel-kafka:4.24.0", "org.apache.kafka:kafka-clients:3.9.0",
                "org.xerial.snappy:snappy-java:1.1.10.1"), c.deps().stream().map(d -> d.gav()).toList());
        assertNull(c.via().get("org.apache.camel:camel-kafka"));
        assertEquals("camel-kafka", c.via().get("org.apache.kafka:kafka-clients"));
        assertEquals("camel-kafka > kafka-clients", c.via().get("org.xerial.snappy:snappy-java"));
    }

    @Test
    void suppressesByIdOrAlias() throws Exception {
        Path file = dir.resolve("camel-audit-suppressions.txt");
        Files.writeString(file, """
                # the YAML comes from our own configuration only
                CVE-2022-1471 not reachable: SafeConstructor everywhere

                ghsa-599f-7c49-w659
                """);
        Map<String, String> suppressions = AuditReport.readSuppressions(file);

        assertEquals("not reachable: SafeConstructor everywhere",
                AuditReport.suppression(finding("CVE-2022-1471", "HIGH", "org.yaml:snakeyaml:1.33", "2.0"), suppressions));
        // by its GHSA alias, case-insensitive, without a reason
        assertEquals("", AuditReport.suppression(finding("CVE-2022-42889", "CRITICAL",
                "org.apache.commons:commons-text:1.9", "1.10.0", "GHSA-599f-7c49-w659"), suppressions));
        assertNull(AuditReport.suppression(finding("CVE-2025-48924", "MEDIUM", "a:b:1", null), suppressions));
        assertTrue(AuditReport.readSuppressions(dir.resolve("none.txt")).isEmpty());
    }

    @Test
    void failsOnASeverityOrAbove() {
        CveFinding medium = finding("CVE-1", "MEDIUM", "a:b:1", "2");
        assertTrue(AuditReport.atOrAbove(medium, "low"));
        assertTrue(AuditReport.atOrAbove(medium, "medium"));
        assertFalse(AuditReport.atOrAbove(medium, "high"));
        assertFalse(AuditReport.atOrAbove(medium, "none"));
        // a severity OSV.dev does not give fails the lowest level only
        assertTrue(AuditReport.atOrAbove(finding("CVE-2", "UNKNOWN", "a:b:1", null), "low"));
        assertFalse(AuditReport.atOrAbove(finding("CVE-2", "UNKNOWN", "a:b:1", null), "medium"));
    }

    @Test
    void theTableHasTheFixAndTheChain() {
        CveFinding f = finding("CVE-2023-43642", "HIGH", "org.xerial.snappy:snappy-java:1.1.10.1", "1.1.10.4");
        f.gavVia().put("org.xerial.snappy:snappy-java:1.1.10.1", "camel-kafka > kafka-clients");

        List<String> lines = AuditReport.table(List.of(f, finding("CVE-2024-3", "LOW", "com.example:lib:1.0", null)));

        assertTrue(lines.get(0).startsWith("SEVERITY  SCORE  CVE"));
        assertTrue(lines.get(1).contains("CVE-2023-43642") && lines.get(1).contains("9.8")
                && lines.get(1).contains("1.1.10.4") && lines.get(1).contains("camel-kafka > kafka-clients"));
        assertTrue(lines.get(2).contains("none"));
    }

    @Test
    void theSarifReportHasARuleAndAResultPerArtifact() {
        CveFinding f = finding("CVE-2022-42889", "CRITICAL", "org.apache.commons:commons-text:1.9", "1.10.0");
        f.affectedGavs().add("org.apache.commons:commons-text:1.8");

        JsonObject doc = AuditReport.sarif(List.of(f), "app/pom.xml", "4.24.0");

        assertEquals("2.1.0", doc.getString("version"));
        JsonObject run = (JsonObject) ((JsonArray) doc.get("runs")).get(0);
        JsonObject driver = (JsonObject) ((JsonObject) run.get("tool")).get("driver");
        JsonObject rule = (JsonObject) ((JsonArray) driver.get("rules")).get(0);
        assertEquals("CVE-2022-42889", rule.getString("id"));
        assertEquals("https://osv.dev/vulnerability/CVE-2022-42889", rule.getString("helpUri"));
        assertEquals("9.8", ((JsonObject) rule.get("properties")).getString("security-severity"));
        JsonArray results = (JsonArray) run.get("results");
        assertEquals(2, results.size());
        JsonObject result = (JsonObject) results.get(0);
        assertEquals("error", result.getString("level"));
        assertTrue(((JsonObject) result.get("message")).getString("text").contains("fixed in 1.10.0"));
        JsonObject location = (JsonObject) ((JsonArray) result.get("locations")).get(0);
        assertEquals("app/pom.xml",
                ((JsonObject) ((JsonObject) location.get("physicalLocation")).get("artifactLocation")).getString("uri"));
    }

    @Test
    void theJsonReportHasTheSuppressedFindings() {
        CveFinding listed = finding("CVE-1", "HIGH", "a:b:1", "2");
        Map<CveFinding, String> hidden = new LinkedHashMap<>();
        hidden.put(finding("CVE-2", "LOW", "c:d:1", null), "not used");

        JsonObject doc = AuditReport.json("com.acme:app", 12, List.of(listed), hidden, "high", true, null, false);

        assertEquals(12, doc.getInteger("scanned"));
        assertEquals(1, doc.getInteger("vulnerabilities"));
        assertEquals(1, doc.getInteger("suppressed"));
        assertTrue(doc.getBoolean("failed"));
        JsonObject row = (JsonObject) ((JsonArray) doc.get("suppressedFindings")).get(0);
        assertEquals("not used", row.getString("suppression"));
        assertNull(doc.get("artifacts"));
    }

    /**
     * CAMEL-27565: by artifact, as camel dependency audit --by-artifact.
     */
    @Test
    void theTableByArtifactHasTheVersionThatFixesAll() {
        String text = "org.apache.commons:commons-text:1.9";
        String lang = "org.apache.commons:commons-lang3:3.11";
        CveFinding critical = finding("CVE-2022-42889", "CRITICAL", text, "1.10.0");
        CveFinding medium = finding("CVE-2024-1", "MEDIUM", text, "1.13.0");
        CveFinding noFix = finding("CVE-2025-48924", "MEDIUM", lang, null);
        noFix.gavVia().put(lang, "commons-text");

        List<String> lines = AuditReport.artifactTable(CveScan.artifactRows(List.of(critical, medium, noFix)));

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("SEVERITY  SCORE  ARTIFACT"));
        assertTrue(lines.get(0).contains("CVES  FIXES ALL  VIA"));
        // the worst severity of the two, and the higher of the two fixes
        assertEquals("CRITICAL 9.8 " + text + " 2 1.13.0", lines.get(1).replaceAll(" +", " "));
        assertEquals("MEDIUM 9.8 " + lang + " 1 none commons-text", lines.get(2).replaceAll(" +", " "));
    }

    @Test
    void theJsonReportHasTheArtifacts() {
        CveFinding critical = finding("CVE-2022-42889", "CRITICAL", "org.apache.commons:commons-text:1.9", "1.10.0");
        CveFinding medium = finding("CVE-2024-1", "MEDIUM", "org.apache.commons:commons-text:1.9", "1.13.0");

        JsonObject doc = AuditReport.json("com.acme:app", 12, List.of(critical, medium), Map.of(), "none", false, null,
                true);

        assertEquals(2, doc.getInteger("vulnerabilities"));
        assertEquals(2, ((JsonArray) doc.get("findings")).size());
        assertEquals(1, doc.getInteger("vulnerableArtifacts"));
        JsonObject row = (JsonObject) ((JsonArray) doc.get("artifacts")).get(0);
        assertEquals("org.apache.commons:commons-text:1.9", row.getString("artifact"));
        assertEquals("CRITICAL", row.getString("severity"));
        assertEquals(2, row.getInteger("cveCount"));
        assertEquals("1.13.0", row.getString("fixesAll"));
    }
}
