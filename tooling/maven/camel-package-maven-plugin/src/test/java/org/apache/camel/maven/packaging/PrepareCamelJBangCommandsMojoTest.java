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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-regression test for CAMEL-25136: interfaces (JavaInterfaceImpl) encountered while scanning command classes must
 * not produce ClassCastException warnings.
 */
class PrepareCamelJBangCommandsMojoTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that an interface file co-located in the commands directory — used as a mixin type — does not produce a
     * WARNING log. Before the fix, Roaster.parse() returned JavaInterfaceImpl and the unchecked cast to JavaClassSource
     * threw a ClassCastException caught as WARN.
     */
    @Test
    void testInterfaceMixinDoesNotProduceWarning() throws Exception {
        // Write a minimal interface (the mixin type) alongside the command class
        Files.writeString(tempDir.resolve("TestMixinSpec.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                public interface TestMixinSpec {
                    String repos();
                    boolean download();
                }
                """);

        // Write a command class whose mixin field type is the interface above
        Files.writeString(tempDir.resolve("TestCommand.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine;
                import picocli.CommandLine.Command;
                import picocli.CommandLine.Option;
                import picocli.CommandLine.Mixin;

                @Command(name = "test", description = "A test command")
                public class TestCommand {

                    @Mixin
                    TestMixinSpec mixin;

                    @Option(names = "--verbose", description = "Enable verbose output")
                    boolean verbose;
                }
                """);

        // Write a minimal CamelJBangMain.java referencing TestCommand
        Files.writeString(tempDir.resolve("CamelJBangMain.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine;
                import picocli.CommandLine.Command;

                @Command(name = "camel")
                public class CamelJBangMain {
                    public static void main(String[] args) {
                        CommandLine commandLine;
                        commandLine = new CommandLine(new CamelJBangMain())
                                .addSubcommand("test", new CommandLine(new TestCommand()));
                    }
                }
                """);

        // Capture warnings via a custom Log
        List<String> warnings = new ArrayList<>();
        Log capturingLog = new SystemStreamLog() {
            @Override
            public void warn(CharSequence content) {
                warnings.add(content.toString());
                super.warn(content);
            }

            @Override
            public void warn(CharSequence content, Throwable error) {
                warnings.add(content.toString());
                super.warn(content, error);
            }
        };

        PrepareCamelJBangCommandsMojo mojo = new PrepareCamelJBangCommandsMojo(null, null);
        mojo.setLog(capturingLog);
        mojo.commandsDir = tempDir.toFile();
        mojo.outFolder = tempDir.resolve("generated-resources").toFile();
        mojo.project = projectWithOutputDir(tempDir.resolve("classes").toFile());

        mojo.execute();

        // The metadata file must have been written
        File metadataFile = tempDir.resolve("generated-resources")
                .resolve(PrepareCamelJBangCommandsMojo.CORE_METADATA_FILE).toFile();
        assertThat(metadataFile).exists();
        assertThat(metadataFile.length()).isGreaterThan(0);

        // No warnings should have been emitted for the interface file
        assertThat(warnings)
                .as("No warnings expected when an interface is encountered in the commands directory")
                .isEmpty();
    }

    /**
     * Verifies that an interface used as a supertype (parent class) in the hierarchy does not produce a WARNING.
     */
    @Test
    void testInterfaceParentTypeDoesNotProduceWarning() throws Exception {
        // A pure interface used as a "parent" (supertype lookup will find this file)
        Files.writeString(tempDir.resolve("TestParentInterface.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                public interface TestParentInterface {
                    void run();
                }
                """);

        Files.writeString(tempDir.resolve("TestChildCommand.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine.Command;
                import picocli.CommandLine.Option;

                @Command(name = "child", description = "A child command")
                public class TestChildCommand implements TestParentInterface {

                    @Option(names = "--output", description = "Output format")
                    String output;

                    public void run() {}
                }
                """);

        Files.writeString(tempDir.resolve("CamelJBangMain.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine;
                import picocli.CommandLine.Command;

                @Command(name = "camel")
                public class CamelJBangMain {
                    public static void main(String[] args) {
                        CommandLine commandLine;
                        commandLine = new CommandLine(new CamelJBangMain())
                                .addSubcommand("child", new CommandLine(new TestChildCommand()));
                    }
                }
                """);

        List<String> warnings = new ArrayList<>();
        Log capturingLog = new SystemStreamLog() {
            @Override
            public void warn(CharSequence content) {
                warnings.add(content.toString());
                super.warn(content);
            }

            @Override
            public void warn(CharSequence content, Throwable error) {
                warnings.add(content.toString());
                super.warn(content, error);
            }
        };

        PrepareCamelJBangCommandsMojo mojo = new PrepareCamelJBangCommandsMojo(null, null);
        mojo.setLog(capturingLog);
        mojo.commandsDir = tempDir.toFile();
        mojo.outFolder = tempDir.resolve("generated-resources").toFile();
        mojo.project = projectWithOutputDir(tempDir.resolve("classes").toFile());

        mojo.execute();

        assertThat(warnings)
                .as("No warnings expected when an interface is encountered as a parent type")
                .isEmpty();
    }

    private static MavenProject projectWithOutputDir(File outputDir) {
        outputDir.mkdirs();
        Build build = new Build();
        build.setOutputDirectory(outputDir.getAbsolutePath());
        MavenProject project = new MavenProject();
        project.setBuild(build);
        return project;
    }
}
