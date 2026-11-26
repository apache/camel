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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.util.IOHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExportPropertiesAndRepositoriesTest extends ExportBaseTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ExportPropertiesAndRepositoriesTest.class);

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProjectWithBuildProperties(RuntimeType rt) throws Exception {
        LOG.info("shouldGenerateProjectWithBuildProperties {}", rt);
        Export command = new CamelJBangMain().command(new Export());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime()), "--build-property=foo=bar"));
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
        Assertions.assertEquals("bar", model.getProperties().getProperty("foo"));
        command.printConfigurationValues("export command");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void testShouldGenerateProjectMultivalue(RuntimeType rt) throws Exception {
        LOG.info("testShouldGenerateProjectMultivalue {}", rt);
        Export command = new CamelJBangMain().command(new Export());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime()), "--dep=foo:bar:1.0,jupiter:rocks:2.0"));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        // it's important for the --build-property to be the last parameter to test a previous
        // export error when this property had arity=*
        cmdArgs.addAll(List.of("--build-property=foo=bar", "--build-property=camel=rocks",
                "target/test-classes/route.yaml"));
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        Assertions.assertEquals("rocks", model.getProperties().getProperty("camel"));
        Assertions.assertEquals("bar", model.getProperties().getProperty("foo"));
        Assertions.assertTrue(containsDependency(model.getDependencies(), "foo", "bar", "1.0"),
                "Generated pom doesn't contain foo:bar:1.0 dependency");
        Assertions.assertTrue(containsDependency(model.getDependencies(), "jupiter", "rocks", "2.0"),
                "Generated pom doesn't contain jupiter:rocks:2.0 dependency");
        command.printConfigurationValues("export command");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportUserProperty(RuntimeType rt) throws Exception {
        LOG.info("shouldExportUserProperty {}", rt);
        // We need a real file as we want to test the generated content
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                // there was a bug where properties starting with camel.main were duplicated in application.properties
                "--property", "hello=world", "--property", "camel.main.foo=bar");
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

    @Test
    public void shouldDashOnlyQuarkusOptionNames() throws Exception {
        LOG.info("shouldDashOnlyQuarkusOptionNames");
        Export command = createCommand(RuntimeType.quarkus, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--property", "quarkus.http.rootPath=/api",
                "--property", "quarkus.swagger-ui.urls.Camel=../openapi.json",
                "--property", "quarkus.log.category.\"com.example.MyRoute\".level=DEBUG",
                "--property", "quarkus.datasource.Orders.db-kind=postgresql",
                "--property", "quarkus.datasource.\"myOrders\".db-kind=mysql",
                "--property", "quarkus.datasource.order_history.db-kind=h2",
                "--property", "quarkus.hawtio.authenticationEnabled=false",
                "--property", "camel.main.streamCachingEnabled=false");
        int exit = command.doCall();

        assertThat(exit).isZero();
        Properties props = loadExportedProperties("application.properties");
        // camel keys are dashed as a whole
        assertThat(props.getProperty("camel.main.stream-caching-enabled")).isEqualTo("false");
        // hawtio takes its options in camel case
        assertThat(props.getProperty("quarkus.hawtio.authenticationEnabled")).isEqualTo("false");
        // option names in camel case are dashed
        assertThat(props.getProperty("quarkus.http.root-path")).isEqualTo("/api");
        // names chosen by the user are kept as they are
        assertThat(props.getProperty("quarkus.swagger-ui.urls.Camel")).isEqualTo("../openapi.json");
        assertThat(props.getProperty("quarkus.log.category.\"com.example.MyRoute\".level")).isEqualTo("DEBUG");
        assertThat(props.getProperty("quarkus.datasource.Orders.db-kind")).isEqualTo("postgresql");
        assertThat(props.getProperty("quarkus.datasource.\"myOrders\".db-kind")).isEqualTo("mysql");
        assertThat(props.getProperty("quarkus.datasource.order_history.db-kind")).isEqualTo("h2");
    }

    @Test
    @SetSystemProperty(key = CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION, value = "4.17.0")
    public void shouldOverrideSpringBootVersionFromSystemProperty() throws Exception {
        LOG.info("shouldOverrideSpringBootVersionFromSystemProperty");
        Export command = createCommand(RuntimeType.springBoot, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        assertThat(model.getDependencyManagement().getDependencies())
                .as("Expected to find dependencyManagement entry: org.apache.camel.springboot:camel-spring-boot-bom:4.17.0")
                .anySatisfy(dep -> {
                    assertThat(dep.getGroupId()).isEqualTo("org.apache.camel.springboot");
                    assertThat(dep.getArtifactId()).isEqualTo("camel-spring-boot-bom");
                    assertThat(dep.getVersion()).isEqualTo("4.17.0");
                });
    }

    @Test
    @SetSystemProperty(key = CamelJBangConstants.QUARKUS_VERSION, value = "3.26.0")
    public void shouldOverrideQuarkusVersionFromSystemProperty() throws Exception {
        LOG.info("shouldOverrideQuarkusVersionFromSystemProperty");

        Export command = createCommand(RuntimeType.quarkus, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        Model model = readMavenModel();
        assertThat(model.getProperties()).containsEntry("quarkus.platform.version",
                System.getProperty(CamelJBangConstants.QUARKUS_VERSION));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithParentPom(RuntimeType rt) throws Exception {
        LOG.info("shouldExportWithParentPom {}", rt);
        Export command = new CamelJBangMain().command(new Export());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime()),
                        "--parent-pom=com.ourorg:camel-parent:1.0"));
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

        // verify parent POM is set
        Assertions.assertNotNull(model.getParent(), "Parent POM should be set");
        Assertions.assertEquals("com.ourorg", model.getParent().getGroupId());
        Assertions.assertEquals("camel-parent", model.getParent().getArtifactId());
        Assertions.assertEquals("1.0", model.getParent().getVersion());

        if (rt == RuntimeType.springBoot) {
            // Spring Boot BOM should still be in dependencyManagement
            assertThat(model.getDependencyManagement().getDependencies())
                    .as("Spring Boot BOM must be in dependencyManagement when using custom parent")
                    .anySatisfy(dep -> {
                        assertThat(dep.getGroupId()).isEqualTo("org.springframework.boot");
                        assertThat(dep.getArtifactId()).isEqualTo("spring-boot-dependencies");
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithoutParentPom(RuntimeType rt) throws Exception {
        LOG.info("shouldExportWithoutParentPom {}", rt);
        Export command = createCommand(rt, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        if (rt == RuntimeType.springBoot) {
            // default Spring Boot parent should be used
            Assertions.assertNotNull(model.getParent(), "Spring Boot parent should be set by default");
            Assertions.assertEquals("org.springframework.boot", model.getParent().getGroupId());
            Assertions.assertEquals("spring-boot-starter-parent", model.getParent().getArtifactId());
        } else {
            // Quarkus and Main should have no parent by default
            Assertions.assertNull(model.getParent(), "No parent POM should be set by default for " + rt);
        }
    }

    @Test
    void shouldOverrideAutoDetectedDriverVersion() throws Exception {
        LOG.info("shouldOverrideAutoDetectedDriverVersion");
        // the bean uses driverClassName org.postgresql.Driver which Camel auto-detects and adds
        // org.postgresql:postgresql with the version from the camel-dependencies BOM. An explicit
        // --dep for the same groupId:artifactId must override that auto-detected version.
        Export command = new CamelJBangMain().command(new Export());
        CommandLineTestHelper.parse(command,
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=camel-main",
                "--dep=org.postgresql:postgresql:42.7.99",
                "src/test/resources/k8s-secret-bean.yaml");
        int exit = command.doCall();

        assertThat(exit).isZero();
        Model model = readMavenModel();

        List<Dependency> pg = model.getDependencies().stream()
                .filter(d -> "org.postgresql".equals(d.getGroupId()) && "postgresql".equals(d.getArtifactId()))
                .toList();
        assertThat(pg)
                .as("Explicit --dep version must override the auto-detected postgresql driver version")
                .singleElement()
                .satisfies(d -> assertThat(d.getVersion()).isEqualTo("42.7.99"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithCustomRepos(RuntimeType rt) throws Exception {
        LOG.info("shouldExportWithCustomRepos {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--repos=https://my.custom.repo/releases,https://other.repo/snapshots");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        assertThat(model.getRepositories())
                .as("Expected custom repositories in generated pom.xml")
                .anySatisfy(repo -> assertThat(repo.getUrl()).isEqualTo("https://my.custom.repo/releases"))
                .anySatisfy(repo -> assertThat(repo.getUrl()).isEqualTo("https://other.repo/snapshots"));

        assertThat(model.getPluginRepositories())
                .as("Expected custom plugin repositories in generated pom.xml")
                .anySatisfy(repo -> assertThat(repo.getUrl()).isEqualTo("https://my.custom.repo/releases"))
                .anySatisfy(repo -> assertThat(repo.getUrl()).isEqualTo("https://other.repo/snapshots"));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldContainJibProfile(RuntimeType rt) throws Exception {
        Export command = new CamelJBangMain().command(new Export());
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir,
                        "--runtime=%s".formatted(rt.runtime())));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        cmdArgs.add("src/test/resources/route.yaml");
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        File f = new File(workingDir, "pom.xml");
        String content = IOHelper.loadText(new FileInputStream(f));
        Assertions.assertTrue(content.contains("<id>jib</id>"),
                "Jib profile not exported!");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithExtraReposSystemProperty(RuntimeType rt) throws Exception {
        try (AutoCloseable property
                = systemProperty("camel.extra.repos", "atlassian=https://packages.atlassian.com/maven-external/")) {
            LOG.info("shouldExportWithExtraReposSystemProperty {}", rt);
            Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                    "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
            int exit = command.doCall();

            Assertions.assertEquals(0, exit);
            Model model = readMavenModel();

            assertThat(model.getRepositories())
                    .as("Expected extra repos from camel.extra.repos in generated pom.xml")
                    .anySatisfy(repo -> {
                        assertThat(repo.getId()).isEqualTo("atlassian");
                        assertThat(repo.getUrl()).isEqualTo("https://packages.atlassian.com/maven-external/");
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithMultipleExtraRepos(RuntimeType rt) throws Exception {
        try (AutoCloseable property = systemProperty("camel.extra.repos",
                "repo1=https://repo1.example.com/maven2,repo2=https://repo2.example.com/releases")) {
            LOG.info("shouldExportWithMultipleExtraRepos {}", rt);
            Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                    "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
            int exit = command.doCall();

            Assertions.assertEquals(0, exit);
            Model model = readMavenModel();

            assertThat(model.getRepositories())
                    .as("Expected both extra repos in generated pom.xml")
                    .anySatisfy(repo -> {
                        assertThat(repo.getId()).isEqualTo("repo1");
                        assertThat(repo.getUrl()).isEqualTo("https://repo1.example.com/maven2");
                    })
                    .anySatisfy(repo -> {
                        assertThat(repo.getId()).isEqualTo("repo2");
                        assertThat(repo.getUrl()).isEqualTo("https://repo2.example.com/releases");
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithReposFromSystemProperty(RuntimeType rt) throws Exception {
        try (AutoCloseable property = systemProperty("camel.jbang.repos", "https://packages.atlassian.com/maven-external/")) {
            LOG.info("shouldExportWithReposFromSystemProperty {}", rt);
            Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                    "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
            int exit = command.doCall();

            Assertions.assertEquals(0, exit);
            Model model = readMavenModel();

            assertThat(model.getRepositories())
                    .as("Expected camel.jbang.repos from system property in generated pom.xml")
                    .anySatisfy(repo -> assertThat(repo.getUrl()).isEqualTo("https://packages.atlassian.com/maven-external/"));
        }
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithReposCliHavingUniqueIds(RuntimeType rt) throws Exception {
        LOG.info("shouldExportWithReposCliHavingUniqueIds {}", rt);
        Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--repos=atlassian=https://packages.atlassian.com/maven-external/,https://repository.apache.org/snapshots/");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();

        List<String> repoIds = model.getRepositories().stream()
                .map(Repository::getId)
                .collect(Collectors.toList());
        assertThat(repoIds)
                .as("Repository ids must be unique in generated pom.xml")
                .doesNotHaveDuplicates();

        assertThat(model.getRepositories())
                .anySatisfy(repo -> {
                    assertThat(repo.getId()).isEqualTo("atlassian");
                    assertThat(repo.getUrl()).isEqualTo("https://packages.atlassian.com/maven-external/");
                });
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportWithExtraReposDefaultValueFallback(RuntimeType rt) throws Exception {
        try (AutoCloseable property = systemProperty("camel.default.extra.repos.default.value",
                "atlassian=https://packages.atlassian.com/maven-external/")) {
            LOG.info("shouldExportWithExtraReposDefaultValueFallback {}", rt);
            Export command = createCommand(rt, new String[] { "src/test/resources/route.yaml" },
                    "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
            int exit = command.doCall();

            Assertions.assertEquals(0, exit);
            Model model = readMavenModel();

            assertThat(model.getRepositories())
                    .as("Expected extra repos from camel.default.extra.repos.default.value fallback in generated pom.xml")
                    .anySatisfy(repo -> {
                        assertThat(repo.getId()).isEqualTo("atlassian");
                        assertThat(repo.getUrl()).isEqualTo("https://packages.atlassian.com/maven-external/");
                    });
        }
    }
}
