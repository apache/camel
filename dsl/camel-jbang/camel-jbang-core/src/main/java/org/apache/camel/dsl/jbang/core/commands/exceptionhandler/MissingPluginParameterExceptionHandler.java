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
package org.apache.camel.dsl.jbang.core.commands.exceptionhandler;

import java.io.PrintWriter;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import picocli.CommandLine;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * Ends a command line that cannot be parsed, such as an unknown option, with the usage on stderr and exit code
 * {@link CliExitCode#USAGE}. A command line that asks for JSON ({@code --json}) also gets the error as a JSON document
 * on stdout, see {@link JsonOutput}.
 */
public class MissingPluginParameterExceptionHandler implements IParameterExceptionHandler {

    private final CamelJBangMain main;

    public MissingPluginParameterExceptionHandler() {
        this(null);
    }

    /**
     * @param main the CLI, whose printer gets the JSON document of an error
     */
    public MissingPluginParameterExceptionHandler(CamelJBangMain main) {
        this.main = main;
    }

    @Override
    public int handleParseException(ParameterException ex, String[] args) throws Exception {
        CommandLine cmd = ex.getCommandLine();
        PrintWriter err = cmd.getErr();
        if (cmd.getCommand() instanceof UsageErrorHandler handler) {
            Integer exitCode = handler.usageError(ex.getMessage(), args);
            if (exitCode != null) {
                return exitCode;
            }
        }

        if (ex.getMessage().startsWith("Unmatched argument at index 0") && args.length > 0) {
            // the CLI installs a known plugin before it runs the command, so this is only reached when plugins are
            // not discovered: the command did not run
            Optional<PluginType> pluginType = PluginType.findByName(args[0]);
            if (pluginType.isPresent()) {
                PluginHelper.enable(pluginType.get());
                err.printf("Installed plugin: %s%n", pluginType.get().getName());
                err.println("Run the command again to use it.");
                printJsonError(cmd, args, CliExitCode.FAILURE,
                        "Installed plugin: " + pluginType.get().getName() + ". Run the command again to use it.");
                return CliExitCode.FAILURE;
            }
        }

        if ("DEBUG".equalsIgnoreCase(System.getProperty("picocli.trace"))) {
            err.println(cmd.getColorScheme().stackTraceText(ex));
        }

        err.println(cmd.getColorScheme().errorText(ex.getMessage()));
        UnmatchedArgumentException.printSuggestions(ex, err);
        err.print(cmd.getHelp().fullSynopsis());

        CommandSpec spec = cmd.getCommandSpec();
        err.printf("Try '%s --help' for more information.%n", spec.qualifiedName());

        if (ex.getMessage().startsWith("Unmatched argument at index 0")) {
            err.println(cmd.getColorScheme().errorText(
                    "Maybe a specific Camel CLI plugin must be installed? (Try 'camel plugin --help' for more information)"));
        }

        int code = cmd.getExitCodeExceptionMapper() != null
                ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                : spec.exitCodeOnInvalidInput();
        printJsonError(cmd, args, code, ex.getMessage());
        return code;
    }

    private void printJsonError(CommandLine cmd, String[] args, int code, String message) {
        // the option may come after the error, so the command line is checked too
        boolean json = cmd.getCommand() instanceof CamelCommand command && command.isJsonOutput()
                || JsonOutput.isRequested(args);
        if (json) {
            if (main != null) {
                JsonOutput.printError(main.getOut(), code, message);
            } else {
                cmd.getOut().println(JsonOutput.error(code, message).toJson());
                cmd.getOut().flush();
            }
        }
    }

}
