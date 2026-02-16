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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.StopProcess;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "diagram", description = "Visualize Camel routes using Hawtio", sortOptions = false,
                     showDefaultValues = true)
public class Diagram extends CamelCommand {

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

    @CommandLine.Option(names = { "--background-wait" }, defaultValue = "true",
                        description = "To wait for run in background to startup successfully, before returning")
    boolean backgroundWait = true;

    @CommandLine.Option(names = { "--keep-running" }, defaultValue = "false",
                        description = "Keep the background Camel integration running after exiting Hawtio")
    boolean keepRunning;

    public Diagram(CamelJBangMain main) {
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
        String runName = name;
        if (hasFiles && (runName == null || runName.isBlank())) {
            runName = FileUtil.onlyName(FileUtil.stripPath(files.get(0)));
        }
        String target = runName;
        if (!hasFiles && (target == null || target.isBlank())) {
            new CommandLine(this).execute("--help");
            return 0;
        }

        long pid = 0;
        boolean started = false;
        int exit = 0;
        try {
            if (hasFiles) {
                Run run = new Run(getMain());
                run.backgroundWait = backgroundWait;
                if (runName != null && !runName.isBlank()) {
                    run.name = runName;
                }
                List<String> args = new ArrayList<>();
                args.add("run");
                if (runName != null && !runName.isBlank()) {
                    args.add("--name=" + runName);
                }
                args.addAll(files);
                RunHelper.addCamelJBangCommand(args);
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(args);
                int rc = run.runBackgroundProcess(pb, "Camel Main");
                if (rc != 0) {
                    return rc;
                }
                pid = run.spawnPid;
                if (pid <= 0) {
                    printer().printErr("Unable to determine the running Camel PID");
                    return 1;
                }
                target = Long.toString(pid);
                started = true;
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

    static class FilesConsumer extends ParameterConsumer<Diagram> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Diagram cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
