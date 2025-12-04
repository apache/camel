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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

class ExportTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExportTest.class);

    private File workingDir;

    @BeforeEach
    public void setup() throws IOException {
        LOG.info("Preparing ExportTest");
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-export").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        org.apache.camel.util.FileUtil.removeDir(workingDir);
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.quarkus),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.main));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProject(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateProject {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"classpath:route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
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
    public void shouldExportDifferentVersion(RuntimeType rt) throws Exception {
        LOG.info("shouldExportDifferentVersion {}", rt);
        // only test for main/spring-boot
        if (rt == RuntimeType.quarkus) {
            return;
        }
        Export command = createCommand(
                rt,
                new String[] {"classpath:route.yaml"},
                "--gav=examples:route:1.0.0",
                "--camel-version=4.8.3",
                "--dir=" + workingDir,
                "--quiet");
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
                    .as("Expected to find dependencyManagement entry: org.apache.camel:camel-bom:4.8.3")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-bom");
                        assertThat(dep.getVersion()).isEqualTo("4.8.3");
                    });
        } else if (rt == RuntimeType.springBoot) {
            assertThat(model.getDependencyManagement().getDependencies())
                    .as(
                            "Expected to find dependencyManagement entry: org.apache.camel.springboot:camel-spring-boot-bom:4.8.3")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel.springboot");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-spring-boot-bom");
                        assertThat(dep.getVersion()).isEqualTo("4.8.3");
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProjectWithBuildProperties(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateProjectWithBuildProperties {}", rt);
        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(
                command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()),
                "--build-property=foo=bar",
                "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        Assertions.assertEquals("bar", model.getProperties().getProperty("foo"));
        command.printConfigurationValues("export command");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void testShouldGenerateProjectMultivalue(RuntimeType rt) throws Exception {
        LOG.info("testShouldGenerateProjectMultivalue {}", rt);
        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(
                command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()),
                "--dep=foo:bar:1.0,jupiter:rocks:2.0",
                // it's important for the --build-property to be the last parameter to test a previous
                // export error when this property had arity=*
                "--build-property=foo=bar",
                "--build-property=camel=rocks",
                "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        Assertions.assertEquals("rocks", model.getProperties().getProperty("camel"));
        Assertions.assertEquals("bar", model.getProperties().getProperty("foo"));
        Assertions.assertTrue(
                containsDependency(model.getDependencies(), "foo", "bar", "1.0"),
                "Generated pom doesn't contain foo:bar:1.0 dependency");
        Assertions.assertTrue(
                containsDependency(model.getDependencies(), "jupiter", "rocks", "2.0"),
                "Generated pom doesn't contain jupiter:rocks:2.0 dependency");
        command.printConfigurationValues("export command");
    }

    private Export createCommand(RuntimeType rt, String[] files, String... args) {
        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(
                command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--runtime=%s".formatted(rt.runtime()));
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportLazyBean(RuntimeType rt) throws Exception {
        LOG.info("shouldExportLazyBean {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"classpath:route.yaml", "file:src/test/resources/LazyFoo.java"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--lazy-bean=true");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportCustomKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportCustomKamelet {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/route.yaml", "src/test/resources/user-source.kamelet.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-kamelet", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-http", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-sql", null));
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-http-starter", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-sql-starter", null));
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-http", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-sql", null));
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        }

        File f = workingDir
                .toPath()
                .resolve("src/main/resources/kamelets/user-source.kamelet.yaml")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir.toPath().resolve("src/main/resources/camel/route.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportOfficialKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportOfficialKamelet {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/counter.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            assertThat(model.getDependencies())
                    .as("Expected to find dependency: org.apache.camel:camel-kamelet")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-kamelet");
                    });

            assertThat(model.getDependencies())
                    .as("Expected to find dependency: org.apache.camel.kamelets:camel-kamelets")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.apache.camel.kamelets");
                        assertThat(dep.getArtifactId()).isEqualTo("camel-kamelets");
                    });
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        }

        File f = workingDir
                .toPath()
                .resolve("src/main/resources/camel/counter.yaml")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportSecret(RuntimeType rt) throws Exception {
        LOG.info("shouldExportSecret {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/k8s-secret.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-kubernetes", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kubernetes-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kubernetes", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportPipeOfficialAndCustomKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportPipeOfficialAndCustomKamelet {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/mypipe.yaml", "src/test/resources/mytimer.kamelet.yaml"},
                "--gav=examples:pipe:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("pipe", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-kamelet", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-timer", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-timer-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-timer", null));
        }

        File f = workingDir
                .toPath()
                .resolve("src/main/resources/kamelets/mytimer.kamelet.yaml")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir.toPath().resolve("src/main/resources/camel/mypipe.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    private Model readMavenModel() throws Exception {
        File f = new File(workingDir, "pom.xml");
        Assertions.assertTrue(f.isFile(), "Not a pom.xml file: " + f);
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(new FileReader(f));
        model.setPomFile(f);
        return model;
    }

    private boolean containsDependency(List<Dependency> deps, String group, String artifact, String version) {
        Dependency dep = new Dependency();
        dep.setGroupId(group);
        dep.setArtifactId(artifact);
        dep.setVersion(version);
        boolean found = false;
        for (int i = 0; i < deps.size() && !found; i++) {
            Dependency d = deps.get(i);
            if (version == null) {
                d.setVersion(null);
            }
            found = toGAV(d).equals(toGAV(dep));
        }
        return found;
    }

    private String toGAV(Dependency d) {
        return d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion();
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateContent(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateContent {}", rt);
        // We need a real file as we want to test the generated content
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        // In this test we can validate any generic resource that must be created along the export.
        // Exporting once to reduce the time to execute the test and the resource required to test.

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        Assertions.assertTrue(appProperties.exists(), "Missing application properties");
        assertApplicationPropertiesContent(rt, appProperties);
        // Camel routes
        Assertions.assertTrue(
                new File(workingDir + "/src/main/resources/camel", "route.yaml").exists(),
                "Missing camel route in resources");
        // Dockerfile
        Assertions.assertTrue(new File(workingDir + "/src/main/docker", "Dockerfile").exists(), "Missing Dockerfile");
        // Readme
        Assertions.assertTrue(new File(workingDir, "readme.md").exists(), "Missing readme.md");
    }

    // Each runtime may have a different logic
    public void assertApplicationPropertiesContent(RuntimeType rt, File appProps) throws Exception {
        try (FileInputStream fis = new FileInputStream(appProps)) {
            String content = IOHelper.loadText(fis);
            if (rt == RuntimeType.quarkus) {
                Assertions.assertFalse(
                        content.contains("quarkus.native.resources.includes=camel/route.yaml"),
                        "should not contain quarkus.native.resources.includes property, was " + content);
            }
            Assertions.assertFalse(
                    content.contains("camel.main.routes-include-pattern"),
                    "should not contain camel.main.routes-include-pattern property, was " + content);
            if (rt == RuntimeType.springBoot) {
                Assertions.assertTrue(
                        content.contains("camel.main.run-controller=true"),
                        "should contain camel.main.run-controller property, was " + content);
            }
        }
    }

    @Test
    public void olderQuarkusVersion() throws Exception {
        LOG.info("olderQuarkusVersion");
        // We need a real file as we want to test the generated content
        Export command = createCommand(
                RuntimeType.quarkus,
                new String[] {"src/test/resources/route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--quarkus-version=3.21.0");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        String content = IOHelper.loadText(new FileInputStream(appProperties));
        Assertions.assertTrue(
                content.contains("quarkus.native.resources.includes=camel/route.yaml"),
                "should contain quarkus.native.resources.includes property, was " + content);
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateJavaContent(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateJavaContent {}", rt);
        // We need a real file as we want to test the generated content
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/Hey.java"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        // In this test we can validate any generic resource that must be created along the export.
        // Exporting once to reduce the time to execute the test and the resource required to test.

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        Assertions.assertTrue(appProperties.exists(), "Missing application properties");
        assertApplicationPropertiesContentJava(rt, appProperties, "examples.route");
        // Camel routes
        Assertions.assertTrue(
                new File(workingDir + "/src/main/java/examples/route", "Hey.java").exists(),
                "Missing camel route in java package");
        // Dockerfile
        Assertions.assertTrue(new File(workingDir + "/src/main/docker", "Dockerfile").exists(), "Missing Dockerfile");
        // Readme
        Assertions.assertTrue(new File(workingDir, "readme.md").exists(), "Missing readme.md");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportActiveMQ(RuntimeType rt) throws Exception {
        LOG.info("shouldExportActiveMQ {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/ActiveMQRoute.java"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-activemq", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.messaginghub", "pooled-jms", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-activemq-starter", null));
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.messaginghub", "pooled-jms", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-activemq", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "io.quarkiverse.messaginghub", "quarkus-pooled-jms", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportKafka(RuntimeType rt) throws Exception {
        LOG.info("shouldExportKafka {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/MyKafkaRepo.java"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-kafka", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kafka-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kafka", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportSecretBean(RuntimeType rt) throws Exception {
        LOG.info("shouldExportSecretBean {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/k8s-secret-bean.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-kubernetes", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kubernetes-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kubernetes", null));
        }
    }

    // Each runtime may have a different logic
    public void assertApplicationPropertiesContentJava(RuntimeType rt, File appProps, String appPackage)
            throws Exception {
        try (FileInputStream fis = new FileInputStream(appProps)) {
            String content = IOHelper.loadText(fis);
            if (rt == RuntimeType.main) {
                Assertions.assertTrue(
                        content.contains("camel.main.basePackageScan=" + appPackage),
                        "should contain camel.main.basePackageScan property, but was " + content);
            } else {
                Assertions.assertFalse(
                        content.contains("camel.main.basePackageScan"),
                        "should not contain camel.main.basePackageScan property, but was " + content);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportUserProperty(RuntimeType rt) throws Exception {
        LOG.info("shouldExportUserProperty {}", rt);
        // We need a real file as we want to test the generated content
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                // there was a bug where properties starting with camel.main were duplicated in application.properties
                "--property",
                "hello=world",
                "--property",
                "camel.main.foo=bar");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        // In this test we can validate any generic resource that must be created along the export.
        // Exporting once to reduce the time to execute the test and the resource required to test.

        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        Assertions.assertTrue(appProperties.exists(), "Missing application.properties");
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(appProperties));
        Assertions.assertEquals("world", appProps.getProperty("hello"));
        Assertions.assertEquals("bar", appProps.getProperty("camel.main.foo"));
        int nrCamelProps = 0;
        // read the file as text to read the properties as clean text
        // as using the Properties class won't allow duplicated properties
        try (Scanner scanner = new Scanner(new File(workingDir + "/src/main/resources/application.properties"))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("camel.main.foo=bar".equals(line)) {
                    nrCamelProps++;
                }
            }
        }
        Assertions.assertEquals(1, nrCamelProps, "Duplicated property camel.main.foo in application.properties");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportGroovy(RuntimeType rt) throws Exception {
        LOG.info("shouldExportGroovy {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/groovy-demo.camel.yaml", "src/test/resources/demo.groovy"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-groovy", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-groovy-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-groovy", null));
        }

        File f = workingDir
                .toPath()
                .resolve("src/main/resources/camel-groovy/demo.groovy")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportObserve(RuntimeType rt) throws Exception {
        LOG.info("shouldExportObserve {}", rt);
        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(
                command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()),
                "--observe=true",
                "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel", "camel-observability-services", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(),
                    "org.apache.camel.springboot",
                    "camel-observability-services-starter",
                    null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-observability-services", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportFromDir(RuntimeType rt) throws Exception {
        LOG.info("shouldExportFromDir {}", rt);
        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(
                command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()),
                "src/test/resources/myapp");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        File f = workingDir
                .toPath()
                .resolve("src/main/resources/application.properties")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir.toPath().resolve("src/main/resources/camel/hello.yaml").toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-timer", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-timer-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-timer", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportToDWithCustomKamelet(RuntimeType rt) throws Exception {
        LOG.info("shouldExportToDWithCustomKamelet {}", rt);
        Export command = createCommand(
                rt,
                new String[] {"src/test/resources/toDroute.yaml", "src/test/resources/cheese-sink.kamelet.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", "camel-log", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-kamelet", null));
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-log-starter", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.springboot", "camel-kamelet-starter", null));
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-log", null));
            Assertions.assertTrue(containsDependency(
                    model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-kamelet", null));
            Assertions.assertFalse(
                    containsDependency(model.getDependencies(), "org.apache.camel.kamelets", "camel-kamelets", null));
        }

        File f = workingDir
                .toPath()
                .resolve("src/main/resources/kamelets/cheese-sink.kamelet.yaml")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
        f = workingDir
                .toPath()
                .resolve("src/main/resources/camel/toDroute.yaml")
                .toFile();
        Assertions.assertTrue(f.isFile());
        Assertions.assertTrue(f.exists());
    }

    @Test
    @SetSystemProperty(key = CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION, value = "4.10.0")
    public void shouldOverrideSpringBootVersionFromSystemProperty() throws Exception {
        LOG.info("shouldOverrideSpringBootVersionFromSystemProperty");
        Export command = createCommand(
                RuntimeType.springBoot,
                new String[] {"classpath:route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        assertThat(model.getDependencyManagement().getDependencies())
                .as(
                        "Expected to find dependencyManagement entry: org.apache.camel.springboot:camel-spring-boot-bom:4.10.0")
                .anySatisfy(dep -> {
                    assertThat(dep.getGroupId()).isEqualTo("org.apache.camel.springboot");
                    assertThat(dep.getArtifactId()).isEqualTo("camel-spring-boot-bom");
                    assertThat(dep.getVersion()).isEqualTo("4.10.0");
                });
    }

    @Test
    @SetSystemProperty(key = CamelJBangConstants.QUARKUS_VERSION, value = "3.26.0")
    public void shouldOverrideQuarkusVersionFromSystemProperty() throws Exception {
        LOG.info("shouldOverrideQuarkusVersionFromSystemProperty");

        Export command = createCommand(
                RuntimeType.quarkus,
                new String[] {"classpath:route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        assertThat(model.getProperties())
                .containsEntry("quarkus.platform.version", System.getProperty(CamelJBangConstants.QUARKUS_VERSION));
    }
}
