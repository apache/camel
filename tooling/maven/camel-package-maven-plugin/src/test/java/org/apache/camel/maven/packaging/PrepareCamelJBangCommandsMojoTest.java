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
import java.util.List;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.camel.tooling.model.JBangCommandModel;
import org.apache.camel.tooling.model.JBangCommandModel.JBangCommand;
import org.apache.camel.tooling.model.JBangCommandModel.JBangCommandOption;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The metadata of the commands is read with the CommandMetadata class of camel-jbang-core, from the classes of the
 * project. The tests compile a CommandMetadata that gives fixed commands into the classes of the project.
 */
class PrepareCamelJBangCommandsMojoTest {

    // what the CommandMetadata of camel-jbang-core gives, built with the helpers command(...) and option(...)
    private static final String COMMANDS = """
            List.of(
                command("get", "Get status of Camel integrations (use get --help to see sub commands)", false,
                    List.of(
                        option("--watch", "Execute periodically and showing output fullscreen", "false", "boolean"),
                        option("--sort", "Sort by (${COMPLETION-CANDIDATES})", null, "java.lang.String",
                               List.of("pid", "name", "age")),
                        hidden(option("--secret", "Not documented", null, "java.lang.String"))),
                    List.of(command("group", "Get status of Camel route groups", false, List.of(), List.of()))),
                command("bind", "Bind source and sink", false,
                    List.of(option("--version", "The version", "${camel-version}", "java.lang.String"),
                            option("--step", "Optional steps such as a Kamelet or Camel endpoint uri", null,
                                   "java.lang.String[]")),
                    List.of()),
                command("internal", "Hidden command", true, List.of(),
                    List.of(command("sub", "Not documented either", false, List.of(), List.of()))))
            """;

    @TempDir
    Path tempDir;

    @Test
    void testCoreCommands() throws Exception {
        compileCommandMetadata("return " + COMMANDS + ";", "throw new UnsupportedOperationException();");
        Path commandsDir = Files.createDirectories(tempDir.resolve("commands"));
        Files.writeString(commandsDir.resolve("CamelJBangMain.java"), "");

        JBangCommandModel model = generate(commandsDir, PrepareCamelJBangCommandsMojo.CORE_METADATA_FILE);

        // sorted by name, without the hidden command and its sub-commands
        assertThat(model.getCommands()).extracting(JBangCommand::getName).containsExactly("bind", "get");

        JBangCommand get = model.getCommands().get(1);
        assertThat(get.getFullName()).isEqualTo("get");
        assertThat(get.getDescription()).isEqualTo("Get status of Camel integrations");
        assertThat(get.getSourceClass()).isEqualTo("org.example.Get");
        assertThat(get.getPlugin()).isNull();
        assertThat(get.getSubcommands()).extracting(JBangCommand::getFullName).containsExactly("get group");

        // sorted by name, without the hidden option
        assertThat(get.getOptions()).extracting(JBangCommandOption::getNames).containsExactly("--sort", "--watch");
        JBangCommandOption sort = get.getOptions().get(0);
        assertThat(sort.getDescription()).isEqualTo("Sort by (pid, name, age)");
        assertThat(sort.getDefaultValue()).isNull();
        JBangCommandOption watch = get.getOptions().get(1);
        assertThat(watch.getDefaultValue()).isEqualTo("false");
        assertThat(watch.getType()).isEqualTo("boolean");

        JBangCommand bind = model.getCommands().get(0);
        JBangCommandOption step = bind.getOptions().get(0);
        assertThat(step.getNames()).isEqualTo("--step");
        assertThat(step.getJavaType()).isEqualTo("java.lang.String[]");
        assertThat(step.getShortJavaType()).isEqualTo("String[]");
        assertThat(step.getType()).isEqualTo("array");
        // a placeholder that AsciiDoctor would read as an attribute is left out
        assertThat(bind.getOptions().get(1).getDefaultValue()).isEqualTo("");
    }

    @Test
    void testPluginCommands() throws Exception {
        compileCommandMetadata("throw new UnsupportedOperationException();",
                """
                        return List.of(command("foo", "Commands of " + pluginClass.getSimpleName(), false, List.of(),
                                List.of(command("bar", "Bar", false, List.of(), List.of()))));""");
        compile("org/example/FooPlugin.java", """
                package org.example;

                public class FooPlugin {
                }
                """);
        Path factory = tempDir.resolve(
                "generated-resources/META-INF/services/org/apache/camel/camel-jbang-plugin/camel-jbang-plugin-foo");
        Files.createDirectories(factory.getParent());
        Files.writeString(factory, "# Generated by camel build tools - do NOT edit this file!\nclass=org.example.FooPlugin\n");

        JBangCommandModel model = generate(tempDir.resolve("commands"), PrepareCamelJBangCommandsMojo.PLUGIN_METADATA_FILE);

        JBangCommand foo = model.getCommands().get(0);
        assertThat(foo.getDescription()).isEqualTo("Commands of FooPlugin");
        assertThat(foo.getPlugin()).isEqualTo("foo");
        JBangCommand bar = foo.getSubcommands().get(0);
        assertThat(bar.getFullName()).isEqualTo("foo bar");
        assertThat(bar.getPlugin()).isEqualTo("foo");
    }

