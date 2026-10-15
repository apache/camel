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

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.NoSuchFileException;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.ResourceDoesNotExist;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IExitCodeExceptionMapper;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/**
 * Ends a command that threw an exception: prints the error on stderr, and returns the exit code from
 * {@link CliExitCode}.
 * <p/>
 * A {@link CliException} prints its message as one {@code ERROR:} line, and exits with its code. A file that does not
 * exist exits with {@link CliExitCode#NOT_FOUND}. Any other exception is unexpected: it prints the error with the stack
 * trace, and exits with {@link CliExitCode#INTERNAL}. With {@code --verbose} or the environment variable
 * {@code CAMEL_CLI_DEBUG=true}, every error prints its stack trace.
 */
public class CliExecutionExceptionHandler implements IExecutionExceptionHandler, IExitCodeExceptionMapper {

    /**
     * The environment variable that prints the stack trace of every error.
     */
    public static final String DEBUG_ENV = "CAMEL_CLI_DEBUG";

    private final CamelJBangMain main;

    public CliExecutionExceptionHandler(CamelJBangMain main) {
        this.main = main;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) {
        return handle(ex, parseResult);
    }

    /**
     * Prints the error on stderr, and returns the exit code.
     *
     * @param  ex          the exception or error that ended the command
     * @param  parseResult the parsed command line, to detect {@code --verbose}, or null
     * @return             the exit code
     */
    public int handle(Throwable ex, ParseResult parseResult) {
        int code = getExitCode(ex);
        Printer printer = main.getOut();
        printer.printErr(message(ex));
        if (code == CliExitCode.INTERNAL || isVerbose(parseResult)) {
            printer.printDiagnostic(stackTrace(ex));
        }
        return code;
    }

    @Override
    public int getExitCode(Throwable ex) {
        CliException cli = find(ex, CliException.class);
        if (cli != null) {
            return cli.getExitCode();
        }
        if (ex instanceof ParameterException) {
            return CliExitCode.USAGE;
        }
        if (fileNotFound(ex) != null) {
            return CliExitCode.NOT_FOUND;
        }
        return CliExitCode.INTERNAL;
    }

    private static String message(Throwable ex) {
        CliException cli = find(ex, CliException.class);
        if (cli != null) {
            return messageOf(cli);
        }
        Throwable missing = fileNotFound(ex);
        if (missing instanceof NoSuchFileException e) {
            // the message is only the path
            return "File not found: " + e.getFile();
        } else if (missing instanceof FileNotFoundException) {
            return "File not found: " + messageOf(missing);
        } else if (missing != null) {
            return messageOf(missing);
        }
        return messageOf(ex);
    }

    private static Throwable fileNotFound(Throwable ex) {
        Throwable answer = find(ex, NoSuchFileException.class);
        if (answer == null) {
            answer = find(ex, FileNotFoundException.class);
        }
        if (answer == null) {
            answer = find(ex, ResourceDoesNotExist.class);
        }
        return answer;
    }

    private static String messageOf(Throwable ex) {
        String msg = ex.getMessage();
        return msg != null && !msg.isBlank() ? msg : ex.getClass().getName();
    }

    private static <T extends Throwable> T find(Throwable ex, Class<T> type) {
        // guard against a cause that points back to an exception in the chain
        int depth = 0;
        for (Throwable t = ex; t != null && depth < 20; t = t.getCause(), depth++) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static boolean isVerbose(ParseResult parseResult) {
        if ("true".equalsIgnoreCase(System.getenv(DEBUG_ENV))) {
            return true;
        }
        for (ParseResult pr = parseResult; pr != null; pr = pr.subcommand()) {
            Object verbose = pr.hasMatchedOption("--verbose") ? pr.matchedOptionValue("--verbose", null) : null;
            if (Boolean.TRUE.equals(verbose)) {
                return true;
            }
        }
        return false;
    }

    private static String stackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString().stripTrailing();
    }
}
