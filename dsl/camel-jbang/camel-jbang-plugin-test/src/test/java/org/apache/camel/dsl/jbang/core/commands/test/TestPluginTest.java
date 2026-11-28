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

package org.apache.camel.dsl.jbang.core.commands.test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.dsl.jbang.run.common.VersionHelper;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.spi.Resources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestPluginTest {

    private StringPrinter printer;

    @BeforeEach
    public void setup() {
        // Set Camel version with system property value, usually set via Maven surefire plugin
        // In case you run this test via local Java IDE you need to provide the system property or a default value here
        VersionHelper.setCamelVersion(System.getProperty("camel.version", ""));
        printer = new StringPrinter();
        CommandLineHelper.useHomeDir("target");
    }

    @Test
    public void shouldListTests() {
        CamelJBangMain camelJBangMain = createCamelJBangMain();

        camelJBangMain.execute("test", "ps");

        Assertions.assertTrue(printer.getOutput().contains("PID  NAME  STATUS  AGE"));
    }

    @Test
    public void shouldInitYamlTest() {
        Path targetDir = CommandLineHelper.getHomeDir().toAbsolutePath().resolve(TestPlugin.TEST_DIR);

        CamelJBangMain camelJBangMain = createCamelJBangMain();
        camelJBangMain.execute("test", "init", "sample.citrus.it.yaml",
                "--directory", targetDir.toString());

        Assertions.assertTrue(targetDir.resolve("citrus-application.properties").toFile().exists());
        Assertions.assertTrue(targetDir.resolve("sample.citrus.it.yaml").toFile().exists());
    }

    @Test
    public void initDoesNotOverwriteATest(@TempDir Path dir) throws Exception {
        // camel test init replaced an edited test without a word (CAMEL-26933)
        CamelJBangMain camelJBangMain = createCamelJBangMain();
        camelJBangMain.execute("test", "init", "edited.citrus.it.yaml", "--directory", dir.toString());
        Path test = dir.resolve("edited.citrus.it.yaml");
        Files.writeString(test, "name: my edited test\n");

        camelJBangMain.execute("test", "init", "edited.citrus.it.yaml", "--directory", dir.toString());

        Assertions.assertEquals("name: my edited test\n", Files.readString(test));
        Assertions.assertTrue(printer.getOutput().contains("File already exists: " + test + " (use --force to overwrite)"),
                printer.getOutput());

        camelJBangMain.execute("test", "init", "edited.citrus.it.yaml", "--directory", dir.toString(), "--force");

        Assertions.assertNotEquals("name: my edited test\n", Files.readString(test));
    }

    @Test
    public void shouldInitXmlTest() {
        Path targetDir = CommandLineHelper.getHomeDir().toAbsolutePath().resolve(TestPlugin.TEST_DIR);

        CamelJBangMain camelJBangMain = createCamelJBangMain();
        camelJBangMain.execute("test", "init", "sample.citrus.it.xml",
                "--directory", targetDir.toString());

        Assertions.assertTrue(targetDir.resolve("citrus-application.properties").toFile().exists());
        Assertions.assertTrue(targetDir.resolve("sample.citrus.it.xml").toFile().exists());
    }

    @Test
    public void shouldRunTest() {
        Path targetDir = CommandLineHelper.getHomeDir().toAbsolutePath().resolve(TestPlugin.TEST_DIR);

        CamelJBangMain camelJBangMain = createCamelJBangMain();
        camelJBangMain.execute("test", "init", "sample.citrus.it.yaml",
                "--directory", targetDir.toString());

        Assertions.assertTrue(targetDir.resolve("citrus-application.properties").toFile().exists());
        Assertions.assertTrue(targetDir.resolve("sample.citrus.it.yaml").toFile().exists());

        camelJBangMain.execute("test", "run", targetDir.toString());
    }

    @Test
    public void shouldInspectTest() {
        Path source = Resources.fromClasspath("my-sample.citrus.it.yaml").file().toPath();

        CamelJBangMain camelJBangMain = createCamelJBangMain();
        camelJBangMain.execute("test", "inspect", source.toString());

        Assertions.assertEquals("""
                {
                  "name": "my-sample.citrus.it.yaml",
                  "actions": [
                    "echo"
                  ]
                }
                """.trim(), printer.getOutput().trim());
    }

    @Test
    public void shouldAlwaysIncludeDefaultDependencies() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        try {
            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-base:${citrus.version}"),
                    "Should always include citrus-base as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-junit-jupiter:${citrus.version}"),
                    "Should always include citrus-junit-jupiter as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-http:${citrus.version}"),
                    "Should always include citrus-http as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-yaml:${citrus.version}"),
                    "Should always include citrus-yaml as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-xml:${citrus.version}"),
                    "Should always include citrus-xml as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-groovy:${citrus.version}"),
                    "Should always include citrus-groovy as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-validation-text:${citrus.version}"),
                    "Should always include citrus-validation-text as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-validation-xml:${citrus.version}"),
                    "Should always include citrus-validation-xml as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-validation-json:${citrus.version}"),
                    "Should always include citrus-validation-json as default dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-validation-yaml:${citrus.version}"),
                    "Should always include citrus-validation-yaml as default dependency");
        } finally {
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldProduceValidMavenPropertyReferences() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        try {
            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);
            Assertions.assertFalse(deps.isEmpty());
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-junit-jupiter:${citrus.version}"),
                    "Should include citrus-junit-jupiter dependency");
            Assertions.assertFalse(deps.contains("mvn@test:org.citrusframework:citrus-junit5:${citrus.version}"),
                    "Should not include legacy citrus-junit5 dependency");
            for (String dep : deps) {
                Assertions.assertFalse(dep.contains("\\"),
                        "Dependency should not contain backslash escapes: " + dep);
                if (dep.contains("org.citrusframework")) {
                    Assertions.assertTrue(dep.contains("${citrus.version}"),
                            "Dependency should contain valid Maven property reference: " + dep);
                }
            }
        } finally {
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldDetectDependenciesFromYamlTestSources() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path yamlFile = testDir.resolve("my-test.citrus.it.yaml");
        try {
            Files.writeString(yamlFile, """
                    name: my-test
                    actions:
                      - sql:
                          datasource: "myDataSource"
                      - send:
                          endpoint: "kafka:myTopic"
                      - echo:
                          message: "Hello"
                    """);

            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-sql:${citrus.version}"),
                    "Should auto-detect citrus-sql from sql action");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-kafka:${citrus.version}"),
                    "Should auto-detect citrus-kafka from kafka endpoint");

            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-base:${citrus.version}"),
                    "Should still include default citrus-base dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-junit-jupiter:${citrus.version}"),
                    "Should still include default citrus-junit-jupiter dependency");
        } finally {
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldDetectDependenciesWhenTheContextClassLoaderCannotSeeCitrus() throws IOException {
        // camel export and camel dependency list run with the class loader of the CLI as the context class loader,
        // which cannot see the citrus-jbang JAR of the plugin, where the code analyzer has its catalog (CAMEL-27341)
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path yamlFile = testDir.resolve("cli-loader.citrus.it.yaml");
        Thread thread = Thread.currentThread();
        ClassLoader tccl = thread.getContextClassLoader();
        try (URLClassLoader cli = new URLClassLoader(new URL[0], null)) {
            Files.writeString(yamlFile, """
                    name: cli-loader
                    actions:
                      - send:
                          endpoint: "kafka:myTopic"
                    """);
            Assertions.assertNull(cli.getResource("citrus-catalog/citrus/citrus-catalog-aggregate-endpoints.json"),
                    "The class loader of the test must not see the catalog of the code analyzer");

            thread.setContextClassLoader(cli);
            Set<String> deps = new TestPluginExporter().getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-kafka:${citrus.version}"),
                    "Should auto-detect citrus-kafka from kafka endpoint");
            Assertions.assertSame(cli, thread.getContextClassLoader(), "Should restore the context class loader");
        } finally {
            thread.setContextClassLoader(tccl);
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldNotAddExtraDepsWithoutMatchingActions() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path yamlFile = testDir.resolve("simple.citrus.it.yaml");
        try {
            Files.writeString(yamlFile, """
                    name: simple-test
                    actions:
                      - echo:
                          message: "Hello"
                    """);

            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-base:${citrus.version}"),
                    "Should still include default citrus-base dependency");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-junit-jupiter:${citrus.version}"),
                    "Should still include default citrus-junit-jupiter dependency");
            Assertions.assertFalse(deps.contains("mvn@test:org.citrusframework:citrus-kafka:${citrus.version}"),
                    "Should not include citrus-kafka when no kafka endpoint is used");
            Assertions.assertFalse(deps.contains("mvn@test:org.citrusframework:citrus-sql:${citrus.version}"),
                    "Should not include citrus-sql when no sql action is used");
        } finally {
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldDetectDependenciesFromMultipleYamlFiles() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path sqlTest = testDir.resolve("sql-test.citrus.it.yaml");
        Path kafkaTest = testDir.resolve("kafka-test.citrus.yaml");
        try {
            Files.writeString(sqlTest, """
                    name: sql-test
                    actions:
                      - sql:
                          datasource: "myDataSource"
                    """);
            Files.writeString(kafkaTest, """
                    name: kafka-test
                    endpoints:
                      - kafka:
                          topic: "myTopic"
                    actions:
                      - echo:
                          message: "Hello"
                    """);

            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-sql:${citrus.version}"),
                    "Should detect citrus-sql from first YAML file");
            Assertions.assertTrue(deps.contains("mvn@test:org.citrusframework:citrus-kafka:${citrus.version}"),
                    "Should detect citrus-kafka from second YAML file");
        } finally {
            Files.deleteIfExists(sqlTest);
            Files.deleteIfExists(kafkaTest);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldDetectCamelInfraServiceDependencies() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path yamlFile = testDir.resolve("infra-test.citrus.it.yaml");
        try {
            Files.writeString(yamlFile, """
                    name: infra-test
                    actions:
                      - camel:
                          infra:
                            run:
                              service: kafka
                      - echo:
                          message: "Hello"
                    """);

            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(
                    deps.stream().anyMatch(d -> d.startsWith("mvn@test:org.apache.camel:camel-test-infra-kafka:")),
                    "Should auto-detect camel-test-infra-kafka from camel infra service action");
        } finally {
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldDetectCamelEndpointDependencies() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path yamlFile = testDir.resolve("camel-endpoint-test.citrus.it.yaml");
        try {
            Files.writeString(yamlFile, """
                    name: camel-endpoint-test
                    actions:
                      - send:
                          endpoint: "camel:kafka:myTopic"
                    """);

            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            Assertions.assertTrue(
                    deps.stream().anyMatch(d -> d.startsWith("mvn@test:org.apache.camel:camel-kafka")),
                    "Should auto-detect camel-kafka from camel endpoint URI");
        } finally {
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldKeepVersionForCamelTestInfraArtifacts() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        Path yamlFile = testDir.resolve("infra-version-test.citrus.it.yaml");
        try {
            Files.writeString(yamlFile, """
                    name: infra-version-test
                    actions:
                      - camel:
                          infra:
                            run:
                              service: kafka
                    """);

            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);

            String infraDep = deps.stream()
                    .filter(d -> d.contains("camel-test-infra-kafka"))
                    .findFirst()
                    .orElse(null);
            Assertions.assertNotNull(infraDep, "Should contain camel-test-infra-kafka dependency");

            String[] parts = infraDep.split(":");
            Assertions.assertEquals(4, parts.length,
                    "camel-test-infra dependency should include version (4 colon-separated parts): " + infraDep);
        } finally {
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void shouldUseTestAgentInCommandTree() {
        CamelJBangMain main = createCamelJBangMain();
        CommandTree root = new CommandTree(main);
        TestPlugin plugin = new TestPlugin();
        plugin.customize(root, main);

        CommandTree agentTree = root.getSubcommands().get("test")
                .getSubcommands().get("agent");
        Assertions.assertNotNull(agentTree, "test agent command should be registered");
        Assertions.assertInstanceOf(TestAgent.class, agentTree.getCommand(),
                "agent command should be TestAgent, not the plain Citrus Agent");
    }

    @Test
    public void shouldUseTestAgentStartInCommandTree() {
        CamelJBangMain main = createCamelJBangMain();
        CommandTree root = new CommandTree(main);
        TestPlugin plugin = new TestPlugin();
        plugin.customize(root, main);

        CommandTree startTree = root.getSubcommands().get("test")
                .getSubcommands().get("agent")
                .getSubcommands().get("start");
        Assertions.assertNotNull(startTree, "test agent start command should be registered");
        Assertions.assertInstanceOf(TestAgentStart.class, startTree.getCommand(),
                "agent start command should be TestAgentStart, not the plain Citrus AgentStart");
    }

    @Test
    public void testAgentGetCommandsShouldReturnTestAgentStart() {
        CitrusJBangMain citrus = new CitrusJBangMain();
        TestAgent agent = new TestAgent(citrus);

        boolean hasTestAgentStart = agent.getCommands().stream()
                .anyMatch(cmd -> cmd instanceof TestAgentStart);
        Assertions.assertTrue(hasTestAgentStart,
                "TestAgent.getCommands() must return TestAgentStart so aesh dispatches to it");
    }

    /**
     * Creates CamelJBangMain instance ready for unit testing. Automatically adds test plugin commands. Automatically
     * sets the out printer.
     */
    private CamelJBangMain createCamelJBangMain() {
        return new CamelJBangMain() {
            @Override
            public void postAddCommands(CommandTree commandLine, String[] args) {
                TestPlugin plugin = new TestPlugin();
                plugin.customize(commandLine, this);

                super.postAddCommands(commandLine, args);
            }

            @Override
            public void quit(int exitCode) {
                if (exitCode < 0) {
                    throw new RuntimeException("Unexpected exit code: " + exitCode);
                }
            }

            @Override
            public Printer getOut() {
                return printer;
            }
        };
    }

}
