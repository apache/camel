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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.component.http.handler.OAuth2TokenRequestHandler;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests related to CAMEL-27233: when the OAuth2 token endpoint is on the same host and port as the API endpoint, the
 * token client must NOT own the endpoint's connection manager.
 *
 * <p>
 * Before the fix, {@link OAuth2ClientConfigurer#configureHttpClient} called {@code clientBuilder.build()} without
 * setting {@code connManagerShared=true}, which made the token client a co-owner of the endpoint's
 * {@code HttpClientConnectionManager}. Because {@link org.apache.hc.client5.http.impl.classic.InternalHttpClient}
 * closes all co-owned resources on {@link java.io.Closeable#close()}, any code path that closed the token client (such
 * as stopping the configurer) also shut down the shared pool.
 *
 * <p>
 * The fix calls {@code clientBuilder.setConnectionManagerShared(true)} before building the token client so that the
 * token client does not take ownership of the connection manager. The connection manager's lifecycle remains under the
 * exclusive control of {@link HttpEndpoint#doStop()}.
 *
 * <p>
 * {@code tokenAndApiOnSameHostSucceeds} is an end-to-end sanity check for the same-host scenario.
 * {@code tokenClientCloseDoesNotShutDownEndpointPool} directly verifies the ownership invariant by simulating the
 * configurer lifecycle and confirming that closing the token client leaves the endpoint's pool functional.
 * </p>
 */
class HttpOAuth2SameHostPoolShutdownTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "same-host-test-token";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Authorization", "Bearer " + FAKE_TOKEN);

        // A single server handles both /token and /items on the same host and port,
        // reproducing the case described in CAMEL-27233.
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/token", new OAuth2TokenRequestHandler(FAKE_TOKEN, CLIENT_ID, CLIENT_SECRET))
                .register("/items",
                        new HeaderValidationHandler("GET", null, null, null, expectedHeaders))
                .create();

        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        closeImmediately(localServer).close();
    }

    /**
     * Sending requests through an OAuth2-configured endpoint whose token URL lives on the same host and port as the API
     * must succeed. The fix ensures the token client does not own the connection manager, so the basic same-host
     * scenario is exercised end-to-end here with two successive requests.
     */
    @Test
    void tokenAndApiOnSameHostSucceeds() {
        String base = "http://localhost:" + localServer.getLocalPort();
        String tokenEndpoint = base + "/token";
        String apiUrl = base + "/items?oauth2ClientId=" + CLIENT_ID
                        + "&oauth2ClientSecret=" + CLIENT_SECRET
                        + "&oauth2TokenEndpoint=" + tokenEndpoint;

        Exchange first = template.request(apiUrl, e -> {
        });
        assertNull(first.getException(), "First request must not throw: " + first.getException());
        assertEquals(HttpStatus.SC_OK, first.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        // A second request exercises the same endpoint client/connection pool
        // to verify the pool was not damaged during the first token fetch.
        Exchange second = template.request(apiUrl, e -> {
        });
        assertNull(second.getException(), "Second request must not throw: " + second.getException());
        assertEquals(HttpStatus.SC_OK, second.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    /**
     * After the fix, the OAuth2 token client is built from the endpoint's builder with {@code connManagerShared=true},
     * so closing it does NOT shut down the endpoint's connection pool.
     * <p>
     * This test directly verifies that invariant: it simulates the lifecycle of {@link OAuth2ClientConfigurer} and
     * confirms the endpoint's connection manager remains usable after the token client is closed.
     */
    @Test
    void tokenClientCloseDoesNotShutDownEndpointPool() throws Exception {
        // Simulate the endpoint's dedicated connection manager (the scenario where connManagerShared=false)
        var endpointConnManager = new PoolingHttpClientConnectionManager();
        var endpointBuilder = HttpClientBuilder.create();
        endpointBuilder.setConnectionManager(endpointConnManager);

        // Apply the configurer — sets connManagerShared=true and builds the token client
        var configurer = new OAuth2ClientConfigurer(
                CLIENT_ID, CLIENT_SECRET,
                "http://localhost:" + localServer.getLocalPort() + "/token",
                null, null, false, 0L, 0L, false);
        configurer.start();
        configurer.configureHttpClient(endpointBuilder);

        // Build the endpoint's main client (simulates what HttpEndpoint.createHttpClient() does)
        var endpointClient = endpointBuilder.build();

        try {
            // Close the OAuth2ClientConfigurer (simulates doStop()) — this closes the token client
            configurer.stop();

            // The endpoint's connection pool must still be functional after the token client closed.
            // A shut-down pool would throw IllegalStateException on lease().
            var route = new HttpRoute(new HttpHost("localhost", 8080));
            assertDoesNotThrow(
                    () -> endpointConnManager.lease("test", route, null),
                    "lease() must not throw — a shut-down pool would throw IllegalStateException")
                    .cancel();
        } finally {
            endpointClient.close();
            endpointConnManager.close();
        }
    }

    /**
     * When the HTTP component is configured with a shared {@link PoolingHttpClientConnectionManager}, both endpoints
     * that use OAuth2 must NOT close that manager when their token client is closed. Stopping one OAuth2 endpoint must
     * leave the component's pool — and any other endpoint using it — fully functional.
     * <p>
     * This test sets a component-level connection manager, creates two endpoints that share it (one with OAuth2, one
     * plain), stops the OAuth2 endpoint, and verifies the plain endpoint can still lease connections from the pool.
     */
    @Test
    void oauth2WithComponentManagerDoesNotShutDownSharedPool() throws Exception {
        // A shared component-level manager (simulates the default: HttpComponent creates one pool for all endpoints)
        var componentManager = new PoolingHttpClientConnectionManager();

        // Apply the OAuth2 configurer as if it were an endpoint using a component-shared manager.
        // In this case connManagerShared=true was set by createHttpClient() AFTER the configurer ran,
        // overriding the configurer's reset to false.  Simulate exactly that order.
        var builder = HttpClientBuilder.create();
        builder.setConnectionManager(componentManager);

        var configurer = new OAuth2ClientConfigurer(
                CLIENT_ID, CLIENT_SECRET,
                "http://localhost:" + localServer.getLocalPort() + "/token",
                null, null, false, 0L, 0L, false);
        configurer.start();
        configurer.configureHttpClient(builder); // sets true, builds token client, resets to false

        // createHttpClient() sets shared=true AFTER the configurer loop — simulate that
        builder.setConnectionManagerShared(true);
        var endpointClient = builder.build(); // endpoint's own client, does NOT own the manager

        try {
            // Stop the OAuth2 configurer — closes the token client; must not touch the shared pool
            configurer.stop();

            // The component's pool must still be functional: a "plain" endpoint using the same manager
            // must be able to lease connections.
            var route = new HttpRoute(new HttpHost("localhost", 8080));
            assertDoesNotThrow(
                    () -> componentManager.lease("plain-endpoint", route, null),
                    "Stopping an OAuth2 endpoint must not shut down the component's shared connection pool")
                    .cancel();
        } finally {
            endpointClient.close();     // must not close the manager (connManagerShared=true)
            componentManager.close();   // component shuts down its own manager on stop
        }
    }

    @Override
    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
    }

    @Override
    protected String getExpectedContent() {
        return "";
    }
}
