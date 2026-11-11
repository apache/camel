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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class CommandMetadataTest {

    @Test
    void commandsOfTheCli() {
        List<Map<String, Object>> commands = CommandMetadata.commands();

        // added under another name than the name of its @Command (complete)
        Map<String, Object> completion = command(commands, "completion");
        assertThat(completion.get("sourceClass")).isEqualTo(Complete.class.getCanonicalName());
        assertThat(subcommands(command(commands, "get"))).extracting(c -> c.get("name")).contains("group", "route");

        Map<String, Object> run = command(commands, "run");
        assertThat(run.get("hidden")).isEqualTo(false);
        Map<String, Object> dev = option(run, "--reload,--dev");
        assertThat(dev.get("javaType")).isEqualTo("boolean");
        assertThat(dev.get("hidden")).isEqualTo(false);
        assertThat(option(run, "--dep,--dependency").get("javaType")).isEqualTo("java.util.List");
    }

    @Test
    void valuesAsWrittenInTheAnnotations() {
        List<Map<String, Object>> commands = CommandMetadata.commands();

        // not filled in by picocli from the system properties of the JVM
        Map<String, Object> add = command(subcommands(command(commands, "plugin")), "add");
        assertThat(option(add, "--version").get("defaultValue")).isEqualTo("${camel-version}");

        // the completion candidates for a description that lists them, and the description as written
        Map<String, Object> receive = command(subcommands(command(commands, "cmd")), "receive");
        Map<String, Object> output = option(receive, "--output");
        assertThat(output.get("description")).isEqualTo("Output format (${COMPLETION-CANDIDATES})");
        assertThat(output.get("completionCandidates")).isEqualTo(List.of("text", "json"));
        assertThat(option(receive, "--grep").get("javaType")).isEqualTo("java.lang.String[]");
    }

    @Test
    void commandsOfAPlugin() throws Exception {
        List<Map<String, Object>> commands = CommandMetadata.pluginCommands(FooPlugin.class);

        assertThat(commands).hasSize(1);
        Map<String, Object> foo = commands.get(0);
        assertThat(foo.get("name")).isEqualTo("foo");
        assertThat(foo.get("description")).isEqualTo("The foo commands");
        assertThat(foo.get("sourceClass")).isEqualTo(FooCommand.class.getCanonicalName());
        assertThat(option(foo, "--profile").get("defaultValue")).isEqualTo("dev");
        assertThat(option(foo, "--secret").get("hidden")).isEqualTo(true);
        assertThat(option(foo, "--secret").get("paramLabel")).isNull();
        assertThat(option(foo, "--label").get("paramLabel")).isEqualTo("<key=value>");
        assertThat(option(foo, "--label").get("required")).isEqualTo(true);

        // once, not again under its alias
        assertThat(subcommands(foo)).extracting(c -> c.get("name")).containsExactly("bar");
        Map<String, Object> bar = subcommands(foo).get(0);
        assertThat(bar.get("hidden")).isEqualTo(true);
        assertThat(bar.get("deprecated")).isEqualTo(true);
        // the option of the parent for its sub-commands is documented with the parent
        assertThat(options(bar)).extracting(o -> o.get("names")).containsExactly("--name");
    }

    private static Map<String, Object> command(List<Map<String, Object>> commands, String name) {
        return commands.stream().filter(c -> name.equals(c.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError("No command " + name));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> subcommands(Map<String, Object> command) {
        return (List<Map<String, Object>>) command.get("subcommands");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> options(Map<String, Object> command) {
        return (List<Map<String, Object>>) command.get("options");
    }

    private static Map<String, Object> option(Map<String, Object> command, String names) {
        return options(command).stream().filter(o -> names.equals(o.get("names"))).findFirst()
                .orElseThrow(() -> new AssertionError("No option " + names + " in " + command.get("name")));
    }

    public static class FooPlugin implements Plugin {

        @Override
        public void customize(CommandLine commandLine, CamelJBangMain main) {
            commandLine.addSubcommand("foo", new CommandLine(new FooCommand())
                    .addSubcommand("bar", new CommandLine(new BarCommand())));
        }
    }

    @CommandLine.Command(name = "foo", description = "The foo commands")
    public static class FooCommand implements Callable<Integer> {

        @CommandLine.Option(names = "--profile", scope = CommandLine.ScopeType.INHERIT, defaultValue = "dev",
                            description = "Profile")
        String profile;

        @CommandLine.Option(names = "--secret", hidden = true, description = "Not documented")
        String secret;

        @CommandLine.Option(names = "--label", paramLabel = "<key=value>", required = true, description = "A label")
        String label;

        @Override
        public Integer call() {
            return 0;
        }
    }

    @Deprecated
    @CommandLine.Command(name = "bar", aliases = "baz", hidden = true, description = "The bar command")
    public static class BarCommand implements Callable<Integer> {

        @CommandLine.Option(names = "--name", description = "The name")
        String name;

        @Override
        public Integer call() {
            return 0;
        }
    }
}
