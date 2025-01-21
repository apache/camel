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
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;

public final class CamelUpdate implements Update {

    private List<String> commands = new ArrayList<>();
    private CamelUpdateMixin updateMixin;
    private MavenDependencyDownloader downloader;

    public CamelUpdate(CamelUpdateMixin updateMixin, MavenDependencyDownloader downloader) {
        this.updateMixin = updateMixin;
        this.downloader = downloader;
    }

    /**
     * Download the jar containing the recipes and extract the recipe name to be used in the maven command
     *
     * @return
     */
    public List<String> activeRecipes() throws CamelUpdateException {
        List<Recipe> recipes;
        MavenArtifact mavenArtifact
                = downloader.downloadArtifact("org.apache.camel.upgrade", getArtifactCoordinates(), updateMixin.version);

        try {
            recipes = getRecipesInJar(mavenArtifact.getFile());
        } catch (IOException ex) {
            throw new CamelUpdateException(ex);
        }

        List<String> activeRecipes = new ArrayList<>();
        for (Recipe recipe : recipes) {
            // The recipe named latest.yaml contains all the recipe for the update up to the selected version
            if (recipe.name().contains("latest")) {
                activeRecipes.clear();
                recipe.recipeName().ifPresent(name -> activeRecipes.add(name));
                break;
            }

            recipe.recipeName().ifPresent(name -> activeRecipes.add(name));
        }

        return activeRecipes;
    }

    private List<Recipe> getRecipesInJar(File jar) throws IOException {
        List<Recipe> recipes = new ArrayList<>();
        Path jarPath = jar.toPath();

        try (FileSystem fileSystem = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            Path recipePath = fileSystem.getPath("META-INF", "rewrite");
            if (Files.exists(recipePath)) {
                try (Stream<Path> walk = Files.walk(recipePath)) {
                    walk.filter(p -> p.toString().endsWith(".yaml"))
                            .forEach(p -> {
                                try {
                                    recipes.add(new Recipe(
                                            p.getFileName().toString(),
                                            Files.readString(p)));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                }
            }
        }
        return recipes;
    }

    @Override
    public String debug() {
        String result = "--no-transfer-progress";
        if (updateMixin.debug) {
            result = "-X";
        }

        return result;
    }

    @Override
    public String runMode() {
        String task = "run";
        if (updateMixin.dryRun) {
            task = "dryRun";
        }

        return task;
    }

    @Override
    public List<String> command() throws CamelUpdateException {
        commands.add(mvnProgramCall());
        commands.add(debug());
        commands.add("org.openrewrite.maven:rewrite-maven-plugin:" + updateMixin.openRewriteVersion + ":"
                     + runMode());

        List<String> coordinates = new ArrayList<>();
        coordinates.add(String.format("org.apache.camel.upgrade:%s:%s", getArtifactCoordinates(), updateMixin.version));
        if (updateMixin.extraRecipeArtifactCoordinates != null && !updateMixin.extraRecipeArtifactCoordinates.isEmpty()) {
            coordinates.addAll(updateMixin.extraRecipeArtifactCoordinates);
        }

        commands.add("-Drewrite.recipeArtifactCoordinates=" +
                     coordinates.stream().collect(Collectors.joining(",")));

        List<String> recipes = new ArrayList<>();
        recipes.addAll(activeRecipes());
        if (updateMixin.extraActiveRecipes != null && !updateMixin.extraActiveRecipes.isEmpty()) {
            recipes.addAll(updateMixin.extraActiveRecipes);
        }
        commands.add("-Drewrite.activeRecipes=" + recipes
                .stream().collect(Collectors.joining(",")));

        return commands;
    }

    public String getArtifactCoordinates() {
        return updateMixin.runtime == RuntimeType.springBoot
                ? updateMixin.camelSpringBootArtifactCoordinates : updateMixin.camelArtifactCoordinates;
    }

    record Recipe(String name, String content) {

        /**
         * Retrieves the name of the recipe if it is a Camel upgrade recipe.
         *
         * @return an Optional containing the recipe name if it is a Camel upgrade recipe, otherwise empty
         */
        public Optional<String> recipeName() {
            return Arrays.stream(content().split(System.lineSeparator()))
                    .filter(l -> l.startsWith("name") && l.contains("org.apache.camel.upgrade"))
                    .map(l -> l.substring(l.indexOf("org.apache.camel.upgrade")))
                    .findFirst();
        }
    }
}
