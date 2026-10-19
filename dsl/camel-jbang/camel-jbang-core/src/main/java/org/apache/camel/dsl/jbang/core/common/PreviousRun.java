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
package org.apache.camel.dsl.jbang.core.common;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * The log of the previous run of an integration, as <tt>kubectl logs --previous</tt> shows the log of the previous
 * instance of a container in Kubernetes.
 * <p/>
 * When <tt>camel run</tt> ends (it was stopped, it failed to start, or it stopped by itself), its log is kept as
 * <tt>~/.camel/&lt;name&gt;-previous.log</tt>, by the name of the integration, with
 * <tt>~/.camel/&lt;name&gt;-previous.json</tt>, which says when and how the run ended. The next run of that name that
 * ends replaces both. Like Kubernetes, at most the last 10 MB of a log are kept.
 * <p/>
 * A process that is gone without keeping its log (it was killed, or its JVM crashed) leaves its
 * <tt>~/.camel/&lt;pid&gt;.log</tt> behind. That log is kept for it, under the name in its status file, the next time
 * the previous runs are looked up, and the next time an integration is run.
 */
public final class PreviousRun {

    /**
     * The end of a log that is kept, at most: Kubernetes keeps at most 10 MB of the log of a container by default.
     */
    static final long MAX_SIZE = 10L * 1024 * 1024;

    /**
     * How old the log of a process that is gone may get, when there is no name to keep it by, before it is removed.
     */
    static final long ORPHAN_AGE = TimeUnit.DAYS.toMillis(1);

    /**
     * What a process that is gone without keeping its log ended with, as far as is known.
     */
    static final String GONE = "the process was gone without stopping Camel (killed, or its JVM crashed)";

    private static final String LOG_SUFFIX = "-previous.log";
    private static final String INFO_SUFFIX = "-previous.json";
    private static final Pattern PID_LOG = Pattern.compile("(\\d+)\\.log");
    private static final int MAX_FAILURE = 500;
    private static final DateTimeFormatter TIME
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * A run that ended, and the log it kept.
     *
     * @param name      the name of the integration
     * @param pid       the pid it ran as, 0 when not known
     * @param directory the directory it ran in, or <tt>null</tt>
     * @param started   when it started, in epoch millis, 0 when not known
     * @param ended     when it ended, in epoch millis
     * @param exitCode  the exit code, or <tt>null</tt> when not known (such as of a process that was killed)
     * @param signal    the signal that stopped the run, such as <tt>SIGTERM</tt>, or <tt>null</tt>
     * @param failure   why it ended, when it failed (such as the error it failed to start with), or <tt>null</tt>
     * @param log       the log it kept
     */
    public record Info(String name, long pid, String directory, long started, long ended, Integer exitCode,
            String signal, String failure, Path log) {

        /**
         * Which run it was, and how it ended, in one line.
         */
        public String describe() {
            return "The previous run of " + name + (pid > 0 ? " (pid " + pid + ")" : "") + " " + howItEnded();
        }

        /**
         * How the run ended: when, with which exit code, and why when it failed.
         */
        public String howItEnded() {
            StringBuilder sb = new StringBuilder("ended at ").append(TIME.format(Instant.ofEpochMilli(ended)));
            long ago = System.currentTimeMillis() - ended;
            if (ago >= 0) {
                sb.append(" (").append(TimeUtils.printDuration(ago, false)).append(" ago)");
            }
            if (exitCode != null) {
                sb.append(" with exit code ").append(exitCode);
            }
            if (signal != null) {
                sb.append(" (").append(signal).append(')');
            }
            if (failure != null) {
                sb.append(": ").append(failure);
            }
            return sb.toString();
        }
    }

    private PreviousRun() {
    }

    /**
     * Keeps the log of a run of this JVM that ended as the previous log of its name.
     *
     * @param  log      the log of the run
     * @param  name     the name of the integration
     * @param  pid      the pid it ran as, 0 when not known
     * @param  exitCode the exit code, or <tt>null</tt> when not known
     * @param  failure  why it ended, when it failed, or <tt>null</tt>
     * @return          the kept log, or <tt>null</tt> when there was no log to keep
     */
    public static Path keep(Path log, String name, long pid, Integer exitCode, String failure) {
        return keep(log, thisRun(name, pid, exitCode, null, failure));
    }

