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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.camel.tooling.model.JBangCommandModel.JBangCommand;
import org.apache.camel.tooling.model.JBangCommandModel.JBangCommandOption;
import org.apache.camel.tooling.model.JsonMapper;
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

    /**
     * CAMEL-25717: a plugin command whose parent class is of a library does not get the options of the command of
     * camel-jbang-core with the simple name of that class, as camel test init (whose parent is the Init of Citrus) got
     * the options of camel init.
     */
    @Test
    void testPluginCommandDoesNotGetTheOptionsOfTheCoreCommandWithTheNameOfItsParent() throws Exception {
        JBangCommand init = generatePluginWithLibraryParent();

        assertThat(init.getOptions()).extracting(JBangCommandOption::getNames).doesNotContain("--from-kamelet");
    }

    /**
     * CAMEL-25717: a plugin command whose parent class is of a library gets the options of that class, and of its
     * parent classes, from the classpath of the project.
     */
    @Test
    void testPluginCommandGetsTheOptionsOfTheLibraryClassItExtends() throws Exception {
        compileLibrary();

        JBangCommand init = generatePluginWithLibraryParent();

        assertThat(init.getOptions()).extracting(JBangCommandOption::getNames).containsExactly("--directory", "-h,--help");
        JBangCommandOption directory = init.getOptions().get(0);
        assertThat(directory.getDescription()).isEqualTo("Directory where the files will be created");
        assertThat(directory.getDefaultValue()).isEqualTo(".");
        assertThat(directory.getJavaType()).isEqualTo("java.lang.String");
    }

    /**
     * A parent class of the same package is found when the class has a static import on demand, for which Roaster
     * resolves the parent class as a nested class of the imported one (as camel export, whose ExportBaseCommand has
     * {@code import static ...CamelJBangConstants.*}).
     */
    @Test
    void testParentOfTheSamePackageWithAStaticImportOnDemand() throws Exception {
        Path commands = tempDir.resolve("commands");
        write(commands.resolve("BaseCommand.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine.Option;

                public abstract class BaseCommand {

                    @Option(names = "--verbose", description = "Verbose output")
                    boolean verbose;
                }
                """);
        write(commands.resolve("Export.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine.Command;
                import picocli.CommandLine.Option;

                import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.*;

                @Command(name = "export", description = "Export to other runtimes")
                public class Export extends BaseCommand {

                    @Option(names = "--dir", description = "Directory where the project will be exported")
                    String dir;
                }
                """);
        write(commands.resolve("CamelJBangMain.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine;
                import picocli.CommandLine.Command;

                @Command(name = "camel")
                public class CamelJBangMain {
                    public static void main(String[] args) {
                        CommandLine commandLine;
                        commandLine = new CommandLine(new CamelJBangMain())
                                .addSubcommand("export", new CommandLine(new Export()));
                    }
                }
                """);

        PrepareCamelJBangCommandsMojo mojo = new PrepareCamelJBangCommandsMojo(null, null);
        mojo.commandsDir = commands.toFile();
        mojo.outFolder = tempDir.resolve("generated-resources").toFile();
        mojo.project = projectWithOutputDir(tempDir.resolve("classes").toFile());

        mojo.execute();

        String json = Files.readString(
                tempDir.resolve("generated-resources").resolve(PrepareCamelJBangCommandsMojo.CORE_METADATA_FILE));
        JBangCommand export = JsonMapper.generateJBangCommandModel(json).getCommands().get(0);
        assertThat(export.getOptions()).extracting(JBangCommandOption::getNames).containsExactly("--dir", "--verbose");
    }

    /**
     * A plugin as camel-jbang-plugin-test: camel test init, whose parent class Init is of a library, while
     * camel-jbang-core has a command class Init too. Returns the generated test init command.
     */
    private JBangCommand generatePluginWithLibraryParent() throws Exception {
        Path core = tempDir.resolve("core/commands");
        write(core.resolve("Init.java"), """
                package org.apache.camel.dsl.jbang.core.commands;

                import picocli.CommandLine.Command;
                import picocli.CommandLine.Option;

                @Command(name = "init", description = "Creates a new Camel integration")
                public class Init {

                    @Option(names = "--from-kamelet", description = "To be used when extending an existing Kamelet")
                    String fromKamelet;
                }
                """);
        Path plugin = tempDir.resolve("plugin/commands");
        write(plugin.resolve("test/TestPlugin.java"), """
                package org.apache.camel.dsl.jbang.core.commands.test;

                import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
                import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
                import org.apache.camel.dsl.jbang.core.common.Plugin;
                import picocli.CommandLine;

                @CamelJBangPlugin(name = "camel-jbang-plugin-test", firstVersion = "4.14.0")
                public class TestPlugin implements Plugin {

                    @Override
                    public void customize(CommandLine commandLine, CamelJBangMain main) {
                        var cmd = new CommandLine(new TestCommand(main))
                                .addSubcommand("init", new CommandLine(new TestInit(main)));

                        commandLine.addSubcommand("test", cmd);
                    }
                }
                """);
        write(plugin.resolve("test/TestCommand.java"), """
                package org.apache.camel.dsl.jbang.core.commands.test;

                import picocli.CommandLine.Command;

                @Command(name = "test", description = "Manage tests")
                public class TestCommand {
                }
                """);
        write(plugin.resolve("test/TestInit.java"), """
                package org.apache.camel.dsl.jbang.core.commands.test;

                import org.example.cli.Init;
                import picocli.CommandLine.Command;

                @Command(name = "init", description = "Creates a new test")
                public class TestInit extends Init {
                }
                """);

        PrepareCamelJBangCommandsMojo mojo = new PrepareCamelJBangCommandsMojo(null, null);
        mojo.commandsDir = plugin.toFile();
        mojo.coreCommandsDir = core.toFile();
        mojo.outFolder = tempDir.resolve("generated-resources").toFile();
        mojo.project = projectWithOutputDir(tempDir.resolve("classes").toFile());

        mojo.execute();

        String json = Files.readString(
                tempDir.resolve("generated-resources").resolve(PrepareCamelJBangCommandsMojo.PLUGIN_METADATA_FILE));
        JBangCommand test = JsonMapper.generateJBangCommandModel(json).getCommands().get(0);
        assertThat(test.getSubcommands()).extracting(JBangCommand::getFullName).containsExactly("test init");
        return test.getSubcommands().get(0);
    }

    /**
     * Compiles the library of the command classes into the classes of the project. picocli is not a dependency of this
     * plugin, so the library has the annotations of picocli that the plugin reads.
     */
    private void compileLibrary() throws IOException {
        Path sources = tempDir.resolve("library");
        write(sources.resolve("picocli/CommandLine.java"), """
                package picocli;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                public class CommandLine {

                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.FIELD)
                    public @interface Option {
                        String[] names();

                        String[] description() default {};

                        String defaultValue() default "__no_default_value__";

                        boolean required() default false;

                        boolean hidden() default false;

                        String paramLabel() default "";
                    }

                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.FIELD)
                    public @interface Mixin {
                    }
                }
                """);
        write(sources.resolve("org/example/cli/LibraryCommand.java"), """
                package org.example.cli;

                import picocli.CommandLine;

                public abstract class LibraryCommand {

                    @CommandLine.Option(names = { "-h", "--help" }, description = "Display the help and sub-commands")
                    boolean help;
                }
                """);
        write(sources.resolve("org/example/cli/Init.java"), """
                package org.example.cli;

                import picocli.CommandLine;

                public class Init extends LibraryCommand {

                    @CommandLine.Option(names = "--directory", description = "Directory where the files will be created",
                                        defaultValue = ".")
                    String directory;
                }
                """);
        Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
             Stream<Path> files = Files.walk(sources)) {
            List<File> javaFiles = files.filter(f -> f.toString().endsWith(".java")).map(Path::toFile).toList();
            Boolean compiled = compiler.getTask(null, fileManager, null, List.of("-d", classes.toString()), null,
                    fileManager.getJavaFileObjectsFromFiles(javaFiles)).call();
            assertThat(compiled).isTrue();
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
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
