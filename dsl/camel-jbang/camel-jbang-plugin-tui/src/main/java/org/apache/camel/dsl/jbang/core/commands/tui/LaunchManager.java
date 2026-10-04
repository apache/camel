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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.json.JsonObject;

class LaunchManager {

    private static volatile Path secureTempDir;

    private final Supplier<List<InfraInfo>> infraServices;
    // added from the UI thread and from tool threads (MCP, the example launcher), read on the UI thread
    private final Queue<PendingLaunch> pendingLaunches = new ConcurrentLinkedQueue<>();
    // integrations started from the TUI (not infra services, which run in the background on their own)
    private final Queue<Process> launched = new ConcurrentLinkedQueue<>();
    private DeferredLaunch deferredLaunch;
    private volatile String pendingAutoSelect;
    private BiConsumer<String, Boolean> notificationCallback;
    private Runnable infraCatalogClearer;
    private BiConsumer<String, Path> failureLogCallback;

    LaunchManager(Supplier<List<InfraInfo>> infraServices) {
        this.infraServices = infraServices;
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
    }

    void setInfraCatalogClearer(Runnable clearer) {
        this.infraCatalogClearer = clearer;
    }

    void setFailureLogCallback(BiConsumer<String, Path> callback) {
        this.failureLogCallback = callback;
    }

    String getPendingAutoSelect() {
        return pendingAutoSelect;
    }

    void clearPendingAutoSelect() {
        pendingAutoSelect = null;
    }

    /**
     * Launches {@code camel <extraArgs>} as a detached background process and registers it as a pending launch so it is
     * tracked and monitored like an example started from the F2 Actions menu. Output is redirected to a temporary log
     * file. Used by the AI panel's {@code /run} and {@code /infra run} slash commands.
     */
    void launchDetached(String displayName, List<String> extraArgs) throws IOException {
        JsonObject example = exampleOf(extraArgs);
        if (example == null) {
            start(displayName, extraArgs, null);
            return;
        }
        // the example runs in a directory of its own, with its files: its routes read relative to it (orders, inbox),
        // where camel run --example would run in the directory of the TUI; a GitHub example is downloaded first
        Thread t = new Thread(() -> {
            try {
                Path dir = ExampleHelper.isBundled(example)
                        ? ExampleHelper.extractBundledExample(example) : ExampleHelper.downloadGithubExample(example);
                start(displayName, exampleArgs(extraArgs, example), dir);
            } catch (Exception e) {
                notify("Failed to start: " + displayName + " - " + e.getMessage(), true);
            }
        }, "CamelTuiExampleLaunch");
        t.setDaemon(true);
        t.start();
    }