    @Test
    void testModuleWithoutCommands() throws Exception {
        mojo(tempDir.resolve("commands")).execute();

        assertThat(tempDir.resolve("generated-resources")).doesNotExist();
    }

    @Test
    void testErrorReadingTheCommands() throws Exception {
        compileCommandMetadata("throw new IllegalStateException(\"Cannot create the commands\");",
                "throw new UnsupportedOperationException();");
        Path commandsDir = Files.createDirectories(tempDir.resolve("commands"));
        Files.writeString(commandsDir.resolve("CamelJBangMain.java"), "");

        assertThatThrownBy(() -> mojo(commandsDir).execute())
                .isInstanceOf(MojoFailureException.class)
                .cause().isInstanceOf(IllegalStateException.class).hasMessage("Cannot create the commands");
    }

    @Test
    void testSanitizeDescription() {
        assertThat(PrepareCamelJBangCommandsMojo.sanitizeDescription("Version (default: ${camel-version})"))
                .isEqualTo("Version");
        assertThat(PrepareCamelJBangCommandsMojo.sanitizeDescription("Format (supports: ${COMPLETION-CANDIDATES})"))
                .isEqualTo("Format");
        // read from the command model, not from the source: a plus or a quote is part of the text
        assertThat(PrepareCamelJBangCommandsMojo.sanitizeDescription("0=Created+Completed, the \"default\""))
                .isEqualTo("0=Created+Completed, the \"default\"");
        assertThat(PrepareCamelJBangCommandsMojo.sanitizeDescription(null)).isNull();
    }

    private JBangCommandModel generate(Path commandsDir, String metadataFile) throws Exception {
        mojo(commandsDir).execute();
        String json = Files.readString(tempDir.resolve("generated-resources").resolve(metadataFile));
        return JsonMapper.generateJBangCommandModel(json);
    }

    private PrepareCamelJBangCommandsMojo mojo(Path commandsDir) {
        PrepareCamelJBangCommandsMojo mojo = new PrepareCamelJBangCommandsMojo(null, null);
        mojo.commandsDir = commandsDir.toFile();
        mojo.outFolder = tempDir.resolve("generated-resources").toFile();
        mojo.project = projectWithOutputDir(tempDir.resolve("classes").toFile());
        return mojo;
    }

    /**
     * Compiles a CommandMetadata into the classes of the project, with the given bodies of its two methods.
     */
    private void compileCommandMetadata(String commands, String pluginCommands) throws IOException {
        compile("org/apache/camel/dsl/jbang/core/commands/CommandMetadata.java", """
                package org.apache.camel.dsl.jbang.core.commands;

                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;

                public final class CommandMetadata {

                    public static List<Map<String, Object>> commands() {
                        %s
                    }

                    public static List<Map<String, Object>> pluginCommands(Class<?> pluginClass) {
                        %s
                    }

                    static Map<String, Object> command(
                            String name, String description, boolean hidden, List<Map<String, Object>> options,
                            List<Map<String, Object>> subcommands) {
                        Map<String, Object> answer = new HashMap<>();
                        answer.put("name", name);
                        answer.put("description", description);
                        answer.put("hidden", hidden);
                        answer.put("deprecated", false);
                        answer.put("sourceClass", "org.example." + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                        answer.put("options", options);
                        answer.put("subcommands", subcommands);
                        return answer;
                    }

                    static Map<String, Object> option(String names, String description, String defaultValue, String javaType) {
                        return option(names, description, defaultValue, javaType, null);
                    }

                    static Map<String, Object> option(
                            String names, String description, String defaultValue, String javaType, List<String> candidates) {
                        Map<String, Object> answer = new HashMap<>();
                        answer.put("names", names);
                        answer.put("description", description);
                        answer.put("defaultValue", defaultValue);
                        answer.put("javaType", javaType);
                        answer.put("required", false);
                        answer.put("hidden", false);
                        answer.put("deprecated", false);
                        answer.put("paramLabel", null);
                        answer.put("completionCandidates", candidates);
                        return answer;
                    }

                    static Map<String, Object> hidden(Map<String, Object> option) {
                        option.put("hidden", true);
                        return option;
                    }
                }
                """.formatted(commands, pluginCommands));
    }

    private void compile(String path, String source) throws IOException {
        Path sources = tempDir.resolve("sources");
        Path file = sources.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        Path classes = Files.createDirectories(tempDir.resolve("classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
             Stream<Path> files = Files.walk(sources)) {
            List<File> javaFiles = files.filter(f -> f.toString().endsWith(".java")).map(Path::toFile).toList();
            Boolean compiled = compiler.getTask(null, fileManager, null, List.of("-d", classes.toString()), null,
                    fileManager.getJavaFileObjectsFromFiles(javaFiles)).call();
            assertThat(compiled).isTrue();
        }
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
