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

import java.util.Optional;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.run.common.Printer;

/**
 * Prints a usage error of the command line (an unknown option, a missing value, an unknown command): for an unknown
 * command that is a Camel plugin, it installs the plugin.
 */
public class MissingPluginParameterExceptionHandler {

    private final CamelJBangMain main;

    public MissingPluginParameterExceptionHandler(CamelJBangMain main) {
        this.main = main;
    }

    /**
     * @param  ex   the error of the parser
     * @param  args the command line
     * @param  root the commands of the CLI
     * @return      the exit code
     */
    public int handleParseException(Exception ex, String[] args, CommandTree root) {
        Printer err = main.getOut();
        CommandTree command = commandOf(root, args);
        String message = ex.getMessage();
        if (command.getCommand() instanceof UsageErrorHandler handler) {
            Integer exitCode = handler.usageError(message, args);
            if (exitCode != null) {
                return exitCode;
            }
        }

        boolean unknownCommand = command == root && args.length > 0 && !args[0].startsWith("-");
        if (unknownCommand) {
            // the CLI installs a known plugin before it runs the command, so this is only reached when plugins are
            // not discovered: the command did not run
            Optional<PluginType> pluginType = PluginType.findByName(args[0]);
            if (pluginType.isPresent()) {
                PluginHelper.enable(pluginType.get());
                err.printDiagnostic("Installed plugin: " + pluginType.get().getName());
                err.printDiagnostic("Run the command again to use it.");
                printJsonError(command, args, CliExitCode.FAILURE,
                        "Installed plugin: " + pluginType.get().getName() + ". Run the command again to use it.");
                return CliExitCode.FAILURE;
            }
        }

        // the error, then the usage and the hints, as picocli printed them (the hints are not errors)
        err.printErr(message);
        String synopsis = synopsisOf(command);
        if (synopsis != null) {
            err.printDiagnostic(synopsis);
        }
        err.printDiagnostic(String.format("Try '%s --help' for more information.", command.getQualifiedName()));
        if (unknownCommand) {
            err.printDiagnostic(
                    "Maybe a specific Camel CLI plugin must be installed? (Try 'camel plugin --help' for more information)");
        }
        printJsonError(command, args, CliExitCode.USAGE, message);
        return CliExitCode.USAGE;
    }

    private static String synopsisOf(CommandTree command) {
        try {
            return command.synopsis();
        } catch (Exception e) {
            // the error and the hint are enough
            return null;
        }
    }

    // the deepest command that the command line names
    private static CommandTree commandOf(CommandTree root, String[] args) {
        CommandTree command = root;
        for (String arg : args) {
            CommandTree sub = command.getSubcommands().get(arg);
            if (sub == null) {
                break;
            }
            command = sub;
        }
        return command;
    }

    private void printJsonError(CommandTree cmd, String[] args, int code, String message) {
        // the option may come after the error, so the command line is checked too
        boolean json = cmd.getCommand() instanceof CamelCommand command && command.isJsonOutput()
                || JsonOutput.isRequested(args);
        if (json) {
            JsonOutput.printError(main.getOut(), code, message);
        }
    }

}
