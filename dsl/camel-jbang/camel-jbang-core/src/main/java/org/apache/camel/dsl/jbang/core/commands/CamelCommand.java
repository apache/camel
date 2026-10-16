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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.Callable;

import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

public abstract class CamelCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private final CamelJBangMain main;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;

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
    public Integer call() throws Exception {
        if (disarrangeLogging()) {
            configureLoggingOff();
        }

        replacePlaceholders();

        return doCall();
    }

    private void replacePlaceholders() throws Exception {
        if (spec != null) {
            for (CommandLine.Model.ArgSpec argSpec : spec.args()) {
                var provider = spec.defaultValueProvider();
                String defaultValue = provider != null ? provider.defaultValue(argSpec) : null;
                if (defaultValue != null &&
                        argSpec instanceof CommandLine.Model.OptionSpec optionSpec) {
                    for (String name : optionSpec.names()) {
                        String placeholder = "#" + StringHelper.after(name, "--");
                        Object v = argSpec.getValue();
                        if (v != null &&
                                v.toString().contains(placeholder)) {
                            argSpec.setValue(v.toString().replace(placeholder, defaultValue));
                        }
                    }
                }
            }
        }
    }

    public abstract Integer doCall() throws Exception;

    /**
     * Whether the command prints its output as JSON (the option {@code --json}). Its errors are then printed as JSON on
     * stdout too, see {@link JsonOutput}. A command with another way to ask for JSON overrides this.
     */
    public boolean isJsonOutput() {
        if (spec != null) {
            CommandLine.Model.OptionSpec option = spec.findOption(JsonOutput.OPTION);
            return option != null && Boolean.TRUE.equals(option.getValue());
        }
        return false;
    }

    /**
     * The value of an option of this command, such as {@code --timeout}.
     *
     * @param  name the name of the option
     * @return      the value, or <tt>null</tt> when the command has no such option, or runs without picocli
     */
    protected Object optionValue(String name) {
        CommandLine.Model.OptionSpec option = spec != null ? spec.findOption(name) : null;
        return option != null ? option.getValue() : null;
    }

    /**
     * Whether an option was given on the command line, rather than having its default value.
     */
    protected boolean isOptionGiven(String name) {
        CommandLine.ParseResult result = spec != null ? spec.commandLine().getParseResult() : null;
        return result != null && result.hasMatchedOption(name);
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
        CommandLine cl = spec != null ? spec.commandLine() : new CommandLine(this);
        printer().print(cl.getUsageMessage());
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
        return getMain().withErrorHandling(new CommandLine(command)).execute(args);
    }

    protected static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
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

    public Path getActivityFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-activity.json");
    }

    protected Printer printer() {
        var out = getMain().getOut();
        CommandHelper.setPrinter(out);
        return out;
    }

    protected int terminalWidth() {
        return TerminalWidthHelper.getTerminalWidth();
    }

    protected void printConfigurationValues(String header) {
        if (spec != null) {
            final Properties configProperties = new Properties();
            CommandLineHelper.loadProperties(configProperties::putAll);
            List<String> lines = new ArrayList<>();
            spec.options().forEach(opt -> {
                if (Arrays.stream(opt.names()).anyMatch(name ->
                // name starts with --
                configProperties.containsKey(name.substring(2)))) {
                    lines.add(String.format("    %s=%s",
                            opt.longestName(), Optional.ofNullable(opt.getValue()).orElse("")));
                }
            });
            if (!lines.isEmpty()) {
                printer().println(header);
                lines.forEach(printer()::println);
            }
        }
    }

    protected abstract static class ParameterConsumer<T> implements IParameterConsumer {

        @Override
        public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec cmdSpec) {
            if (failIfEmptyArgs() && args.isEmpty()) {
                throw new ParameterException(cmdSpec.commandLine(), "Error: missing required parameter");
            }
            T cmd = (T) cmdSpec.userObject();
            doConsumeParameters(args, cmd);
        }

        protected abstract void doConsumeParameters(Stack<String> args, T cmd);

        protected boolean failIfEmptyArgs() {
            return true;
        }
    }

}
