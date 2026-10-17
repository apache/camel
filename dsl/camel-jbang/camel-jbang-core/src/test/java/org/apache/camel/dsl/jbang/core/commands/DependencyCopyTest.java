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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * camel dependency copy: the JARs of the dependencies of the integration, copied to a directory. The dependencies are
 * not downloaded: each resolves to a JAR of its own.
 */
@UsesWorkingDirectory
class DependencyCopyTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path work;

    @TempDir
    Path repo;

    @TempDir
    Path lib;

    @Test
    void copiesTheJarsOfTheDependencies() throws Exception {
        assertThat(command(resolver(null)).doCall()).isZero();

        assertThat(lib.resolve("camel-main-4.11.0.jar")).exists();
        assertThat(jars()).hasSizeGreaterThan(1).allMatch(name -> name.endsWith("-4.11.0.jar"));
    }

    @Test
    void replacesTheJarsOfAnEarlierCopyAndKeepsTheOtherFiles() throws Exception {
        Files.writeString(lib.resolve("camel-main-4.10.0.jar"), "older");
        Files.writeString(lib.resolve("README.txt"), "my notes");
        Files.createDirectories(lib.resolve("config"));

        assertThat(command(resolver(null)).doCall()).isZero();

        assertThat(lib.resolve("camel-main-4.10.0.jar")).doesNotExist();
        assertThat(lib.resolve("camel-main-4.11.0.jar")).exists();
        assertThat(lib.resolve("README.txt")).hasContent("my notes");
        assertThat(lib.resolve("config")).isDirectory();
    }

    @Test
    void anArtifactThatCannotBeResolvedIsAFailure() throws Exception {
        assertThat(command(resolver("camel-main")).doCall()).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr())
                .contains("Error resolving the artifact: org.apache.camel:camel-main:4.11.0 due to: not in the repository");
        // the other dependencies are copied
        assertThat(jars()).isNotEmpty().doesNotContain("camel-main-4.11.0.jar");
    }

    @Test
    void outputDirectoryThatIsAFileIsAUsageError() throws Exception {
        Path file = Files.writeString(work.resolve("lib"), "not a directory");
        DependencyCopy command = command(resolver(null));
        command.outputDirectory = file.toString();

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--output-directory must be a directory, was: " + file);
    }

    @Test
    void theOutputDirectoryIsLibByDefault() {
        DependencyCopy command = new DependencyCopy(new CamelJBangMain().withPrinter(printer));

        CommandLine.populateCommand(command);

        assertThat(command.outputDirectory).isEqualTo("lib");
    }

    private DependencyCopy command(MavenDownloader downloader) {
        DependencyCopy command = new DependencyCopy(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "--gav=examples:route:1.0.0", "--dir=" + work, "--quiet",
                "--runtime=camel-main", "--camel-version=4.11.0", "--quarkus-version=3.22.2",
                CamelCommandBaseTestSupport.quarkusExtRegistry(), "--output-directory=" + lib);
        command.files = List.of("classpath:route.yaml");
        command.mavenResolver = new MavenResolverMixin() {
            @Override
            public MavenDownloader downloader() {
                return downloader;
            }
        };
        return command;
    }

    // resolves each dependency to a JAR of its own, except the one with the artifact id that is missing (the export
    // before the copy resolves the rest as usual)
    private MavenDownloader resolver(String missing) throws Exception {
        MavenDownloader downloader = spy(new MavenResolverMixin().downloader());
        doAnswer(inv -> {
            List<String> gavs = inv.getArgument(0);
            MavenGav gav = MavenGav.parseGav(gavs.get(0));
            if (gav.getArtifactId().equals(missing)) {
                throw new MavenResolutionException("not in the repository");
            }
            Path jar = repo.resolve(gav.getArtifactId() + "-" + gav.getVersion() + ".jar");
            if (!Files.exists(jar)) {
                Files.writeString(jar, gav.toString());
            }
            return List.of(new MavenArtifact(gav, jar.toFile()));
        }).when(downloader).resolveArtifacts(anyList(), anySet(), anyBoolean(), anyBoolean());
        return downloader;
    }

    private List<String> jars() throws Exception {
        try (Stream<Path> files = Files.list(lib)) {
            return files.map(f -> f.getFileName().toString()).filter(name -> name.endsWith(".jar")).toList();
        }
    }
}
