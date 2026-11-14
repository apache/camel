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
package org.apache.camel.dsl.jbang.core.commands.exceptionhandler;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CliExecutionExceptionHandlerTest {

    private final CliExecutionExceptionHandler handler = new CliExecutionExceptionHandler(new CamelJBangMain());

    @Test
    void timeoutExceptionIsExit124() {
        assertThat(handler.getExitCode(new TimeoutException("timed out")))
                .isEqualTo(CliExitCode.TIMEOUT);
    }

    @Test
    void timeoutExceptionWrappedInIoExceptionIsExit124() {
        IOException wrapped = new IOException("GitHub API request timed out", new TimeoutException("timed out"));
        assertThat(handler.getExitCode(wrapped))
                .isEqualTo(CliExitCode.TIMEOUT);
    }

    @Test
    void httpTimeoutExceptionIsExit124() {
        assertThat(handler.getExitCode(new HttpTimeoutException("connect timed out")))
                .isEqualTo(CliExitCode.TIMEOUT);
    }

    @Test
    void httpTimeoutExceptionWrappedInIoExceptionIsExit124() {
        IOException wrapped = new IOException("request failed", new HttpTimeoutException("read timed out"));
        assertThat(handler.getExitCode(wrapped))
                .isEqualTo(CliExitCode.TIMEOUT);
    }

    @Test
    void genericIoExceptionIsInternal() {
        assertThat(handler.getExitCode(new IOException("unexpected error")))
                .isEqualTo(CliExitCode.INTERNAL);
    }
}
