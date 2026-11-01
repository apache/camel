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

import picocli.CommandLine;

/**
 * Parses a command line into a command object in a test, as the CLI does before it runs the command. The tests use this
 * instead of the API of the command line parser, so they stay the same when the parser changes.
 */
public final class CommandLineTestHelper {

    private CommandLineTestHelper() {
    }

    /**
     * Sets the options and arguments of the command from the command line, and the defaults of the options that are not
     * given.
     *
     * @param  command      the command
     * @param  args         the command line, without the name of the command
     * @return              the command
     * @throws CliException with the usage exit code, when the command line is not valid for the command
     */
    public static <T> T parse(T command, String... args) {
        try {
            return CommandLine.populateCommand(command, args);
        } catch (CommandLine.ParameterException e) {
            throw new CliException(CliExitCode.USAGE, e.getMessage(), e);
        }
    }
}
