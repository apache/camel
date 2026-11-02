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
package org.apache.camel.component.openfga.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The readiness check registered by {@link OpenFgaSecurityPolicy} must follow the lifecycle of the routes it guards: it
 * is kept while any guarded route runs, removed once the last one stops, and restored when a route starts again.
 * Otherwise a stopped or reloaded route leaves a check behind reporting on a policy that is no longer enforcing
 * anything (CAMEL-24751).
 */
class OpenFgaSecurityPolicyHealthCheckLifecycleTest extends CamelTestSupport {

    // a syntactically valid store id, so the policy can build its own OpenFgaClient without an injected one - which is
    // what makes it own the client, and so register a check (an injected client registers none)
    private static final String STORE = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    private static HttpServer server;
    private static String apiUrl;

    private final OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static String startHealthyServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/healthz", exchange -> {
            byte[] body = "{\"status\":\"SERVING\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        apiUrl = startHealthyServer();
        policy.setApiUrl(apiUrl);
        policy.setStoreId(STORE);
        policy.setRelation("reader");
        policy.setUser("user:anne");
        policy.setObject("document:budget");
        // two routes share the same policy instance, so the check is registered once and its removal is ref-counted
        // against the routes still running
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:one").routeId("guarded1").policy(policy).to("mock:result");
                from("direct:two").routeId("guarded2").policy(policy).to("mock:result");
            }
        };
    }

    private long registeredChecks() {
        HealthCheckRegistry registry = HealthCheckRegistry.get(context);
        assertThat(registry).isNotNull();
        return registry.stream()
                .filter(hc -> hc.getId().startsWith("security-policy:openfga-"))
                .count();
    }

    @Test
    void keepsTheCheckWhileAnyGuardedRouteRunsAndRestoresItOnRestart() throws Exception {
        assertThat(registeredChecks()).isEqualTo(1);

        // stopping one of the two routes must not remove the check - the other still enforces the policy
        context.getRouteController().stopRoute("guarded1");
        assertThat(registeredChecks()).isEqualTo(1);

        // once the last guarded route stops, the check is gone
        context.getRouteController().stopRoute("guarded2");
        assertThat(registeredChecks()).isEqualTo(0);

        // starting a route again restores it (beforeWrap does not run on a plain restart, so this proves the
        // registration is driven by the processor lifecycle rather than the wrap)
        context.getRouteController().startRoute("guarded1");
        assertThat(registeredChecks()).isEqualTo(1);
    }

    @Test
    void doesNotRegisterACheckWhenHealthCheckIsDisabled() throws Exception {
        // the healthCheckEnabled guard runs on processor start, not in beforeWrap, so it has to be exercised through a
        // started route. A distinct apiUrl keeps the would-be check from deduplicating against the one the enabled
        // routes share, so a missing guard would show up as a second registration.
        OpenFgaSecurityPolicy disabled = new OpenFgaSecurityPolicy();
        disabled.setApiUrl("http://disabled-unused:8080");
        disabled.setStoreId(STORE);
        disabled.setRelation("reader");
        disabled.setUser("user:anne");
        disabled.setObject("document:budget");
        disabled.setHealthCheckEnabled(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:disabled").routeId("disabled").policy(disabled).to("mock:result");
            }
        });
        context.getRouteController().startRoute("disabled");

        // still only the one check the two enabled routes share; the disabled policy registered nothing
        assertThat(registeredChecks()).isEqualTo(1);
    }
}
