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

class MigrationToolsTest {

    private final MigrationTools tools;

    MigrationToolsTest() {
        tools = new MigrationTools();
        tools.migrationData = new MigrationData();
        tools.catalogService = new CatalogService();
    }

    // a Spring Boot application packaged as a WAR for Tomcat: the BOM and a managed-only component declare nothing,
    // the plugin dependency is not a project dependency
    private static final String SPRING_BOOT_WAR_POM = """
            <project>
                <packaging>war</packaging>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.0</version>
                </parent>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.camel.springboot</groupId>
                            <artifactId>camel-spring-boot-bom</artifactId>
                            <version>4.18.0</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.camel.springboot</groupId>
                            <artifactId>camel-jms-starter</artifactId>
                            <version>4.18.0</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.camel.springboot</groupId>
                        <artifactId>camel-kafka-starter</artifactId>
                    </dependency>
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>maven-war-plugin</artifactId>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.camel</groupId>
                            <artifactId>camel-maven-plugin</artifactId>
                            <dependencies>
                                <dependency>
                                    <groupId>org.apache.camel</groupId>
                                    <artifactId>camel-xml-io</artifactId>
                                </dependency>
                            </dependencies>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

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

    // ---- Runtime detection ----

    @Test
    void springBootWarIsSpringBootAndOnlyDeclaredDependenciesCount() {
        MigrationTools.ProjectAnalysisResult result = tools.camel_migration_analyze(SPRING_BOOT_WAR_POM, null);

        assertThat(result.runtimeType()).isEqualTo("spring-boot");
        assertThat(result.camelVersion()).isEqualTo("4.18.0");
        assertThat(result.camelComponents()).containsExactly("camel-kafka-starter");
        assertThat(result.nextStep()).contains("camel_migration_compatibility");
    }

    @Test
    void pluginsAreRuntimeMarkers() {
        String plainWar = """
                <project>
                    <properties><camel.version>3.20.0</camel.version></properties>
                    <build><plugins><plugin><artifactId>maven-war-plugin</artifactId></plugin></plugins></build>
                </project>
                """;
        String bundle = """
                <project>
                    <properties><camel.version>3.20.0</camel.version></properties>
                    <build><plugins><plugin>
                        <groupId>org.apache.felix</groupId><artifactId>maven-bundle-plugin</artifactId>
                    </plugin></plugins></build>
                </project>
                """;

        assertThat(tools.camel_migration_analyze(plainWar, null).runtimeType()).isEqualTo("wildfly");
        assertThat(tools.camel_migration_analyze(bundle, null).runtimeType()).isEqualTo("karaf");
    }

    @Test
    void quarkusSpringBootPropertiesIsNotSpringBoot() {
        // a Camel Quarkus project with the Quarkus extension for Spring Boot properties: Spring Boot is told by the group
        String pom = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.quarkus.platform</groupId>
                                <artifactId>quarkus-camel-bom</artifactId>
                                <version>3.27.1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.camel.quarkus</groupId>
                            <artifactId>camel-quarkus-kafka</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-spring-boot-properties</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertThat(tools.camel_migration_analyze(pom, null).runtimeType()).isEqualTo("quarkus");
    }

    // ---- Compatibility and recipes ----

    @Test
    void compatibilityTakesTheJavaRequirementFromTheReleaseData() {
        // the catalog's release data: 4.20 supports Java 17, 21 and 25; 4.0 needs Java 17
        MigrationTools.CompatibilityResult java17 = tools.camel_migration_compatibility("4.18.0", "4.20.0", "main", "17");
        assertThat(java17.compatible()).isTrue();
        assertThat(java17.javaCompatibility().requiredVersion()).isEqualTo("17");
        assertThat(java17.javaCompatibility().supportedVersions()).isEqualTo("17, 21, 25");

        MigrationTools.CompatibilityResult java11 = tools.camel_migration_compatibility("3.20.0", "4.0.0", "main", "11");
        assertThat(java11.compatible()).isFalse();
        assertThat(java11.blockers()).singleElement().asString().contains("requires Java 17+");
    }

    @Test
    void compatibilityHasNoJavaRequirementForAVersionNewerThanTheCatalog() {
        // the catalog only learns a release once it is out: the next minor version may drop a JDK version (as 4.24
        // drops Java 17), so the newest release must not tell its Java requirement
        String latest = CamelReleases.latest(tools.catalogService.getDefaultCatalog());
        String[] parts = latest.split("\\.");
        String nextMinor = parts[0] + "." + (Integer.parseInt(parts[1]) + 1) + ".0";

        MigrationTools.CompatibilityResult result = tools.camel_migration_compatibility(latest, nextMinor, "main", "17");
        assertThat(result.javaCompatibility()).isNull();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).singleElement().asString()
                .contains("no release data for Camel " + nextMinor).contains("camel_version_list");

        // a patch release of a known minor version takes the Java requirement of that minor version
        String patch = parts[0] + "." + parts[1] + ".99";
        MigrationTools.CompatibilityResult samePatch = tools.camel_migration_compatibility(latest, patch, "main", "17");
        assertThat(samePatch.javaCompatibility()).isNotNull();
        assertThat(samePatch.warnings()).noneMatch(w -> w.contains("no release data"));
    }

    @Test
    void recipesUseTheRecipeOfEachArtifact() {
        MigrationTools.MigrationRecipesResult main
                = tools.camel_migration_recipes("main", "4.14.0", "4.22.1", "17", null, null);
        assertThat(main.mavenCommands()).singleElement().asString()
                .contains("rewrite-maven-plugin:dryRun ")
                .contains("org.apache.camel.upgrade:camel-upgrade-recipes:4.22.0 ")
                .endsWith("-Drewrite.activeRecipes=org.apache.camel.upgrade.CamelMigrationRecipe");
        assertThat(main.javaUpgradeSuggestions()).isEmpty();

        MigrationTools.MigrationRecipesResult springBoot
                = tools.camel_migration_recipes("spring-boot", "4.14.0", "4.22.0", "11", false, null);
        assertThat(springBoot.mavenCommands()).singleElement().asString()
                .contains("rewrite-maven-plugin:run ")
                .contains("org.apache.camel.upgrade:camel-spring-boot-upgrade-recipes:4.22.0 ")
                .endsWith("-Drewrite.activeRecipes=org.apache.camel.upgrade.CamelSpringBootMigrationRecipe");
        assertThat(springBoot.javaUpgradeSuggestions()).singleElement().asString()
                .contains("requires Java 17+").contains("UpgradeToJava17");
    }

    @Test
    void quarkusRecipesUseTheQuarkusPlatformVersion() {
        MigrationTools.MigrationRecipesResult pinned
                = tools.camel_migration_recipes("quarkus", "4.14.0", "4.22.0", null, false, "3.27.1");
        assertThat(pinned.mavenCommands()).singleElement().asString()
                .contains("io.quarkus.platform:quarkus-maven-plugin:3.27.1:update -DplatformVersion=3.27.1 -Drewrite=true");

        MigrationTools.MigrationRecipesResult latest
                = tools.camel_migration_recipes("quarkus", "4.14.0", "4.22.0", null, null, null);
        assertThat(latest.mavenCommands()).singleElement().asString()
                .contains("io.quarkus.platform:quarkus-maven-plugin:update -DrewriteDryRun=true")
                .doesNotContain("4.22.0");
        assertThat(latest.notes()).anyMatch(n -> n.contains("camel_version_list") && n.contains("quarkusPlatformVersion"));
    }

    // ---- Guide search ----

    @Test
    void guideSearchAnswersWithTopicEntrySnippetAndNextStep() {
        MigrationTools.GuideSearchResult result = tools.camel_migration_guide_search(
                "CXF SOAP web service migration Camel 2 to 4", 5, "2.25", "4.22");

        assertThat(result.resultCount()).isPositive();
        // among the hits, not necessarily first: the exact rank depends on the wording of the in-range guides
        assertThat(result.results()).anySatisfy(hit -> {
            assertThat(hit.sectionTitle()).startsWith("Migrating Camel applications");
            assertThat(hit.snippet()).isNotBlank().containsIgnoringCase("cxf");
        });
        assertThat(result.results()).extracting(MigrationTools.GuideSnippet::version)
                .noneMatch(v -> v.equals("4.23") || v.equals("4.24"));
        assertThat(result.nextStep()).contains("camel_migration_recipes");
    }

    @Test
    void guideSearchNoMatchHintAndInvalidVersion() {
        MigrationTools.GuideSearchResult result
                = tools.camel_migration_guide_search("xyznonexistent12345", null, null, null);

        assertThat(result.resultCount()).isZero();
        assertThat(result.nextStep()).contains("Rephrase").contains("camel_catalog_doc");
        assertThatThrownBy(() -> tools.camel_migration_guide_search("cxf", 3, "latest", null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("currentVersion");
    }
}
