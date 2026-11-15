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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.maven.MavenResolutionException;

@CommandDefinition(name = "copy", description = "Copies all Camel dependencies required to run to a specific directory",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel dependency copy --output-directory=lib/" })
public class DependencyCopy extends DependencyList {

    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.fusesource.jansi", "org.apache.logging.log4j");

    @Option(name = "output-directory", description = "Directory where dependencies should be copied", defaultValue = "lib")
    protected String outputDirectory;

    private boolean outputDirectoryCreated = false;

    // the artifacts that could not be resolved or copied
    private int failures;

    // the dependencies to copy
    private final List<MavenGav> gavs = new ArrayList<>();

    public DependencyCopy(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Path dir = Paths.get(outputDirectory);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw CliException.usage("--output-directory must be a directory, was: " + outputDirectory);
        }
        int answer = super.doCall();
        if (answer == 0 && failures > 0) {
            // each artifact that failed is printed on stderr
            return CliExitCode.FAILURE;
        }
        return answer;
    }

    private void createOutputDirectory() {
        if (!outputDirectoryCreated) {
            outputDirectoryCreated = true;
            Path dir = Paths.get(outputDirectory);
            try {
                if (Files.isDirectory(dir)) {
                    // the JARs of an earlier copy, which can be of other versions, but not the other files of the
                    // directory: it is not always one that only holds the dependencies, such as with
                    // --output-directory=.
                    try (Stream<Path> files = Files.list(dir)) {
                        for (Path jar : files.filter(f -> f.getFileName().toString().endsWith(".jar")).toList()) {
                            Files.delete(jar);
                        }
                    }
                }
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw CliException.failure("Cannot create the output directory " + outputDirectory + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        // the dependencies are resolved together, once all are known, as one dependency graph: resolved one at a time,
        // each picks the versions of its own dependencies, and lib/ gets several versions of a library (CAMEL-26931)
        gavs.add(gav);
        if (index == total - 1) {
            createOutputDirectory();
            copy(resolve(gavs));
        }
    }

    // the artifacts of the dependencies, resolved together; when they cannot be, the ones that can be
    private List<MavenArtifact> resolve(List<MavenGav> dependencies) {
        try {
            return resolveTogether(dependencies);
        } catch (MavenResolutionException e) {
            // find the dependencies that cannot be resolved, which are printed on stderr, and resolve the others together
            List<MavenGav> resolvable = new ArrayList<>();
            for (MavenGav gav : dependencies) {
                try {
                    resolveTogether(List.of(gav));
                    resolvable.add(gav);
                } catch (MavenResolutionException ex) {
                    failures++;
                    printer().printErr("Error resolving the artifact: " + gav + " due to: " + ex.getMessage());
                }
            }
            if (resolvable.isEmpty()) {
                return List.of();
            }
            try {
                return resolveTogether(resolvable);
            } catch (MavenResolutionException ex) {
                failures++;
                printer().printErr("Error resolving the artifacts: " + resolvable + " due to: " + ex.getMessage());
                return List.of();
            }
        }
    }

    private List<MavenArtifact> resolveTogether(List<MavenGav> dependencies) throws MavenResolutionException {
        List<String> list = dependencies.stream().map(MavenGav::toString).toList();
        boolean snapshot = dependencies.stream().anyMatch(g -> g.getVersion() != null && g.getVersion().contains("SNAPSHOT"));
        return mavenResolver.downloader().resolveArtifacts(list, Set.of(), true, snapshot);
    }

    private void copy(List<MavenArtifact> artifacts) {
        for (MavenArtifact artifact : artifacts) {
            Path target = Paths.get(outputDirectory, artifact.getFile().getName());
            if (Files.exists(target) || EXCLUDED_GROUP_IDS.contains(artifact.getGav().getGroupId())) {
                continue;
            }
            try {
                Files.copy(artifact.getFile().toPath(), target);
            } catch (IOException e) {
                failures++;
                printer().printErr("Error copying the artifact: " + artifact.getGav() + " due to: " + e.getMessage());
            }
        }
    }

}
