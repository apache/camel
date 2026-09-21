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

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The readiness check registered by {@link OpaSecurityPolicy} must follow the lifecycle of the routes it guards: it is
 * kept while any guarded route runs, removed once the last one stops, and restored when a route starts again. Otherwise
 * a stopped or reloaded route leaves a check behind reporting on a policy that is no longer enforcing anything
 * (CAMEL-24751).
 */
public class OpaSecurityPolicyHealthCheckLifecycleTest extends CamelTestSupport {

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
        // two routes share the same policy instance, so the check is deduplicated by id and its removal is
        // ref-counted against the routes that are still running
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
                .filter(hc -> hc.getId().startsWith("security-policy:opa-"))
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
        // the healthCheckEnabled guard now runs on processor start, not in beforeWrap, so it has to be exercised
        // through a started route. A distinct serverUrl keeps the would-be check from deduplicating against the one
        // the enabled routes share, so a missing guard would show up as a second registration.
        OpaSecurityPolicy disabled = new OpaSecurityPolicy();
        disabled.setPolicyPath("authz/allow");
        disabled.setServerUrl("http://disabled-unused:8181");
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
