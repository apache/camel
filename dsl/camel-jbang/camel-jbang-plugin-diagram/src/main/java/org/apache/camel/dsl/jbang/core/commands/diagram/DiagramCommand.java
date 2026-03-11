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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.RunHelper;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.StopProcess;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "diagram", description = "Visualize Camel routes using Hawtio%n%n"
                                                     + "NOTE: PNG export (--output) requires a Chromium browser. "
                                                     + "Set PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH or use --playwright-browser-path "
                                                     + "to point to an existing Chromium or Google Chrome binary.",
                     sortOptions = false, showDefaultValues = true)
public class DiagramCommand extends CamelCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run. If no files specified then use --name to attach to a running integration.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used
    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--name" },
                        description = "Name or pid of running Camel integration")
    String name;

    @CommandLine.Option(names = { "--renderer" },
                        description = "Renderer to use (hawtio)",
                        defaultValue = "hawtio")
    String renderer = "hawtio";

    @CommandLine.Option(names = { "--port" },
                        description = "Port number to use for Hawtio web console (port 8888 by default)", defaultValue = "8888")
    int port = 8888;

    @CommandLine.Option(names = { "--openUrl" },
                        description = "To automatic open Hawtio web console in the web browser", defaultValue = "true")
    boolean openUrl = true;

    @CommandLine.Option(names = { "--keep-running" }, defaultValue = "false",
                        description = "Keep the background Camel integration running after exiting Hawtio")
    boolean keepRunning;

    @CommandLine.Option(names = { "--output" },
                        description = "Write a PNG snapshot of the route diagram to the given file")
    Path output;

    @CommandLine.Option(names = { "--browser" },
                        description = "Playwright browser to use (chromium only)",
                        defaultValue = "chromium")
    String browser = "chromium";

    @CommandLine.Option(names = { "--playwright-browser-path" },
                        description = "Path to the Playwright browser executable")
    String playwrightBrowserPath;

    @CommandLine.Option(names = { "--route-id" },
                        description = "Route id to render (defaults to the first route)")
    String routeId;

    @CommandLine.Option(names = { "--jolokia-port" }, defaultValue = "8778",
                        description = "Jolokia port to attach when exporting PNG")
    int jolokiaPort = 8778;

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "60",
                        description = "Maximum time in seconds to wait for integration startup and diagram rendering")
    int timeout = 60;

    public DiagramCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String selectedRenderer = renderer == null ? "hawtio" : renderer.toLowerCase(Locale.ROOT);
        if (!"hawtio".equals(selectedRenderer)) {
            printer().printErr("Unsupported renderer: " + renderer);
            return 1;
        }

        boolean hasFiles = files != null && !files.isEmpty();
        boolean exportPng = output != null;
        if (exportPng && openUrl) {
            openUrl = false;
        }

        String runName = name;
        if (hasFiles && (runName == null || runName.isBlank())) {
            runName = FileUtil.onlyName(FileUtil.stripPath(files.get(0)));
        }
        String target = runName;
        if (!hasFiles && (target == null || target.isBlank())) {
            new CommandLine(this).execute("--help");
            return 0;
        }

        boolean started = false;
        int exit = 0;
        DiagramPngExporter.CamelLaunch camelLaunch = null;
        try {
            if (hasFiles) {
                camelLaunch = launchCamelProcess(runName);
                target = Long.toString(camelLaunch.pid());
                started = true;
                printer().println(
                        "Running Camel Main: " + runName + " in background with PID: " + camelLaunch.pid()
                                  + " (waiting to startup)");
            }

            if (exportPng) {
                DiagramPngExporter exporter = new DiagramPngExporter(
                        getMain(), printer(), output, browser, playwrightBrowserPath, routeId, jolokiaPort, port, keepRunning,
                        false, timeout, camelLaunch);
                exit = exporter.export(target);
                return exit;
            }

            Hawtio hawtio = new Hawtio(getMain());
            List<String> hawtioArgs = new ArrayList<>();
            if (target != null && !target.isBlank()) {
                hawtioArgs.add(target);
            }
            hawtioArgs.add("--port=" + port);
            hawtioArgs.add("--openUrl=" + openUrl);
            CommandLine.populateCommand(hawtio, hawtioArgs.toArray(new String[0]));
            exit = hawtio.doCall();
            return exit;
        } finally {
            if (started && !keepRunning) {
                StopProcess stop = new StopProcess(getMain());
                if (target != null && !target.isBlank()) {
                    CommandLine.populateCommand(stop, target);
                }
                stop.doCall();
            }
        }
    }

    /**
     * Launches the Camel integration in a background subprocess and returns immediately. The returned
     * {@link DiagramPngExporter.CamelLaunch} holds the process, PID, log file path, and name. Waiting for the
     * integration to reach Running state is handled by {@link DiagramPngExporter}, which starts Hawtio in parallel to
     * reduce overall startup time.
     */
    private DiagramPngExporter.CamelLaunch launchCamelProcess(String runName) throws Exception {
        List<String> cmd = new ArrayList<>();
        RunHelper.addCamelCLICommand(cmd);
        cmd.add("run");
        if (runName != null && !runName.isBlank()) {
            cmd.add("--name=" + runName);
        }
        cmd.addAll(files);

        // Temp log to capture startup output for error reporting
        Path logPath = CommandLineHelper.getCamelDir().resolve(new Random().nextLong() + "-diagram-run.log"); // NOSONAR
        Files.createDirectories(logPath.getParent());
        Files.createFile(logPath);
        logPath.toFile().deleteOnExit();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logPath.toFile());

        Process p = pb.start();
        return new DiagramPngExporter.CamelLaunch(p, p.pid(), logPath, runName);
    }

    static class FilesConsumer extends ParameterConsumer<DiagramCommand> {
        @Override
        protected void doConsumeParameters(Stack<String> args, DiagramCommand cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
