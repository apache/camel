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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExportTest extends ExportBaseTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ExportTest.class);

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProject(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateProject {}", rt);
        Export command = createCommand(rt, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        // Reproducible build
        Assertions.assertNotNull(model.getProperties().getProperty("project.build.outputTimestamp"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportResourceDirs(RuntimeType rt) throws Exception {
        LOG.info("shouldExportResourceDirs {}", rt);
        // --resource-dir only accepts relative paths
        Path resources = Files.createTempDirectory("camel-export-resource-dirs");
        try {
            Path schemas = resources.resolve("schemas");
            Files.createDirectories(schemas.resolve("common"));
            Files.writeString(schemas.resolve("api.yaml"), "openapi: 3.0.3");
            Files.writeString(schemas.resolve("common/types.json"), "{}");
            String relative = Path.of("").toAbsolutePath().relativize(schemas).toString();

            Export command = createCommand(rt, new String[] { "classpath:route.yaml" },
                    "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--resource-dir=" + relative);
            int exit = command.doCall();

            assertThat(exit).isZero();
            // the last path component is the target directory, the structure below it is preserved
            Path exported = workingDir.toPath().resolve("src/main/resources/schemas");
            assertThat(exported.resolve("api.yaml")).hasContent("openapi: 3.0.3");
            assertThat(exported.resolve("common/types.json")).hasContent("{}");
        } finally {
            FileUtil.removeDir(resources.toFile());
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    void shouldExportRouteConfigurationWithStringRedeliveryDelay(RuntimeType rt) throws Exception {
        LOG.info("shouldExportRouteConfigurationWithStringRedeliveryDelay {}", rt);
        Export command = createCommand(rt,
                new String[] { "src/test/resources/route-configuration-redelivery-delay.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        assertThat(exit).isZero();
        Model model = readMavenModel();
        assertThat(model.getGroupId()).isEqualTo("examples");
        assertThat(model.getArtifactId()).isEqualTo("route");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportDifferentVersion(RuntimeType rt) throws Exception {
        LOG.info("shouldExportDifferentVersion {}", rt);
        // only test for main/spring-boot
        if (rt == RuntimeType.quarkus) {
            return;
        }
        Export command = createCommand(rt, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--camel-version=4.17.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        // Reproducible build
        Assertions.assertNotNull(model.getProperties().getProperty("project.build.outputTimestamp"));

        if (rt == RuntimeType.main) {
            assertThat(model.getDependencyManagement().getDependencies())
                    .as("Expected to find dependencyManagement entry: org.apache.camel:camel-bom:4.17.0")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-bom");
                        assertThat(dep.getVersion()).isEqualTo("4.17.0");
                    });
        } else if (rt == RuntimeType.springBoot) {
            assertThat(model.getDependencyManagement().getDependencies())
                    .as("Expected to find dependencyManagement entry: org.apache.camel.springboot:camel-spring-boot-bom:4.17.0")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel.springboot");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-spring-boot-bom");
                        assertThat(dep.getVersion()).isEqualTo("4.17.0");
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithJpaAndHibernate(RuntimeType rt) throws Exception {
        LOG.info("shouldExportWithJpaAndHibernate {}", rt);
        Export command = new Export(new CamelJBangMain());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime()), "--dep=camel:jpa"));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        cmdArgs.add("target/test-classes/route.yaml");
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-jpa", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.hibernate.orm", "hibernate-core", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-jpa-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.hibernate.orm", "hibernate-core", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-jpa", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "io.quarkus", "quarkus-hibernate-orm", null));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "camel:microprofile-health", "org.apache.camel:camel-microprofile-health",
            "org.apache.camel.quarkus:camel-quarkus-microprofile-health" })
    public void shouldExportMicroProfileHealthToQuarkus(String dep) throws Exception {
        LOG.info("shouldExportMicroProfileHealthToQuarkus {}", dep);
        Export command = createCommand(RuntimeType.quarkus, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--dep=" + dep);
        int exit = command.doCall();

        assertThat(exit).isZero();
        Model model = readMavenModel();
        // quarkus-pom.ftl does not contain the extension, so it must come from the dependency
        assertThat(model.getDependencies())
                .filteredOn(d -> "camel-quarkus-microprofile-health".equals(d.getArtifactId()))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getGroupId()).isEqualTo("org.apache.camel.quarkus");
                    assertThat(d.getVersion()).isNull();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "camel:core", "org.apache.camel:camel-core", "org.apache.camel.quarkus:camel-quarkus-core" })
    public void shouldExportCamelQuarkusCoreOnceToQuarkus(String dep) throws Exception {
        LOG.info("shouldExportCamelQuarkusCoreOnceToQuarkus {}", dep);
        Export command = createCommand(RuntimeType.quarkus, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--dep=" + dep);
        int exit = command.doCall();

        assertThat(exit).isZero();
        Model model = readMavenModel();
        // quarkus-pom.ftl contains the extension, so the dependency must not add it again
        assertThat(model.getDependencies())
                .filteredOn(d -> "camel-quarkus-core".equals(d.getArtifactId()))
                .singleElement()
                .satisfies(d -> assertThat(d.getGroupId()).isEqualTo("org.apache.camel.quarkus"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateContent(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateContent {}", rt);
        // We need a real file as we want to test the generated content
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        // In this test we can validate any generic resource that must be created along the export.
        // Exporting once to reduce the time to execute the test and the resource required to test.

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        Assertions.assertTrue(appProperties.exists(), "Missing application properties");
        assertApplicationPropertiesContent(rt, appProperties);
        // Camel routes
        Assertions.assertTrue(new File(workingDir + "/src/main/resources/camel", "route.yaml").exists(),
                "Missing camel route in resources");
        // Dockerfile
        Assertions.assertTrue(new File(workingDir + "/src/main/docker", "Dockerfile").exists(), "Missing Dockerfile");
        // Readme
        Assertions.assertTrue(new File(workingDir, "readme.md").exists(), "Missing readme.md");
        // AGENTS.md
        Assertions.assertTrue(new File(workingDir, "AGENTS.md").exists(), "Missing AGENTS.md");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportCamelWrapper(RuntimeType rt) throws Exception {
        LOG.info("shouldExportCamelWrapper {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--camel-wrapper");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(new File(workingDir, "camel").exists(), "Missing camel wrapper script");
        Assertions.assertTrue(new File(workingDir, "camel.cmd").exists(), "Missing camel.cmd wrapper script");
        Assertions.assertTrue(new File(workingDir, ".camel/camel-wrapper.properties").exists(),
                "Missing .camel/camel-wrapper.properties");

        String properties = Files.readString(workingDir.toPath().resolve(".camel/camel-wrapper.properties"));
        assertThat(properties).contains("camel.version=");
        assertThat(properties).contains("distributionUrl=");
        assertThat(properties).contains("camel-launcher-");

        String camelScript = Files.readString(workingDir.toPath().resolve("camel"));
        assertThat(camelScript).startsWith("#!/bin/sh");
        assertThat(camelScript).contains("camel-wrapper.properties");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldNotExportCamelWrapperByDefault(RuntimeType rt) throws Exception {
        LOG.info("shouldNotExportCamelWrapperByDefault {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertFalse(new File(workingDir, "camel").exists(),
                "Camel wrapper script should not exist by default");
        Assertions.assertFalse(new File(workingDir, ".camel/camel-wrapper.properties").exists(),
                "Camel wrapper properties should not exist by default");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldSkipDockerFiles(RuntimeType rt) throws Exception {
        LOG.info("shouldSkipDockerFiles {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--docker=false");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertFalse(new File(workingDir + "/src/main/docker", "Dockerfile").exists(),
                "Dockerfile should not exist when --docker=false");
        Assertions.assertTrue(new File(workingDir, "readme.md").exists(), "Missing readme.md");
    }

    @ParameterizedTest
    @MethodSource("runtimeAndJavaVersionProvider")
    public void shouldUseJavaVersionInDockerFiles(RuntimeType rt, String javaVersion) throws Exception {
        LOG.info("shouldUseJavaVersionInDockerFiles {} {}", rt, javaVersion);
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--java-version=" + javaVersion);
        int exit = command.doCall();

        assertThat(exit).isZero();
        String other = "21".equals(javaVersion) ? "25" : "21";
        List<Path> jvmDockerFiles;
        try (Stream<Path> files = Files.list(workingDir.toPath().resolve("src/main/docker"))) {
            jvmDockerFiles = files.filter(f -> readString(f).contains("ubi9/openjdk-")).toList();
        }
        assertThat(jvmDockerFiles).isNotEmpty();
        for (Path dockerfile : jvmDockerFiles) {
            assertThat(readString(dockerfile))
                    .as(dockerfile.getFileName().toString())
                    .contains("ubi9/openjdk-" + javaVersion)
                    .doesNotContain("ubi9/openjdk-" + other);
        }
    }

    @Test
    public void shouldExportJBangRuntimeAsCamelMain() throws Exception {
        Export command = new Export(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--runtime=jbang", "route.yaml");

        assertThat(command.runtime).isEqualTo(RuntimeType.main);
    }

    @Test
    public void olderQuarkusVersion() throws Exception {
        LOG.info("olderQuarkusVersion");
        // We need a real file as we want to test the generated content
        Export command = createCommand(RuntimeType.quarkus, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--quarkus-version=3.21.0");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        String content = IOHelper.loadText(new FileInputStream(appProperties));
        Assertions.assertTrue(content.contains("quarkus.native.resources.includes=camel/route.yaml"),
                "should contain quarkus.native.resources.includes property, was " + content);
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateJavaContent(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateJavaContent {}", rt);
        // We need a real file as we want to test the generated content
        Export command = createCommand(rt, new String[] { "src/test/resources/Hey.java" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        // In this test we can validate any generic resource that must be created along the export.
        // Exporting once to reduce the time to execute the test and the resource required to test.

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        Assertions.assertTrue(appProperties.exists(), "Missing application properties");
        assertApplicationPropertiesContentJava(rt, appProperties, "examples.route");
        // Camel routes
        Assertions.assertTrue(new File(workingDir + "/src/main/java/examples/route", "Hey.java").exists(),
                "Missing camel route in java package");
        // Dockerfile
        Assertions.assertTrue(new File(workingDir + "/src/main/docker", "Dockerfile").exists(), "Missing Dockerfile");
        // Readme
        Assertions.assertTrue(new File(workingDir, "readme.md").exists(), "Missing readme.md");
        // AGENTS.md
        Assertions.assertTrue(new File(workingDir, "AGENTS.md").exists(), "Missing AGENTS.md");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportFromDir(RuntimeType rt) throws Exception {
        LOG.info("shouldExportFromDir {}", rt);
        Export command = new Export(new CamelJBangMain());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime())));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        cmdArgs.add("src/test/resources/myapp");
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        File f = workingDir.toPath().resolve("src/main/resources/application.properties").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir.toPath().resolve("src/main/resources/camel/hello.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-timer", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot",
                            "camel-timer-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus",
                            "camel-quarkus-timer", null));
        }
    }
}
