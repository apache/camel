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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

/**
 * Runs a Camel CLI command in this JVM, as camel ask and the TUI do, and gives what a script running camel gets: the
 * exit code, and stdout and stderr apart. The commands share the printer of {@link CamelJBangMain} and the picocli
 * command tree, so one command runs at a time: the camel shell takes the same {@link #LOCK}.
 */
public final class CliRunner {

    /**
     * The lock of the commands that run in this JVM.
     */
    public static final Object LOCK = CamelJBangMain.class;

    /**
     * How long a command that did not finish in time gets to stop after the interrupt, before the result says it has
     * not.
     */
    private static final long STOP_GRACE_MILLIS = 2000;

    private CliRunner() {
    }

    /**
     * What a command did.
     *
     * @param exitCode  the exit code, as camel exits with it ({@link CliExitCode#TIMEOUT} when it did not finish in
     *                  time)
     * @param stdout    the output of the command
     * @param stderr    the errors, warnings and tips
     * @param elapsedMs how long the command ran
     * @param timedOut  whether the command did not finish in time, and was interrupted
     */
    public record CliResult(int exitCode, String stdout, String stderr, long elapsedMs, boolean timedOut) {

        public boolean isSuccess() {
            return exitCode == CliExitCode.OK;
        }

        /**
         * The result as JSON, for an AI agent: exitCode, stdout, stderr, elapsedMs and timedOut, where stdout and
         * stderr are cut at the given number of characters.
         */
        public JsonObject toJson(int maxChars) {
            JsonObject answer = new JsonObject();
            answer.put("exitCode", exitCode);
            answer.put("stdout", cut(stdout, maxChars));
            answer.put("stderr", cut(stderr, maxChars));
            answer.put("elapsedMs", elapsedMs);
            answer.put("timedOut", timedOut);
            return answer;
        }

        private static String cut(String text, int maxChars) {
            return text.length() > maxChars ? text.substring(0, maxChars) + "\n... (truncated)" : text;
        }
    }

    /**
     * Runs the command, and waits until it has finished.
     *
     * @param args the command line without camel, such as <tt>get route --json</tt>
     */
    public static CliResult run(List<String> args) {
        return run(args, 0);
    }

    /**
     * Runs the command, and interrupts it when it does not finish in time.
     *
     * @param args          the command line without camel, such as <tt>get route --json</tt>
     * @param timeoutMillis how long the command may run, or 0 to wait until it has finished
     */
    public static CliResult run(List<String> args, long timeoutMillis) {
        CommandLine main = CamelJBangMain.getCommandLine();
        if (main == null) {
            throw new IllegalStateException("The Camel CLI is not initialized");
        }
        // new instances of the commands, so that no field keeps the value a run before left in it
        CommandLine commandLine = ((CamelJBangMain) main.getCommand()).newCommandLine();
        Output output = new Output();
        long start = System.nanoTime();
        if (timeoutMillis <= 0) {
            int code = execute(commandLine, args, output, new AtomicBoolean());
            return output.result(code, elapsed(start), false);
        }

        // set when the caller gives up, so a command still waiting for another one to end does not run afterwards
        AtomicBoolean abandoned = new AtomicBoolean();
        FutureTask<Integer> task = new FutureTask<>(() -> execute(commandLine, args, output, abandoned));
        Thread thread = new Thread(task, "camel-cli-runner");
        thread.setDaemon(true);
        thread.start();
        try {
            int code = task.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return output.result(code, elapsed(start), false);
        } catch (TimeoutException e) {
            abandoned.set(true);
            task.cancel(true);
            output.timedOut("camel " + String.join(" ", args) + " did not finish within " + timeoutMillis + " millis"
                            + stillRunning(thread));
            return output.result(CliExitCode.TIMEOUT, elapsed(start), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abandoned.set(true);
            task.cancel(true);
            output.timedOut("camel " + String.join(" ", args) + " was interrupted");
            return output.result(CliExitCode.TIMEOUT, elapsed(start), true);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            output.timedOut("camel " + String.join(" ", args) + " failed: " + cause.getMessage());
            return output.result(CliExitCode.INTERNAL, elapsed(start), false);
        }
    }

    /**
     * What keeps a command that did not finish in time: a command that ignores the interrupt (such as one blocked in
     * I/O) holds the lock, so the next command waits until it has ended.
     */
    private static String stillRunning(Thread thread) {
        try {
            thread.join(STOP_GRACE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.getState() == Thread.State.BLOCKED) {
            return "; it waited for another command that is still running, and does not run";
        } else if (thread.isAlive()) {
            return "; it has not stopped yet, and the next command waits until it has";
        }
        return "";
    }

    private static int execute(CommandLine commandLine, List<String> args, Output output, AtomicBoolean abandoned) {
        CamelJBangMain main = (CamelJBangMain) commandLine.getCommand();
        synchronized (LOCK) {
            if (abandoned.get()) {
                // the caller gave up while another command held the lock
                return CliExitCode.TIMEOUT;
            }
            Printer printer = main.getOut();
            PrintWriter out = commandLine.getOut();
            PrintWriter err = commandLine.getErr();
            main.setOut(output.printer);
            // picocli prints the help on its out, and a usage error on its err
            commandLine.setOut(output.helpOut);
            commandLine.setErr(output.helpErr);
            try {
                RuntimeUtil.newCommand();
                return commandLine.execute(args.toArray(String[]::new));
            } finally {
                output.helpOut.flush();
                output.helpErr.flush();
                main.setOut(printer);
                commandLine.setOut(out);
                commandLine.setErr(err);
            }
        }
    }

    private static long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /**
     * What a command prints: through the printer of the CLI, and the help and usage errors of picocli.
     */
    private static final class Output {
        private final StringPrinter printer = new StringPrinter();
        private final StringWriter help = new StringWriter();
        private final StringWriter usage = new StringWriter();
        private final PrintWriter helpOut = new PrintWriter(help);
        private final PrintWriter helpErr = new PrintWriter(usage);
        private volatile String extra;

        void timedOut(String message) {
            extra = "ERROR: " + message;
        }

        CliResult result(int code, long elapsedMs, boolean timedOut) {
            String stdout = join(printer.getStdout(), help.toString().trim());
            String stderr = join(join(printer.getStderr(), usage.toString().trim()), extra);
            return new CliResult(code, stdout, stderr, elapsedMs, timedOut);
        }

        private static String join(String first, String second) {
            if (second == null || second.isEmpty()) {
                return first;
            }
            return first.isEmpty() ? second : first + System.lineSeparator() + second;
        }
    }
}
