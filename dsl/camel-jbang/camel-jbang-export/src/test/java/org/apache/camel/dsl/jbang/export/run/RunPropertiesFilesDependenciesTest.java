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
package org.apache.camel.dsl.jbang.export.run;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25528: camel.jbang.dependencies in a properties file given on the command line, such as
 * camel/application.properties run from the parent directory, adds the dependencies as the application.properties of
 * the current directory does.
 */
class RunPropertiesFilesDependenciesTest {

    private static RunPipeline pipeline(RunRequest request) {
        request.files.add("route.camel.yaml");
        return new RunPipeline(request);
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
        RunRequest request = new RunRequest();
        request.dependencies.add("org.example:from-option:1.0");

        // as the run command passes the files it found among the files to run
        pipeline(request).addDependenciesFromPropertiesFiles("file:" + file);

        assertThat(request.dependencies).containsExactly("org.example:from-option:1.0", "camel:spring",
                "org.example:shared:1.0", "org.example:main-only:1.0");
    }

    @Test
    void runtimeSpecificDependencies(@TempDir Path dir) throws Exception {
        Path file = properties(dir);

        RunRequest springBoot = new RunRequest();
        springBoot.runtime = RuntimeType.springBoot;
        pipeline(springBoot).addDependenciesFromPropertiesFiles(file.toString());
        assertThat(springBoot.dependencies).containsExactly("camel:spring", "org.example:shared:1.0",
                "org.example:spring-boot-only:1.0");

        RunRequest quarkus = new RunRequest();
        quarkus.runtime = RuntimeType.quarkus;
        pipeline(quarkus).addDependenciesFromPropertiesFiles(file.toUri().toString());
        assertThat(quarkus.dependencies).containsExactly("camel:spring", "org.example:shared:1.0",
                "org.example:quarkus-only:1.0");
    }

    @Test
    void dependenciesOfThePropertiesOption(@TempDir Path dir) throws Exception {
        Path file = properties(dir);
        RunRequest request = new RunRequest();
        request.propertiesFiles = file.toString();

        pipeline(request).addDependenciesFromPropertiesFiles(request.propertiesFiles);

        assertThat(request.dependencies).containsExactly("camel:spring", "org.example:shared:1.0",
                "org.example:main-only:1.0");
    }

    @Test
    void theProfilePropertiesAreNotAddedTwice(@TempDir Path dir) throws Exception {
        Path file = properties(dir);
        RunRequest request = new RunRequest();
        // the application.properties of the current directory, whose dependencies are added already
        request.profilePropertiesFiles.add(file.toAbsolutePath().normalize());

        pipeline(request).addDependenciesFromPropertiesFiles("file:" + file);

        assertThat(request.dependencies).isEmpty();
    }

    @Test
    void remoteAndMissingFilesAreSkipped(@TempDir Path dir) throws Exception {
        RunRequest request = new RunRequest();

        pipeline(request).addDependenciesFromPropertiesFiles(
                "github:apache:camel-kamelets-examples:jbang/hello-java/application.properties,classpath:app.properties,file:"
                                                             + dir.resolve("missing.properties"));

        assertThat(request.dependencies).isEmpty();
    }

    @Test
    void aFileWithoutDependencies(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("other.properties"), "greeting=Hello\n");
        RunRequest request = new RunRequest();

        pipeline(request).addDependenciesFromPropertiesFiles("file:" + file);

        assertThat(request.dependencies).isEmpty();
    }
}
