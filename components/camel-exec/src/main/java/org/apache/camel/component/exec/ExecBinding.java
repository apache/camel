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
package org.apache.camel.component.exec;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * Represents the binding of input and output types of a {@link ExecCommandExecutor} to an {@link Exchange}. The input
 * of the executor is an {@link ExecCommand} and the output is an {@link ExecResult}.
 */
public interface ExecBinding {

    /**
     * The header value overrides the executable of the command, configured in the exec endpoint URI. As executable is
     * considered the remaining of the {@link ExecEndpoint} URI; <br>
     * <br>
     * e.g. in the URI <i> <code>exec:C:/Program Files/jdk/java.exe</code> </i>, <code>C:/Program
     * Files/jdk/java.exe<code> is the executable.
     */
    @Metadata(label = "in", description = "The name of the system command that will be executed. Overrides\n" +
                                          "`executable` in the URI.",
              javaType = "String")
    String EXEC_COMMAND_EXECUTABLE = "CamelExecCommandExecutable";

    /**
     * The header value overrides the existing command arguments in the {@link ExecEndpoint} URI. The arguments may be a
     * <code>List<String></code>. In this case no parsing of the arguments is necessary.
     *
     * @see #EXEC_COMMAND_EXECUTABLE
     */
    @Metadata(label = "in", description = "Command-line argument(s) to pass to the executed process. The argument(s)\n" +
                                          "is/are used literally - no quoting is applied. Overrides any existing\n" +
                                          "`args` in the URI.",
              javaType = "java.util.List<String> or String")
    String EXEC_COMMAND_ARGS = "CamelExecCommandArgs";

    /**
     * Specifies the file name of a file, created by the executable, that should be considered as output of the
     * executable, e.g. a log file.
     *
     * @see ExecResultConverter#toInputStream(ExecResult)
     */
    @Metadata(label = "in", description = "The name of a file, created by the executable, that should be considered\n" +
                                          "as its output. Overrides any existing `outFile` in the URI.",
              javaType = "String")
    String EXEC_COMMAND_OUT_FILE = "CamelExecCommandOutFile";

    /**
     * Sets the working directory of the {@link #EXEC_COMMAND_EXECUTABLE}. The header value overrides any existing
     * command in the endpoint URI. If this is not configured, the working directory of the current process will be
     * used.
     */
    @Metadata(label = "in", description = "The directory in which the command should be executed. Overrides any\n" +
                                          "existing `workingDir` in the URI.",
              javaType = "String")
    String EXEC_COMMAND_WORKING_DIR = "CamelExecCommandWorkingDir";

    /**
     * Specifies the amount of time, in milliseconds, after which the process of the executable should be terminated.
     * The default value is {@link Long#MAX_VALUE}.
     */
    @Metadata(label = "in", description = "The timeout, in milliseconds, after which the executable should be\n" +
                                          "terminated. Overrides any existing `timeout` in the URI.",
              javaType = "long")
    String EXEC_COMMAND_TIMEOUT = "CamelExecCommandTimeout";

    /**
     * Which exit values of the process are considered a success. When the process exits with a value not in this list,
     * an ExecuteException is raised. When the list is empty (the default), no exception is raised based on the exit
     * value. Example:
     */
    @Metadata(label = "in", description = "The exit values for successful execution of the process.\n" +
                                          "Overrides any existing `exitValues` in the URI.",
              javaType = "String")
    String EXEC_COMMAND_EXIT_VALUES = "CamelExecExitValues";

    /**
     * The value of this header is a {@link InputStream} with the standard error stream of the executable.
     */
    @Metadata(label = "out", description = "The value of this header points to the standard error stream (stderr) of\n" +
                                           "the executable. If no stderr is written, the value is `null`.",
              javaType = "java.io.InputStream")
    String EXEC_STDERR = "CamelExecStderr";

    /**
     * The value of this header is the exit value that is returned, after the execution. By convention a non-zero status
     * exit value indicates abnormal termination. <br>
     * <b>Note that the exit value is OS dependent.</b>
     */
    @Metadata(label = "out", description = "The value of this header is the _exit value_ of the executable. Non-zero\n" +
                                           "exit values typically indicate abnormal termination. Note that the exit\n" +
                                           "value is OS-dependent.",
              javaType = "int")
    String EXEC_EXIT_VALUE = "CamelExecExitValue";

    /**
     * The value of this header is a boolean which indicates whether to fallback and use stderr when stdout is empty.
     */
    @Metadata(label = "in", description = "Indicates that when `stdout` is empty, this component will populate the\n" +
                                          "Camel Message Body with `stderr`. This behavior is disabled (`false`) by\n" +
                                          "default.",
              javaType = "boolean")
    String EXEC_USE_STDERR_ON_EMPTY_STDOUT = "CamelExecUseStderrOnEmptyStdout";

    /**
     * The value of this header define logging level to be used for commands during execution. The default value is
     * INFO. Possible values are TRACE, DEBUG, INFO, WARN, ERROR or OFF. (Values of LoggingLevel enum)
     */
    @Metadata(label = "in",
              description = "Logging level to be used for commands during execution. The default value is DEBUG.\n" +
                            "Possible values are TRACE, DEBUG, INFO, WARN, ERROR or OFF (Values of LoggingLevel enum)",
              javaType = "String")
    String EXEC_COMMAND_LOG_LEVEL = "CamelExecCommandLogLevel";

    /**
     * Creates a {@link ExecCommand} from the headers in the <code>exchange</code> and the settings of the
     * <code>endpoint</code>.
     *
     * @param  exchange a Camel {@link Exchange}
     * @param  endpoint an {@link ExecEndpoint} instance
     * @return          an {@link ExecCommand} object
     * @see             ExecCommandExecutor
     */
    ExecCommand readInput(Exchange exchange, ExecEndpoint endpoint);

    /**
     * Populates the exchange form the {@link ExecResult}.
     *
     * @param exchange a Camel {@link Exchange}, in which to write the <code>result</code>
     * @param result   the result of a command execution
     * @see            ExecCommandExecutor
     */
    void writeOutput(Exchange exchange, ExecResult result);
}
