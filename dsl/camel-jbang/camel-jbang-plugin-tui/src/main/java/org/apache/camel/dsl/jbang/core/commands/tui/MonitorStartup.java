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

import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.export.common.Printer;
import picocli.CommandLine;

/**
 * What the monitor works out from its options before it starts.
 */
final class MonitorStartup {

    private MonitorStartup() {
    }

    /**
     * The project to open, when the name argument is a directory rather than the name or pid of an integration.
     *
     * @return the directory, or <tt>null</tt>
     */
    static String openDirectory(String name) {
        if (name != null && !"*".equals(name)) {
            Path p = Path.of(name).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p.toString();
            }
        }
        return null;
    }

    /**
     * Validates and applies the --theme and --panel-theme options.
     *
     * @throws CommandLine.ParameterException for a theme that does not exist
     */
    static void applyThemeOptions(CommandLine command, String theme, String panelTheme) {
        if (theme != null) {
            if (!Theme.isValidMode(theme)) {
                String expected = String.join("' or '", ThemeMode.ids());
                throw new CommandLine.ParameterException(
                        command, "Invalid value for option '--theme': expected '" + expected + "', was '" + theme + "'");
            }
            Theme.applyStartupMode(theme);
        }
        if (panelTheme != null) {
            if (!Theme.isValidMode(panelTheme) && !Theme.PANEL_THEME_SAME.equalsIgnoreCase(panelTheme.strip())) {
                String expected = String.join("' or '", ThemeMode.ids());
                throw new CommandLine.ParameterException(
                        command, "Invalid value for option '--panel-theme': expected '" + Theme.PANEL_THEME_SAME + "' or '"
                                 + expected + "', was '" + panelTheme + "'");
            }
            Theme.setPanelTheme(panelTheme);
        }
    }

    /**
     * Fails the monitor without an interactive terminal (a script, a CI job, an AI agent), instead of drawing in none:
     * a browser session (--web) has a terminal of its own, and a recording (--record) plays a tape headless.
     *
     * @throws CliException with exit code 2
     */
    static void requireTerminal(boolean browserSession, boolean recording) {
        if (!browserSession && !recording && !EnvironmentHelper.isInteractiveTerminal()) {
            throw CliException.usage("camel tui requires an interactive terminal");
        }
    }

    /**
     * Whether the monitor serves browsers only: --web without a terminal to draw in (a script, a CI job, a background
     * shell). Each browser session is a monitor of its own, so the one started here needs none.
     */
    static boolean browserOnly(boolean web, boolean browserSession, boolean recording, boolean interactiveTerminal) {
        return web && !browserSession && !recording && !interactiveTerminal;
    }

    /**
     * Serves the TUI to browsers until the JVM stops (Ctrl+C) or the server is stopped. The MCP server drives the
     * screen of the terminal, which a browser-only monitor does not have, so --mcp fails here.
     *
     * @throws CliException with exit code 2 for --mcp, or when the port is taken
     */
    static void serveBrowserOnly(TuiWebServer server, int port, boolean mcp, Printer printer) throws Exception {
        if (mcp) {
            throw CliException.usage("camel tui --mcp needs an interactive terminal: without one, --web serves browsers only");
        }
        try {
            server.start();
        } catch (BindException e) {
            throw CliException.usage("Web server failed to start: port " + port
                                     + " is already in use. Use --web-port to choose another one.");
        }
        Thread stopper = new Thread(server::stop, "camel-tui-web-stop");
        Runtime.getRuntime().addShutdownHook(stopper);
        printer.println("Camel TUI in the browser at http://127.0.0.1:" + port + "/ (Ctrl+C to stop)");
        try {
            server.awaitStop();
        } finally {
            server.stop();
            try {
                Runtime.getRuntime().removeShutdownHook(stopper);
            } catch (IllegalStateException e) {
                // the JVM is already stopping
            }
        }
    }
}
