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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.exec.impl.DefaultExecBinding;
import org.apache.camel.component.exec.impl.DefaultExecCommandExecutor;
import org.apache.camel.component.exec.impl.ExecParseUtils;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The endpoint utilizes an {@link ExecCommandExecutor} to execute a system
 * command when it receives message exchanges.
 * 
 * @see Endpoint
 * @see ExecBinding
 * @see ExecCommandExecutor
 * @see ExecCommand
 * @see ExecResult
 */
public class ExecEndpoint extends DefaultEndpoint {

    /**
     * Indicates that no {@link #timeout} is used.
     */
    public static final long NO_TIMEOUT = Long.MAX_VALUE;

    private String executable;

    private String args;

    private String workingDir;

    private long timeout;

    private String outFile;

    private ExecCommandExecutor commandExecutor;

    private ExecBinding binding;

    public ExecEndpoint(String uri, ExecComponent component) {
        super(uri, component);
        this.timeout = NO_TIMEOUT;
        this.binding = new DefaultExecBinding();
        this.commandExecutor = new DefaultExecCommandExecutor();
    }

    public Producer createProducer() throws Exception {
        return new ExecProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for ExecEndpoint!");
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * @return the executable to be executed; that is the remaining part of the
     *         endpoint URI
     * @see ExecBinding#EXEC_COMMAND_EXECUTABLE
     */
    public String getExecutable() {
        return executable;
    }

    /**
     * Sets the executable to be executed. The executable must not be empty or
     * <code>null</code>.
     * 
     * @param executable Sets the executable to be executed.
     */
    public void setExecutable(String executable) {
        ObjectHelper.notEmpty(executable, "executable");
        this.executable = executable;
    }

    /**
     * The arguments may be one or many whitespace-separated tokens, that can be
     * quoted with ", e.g. <code>args="arg 1" arg2"</code> will use two arguments
     * <code>arg 1</code> and <code>arg2</code>. To include the quotes use
     * <code>""</code><br>
     * , e.g. <code>args=""arg 1"" arg2</code> will use the arguments
     * <code>"arg 1"</code> and <code>arg2</code>.
     * 
     * @return the arguments of the executable application, as configured from
     *         the endpoint URI.
     * @see ExecBinding#EXEC_COMMAND_ARGS
     * @see ExecParseUtils#splitToWhiteSpaceSeparatedTokens(String)
     */
    public String getArgs() {
        return args;
    }

    /**
     * Sets the arguments of the executable application
     * 
     * @param args Returns <code>null</code> value if no arguments are
     *            configured in the endpoint URI
     * @see #getArgs()
     * @see ExecBinding#EXEC_COMMAND_ARGS
     */
    public void setArgs(String args) {
        this.args = args;
    }

    /**
     * @return the working directory of the executable, or <code>null</code> is
     *         such is not set.
     * @see ExecBinding#EXEC_COMMAND_WORKING_DIR
     */
    public String getWorkingDir() {
        return workingDir;
    }

    /**
     * Sets the working directory of the executable.
     * 
     * @param dir the working directory of the executable. <code>null</code>
     *            values indicates that the current working directory will be
     *            used.
     */
    public void setWorkingDir(String dir) {
        this.workingDir = dir;
    }

    /**
     * @return The returned value is always a positive <code>long</code>. The
     *         default value is {@link ExecEndpoint#NO_TIMEOUT}
     * @see ExecBinding#EXEC_COMMAND_TIMEOUT
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout.
     * 
     * @param timeout The <code>timeout</code> must be a positive long
     * @see ExecBinding#EXEC_COMMAND_TIMEOUT
     */
    public void setTimeout(long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("The timeout must be a positive long!");
        }
        this.timeout = timeout;
    }

    /**
     * @return <code>null</code> if no out file is set, otherwise returns the
     *         value of the outFile
     * @see ExecBinding#EXEC_COMMAND_OUT_FILE
     */
    public String getOutFile() {
        return outFile;
    }

    /**
     * @param outFile a not-empty file path
     * @see ExecBinding#EXEC_COMMAND_OUT_FILE
     */
    public void setOutFile(String outFile) {
        ObjectHelper.notEmpty(outFile, "outFile");
        this.outFile = outFile;
    }

    /**
     * @return The command executor used to execute commands. Defaults to
     *         {@link DefaultExecCommandExecutror}
     */
    public ExecCommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    /**
     * Sets a custom executor to execute commands.
     * 
     * @param commandExecutor a not-null instance of {@link ExecCommandExecutor}
     */
    public void setCommandExecutor(ExecCommandExecutor commandExecutor) {
        ObjectHelper.notNull(commandExecutor, "commandExecutor");
        this.commandExecutor = commandExecutor;
    }

    public ExecBinding getBinding() {
        return binding;
    }

    public void setBinding(ExecBinding binding) {
        ObjectHelper.notNull(binding, "binding");
        this.binding = binding;
    }
}
