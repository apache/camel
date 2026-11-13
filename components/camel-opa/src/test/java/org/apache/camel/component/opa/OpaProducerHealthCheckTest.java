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
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
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
        OpaProducerHealthCheck check = new OpaProducerHealthCheck(serverUrl, null, "authz/allow", "authz/allow", null);
        check.setEnabled(true);
        return check.call(Map.of());
    }

    @Test
    void neverPublishesTheBearerTokenInTheHealthCheckId() {
        // the id is derived from the endpoint URI, which carries the token in the clear; the id reaches the
        // health output, so the token must not survive into it
        OpaProducerHealthCheck check = new OpaProducerHealthCheck(
                "http://localhost:8181", "s3cr3t-token", "authz/allow",
                "opa://authz/allow?bearerToken=s3cr3t-token&serverUrl=http://localhost:8181", null);

        assertThat(check.getId()).doesNotContain("s3cr3t-token");
        assertThat(check.getId()).contains("serverUrl=http://localhost:8181");
    }

    @Test
    void givesEndpointsOnDifferentServersDistinctIds() {
        OpaProducerHealthCheck primary = new OpaProducerHealthCheck(
                "http://opa-primary:8181", null, "authz/allow",
                "opa://authz/allow?serverUrl=http://opa-primary:8181", null);
        OpaProducerHealthCheck secondary = new OpaProducerHealthCheck(
                "http://opa-secondary:8181", null, "authz/allow",
                "opa://authz/allow?serverUrl=http://opa-secondary:8181", null);

        assertThat(primary.getId()).isNotEqualTo(secondary.getId());
        assertThat(primary).isNotEqualTo(secondary);
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

    @Test
    void includesBundleActivationOnlyWhenEnabled() throws Exception {
        AtomicBoolean activated = new AtomicBoolean();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = "bundles=true".equals(query.get()) && !activated.get() ? 500 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        String serverUrl = "http://localhost:" + server.getAddress().getPort();

        assertThat(call(serverUrl).getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(query.get()).isNull();

        OpaProducerHealthCheck bundleCheck = new OpaProducerHealthCheck(
                serverUrl + "/", "test-token", "authz/allow", "bundles", null, true);
        bundleCheck.setEnabled(true);
        HealthCheck.Result notActivated = bundleCheck.call(Map.of());

        assertThat(notActivated.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(notActivated.getDetails()).containsEntry("opa.healthCheckBundles", true);
        assertThat(query.get()).isEqualTo("bundles=true");
        assertThat(authorization.get()).isEqualTo("Bearer test-token");

        activated.set(true);
        assertThat(bundleCheck.call(Map.of()).getState()).isEqualTo(HealthCheck.State.UP);
    }

    @Test
    void preservesTlsConfigurationWhenCheckingBundles() throws Exception {
        KeyStore fixture = KeyStore.getInstance("PKCS12");
        try (var in = getClass().getResourceAsStream("/opa-server.p12")) {
            fixture.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(fixture, "changeit".toCharArray());
        SSLContext serverTls = SSLContext.getInstance("TLS");
        serverTls.init(keys.getKeyManagers(), null, null);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(fixture);
        SSLContext clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, trust.getTrustManagers(), null);
        HttpsServer https = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        server = https;
        AtomicReference<String> query = new AtomicReference<>();
        https.createContext("/health", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        https.start();
        OpaProducerHealthCheck check = new OpaProducerHealthCheck(
                "https://localhost:" + https.getAddress().getPort(), null, "authz/allow", "tls-bundles", clientTls, true);
        check.setEnabled(true);

        assertThat(check.call(Map.of()).getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(query.get()).isEqualTo("bundles=true");
    }

}
