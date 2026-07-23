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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.citrusframework.spi.Resources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
    public void shouldProduceValidMavenPropertyReferences() throws IOException {
        Path testDir = Path.of(".").resolve("test");
        Files.createDirectories(testDir);
        try {
            TestPluginExporter exporter = new TestPluginExporter();
            Set<String> deps = exporter.getDependencies(RuntimeType.quarkus);
            Assertions.assertFalse(deps.isEmpty());
            for (String dep : deps) {
                Assertions.assertFalse(dep.contains("\\"),
                        "Dependency should not contain backslash escapes: " + dep);
                Assertions.assertTrue(dep.contains("${citrus.version}"),
                        "Dependency should contain valid Maven property reference: " + dep);
            }
        } finally {
            Files.deleteIfExists(testDir);
        }
    }

    /**
     * Creates CamelJBangMain instance ready for unit testing. Automatically adds test plugin commands. Automatically
     * sets the out printer.
     */
    private CamelJBangMain createCamelJBangMain() {
        return new CamelJBangMain() {
            @Override
            public void postAddCommands(CommandLine commandLine, String[] args) {
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