    private void start(String displayName, List<String> args, Path dir) throws IOException {
        List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
        cmd.addAll(args);
        Path outputFile = createSecureTempFile("camel-launch-", ".log");
        outputFile.toFile().deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputFile.toFile());
        Process process = pb.start();
        addPendingLaunch(displayName, process, outputFile);
    }

    /** The catalog entry of the example a camel run with --example=name runs, when it lists its files; else null. */
    static JsonObject exampleOf(List<String> args) {
        if (args.isEmpty() || !"run".equals(args.get(0))) {
            return null;
        }
        String name = null;
        for (String a : args) {
            if (a.startsWith("--example=")) {
                name = a.substring("--example=".length());
            }
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            List<JsonObject> catalog = ExampleHelper.loadCatalog();
            JsonObject entry = ExampleHelper.findExample(catalog, name);
            if (entry == null && !name.contains("/")) {
                List<JsonObject> same = ExampleHelper.findExamplesByShortName(catalog, name);
                entry = same.size() == 1 ? same.get(0) : null;
            }
            return entry != null && !ExampleHelper.getFiles(entry).isEmpty() ? entry : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The arguments with --example=name replaced by the files of the example, and its name unless one is given. */
    static List<String> exampleArgs(List<String> args, JsonObject example) {
        return ExampleHelper.runArgs(args, example);
    }

    void addPendingLaunch(String name, Process process, Path outputFile) {
        pendingLaunches.add(new PendingLaunch(name, process, outputFile, System.currentTimeMillis()));
        pendingAutoSelect = name;
        launched.add(process);
    }

    /** The integrations started from this TUI that still run: they keep running when the TUI quits. */
    long runningLaunchCount() {
        launched.removeIf(p -> !p.isAlive());
        return launched.size();
    }

    /** Stops the integrations started from this TUI (the camel launcher and the JVM it started). */
    void stopLaunched() {
        for (Process p : launched) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
        launched.clear();
    }

    void addPendingLaunchNoAutoSelect(String name, Process process, Path outputFile) {
        pendingLaunches.add(new PendingLaunch(name, process, outputFile, System.currentTimeMillis()));
    }

    void tick(long now) {
        monitorPendingLaunches(now);
        checkDeferredLaunch(now);
    }

    List<String> findMissingInfraServices(JsonObject example) {
        List<String> required = ExampleHelper.getInfraServices(example);
        if (required.isEmpty()) {
            return List.of();
        }
        Set<String> runningAliases = infraServices.get().stream()
                .filter(i -> i.alive)
                .map(i -> i.alias)
                .collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String alias : required) {
            if (!runningAliases.contains(alias)) {
                missing.add(alias);
            }
        }
        return missing;
    }

    boolean isJaegerRunning() {
        return infraServices.get().stream()
                .anyMatch(i -> i.alive && "jaeger".equals(i.alias));
    }

    /**
     * Starts an infra service in the background via {@code camel infra run <alias> --background}. The launch is
     * monitored like any other, so a failure surfaces through the failure log callback.
     */
    void startInfra(String alias) throws IOException {
        List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
        cmd.add("infra");
        cmd.add("run");
        cmd.add(alias);
        cmd.add("--background");
        Path outputFile = createSecureTempFile("camel-infra-", ".log");
        outputFile.toFile().deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputFile.toFile());
        Process process = pb.start();
        pendingLaunches.add(new PendingLaunch(alias, process, outputFile, System.currentTimeMillis()));
    }

    void startMissingInfraAndDefer(List<String> missingInfra, String displayName, Runnable launchAction) {
        for (String alias : missingInfra) {
            try {
                startInfra(alias);
            } catch (Exception e) {
                notify("Failed to start infra: " + alias + " - " + e.getMessage(), true);
                return;
            }
        }
        deferredLaunch = new DeferredLaunch(displayName, missingInfra, System.currentTimeMillis(), launchAction);
        if (infraCatalogClearer != null) {
            infraCatalogClearer.run();
        }
        String infraList = String.join(", ", missingInfra);
        notify("Starting infra: " + infraList + " → then: " + displayName, false);
    }

    /**
     * Creates a temporary file inside a secure (owner-only permissions) subdirectory rather than directly in the
     * publicly writable system temp directory. This avoids SonarCloud S5443 (use of publicly writable directories).
     */
    static Path createSecureTempFile(String prefix, String suffix) throws IOException {
        Path dir = secureTempDir;
        if (dir == null || !Files.isDirectory(dir)) {
            synchronized (LaunchManager.class) {
                dir = secureTempDir;
                if (dir == null || !Files.isDirectory(dir)) {
                    dir = Files.createTempDirectory("camel-tui-");
                    dir.toFile().deleteOnExit();
                    secureTempDir = dir;
                }
            }
        }
        return Files.createTempFile(dir, prefix, suffix);
    }

    static boolean isContainerRuntimeAvailable() {
        for (String cmd : new String[] { "docker", "podman" }) {
            try {
                Process p = new ProcessBuilder(cmd, "info")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                boolean done = p.waitFor(5, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    continue;
                }
                if (p.exitValue() == 0) {
                    return true;
                }
            } catch (Exception e) {
                // not found, try next
            }
        }
        return false;
    }

    /**
     * Runs an existing Maven project via {@code camel run pom.xml}, which detects the runtime, injects the CLI
     * connector, and logs to a file in {@code ~/.camel} that the Log tab reads (the same for all runtimes).
     */
    void launchMavenProject(String dir, String projectType, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add(Path.of(dir, "pom.xml").toString());
            cmd.addAll(extraArgs);
            Path outputFile = createSecureTempFile("camel-maven-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(dir));
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            addPendingLaunch(displayName, process, outputFile);
            notify("Starting: " + displayName + " (" + projectType + ")", false);
        } catch (Exception e) {
            notify("Failed to start Maven project: " + e.getMessage(), true);
        }
    }

    void launchCamelRun(String sourceDir, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--source-dir=" + sourceDir);
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            Path outputFile = createSecureTempFile("camel-folder-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            addPendingLaunch(displayName, process, outputFile);
            notify("Starting: " + displayName, false);
        } catch (Exception e) {
            notify("Failed to start: " + sourceDir + " - " + e.getMessage(), true);
        }
    }

    private void checkDeferredLaunch(long now) {
        if (deferredLaunch != null) {
            Set<String> runningAliases = infraServices.get().stream()
                    .filter(i -> i.alive)
                    .map(i -> i.alias)
                    .collect(Collectors.toSet());
            if (runningAliases.containsAll(deferredLaunch.requiredInfra)) {
                DeferredLaunch dl = deferredLaunch;
                deferredLaunch = null;
                dl.launchAction.run();
            } else if (now - deferredLaunch.startTime > 120_000) {
                deferredLaunch = null;
                notify("Timeout waiting for infra services to start", true);
            }
        }
    }

    private void monitorPendingLaunches(long now) {
        Iterator<PendingLaunch> it = pendingLaunches.iterator();
        while (it.hasNext()) {
            PendingLaunch pl = it.next();
            if (!pl.process.isAlive()) {
                int exitCode = pl.process.exitValue();
                if (exitCode == 0 || pl.started) {
                    if (!pl.announced) {
                        notify("Started: " + pl.name, false);
                    }
                } else if (failureLogCallback != null) {
                    failureLogCallback.accept(pl.name, pl.outputFile);
                }
                it.remove();
            } else if (pl.started) {
                // up and running: a stop or a failure from now on is not a failed start
                it.remove();
            } else if (pl.startFailed()) {
                // the app gave up starting (port in use, build failure) but its JVM lives on: stop it, and show why
                pl.process.descendants().forEach(ProcessHandle::destroy);
                pl.process.destroy();
                if (failureLogCallback != null) {
                    failureLogCallback.accept(pl.name, pl.outputFile);
                }
                it.remove();
            } else {
                if (!pl.announced && now - pl.startTime > 8000) {
                    notify("Started: " + pl.name, false);
                    pl.announced = true;
                }
                if (now - pl.startTime > WATCH_MS) {
                    it.remove();
                }
            }
        }
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }

    /**
     * How long a launch is watched for a failed start: a Maven project builds before it starts, and Spring Boot only
     * then finds that its port is in use.
     */
    static final long WATCH_MS = 5 * 60_000;

    /** What a runtime prints when it gives up starting, while its JVM may stay up. */
    static final List<String> START_FAILURES = List.of(
            "APPLICATION FAILED TO START", "[ERROR] BUILD FAILURE", "Failed to start application");

    /** What Camel prints when it has started, whatever the runtime: the start did not fail. */
    static final Pattern STARTED = Pattern.compile("Apache Camel \\S+ \\(.*\\) started in");

    /** A started process, watched until it is up for a while, ends, or fails to start. */
    static final class PendingLaunch {
        final String name;
        final Process process;
        final Path outputFile;
        final long startTime;
        boolean announced;
        // Camel said it started: the launch is no longer watched for a failed start
        boolean started;
        private long offset;

        PendingLaunch(String name, Process process, Path outputFile, long startTime) {
            this.name = name;
            this.process = process;
            this.outputFile = outputFile;
            this.startTime = startTime;
        }

        /** Whether the output printed since the last look says the start failed. */
        boolean startFailed() {
            if (outputFile == null) {
                return false;
            }
            try (var channel = Files.newByteChannel(outputFile)) {
                long size = channel.size();
                if (size <= offset) {
                    return false;
                }
                // a marker can be cut by the previous look: read a little of what was seen before
                long from = Math.max(0, offset - 64);
                ByteBuffer buf = ByteBuffer.allocate((int) Math.min(size - from, 1024 * 1024));
                channel.position(from);
                channel.read(buf);
                offset = from + buf.position();
                String text = new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8);
                if (START_FAILURES.stream().anyMatch(text::contains)) {
                    return true;
                }
                started |= STARTED.matcher(text).find();
                return false;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private record DeferredLaunch(
            String displayName, List<String> requiredInfra, long startTime,
            Runnable launchAction) {
    }
}
