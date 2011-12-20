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

import java.io.InputStream;
import java.io.Serializable;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Value object, that represents the result of an {@link ExecCommand} execution.
 */
public class ExecResult implements Serializable {

    private static final long serialVersionUID = -2238558080056724637L;

    private final ExecCommand command;

    private final int exitValue;

    private final InputStream stdout;

    private final InputStream stderr;

    /**
     * Creates a <code>ExecResult</code> instance.
     * 
     * @param command A not-null reference of {@link ExecCommand}, that produced
     *            the result.
     * @param stdout InputStream with the stdout of the command executable. If
     *            there was no stdout, the value must be <code>null</code>.
     * @param stderr InputStream with the stderr of the command executable. If
     *            there was no stderr, the value must be <code>null</code>.
     * @param exitValue the exit value of the command executable.
     */
    public ExecResult(ExecCommand command, InputStream stdout, InputStream stderr, int exitValue) {
        notNull(command, "command");
        this.command = command;

        this.stdout = stdout;
        this.stderr = stderr;
        this.exitValue = exitValue;
    }

    /**
     * The executed command, that produced this result. The returned object is
     * never <code>null</code>.
     * 
     * @return The executed command, that produced this result.
     */
    public ExecCommand getCommand() {
        return command;
    }

    /**
     * The exit value of the command executable.
     * 
     * @return The exit value of the command executable
     */
    public int getExitValue() {
        return exitValue;
    }

    /**
     * Returns the content of the standart output (stdout) of the executed
     * command or <code>null</code>, if no output was produced in the stdout.
     * 
     * @return The standart output (stdout) of the command executable.
     */
    public InputStream getStdout() {
        return stdout;
    }

    /**
     * Returns the content of the standart error output (stderr) of the executed
     * command or <code>null</code>, if no output was produced in the stderr.
     * 
     * @return The standart error output (stderr) of the command executable.
     */
    public InputStream getStderr() {
        return stderr;
    }
}
