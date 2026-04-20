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

class MigrationWildflyKarafToolsTest {

    private final MigrationWildflyKarafTools tools;

    MigrationWildflyKarafToolsTest() {
        tools = new MigrationWildflyKarafTools();
        tools.migrationData = new MigrationData();
    }

    // A WildFly POM with sensitive data
    private static final String WILDFLY_POM_WITH_SENSITIVE_DATA = """
            <project>
                <properties>
                    <camel.version>2.25.0</camel.version>
                    <db.password>superSecret123</db.password>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                        <version>${camel.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-cdi</artifactId>
                        <version>${camel.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    // A clean WildFly POM
    private static final String WILDFLY_POM_CLEAN = """
            <project>
                <properties>
                    <camel.version>2.25.0</camel.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-core</artifactId>
                        <version>${camel.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.camel</groupId>
                        <artifactId>camel-cdi</artifactId>
                        <version>${camel.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    // ---- POM sanitization ----

    @Test
    void sanitizationMasksSensitiveData() {
        MigrationWildflyKarafTools.WildflyKarafMigrationResult result
                = tools.camel_migration_wildfly_karaf(WILDFLY_POM_WITH_SENSITIVE_DATA, null, "4.18.0", null);

        assertThat(result.warnings()).anyMatch(w -> w.contains("db.password"));
    }

    @Test
    void sanitizationDisabledWhenFalse() {
        MigrationWildflyKarafTools.WildflyKarafMigrationResult result
                = tools.camel_migration_wildfly_karaf(WILDFLY_POM_WITH_SENSITIVE_DATA, null, "4.18.0", false);

        // No sanitization warnings when disabled
        assertThat(result.warnings()).noneMatch(w -> w.contains("Sensitive data detected"));
    }

    @Test
    void analysisWorksAfterSanitization() {
        MigrationWildflyKarafTools.WildflyKarafMigrationResult result
                = tools.camel_migration_wildfly_karaf(WILDFLY_POM_WITH_SENSITIVE_DATA, null, "4.18.0", null);

        assertThat(result.sourceRuntime()).isEqualTo("wildfly");
        assertThat(result.sourceCamelVersion()).isEqualTo("2.25.0");
        assertThat(result.targetRuntime()).isEqualTo("quarkus");
    }

    @Test
    void cleanPomHasNoSanitizationWarnings() {
        MigrationWildflyKarafTools.WildflyKarafMigrationResult result
                = tools.camel_migration_wildfly_karaf(WILDFLY_POM_CLEAN, null, "4.18.0", null);

        assertThat(result.warnings()).noneMatch(w -> w.contains("Sensitive data detected"));
    }
}
