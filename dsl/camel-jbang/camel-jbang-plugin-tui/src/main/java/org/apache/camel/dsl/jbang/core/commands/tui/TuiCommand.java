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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandTree;

@CommandDefinition(name = "tui", description = "Camel TUI (use --help to see sub commands)", generateHelp = true,
                   sortOptions = true)
public class TuiCommand extends CamelCommand {

    private ClassLoader classLoader;

    @Argument(description = "Name or pid of running Camel integration")
    String name;

    @Option(name = "mcp", hasValue = false, description = "Enable embedded MCP server for AI agent access to the TUI")
    boolean mcp;

    @Option(name = "mcp-port", description = "MCP server port (default: ${DEFAULT-VALUE})", defaultValue = "8123")
    int mcpPort = 8123;

    @Option(name = "web", hasValue = false,
            description = "Enable browser-accessible terminal (WebSocket) server; without an interactive"
                          + " terminal it serves browsers only")
    boolean web;

    @Option(name = "web-port", description = "Web terminal server port (default: ${DEFAULT-VALUE})", defaultValue = "8090")
    int webPort = 8090;

    @Option(name = "refresh", description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
            defaultValue = "100")
    long refreshInterval = 100;

    @Option(name = "record", optionalValue = true,
            description = "Play a .tape file in the TUI and record it to an Asciinema .cast file")
    String record;

    @Option(name = "record-size",
            description = "Size of the recorded terminal for --record, as <cols>x<rows> (default: ${DEFAULT-VALUE})",
            defaultValue = "200x50")
    String recordSize = "200x50";

    @Option(name = "record-fps", description = "Frames per second captured by --record (default: ${DEFAULT-VALUE})",
            defaultValue = "10")
    int recordFps = 10;

    @Option(name = "record-duration",
            description = "Maximum duration in milliseconds captured by --record (default: ${DEFAULT-VALUE})",
            defaultValue = "120000")
    int recordDuration = 120000;

    @Option(name = "theme", description = "Color theme: dark or light (overrides persisted preference for this session)",
            completer = ThemeModeCompletionCandidates.class)
    String theme;

    @Option(name = "panel-theme", description = "Color theme of the shell (F6) and AI (F8) panels, or 'same' for the main theme"
                                                + " (overrides the Panel Theme setting for this session)",
            completer = ThemeModeCompletionCandidates.class)
    String panelTheme;

    @Option(name = "defaults", hasValue = false,
            description = "Start with the default settings, as a new user sees the TUI; changes last for the"
                          + " session and are not saved")
    boolean defaults;

    public TuiCommand(CamelJBangMain main, ClassLoader classLoader) {
        super(main);
        this.classLoader = classLoader;
    }

    @Override
    public Integer doCall() throws Exception {
        if (record == null && !web) {
            // a recording plays a tape headless, and --web without a terminal serves browsers only
            requireInteractiveTerminal("camel tui");
        }
        CamelMonitor cmd = new CamelMonitor(getMain(), classLoader);
        return new CommandTree(cmd).execute(buildArgs().toArray(String[]::new)).getResultValue();
    }

    /**
     * Builds the {@link CamelMonitor} command line this command delegates to.
     * <p>
     * Every option declared here must be forwarded, otherwise the option is silently accepted and then ignored. Only
     * non-default values are passed on, so the delegate keeps applying its own defaults.
     */
    List<String> buildArgs() {
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
        if (web) {
            args.add("--web");
        }
        if (webPort != 8090) {
            args.add("--web-port");
            args.add(String.valueOf(webPort));
        }
        if (refreshInterval != 100) {
            args.add("--refresh");
            args.add(String.valueOf(refreshInterval));
        }
        if (record != null) {
            args.add("--record");
            args.add(record);
        }
        if (!"200x50".equals(recordSize)) {
            args.add("--record-size");
            args.add(recordSize);
        }
        if (recordFps != 10) {
            args.add("--record-fps");
            args.add(String.valueOf(recordFps));
        }
        if (recordDuration != 120000) {
            args.add("--record-duration");
            args.add(String.valueOf(recordDuration));
        }
        if (theme != null) {
            args.add("--theme");
            args.add(theme);
        }
        if (panelTheme != null) {
            args.add("--panel-theme");
            args.add(panelTheme);
        }
        if (defaults) {
            args.add("--defaults");
        }
        return args;
    }
}
