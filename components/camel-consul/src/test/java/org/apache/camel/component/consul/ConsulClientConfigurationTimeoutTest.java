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
package org.apache.camel.component.consul;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The readTimeout and writeTimeout options must be applied to the Consul client. Without them the HTTP client waits 10
 * seconds (its default), so a request to an agent that does not answer must fail well before that.
 */
class ConsulClientConfigurationTimeoutTest {

    private static final Duration TIMEOUT = Duration.ofMillis(200);
    private static final Duration MAX_WAIT = Duration.ofSeconds(5);

    @Test
    void readTimeout() throws Exception {
        // the connection is accepted by the operating system (backlog) but nothing is ever answered
        try (ServerSocket agent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ConsulConfiguration configuration = configuration(agent);
            configuration.setReadTimeout(TIMEOUT);

            assertTimesOut(configuration, consul -> consul.keyValueClient().getValueAsString("camel/key"));
        }
    }

    @Test
    void writeTimeout() throws Exception {
        // nothing is ever read: writing a value larger than the socket buffers blocks
        try (ServerSocket agent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ConsulConfiguration configuration = configuration(agent);
            configuration.setWriteTimeout(TIMEOUT);

            String value = "x".repeat(32 * 1024 * 1024);
            assertTimesOut(configuration, consul -> consul.keyValueClient().putValue("camel/key", value));
        }
    }

    private static ConsulConfiguration configuration(ServerSocket agent) {
        ConsulConfiguration configuration = new ConsulConfiguration();
        configuration.setUrl("http://localhost:" + agent.getLocalPort());
        configuration.setPingInstance(false);
        return configuration;
    }

    private static void assertTimesOut(ConsulConfiguration configuration, ConsulCall call) throws Exception {
        Consul consul = configuration.createConsulClient();
        try {
            Executable request = () -> call.execute(consul);
            ConsulException e = assertTimeoutPreemptively(MAX_WAIT, () -> assertThrows(ConsulException.class, request));
            assertInstanceOf(SocketTimeoutException.class, e.getCause());
        } finally {
            consul.destroy();
        }
    }

    @FunctionalInterface
    private interface ConsulCall {
        void execute(Consul consul);
    }
}
