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
import java.util.stream.Stream;

import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;

public class CamelUpdate {

    protected List<String> commands = new ArrayList<>();

    /**
     * Download the jar containing the recipes and extract the recipe name to be used in the maven command
     *
     * @return
     */
    public List<String> activeRecipes(MavenDependencyDownloader downloader, String recipesArtifactId, String version) {
        List<Recipe> recipes;
        MavenArtifact mavenArtifact
                = downloader.downloadArtifact("org.apache.camel.upgrade", recipesArtifactId, version);

        try {
            recipes = getRecipesInJar(mavenArtifact.getFile());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
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

    public String debug(boolean debug) {
        String result = "--no-transfer-progress";
        if (debug) {
            result = "-X";
        }

        return result;
    }

    public String runMode(boolean dryRun) {
        String task = "run";
        if (dryRun) {
            task = "dryRun";
        }

        return task;
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
