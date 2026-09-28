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
package org.apache.camel.component.openfga;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.health.HealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFgaProducerHealthCheckTest {

    private static final String STORE = "01HQMVAJXYZ0000000000000";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/healthz", exchange -> {
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static HealthCheck.Result call(String apiUrl) {
        OpenFgaProducerHealthCheck check = new OpenFgaProducerHealthCheck(apiUrl, null, STORE, apiUrl, null);
        check.setEnabled(true);
        return check.call(Map.of());
    }

    @Test
    void neverPublishesTheApiTokenInTheHealthCheckId() {
        // the id is derived from the endpoint URI, which carries the token in the clear; the id reaches the
        // health output, so the token must not survive into it
        OpenFgaProducerHealthCheck check = new OpenFgaProducerHealthCheck(
                "http://localhost:8080", "s3cr3t-token", STORE,
                "openfga://check?apiToken=s3cr3t-token&storeId=" + STORE, null);

        assertThat(check.getId()).doesNotContain("s3cr3t-token");
        assertThat(check.getId()).contains("storeId=" + STORE);
    }

    @Test
    void givesEndpointsOnDifferentServersDistinctIds() {
        OpenFgaProducerHealthCheck primary = new OpenFgaProducerHealthCheck(
                "http://fga-primary:8080", null, STORE, "openfga://check?apiUrl=http://fga-primary:8080", null);
        OpenFgaProducerHealthCheck secondary = new OpenFgaProducerHealthCheck(
                "http://fga-secondary:8080", null, STORE, "openfga://check?apiUrl=http://fga-secondary:8080", null);

        assertThat(primary.getId()).isNotEqualTo(secondary.getId());
        assertThat(primary).isNotEqualTo(secondary);
    }

    @Test
    void isUpWhenTheServerReportsThatItIsServing() throws Exception {
        HealthCheck.Result result = call(startServer(200, "{\"status\":\"SERVING\"}"));

        assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(result.getDetails()).containsEntry("openfga.storeId", STORE);
    }

    @Test
    void isDownWhenTheServerAnswersOkButIsNotServing() throws Exception {
        // the HTTP gateway can answer 200 while the health service behind it reports something else, so the body is
        // what decides - taking the status alone would report such a server ready
        HealthCheck.Result result = call(startServer(200, "{\"status\":\"NOT_SERVING\"}"));

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage().orElse("")).contains("not serving");
    }

    @Test
    void isDownWhenTheServerAnswersWithAnError() throws Exception {
        HealthCheck.Result result = call(startServer(503, "unavailable"));

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getDetails()).containsEntry("openfga.statusCode", 503);
    }

    @Test
    void isDownWhenTheServerCannotBeReached() {
        // port 1 is reserved and nothing listens on it
        HealthCheck.Result result = call("http://localhost:1");

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage().orElse("")).contains("Cannot reach the OpenFGA server");
    }

    @Test
    void doesNotBuildADoubleSlashFromAnApiUrlWithATrailingSlash() throws Exception {
        // //healthz is answered with a redirect the probe does not follow, which would report a healthy server DOWN
        HealthCheck.Result result = call(startServer(200, "{\"status\":\"SERVING\"}") + "/");

        assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
    }
}
