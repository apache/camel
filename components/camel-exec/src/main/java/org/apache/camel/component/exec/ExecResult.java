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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Represents the result of the execution of an {@link ExecCommand}
 */
public class ExecResult implements Serializable {

    private static final long serialVersionUID = -2238558080056724637L;

    private final ExecCommand command;

    private int exitValue;

    private InputStream stdout;

    private InputStream stderr;

    public ExecResult(ExecCommand command) {
        this.command = command;
        this.stdout = new ByteArrayInputStream(new byte[0]);
        this.stderr = new ByteArrayInputStream(new byte[0]);
    }

    /**
     * @return The command, that produced this result
     */
    public ExecCommand getCommand() {
        return command;
    }

    /**
     * @return The exit value of the command executable
     */
    public int getExitValue() {
        return exitValue;
    }

    public void setExitValue(int exitValue) {
        this.exitValue = exitValue;
    }

    /**
     * @return The stdout of the command executable
     */
    public InputStream getStdout() {
        return stdout;
    }

    public void setStdout(InputStream stdout) {
        this.stdout = stdout;
    }

    /**
     * @return The stderr of the command executable
     */
    public InputStream getStderr() {
        return stderr;
    }

    public void setStderr(InputStream stderr) {
        this.stderr = stderr;
    }

}
