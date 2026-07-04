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
import java.util.Map;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroupedCommandHelpRendererTest extends CamelCommandBaseTestSupport {

    private CommandLine capturedCommandLine;

    private String captureHelp() {
        StringWriter sw = new StringWriter();
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
            }

            @Override
            public void postAddCommands(CommandLine commandLine, String[] args) {
                commandLine.setOut(new PrintWriter(sw));
                capturedCommandLine = commandLine;
            }
        }.withPrinter(printer);
        main.setDiscoverPlugins(false);
        main.execute("--help");
        return sw.toString();
    }

    @Test
    public void helpOutputContainsGroups() throws Exception {
        String output = captureHelp();

        assertTrue(output.contains("Running:"), "Missing 'Running' group");
        assertTrue(output.contains("Monitoring:"), "Missing 'Monitoring' group");
        assertTrue(output.contains("Actions:"), "Missing 'Actions' group");
        assertTrue(output.contains("Development:"), "Missing 'Development' group");
        assertTrue(output.contains("Configuration:"), "Missing 'Configuration' group");
        assertTrue(output.contains("Catalog:"), "Missing 'Catalog' group");
        assertTrue(output.contains("AI:"), "Missing 'AI' group");
    }

    @Test
    public void helpOutputContainsKeyCommands() throws Exception {
        String output = captureHelp();

        assertTrue(output.contains("run"), "Missing 'run' command");
        assertTrue(output.contains("get"), "Missing 'get' command");
        assertTrue(output.contains("config"), "Missing 'config' command");
        assertTrue(output.contains("catalog"), "Missing 'catalog' command");
    }

    @Test
    public void noBuiltInCommandFallsIntoOther() throws Exception {
        String output = captureHelp();

        // Every command should belong to a category, so we never expect to see "Other".
        // If this fails, someone added a command but forgot to put it in a group.
        assertFalse(output.contains("Other:"),
                "An unmapped command fell into the 'Other' group — add it to a category in GroupedCommandHelpRenderer");
    }

    @Test
    public void allRegisteredCommandsAppearInHelp() throws Exception {
        String output = captureHelp();

        // Make sure no command quietly goes missing: every registered, non-hidden
        // command should appear somewhere in the grouped output.
        for (Map.Entry<String, CommandLine> entry : capturedCommandLine.getSubcommands().entrySet()) {
            if (entry.getValue().getCommandSpec().usageMessage().hidden()) {
                continue;
            }
            String name = entry.getValue().getCommandSpec().name();
            assertTrue(output.contains(name), "Command '" + name + "' is missing from the grouped help output");
        }
    }
}
