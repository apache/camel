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

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Value object that describes the command to be executed.
 */
public class ExecCommand implements Serializable {

    private static final long serialVersionUID = 1755094616849607573L;

    /**
     * @see ExecBinding#EXEC_COMMAND_EXECUTABLE
     */
    private final String executable;

    /**
     * @see ExecBinding#EXEC_COMMAND_ARGS
     */
    private final List<String> args;

    /**
     * @see ExecBinding#EXEC_COMMAND_WORKING_DIR
     */
    private final String workingDir;

    /**
     * @see ExecBinding#EXEC_COMMAND_TIMEOUT
     */
    private final long timeout;

    /**
     * @see ExecBinding#EXEC_COMMAND_OUT_FILE
     */
    private final File outFile;

    /**
     * The input of the executable
     */
    private final InputStream input;

    private final boolean useStderrOnEmptyStdout;

    public ExecCommand(String executable, List<String> args, String workingDir, Long timeout,
                       InputStream input, File outFile, boolean useStderrOnEmptyStdout) {
        notNull(executable, "command executable");
        this.executable = executable;
        this.args = unmodifiableOrEmptyList(args);
        this.workingDir = workingDir;
        this.timeout = timeout;
        this.input = input;
        this.outFile = outFile;
        this.useStderrOnEmptyStdout = useStderrOnEmptyStdout;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getExecutable() {
        return executable;
    }

    public InputStream getInput() {
        return input;
    }

    public File getOutFile() {
        return outFile;
    }

    public long getTimeout() {
        return timeout;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public boolean isUseStderrOnEmptyStdout() {
        return useStderrOnEmptyStdout;
    }

    @Override
    public String toString() {
        String dirToPrint = workingDir == null ? "null" : workingDir;
        String outFileToPrint = outFile == null ? "null" : outFile.getPath();
        return "ExecCommand [args=" + args + ", executable=" + executable + ", timeout=" + timeout + ", outFile="
                + outFileToPrint + ", workingDir=" + dirToPrint + ", useStderrOnEmptyStdout=" + useStderrOnEmptyStdout + "]";
    }

    private <T> List<T> unmodifiableOrEmptyList(List<T> list) {
        return Collections.unmodifiableList(list == null ? new ArrayList<T>() : list);
    }

}
