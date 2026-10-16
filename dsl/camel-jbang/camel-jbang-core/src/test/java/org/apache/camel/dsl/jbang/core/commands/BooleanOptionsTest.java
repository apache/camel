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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A boolean option given without a value is on. picocli sets a flag to the opposite of its default, so an option that
 * is on by default needs <tt>arity = "0..1", fallbackValue = "true"</tt>: otherwise <tt>--maven-wrapper</tt> would turn
 * the Maven wrapper off.
 */
class BooleanOptionsTest extends CamelCommandBaseTestSupport {

    private CommandLine commandLine;

    @BeforeEach
    void commandLine() {
        StringWriter help = new StringWriter();
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
                // in the test
            }

            @Override
            public void postAddCommands(CommandLine commandLine, String[] args) {
                commandLine.setOut(new PrintWriter(help));
                BooleanOptionsTest.this.commandLine = commandLine;
            }
        }.withPrinter(printer);
        main.setDiscoverPlugins(false);
        main.execute("--help");
    }

    @Test
    void everyOptionThatIsOnByDefaultIsOnWhenGivenWithoutValue() {
        List<String> wrong = new ArrayList<>();
        check(commandLine, wrong);

        assertThat(wrong).as("boolean options that are on by default, without arity = \"0..1\", fallbackValue = \"true\"")
                .isEmpty();
    }

    @Test
    void flagWithoutValue() {
        assertThat(option("--maven-wrapper", "export", "--maven-wrapper")).isEqualTo(true);
        assertThat(option("--maven-wrapper", "export", "--maven-wrapper=false")).isEqualTo(false);
        assertThat(option("--maven-wrapper", "export")).isEqualTo(true);

        // the local configuration is written only with --global=false
        assertThat(option("--global", "config", "set", "--global", "foo=bar")).isEqualTo(true);
        assertThat(option("--global", "config", "set", "--global=false", "foo=bar")).isEqualTo(false);

        // the name after the flag is not taken as its value
        CommandSpec log = parse("log", "--timestamp", "orders");
        assertThat(log.findOption("--timestamp").<Boolean> getValue()).isTrue();
        assertThat(log.positionalParameters().get(0).<String> getValue()).isEqualTo("orders");
    }

    // the value of the option of the command, once the command line is parsed
    private Object option(String name, String... args) {
        return parse(args).findOption(name).getValue();
    }

    private CommandSpec parse(String... args) {
        CommandLine.ParseResult result = commandLine.parseArgs(args);
        while (result.hasSubcommand()) {
            result = result.subcommand();
        }
        return result.commandSpec();
    }

    private static void check(CommandLine commandLine, List<String> wrong) {
        CommandSpec spec = commandLine.getCommandSpec();
        for (OptionSpec option : spec.options()) {
            boolean flag = option.type() == boolean.class || option.type() == Boolean.class;
            boolean onByDefault = "true".equals(option.defaultValue()) || Boolean.TRUE.equals(option.initialValue());
            if (flag && onByDefault && !(option.arity().min() == 0 && "true".equals(option.fallbackValue()))) {
                wrong.add(spec.qualifiedName(" ") + " " + option.longestName());
            }
        }
        commandLine.getSubcommands().values().forEach(sub -> check(sub, wrong));
    }
}
