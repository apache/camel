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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25528: camel.jbang.dependencies in a properties file given on the command line, such as
 * camel/application.properties run from the parent directory, adds the dependencies as the application.properties of
 * the current directory does.
 */
class RunPropertiesFilesDependenciesTest {

    private static Run run(String... args) {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, args);
        return command;
    }

    private static Path properties(Path dir) throws Exception {
        Path camel = Files.createDirectories(dir.resolve("camel"));
        return Files.writeString(camel.resolve("application.properties"), """
                camel.jbang.dependencies=camel:spring,org.example:shared:1.0
                camel.jbang.dependencies.main=org.example:main-only:1.0
                camel.jbang.dependencies.spring-boot=org.example:spring-boot-only:1.0
                camel.jbang.dependencies.quarkus=org.example:quarkus-only:1.0
                greeting=Hello
                """);
    }

    @Test
    void dependenciesOfAPropertiesFileOnTheCommandLine(@TempDir Path dir) throws Exception {
        Path file = properties(dir);
        Run run = run("--dep=org.example:from-option:1.0", "route.camel.yaml");

        // as the run command passes the files it found among the files to run
        run.addDependenciesFromPropertiesFiles("file:" + file);

        assertThat(run.dependencies).containsExactly("org.example:from-option:1.0", "camel:spring",
                "org.example:shared:1.0", "org.example:main-only:1.0");
    }

    @Test
    void runtimeSpecificDependencies(@TempDir Path dir) throws Exception {
        Path file = properties(dir);

        Run springBoot = run("--runtime=spring-boot", "route.camel.yaml");
        springBoot.addDependenciesFromPropertiesFiles(file.toString());
        assertThat(springBoot.dependencies).containsExactly("camel:spring", "org.example:shared:1.0",
                "org.example:spring-boot-only:1.0");

        Run quarkus = run("--runtime=quarkus", "route.camel.yaml");
        quarkus.addDependenciesFromPropertiesFiles(file.toUri().toString());
        assertThat(quarkus.dependencies).containsExactly("camel:spring", "org.example:shared:1.0",
                "org.example:quarkus-only:1.0");
    }

    @Test
    void dependenciesOfThePropertiesOption(@TempDir Path dir) throws Exception {
        Path file = properties(dir);
        Run run = run("--properties=" + file, "route.camel.yaml");

        run.addDependenciesFromPropertiesFiles(run.propertiesFiles);

        assertThat(run.dependencies).containsExactly("camel:spring", "org.example:shared:1.0",
                "org.example:main-only:1.0");
    }

    @Test
    void theProfilePropertiesAreNotAddedTwice(@TempDir Path dir) throws Exception {
        Path file = properties(dir);
        Run run = run("route.camel.yaml");
        // the application.properties of the current directory, whose dependencies are added already
        run.profilePropertiesFiles.add(file.toAbsolutePath().normalize());

        run.addDependenciesFromPropertiesFiles("file:" + file);

        assertThat(run.dependencies).isEmpty();
    }

    @Test
    void remoteAndMissingFilesAreSkipped(@TempDir Path dir) throws Exception {
        Run run = run("route.camel.yaml");

        run.addDependenciesFromPropertiesFiles(
                "github:apache:camel-kamelets-examples:jbang/hello-java/application.properties,classpath:app.properties,file:"
                                               + dir.resolve("missing.properties"));

        assertThat(run.dependencies).isEmpty();
    }

    @Test
    void aFileWithoutDependencies(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("other.properties"), "greeting=Hello\n");
        Run run = run("route.camel.yaml");

        run.addDependenciesFromPropertiesFiles("file:" + file);

        assertThat(run.dependencies).isEmpty();
    }
}
