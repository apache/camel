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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

/**
 * A command-line tool for listing available update versions for Apache Camel and its runtime variants.
 *
 * <p>
 * The command supports listing versions in both human-readable ASCII table format and JSON format. It downloads version
 * information from Maven repositories and presents available upgrade paths for different Camel runtime variants.
 * </p>
 *
 * <p>
 * Command usage: list [--repos=<repos>] [--json]
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 * <li>Lists available update versions for Plain Camel, Camel Spring Boot, and Camel Quarkus</li>
 * <li>Supports additional Maven repositories for dependency resolution</li>
 * <li>Provides output in both ASCII table and JSON formats</li>
 * <li>Includes runtime version information and upgrade descriptions</li>
 * </ul>
 *
 * <h3>Command Options:</h3>
 * <ul>
 * <li>--repos: Specifies additional Maven repositories for downloading dependencies (comma-separated)</li>
 * <li>--json: Outputs the version information in JSON format</li>
 * </ul>
 *
 * <h3>Version Support:</h3>
 * <ul>
 * <li>Plain Camel: Supports versions from 4.8.0 onwards</li>
 * <li>Spring Boot: Supports versions from 4.8.0 onwards</li>
 * <li>Quarkus: Supports versions from 4.4.0 onwards with recipes from 1.0.22</li>
 * </ul>
 *
 * @see org.apache.camel.dsl.jbang.core.commands.CamelCommand
 * @see org.apache.camel.dsl.jbang.core.commands.CamelJBangMain
 */
@CommandLine.Command(name = "list",
                     description = "List available update versions for Apache Camel and its runtime variants")
public class UpdateList extends CamelCommand {

    @CommandLine.Option(names = { "--repos" },
                        description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories)")
    String repos;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Option(names = { "--use-cache" },
                        description = "Use Maven cache")
    boolean useCache;

    private static final String CAMEL_UPGRADE_GROUPID = "org.apache.camel.upgrade";
    private static final String CAMEL_UPGRADE_ARTIFACTID = "camel-upgrade-recipes";
    private static final String CAMEL_SB_UPGRADE_ARTIFACTID = "camel-spring-boot-upgrade-recipes";
    private static final String FIRST_RECIPE_VERSION = "4.8.0";
    private static final String QUARKUS_FIRST_RECIPE_VERSION = "1.0.22";
    private static final String QUARKUS_FIRST_UPDATABLE_VERSION = "4.4.0";

    public UpdateList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();
        try (MavenDependencyDownloader downloader = new MavenDependencyDownloader();) {
            downloader.setRepositories(repos);
            downloader.setFresh(!useCache);
            downloader.start();

            RecipeVersions recipesVersions = collectRecipesVersions(downloader);

            // Convert recipes versions into Rows for table and json visualization
            recipesVersions.plainCamelRecipesVersion()
                    .forEach(l -> rows
                            .add(new Row(l[0], "Camel", "", "Migrates Apache Camel 4 application to Apache Camel " + l[0])));
            recipesVersions.camelSpringBootRecipesVersion().forEach(l -> {
                String[] runtimeVersion
                        = recipesVersions.sbVersions().stream().filter(v -> v[0].equals(l[0])).findFirst().orElseThrow();

                rows.add(new Row(
                        l[0], "Camel Spring Boot", runtimeVersion[1],
                        "Migrates Apache Camel Spring Boot 4 application to Apache Camel Spring Boot " + l[0]));
            });
            // Translate quarkus versions to Camel
            recipesVersions.camelQuarkusRecipesVersions();
            recipesVersions.quarkusUpdateRecipes().forEach(l -> {
                List<String[]> runtimeVersions = recipesVersions.qVersions().stream().filter(v -> v[1].startsWith(l.version()))
                        .collect(Collectors.toList());
                runtimeVersions.sort(Comparator.comparing(o -> o[1]));
                String[] runtimeVersion = runtimeVersions.get(runtimeVersions.size() - 1);
                // Quarkus may release patches independently, therefore, we do not know the real micro version
                String quarkusVersion = runtimeVersion[1];
                quarkusVersion = quarkusVersion.substring(0, quarkusVersion.lastIndexOf('.')) + ".x";

                rows.add(new Row(runtimeVersion[0], "Camel Quarkus", quarkusVersion, l.description()));
            });
        }

        rows.sort(Comparator.comparing(Row::version));

