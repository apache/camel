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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencySecurityAuditToolsTest {

    private DependencySecurityAuditTools createTools() {
        DependencySecurityAuditTools tools = new DependencySecurityAuditTools();
        tools.dependencyData = new DependencyData();
        return tools;
    }

    // ---- Input validation ----

    @Test
    void nullPomThrowsException() {
        DependencySecurityAuditTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_dependency_security_audit(null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    @Test
    void blankPomThrowsException() {
        DependencySecurityAuditTools tools = createTools();
        assertThatThrownBy(() -> tools.camel_dependency_security_audit("  ", null))
                .isInstanceOf(ToolCallException.class);
    }

    // ---- Dependency parsing ----

    @Test
    void parsesBasicDependencies() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-core</artifactId>
                      <version>4.22.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).hasSize(2);
        assertThat(deps).anyMatch(d -> "camel-core".equals(d.artifactId()) && d.isCamelDependency());
        assertThat(deps).anyMatch(d -> "jackson-databind".equals(d.artifactId()) && !d.isCamelDependency());
    }

    @Test
    void skipsDependencyManagementEntries() throws Exception {
        String pom = """
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-bom</artifactId>
                        <version>4.22.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-kafka</artifactId>
                      <version>4.22.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).artifactId()).isEqualTo("camel-kafka");
    }

    @Test
    void skipsTestScopeDependencies() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-kafka</artifactId>
                      <version>4.22.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.10.0</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).artifactId()).isEqualTo("camel-kafka");
    }

    @Test
    void resolvesPropertyPlaceholders() throws Exception {
        String pom = """
                <project>
                  <properties>
                    <jackson.version>2.17.0</jackson.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>${jackson.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).version()).isEqualTo("2.17.0");
    }

    @Test
    void marksCamelDependenciesCorrectly() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-kafka</artifactId>
                      <version>4.22.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel.quarkus</groupId>
                      <artifactId>camel-quarkus-core</artifactId>
                      <version>3.8.0</version>
                    </dependency>
                    <dependency>
                      <groupId>io.vertx</groupId>
                      <artifactId>vertx-core</artifactId>
                      <version>4.5.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).hasSize(3);
        assertThat(deps.get(0).isCamelDependency()).isTrue();
        assertThat(deps.get(1).isCamelDependency()).isTrue();
        assertThat(deps.get(2).isCamelDependency()).isFalse();
    }

    @Test
    void marksCoreTransitiveDependenciesCorrectly() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-core</artifactId>
                      <version>4.22.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-kafka</artifactId>
                      <version>4.22.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).anyMatch(d -> "camel-core".equals(d.artifactId()) && d.isCoreTransitive());
        assertThat(deps).anyMatch(d -> "camel-kafka".equals(d.artifactId()) && !d.isCoreTransitive());
    }

    // ---- OSS Index response parsing ----

    @Test
    void parsesOssIndexResponseWithVulnerabilities() throws Exception {
        String responseBody = """
                [
                  {
                    "coordinates": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.0",
                    "vulnerabilities": [
                      {
                        "id": "sonatype-2022-1234",
                        "displayName": "CVE-2022-42003",
                        "title": "Deserialization vulnerability",
                        "description": "A deserialization flaw in jackson-databind allows remote code execution.",
                        "cvssScore": 9.8,
                        "cvssVector": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
                        "cve": "CVE-2022-42003",
                        "cwe": "CWE-502",
                        "reference": "https://ossindex.sonatype.org/vulnerability/sonatype-2022-1234"
                      }
                    ]
                  }
                ]
                """;

        DependencySecurityAuditTools tools = createTools();
        Map<String, DependencySecurityAuditTools.Dependency> coordMap = new LinkedHashMap<>();
        coordMap.put("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.0",
                new DependencySecurityAuditTools.Dependency(
                        "com.fasterxml.jackson.core", "jackson-databind", "2.13.0", false, false));

        List<DependencySecurityAuditTools.VulnerabilityFinding> findings
                = tools.parseOssIndexResponse(responseBody, coordMap);

        assertThat(findings).hasSize(1);
        DependencySecurityAuditTools.VulnerabilityFinding f = findings.get(0);
        assertThat(f.cve()).isEqualTo("CVE-2022-42003");
        assertThat(f.severity()).isEqualTo("critical");
        assertThat(f.cvssScore()).isEqualTo(9.8);
        assertThat(f.artifactId()).isEqualTo("jackson-databind");
        assertThat(f.isCamelDependency()).isFalse();
    }

    @Test
    void parsesResponseWithNoVulnerabilities() throws Exception {
        String responseBody = """
                [
                  {
                    "coordinates": "pkg:maven/org.apache.camel/camel-core@4.22.0",
                    "vulnerabilities": []
                  }
                ]
                """;

        DependencySecurityAuditTools tools = createTools();
        Map<String, DependencySecurityAuditTools.Dependency> coordMap = new LinkedHashMap<>();
        coordMap.put("pkg:maven/org.apache.camel/camel-core@4.22.0",
                new DependencySecurityAuditTools.Dependency(
                        "org.apache.camel", "camel-core", "4.22.0", true, true));

        List<DependencySecurityAuditTools.VulnerabilityFinding> findings
                = tools.parseOssIndexResponse(responseBody, coordMap);

        assertThat(findings).isEmpty();
    }

    @Test
    void parsesMultipleVulnerabilitiesForSameComponent() throws Exception {
        String responseBody = """
                [
                  {
                    "coordinates": "pkg:maven/io.netty/netty-handler@4.1.86.Final",
                    "vulnerabilities": [
                      {
                        "id": "v1",
                        "displayName": "CVE-2023-34462",
                        "title": "DoS via SniHandler",
                        "cvssScore": 6.5,
                        "cve": "CVE-2023-34462"
                      },
                      {
                        "id": "v2",
                        "displayName": "CVE-2023-44487",
                        "title": "HTTP/2 Rapid Reset",
                        "cvssScore": 7.5,
                        "cve": "CVE-2023-44487"
                      }
                    ]
                  }
                ]
                """;

        DependencySecurityAuditTools tools = createTools();
        Map<String, DependencySecurityAuditTools.Dependency> coordMap = new LinkedHashMap<>();
        coordMap.put("pkg:maven/io.netty/netty-handler@4.1.86.Final",
                new DependencySecurityAuditTools.Dependency(
                        "io.netty", "netty-handler", "4.1.86.Final", false, false));

        List<DependencySecurityAuditTools.VulnerabilityFinding> findings
                = tools.parseOssIndexResponse(responseBody, coordMap);

        assertThat(findings).hasSize(2);
    }

    // ---- Severity scoring ----

    @Test
    void scoreSeverityMapsCorrectly() {
        assertThat(DependencySecurityAuditTools.scoreSeverity(10.0)).isEqualTo("critical");
        assertThat(DependencySecurityAuditTools.scoreSeverity(9.0)).isEqualTo("critical");
        assertThat(DependencySecurityAuditTools.scoreSeverity(8.5)).isEqualTo("high");
        assertThat(DependencySecurityAuditTools.scoreSeverity(7.0)).isEqualTo("high");
        assertThat(DependencySecurityAuditTools.scoreSeverity(5.0)).isEqualTo("medium");
        assertThat(DependencySecurityAuditTools.scoreSeverity(4.0)).isEqualTo("medium");
        assertThat(DependencySecurityAuditTools.scoreSeverity(3.0)).isEqualTo("low");
        assertThat(DependencySecurityAuditTools.scoreSeverity(0.1)).isEqualTo("low");
        assertThat(DependencySecurityAuditTools.scoreSeverity(0.0)).isEqualTo("none");
    }

    // ---- Empty POM ----

    @Test
    void emptyDependenciesSectionReturnsEmptyResult() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).isEmpty();
    }

    @Test
    void pomWithNoDependenciesSectionReturnsEmpty() throws Exception {
        String pom = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>my-app</artifactId>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).isEmpty();
    }

    // ---- Dependencies without version (BOM-managed) ----

    @Test
    void dependenciesWithoutVersionAreIncluded() throws Exception {
        String pom = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-kafka</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """;

        DependencySecurityAuditTools tools = createTools();
        List<DependencySecurityAuditTools.Dependency> deps = tools.parseDependencies(pom);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).version()).isNull();
    }
}
