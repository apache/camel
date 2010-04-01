/**
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

import org.apache.camel.Exchange;
import org.apache.camel.component.exec.impl.ExecParseUtils;

/**
 * Represents the binding of input and output types of a
 * {@link ExecCommandExecutor} to an {@link Exchange}. The input of the executor
 * is an {@link ExecCommand} and the output is an {@link ExecResult}.
 */
public interface ExecBinding {
    /**
     * The header value overrides the executable of the command, configured in
     * the exec endpoint URI. As executable is considered the remaining of the
     * {@link ExecEndpoint} URI; <br>
     * <br>
     * e.g. in the URI <i> <code>exec:"C:/Program Files/jdk/java.exe"</code>
     * </i>, <code>"C:/Program Files/jdk/java.exe"<code> is the executable, escaped by the " character.
     */
    String EXEC_COMMAND_EXECUTABLE = "CamelExecCommandExecutable";
    /**
     * The header value overrides the existing command arguments in the
     * {@link ExecEndpoint} URI. The arguments may be one or many
     * whitespace-separated tokens, and can be quoted with <code>"</code>, or with
     * <code>""</code>.
     * 
     * @see {@link #EXEC_COMMAND_EXECUTABLE}
     * @see ExecParseUtils#splitToWhiteSpaceSeparatedTokens(String)
     */
    String EXEC_COMMAND_ARGS = "CamelExecCommandArgs";

    /**
     * Specifies the file name of a file, created by the executable, that should
     * be considered as output of the executable, e.g. a log file.
     * 
     * @see ExecResultConverter#toInputStream(ExecResult)
     */
    String EXEC_COMMAND_OUT_FILE = "CamelExecCommandOutFile";

    /**
     * Sets the working directory of the {@link #EXEC_COMMAND_EXECUTABLE}. The
     * header value overrides any existing command in the endpoint URI. If this
     * is not configured, the working directory of the current process will be
     * used.
     */
    String EXEC_COMMAND_WORKING_DIR = "CamelExecCommandWorkingDir";
    /**
     * Specifies the amount of time, in milliseconds, after which the process of
     * the executable should be terminated.
     */
    String EXEC_COMMAND_TIMEOUT = "CamelExecCommandTimeout";

    /**
     * The value of this header is a String with the standard output stream of
     * the executable.
     */
    String EXEC_STDOUT = "CamelExecStdout";
    /**
     * The value of this header is a String with the standard error stream of
     * the executable.
     */
    String EXEC_STDERR = "CamelExecStderr";

    /**
     * The value of this header is the exit value that is returned, after the
     * execution. By convention a non-zero status exit value indicates abnormal
     * termination. <br>
     * <b>Note that the exit value is OS dependent.</b>
     */
    String EXEC_EXIT_VALUE = "CamelExecExitValue";

    /**
     * Creates a {@link ExecCommand} from the headers in the
     * <code>exchange</code> and the settings of the <code>endpoint</code>.
     * 
     * @param exchange a Camel {@link Exchange}
     * @param endpoint an {@link ExecEndpoint} instance
     * @return an {@link ExecCommand} object
     * @see ExecCommandExecutor
     */
    ExecCommand readInput(Exchange exchange, ExecEndpoint endpoint);

    /**
     * Populates the exchange form the {@link ExecResult}.
     * 
     * @param exchange a Camel {@link Exchange}, in which to write the
     *            <code>result</code>
     * @param result the result of a command execution
     * @see ExecCommandExecutor
     */
    void writeOutput(Exchange exchange, ExecResult result);
}