        if (jsonOutput) {
            printer().println(
                    Jsoner.serialize(
                            rows.stream().map(row -> Map.of(
                                    "version", row.version(),
                                    "runtime", row.runtime(),
                                    "runtimeVersion", row.runtimeVersion(),
                                    "description", row.description()))
                                    .collect(Collectors.toList())));
        } else {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("VERSION").minWidth(30).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.version()),
                    new Column().header("RUNTIME")
                            .dataAlign(HorizontalAlign.LEFT).with(r -> r.runtime()),
                    new Column().header("RUNTIME VERSION")
                            .dataAlign(HorizontalAlign.LEFT).with(r -> r.runtimeVersion()),
                    new Column().header("DESCRIPTION")
                            .dataAlign(HorizontalAlign.LEFT).with(r -> r.description()))));
        }

        return 0;
    }

    /**
     * Download camel, camel-spring-boot and quarkus upgrade-recipes dependencies and collect existing versions
     *
     * @param  downloader
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private RecipeVersions collectRecipesVersions(MavenDependencyDownloader downloader)
            throws ExecutionException, InterruptedException {
        CompletableFuture<List<String[]>> plainCamelRecipesVersionFuture
                = CompletableFuture.supplyAsync(() -> downloader.resolveAvailableVersions(
                        CAMEL_UPGRADE_GROUPID,
                        CAMEL_UPGRADE_ARTIFACTID,
                        FIRST_RECIPE_VERSION,
                        repos));

        final List<String[]> sbVersions = new ArrayList<>();
        CompletableFuture<List<String[]>> camelSpringBootRecipesVersionFuture = CompletableFuture.supplyAsync(() -> {
            List<String[]> camelSpringBootRecipesVersion = downloader.resolveAvailableVersions(
                    CAMEL_UPGRADE_GROUPID,
                    CAMEL_SB_UPGRADE_ARTIFACTID,
                    FIRST_RECIPE_VERSION,
                    repos);
            if (!camelSpringBootRecipesVersion.isEmpty()) {
                // 4.8.0 is the first version with update recipes
                sbVersions.addAll(
                        downloader.resolveAvailableVersions(
                                "org.apache.camel.springboot",
                                "camel-spring-boot",
                                FIRST_RECIPE_VERSION,
                                repos));
            }

            return camelSpringBootRecipesVersion;
        });

        final Set<QuarkusUpdates> quarkusUpdateRecipes = new LinkedHashSet<>();
        CompletableFuture<List<String[]>> camelQuarkusRecipesVersionsFuture = CompletableFuture
                .supplyAsync(() -> {
                    List<String[]> camelQuarkusRecipesVersions = downloader.resolveAvailableVersions(
                            "io.quarkus",
                            "quarkus-update-recipes",
                            QUARKUS_FIRST_RECIPE_VERSION,
                            repos);

                    for (String[] camelQuarkusRecipeVersion : camelQuarkusRecipesVersions) {
                        String version = camelQuarkusRecipeVersion[0];
                        MavenArtifact artifact = downloader.downloadArtifact("io.quarkus",
                                "quarkus-update-recipes",
                                version);

                        try {
                            quarkusUpdateRecipes.addAll(getCamelQuarkusRecipesInJar(artifact.getFile()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    return camelQuarkusRecipesVersions;
                });

        CompletableFuture<List<String[]>> qVersionsFuture
                = CompletableFuture.supplyAsync(() -> downloader.resolveAvailableVersions(
                        "org.apache.camel.quarkus",
                        "camel-quarkus-catalog",
                        QUARKUS_FIRST_UPDATABLE_VERSION,
                        repos));

        return new RecipeVersions(
                plainCamelRecipesVersionFuture.get(),
                sbVersions,
                camelSpringBootRecipesVersionFuture.get(),
                quarkusUpdateRecipes,
                camelQuarkusRecipesVersionsFuture.get(),
                qVersionsFuture.get());
    }

    record RecipeVersions(List<String[]> plainCamelRecipesVersion,
            List<String[]> sbVersions,
            List<String[]> camelSpringBootRecipesVersion,
            Set<QuarkusUpdates> quarkusUpdateRecipes,
            List<String[]> camelQuarkusRecipesVersions,
            List<String[]> qVersions) {
    }

    record Row(String version, String runtime, String runtimeVersion, String description) {
    }

    record QuarkusUpdates(String version, String description) {
    }

    /**
     * Extracts Camel Quarkus recipe information from a JAR file.
     *
     * @param  jar         The JAR file containing Quarkus update recipes
     * @return             Collection of QuarkusUpdates containing version and description information
     * @throws IOException if an error occurs while reading the JAR file
     */
    private Collection<QuarkusUpdates> getCamelQuarkusRecipesInJar(File jar) throws IOException {
        List<QuarkusUpdates> quarkusUpdateRecipes = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                String name = jarEntry.getName();
                if (name.contains("quarkus-updates/org.apache.camel.quarkus/camel-quarkus/")
                        && name.endsWith(".yaml")
                        /* Quarkus specific, maybe in the future can be removed */
                        && !name.contains("alpha")) {

                    String content = new String(jarFile.getInputStream(jarEntry).readAllBytes());
                    String description = Arrays.stream(content.split(System.lineSeparator()))
                            .filter(l -> l.startsWith("description"))
                            .map(l -> l.substring(l.indexOf(":") + 1).trim())
                            .findFirst().orElse("");

                    quarkusUpdateRecipes.add(new QuarkusUpdates(
                            name.substring(name.lastIndexOf("/") + 1, name.indexOf(".yaml")),
                            description));
                }
            }

            return quarkusUpdateRecipes;
        }
    }

}
