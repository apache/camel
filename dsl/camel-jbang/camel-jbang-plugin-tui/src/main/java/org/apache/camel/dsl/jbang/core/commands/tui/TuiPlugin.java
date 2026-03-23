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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-tui", firstVersion = "4.19.0",
                  commands = { "health", "monitor", "top", "trace", "log", "catalog" })
public class TuiPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        // Top-level TUI commands
        commandLine.addSubcommand("health", new CommandLine(new CamelHealthTui(main)));
        commandLine.addSubcommand("monitor", new CommandLine(new CamelMonitor(main)));

        // Subcommands of existing commands
        CommandLine topCmd = commandLine.getSubcommands().get("top");
        if (topCmd != null) {
            topCmd.addSubcommand("tui", new CommandLine(new CamelTopTui(main)));
        }
        CommandLine traceCmd = commandLine.getSubcommands().get("trace");
        if (traceCmd != null) {
            traceCmd.addSubcommand("tui", new CommandLine(new CamelTraceTui(main)));
        }
        CommandLine logCmd = commandLine.getSubcommands().get("log");
        if (logCmd != null) {
            logCmd.addSubcommand("tui", new CommandLine(new CamelLogTui(main)));
        }
        CommandLine catalogCmd = commandLine.getSubcommands().get("catalog");
        if (catalogCmd != null) {
            catalogCmd.addSubcommand("tui", new CommandLine(new CamelCatalogTui(main)));
        }
    }
}
