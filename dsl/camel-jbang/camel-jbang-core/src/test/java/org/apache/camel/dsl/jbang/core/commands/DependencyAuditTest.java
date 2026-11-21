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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.catalog.cve.CveAudit;
import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.catalog.cve.Dependency;
import org.apache.camel.catalog.cve.OsvDatabase;
import org.apache.camel.catalog.cve.VulnerabilitySource;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * CAMEL-27340: camel dependency audit, the parts that need no network or running integration.
 */
class DependencyAuditTest {

    private static CveFinding finding(String id, String severity, String gav, String fix) {
        CveFinding f = new CveFinding(
                id, severity, "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H", "summary of " + id,
                null, null, new ArrayList<>(List.of(gav)), new HashMap<>(), new ArrayList<>(), new ArrayList<>());
        if (fix != null) {
            f.fixFor().put(gav, fix);
        }
        return f;
    }

    @Test
    void failOnASeverityOrAbove() {
        List<CveFinding> medium = List.of(finding("CVE-1", "MEDIUM", "a:b:1", "2"));
        DependencyAudit audit = new DependencyAudit();

        audit.failOn = "low";
        assertThat(audit.failed(medium)).isTrue();
        audit.failOn = "medium";
        assertThat(audit.failed(medium)).isTrue();
        audit.failOn = "high";
        assertThat(audit.failed(medium)).isFalse();
        audit.failOn = "none";
        assertThat(audit.failed(medium)).isFalse();
        audit.failOn = "low";
        assertThat(audit.failed(List.of())).isFalse();
        // a severity OSV.dev does not give fails the lowest level only
        assertThat(audit.failed(List.of(finding("CVE-2", "UNKNOWN", "a:b:1", null)))).isTrue();
    }

    @Test
    void theJsonReportTellsWhetherTheAuditFailsWhenSeverityHidesFindings() {
        CveFinding low = finding("CVE-3", "LOW", "a:b:1", "2");
        CveAudit.Result result = new CveAudit.Result(List.of(low), CveScan.artifactRows(List.of(low)), null);
        DependencyAudit audit = new DependencyAudit();
        audit.severity = "critical";
        audit.failOn = "low";

        // --severity=critical lists none, but --fail-on=low fails on the LOW one
        JsonObject doc = audit.jsonReport("project", 5, result, List.of(), List.of());
        assertThat(doc.getInteger("vulnerabilities")).isZero();
        assertThat(doc.getInteger("totalVulnerabilities")).isEqualTo(1);
        assertThat(doc.getBoolean("failed")).isTrue();
        assertThat(doc.getString("severity")).isEqualTo("critical");
        assertThat(doc.getString("failOn")).isEqualTo("low");

        audit.failOn = "none";
        assertThat(audit.jsonReport("project", 5, result, List.of(), List.of()).getBoolean("failed")).isFalse();
    }

    @Test
    void aSeverityOptionMustBeALevel() {
        DependencyAudit.checkLevel("--severity", "HIGH", false);
        DependencyAudit.checkLevel("--fail-on", "none", true);
        assertThatThrownBy(() -> DependencyAudit.checkLevel("--severity", "none", false))
                .isInstanceOf(CliException.class).hasMessageContaining("--severity must be critical, high, medium, low");
        assertThatThrownBy(() -> DependencyAudit.checkLevel("--fail-on", "bad", true))
                .isInstanceOf(CliException.class).hasMessageContaining("or none, was: bad");
    }

