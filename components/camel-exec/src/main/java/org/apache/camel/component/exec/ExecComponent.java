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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.exec.impl.DefaultExecBinding;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import static org.apache.camel.component.exec.ExecEndpoint.NO_TIMEOUT;

/**
 * Represents the component that manages {@link ExecEndpoint}. With the component it is possible to execute system
 * commands.
 */
@Component("exec")
public class ExecComponent extends DefaultComponent {

    @Metadata
    private String workingDir;
    @Metadata(label = "advanced", security = "insecure:dev")
    private boolean allowControlHeaders;
    @Metadata(label = "advanced")
    private ExecCommandExecutor commandExecutor;
    @Metadata(label = "advanced")
    private ExecBinding binding = new DefaultExecBinding();
    @Metadata
    private long timeout = NO_TIMEOUT;

    public ExecComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ExecEndpoint endpoint = new ExecEndpoint(uri, this);
        endpoint.setWorkingDir(workingDir);
        endpoint.setAllowControlHeaders(allowControlHeaders);
        endpoint.setCommandExecutor(commandExecutor);
        endpoint.setBinding(binding);
        endpoint.setTimeout(timeout);
        setProperties(endpoint, parameters);
        endpoint.setExecutable(URLDecoder.decode(remaining, StandardCharsets.UTF_8));
        return endpoint;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    /**
     * The directory in which the command should be executed. If null, the working directory of the current process will
     * be used.
     */
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public ExecCommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    /**
     * To use a custom org.apache.commons.exec.ExecCommandExecutor that customizes the command execution. The default
     * command executor utilizes the commons-exec library, which adds a shutdown hook for every executed command.
     */
    public void setCommandExecutor(ExecCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public ExecBinding getBinding() {
        return binding;
    }

    /**
     * To use a custom org.apache.commons.exec.ExecBinding for advanced use-cases.
     */
    public void setBinding(ExecBinding binding) {
        this.binding = binding;
    }

    public boolean isAllowControlHeaders() {
        return allowControlHeaders;
    }

    /**
     * Whether {@code CamelExec*} in-headers may override URI options (default {@code false} since Camel 4.20). When
     * {@code false}, {@code CamelExecCommandExecutable}, {@code CamelExecCommandArgs}, {@code CamelExecCommandOutFile},
     * {@code CamelExecCommandWorkingDir}, {@code CamelExecCommandTimeout}, {@code CamelExecExitValues},
     * {@code CamelExecUseStderrOnEmptyStdout}, and {@code CamelExecCommandLogLevel} are ignored. Enable only when those
     * headers come from a trusted route, not from an untrusted consumer.
     */
    public void setAllowControlHeaders(boolean allowControlHeaders) {
        this.allowControlHeaders = allowControlHeaders;
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

}
