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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.config.BaseConfigTestSupport;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginCommand;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The user configuration (camel config set) gives the default values of the options of the commands.
 */
class UserConfigDefaultsTest extends BaseConfigTestSupport {

    @Test
    void configurationIsTheDefaultOfEveryCommandWithTheOption() throws Exception {
        UserConfigHelper.createUserConfig("sort=name");

        assertThat(run("echo")).isEqualTo("sort=name repos=null");
        assertThat(run("other")).isEqualTo("sort=name repos=null");
    }

    @Test
    void optionOnTheCommandLineWins() throws Exception {
        UserConfigHelper.createUserConfig("sort=name");

        assertThat(run("echo", "--sort=age")).isEqualTo("sort=age repos=null");
    }

    @Test
    void configurationOfOneCommand() throws Exception {
        UserConfigHelper.createUserConfig("camel.echo.sort=age\nsort=name");

        assertThat(run("echo")).isEqualTo("sort=age repos=null");
        assertThat(run("other")).isEqualTo("sort=name repos=null");
    }

    @Test
    void withoutConfigurationTheOptionHasItsOwnDefault() throws Exception {
        UserConfigHelper.createUserConfig("");

        assertThat(run("echo")).isEqualTo("sort=pid repos=null");
    }

    @Test
    void placeholderOfTheConfiguredValue() throws Exception {
        UserConfigHelper.createUserConfig("repos=https://repo.example.com/ga");

        assertThat(run("echo", "--repos=#repos,https://other.example.com/maven"))
                .isEqualTo("sort=pid repos=https://repo.example.com/ga,https://other.example.com/maven");
    }

    @Test
    void localConfigurationTakesPrecedence() throws Exception {
        UserConfigHelper.createUserConfig("sort=name");
        UserConfigHelper.createUserConfig("sort=age", true);

        assertThat(run("echo")).isEqualTo("sort=age repos=null");
    }

    @Test
    void malformedUserConfigDoesNotCrashCli() throws Exception {
        // A Windows-style path stored without escaping produces a malformed \\uXXXX sequence,
        // which throws IllegalArgumentException from Properties.load — not IOException.
        // The CLI must warn and continue instead of propagating the exception.
        UserConfigHelper.createUserConfig("path=C:\\users\\name");

        // The command must succeed (not throw); the option falls back to its own default.
        assertThat(runCapturingWarning("echo")).isEqualTo("sort=pid repos=null");
    }

    @Test
    void malformedConfigLoadPropertiesReturnsFalseAndWarns() throws Exception {
        // loadProperties must not throw and must return false for a malformed config file
        UserConfigHelper.createUserConfig("path=C:\\users\\name");

        StringPrinter out = new StringPrinter();
        boolean[] loaded = { true };
        loaded[0] = CommandLineHelper.loadProperties(props -> {
            // should NOT be called since the 3-arg overload does not call consumer on failure
            loaded[0] = true;
        }, false, out);

        assertThat(loaded[0]).as("loadProperties should return false for a malformed file").isFalse();
        assertThat(out.getStderr()).contains("could not parse");
        assertThat(out.getStderr()).contains("escaping");
    }

    @Test
    void malformedConfigLoadPropertiesTwoArgCallsConsumerWithEmpty() throws Exception {
        // The 2-arg overload (used by read-only callers) calls consumer with empty props
        UserConfigHelper.createUserConfig("path=C:\\users\\name");

        boolean[] consumerCalled = { false };
        CommandLineHelper.loadProperties(props -> {
            consumerCalled[0] = true;
            assertThat(props).isEmpty();
        }, false);

        assertThat(consumerCalled[0]).as("2-arg overload should call consumer with empty props").isTrue();
    }

    private static String runCapturingWarning(String... args) {
        StringPrinter out = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(out);
        CommandTree commandLine = new CommandTree(main)
                .addSubcommand("echo", new CommandTree(new EchoCommand(main)))
                .addSubcommand("other", new CommandTree(new EchoCommand(main)))
                .addSubcommand("dev", new CommandTree(new DevCommand(main)))
                .addSubcommand("plugin", new CommandTree(new PluginCommand(main))
                        .addSubcommand("echo", new CommandTree(new EchoCommand(main))));
        // augmentWithUserConfiguration must NOT throw for a malformed config
        CommandLineHelper.augmentWithUserConfiguration(commandLine, out);

        try {
            assertThat(commandLine.execute(args).getResultValue()).isZero();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.getStdout();
    }

    @Test
    void pluginCommandsDoNotUseTheConfiguration() throws Exception {
        UserConfigHelper.createUserConfig("sort=name");

        assertThat(run("plugin", "echo")).isEqualTo("sort=pid repos=null");
    }

    @Test
    void everyLongNameOfAnOption() throws Exception {
        UserConfigHelper.createUserConfig("dev=true");
        assertThat(run("dev")).isEqualTo("dev=true");

        UserConfigHelper.createUserConfig("reload=true");
        assertThat(run("dev")).isEqualTo("dev=true");

        UserConfigHelper.createUserConfig("camel.dev.dev=true");
        assertThat(run("dev")).isEqualTo("dev=true");
    }

    @Test
    void configurationOfOneCommandWinsForAnyName() throws Exception {
        UserConfigHelper.createUserConfig("reload=true\ncamel.dev.dev=false");

        assertThat(run("dev")).isEqualTo("dev=false");
    }

    private static String run(String... args) {
        StringPrinter out = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(out);
        CommandTree commandLine = new CommandTree(main)
                .addSubcommand("echo", new CommandTree(new EchoCommand(main)))
                .addSubcommand("other", new CommandTree(new EchoCommand(main)))
                .addSubcommand("dev", new CommandTree(new DevCommand(main)))
                .addSubcommand("plugin", new CommandTree(new PluginCommand(main))
                        .addSubcommand("echo", new CommandTree(new EchoCommand(main))));
        CommandLineHelper.augmentWithUserConfiguration(commandLine);

        try {
            assertThat(commandLine.execute(args).getResultValue()).isZero();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.getStdout();
    }

    // added under other names too: it has the name it is added with
    @CommandDefinition(name = "echo", description = "Prints its options")
    static class EchoCommand extends CamelCommand {

        @Option(name = "sort", defaultValue = "pid")
        String sort;

        @Option(name = "repos")
        String repos;

        EchoCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        protected boolean disarrangeLogging() {
            return false;
        }

        @Override
        public Integer doCall() {
            printer().println("sort=" + sort + " repos=" + repos);
            return 0;
        }
    }

    // an option with two long names, as --reload,--dev of camel run
    @CommandDefinition(name = "dev", description = "Prints its option")
    static class DevCommand extends CamelCommand {

        @Option(name = "reload", aliases = { "dev" }, hasValue = false)
        boolean dev;

        DevCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        protected boolean disarrangeLogging() {
            return false;
        }

        @Override
        public Integer doCall() {
            printer().println("dev=" + dev);
            return 0;
        }
    }
}
