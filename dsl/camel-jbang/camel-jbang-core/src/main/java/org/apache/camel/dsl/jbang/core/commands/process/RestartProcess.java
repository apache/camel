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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
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

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "1")
    String name;

    public RestartProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            printer().println("No matching Camel integration found: " + name);
            return 1;
        }
        if (pids.size() > 1) {
            printer().println("Multiple integrations match '" + name + "'. Please be more specific.");
            return 1;
        }

        long pid = pids.get(0);
        ProcessHandle ph = ProcessHandle.of(pid).orElse(null);
        if (ph == null) {
            printer().println("Process not found: " + pid);
            return 1;
        }

        // resolve name for display
        String displayName = name;
        JsonObject root = loadStatus(pid);
        if (root != null) {
            displayName = ProcessHelper.extractName(root, ph);
        }

        // capture command line before stopping
        Optional<String> cmdOpt = ph.info().command();
        Optional<String[]> argsOpt = ph.info().arguments();
        Optional<String> cmdLineOpt = ph.info().commandLine();

        // get working directory from status file
        String directory = null;
        if (root != null) {
            JsonObject runtime = (JsonObject) root.get("runtime");
            if (runtime != null) {
                directory = runtime.getString("directory");
            }
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
            printer().println("Cannot restart: command line not available for PID " + pid);
            return 1;
        }

        // stop the process gracefully
        printer().println("Stopping Camel integration: " + displayName + " (PID: " + pid + ")");
        ph.destroy();

        // wait for termination
        CompletableFuture<ProcessHandle> exitFuture = ph.onExit().toCompletableFuture();
        try {
            exitFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            printer().println("Graceful shutdown timed out, force killing...");
            ph.destroyForcibly();
            Thread.sleep(500);
        }

        // re-launch
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (directory != null) {
            pb.directory(new File(directory));
        }
        pb.redirectErrorStream(true);
        Path outputFile = Files.createTempFile("camel-restart-", ".log");
        outputFile.toFile().deleteOnExit();
        pb.redirectOutput(outputFile.toFile());
        Process process = pb.start();

        printer().println("Restarted Camel integration: " + displayName + " (PID: " + process.pid() + ")");

        return 0;
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
