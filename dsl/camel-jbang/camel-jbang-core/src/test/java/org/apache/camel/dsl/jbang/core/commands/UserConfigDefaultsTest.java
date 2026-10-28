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

import org.apache.camel.dsl.jbang.core.commands.config.BaseConfigTestSupport;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginCommand;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
    void pluginCommandsDoNotUseTheConfiguration() throws Exception {
        UserConfigHelper.createUserConfig("sort=name");

        assertThat(run("plugin", "echo")).isEqualTo("sort=pid repos=null");
    }

    private static String run(String... args) {
        StringPrinter out = new StringPrinter();
        CamelJBangMain main = new CamelJBangMain().withPrinter(out);
        CommandLine commandLine = new CommandLine(main)
                .addSubcommand("echo", new CommandLine(new EchoCommand(main)))
                .addSubcommand("other", new CommandLine(new EchoCommand(main)))
                .addSubcommand("plugin", new CommandLine(new PluginCommand(main))
                        .addSubcommand("echo", new CommandLine(new EchoCommand(main))));
        CommandLineHelper.augmentWithUserConfiguration(commandLine);

        assertThat(commandLine.execute(args)).isZero();
        return out.getStdout();
    }

    // no name of its own: it is the name it is added with
    static class EchoCommand extends CamelCommand {

        @CommandLine.Option(names = { "--sort" }, defaultValue = "pid")
        String sort;

        @CommandLine.Option(names = { "--repos" })
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
}
