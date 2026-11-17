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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.HawtioVersion;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExportConsoleTest extends ExportBaseTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ExportConsoleTest.class);

    @Test
    public void shouldExportGenAiRouteWithObservability() throws Exception {
        Export command = new Export(new CamelJBangMain());
        CommandLineTestHelper.parse(command,
                "--gav=examples:genai:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--runtime=main",
                "--observe=true",
                "src/test/resources/genai/langchain4j-route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertTrue(
                containsDependency(model.getDependencies(), "org.apache.camel", "camel-langchain4j-chat", null));
        Assertions.assertTrue(
                containsDependency(model.getDependencies(), "org.apache.camel", "camel-ai-observability", null));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportConsoleForDevProfileOnly(RuntimeType rt) throws Exception {
        LOG.info("shouldExportConsoleForDevProfileOnly {}", rt);
        int exit = exportWithConsole(rt);

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        assertConsoleDependencies(model, rt);

        // an exported project only has the console with the dev profile
        Properties props = loadExportedProperties("application.properties");
        Assertions.assertNull(exportedProperty(props, "camel.main.devConsoleEnabled"));
        Assertions.assertNull(props.getProperty("management.endpoints.web.exposure.include"));
        Assertions.assertNull(props.getProperty("camel.jbang.console"));

        Properties dev = loadExportedProperties("application-dev.properties");
        Assertions.assertNull(dev.getProperty("quarkus.camel.console.exposure-mode"));
        // what the application sees with the dev profile active
        Properties effective = new Properties();
        effective.putAll(props);
        effective.putAll(dev);
        assertConsoleProperties(effective, rt);
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportConsoleInDevProfileExport(RuntimeType rt) throws Exception {
        LOG.info("shouldExportConsoleInDevProfileExport {}", rt);
        int exit = exportWithConsole(rt, "--profile=dev");

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        assertConsoleDependencies(model, rt);

        // the dev profile is flattened into application.properties, and so is the console
        Properties props = loadExportedProperties("application.properties");
        assertConsoleProperties(props, rt);
        Assertions.assertNull(props.getProperty("quarkus.camel.console.exposure-mode"));
        Assertions.assertNull(props.getProperty("camel.jbang.console"));
        Assertions.assertFalse(new File(workingDir, "src/main/resources/application-dev.properties").exists());
    }

    @Test
    public void shouldExposeConsoleWithObserveOnSpringBoot() throws Exception {
        int exit = exportWithConsole(RuntimeType.springBoot, "--observe");

        Assertions.assertEquals(0, exit);
        // the observability defaults must survive the explicit exposure list
        Properties dev = loadExportedProperties("application-dev.properties");
        Assertions.assertEquals("health,prometheus,camel", dev.getProperty("management.endpoints.web.exposure.include"));
    }

    @Test
    public void shouldExposeConsoleWithHawtioOnSpringBoot() throws Exception {
        int exit = exportWithConsole(RuntimeType.springBoot, "--hawtio");

        Assertions.assertEquals(0, exit);
        // hawtio is exposed in application.properties, and the dev profile must keep it when adding the console
        Properties props = loadExportedProperties("application.properties");
        Assertions.assertEquals("hawtio,jolokia", props.getProperty("management.endpoints.web.exposure.include"));
        Properties dev = loadExportedProperties("application-dev.properties");
        Assertions.assertEquals("hawtio,jolokia,camel", dev.getProperty("management.endpoints.web.exposure.include"));
    }

    @Test
    public void shouldAppendConsoleToExistingDevProfile() throws Exception {
        Export command = new Export(new CamelJBangMain());
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--runtime=main",
                "--console",
                "--port=0",
                "src/test/resources/devprofile");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Properties dev = loadExportedProperties("application-dev.properties");
        // the user's own dev profile settings are kept
        Assertions.assertEquals("true", dev.getProperty("camel.main.tracing"));
        Assertions.assertEquals("true", exportedProperty(dev, "camel.main.devConsoleEnabled"));
    }

    @Test
    public void shouldNotExportConsoleByDefault() throws Exception {
        Export command = new Export(new CamelJBangMain());
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--runtime=main",
                "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertFalse(containsDependency(model.getDependencies(), "org.apache.camel", "camel-console", null));

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(new File(workingDir, "src/main/resources/application.properties"))) {
            props.load(fis);
        }
        Assertions.assertNull(exportedProperty(props, "camel.management.devConsoleEnabled"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportObserve(RuntimeType rt) throws Exception {
        LOG.info("shouldExportObserve {}", rt);
        Export command = new Export(new CamelJBangMain());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime()), "--observe=true"));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        cmdArgs.add("target/test-classes/route.yaml");
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel", "camel-observability-services", null));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot",
                            "camel-observability-services-starter", null));
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus",
                            "camel-quarkus-observability-services", null));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportHawtio(RuntimeType rt) throws Exception {
        LOG.info("shouldExportHawtio {}", rt);
        Export command = new Export(new CamelJBangMain());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime()), "--hawtio=true"));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        cmdArgs.add("target/test-classes/route.yaml");
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());

        if (rt == RuntimeType.main) {
            // hawtio not supported
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.springboot", "camel-management-starter",
                            null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "io.hawt",
                            "hawtio-springboot4", HawtioVersion.HAWTIO_VERSION));
            // Application properties
            File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
            String content = IOHelper.loadText(new FileInputStream(appProperties));
            Assertions.assertTrue(content.contains("management.endpoints.web.exposure.include=hawtio,jolokia"),
                    "should contain management.endpoints.web.exposure.include property, was " + content);
            Assertions.assertTrue(content.contains("spring.jmx.enabled=true"),
                    "should contain spring.jmx.enabled property, was " + content);
            Assertions.assertTrue(content.contains("hawtio.authenticationEnabled=false"),
                    "should contain hawtio.authenticationEnabled property, was " + content);
        } else if (rt == RuntimeType.quarkus) {
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "org.apache.camel.quarkus", "camel-quarkus-management", null));
            Assertions.assertTrue(
                    containsDependency(model.getDependencies(), "io.hawt",
                            "hawtio-quarkus", HawtioVersion.HAWTIO_VERSION));
            // Application properties
            File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
            String content = IOHelper.loadText(new FileInputStream(appProperties));
            Assertions.assertTrue(content.contains("quarkus.hawtio.authenticationEnabled=false"),
                    "should contain quarkus.hawtio.authenticationEnabled property, was " + content);
        }
    }

    @Test
    public void shouldExportHawtioWithSpringBoot3() throws Exception {
        LOG.info("shouldExportHawtioWithSpringBoot3");
        Export command = new Export(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir,
                "--runtime=spring-boot", "--camel-version=" + RELEASED_CAMEL_VERSION,
                "--spring-boot-version=3.5.14", "--hawtio=true", "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertTrue(containsDependency(model.getDependencies(), "io.hawt",
                "hawtio-springboot", HawtioVersion.HAWTIO_VERSION));
    }

    @ParameterizedTest
    @ValueSource(strings = { "camel.metrics.enabled", "camel.management.metricsEnabled", "camel.server.metricsEnabled" })
    void shouldNotDuplicateMetricsDependency(String metricsOption) throws Exception {
        LOG.info("shouldNotDuplicateMetricsDependency {}", metricsOption);
        Path baseDir = baseDirWithApplicationProperties(metricsOption + "=true\n");

        Export command = new Export(new CamelJBangMain());
        command.exportBaseDir = baseDir;
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=camel-main",
                "src/test/resources/route.yaml");
        int exit = command.doCall();

        assertThat(exit).isEqualTo(0);
        Model model = readMavenModel();

        assertThat(model.getDependencies()).extracting(Dependency::getArtifactId)
                .as("the metrics option adds camel-micrometer-prometheus")
                .contains("camel-micrometer-prometheus");
        assertEachArtifactOnce(model);
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    void shouldNotDuplicateConsoleDependencies(RuntimeType rt) throws Exception {
        LOG.info("shouldNotDuplicateConsoleDependencies {}", rt);
        // a project that has the console options itself, exported with --console: the dependencies of the
        // console are added for the options and for the flag
        Path baseDir = baseDirWithApplicationProperties("""
                camel.management.devConsoleEnabled=true
                camel.management.healthCheckEnabled=true
                camel.management.jolokiaEnabled=true
                """);
        int exit = exportWithConsole(baseDir, rt);

        assertThat(exit).isEqualTo(0);
        Model model = readMavenModel();

        assertConsoleDependencies(model, rt);
        assertEachArtifactOnce(model);
    }

    @ParameterizedTest
    @MethodSource("consoleArtifactProvider")
    void shouldNotDuplicateConsoleDependencyGivenAsRuntimeArtifact(RuntimeType rt, String groupId, String artifactId)
            throws Exception {
        LOG.info("shouldNotDuplicateConsoleDependencyGivenAsRuntimeArtifact {}", rt);
        // --console adds org.apache.camel:camel-console, which the runtime maps to its own artifact: the same artifact
        // as the --dep names, with other coordinates before the mapping
        int exit = exportWithConsole(rt, "--dep=" + groupId + ":" + artifactId);

        assertThat(exit).isEqualTo(0);
        Model model = readMavenModel();

        assertThat(model.getDependencies()).extracting(Dependency::getArtifactId)
                .contains(artifactId);
        assertEachArtifactOnce(model);
    }
}
