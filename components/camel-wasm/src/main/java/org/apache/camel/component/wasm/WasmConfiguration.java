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
package org.apache.camel.component.wasm;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class WasmConfiguration implements Cloneable {

    @UriParam
    private String args;
    @UriParam(prefix = "environment.", multiValue = true)
    private Map<String, Object> environment;
    @UriParam
    private String environmentHeaders;
    @UriParam(defaultValue = "true")
    private boolean failOnNonZeroExit = true;
    @UriParam
    private int maxMemoryPages;
    @UriParam
    private int maxOutputSize;
    @UriParam(javaType = "java.time.Duration")
    private long timeout;

    public String getArgs() {
        return args;
    }

    /**
     * The command line arguments passed to the module, separated by whitespace. Use double quotes around an argument
     * that contains whitespace. The module receives the module location as argument zero, followed by these arguments.
     * The arguments are fixed by the route: they cannot be set from message headers.
     */
    public void setArgs(String args) {
        this.args = args;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    /**
     * Environment variables for the module, for example environment.LOG_LEVEL=debug. The module sees no environment
     * variables other than these and the headers listed in environmentHeaders; the environment of the JVM is never
     * passed on.
     */
    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment;
    }

    public String getEnvironmentHeaders() {
        return environmentHeaders;
    }

    /**
     * A comma separated allow-list of message headers whose values are passed to the module as environment variables of
     * the same name. Headers usually come from whoever sent the message, so no header reaches the module unless it is
     * listed here. A listed header that is not on the message is left unset. A name may not be listed here and also be
     * configured with environment.
     */
    public void setEnvironmentHeaders(String environmentHeaders) {
        this.environmentHeaders = environmentHeaders;
    }

    public boolean isFailOnNonZeroExit() {
        return failOnNonZeroExit;
    }

    /**
     * Whether a module that exits with a non-zero exit code fails the exchange, with a WasmExitCodeException, leaving
     * the message body unchanged. When false, the standard output of the module replaces the message body whatever the
     * exit code, which is available in the CamelWasmExitCode header.
     */
    public void setFailOnNonZeroExit(boolean failOnNonZeroExit) {
        this.failOnNonZeroExit = failOnNonZeroExit;
    }

    public int getMaxMemoryPages() {
        return maxMemoryPages;
    }

    /**
     * The maximum size of the memory of the module, in WebAssembly pages of 64 KiB. Rust and Go programs declare no
     * maximum by default. When the limit is reached, growing the memory fails inside the module, which then usually
     * traps or exits with an error. 0 (the default) keeps the maximum declared by the module. The value must not be
     * lower than the initial size the module declares.
     */
    public void setMaxMemoryPages(int maxMemoryPages) {
        this.maxMemoryPages = maxMemoryPages;
    }

    public int getMaxOutputSize() {
        return maxOutputSize;
    }

    /**
     * The maximum number of bytes the module may write to its standard output, and separately to its standard error,
     * which are buffered in memory. A module that writes more is stopped and the exchange fails. 0 (the default) means
     * no limit.
     */
    public void setMaxOutputSize(int maxOutputSize) {
        this.maxOutputSize = maxOutputSize;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The maximum time the module may run for one exchange, in milliseconds. When it is exceeded the module is
     * interrupted and the exchange fails with an ExchangeTimedOutException. When a timeout is set, the module runs on a
     * separate thread while the calling thread waits. 0 (the default) means no timeout.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public WasmConfiguration copy() {
        try {
            WasmConfiguration answer = (WasmConfiguration) super.clone();
            if (environment != null) {
                // each endpoint gets its own map
                answer.environment = new LinkedHashMap<>(environment);
            }
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
