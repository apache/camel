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
        assertAllCommands(execute("config", "--help"));
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
    }

    @Test
    public void aNewCommandLineHasAllCommands() {
        // camel ask, camel shell and the monitor run any command on a new command line
        CamelJBangMain main = new CamelJBangMain().withPrinter(printer);

        assertAllCommands(main.newCommandLine());
    }

    private CommandLine execute(String... args) {
        CommandLine[] executed = new CommandLine[1];
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void postExecute(CommandLine commandLine, String[] args, int exitCode) {
                executed[0] = commandLine;
            }

            @Override
            public void quit(int exitCode) {
                // noop
            }
        }.withPrinter(printer);
        main.setDiscoverPlugins(false);
        main.execute(args);
        return executed[0];
    }

    private static void assertAllCommands(CommandLine commandLine) {
        assertTrue(commandLine.getSubcommands().keySet().containsAll(List.of("config", "get", "run", "shell", "version")),
                () -> "All commands of the CLI, but was: " + commandLine.getSubcommands().keySet());
    }
}
