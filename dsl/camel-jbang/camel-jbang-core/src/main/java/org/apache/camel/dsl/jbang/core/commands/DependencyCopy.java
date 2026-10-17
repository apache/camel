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
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.maven.MavenResolutionException;
import picocli.CommandLine;

@CommandLine.Command(name = "copy",
                     description = "Copies all Camel dependencies required to run to a specific directory", sortOptions = false,
                     showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel dependency copy --output-directory=lib/" })
public class DependencyCopy extends DependencyList {

    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.fusesource.jansi", "org.apache.logging.log4j");

    private MavenDownloader downloader;

    @CommandLine.Option(names = { "--output-directory" }, description = "Directory where dependencies should be copied",
                        defaultValue = "lib", required = true)
    protected String outputDirectory;

    private boolean outputDirectoryCreated = false;

    // the artifacts that could not be resolved or copied
    private int failures;

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
        try {
            createOutputDirectory();
            List<MavenArtifact> artifacts = mavenResolver.downloader().resolveArtifacts(
                    List.of(gav.toString()), Set.of(), true, gav.getVersion().contains("SNAPSHOT"));
            for (MavenArtifact artifact : artifacts) {
                Path target = Paths.get(outputDirectory, artifact.getFile().getName());
                if (Files.exists(target) || EXCLUDED_GROUP_IDS.contains(artifact.getGav().getGroupId())) {
                    continue;
                }
                Files.copy(artifact.getFile().toPath(), target);
            }
        } catch (MavenResolutionException e) {
            failures++;
            printer().printErr("Error resolving the artifact: " + gav + " due to: " + e.getMessage());
        } catch (IOException e) {
            failures++;
            printer().printErr("Error copying the artifact: " + gav + " due to: " + e.getMessage());
        }
    }

}
