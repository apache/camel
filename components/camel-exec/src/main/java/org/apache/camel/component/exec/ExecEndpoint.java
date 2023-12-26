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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.exec.impl.DefaultExecBinding;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Execute commands on the underlying operating system.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "exec", title = "Exec", syntax = "exec:executable", producerOnly = true,
             remote = false, category = { Category.CORE }, headersClass = ExecBinding.class)
public class ExecEndpoint extends DefaultEndpoint {

    /**
     * Indicates that no {@link #timeout} is used.
     */
    public static final long NO_TIMEOUT = Long.MAX_VALUE;

    @UriPath
    @Metadata(required = true)
    private String executable;
    @UriParam
    private String args;
    @UriParam
    private String workingDir;
    @UriParam(javaType = "java.time.Duration")
    private long timeout;
    @UriParam
    private String exitValues;
    @UriParam
    private String outFile;
    @UriParam
    private ExecCommandExecutor commandExecutor;
    @UriParam
    private ExecBinding binding;
    @UriParam
    private boolean useStderrOnEmptyStdout;
    @UriParam(defaultValue = "DEBUG")
    private LoggingLevel commandLogLevel = LoggingLevel.DEBUG;

    public ExecEndpoint(String uri, ExecComponent component) {
        super(uri, component);
        this.timeout = NO_TIMEOUT;
        this.binding = new DefaultExecBinding();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ExecProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for ExecEndpoint!");
    }

    public String getExecutable() {
        return executable;
    }

    /**
     * Sets the executable to be executed. The executable must not be empty or <code>null</code>.
     */
    public void setExecutable(String executable) {
        StringHelper.notEmpty(executable, "executable");
        this.executable = executable;
    }

    public String getArgs() {
        return args;
    }

    /**
     * The arguments may be one or many whitespace-separated tokens.
     */
    public void setArgs(String args) {
        this.args = args;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    /**
     * The directory in which the command should be executed. If null, the working directory of the current process will
     * be used.
     */
    public void setWorkingDir(String dir) {
        this.workingDir = dir;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout, in milliseconds, after which the executable should be terminated. If execution has not completed
     * within the timeout, the component will send a termination request.
     */
    public void setTimeout(long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("The timeout must be a positive long!");
        }
        this.timeout = timeout;
    }

    public String getExitValues() {
        return exitValues;
    }

    /**
     * The exit values of successful executions. If the process exits with another value, an exception is raised.
     * Comma-separated list of exit values. And empty list (the default) sets no expected exit values and disables the
     * check.
     */
    public void setExitValues(String exitValues) {
        this.exitValues = exitValues;
    }

    public String getOutFile() {
        return outFile;
    }

    /**
     * The name of a file, created by the executable, that should be considered as its output. If no outFile is set, the
     * standard output (stdout) of the executable will be used instead.
     */
    public void setOutFile(String outFile) {
        StringHelper.notEmpty(outFile, "outFile");
        this.outFile = outFile;
    }

    public ExecCommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    /**
     * A reference to a org.apache.commons.exec.ExecCommandExecutor in the Registry that customizes the command
     * execution. The default command executor utilizes the commons-exec library, which adds a shutdown hook for every
     * executed command.
     */
    public void setCommandExecutor(ExecCommandExecutor commandExecutor) {
        ObjectHelper.notNull(commandExecutor, "commandExecutor");
        this.commandExecutor = commandExecutor;
    }

    public ExecBinding getBinding() {
        return binding;
    }

    /**
     * A reference to a org.apache.commons.exec.ExecBinding in the Registry.
     */
    public void setBinding(ExecBinding binding) {
        ObjectHelper.notNull(binding, "binding");
        this.binding = binding;
    }

    public boolean isUseStderrOnEmptyStdout() {
        return useStderrOnEmptyStdout;
    }

    /**
     * A boolean indicating that when stdout is empty, this component will populate the Camel Message Body with stderr.
     * This behavior is disabled (false) by default.
     */
    public void setUseStderrOnEmptyStdout(boolean useStderrOnEmptyStdout) {
        this.useStderrOnEmptyStdout = useStderrOnEmptyStdout;
    }

    public LoggingLevel getCommandLogLevel() {
        return commandLogLevel;
    }

    /**
     * Logging level to be used for commands during execution. The default value is DEBUG. Possible values are TRACE,
     * DEBUG, INFO, WARN, ERROR or OFF. (Values of ExecCommandLogLevelType enum)
     */
    public void setCommandLogLevel(LoggingLevel commandLogLevel) {
        this.commandLogLevel = commandLogLevel;
    }
}
