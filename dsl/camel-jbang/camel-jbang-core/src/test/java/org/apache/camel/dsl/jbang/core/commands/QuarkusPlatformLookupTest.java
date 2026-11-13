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

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * When camel export looks up the Quarkus Platform in the Quarkus Extension Registry: for the Quarkus runtime, which
 * runs on it, and not for the other runtimes (CAMEL-26673).
 */
class QuarkusPlatformLookupTest extends CamelCommandBaseTestSupport {

    private static final String ROUTE = "src/test/resources/golden/yaml/hello.camel.yaml";

    // camel-spring-boot comes from another project, whose snapshot may not be deployed: see ExportTest
    private static final String RELEASED_CAMEL_VERSION = "4.13.0";

    // the Quarkus Platform that the registry of the tests has for this Camel version
    private static final String REGISTRY_QUARKUS_VERSION = "3.35.3";

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        useTestWorkDir();
    }

    @ParameterizedTest
    @EnumSource(value = RuntimeType.class, names = { "main", "springBoot" })
    void runtimeOtherThanQuarkusDoesNotNeedTheRegistry(RuntimeType runtime, @TempDir Path dir) throws Exception {
        Export command = export(runtime, dir.resolve("out"), List.of(unreachableRegistry(dir)), ROUTE);

        assertThat(command.doCall()).isZero();

        assertThat(dir.resolve("out/pom.xml")).exists();
        assertThat(runSettings()).noneMatch(line -> line.startsWith("camel.jbang.quarkus"));
    }

    @Test
    void dependencyWithTheVersionOfQuarkusDoesNotNeedTheRegistry(@TempDir Path dir) throws Exception {
        Export command = export(RuntimeType.main, dir.resolve("out"), List.of(unreachableRegistry(dir)), ROUTE,
                greeter(dir).toString());

        assertThat(command.doCall()).isZero();

        // the version of the camel-dependencies POM, not its placeholder
        assertThat(runSettings())
                .anyMatch(line -> line.matches("dependency=mvn\\\\:io\\.quarkus\\\\:quarkus-core\\\\:\\d[\\w.-]*"));
        assertThat(runSettings()).noneMatch(line -> line.startsWith("camel.jbang.quarkus"));
    }

    @Test
    void givenQuarkusVersionIsUsedWithoutTheRegistry(@TempDir Path dir) throws Exception {
        Export command = export(RuntimeType.main, dir.resolve("out"),
                List.of(unreachableRegistry(dir), "--quarkus-version=" + REGISTRY_QUARKUS_VERSION), ROUTE);

        assertThat(command.doCall()).isZero();

        assertThat(dir.resolve("out/pom.xml")).exists();
        assertThat(runSettings()).contains("camel.jbang.quarkusVersion=" + REGISTRY_QUARKUS_VERSION);
    }

    @Test
    void quarkusVersionOfApplicationPropertiesIsUsedWithoutTheRegistry(@TempDir Path dir) throws Exception {
        Path project = Files.createDirectory(dir.resolve("project"));
        Files.copy(Path.of(ROUTE), project.resolve("hello.camel.yaml"));
        Files.writeString(project.resolve("application.properties"),
                "camel.jbang.quarkusVersion=" + REGISTRY_QUARKUS_VERSION + System.lineSeparator());

        // a directory is exported with its application.properties
        Export command = export(RuntimeType.main, dir.resolve("out"), List.of(unreachableRegistry(dir)),
                project.toString());

        assertThat(command.doCall()).isZero();

        assertThat(dir.resolve("out/pom.xml")).exists();
        // the platform of the given version is resolved, which also sets its group
        assertThat(runSettings()).contains("camel.jbang.quarkusGroupId=io.quarkus.platform",
                "camel.jbang.quarkusVersion=" + REGISTRY_QUARKUS_VERSION);
    }

    @Test
    void quarkusRuntimeLooksUpItsPlatform(@TempDir Path dir) throws Exception {
        Export command = export(RuntimeType.quarkus, dir.resolve("out"),
                List.of(CamelCommandBaseTestSupport.quarkusExtRegistry()), ROUTE);

        assertThat(command.doCall()).isZero();

        assertThat(dir.resolve("out/pom.xml")).exists();
        assertThat(runSettings()).contains("camel.jbang.quarkusGroupId=io.quarkus.platform",
                "camel.jbang.quarkusVersion=" + REGISTRY_QUARKUS_VERSION);
    }

    @Test
    void quarkusRuntimeNeedsTheRegistry(@TempDir Path dir) {
        Export command = export(RuntimeType.quarkus, dir.resolve("out"), List.of(unreachableRegistry(dir)), ROUTE);

        assertThatThrownBy(command::doCall)
                .hasMessageContaining("Could not fetch from " + dir.resolve("registry").toUri());
        assertThat(dir.resolve("out/pom.xml")).doesNotExist();
    }

    @Test
    void quarkusExportLeavesTheVersionOfQuarkusDependenciesToThePlatform(@TempDir Path dir) throws Exception {
        // the run data of another runtime has io.quarkus:quarkus-core in the version of the camel-dependencies POM
        Export main = export(RuntimeType.main, dir.resolve("main"), List.of(unreachableRegistry(dir)), ROUTE,
                greeter(dir).toString());
        assertThat(main.doCall()).isZero();

        // an export without files reuses the run data
        Export quarkus = export(RuntimeType.quarkus, dir.resolve("quarkus"),
                List.of(CamelCommandBaseTestSupport.quarkusExtRegistry()));
        assertThat(quarkus.doCall()).isZero();

        Model pom = readPom(dir.resolve("quarkus/pom.xml"));
        assertThat(pom.getProperties()).containsEntry("quarkus.platform.version", REGISTRY_QUARKUS_VERSION);
        assertThat(quarkusDependency(pom, "quarkus-core").getVersion()).isNull();
    }

    @Test
    void quarkusExportKeepsTheVersionOfAGivenQuarkusDependency(@TempDir Path dir) throws Exception {
        // given to a run: the run data has it in camel.jbang.dependencies
        Export main = export(RuntimeType.main, dir.resolve("main"),
                List.of(unreachableRegistry(dir), "--dep=io.quarkus:quarkus-given-to-the-run:1.0"), ROUTE);
        assertThat(main.doCall()).isZero();

        // given to the export, which has no files and reuses the run data
        Export quarkus = export(RuntimeType.quarkus, dir.resolve("quarkus"),
                List.of(CamelCommandBaseTestSupport.quarkusExtRegistry(),
                        "--dep=io.quarkus:quarkus-given-to-the-export:2.0"));
        assertThat(quarkus.doCall()).isZero();

        Model pom = readPom(dir.resolve("quarkus/pom.xml"));
        assertThat(quarkusDependency(pom, "quarkus-given-to-the-run").getVersion()).isEqualTo("1.0");
        assertThat(quarkusDependency(pom, "quarkus-given-to-the-export").getVersion()).isEqualTo("2.0");
    }

    private static Model readPom(Path pom) throws Exception {
        try (Reader reader = Files.newBufferedReader(pom)) {
            return new MavenXpp3Reader().read(reader);
        }
    }

    private static Dependency quarkusDependency(Model pom, String artifactId) {
        List<Dependency> found = pom.getDependencies().stream()
                .filter(d -> "io.quarkus".equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()))
                .toList();
        assertThat(found).as("io.quarkus:%s in the pom.xml", artifactId).hasSize(1);
        return found.get(0);
    }

    /**
     * A bean with jakarta.inject.Named, which comes with io.quarkus:quarkus-core: a known dependency that has the
     * version of Quarkus.
     */
    private static Path greeter(Path dir) throws Exception {
        Path bean = dir.resolve("Greeter.java");
        Files.writeString(bean, """
                import jakarta.inject.Named;

                @Named("greeter")
                public class Greeter {

                    public String hello() {
                        return "Hello";
                    }
                }
                """);
        return bean;
    }

    /**
     * A Quarkus Extension Registry that gives no answer: the file of a registry that does not exist, so the test needs
     * no network.
     */
    private static String unreachableRegistry(Path dir) {
        return "--quarkus-ext-registry=" + dir.resolve("registry").toUri();
    }

    private static Export export(RuntimeType runtime, Path out, List<String> options, String... files) {
        List<String> args = new ArrayList<>(
                List.of("--runtime=" + runtime.runtime(), "--gav=org.example:lookup:1.0.0", "--dir=" + out, "--quiet"));
        args.addAll(options);
        if (runtime == RuntimeType.springBoot) {
            args.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        Export command = new Export(new CamelJBangMain());
        // parsed at once: an option that is not given again is set to its default
        CommandLineTestHelper.parse(command, args.toArray(new String[0]));
        // the export adds application.properties to the files when the directory has one
        command.files = new ArrayList<>(List.of(files));
        return command;
    }

    private static List<String> runSettings() throws Exception {
        return Files.readAllLines(CommandLineHelper.getWorkDir().resolve(ExportHelper.RUN_SETTINGS_FILE));
    }
}
