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
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelJBangMainOnlyCommandTest extends CamelCommandBaseTestSupport {

    @BeforeEach
    public void setup() throws Exception {
        super.setup();

        useTestHomeDir();
    }

    @Test
    public void aCommandOfTheCliCreatesOnlyItself() {
        CommandLine commandLine = execute("config", "list");

        assertEquals(Set.of("config"), commandLine.getSubcommands().keySet());
        assertEquals(Set.of("get", "list", "set", "unset"),
                commandLine.getSubcommands().get("config").getSubcommands().keySet());
        assertTrue(CamelJBangMain.hasOnlyCommand(commandLine, "config", "list"));
    }

    @Test
    public void theHelpCreatesAllCommands() {
        assertAllCommands(execute("--help"));
        assertAllCommands(execute());
    }

    @Test
    public void theHelpOfACommandCreatesOnlyThatCommand() {
        CommandLine commandLine = execute("config", "--help");

        assertEquals(Set.of("config"), commandLine.getSubcommands().keySet());
        assertTrue(CamelJBangMain.hasOnlyCommand(commandLine, "config", "--help"));
    }

    @Test
    public void aCommandOfAPluginCreatesNoCommandOfTheCli() {
        CommandLine commandLine = execute(new PluginMain(true), "fake", "--help");

        assertEquals(Set.of("fake"), commandLine.getSubcommands().keySet());
    }

    @Test
    public void aCommandOfAPluginThatAddsNoCommandCreatesAllCommands() {
        // for the commands that picocli suggests
        assertAllCommands(execute(new PluginMain(false), "fake"));
    }

    @Test
    public void aCommandThatIsNotOfTheCliCreatesAllCommands() {
        // the command of a plugin that is not installed, or a typo, for which picocli suggests the commands it knows
        CommandLine commandLine = execute("confg", "list");

        assertAllCommands(commandLine);
        assertFalse(CamelJBangMain.hasOnlyCommand(commandLine, "confg", "list"));
    }

    @Test
    public void theCommandsThatUseTheOtherCommandsOrThePluginsCreateAllCommands() {
        for (String command : List.of("shell", "completion", "run", "export", "cmd")) {
            assertNull(CamelJBangMain.onlyCommand(command, "--foo"), command);
        }
        assertNull(CamelJBangMain.onlyCommand("--verbose", "version"));
        assertEquals("version", CamelJBangMain.onlyCommand("version", "get"));
        assertEquals("version", CamelJBangMain.onlyCommand("version", "--help"));
        assertNull(CamelJBangMain.onlyCommand("--help"));
    }

    @Test
    public void aNewCommandLineHasAllCommands() {
        // camel ask, camel shell and the monitor run any command on a new command line
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        assertAllCommands(main.newCommandLine());
    }

    private CommandLine execute(String... args) {
        return execute(new TestMain(), args);
    }

    private CommandLine execute(TestMain main, String... args) {
        main.withPrinter(printer);
        main.setDiscoverPlugins(false);
        main.execute(args);
        return main.executed;
    }

    private static class TestMain extends CamelJBangMain {
        CommandLine executed;

        @Override
        public void postExecute(CommandLine commandLine, String[] args, int exitCode) {
            executed = commandLine;
        }

        @Override
        public void quit(int exitCode) {
            // noop
        }
    }

    // a main with the plugin of the command fake
    private static class PluginMain extends TestMain {
        private final boolean addsCommand;

        PluginMain(boolean addsCommand) {
            this.addsCommand = addsCommand;
        }

        @Override
        protected boolean isPluginCommand(String command) {
            return "fake".equals(command);
        }

        @Override
        public void postAddCommands(CommandLine commandLine, String[] args) {
            if (addsCommand && "fake".equals(onlyPluginCommand(commandLine, args))) {
                commandLine.addSubcommand("fake", new CommandLine(new FakeCommand()));
            }
        }
    }

    @CommandLine.Command(name = "fake", description = "The command of a plugin")
    static class FakeCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            return 0;
        }
    }

    private static void assertAllCommands(CommandLine commandLine) {
        assertTrue(commandLine.getSubcommands().keySet().containsAll(List.of("config", "get", "run", "shell", "version")),
                () -> "All commands of the CLI, but was: " + commandLine.getSubcommands().keySet());
    }
}
