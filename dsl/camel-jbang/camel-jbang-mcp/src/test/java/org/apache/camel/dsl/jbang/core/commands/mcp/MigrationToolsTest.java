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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationToolsTest {

    private final MigrationTools tools;

    MigrationToolsTest() {
        tools = new MigrationTools();
        tools.migrationData = new MigrationData();
    }

    private static final String CLEAN_POM = """
            <project>
                <properties>
                    <camel.version>3.20.0</camel.version>
                    <maven.compiler.release>17</maven.compiler.release>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                        <version>${camel.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final String POM_WITH_SENSITIVE_DATA = """
            <project>
                <properties>
                    <camel.version>3.20.0</camel.version>
                    <maven.compiler.release>17</maven.compiler.release>
                    <db.password>superSecret123</db.password>
                    <api.token>tok_abc123xyz</api.token>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                        <version>${camel.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    // ---- POM sanitization ----

    @Test
    void sanitizationMasksSensitiveData() {
        MigrationTools.ProjectAnalysisResult result = tools.camel_migration_analyze(POM_WITH_SENSITIVE_DATA, null);

        assertThat(result.warnings()).anyMatch(w -> w.contains("db.password"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("api.token"));
    }

    @Test
    void sanitizationDisabledWhenFalse() {
        MigrationTools.ProjectAnalysisResult result = tools.camel_migration_analyze(POM_WITH_SENSITIVE_DATA, false);

        // No sanitization warnings when disabled
        assertThat(result.warnings()).noneMatch(w -> w.contains("Sensitive data detected"));
    }

    @Test
    void analysisWorksAfterSanitization() {
        MigrationTools.ProjectAnalysisResult result = tools.camel_migration_analyze(POM_WITH_SENSITIVE_DATA, null);

        assertThat(result.camelVersion()).isEqualTo("3.20.0");
        assertThat(result.runtimeType()).isEqualTo("main");
    }

    @Test
    void cleanPomHasNoSanitizationWarnings() {
        MigrationTools.ProjectAnalysisResult result = tools.camel_migration_analyze(CLEAN_POM, null);

        assertThat(result.warnings()).noneMatch(w -> w.contains("Sensitive data detected"));
    }
}
