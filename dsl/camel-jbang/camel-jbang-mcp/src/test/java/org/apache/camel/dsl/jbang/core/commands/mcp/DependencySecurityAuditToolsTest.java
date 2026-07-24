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

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencySecurityAuditToolsTest {

    private static final String SIMPLE_POM = """
            <project>
                <properties>
                    <camel.version>4.10.0</camel.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-http</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final String CURRENT_VERSION_POM = """
            <project>
                <properties>
                    <camel.version>4.22.0</camel.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    private final DependencySecurityAuditTools tools = createTools();

    private static DependencySecurityAuditTools createTools() {
        DependencySecurityAuditTools t = new DependencySecurityAuditTools();
        t.catalogService = new CatalogService();
        t.advisoryService = new AdvisoryService();
        return t;
    }

    @Test
    void shouldRequirePomContent() {
        assertThatThrownBy(() -> tools.camel_dependency_security_audit(null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("pomContent is required");
    }

    @Test
    void shouldAuditOlderVersionWithKnownCves() {
        DependencySecurityAuditTools.AuditResult result
                = tools.camel_dependency_security_audit(SIMPLE_POM, null, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotNull();
        assertThat(result.summary().camelVersion()).isEqualTo("4.10.0");
        assertThat(result.summary().totalDependencies()).isGreaterThan(0);
        assertThat(result.summary().totalCves()).isGreaterThan(0);
        assertThat(result.recommendations()).isNotNull();
    }

    @Test
    void shouldReportCleanForCurrentVersion() {
        DependencySecurityAuditTools.AuditResult result
                = tools.camel_dependency_security_audit(CURRENT_VERSION_POM, null, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotNull();
        assertThat(result.summary().camelVersion()).isEqualTo("4.22.0");
        assertThat(result.summary().clean()).isTrue();
    }

    @Test
    void shouldIncludeReachabilityWhenRoutesProvided() {
        String routes = "from: \"http:example.com\"\nsteps:\n  - to: \"log:out\"";
        DependencySecurityAuditTools.AuditResult result
                = tools.camel_dependency_security_audit(SIMPLE_POM, routes, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotNull();
        assertThat(result.summary().totalDependencies()).isGreaterThan(0);
        assertThat(result.summary().reachableVulnerableArtifacts()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldSanitizePomByDefault() {
        String pomWithSecret = """
                <project>
                    <properties>
                        <camel.version>4.10.0</camel.version>
                        <db.password>secret123</db.password>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.camel</groupId>
                            <artifactId>camel-core</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        DependencySecurityAuditTools.AuditResult result
                = tools.camel_dependency_security_audit(pomWithSecret, null, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.sanitizationWarnings()).isNotNull();
        assertThat(result.sanitizationWarnings()).isNotEmpty();
    }

    @Test
    void shouldRecommendUpgradeForOlderVersion() {
        DependencySecurityAuditTools.AuditResult result
                = tools.camel_dependency_security_audit(SIMPLE_POM, null, null, null, null, null);

        assertThat(result.recommendations()).isNotNull();
        assertThat(result.recommendations()).anyMatch(r -> r.contains("Upgrade"));
    }

    @Test
    void shouldHandleEmptyRoutes() {
        DependencySecurityAuditTools.AuditResult result
                = tools.camel_dependency_security_audit(SIMPLE_POM, "", null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.summary()).isNotNull();
    }
}
