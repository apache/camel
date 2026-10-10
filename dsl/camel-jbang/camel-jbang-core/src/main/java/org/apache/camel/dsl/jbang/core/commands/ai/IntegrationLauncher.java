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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Starts, stops and restarts an integration for an AI agent that is building one: {@code camel run --dev} in a separate
 * JVM (through the same launcher the CLI itself uses), watched until its status file appears so the caller gets the pid
 * and the log file back, and a restart that relaunches the process with its own command line.
 */
public final class IntegrationLauncher {

    /** How long a started integration is given to publish its status file before the call returns without a pid. */
    static final long STARTUP_TIMEOUT_MS = 30_000;

    /** The last log records of a failed start that are returned. */
    static final int FAILURE_RECORDS = 15;

    /** A stack frame, or the count of the frames a cause shares with the exception it caused. */
    private static final Pattern FRAME = Pattern.compile("^\\s*(at \\S.*|\\.\\.\\. \\d+ (more|common frames omitted))$");

    /**
     * The processes this JVM started from a directory with --source-dir, so a copy that has not written its status file
     * yet (still starting, or started by a concurrent call) is seen by the guard too.
     */
    private static final Map<Path, ProcessHandle> LAUNCHED = new ConcurrentHashMap<>();

    private IntegrationLauncher() {
    }

    /**
     * Starts {@code camel run} detached in the given directory.
     *
     * @param  directory the project directory (the working directory of the process)
     * @param  files     the source files to run, relative to the directory; empty runs every route file in it
     * @param  name      the integration name (the {@code --name} option); null keeps the default
     * @param  dev       dev mode (reload on file changes)
     * @param  extraArgs further {@code camel run} arguments
     * @return           status started (with pid, name, log), failed (with the output) or starting
     */
    public static JsonObject run(Path directory, List<String> files, String name, boolean dev, List<String> extraArgs) {
        List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
        // no files given: the whole directory is the app (camel run --source-dir), so a file the agent adds later,
        // a bean file, a Java class under src/main/java, is part of it and reloaded in dev mode (CAMEL-24861);
        // with files given only those run, for a directory that holds several apps
        boolean sourceDir = files == null || files.isEmpty();
        List<String> sources = sourceDir ? sourceFiles(directory) : files;
        cmd.addAll(sourceDir ? sourceDirArguments(name, dev, extraArgs) : runArguments(sources, name, dev, extraArgs));
        JsonObject result = new JsonObject();
        // with --source-dir the guard looks at the top level only; a class under src/main/java is an app too
        if (!sourceDir && sources.isEmpty()) {
            result.put("directory", directory.toString());
            result.put("status", "failed");
            result.put("error", "No source files to run in " + directory
                                + " (route files such as *.camel.yaml, *.xml, *.java, or application.properties)");
            return result;
        }
        result.put("directory", directory.toString());
        Path output;
        Process process;
        // one check and start at a time, so two calls in flight do not both start a copy
        synchronized (LAUNCHED) {
            if (sourceDir && alreadyRunning(directory, result)) {
                // the whole directory is the app: one already running from it is that app, and a second copy only
                // fights it for ports and files and makes its name ambiguous to the other tools
                return result;
            }
            result.put("command", String.join(" ", cmd));
            try {
                Files.createDirectories(CommandLineHelper.getCamelDir());
                output = Files.createTempFile(CommandLineHelper.getCamelDir(), "camel-launch-", ".log");
                output.toFile().deleteOnExit();
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(directory.toFile());
                pb.redirectErrorStream(true);
                pb.redirectOutput(output.toFile());
                process = pb.start();
            } catch (IOException e) {
                result.put("status", "failed");
                result.put("error", "Cannot start camel run: " + e.getMessage());
                return result;
            }
            if (sourceDir) {
                LAUNCHED.put(key(directory), process.toHandle());
            }
        }
        long pid = process.pid();
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                result.put("status", "failed");
                result.put("exitCode", process.exitValue());
                result.put("output", failureOutput(output));
                result.put("message", "camel run exited before the integration started; the output has the reason");
                return result;
            }
            RuntimeHelper.ProcessInfo info = findByPid(pid);
            if (info != null) {
                // the name the other tools find the process by: the one the caller gave, else the Camel context name
                // (the source file name without its extensions); the pid is in the result as well
                String started = name != null && !name.isBlank()
                        ? name : info.contextName() != null && !info.contextName().isBlank() ? info.contextName() : info.name();
                result.put("status", "started");
                result.put("pid", pid);
                result.put("name", started);
                result.put("log", LogFileReader.logFile(pid, info.name()).toString());
                result.put("devMode", dev);
                result.put("sourceDir", sourceDir);
                result.put("message", "Started " + started + " (pid " + pid + ")"
                                      + (dev
                                              ? sourceDir
                                                      ? "; dev mode watches the directory: a changed or added file is"
                                                        + " reloaded"
                                                      : "; dev mode reloads the routes when a source file changes"
                                              : "; restart it after changing a source file")
                                      + ". camel_get_log reads its log, camel_get_errors its failed exchanges.");
                return result;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        result.put("status", "starting");
        result.put("pid", pid);
        result.put("output", readTail(output));
        result.put("message", "camel run is still starting after " + STARTUP_TIMEOUT_MS / 1000
                              + "s (dependencies may be downloading); list_processes shows it once it is up");
        return result;
    }

