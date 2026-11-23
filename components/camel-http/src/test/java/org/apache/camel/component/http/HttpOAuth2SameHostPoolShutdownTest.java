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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for CAMEL-27233: when the OAuth2 token endpoint is on the same host and port as the API endpoint, the
 * token client's independent connection manager must NOT affect the API client's connection pool.
 *
 * <p>
 * Before the fix, {@link OAuth2ClientConfigurer#configureHttpClient} called {@code clientBuilder.build()}, which built
 * the token client inheriting the endpoint's {@code HttpClientConnectionManager}. Because {@code connManagerShared}
 * defaults to {@code false}, both clients considered themselves the connection-manager owner. When the token client was
 * closed (e.g. on a stop/restart cycle or when {@code doStop()} ran), it shut down the shared pool, leaving the API
 * client's pool in a shut-down state and causing subsequent requests to throw
 * {@code IllegalStateException: Connection pool shut down}.
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
     * must succeed. Before the fix, the connection manager shared between the token client and the API client was shut
     * down when the token client closed, causing subsequent API requests to throw
     * {@code IllegalStateException: Connection pool shut down}.
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

            // The endpoint's connection pool must still be functional after the token client closed
            var route = new HttpRoute(new HttpHost("localhost", 8080));
            var future = endpointConnManager.lease("test", route, null);
            try {
                // just checking lease() doesn't throw — a shut-down pool would throw IllegalStateException here
            } finally {
                future.cancel();
            }
        } finally {
            endpointClient.close();
            endpointConnManager.close();
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
