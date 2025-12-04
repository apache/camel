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

import picocli.CommandLine;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

public class MissingPluginParameterExceptionHandler implements IParameterExceptionHandler {

    @Override
    public int handleParseException(ParameterException ex, String[] args) throws Exception {
        CommandLine cmd = ex.getCommandLine();
        PrintWriter err = cmd.getErr();

        if ("DEBUG".equalsIgnoreCase(System.getProperty("picocli.trace"))) {
            err.println(cmd.getColorScheme().stackTraceText(ex));
        }

        err.println(cmd.getColorScheme().errorText(ex.getMessage()));
        UnmatchedArgumentException.printSuggestions(ex, err);
        err.print(cmd.getHelp().fullSynopsis());

        CommandSpec spec = cmd.getCommandSpec();
        err.printf("Try '%s --help' for more information.%n", spec.qualifiedName());

        if (ex.getMessage().startsWith("Unmatched argument at index 0")) {
            err.println(
                    cmd.getColorScheme()
                            .errorText(
                                    "Maybe a specific Camel JBang plugin must be installed? (Try camel plugin --help' for more information)"));
        }

        return cmd.getExitCodeExceptionMapper() != null
                ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                : spec.exitCodeOnInvalidInput();
    }
}
