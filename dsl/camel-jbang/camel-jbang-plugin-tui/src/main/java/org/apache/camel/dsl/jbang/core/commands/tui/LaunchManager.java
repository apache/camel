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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.FileUtil;
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
    // the folders of the examples started here, with the process that runs each (camel run, which waits for the app)
    private final Map<Path, Process> exampleDirs = new ConcurrentHashMap<>();

    /**
     * Deletes the folders of the examples that are no longer running: those started here, and those left behind in the
     * temporary directory for an hour or more (by an earlier session, or a run that was killed). The folder of an
     * example that still runs is kept, with the files it reads, as the example keeps running when the TUI quits.
     *
     * @param runningDirs the directories of the running integrations
     */
    void deleteExampleDirs(Collection<Path> runningDirs) {
        Set<Path> unused = new HashSet<>(ExampleHelper.staleExampleDirs(runningDirs, Duration.ZERO));
        exampleDirs.forEach((dir, process) -> {
            // an example that is still being built (exported and packaged by Maven) is not running yet
            if (!process.isAlive() && unused.contains(dir)) {
                FileUtil.removeDir(dir.toFile());
            }
        });
        for (Path dir : ExampleHelper.staleExampleDirs(runningDirs, Duration.ofHours(1))) {
            FileUtil.removeDir(dir.toFile());
        }
    }

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
                // the example keeps running when the TUI quits, so its folder is not deleted on exit (CAMEL-25425):
                // deleteExampleDirs removes it once the example has stopped
                Path dir = ExampleHelper.isBundled(example)
                        ? ExampleHelper.extractBundledExample(example, false)
                        : ExampleHelper.downloadGithubExample(example, false);
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
        if (dir != null) {
            exampleDirs.put(dir, process);
        }
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
            if (!runningAliases.contains(serviceOf(alias))) {
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
     * The service of an infra entry, which may name its implementation too: {@code aws sqs} is the {@code aws} service.
     * A running service is known by the service alone.
     */
    static String serviceOf(String alias) {
        String s = alias.trim();
        int space = s.indexOf(' ');
        return space > 0 ? s.substring(0, space) : s;
    }

    /**
     * Starts an infra service via {@code camel infra run <alias>}, where the alias may name the implementation too
     * ({@code aws sqs}). It runs in a process of its own, which outlives the TUI, as {@code --background} would start
     * it; but the TUI keeps its output, so a service that fails to start (its port taken, its image not pulled) shows
     * why through the failure log callback, where {@code --background} would lose it.
     */
    void startInfra(String alias) throws IOException {
        startInfra(alias, null);
    }

    /** Starts an infra service as {@link #startInfra(String)} does, on the given port (none: its default). */
    void startInfra(String alias, String port) throws IOException {
        List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
        cmd.add("infra");
        cmd.add("run");
        cmd.addAll(Arrays.asList(alias.trim().split("\\s+")));
        if (port != null && !port.isBlank()) {
            cmd.add("--port=" + port.trim());
        }
        launchInfra(alias, cmd);
    }

    /** Runs the command that starts the given infra service, and watches it until the service is up. */
    void launchInfra(String alias, List<String> cmd) throws IOException {
        Path outputFile = createSecureTempFile("camel-infra-", ".log");
        outputFile.toFile().deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputFile.toFile());
        Process process = pb.start();
        PendingLaunch pl = new PendingLaunch(alias, process, outputFile, System.currentTimeMillis());
        pl.infraAlias = serviceOf(alias);
        pendingLaunches.add(pl);
    }

    /**
     * Whether an infra service of the given ones is still starting: its camel infra run lives, and it is not up yet.
     */
    private boolean infraStarting(List<String> aliases) {
        Set<String> services = aliases.stream().map(LaunchManager::serviceOf).collect(Collectors.toSet());
        return pendingLaunches.stream()
                .anyMatch(pl -> pl.infraAlias != null && services.contains(pl.infraAlias) && pl.process.isAlive());
    }

    /** Runs the launch once the given infra services are up, or drops it when they fail to start. */
    void deferUntilInfra(List<String> infra, String displayName, Runnable launchAction) {
        deferredLaunch = new DeferredLaunch(displayName, infra, System.currentTimeMillis(), launchAction);
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
        deferUntilInfra(missingInfra, displayName, launchAction);
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
     *
     * @return the launched process, or null when it did not start
     */
    ProcessHandle launchMavenProject(String dir, String projectType, String displayName, List<String> extraArgs) {
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
            return process.toHandle();
        } catch (Exception e) {
            notify("Failed to start Maven project: " + e.getMessage(), true);
            return null;
        }
    }

    /**
     * Runs a folder of route files with camel run --source-dir.
     *
     * @return the launched process, or null when it did not start
     */
    ProcessHandle launchCamelRun(String sourceDir, String displayName, List<String> extraArgs) {
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
            return process.toHandle();
        } catch (Exception e) {
            notify("Failed to start: " + sourceDir + " - " + e.getMessage(), true);
            return null;
        }
    }

    private void checkDeferredLaunch(long now) {
        if (deferredLaunch != null) {
            Set<String> runningAliases = infraServices.get().stream()
                    .filter(i -> i.alive)
                    .map(i -> i.alias)
                    .collect(Collectors.toSet());
            if (deferredLaunch.requiredInfra.stream().map(LaunchManager::serviceOf).allMatch(runningAliases::contains)) {
                DeferredLaunch dl = deferredLaunch;
                deferredLaunch = null;
                dl.launchAction.run();
            } else if (!infraStarting(deferredLaunch.requiredInfra)) {
                // the infra failed to start: the failure log says why
                DeferredLaunch dl = deferredLaunch;
                deferredLaunch = null;
                notify("Not started: " + dl.displayName() + " (its infra services failed to start)", true);
            } else if (now - deferredLaunch.startTime > INFRA_WATCH_MS) {
                // a first start pulls the container image, which can take minutes: wait while the infra is starting
                deferredLaunch = null;
                notify("Timeout waiting for infra services to start", true);
            }
        }
    }

    private void monitorPendingLaunches(long now) {
        Set<String> runningAliases = null;
        Iterator<PendingLaunch> it = pendingLaunches.iterator();
        while (it.hasNext()) {
            PendingLaunch pl = it.next();
            if (pl.infraAlias != null && !pl.started) {
                if (runningAliases == null) {
                    runningAliases = infraServices.get().stream()
                            .filter(i -> i.alive).map(i -> i.alias).collect(Collectors.toSet());
                }
                // an infra service has started when it is up
                if (runningAliases.contains(pl.infraAlias)) {
                    pl.started = true;
                    notify("Started: " + pl.name, false);
                    pl.announced = true;
                }
            }
            if (!pl.process.isAlive()) {
                int exitCode = pl.process.exitValue();
                boolean ok = pl.infraAlias != null ? pl.started : exitCode == 0 || pl.started;
                if (ok) {
                    if (!pl.announced) {
                        notify("Started: " + pl.name, false);
                    }
                    outcomes.put(pl.name, LaunchOutcome.started());
                } else {
                    outcomes.put(pl.name, LaunchOutcome.failed(pl.outputFile));
                    if (failureLogCallback != null) {
                        failureLogCallback.accept(pl.name, pl.outputFile);
                    }
                }
                it.remove();
            } else if (pl.started) {
                // up and running: a stop or a failure from now on is not a failed start
                outcomes.put(pl.name, LaunchOutcome.started());
                it.remove();
            } else if (pl.startFailed()) {
                // the app gave up starting (port in use, build failure) but its JVM lives on: stop it, and show why
                pl.process.descendants().forEach(ProcessHandle::destroy);
                pl.process.destroy();
                outcomes.put(pl.name, LaunchOutcome.failed(pl.outputFile));
                if (failureLogCallback != null) {
                    failureLogCallback.accept(pl.name, pl.outputFile);
                }
                it.remove();
            } else if (pl.infraAlias != null) {
                // a first start pulls the image, which can take minutes: show how far it got
                if (pl.pullProgress != null && !pl.pullProgress.equals(pl.shownPullProgress)) {
                    notify("Pulling image of " + pl.name + ": " + pl.pullProgress, false);
                    pl.shownPullProgress = pl.pullProgress;
                }
                if (now - pl.startTime > INFRA_WATCH_MS) {
                    // not up after the longest an image pull should take: a failed start, as the other ends record
                    outcomes.put(pl.name, LaunchOutcome.failed(pl.outputFile));
                    if (failureLogCallback != null) {
                        failureLogCallback.accept(pl.name, pl.outputFile);
                    }
                    it.remove();
                }
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

    // how the latest launch of each name went, for an agent that asks (tui_run_example waits for it)
    private final Map<String, LaunchOutcome> outcomes = new ConcurrentHashMap<>();

    /**
     * How a launch went: started (Camel said so), or failed with the end of its output; null while it is starting.
     */
    record LaunchOutcome(boolean ok, String log) {

        private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*m");

        static LaunchOutcome started() {
            return new LaunchOutcome(true, null);
        }

        static LaunchOutcome failed(Path outputFile) {
            String tail = null;
            try {
                // without colors and stack frames: the messages and their causes are what tell why
                List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8).stream()
                        .map(l -> ANSI.matcher(l).replaceAll(""))
                        .filter(l -> !l.stripLeading().startsWith("at ") && !l.stripLeading().startsWith("... "))
                        .toList();
                tail = String.join("\n", lines.subList(Math.max(0, lines.size() - 30), lines.size()));
            } catch (Exception e) {
                // no output to show
            }
            return new LaunchOutcome(false, tail);
        }
    }

    /** Forgets how the last launch of the given name went, before it is launched again. */
    void clearOutcome(String name) {
        outcomes.remove(name);
    }

    /** How the latest launch of the given name went, or null while it is still starting. */
    LaunchOutcome outcome(String name) {
        return outcomes.get(name);
    }

    /** How long an infra service may take to start: the first start pulls its container image. */
    static final long INFRA_WATCH_MS = 15 * 60_000;

    /** What a runtime prints when it gives up starting, while its JVM may stay up. */
    static final List<String> START_FAILURES = List.of(
            "APPLICATION FAILED TO START", "[ERROR] BUILD FAILURE", "Failed to start application");

    /** What Camel prints when it has started, whatever the runtime: the start did not fail. */
    static final Pattern STARTED = Pattern.compile("Apache Camel \\S+ \\(.*\\) started in");

    /** What camel infra run prints while it pulls the image of a service (the pull progress of Testcontainers). */
    static final Pattern PULL_PROGRESS = Pattern.compile(
            "Pulling image layers:\\s*(\\d+) pending,\\s*(\\d+) downloaded,\\s*\\d+ extracted, \\((.+?)/(.+?)\\)");
    private static final Pattern SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?) (bytes|KB|MB|GB|TB)");

    /**
     * The progress of an image pull in the given output, from its last progress line, such as {@code 2 of 4 layers,
     * 22 MB}, or {@code 2 of 4 layers, 1 GB of 3 GB (33%)} once the total is known: Docker reports the size of a layer
     * only when it starts on it. Null when the output has no progress, or the pull has completed since.
     */
    static String pullProgress(String text) {
        var m = PULL_PROGRESS.matcher(text);
        String answer = null;
        int last = -1;
        while (m.find()) {
            last = m.start();
            int pending = Integer.parseInt(m.group(1));
            int downloaded = Integer.parseInt(m.group(2));
            String done = m.group(3).trim();
            String total = m.group(4).trim();
            if (pending + downloaded == 0) {
                // no layer reported yet
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(downloaded).append(" of ").append(pending + downloaded).append(" layers, ").append(done);
            long doneBytes = bytes(done);
            long totalBytes = bytes(total);
            if (doneBytes >= 0 && totalBytes > 0) {
                sb.append(" of ").append(total).append(" (").append(Math.min(100, doneBytes * 100 / totalBytes))
                        .append("%)");
            }
            answer = sb.toString();
        }
        if (answer != null && text.lastIndexOf("Pull complete") > last) {
            return null;
        }
        return answer;
    }

    /** The bytes of a size as Testcontainers prints it (such as 22 MB), or -1 when it is not known (? MB). */
    private static long bytes(String size) {
        var m = SIZE.matcher(size);
        if (!m.matches()) {
            return -1;
        }
        double n = Double.parseDouble(m.group(1));
        int power = List.of("bytes", "KB", "MB", "GB", "TB").indexOf(m.group(2));
        return (long) (n * (1L << (10 * power)));
    }

    /** A started process, watched until it is up for a while, ends, or fails to start. */
    static final class PendingLaunch {
        final String name;
        final Process process;
        final Path outputFile;
        final long startTime;
        // the service of an infra launch: it has started once the service is up; null for an integration
        String infraAlias;
        boolean announced;
        // Camel said it started: the launch is no longer watched for a failed start
        boolean started;
        // the progress of pulling the image of an infra service, and the one last shown
        String pullProgress;
        String shownPullProgress;
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
                if (text.contains("Pull")) {
                    String progress = pullProgress(text);
                    if (progress != null || text.contains("Pull complete")) {
                        pullProgress = progress;
                    }
                }
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
