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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.BackgroundLog;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "restart",
         description = "Restarts running Camel integrations (stop + re-launch)", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel restart hello",
                 "  camel restart 12345" })
public class RestartProcess extends ProcessBaseCommand {

    /**
     * The phase of a Camel that has started (see the status file).
     */
    private static final int STARTED = 5;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "1")
    String name;

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "60000", converter = TimeoutConverter.class,
                        description = "How long to wait for the integration to stop, and again to start, in millis or as a"
                                      + " duration such as 30s")
    long timeout = 60000;

    public RestartProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = ProcessFinder.requireOne(name);
        long pid = process.pid();
        ProcessHandle ph = ProcessHandle.of(pid)
                .orElseThrow(() -> CliException.notFound("No running Camel integration matches: " + name));
        String displayName = process.name();

        // capture command line before stopping
        Optional<String> cmdOpt = ph.info().command();
        Optional<String[]> argsOpt = ph.info().arguments();
        Optional<String> cmdLineOpt = ph.info().commandLine();

        // get working directory from status file
        String directory = null;
        JsonObject runtime = (JsonObject) process.status().get("runtime");
        if (runtime != null) {
            directory = runtime.getString("directory");
        }

        // build the command to re-launch
        List<String> cmd = new ArrayList<>();
        if (cmdOpt.isPresent() && argsOpt.isPresent() && argsOpt.get().length > 0) {
            cmd.add(cmdOpt.get());
            Collections.addAll(cmd, argsOpt.get());
        } else if (cmdLineOpt.isPresent()) {
            cmd.addAll(parseCommandLine(cmdLineOpt.get()));
        }

        if (cmd.isEmpty()) {
            throw CliException.failure("Cannot restart: command line not available for PID " + pid);
        }

        stop(ph, displayName);
        BackgroundLog.remove(pid);

        // re-launch, with its output in a log file that stays (the path is printed)
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (directory != null) {
            pb.directory(new File(directory));
        }
        Path log = BackgroundLog.redirect(pb);
        Process started = pb.start();
        log = BackgroundLog.started(log, started.pid());

        awaitStarted(started, displayName, log);
        printer().println("Restarted Camel integration: " + displayName + " (PID: " + started.pid() + ")");
        printer().println("Log: " + log);
        return 0;
    }

    /**
     * Stops the integration like camel stop does, and terminates it when it does not stop in time: the old one must be
     * gone before the new one starts.
     */
    private void stop(ProcessHandle ph, String displayName) {
        long pid = ph.pid();
        printer().println("Stopping Camel integration: " + displayName + " (PID: " + pid + ")");
        Path pidFile = CommandLineHelper.getCamelDir().resolve(Long.toString(pid));
        if (Files.exists(pidFile)) {
            PathUtils.deleteFile(pidFile);
        } else {
            ph.destroy();
        }
        if (StopProcess.awaitExit(pid, System.currentTimeMillis() + timeout)) {
            return;
        }
        printer().printWarn("Camel integration (PID: " + pid + ") did not stop within "
                            + TimeUtils.printDuration(timeout) + "; terminating it");
        ph.destroy();
        if (StopProcess.awaitExit(pid, System.currentTimeMillis() + 10000)) {
            return;
        }
        ph.destroyForcibly();
        if (!StopProcess.awaitExit(pid, System.currentTimeMillis() + 5000)) {
            throw CliException.timeout("Camel integration (PID: " + pid + ") did not stop, so it is not restarted");
        }
    }

    /**
     * Waits until the new integration has started.
     *
     * @throws CliException with exit code 1 when it stopped right away, or 124 when it did not start in time (it is
     *                      left running)
     */
    private void awaitStarted(Process started, String displayName, Path log) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            if (!started.isAlive()) {
                printer().printDiagnostic(BackgroundLog.tail(log, 20));
                throw CliException.failure("Camel integration " + displayName + " stopped right after the restart (exit code "
                                           + started.exitValue() + "); the log is in " + log);
            }
            JsonObject status = loadStatus(started.pid());
            if (status != null && status.get("context") instanceof JsonObject context
                    && context.getIntegerOrDefault("phase", 0) == STARTED) {
                return;
            }
            Thread.sleep(200);
        }
        throw CliException.timeout("Camel integration " + displayName + " (PID: " + started.pid() + ") did not start within "
                                   + TimeUtils.printDuration(timeout) + "; it is still starting, the log is in "
                                   + log);
    }

    static List<String> parseCommandLine(String commandLine) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
