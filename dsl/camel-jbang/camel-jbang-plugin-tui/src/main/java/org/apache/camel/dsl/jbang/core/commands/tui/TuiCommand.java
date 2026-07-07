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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

@CommandLine.Command(name = "tui", description = "Camel TUI (use --help to see sub commands)")
public class TuiCommand extends CamelCommand {

    private ClassLoader classLoader;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name;

    @CommandLine.Option(names = { "--mcp" },
                        description = "Enable embedded MCP server for AI agent access to the TUI")
    boolean mcp;

    @CommandLine.Option(names = { "--mcp-port" },
                        description = "MCP server port (default: ${DEFAULT-VALUE})",
                        defaultValue = "8123")
    int mcpPort = 8123;

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "100")
    long refreshInterval = 100;

    @CommandLine.Option(names = { "--record" },
                        description = "Replay a .tape file inside the TUI and record to an Asciinema .cast file",
                        arity = "0..1")
    String record;

    @CommandLine.Option(names = { "--theme" },
                        description = "Color theme: dark or light (overrides persisted preference for this session)",
                        completionCandidates = ThemeModeCompletionCandidates.class)
    String theme;

    public TuiCommand(CamelJBangMain main, ClassLoader classLoader) {
        super(main);
        this.classLoader = classLoader;
    }

    @Override
    public Integer doCall() throws Exception {
        List<String> args = new ArrayList<>();
        if (name != null) {
            args.add(name);
        }
        if (mcp) {
            args.add("--mcp");
        }
        if (mcpPort != 8123) {
            args.add("--mcp-port");
            args.add(String.valueOf(mcpPort));
        }
        if (refreshInterval != 100) {
            args.add("--refresh");
            args.add(String.valueOf(refreshInterval));
        }
        if (record != null) {
            args.add("--record");
            args.add(record);
        }
        if (theme != null) {
            args.add("--theme");
            args.add(theme);
        }
        CamelMonitor cmd = new CamelMonitor(getMain(), classLoader);
        return new CommandLine(cmd).execute(args.toArray(String[]::new));
    }
}
