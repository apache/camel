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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "copy",
                     description = "Copies all Camel dependencies required to run to a specific directory")
public class DependencyCopy extends DependencyList {
    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.fusesource.jansi", "org.apache.logging.log4j");

    private MavenDownloader downloader;

    @CommandLine.Option(names = { "--output-directory" }, description = "Directory where dependencies should be copied",
                        defaultValue = "lib", required = true)
    protected String outputDirectory;

    public DependencyCopy(CamelJBangMain main) {
        super(main);
    }

    private void createOutputDirectory() {
        Path outputDirectoryPath = Paths.get(outputDirectory);
        if (Files.exists(outputDirectoryPath)) {
            if (Files.isDirectory(outputDirectoryPath)) {
                FileUtil.removeDir(outputDirectoryPath.toFile());
            } else {
                System.err.println("Error creating the output directory: " + outputDirectory
                                   + " is not a directory");
                return;
            }
        }
        try {
            Files.createDirectories(outputDirectoryPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Error creating the output directory: " + outputDirectory, e);
        }
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        try {
            List<MavenArtifact> artifacts = getDownloader().resolveArtifacts(
                    List.of(gav.toString()), Set.of(), true, gav.getVersion().contains("SNAPSHOT"));
            for (MavenArtifact artifact : artifacts) {
                Path target = Paths.get(outputDirectory, artifact.getFile().getName());
                if (Files.exists(target) || EXCLUDED_GROUP_IDS.contains(artifact.getGav().getGroupId())) {
                    continue;
                }
                Files.copy(artifact.getFile().toPath(), target);
            }
        } catch (MavenResolutionException e) {
            System.err.println("Error resolving the artifact: " + gav + " due to: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error copying the artifact: " + gav + " due to: " + e.getMessage());
        }
    }

    private MavenDownloader getDownloader() {
        if (downloader == null) {
            init();
        }
        return downloader;
    }

    private void init() {
        this.downloader = new MavenDownloaderImpl();
        ((MavenDownloaderImpl) downloader).build();
        createOutputDirectory();
    }
}
