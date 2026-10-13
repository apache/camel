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

import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import dev.tamboui.tui.TuiRunner;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * What the TUI does to the processes of the integrations: stop or kill the selected one, restart it, and start, stop,
 * suspend or resume one of its routes. The Process Control popup (F10), the Overview tab and the MCP tools act through
 * it.
 */
class ProcessControl {

    private static final Logger LOG = System.getLogger(ProcessControl.class.getName());

    private final MonitorContext ctx;
    private final DataRefreshService dataService;
    private final Supplier<TuiRunner> tuiRunner;
    private final LaunchManager launchManager;
    private final BiConsumer<String, Boolean> notification;

    /**
     * @param tuiRunner     the runner of the TUI while it runs, which a restart tells how it went; null before and
     *                      after
     * @param launchManager where a restarted Spring Boot or Quarkus integration is shown while it starts
     * @param notification  shows a notification: the message, and whether it is an error
     */
    ProcessControl(MonitorContext ctx, DataRefreshService dataService, Supplier<TuiRunner> tuiRunner,
                   LaunchManager launchManager, BiConsumer<String, Boolean> notification) {
        this.ctx = ctx;
        this.dataService = dataService;
        this.tuiRunner = tuiRunner;
        this.launchManager = launchManager;
        this.notification = notification;
    }

