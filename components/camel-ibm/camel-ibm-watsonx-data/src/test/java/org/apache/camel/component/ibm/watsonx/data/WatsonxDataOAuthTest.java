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
package org.apache.camel.component.ibm.watsonx.data;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ColumnsCollection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The oauthProfile option: the access token of the profile is sent to watsonx.data as a Bearer token, asked again for
 * every request so a renewed token is used, and never posted to IBM Cloud IAM as an API key. A JDK HttpServer plays
 * watsonx.data and IAM.
 */
class WatsonxDataOAuthTest extends CamelTestSupport {

    private static final AtomicInteger TOKEN = new AtomicInteger();
    private static final AtomicInteger IAM_REQUESTS = new AtomicInteger();
    private static final List<String> AUTHORIZATIONS = new CopyOnWriteArrayList<>();

    private static HttpServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/identity/token", exchange -> {
            IAM_REQUESTS.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 400, "{\"errorCode\":\"BXNIM0415E\",\"errorMessage\":\"Provided API key could not be found.\"}");
        });
        server.createContext("/columns", exchange -> {
            AUTHORIZATIONS.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"columns\":[],\"count\":0}");
        });
        server.start();
        // the route starts before each test, and asks the token of its profile at startup
        TestOAuthClientAuthenticationFactory.tokens(WatsonxDataOAuthTest::token);
    }

    private static String token(String profile) {
        return "access-token-" + profile + "-" + TOKEN.get();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        TestOAuthClientAuthenticationFactory.reset();
    }

    @BeforeEach
    void reset() {
        TOKEN.set(1);
        IAM_REQUESTS.set(0);
        AUTHORIZATIONS.clear();
        TestOAuthClientAuthenticationFactory.PROFILES.clear();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String uri(String profile) {
        return "ibm-watsonx-data:test?oauthProfile=" + profile + "&catalogName=iceberg_data&schemaName=sales"
               + "&engineId=presto-01&operation=getAllColumns&serviceUrl=http://localhost:" + server.getAddress().getPort();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(uri("myidp"));
            }
        };
    }

    @Test
    void tokenIsSentAsBearerTokenAndRenewed() {
        // the token is never exchanged at IBM Cloud IAM as an API key: the client has no IAM authenticator
        WatsonxDataEndpoint endpoint = context.getEndpoint(uri("myidp"), WatsonxDataEndpoint.class);
        assertInstanceOf(OAuthProfileAuthenticator.class, endpoint.getWatsonxDataService().getAuthenticator());

        Exchange first = template.send("direct:start", e -> e.getIn().setBody(null));
        assertNull(first.getException());
        assertInstanceOf(ColumnsCollection.class, first.getMessage().getBody());
        // the token is asked for this request, not reused from the startup
        assertEquals(List.of("myidp"), TestOAuthClientAuthenticationFactory.PROFILES);
        // the identity provider renews the token: the next request must use the new one
        TOKEN.set(2);
        Exchange second = template.send("direct:start", e -> e.getIn().setBody(null));
        assertNull(second.getException());
        assertEquals(List.of("myidp", "myidp"), TestOAuthClientAuthenticationFactory.PROFILES);

        assertEquals(List.of("Bearer access-token-myidp-1", "Bearer access-token-myidp-2"), AUTHORIZATIONS);
        assertEquals(0, IAM_REQUESTS.get());
    }

    @Test
    void clientCreatedBeforeStartupUsesTheProfile() throws Exception {
        WatsonxDataConfiguration configuration = new WatsonxDataConfiguration();
        configuration.setApiKey("an-api-key");
        configuration.setOauthProfile("both");
        configuration.setServiceUrl("http://localhost:" + server.getAddress().getPort());
        WatsonxDataEndpoint endpoint = new WatsonxDataEndpoint(
                "ibm-watsonx-data:both", context.getComponent("ibm-watsonx-data", WatsonxDataComponent.class),
                configuration);
        endpoint.setCamelContext(context);
        // the profile takes the place of the API key, also for a client obtained before the endpoint starts
        assertInstanceOf(OAuthProfileAuthenticator.class, endpoint.getWatsonxDataService().getAuthenticator());
        endpoint.start();
        try {
            assertInstanceOf(OAuthProfileAuthenticator.class, endpoint.getWatsonxDataService().getAuthenticator());
        } finally {
            endpoint.stop();
        }
    }

    @Test
    void profileWithNullTokenFailsAtStartup() {
        TestOAuthClientAuthenticationFactory.tokens(profile -> null);
        try {
            Exception e = assertThrows(Exception.class, () -> context.getEndpoint(uri("nothing")));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("The OAuth profile nothing gave no access token", e.getCause().getMessage());
        } finally {
            TestOAuthClientAuthenticationFactory.tokens(WatsonxDataOAuthTest::token);
        }
    }

    @Test
    void profileWithoutTokenFailsAtStartup() {
        TestOAuthClientAuthenticationFactory.tokens(profile -> {
            throw new IllegalStateException("unknown profile " + profile);
        });
        try {
            // the endpoint starts when it is resolved
            Exception e = assertThrows(Exception.class, () -> context.getEndpoint(uri("missing")));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("unknown profile missing", e.getCause().getMessage());
        } finally {
            TestOAuthClientAuthenticationFactory.tokens(WatsonxDataOAuthTest::token);
        }
    }

    @Test
    void profileWithEmptyTokenFailsAtStartup() {
        TestOAuthClientAuthenticationFactory.tokens(profile -> "");
        try {
            Exception e = assertThrows(Exception.class, () -> context.getEndpoint(uri("empty")));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("The OAuth profile empty gave no access token", e.getCause().getMessage());
        } finally {
            TestOAuthClientAuthenticationFactory.tokens(WatsonxDataOAuthTest::token);
        }
    }

    @Test
    void endpointWithoutApiKeyOrProfileFailsAtStartup() {
        Exception e = assertThrows(Exception.class, () -> context.getEndpoint(
                "ibm-watsonx-data:none?operation=getAllColumns&serviceUrl=http://localhost:"
                                                                              + server.getAddress().getPort()));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }
}
