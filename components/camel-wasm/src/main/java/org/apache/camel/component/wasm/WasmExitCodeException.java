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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;

/**
 * Thrown when a module exits with a non-zero exit code and the endpoint fails on that (failOnNonZeroExit, the default).
 * The message body is left as it was before the call.
 */
public class WasmExitCodeException extends CamelExchangeException {

    private final int exitCode;
    private final String stderr;

    public WasmExitCodeException(String message, int exitCode, String stderr, Exchange exchange) {
        super(message, exchange);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    /**
     * What the module wrote to its standard error, decoded as UTF-8, or an empty string.
     */
    public String getStderr() {
        return stderr;
    }
}