    /**
     * Stops the selected integration, or kills it (its status files deleted as it cannot), or stops the selected
     * infrastructure service.
     */
    void stopSelectedProcess(boolean forceKill) {
        dataService.enableBurstMode();
        if (ctx.selectedPid == null) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            LOG.log(Level.DEBUG, "Cannot parse selected PID: {0}", ctx.selectedPid);
            return;
        }
        if (ctx.isInfraSelected()) {
            InfraInfo infra = ctx.findSelectedInfra();
            if (infra != null) {
                Path camelDir = CommandLineHelper.getCamelDir();
                PathUtils.deleteFile(camelDir.resolve("infra-" + infra.alias + "-" + infra.pid + ".json"));
                PathUtils.deleteFile(camelDir.resolve("infra-" + infra.alias + "-" + infra.pid + ".log"));
            }
            if (forceKill) {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
        } else {
            String pidStr = ctx.selectedPid;
            ProcessHandle.of(pid).ifPresent(ph -> {
                if (forceKill) {
                    ph.destroyForcibly();
                    Path camelDir = CommandLineHelper.getCamelDir();
                    PathUtils.deleteFile(camelDir.resolve(pidStr + ".log"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-status.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-action.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-output.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-trace.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-history.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-debug.json"));
                    PathUtils.deleteFile(camelDir.resolve(pidStr + "-receive.json"));
                } else {
                    dataService.stoppingPids().add(pidStr);
                    ph.destroy();
                }
            });
        }
    }

    /**
     * Restarts the selected integration with the command line it was started with: through its Maven parent process
     * when Spring Boot or Quarkus runs it, which recompiles it.
     */
    void restartSelectedProcess() {
        dataService.enableBurstMode();
        if (ctx.selectedPid == null || ctx.isInfraSelected()) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(ctx.selectedPid);
        } catch (NumberFormatException e) {
            LOG.log(Level.DEBUG, "Cannot parse selected PID for restart: {0}", ctx.selectedPid);
            return;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        ProcessHandle ph = ProcessHandle.of(pid).orElse(null);
        if (ph == null) {
            return;
        }

        String platform = info.platform;
        boolean isMavenManaged = "Spring Boot".equals(platform) || "Quarkus".equals(platform);
        if (isMavenManaged) {
            restartMavenProcess(ph, info);
        } else {
            restartCamelMainProcess(ph, info);
        }
    }

    private void restartMavenProcess(ProcessHandle ph, IntegrationInfo info) {
        TuiRunner runner = tuiRunner.get();
        // Find the Maven parent process (mvn spring-boot:run / quarkus:dev)
        ProcessHandle mavenPh = ph.parent().orElse(null);
        if (mavenPh == null) {
            notification.accept("Cannot restart: Maven parent process not found", true);
            return;
        }

        // Capture Maven parent command line before stopping
        Optional<String> cmdOpt = mavenPh.info().command();
        Optional<String[]> argsOpt = mavenPh.info().arguments();
        Optional<String> cmdLineOpt = mavenPh.info().commandLine();

        String name = info.name;
        String directory = info.directory;

        ctx.lastSelectedName = name;
        dataService.forceFullScan();

        // Kill the Maven parent (which also kills the child Java process)
        mavenPh.destroy();
        notification.accept("Restarting: " + name, false);

        if (runner != null) {
            ctx.backgroundExecutor.execute(() -> {
                try {
                    CompletableFuture<ProcessHandle> exitFuture = mavenPh.onExit().toCompletableFuture();
                    try {
                        exitFuture.get(15, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        mavenPh.destroyForcibly();
                        Thread.sleep(500);
                    }

                    List<String> cmd = new ArrayList<>();
                    if (cmdOpt.isPresent() && argsOpt.isPresent() && argsOpt.get().length > 0) {
                        cmd.add(cmdOpt.get());
                        Collections.addAll(cmd, argsOpt.get());
                    } else {
                        cmdLineOpt.ifPresent(s -> cmd.addAll(parseCommandLine(s)));
                    }

                    if (cmd.isEmpty()) {
                        runner.runOnRenderThread(
                                () -> notification.accept("Cannot restart: Maven command line not available", true));
                        return;
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    if (directory != null) {
                        pb.directory(new File(directory));
                    }
                    pb.redirectErrorStream(true);
                    Path outputFile = LaunchManager.createSecureTempFile("camel-restart-", ".log");
                    outputFile.toFile().deleteOnExit();
                    pb.redirectOutput(outputFile.toFile());
                    Process process = pb.start();
                    launchManager.addPendingLaunch(name, process, outputFile);

                    runner.runOnRenderThread(() -> notification.accept("Restarted: " + name + " (recompiling)", false));
                } catch (Exception e) {
                    runner.runOnRenderThread(
                            () -> notification.accept("Restart failed: " + e.getMessage(), true));
                }
            });
        }
    }

    private void restartCamelMainProcess(ProcessHandle ph, IntegrationInfo info) {
        TuiRunner runner = tuiRunner.get();
        // capture command line before stopping
        Optional<String> cmdOpt = ph.info().command();
        Optional<String[]> argsOpt = ph.info().arguments();
        Optional<String> cmdLineOpt = ph.info().commandLine();

        String name = info.name;
        String directory = info.directory;

        // remember name so the restarted process gets auto-selected
        ctx.lastSelectedName = name;
        dataService.forceFullScan();

        // stop gracefully
        ph.destroy();
        notification.accept("Restarting: " + name, false);

        // re-launch in background after process terminates (use the background executor
        // instead of runner.scheduler() — the scheduler is single-threaded and generates
        // tick events; blocking it would freeze the notification wave animation)
        if (runner != null) {
            ctx.backgroundExecutor.execute(() -> {
                try {
                    // wait for termination (max 10 seconds, then force kill)
                    CompletableFuture<ProcessHandle> exitFuture = ph.onExit().toCompletableFuture();
                    try {
                        exitFuture.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        ph.destroyForcibly();
                        Thread.sleep(500);
                    }

                    // build command
                    List<String> cmd = new ArrayList<>();
                    if (cmdOpt.isPresent() && argsOpt.isPresent() && argsOpt.get().length > 0) {
                        cmd.add(cmdOpt.get());
                        Collections.addAll(cmd, argsOpt.get());
                    } else {
                        cmdLineOpt.ifPresent(s -> cmd.addAll(parseCommandLine(s)));
                    }

                    if (cmd.isEmpty()) {
                        runner.runOnRenderThread(
                                () -> notification.accept("Cannot restart: command line not available", true));
                        return;
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    if (directory != null) {
                        pb.directory(new File(directory));
                    }
                    pb.redirectErrorStream(true);
                    Path outputFile = Files.createTempFile("camel-restart-", ".log");
                    outputFile.toFile().deleteOnExit();
                    pb.redirectOutput(outputFile.toFile());
                    pb.start();

                    runner.runOnRenderThread(() -> notification.accept("Restarted: " + name, false));
                } catch (Exception e) {
                    runner.runOnRenderThread(
                            () -> notification.accept("Restart failed: " + e.getMessage(), true));
                }
            });
        }
    }

    /** The arguments of a command line, split at the spaces outside double quotes, a backslash escaping a char. */
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

    /** Starts, stops, suspends or resumes a route of an integration: the command of the route action. */
    void sendRouteCommand(String pid, String routeId, String command) {
        dataService.enableBurstMode();
        JsonObject root = new JsonObject();
        root.put("action", "route");
        root.put("id", routeId);
        root.put("command", command);
        ctx.fireAction(pid, root);
    }
}