    /**
     * File extensions {@code camel run} loads from a project directory, as {@code camel run *} would pass them: the
     * three DSLs (see {@code SourceHelper.ACCEPTED_FILE_EXT}) and the properties files.
     */
    private static final List<String> SOURCE_EXTENSIONS = List.of(".yaml", ".yml", ".xml", ".java", ".properties");

    /**
     * The source files {@code camel run} should load from a directory when the caller names none: the regular,
     * non-hidden files with a source extension, sorted by name. This is what a shell expands {@code camel run *} to;
     * the process is started without a shell, and {@code camel run} with no files would instead look for an
     * {@code application.properties} with {@code camel.main.routesIncludePattern} and fail when there is none.
     *
     * @param  directory the project directory
     * @return           the file names relative to the directory, empty when there is nothing to run
     */
    static List<String> sourceFiles(Path directory) {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith(".") && SOURCE_EXTENSIONS.stream().anyMatch(n::endsWith))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            // an unreadable directory has nothing to run
        }
        return names;
    }

    /** The {@code camel run --source-dir=.} arguments: the directory the process starts in is the app. */
    static List<String> sourceDirArguments(String name, boolean dev, List<String> extraArgs) {
        List<String> cmd = new ArrayList<>();
        cmd.add("run");
        cmd.add("--source-dir=.");
        if (dev) {
            cmd.add("--dev");
        }
        if (name != null && !name.isBlank()) {
            cmd.add("--name=" + name);
        }
        cmd.add("--logging-color=false");
        if (extraArgs != null) {
            cmd.addAll(extraArgs);
        }
        return cmd;
    }

    /**
     * The {@code camel run} arguments for the given files, name and mode.
     *
     * @param  files     the source files, relative to the directory
     * @param  name      the integration name, or null for the default
     * @param  dev       whether to run in dev mode
     * @param  extraArgs further {@code camel run} arguments
     * @return           the arguments after the camel command itself
     */
    static List<String> runArguments(List<String> files, String name, boolean dev, List<String> extraArgs) {
        List<String> cmd = new ArrayList<>();
        cmd.add("run");
        cmd.addAll(files);
        if (dev) {
            cmd.add("--dev");
        }
        if (name != null && !name.isBlank()) {
            cmd.add("--name=" + name);
        }
        cmd.add("--logging-color=false");
        if (extraArgs != null) {
            cmd.addAll(extraArgs);
        }
        return cmd;
    }

    private static RuntimeHelper.ProcessInfo findByPid(long pid) {
        for (RuntimeHelper.ProcessInfo p : RuntimeHelper.discoverProcesses()) {
            if (p.pid() == pid) {
                return p;
            }
        }
        return null;
    }

    /**
     * The console output of a run that failed to start, as camel_get_log shows a log (CAMEL-25364): each record keeps
     * its message, the lines of its exceptions (the exception, its causes, and a message over several lines such as the
     * diagnostics of a Java class that does not compile) and the first line of the user's code, and the stack frames
     * are left out. The end of the raw output is mostly frames of the runtime, and the line that says what went wrong
     * is above them. This is the only copy the agent gets, as the console capture is deleted. Output without log
     * records (a launcher that fails before logging) is returned as it is, its end kept.
     */
    static String failureOutput(Path output) {
        List<String> lines;
        try {
            lines = readText(output).lines().toList();
        } catch (IOException e) {
            return readTail(output).strip();
        }
        if (lines.size() > 5000) {
            lines = lines.subList(lines.size() - 5000, lines.size());
        }
        lines = lines.stream().filter(l -> !l.isBlank()).toList();
        // folded for the first line of the user's code, and once more without the frames for the exception lines
        JsonArray rows = (JsonArray) LogFileReader.build(lines, FAILURE_RECORDS, null, null, false, new JsonObject())
                .get("lines");
        if (rows == null || rows.isEmpty()
                || rows.stream().allMatch(r -> ((JsonObject) r).getStringOrDefault("time", "").isEmpty())) {
            // no log records: a launcher that failed before logging prints its error at the end
            return readTail(output).strip();
        }
        List<String> withoutFrames = lines.stream().filter(l -> !FRAME.matcher(l).matches()).toList();
        JsonArray exceptions = (JsonArray) LogFileReader
                .build(withoutFrames, FAILURE_RECORDS, null, null, true, new JsonObject()).get("lines");
        boolean aligned = exceptions != null && exceptions.size() == rows.size();
        StringBuilder sb = new StringBuilder();
        // newest first in the log tool; oldest first here, so it reads as the console did
        for (int i = rows.size() - 1; i >= 0; i--) {
            JsonObject r = (JsonObject) rows.get(i);
            String level = r.getStringOrDefault("time", "").isEmpty() ? "" : r.getStringOrDefault("level", "") + " ";
            sb.append(level).append(r.getStringOrDefault("message", ""));
            if (r.get("detailLines") != null) {
                String detail = aligned ? ((JsonObject) exceptions.get(i)).getString("detail") : null;
                if (detail != null) {
                    // the exception, its causes and a message over several lines (compiler diagnostics)
                    detail.lines().forEach(l -> sb.append("\n  ").append(l.strip()));
                } else if (r.get("cause") != null) {
                    sb.append("\n  cause: ").append(r.getString("cause"));
                }
                if (r.get("at") != null) {
                    sb.append("\n  at: ").append(r.getString("at"));
                }
                sb.append("\n  (stack frames left out)");
            } else if (r.get("detail") != null) {
                sb.append("\n").append(r.getString("detail"));
            }
            sb.append("\n");
        }
        return sb.toString().strip();
    }

    /**
     * Whether an integration already runs from the directory, or is still starting from it, filling in the result for
     * it: status running or starting, its pid, and what dev mode and a restart do for it.
     */
    static boolean alreadyRunning(Path directory, JsonObject result) {
        RuntimeHelper.ProcessInfo running = runningFrom(directory);
        if (running != null) {
            String runningName = running.contextName() != null && !running.contextName().isBlank()
                    ? running.contextName() : running.name();
            result.put("status", "running");
            result.put("pid", running.pid());
            result.put("name", runningName);
            result.put("log", LogFileReader.logFile(running.pid(), running.name()).toString());
            result.put("message", runningMessage(runningName, running.pid()));
            return true;
        }
        ProcessHandle starting = LAUNCHED.get(key(directory));
        if (starting != null && starting.isAlive()) {
            result.put("status", "starting");
            result.put("pid", starting.pid());
            result.put("message", "pid " + starting.pid() + " started earlier from this directory is still starting"
                                  + " (dependencies may be downloading); a second copy was not started."
                                  + " list_processes shows it once it is up.");
            return true;
        }
        return false;
    }

    /** What the agent can do with the integration that runs from the directory, by how it was started. */
    private static String runningMessage(String name, long pid) {
        String prefix = name + " (pid " + pid + ") already runs from this directory; a second copy was not started.";
        List<String> args = ProcessHandle.of(pid).flatMap(ph -> ph.info().arguments()).map(Arrays::asList).orElse(null);
        if (args == null) {
            return prefix + " camel_control stop stops it, then camel_run starts the directory again.";
        }
        boolean sourceDir = args.stream().anyMatch(a -> a.startsWith("--source-dir"));
        boolean dev = args.contains("--dev");
        if (!sourceDir) {
            // started with its own files: dev mode watches those only, and a restart runs the same files again
            return prefix + " It was started with its own files, not the whole directory, so a file added to the"
                   + " directory, such as a Java class, is not part of it, also not after camel_control restart: stop it"
                   + " with camel_control stop, then camel_run runs the whole directory.";
        }
        return prefix + (dev
                ? " In dev mode a changed or added file is reloaded; camel_control restart restarts it, for example"
                  + " after adding a Java class."
                : " It is not in dev mode: camel_control restart picks up a changed or added file.");
    }

    /**
     * The integration that runs in dev mode from the directory (its working directory, started with --dev), or null:
     * the one whose reload a write to the directory is answered with when no integration is selected (CAMEL-25513).
     */
    static RuntimeHelper.ProcessInfo devModeFrom(Path directory) {
        RuntimeHelper.ProcessInfo running = runningFrom(directory);
        if (running == null) {
            return null;
        }
        List<String> args = ProcessHandle.of(running.pid()).flatMap(ph -> ph.info().arguments()).map(Arrays::asList)
                .orElse(null);
        return args != null && args.contains("--dev") ? running : null;
    }

    /** The integration that runs with this directory as its working directory, or null. */
    private static RuntimeHelper.ProcessInfo runningFrom(Path directory) {
        Path wanted = directory.toAbsolutePath().normalize();
        for (RuntimeHelper.ProcessInfo p : RuntimeHelper.discoverProcesses()) {
            Path dir = workingDirectory(p.pid());
            if (dir != null && sameDirectory(dir, wanted)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Whether two paths are the same directory: the process reports its symlink-resolved working directory, and the
     * caller may pass a path through a symlink (every directory under /tmp on macOS).
     */
    private static boolean sameDirectory(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    /** The key of a directory in {@link #LAUNCHED}: its real path, the same through any symlink. */
    private static Path key(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            return directory.toAbsolutePath().normalize();
        }
    }

    /** The console output, decoded leniently: console output is not always UTF-8 (Windows uses the code page). */
    private static String readText(Path output) throws IOException {
        return new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
    }

    private static String readTail(Path output) {
        try {
            String text = readText(output);
            return text.length() > 4000 ? "..." + text.substring(text.length() - 4000) : text;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Controls a running integration.
     *
     * @param  ctx    the context with the selected process
     * @param  action stop (graceful), kill, restart, reload, stop-routes, start-routes or reset-stats
     * @return        what was done
     */
    public static String control(ToolContext ctx, String action) {
        ctx.requireProcess();
        long pid = ctx.pid();
        return switch (action) {
            case "stop" -> ctx.stopApplication();
            case "kill" -> {
                Optional<ProcessHandle> ph = ProcessHandle.of(pid);
                if (ph.isEmpty()) {
                    yield "No process with pid " + pid;
                }
                ph.get().destroyForcibly();
                yield "Killed pid " + pid;
            }
            case "restart" -> restart(pid);
            case "stop-routes", "pause" -> ctx.executeAction("route", root -> {
                root.put("id", "*");
                root.put("command", "stop");
            });
            case "start-routes", "resume" -> ctx.executeAction("route", root -> {
                root.put("id", "*");
                root.put("command", "start");
            });
            case "reset-stats" -> {
                ctx.executeAction("reset-stats", null);
                yield "Statistics reset for pid " + pid;
            }
            case "reload" -> {
                // what camel cmd reload does: the routes are loaded again from their files without a restart, so
                // a changed stylesheet or a dropped data file takes effect, and a file consumed once is read again
                ctx.executeAction("reload", null);
                yield "Reload triggered for pid " + pid + "; camel_get_log shows the routes reloaded summary";
            }
            default -> throw new ToolExecutionException(
                    "Unknown action: " + action + ". Use stop, kill, restart, reload, stop-routes, start-routes or"
                                                        + " reset-stats");
        };
    }

    /**
     * Stops the process and starts it again with its own command line and working directory, so a route file edited
     * without dev mode is picked up. Returns the new pid once its status file appears.
     */
    static String restart(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            return "No process with pid " + pid;
        }
        ProcessHandle ph = handle.get();
        ProcessHandle.Info info = ph.info();
        List<String> cmd = new ArrayList<>();
        if (info.command().isPresent() && info.arguments().isPresent()) {
            cmd.add(info.command().get());
            Collections.addAll(cmd, info.arguments().get());
        }
        if (cmd.isEmpty()) {
            return "Cannot restart pid " + pid + ": its command line is not available; stop it and run it again";
        }
        RuntimeHelper.ProcessInfo pi = findByPid(pid);
        String name = pi != null ? pi.name() : null;
        Path directory = workingDirectory(pid);
        RuntimeHelper.stopApplication(pid);
        try {
            ph.onExit().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            ph.destroyForcibly();
            try {
                ph.onExit().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // the relaunch below is attempted anyway
            }
        }
        try {
            Path output = Files.createTempFile(CommandLineHelper.getCamelDir(), "camel-restart-", ".log");
            output.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (directory != null) {
                pb.directory(directory.toFile());
            }
            pb.redirectErrorStream(true);
            pb.redirectOutput(output.toFile());
            Process p = pb.start();
            long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline && p.isAlive()) {
                if (findByPid(p.pid()) != null) {
                    return "Restarted " + (name != null ? name : "pid " + pid) + " as pid " + p.pid();
                }
                Thread.sleep(250);
            }
            if (!p.isAlive()) {
                return "Restart of " + (name != null ? name : "pid " + pid) + " failed with exit code "
                       + p.exitValue() + ", so it is stopped now: fix the cause below, then camel_run starts it again.\n"
                       + failureOutput(output);
            }
            return "Restarting " + (name != null ? name : "pid " + pid) + " as pid " + p.pid()
                   + " (still starting)";
        } catch (IOException e) {
            return "Restart failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Restart interrupted";
        }
    }

    /** The working directory of a camel run process, which it reports in the runtime section of its status file. */
    private static Path workingDirectory(long pid) {
        JsonObject status = RuntimeHelper.readStatus(pid);
        if (status == null) {
            return null;
        }
        JsonObject runtime = (JsonObject) status.get("runtime");
        String dir = runtime != null ? runtime.getString("directory") : null;
        return dir != null && Files.isDirectory(Path.of(dir)) ? Path.of(dir) : null;
    }
}
