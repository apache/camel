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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    protected void configureLoggingOff() {
        RuntimeUtil.configureLog("off", false, false, false, false, null);
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
                        argSpec instanceof CommandLine.Model.OptionSpec) {
                    CommandLine.Model.OptionSpec optionSpec = (CommandLine.Model.OptionSpec) argSpec;
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

    public File getStatusFile(String pid) {
        return new File(CommandLineHelper.getCamelDir(), pid + "-status.json");
    }

    public File getActionFile(String pid) {
        return new File(CommandLineHelper.getCamelDir(), pid + "-action.json");
    }

    public File getOutputFile(String pid) {
        return new File(CommandLineHelper.getCamelDir(), pid + "-output.json");
    }

    public File getTraceFile(String pid) {
        return new File(CommandLineHelper.getCamelDir(), pid + "-trace.json");
    }

    public File getDebugFile(String pid) {
        return new File(CommandLineHelper.getCamelDir(), pid + "-debug.json");
    }

    protected Printer printer() {
        return getMain().getOut();
    }

    protected void printConfigurationValues(String header) {
        final Properties configProperties = new Properties();
        CommandLineHelper.loadProperties(configProperties::putAll);
        List<String> lines = new ArrayList<>();
        spec.options().forEach(opt -> {
            if (Arrays.stream(opt.names()).anyMatch(name ->
            // name starts with --
            configProperties.containsKey(name.substring(2)))) {
                lines.add(String.format("    %s=%s",
                        opt.longestName(), opt.getValue().toString()));
            }
        });
        if (!lines.isEmpty()) {
            printer().println(header);
            lines.forEach(printer()::println);
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
