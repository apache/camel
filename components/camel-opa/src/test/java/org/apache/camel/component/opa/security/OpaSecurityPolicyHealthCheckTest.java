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
package org.apache.camel.component.opa.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.styra.opa.OPAClient;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The security policy hard-fails every exchange when OPA is unreachable, so it needs the same readiness signal the
 * producer got.
 */
public class OpaSecurityPolicyHealthCheckTest extends CamelTestSupport {

    private static final String TOKEN = "s3cr3t-token";

    private static HttpServer server;
    private static String serverUrl;

    private final OpaSecurityPolicy policy = new OpaSecurityPolicy();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static String startHealthyServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            try (OutputStream out = exchange.getResponseBody()) {
                out.flush();
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        serverUrl = startHealthyServer();
        policy.setPolicyPath("authz/allow");
        policy.setServerUrl(serverUrl);
        policy.setBearerToken(TOKEN);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").policy(policy).to("mock:result");
            }
        };
    }

    private List<HealthCheck> registered() {
        HealthCheckRegistry registry = HealthCheckRegistry.get(context);
        assertThat(registry).isNotNull();
        return registry.stream()
                .filter(hc -> hc.getId().startsWith("security-policy:opa-"))
                .toList();
    }

    @Test
    void registersAReadinessCheckForTheServerItEnforces() {
        assertThat(registered()).hasSize(1);
        HealthCheck check = registered().get(0);

        HealthCheck.Result result = check.call(Map.of());
        assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(result.getDetails()).containsEntry("opa.policyPath", "authz/allow");
    }

    @Test
    void neverPublishesTheBearerTokenInTheCheckId() {
        // the id reaches the health output, and serverUrl/policyPath are enough to identify the decision
        assertThat(registered().get(0).getId()).doesNotContain(TOKEN);
    }

    @Test
    void toleratesATrailingSlashOnTheServerUrl() {
        // concatenating "/health" onto a base that already ends in one builds //health. OPA's router answers a
        // non-canonical path with a redirect, and the probe's client does not follow redirects, so a healthy
        // server was reported DOWN. Every other test here uses a slash-free URL, which is how it went unnoticed.
        OpaSecurityPolicyHealthCheck check
                = new OpaSecurityPolicyHealthCheck(serverUrl + "/", null, "authz/allow", null);
        check.setEnabled(true);

        HealthCheck.Result result = check.call(Map.of());

        assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
    }

    @Test
    void reportsDownWhenTheServerCannotBeReached() {
        OpaSecurityPolicyHealthCheck check
                = new OpaSecurityPolicyHealthCheck("http://localhost:1", null, "authz/allow", null);
        check.setEnabled(true);

        HealthCheck.Result result = check.call(Map.of());

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage()).get().asString().contains("Cannot reach the OPA server");
    }

    @Test
    void registersNothingWhenTheCheckIsDisabled() {
        OpaSecurityPolicy disabled = new OpaSecurityPolicy();
        disabled.setPolicyPath("authz/allow");
        disabled.setServerUrl("http://unused:8181");
        disabled.setHealthCheckEnabled(false);

        disabled.beforeWrap(context.getRoutes().get(0), null);

        // still only the one the route under test registered
        assertThat(registered()).hasSize(1);
    }

    @Test
    void registersNothingForAPolicyThatFailsValidation() {
        // registration used to run before notEmpty(policyPath), which left a ".../null" check in the registry
        // of a policy whose route then never started
        OpaSecurityPolicy misconfigured = new OpaSecurityPolicy();
        misconfigured.setServerUrl("http://unused:8181");

        assertThatThrownBy(() -> misconfigured.beforeWrap(context.getRoutes().get(0), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyPath");

        assertThat(registered()).hasSize(1);
        assertThat(registered().get(0).getId()).doesNotContain("null");
    }

    @Test
    void registersNothingWhenAnOpaClientWasInjected() {
        OpaSecurityPolicy injected = new OpaSecurityPolicy();
        injected.setPolicyPath("authz/allow");
        injected.setServerUrl("http://unused:8181");
        injected.setOpaClient(mock(OPAClient.class));

        injected.beforeWrap(context.getRoutes().get(0), null);

        // an injected client may point anywhere, so probing serverUrl would report on the wrong server
        assertThat(registered()).hasSize(1);
    }
}
