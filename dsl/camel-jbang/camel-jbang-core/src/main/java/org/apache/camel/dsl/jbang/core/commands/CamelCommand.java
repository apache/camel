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

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
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

    public CamelCommand(CamelJBangMain main) {
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

    public Path getStatusFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
    }

    public Path getActionFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-action.json");
    }

    public Path getOutputFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-output.json");
    }

    public Path getTraceFile(String pid) {
        return CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
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

    public Path getRunBackgroundLogFile(String uuid) {
        return CommandLineHelper.getCamelDir().resolve(uuid + "-run.log");
    }

    protected Printer printer() {
        var out = getMain().getOut();
        CommandHelper.setPrinter(out);
        return out;
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
