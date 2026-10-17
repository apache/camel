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
package org.apache.camel.dsl.jbang.core.commands.update;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel update run: the Maven command that updates a project with the Camel upgrade recipes (Camel Main and Spring
 * Boot) or the Quarkus Maven plugin (Quarkus). The command is not run, and nothing is downloaded.
 */
class UpdateRunTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path project;

    @TempDir
    Path repo;

    // what the fake downloader has, by artifact id
    private final Map<String, Path> artifacts = new HashMap<>();
    // the versions of the Camel Quarkus catalog: the Camel version and the Camel Quarkus version
    private final List<String[]> quarkusVersions = new ArrayList<>();
    // the command the update ran
    private List<String> command;

    @BeforeEach
    void pom() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
    }

    @Test
    void withoutAMavenProjectIsNotFound() throws Exception {
        Files.delete(project.resolve("pom.xml"));

        assertThat(command("4.18.0").doCall()).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).contains("No Maven project found in the current directory");
        assertThat(command).isNull();
    }

    @Test
    void camelMainRunsTheRewritePluginWithTheLatestRecipe() throws Exception {
        recipes("camel-upgrade-recipes", "4.4.yaml", "org.apache.camel.upgrade.camel44.CamelMigrationRecipe",
                "latest.yaml", "org.apache.camel.upgrade.CamelMigrationRecipe");

        assertThat(command("4.18.0").doCall()).isZero();

        assertThat(command).contains("--no-transfer-progress",
                "org.openrewrite.maven:rewrite-maven-plugin:6.0.4:run",
                "-Drewrite.recipeArtifactCoordinates=org.apache.camel.upgrade:camel-upgrade-recipes:4.18.0",
                "-Drewrite.activeRecipes=org.apache.camel.upgrade.CamelMigrationRecipe");
    }

    @Test
    void everyRecipeWithoutALatestOne() throws Exception {
        recipes("camel-upgrade-recipes", "4.4.yaml", "org.apache.camel.upgrade.camel44.CamelMigrationRecipe");

        assertThat(command("4.4.0").doCall()).isZero();

        assertThat(command).contains("-Drewrite.activeRecipes=org.apache.camel.upgrade.camel44.CamelMigrationRecipe");
    }

    @Test
    void dryRunWithDebugAndExtraRecipes() throws Exception {
        recipes("camel-upgrade-recipes", "latest.yaml", "org.apache.camel.upgrade.CamelMigrationRecipe");

        assertThat(command("4.18.0", "--dryRun", "--debug", "--openRewriteVersion=6.1.0",
                "--extraActiveRecipes=org.example.MyRecipe", "--extraRecipeArtifactCoordinates=org.example:recipes:1.0")
                .doCall()).isZero();

        assertThat(command).contains("-X", "org.openrewrite.maven:rewrite-maven-plugin:6.1.0:dryRun",
                "-Drewrite.recipeArtifactCoordinates=org.apache.camel.upgrade:camel-upgrade-recipes:4.18.0,"
                                                                                                      + "org.example:recipes:1.0",
                "-Drewrite.activeRecipes=org.apache.camel.upgrade.CamelMigrationRecipe,org.example.MyRecipe");
    }

    @Test
    void springBootUsesItsRecipes() throws Exception {
        recipes("camel-spring-boot-upgrade-recipes", "latest.yaml",
                "org.apache.camel.upgrade.CamelSpringBootMigrationRecipe");

        assertThat(command("4.18.0", "--runtime=spring-boot").doCall()).isZero();

        assertThat(command).contains(
                "-Drewrite.recipeArtifactCoordinates=org.apache.camel.upgrade:camel-spring-boot-upgrade-recipes:4.18.0",
                "-Drewrite.activeRecipes=org.apache.camel.upgrade.CamelSpringBootMigrationRecipe");
    }

    @Test
    void versionWithoutRecipesIsAFailure() throws Exception {
        assertThat(command("9.9.9").doCall()).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr())
                .contains("Cannot find the Camel upgrade recipes org.apache.camel.upgrade:camel-upgrade-recipes:9.9.9");
        assertThat(command).isNull();
    }

    @Test
    void quarkusRunsTheQuarkusUpdateOfTheStreamOfTheCamelVersion() throws Exception {
        quarkusVersions.add(new String[] { "4.14.0", "3.27.1" });
        quarkusVersions.add(new String[] { "4.18.0", "3.33.2" });

        assertThat(command("4.18.0", "--runtime=quarkus", "--quarkusMavenPluginVersion=3.33.2").doCall()).isZero();

        assertThat(command).contains("io.quarkus.platform:quarkus-maven-plugin:3.33.2:update", "-Dstream=3.33",
                "-DrewriteFullRun");
    }

    @Test
    void quarkusDryRun() throws Exception {
        quarkusVersions.add(new String[] { "4.18.0", "3.33.2" });

        assertThat(command("4.18.0", "--runtime=quarkus", "--quarkusMavenPluginVersion=3.33.2", "--dryRun").doCall())
                .isZero();

        assertThat(command).contains("-DrewriteDryRun");
    }

    @Test
    void quarkusWithoutTheCamelVersionIsAFailure() throws Exception {
        quarkusVersions.add(new String[] { "4.14.0", "3.27.1" });

        assertThat(command("4.18.0", "--runtime=quarkus", "--quarkusMavenPluginVersion=3.33.2").doCall())
                .isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).contains("Cannot determine Quarkus stream version for Camel version 4.18.0");
    }

    @Test
    void quarkusWithoutDownloadNeedsThePluginVersion() {
        UpdateRun command = command("4.18.0", "--runtime=quarkus", "--download=false");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessageContaining("--quarkusMavenPluginVersion is required with --download=false");
    }

    @Test
    void theExitCodeOfMaven() throws Exception {
        recipes("camel-upgrade-recipes", "latest.yaml", "org.apache.camel.upgrade.CamelMigrationRecipe");
        UpdateRun command = new UpdateRun(new CamelJBangMain().withPrinter(printer)) {
            @Override
            protected MavenDependencyDownloader createDownloader() {
                return downloader();
            }

            @Override
            protected int executeCommand(List<String> command) {
                return 1;
            }
        };
        CommandLine.populateCommand(command, "4.18.0");
        command.projectDir = project;

        assertThat(command.doCall()).isEqualTo(1);
    }

    private UpdateRun command(String... args) {
        UpdateRun command = new UpdateRun(new CamelJBangMain().withPrinter(printer)) {
            @Override
            protected MavenDependencyDownloader createDownloader() {
                return downloader();
            }

            @Override
            protected int executeCommand(List<String> command) {
                UpdateRunTest.this.command = command;
                return 0;
            }
        };
        CommandLine.populateCommand(command, args);
        command.projectDir = project;
        return command;
    }

    // a downloader with the recipes and versions of the test, and nothing else
    private MavenDependencyDownloader downloader() {
        return new MavenDependencyDownloader() {
            @Override
            public MavenArtifact downloadArtifact(String groupId, String artifactId, String version) {
                Path jar = artifacts.get(artifactId);
                return jar != null
                        ? new MavenArtifact(
                                MavenGav.parseGav(groupId + ":" + artifactId + ":" + version),
                                jar.toFile())
                        : null;
            }

            @Override
            public List<String[]> resolveAvailableVersions(
                    String groupId, String artifactId, String minimumVersion, String repo) {
                return quarkusVersions;
            }

            @Override
            protected void doBuild() {
            }

            @Override
            protected void doInit() {
            }

            @Override
            protected void doStart() {
            }

            @Override
            protected void doStop() {
            }
        };
    }

    // a jar of recipes: pairs of the file name and the name of the recipe in it
    private void recipes(String artifactId, String... files) throws Exception {
        Path jar = repo.resolve(artifactId + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (int i = 0; i < files.length; i += 2) {
                out.putNextEntry(new ZipEntry("META-INF/rewrite/" + files[i]));
                write(out, "---\ntype: specs.openrewrite.org/v1beta/recipe\nname: " + files[i + 1]
                           + "\ndisplayName: Migrates Camel\n");
                out.closeEntry();
            }
        }
        artifacts.put(artifactId, jar);
    }

    private static void write(OutputStream out, String text) throws Exception {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
