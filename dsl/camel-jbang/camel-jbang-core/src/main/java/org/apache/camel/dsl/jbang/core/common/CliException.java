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

/**
 * Ends a command with an exit code and one error message.
 * <p/>
 * A command throws it instead of printing an error and returning a code. The CLI prints the message as one
 * {@code ERROR:} line on stderr, and exits with the code from {@link CliExitCode}. The stack trace is only printed in
 * verbose mode, as the message must say what went wrong and what to do about it.
 */
public class CliException extends RuntimeException {

    private final int exitCode;

    public CliException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public CliException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    /**
     * The exit code of the command, one of {@link CliExitCode}.
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * The command ran, but the outcome is negative.
     */
    public static CliException failure(String message) {
        return new CliException(CliExitCode.FAILURE, message);
    }

    /**
     * The command was called wrong: an invalid option or argument.
     */
    public static CliException usage(String message) {
        return new CliException(CliExitCode.USAGE, message);
    }

    /**
     * A file, integration, route, component or service named on the command line does not exist.
     */
    public static CliException notFound(String message) {
        return new CliException(CliExitCode.NOT_FOUND, message);
    }

    /**
     * The integration did not reply, start or stop in time.
     */
    public static CliException timeout(String message) {
        return new CliException(CliExitCode.TIMEOUT, message);
    }
}