    // a run of this JVM that ends now
    private static Info thisRun(String name, long pid, Integer exitCode, String signal, String failure) {
        long started = ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(0L);
        return new Info(
                name, pid, System.getProperty("user.dir"), started, System.currentTimeMillis(), exitCode, signal,
                failure, null);
    }

    /**
     * Keeps the log of a run that ended as the previous log of its name, in place of the one kept before.
     *
     * @return the kept log, or <tt>null</tt> when there was no log to keep
     */
    static Path keep(Path log, Info info) {
        if (log == null || info.name() == null || info.name().isBlank() || !Files.isRegularFile(log)) {
            return null;
        }
        Path dir = CommandLineHelper.getCamelDir();
        String file = fileName(info.name());
        Path target = dir.resolve(file + LOG_SUFFIX);
        try {
            // what was kept about the run before goes first, so a keep that fails (such as on a full disk) does not
            // leave it next to the log of this run
            Files.deleteIfExists(dir.resolve(file + INFO_SUFFIX));
            if (Files.size(log) > MAX_SIZE || !move(log, target)) {
                copyEnd(log, target, MAX_SIZE);
                try {
                    Files.deleteIfExists(log);
                } catch (IOException e) {
                    // still open (Windows): removed when the process is gone (see keepOrphans)
                }
            }
            JsonObject jo = new JsonObject();
            jo.put("name", info.name());
            if (info.pid() > 0) {
                jo.put("pid", info.pid());
            }
            if (info.directory() != null) {
                jo.put("directory", info.directory());
            }
            if (info.started() > 0) {
                jo.put("started", info.started());
            }
            jo.put("ended", info.ended());
            if (info.exitCode() != null) {
                jo.put("exitCode", info.exitCode());
            }
            if (info.signal() != null) {
                jo.put("signal", info.signal());
            }
            if (info.failure() != null) {
                String failure = info.failure().strip();
                jo.put("failure", failure.length() > MAX_FAILURE ? failure.substring(0, MAX_FAILURE) + "..." : failure);
            }
            Files.writeString(dir.resolve(file + INFO_SUFFIX), jo.toJson());
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The previous run of each name, the newest first.
     */
    public static List<Info> findAll() {
        keepOrphans();
        List<Info> answer = new ArrayList<>();
        File[] files = CommandLineHelper.getCamelDir().toFile().listFiles((d, n) -> n.endsWith(INFO_SUFFIX));
        if (files != null) {
            for (File f : files) {
                Info info = read(f.toPath());
                if (info != null) {
                    answer.add(info);
                }
            }
        }
        answer.sort(Comparator.comparingLong(Info::ended).reversed());
        return answer;
    }

    /**
     * The previous runs that match the name or pid, as a running integration is found (see {@link ProcessFinder}): a
     * number is the pid it ran as, and a name is a prefix (<tt>foo</tt> matches <tt>foo-bar</tt>) unless it ends with
     * <tt>!</tt>, and may use wildcards. A name that is the whole name of a previous run matches only that run.
     *
     * @param nameOrPid a name, a pattern or a pid; <tt>null</tt> or <tt>*</tt> for every previous run
     */
    public static List<Info> find(String nameOrPid) {
        List<Info> all = findAll();
        if (nameOrPid == null || nameOrPid.isBlank() || "*".equals(nameOrPid)) {
            return all;
        }
        if (nameOrPid.matches("\\d+")) {
            long pid = Long.parseLong(nameOrPid);
            return all.stream().filter(i -> i.pid() == pid).toList();
        }
        boolean exact = nameOrPid.endsWith("!");
        String name = exact ? nameOrPid.substring(0, nameOrPid.length() - 1) : nameOrPid;
        List<Info> same = all.stream().filter(i -> i.name().equals(name)).toList();
        if (exact || !same.isEmpty()) {
            return same;
        }
        String pattern = name.endsWith("*") ? name : name + "*";
        return all.stream().filter(i -> PatternHelper.matchPattern(i.name(), pattern)).toList();
    }

    /**
     * Whether the file in the Camel directory is a log of a previous run, or what it says about that run.
     */
    public static boolean isPreviousRunFile(String fileName) {
        return fileName.endsWith(LOG_SUFFIX) || fileName.endsWith(INFO_SUFFIX);
    }

    /**
     * Keeps the logs that processes which are gone left behind (they were killed, or their JVM crashed), under the name
     * in their status file. A log older than the previous run of its name is removed, and so is a log without a name
     * that is older than a day.
     */
    public static void keepOrphans() {
        File[] files = CommandLineHelper.getCamelDir().toFile().listFiles((d, n) -> PID_LOG.matcher(n).matches());
        if (files == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (File f : files) {
            Matcher m = PID_LOG.matcher(f.getName());
            if (!m.matches()) {
                continue;
            }
            long pid;
            try {
                pid = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                // still running (or its pid is taken by another process)
                continue;
            }
            long modified = f.lastModified();
            JsonObject status = readJson(CommandLineHelper.getCamelDir().resolve(pid + "-status.json"));
            String name = status != null && status.get("context") instanceof JsonObject context
                    ? context.getString("name") : null;
            if (name != null && !name.isBlank()) {
                Info before = read(CommandLineHelper.getCamelDir().resolve(fileName(name) + INFO_SUFFIX));
                // the time of the kept log, not when its run ended, so both times come from the file system (a
                // network file system has a clock of its own)
                if (before == null || before.log().toFile().lastModified() < modified) {
                    String directory = status.get("runtime") instanceof JsonObject runtime
                            ? runtime.getString("directory") : null;
                    keep(f.toPath(), new Info(name, pid, directory, 0, modified, null, null, GONE, null));
                    continue;
                }
            } else if (now - modified < ORPHAN_AGE) {
                continue;
            }
            // older than the previous run of its name, or without a name to keep it by
            try {
                Files.deleteIfExists(f.toPath());
            } catch (IOException e) {
                // removed by camel dirty --clean
            }
        }
    }

    /**
     * Keeps the log of the run of this JVM once, when the run ends: see {@link #ended(Integer, String)}. When the JVM
     * exits before the run has ended (such as on Ctrl+C, while Camel stops), the log is kept as the JVM exits: the run
     * gets a few seconds to end and say how it ended, else the log is kept without that.
     * <p/>
     * A run that Ctrl+C or SIGTERM stops ends with the exit code of the JVM: 128 and the number of the signal (130 for
     * SIGINT, 143 for SIGTERM), as a shell and Kubernetes show it. The keeper watches these signals for that, and
     * passes each on to the handler of the JVM, which runs the shutdown hooks and exits.
     */
    public static final class Keeper {

        /**
         * How long a JVM that exits waits for the run to end, in seconds.
         */
        static final int EXIT_WAIT_SECONDS = 10;

        /**
         * The signals the JVM exits on, running its shutdown hooks.
         */
        private static final List<String> STOP_SIGNALS = List.of("INT", "TERM");

        private final Path log;
        private final Supplier<String> name;
        private final Thread hook;
        private final CountDownLatch ended = new CountDownLatch(1);
        private final List<Watch> watches = new ArrayList<>();
        private volatile Signal stoppedBy;
        private boolean kept;

        // a signal that stops the JVM: the handler of the keeper, and the handler of the JVM it passes the signal on to
        private record Watch(Signal signal, SignalHandler handler, AtomicReference<SignalHandler> jvm) {
        }

        /**
         * A run that has started, and keeps its log when it ends.
         *
         * @param log  the log of the run
         * @param name the name of the integration, asked for when the run ends
         */
        public Keeper(Path log, Supplier<String> name) {
            this.log = log;
            this.name = name;
            this.hook = new Thread(() -> exiting(EXIT_WAIT_SECONDS), "CamelKeepPreviousLog");
            Runtime.getRuntime().addShutdownHook(hook);
            watchSignals();
        }

        /**
         * The run has ended: keeps its log as the previous log of its name.
         *
         * @param exitCode the exit code, or <tt>null</tt> when not known
         * @param failure  why it ended, when it failed, or <tt>null</tt>
         */
        public void ended(Integer exitCode, String failure) {
            keep(exitCode, failure);
            ended.countDown();
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
                // the JVM goes on (such as in a test): the signals go to the handlers of the JVM again
                unwatchSignals();
            } catch (IllegalStateException e) {
                // the JVM is exiting: the hook finds the log kept
            }
        }

        /**
         * The signal stopped the JVM; visible for tests, as a test cannot send it.
         */
        void stoppedBy(Signal signal) {
            stoppedBy = signal;
        }

        private void watchSignals() {
            for (String name : STOP_SIGNALS) {
                try {
                    Signal signal = new Signal(name);
                    AtomicReference<SignalHandler> jvm = new AtomicReference<>();
                    SignalHandler handler = s -> {
                        stoppedBy = s;
                        SignalHandler next = jvm.get();
                        if (next != null) {
                            next.handle(s);
                        } else {
                            // what the JVM does on the signal
                            System.exit(128 + s.getNumber());
                        }
                    };
                    SignalHandler before = Signal.handle(signal, handler);
                    if (before == SignalHandler.SIG_DFL || before == SignalHandler.SIG_IGN) {
                        // the JVM does not exit on it here (such as a signal that is ignored): left as it was
                        Signal.handle(signal, before);
                    } else {
                        jvm.set(before);
                        watches.add(new Watch(signal, handler, jvm));
                    }
                } catch (IllegalArgumentException e) {
                    // a signal this platform does not have, or that the JVM keeps (such as with -Xrs)
                }
            }
        }

        private void unwatchSignals() {
            for (Watch watch : watches) {
                try {
                    SignalHandler current = Signal.handle(watch.signal(), watch.jvm().get());
                    if (current != watch.handler()) {
                        // a handler that came after the keeper stays
                        Signal.handle(watch.signal(), current);
                    }
                } catch (IllegalArgumentException e) {
                    // left as it is
                }
            }
            watches.clear();
        }

        // the JVM exits: the run gets the seconds to end, and say how it ended
        void exiting(int waitSeconds) {
            try {
                ended.await(waitSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            keep(null, null);
        }

        private synchronized void keep(Integer exitCode, String failure) {
            if (!kept) {
                kept = true;
                Signal signal = stoppedBy;
                // the JVM exits with the exit code of the signal, whatever the run answered
                PreviousRun.keep(log, thisRun(name.get(), ProcessHandle.current().pid(),
                        signal != null ? Integer.valueOf(128 + signal.getNumber()) : exitCode,
                        signal != null ? "SIG" + signal.getName() : null, failure));
            }
        }
    }

    // the name of an integration as a file name: what is not a letter, a digit, a dot, a dash or an underscore
    // becomes an underscore
    static String fileName(String name) {
        String answer = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return answer.isBlank() || answer.startsWith(".") ? "_" + answer : answer;
    }

    private static boolean move(Path log, Path target) {
        try {
            Files.move(log, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            // such as a log that is still open (Windows)
            return false;
        }
    }

    // the end of the log, at most max bytes, from the start of a line
    static void copyEnd(Path log, Path target, long max) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(log.toFile(), "r");
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            long from = Math.max(0, in.length() - max);
            in.seek(from);
            if (from > 0) {
                int b;
                do {
                    b = in.read();
                } while (b != -1 && b != '\n');
            }
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }

    // a previous run, with its log, or null
    private static Info read(Path info) {
        JsonObject jo = readJson(info);
        String name = jo != null ? jo.getString("name") : null;
        if (name == null) {
            return null;
        }
        String file = info.getFileName().toString();
        Path log = info.resolveSibling(file.substring(0, file.length() - INFO_SUFFIX.length()) + LOG_SUFFIX);
        if (!Files.isRegularFile(log)) {
            return null;
        }
        return new Info(
                name, jo.getLongOrDefault("pid", 0), jo.getString("directory"),
                jo.getLongOrDefault("started", 0), jo.getLongOrDefault("ended", 0), jo.getInteger("exitCode"),
                jo.getString("signal"), jo.getString("failure"), log);
    }

    private static JsonObject readJson(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String text = Files.readString(file);
            return text.isBlank() ? null : Jsoner.deserialize(text, (JsonObject) null);
        } catch (Exception e) {
            // being written, or gone
            return null;
        }
    }
}
