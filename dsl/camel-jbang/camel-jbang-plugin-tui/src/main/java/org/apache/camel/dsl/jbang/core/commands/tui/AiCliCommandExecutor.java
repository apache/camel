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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

final class AiCliCommandExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AiCliCommandExecutor.class);
    private static final long CANCEL_TIMEOUT_MILLIS = 30_000;

    private final Invoker invoker;
    private final long cancelTimeoutMillis;
    private ActiveCommand activeCommand;

    AiCliCommandExecutor() {
        this(AiCliCommandExecutor::invoke);
    }

    AiCliCommandExecutor(Invoker invoker) {
        this(invoker, CANCEL_TIMEOUT_MILLIS);
    }

    AiCliCommandExecutor(Invoker invoker, long cancelTimeoutMillis) {
        this.invoker = invoker;
        this.cancelTimeoutMillis = cancelTimeoutMillis;
    }

    synchronized CompletableFuture<Result> executeAsync(Request request) {
        if (activeCommand != null) {
            String message = activeCommand.cancelled.get()
                    ? "A previous command is still stopping after cancellation. Please wait and try again."
                    : "A CLI command is already running";
            return CompletableFuture.failedFuture(new IllegalStateException(message));
        }

        CompletableFuture<Result> result = new CompletableFuture<>();
        ActiveCommand command = new ActiveCommand(request, result);
        Thread thread = new Thread(() -> execute(command), "tui-ai-cli-command");
        thread.setDaemon(true);
        command.thread = thread;
        activeCommand = command;
        thread.start();
        return result;
    }

    void cancel() {
        ActiveCommand command;
        synchronized (this) {
            command = activeCommand;
        }
        if (command == null) {
            return;
        }

        Thread thread = command.thread;
        command.cancelled.set(true);
        thread.interrupt();

        // Wait for the cancellation timeout off the caller's thread (typically the TUI event/render
        // thread) so an unresponsive external command cannot freeze the UI for cancelTimeoutMillis.
        Thread watcher = new Thread(() -> awaitCancellationTimeout(command, thread), "tui-ai-cli-cancel-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void awaitCancellationTimeout(ActiveCommand command, Thread thread) {
        try {
            thread.join(cancelTimeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (thread.isAlive()) {
            command.result.complete(new Result(
                    command.request.displayText(), 130, "Command cancellation timed out\n",
                    elapsedMillis(command.startedAtNanos), true));
        }
    }

    private void execute(ActiveCommand command) {
        StringBuilder output = new StringBuilder();
        int exitCode = 1;
        boolean interrupted = false;
        try {
            synchronized (CamelJBangMain.class) {
                exitCode = invoker.execute(command.request.argv(), new CapturePrinter(output));
            }
            interrupted = command.cancelled.get() || Thread.currentThread().isInterrupted();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "(no message; see logs for the stack trace)";
            output.append(e.getClass().getSimpleName()).append(": ").append(message).append('\n');
            LOG.warn("Failed to execute CLI command: {}", command.request.displayText(), e);
            interrupted = command.cancelled.get() || Thread.currentThread().isInterrupted();
        } finally {
            command.result.complete(
                    new Result(
                            command.request.displayText(), exitCode, output.toString(), elapsedMillis(command.startedAtNanos),
                            interrupted));
            synchronized (this) {
                if (activeCommand == command) {
                    activeCommand = null;
                }
            }
        }
    }

    private static int invoke(List<String> argv, Printer printer) throws Exception {
        CommandLine commandLine = CamelJBangMain.getCommandLine();
        CamelJBangMain main = (CamelJBangMain) commandLine.getCommand();
        Printer originalPrinter = main.getOut();
        PrintWriter originalErr = commandLine.getErr();
        StringWriter errCapture = new StringWriter();
        PrintWriter errWriter = new PrintWriter(errCapture);
        String originalProcess = EnvironmentHelper.getSelectedProcess();
        String selectedProcess = selectedProcess(argv);
        try {
            main.setOut(printer);
            // picocli's default execution strategy catches exceptions thrown by a sub-command and
            // prints them to CommandLine.getErr() instead of rethrowing, so without redirecting it
            // here that output would go to the real System.err and never reach the TUI.
            commandLine.setErr(errWriter);
            if (selectedProcess != null) {
                EnvironmentHelper.setSelectedProcess(selectedProcess);
            }
            return commandLine.execute(argv.toArray(String[]::new));
        } finally {
            main.setOut(originalPrinter);
            commandLine.setErr(originalErr);
            errWriter.flush();
            String errOutput = errCapture.toString();
            if (!errOutput.isBlank()) {
                printer.print(errOutput);
            }
            if (selectedProcess != null) {
                EnvironmentHelper.setSelectedProcess(originalProcess);
            }
        }
    }

    private static String selectedProcess(List<String> argv) {
        if (argv.size() > 2 && "cmd".equals(argv.get(0)) && "send".equals(argv.get(1))
                && !argv.get(2).startsWith("--")) {
            return argv.get(2);
        }
        return null;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    interface Invoker {
        int execute(List<String> argv, Printer printer) throws Exception;
    }

    private static final class ActiveCommand {

        private final Request request;
        private final CompletableFuture<Result> result;
        private final long startedAtNanos = System.nanoTime();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Thread thread;

        private ActiveCommand(Request request, CompletableFuture<Result> result) {
            this.request = request;
            this.result = result;
        }
    }

    private static final class CapturePrinter implements Printer {

        private final StringBuilder output;

        private CapturePrinter(StringBuilder output) {
            this.output = output;
        }

        @Override
        public void println() {
            output.append(System.lineSeparator());
        }

        @Override
        public void println(String line) {
            output.append(line).append(System.lineSeparator());
        }

        @Override
        public void print(String text) {
            output.append(text);
        }

        @Override
        public void printf(String format, Object... args) {
            output.append(format.formatted(args));
        }
    }

    record Request(List<String> argv, String displayText) {

        Request {
            argv = List.copyOf(argv);
        }

        Request(String displayText) {
            this(List.of(), displayText);
        }

        static Request run(String rawTail) {
            List<String> args = new ArrayList<>();
            args.add("run");
            args.addAll(splitRawTail(rawTail));
            return new Request(args, "camel " + String.join(" ", args));
        }

        static Request infra(String rawTail) {
            List<String> args = new ArrayList<>();
            args.add("infra");
            args.addAll(splitRawTail(rawTail));
            return new Request(args, "camel " + String.join(" ", args));
        }

        static Request send(String selectedProcessName, AiSlashCommandRegistry.SendCommand send) {
            List<String> args = new ArrayList<>();
            args.add("cmd");
            args.add("send");
            if (selectedProcessName != null && !selectedProcessName.isBlank()) {
                args.add(selectedProcessName);
            }
            args.add("--endpoint=" + send.endpoint());
            args.add("--body=" + (send.fileBody() ? "file:" + send.body() : send.body()));
            return new Request(args, "camel " + String.join(" ", args));
        }

        static List<String> splitRawTail(String rawTail) {
            if (rawTail == null || rawTail.isBlank()) {
                return List.of();
            }
            List<String> words = new ArrayList<>();
            StringBuilder word = new StringBuilder();
            char quote = 0;
            boolean wordStarted = false;
            for (int i = 0; i < rawTail.length(); i++) {
                char character = rawTail.charAt(i);
                if (quote != 0) {
                    if (character == quote) {
                        quote = 0;
                    } else if (character == '\\' && quote == '"' && i + 1 < rawTail.length()) {
                        word.append(rawTail.charAt(++i));
                    } else {
                        word.append(character);
                    }
                    continue;
                }

                if (character == '\'' || character == '"') {
                    quote = character;
                    wordStarted = true;
                } else if (Character.isWhitespace(character)) {
                    if (wordStarted) {
                        words.add(word.toString());
                        word.setLength(0);
                        wordStarted = false;
                    }
                } else if (character == '\\' && i + 1 < rawTail.length()) {
                    word.append(rawTail.charAt(++i));
                    wordStarted = true;
                } else {
                    word.append(character);
                    wordStarted = true;
                }
            }
            if (wordStarted) {
                words.add(word.toString());
            }
            return List.copyOf(words);
        }
    }

    record Result(String displayText, int exitCode, String output, long elapsedMs, boolean interrupted) {
    }
}
