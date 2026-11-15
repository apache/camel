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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.DefaultValueProvider;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.invocation.CommandInvocation;
import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.CliExecutionExceptionHandler;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.dsl.jbang.run.common.Printer;
import org.apache.camel.dsl.jbang.run.common.RuntimeUtil;
import org.jline.jansi.AnsiConsole;
import org.jline.jansi.AnsiMode;

public abstract class CamelCommand implements Command<CommandInvocation>, CommandTree.ParsedCommandAware {

    // the model of the options of this command, and the tree it runs in (set when the command line is built)
    private ProcessedCommand<?, ?> processedCommand;
    private CommandTree tree;
    private CommandInvocation invocation;

    private final CamelJBangMain main;

    protected CamelCommand(CamelJBangMain main) {
        this.main = main;
    }

    public CamelJBangMain getMain() {
        return main;
    }

    protected void configureLoggingOff() throws Exception {
        RuntimeUtil.configureLog("off", false, false, false, false, null, null);
    }

    protected boolean disarrangeLogging() {
        return true;
    }

    @Override
    public void setProcessedCommand(ProcessedCommand<?, ?> processedCommand, CommandTree tree) {
        this.processedCommand = processedCommand;
        this.tree = tree;
    }

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        this.invocation = invocation;
        if (main.parseOnly(this)) {
            return CommandResult.SUCCESS;
        }
        try {
            return CommandResult.valueOf(call());
        } catch (Exception e) {
            return CommandResult.valueOf(new CliExecutionExceptionHandler(main).handle(e, this));
        }
    }

    public Integer call() throws Exception {
        if (disarrangeLogging()) {
            configureLoggingOff();
        }

        replacePlaceholders();

        return doCall();
    }

    private void replacePlaceholders() throws Exception {
        DefaultValueProvider provider = tree != null ? tree.getDefaultValueProvider() : null;
        if (processedCommand == null || provider == null) {
            return;
        }
        for (ProcessedOption option : processedCommand.getOptions()) {
            String defaultValue = provider.defaultValue(option);
            Object holder = holderOf(option);
            if (defaultValue == null || holder == null) {
                continue;
            }
            List<String> names = new ArrayList<>();
            names.add(option.name());
            names.addAll(option.getAliases());
            for (String name : names) {
                String placeholder = "#" + name;
                Object v = option.getFieldValue(holder);
                if (v instanceof String text && text.contains(placeholder)) {
                    option.setFieldValue(holder, text.replace(placeholder, defaultValue));
                }
            }
        }
    }

    /**
     * The arguments of the command line that runs this command, or {@code null} when it runs without one.
     */
    protected List<String> originalArgs() {
        return tree != null ? tree.getArgs() : null;
    }

    private ProcessedOption findOption(String name) {
        if (processedCommand == null) {
            return null;
        }
        String n = name.startsWith("--") ? name.substring(2) : name;
        for (ProcessedOption option : processedCommand.getOptions()) {
            if (n.equals(option.name()) || option.getAliases().contains(n)) {
                return option;
            }
        }
        return null;
    }

    // the object to read and write the field of the option on: the command itself, as aesh finds the mixin (also a
    // mixin of a mixin) that holds the field from the command
    private Object holderOf(ProcessedOption option) {
        return this;
    }

    public abstract Integer doCall() throws Exception;

    /**
     * Whether the command prints its output as JSON (the option {@code --json}). Its errors are then printed as JSON on
     * stdout too, see {@link JsonOutput}. A command with another way to ask for JSON overrides this.
     */
    public boolean isJsonOutput() {
        return Boolean.TRUE.equals(optionValue(JsonOutput.OPTION));
    }

    /**
     * The value of an option of this command, such as {@code --timeout}.
     *
     * @param  name the name of the option
     * @return      the value, or <tt>null</tt> when the command has no such option, or was not parsed by aesh
     */
    public Object optionValue(String name) {
        ProcessedOption option = findOption(name);
        Object holder = option != null ? holderOf(option) : null;
        return holder != null ? option.getFieldValue(holder) : null;
    }

    /**
     * The names of the options given on the command line (without {@code --}), such as {@code trace}; empty when the
     * command was not parsed by aesh.
     */
    protected Set<String> givenOptions() {
        Set<String> answer = new HashSet<>();
        if (processedCommand != null) {
            for (ProcessedOption option : processedCommand.getOptions()) {
                if (!option.getValues().isEmpty()) {
                    answer.add(option.name());
                }
            }
        }
        return answer;
    }

    /**
     * Whether an option was given on the command line, rather than having its default value.
     */
    protected boolean isOptionGiven(String name) {
        ProcessedOption option = findOption(name);
        return option != null && !option.getValues().isEmpty();
    }

    /**
     * Whether a command that follows, such as camel log, keeps following. Without an interactive terminal nobody can
     * press Enter to stop it, so then it only follows when --follow or --duration is given.
     *
     * @param follow   the --follow option
     * @param duration the --duration option, in millis
     */
    protected boolean isFollowing(boolean follow, long duration) {
        return follow && (duration > 0 || isOptionGiven("--follow") || EnvironmentHelper.isInteractiveTerminal());
    }

    /**
     * Fails a command that needs an interactive terminal when there is none, instead of waiting for input that never
     * comes.
     *
     * @param  what         what needs the terminal, such as camel debug
     * @throws CliException with exit code 2 without an interactive terminal
     */
    protected void requireInteractiveTerminal(String what) {
        if (!EnvironmentHelper.isInteractiveTerminal()) {
            throw CliException.usage(what + " requires an interactive terminal");
        }
    }

    /**
     * Prints the help of this command, which lists its sub-commands.
     *
     * @return the exit code
     */
    protected int printHelp() {
        if (invocation != null) {
            printer().println(invocation.getHelpInfo());
        }
        return CliExitCode.OK;
    }

    /**
     * Runs the given command as the default sub-command of this command, with the error handling of the CLI.
     *
     * @param  command the command to run
     * @param  args    the arguments for the command
     * @return         the exit code of the command
     */
    protected int runDefault(CamelCommand command, String... args) {
        try {
            return new CommandTree(command).setOutput(text -> command.printer().print(text)).execute(args)
                    .getResultValue();
        } catch (Exception e) {
            return new CliExecutionExceptionHandler(getMain()).handle(e, command);
        }
    }

    /**
     * The values of an option of several values as an array, or {@code null}.
     */
    protected static String[] array(List<String> values) {
        return values != null ? values.toArray(new String[0]) : null;
    }

    /**
     * The scheme of a file, such as classpath or github, or null for a file without scheme. A single letter is the
     * drive of a path on Windows (C:\work\route.camel.yaml), not a scheme (CAMEL-26912).
     */
    protected static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos > 1) {
            return name.substring(0, pos);
        }
        return null;
    }

    public Path getStatusFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
    }

    /**
     * The action file that all the clients of the integration share.
     *
     * @deprecated use {@link ActionClient}, which sends each action in a file of its own, and uses this file only for
     *             an integration before Camel 4.21
     */
    @Deprecated(since = "4.24")
    public Path getActionFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-action.json");
    }

    /**
     * The output file that all the clients of the integration share.
     *
     * @deprecated use {@link ActionClient}, which reads the answer to each action from a file of its own, and uses this
     *             file only for an integration before Camel 4.21
     */
    @Deprecated(since = "4.24")
    public Path getOutputFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-output.json");
    }

    public Path getTraceFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
    }

    public Path getErrorFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-error.json");
    }

    public Path getReceiveFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-receive.json");
    }

    public Path getDebugFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-debug.json");
    }

    public Path getMessageHistoryFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-history.json");
    }

    public Path getTimelineFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-timeline.json");
    }

    protected Printer printer() {
        var out = getMain().getOut();
        CommandHelper.setPrinter(out);
        return out;
    }

    /**
     * Prints text with colors (ANSI escape codes): on the console when the command prints to the console, which leaves
     * the colors out when the output is not a terminal; else with the printer of the command, as in the shell, or in a
     * CLI that runs the command in its own JVM and keeps its output (see {@link CliRunner}).
     */
    protected void printColored(Object text) {
        if (printsToConsole()) {
            AnsiConsole.out().print(text);
        } else {
            printer().print(String.valueOf(text));
        }
    }

    /**
     * Prints a line with colors (ANSI escape codes), see {@link #printColored(Object)}.
     */
    protected void printlnColored(Object text) {
        if (printsToConsole()) {
            AnsiConsole.out().println(text);
        } else {
            printer().println(String.valueOf(text));
        }
    }

    /**
     * Whether the console strips the ANSI colors of {@link #printColored(Object)}: the command prints to the console,
     * and the output is not a terminal (a file or a pipe). A command that prints much text in colors, such as camel
     * log, prints it without colors then, as letting the console strip the colors of each piece of text is slow
     * (CAMEL-26911).
     */
    protected boolean colorsStripped() {
        return printsToConsole() && AnsiConsole.out().getMode() == AnsiMode.Strip;
    }

    /**
     * Whether the command prints in colors: as --logging-color says when it is given, else only when it prints to a
     * terminal, as the default of the option is colors. In a file or a pipe, which an AI agent or a script reads, the
     * ANSI codes of the colors are noise (CAMEL-26929). A command that prints to another printer than the console keeps
     * its option.
     *
     * @param  loggingColor the --logging-color option
     * @return              whether to print in colors
     */
    protected boolean colorOutput(boolean loggingColor) {
        if (!loggingColor || isOptionGiven("--logging-color")) {
            return loggingColor;
        }
        return !printsToConsole() || EnvironmentHelper.hasTerminal();
    }

    private boolean printsToConsole() {
        return printer() instanceof Printer.SystemOutPrinter && !EnvironmentHelper.isEmbedded();
    }

    protected int terminalWidth() {
        return TerminalWidthHelper.getTerminalWidth();
    }

    protected void printConfigurationValues(String header) {
        if (processedCommand != null) {
            // the global user configuration with the local one laid over it, as the option defaults come from it
            final Properties configProperties = CommandLineHelper.loadUserConfiguration(printer());
            List<String> lines = new ArrayList<>();
            for (ProcessedOption opt : processedCommand.getOptions()) {
                Object holder = holderOf(opt);
                // the configuration may name the option by any of its long names, such as dev for --reload,--dev
                boolean configured = configProperties.containsKey(opt.name())
                        || opt.getAliases().stream().anyMatch(configProperties::containsKey);
                if (configured && holder != null) {
                    lines.add(String.format("    --%s=%s",
                            opt.name(), Optional.ofNullable(opt.getFieldValue(holder)).orElse("")));
                }
            }
            if (!lines.isEmpty()) {
                printer().println(header);
                lines.forEach(printer()::println);
            }
        }
    }

}
