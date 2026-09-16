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
package org.apache.camel.component.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.AuthenticationValidationHandler;
import org.apache.camel.component.http.interceptor.RequestBasicAuth;
import org.apache.camel.component.http.interceptor.ResponseBasicUnauthorized;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.spi.SecretRotationAware;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that {@link HttpComponent} implements {@link SecretRotationAware} and that calling
 * {@link HttpComponent#onSecretRotation(Object)} does not throw and that fresh credentials are picked up when routes
 * are restarted after the rotation.
 */
class HttpComponentSecretRotationAwareTest extends BaseHttpTest {

    private HttpServer localServer;

    /** Server-side expected credentials: mutable so we can "rotate" them during the test. */
    private final AtomicReference<String[]> expectedCreds = new AtomicReference<>(new String[] { "alice", "secret1" });

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/api", (request, response, ctx) -> {
                    String[] creds = expectedCreds.get();
                    new AuthenticationValidationHandler(
                            GET.name(), null, null, getExpectedContent(),
                            creds[0], creds[1])
                            .handle(request, response, ctx);
                })
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestValidateHost());
        requestInterceptors.add(new RequestBasicAuth());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseContent());
        responseInterceptors.add(new ResponseBasicUnauthorized());
        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    @Test
    void httpComponentImplementsSecretRotationAware() {
        HttpComponent component = context.getComponent("http", HttpComponent.class);
        assertNotNull(component);
        assertInstanceOf(SecretRotationAware.class, component,
                "HttpComponent must implement SecretRotationAware");
    }

    @Test
    void onSecretRotationDoesNotThrow() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);
        // Must not throw regardless of current state
        component.onSecretRotation("unit-test-source");
        component.onSecretRotation(null);
    }

    /**
     * Simulates the full rotation lifecycle: configure component with old credentials → send request (succeeds) →
     * rotate secret (update component + server expectation) → call onSecretRotation() → restart routes → send request
     * again (must succeed with new credentials because endpoints were recreated).
     */
    @Test
    void credentialsArePickedUpAfterRotationAndRouteRestart() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        // Configure the component with initial credentials via HttpConfiguration
        HttpConfiguration config = new HttpConfiguration();
        config.setAuthMethod("Basic");
        config.setAuthUsername("alice");
        config.setAuthPassword("secret1");
        component.setHttpConfiguration(config);
        expectedCreds.set(new String[] { "alice", "secret1" });

        String serverUrl = "http://localhost:" + localServer.getLocalPort() + "/api";

        // Add a route that hits the authenticated endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:secured").routeId("secured-route")
                        .to(serverUrl);
            }
        });

        // Phase 1: initial credentials work
        Exchange ex1 = template.request("direct:secured", e -> {
        });
        assertNotNull(ex1);
        assertNull(ex1.getException(), "Initial request should succeed");
        assertEquals(HttpStatus.SC_OK, ex1.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        // Phase 2: rotate — update both the server expectation and the component configuration.
        // In the real CAMEL-24636 flow, reloadComponentProperties() re-applies the placeholders first,
        // then onSecretRotation() is called, then reloadAllRoutes() clears the endpoint registry
        // and restarts all route definitions with fresh endpoints.
        expectedCreds.set(new String[] { "alice", "secret2" });
        config.setAuthPassword("secret2");
        component.onSecretRotation("vault-rotation-test");

        // Simulate reloadAllRoutes(): remove the route, clear endpoint registry, then restart.
        // This replicates InternalRouteController.reloadAllRoutes() which explicitly clears the
        // endpoint registry so that fresh endpoints are created from the updated component config.
        context.getRouteController().stopRoute("secured-route");
        context.removeRoute("secured-route");
        context.getEndpointRegistry().clear();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:secured").routeId("secured-route")
                        .to(serverUrl);
            }
        });

        // Phase 3: the restarted route creates a new endpoint from the updated HttpConfiguration
        Exchange ex2 = template.request("direct:secured", e -> {
        });
        assertNotNull(ex2);
        assertNull(ex2.getException(), "Request after rotation should succeed with new credentials");
        assertEquals(HttpStatus.SC_OK, ex2.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
}