    @Test
    void theTablesHaveTheFixTheChainAndTheUsage() {
        CveFinding f = finding("CVE-2023-43642", "HIGH", "org.xerial.snappy:snappy-java:1.1.10.1", "1.1.10.4");
        f.gavVia().put("org.xerial.snappy:snappy-java:1.1.10.1", "camel-kafka > kafka-clients");
        f.setUsedByRoutes(true);
        CveFinding unfixed = finding("CVE-2024-3", "LOW", "com.example:lib:1.0", null);

        String table = DependencyAudit.findingTable(List.of(f, unfixed));
        assertThat(table).contains("SEVERITY", "SCORE", "CVE", "ARTIFACT", "FIX", "VIA", "USED", "SUMMARY")
                .contains("CVE-2023-43642", "9.8", "1.1.10.4", "camel-kafka > kafka-clients", "yes")
                .contains("CVE-2024-3", "none");

        String byArtifact = DependencyAudit.artifactTable(CveScan.artifactRows(List.of(f, unfixed)));
        assertThat(byArtifact).contains("CVES", "FIXES ALL", "org.xerial.snappy:snappy-java:1.1.10.1", "1.1.10.4");
    }

    @Test
    void theDeclaredDependenciesAndEndpointsOfARunningIntegration() throws Exception {
        JsonObject status = (JsonObject) Jsoner.deserialize("""
                {"properties": {"properties": [
                   {"key": "camel.main.name", "value": "app"},
                   {"key": "camel.jbang.dependencies",
                    "value": "org.apache.commons:commons-text:1.9, mvn:org.yaml:snakeyaml:1.33,camel:kafka"}]},
                 "endpoints": {"endpoints": [{"uri": "kafka://orders"}, {"uri": "timer://tick"}, {"uri": "log:x"}]}}
                """);

        assertThat(DependencyAudit.declaredDependencies(status))
                .containsExactly("org.apache.commons:commons-text", "org.yaml:snakeyaml");
        assertThat(DependencyAudit.endpointComponents(status)).containsExactly("kafka", "timer", "log");
        assertThat(DependencyAudit.declaredDependencies(null)).isEmpty();
    }

    @Test
    void theClasspathHasTheJarsOfTheApplicationClassLoaderToo() throws Exception {
        JsonObject reply = (JsonObject) Jsoner.deserialize("""
                {"classpath": ["/r/a.jar", "/r/b.jar"], "applicationClasspath": ["/r/b.jar", "/r/commons-text-1.9.jar"]}
                """);
        assertThat(DependencyAudit.classpathOf(reply)).containsExactly("/r/a.jar", "/r/b.jar", "/r/commons-text-1.9.jar");
        assertThat(DependencyAudit.classpathOf(null)).isEmpty();
    }

    @Test
    void theOfflineOsvDatabaseOfAFile(@TempDir Path dir) throws IOException {
        // CAMEL-27344: a ZIP on disk, as a mirror of the OSV export
        Path zip = dir.resolve("all.zip");
        try (OutputStream file = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(file)) {
            out.putNextEntry(new ZipEntry("GHSA-599f-7c49-w659.json"));
            out.write("""
                    {"id": "GHSA-599f-7c49-w659", "aliases": ["CVE-2022-42889"], "summary": "Arbitrary code execution",
                     "affected": [{"package": {"name": "org.apache.commons:commons-text", "ecosystem": "Maven"},
                       "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "1.5"}, {"fixed": "1.10.0"}]}]}]}
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        DependencyAudit audit = new DependencyAudit();
        audit.osvDatabaseUrl = zip.toString();

        VulnerabilitySource source = audit.vulnerabilitySource(null);

        assertThat(source).isInstanceOf(OsvDatabase.class);
        assertThat(source.queryBatch(List.of(new Dependency("org.apache.commons", "commons-text", "1.9"))))
                .extractingByKey("org.apache.commons:commons-text:1.9").asInstanceOf(LIST).hasSize(1);
        CveAudit.Result result = new CveAudit.Result(List.of(), List.of(), null);
        JsonObject db = (JsonObject) audit.jsonReport("project", 1, result, List.of(), List.of()).get("osvDatabase");
        assertThat(db.getString("location")).isEqualTo(zip.toString());
        assertThat(db.getString("updated")).isNotBlank();

        // --offline wins
        audit.offline = true;
        assertThat(audit.vulnerabilitySource(null)).isNull();
    }
}
