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
package org.apache.camel.component.opa;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.health.HealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaProducerHealthCheckTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            try (OutputStream out = exchange.getResponseBody()) {
                out.flush();
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static HealthCheck.Result call(String serverUrl) {
        OpaProducerHealthCheck check = new OpaProducerHealthCheck(serverUrl, null, "authz/allow", "authz/allow");
        check.setEnabled(true);
        return check.call(Map.of());
    }

    @Test
    void isUpWhenTheServerIsHealthy() throws Exception {
        HealthCheck.Result result = call(startServer(200));

        assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(result.getDetails()).containsKey("opa.policyPath");
    }

    @Test
    void isDownWhenTheServerReportsUnhealthy() throws Exception {
        HealthCheck.Result result = call(startServer(500));

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage()).get().asString().contains("500");
        assertThat(result.getDetails()).containsEntry("opa.statusCode", 500);
    }

    @Test
    void isDownWhenTheServerCannotBeReached() {
        HealthCheck.Result result = call("http://localhost:1");

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage()).get().asString().contains("Cannot reach the OPA server");
        assertThat(result.getError()).isPresent();
    }
}
