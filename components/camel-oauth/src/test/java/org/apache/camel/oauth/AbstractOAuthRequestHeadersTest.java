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
package org.apache.camel.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The OAuth processors behind a real HTTP consumer: the bearer token and the session cookie must come from the
 * {@code Authorization} and {@code Cookie} request headers, although the consumer also maps the query and form
 * parameters of the request into the message headers. The identity provider is {@link StubOAuth}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractOAuthRequestHeadersTest {

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private int port;
    private CamelContext context;
    private OAuth oauth;

    /**
     * Starts the HTTP server, before the routes are added to the context.
     */
    abstract void startServer(CamelContext context, int port) throws Exception;

    abstract void stopServer() throws Exception;

    /**
     * The consumer endpoint uri for the given path.
     */
    abstract String endpointUri(String path);

    @BeforeAll
    void start() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        context = new DefaultCamelContext();
        context.getPropertiesComponent().addInitialProperty(OAuth.CAMEL_OAUTH_REDIRECT_URI, "https://app.example/auth");
        oauth = new StubOAuth();
        context.getRegistry().bind(OAuth.class.getName(), oauth);
        startServer(context, port);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpointUri("/api"))
                        .process(new OAuthBearerTokenProcessor())
                        .process(e -> e.getMessage().setBody("api:" + bearerSubject(e)));
                from(endpointUri("/auth"))
                        .process(new OAuthCodeFlowCallback());
                from(endpointUri("/app"))
                        .process(new OAuthCodeFlowProcessor())
                        .process(e -> e.getMessage().setBody("app:" + sessionSubject(e)));
            }
        });
        context.start();
    }

    @AfterAll
    void stop() throws Exception {
        if (context != null) {
            context.stop();
        }
        stopServer();
    }

    @Test
    void bearerTokenIsReadFromTheAuthorizationHeader() throws Exception {
        var response = get("/api", null, "Bearer " + StubOAuth.ALICE_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals("api:alice", response.body());
        assertNull(setCookie(response), "a bearer request does not start a session");
    }

    @Test
    void bearerTokenIsNotReadFromAQueryParameter() throws Exception {
        var response = get("/api?Authorization=Bearer%20" + StubOAuth.ALICE_TOKEN, null, null);

        assertEquals(401, response.statusCode());
        assertNull(setCookie(response));
    }

    @Test
    void bearerTokenIsNotReadFromAFormParameter() throws Exception {
        var request = request("/api")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("Authorization=Bearer%20" + StubOAuth.ALICE_TOKEN))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertNull(setCookie(response));
    }

    @Test
    void aQueryParameterDoesNotReplaceTheAuthorizationHeader() throws Exception {
        var response = get("/api?Authorization=Bearer%20" + StubOAuth.BOB_TOKEN, null, "Bearer " + StubOAuth.ALICE_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals("api:alice", response.body());
    }

    @Test
    void severalAuthorizationHeadersAreRejected() throws Exception {
        var request = request("/api")
                .header("Authorization", "Bearer " + StubOAuth.ALICE_TOKEN)
                .header("Authorization", "Bearer " + StubOAuth.BOB_TOKEN)
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    void sessionCookieIsReadFromTheCookieHeader() throws Exception {
        var cookie = loginAsBob();

        var response = get("/app", cookie, null);

        assertEquals(200, response.statusCode());
        assertEquals("app:bob", response.body());
    }

    @Test
    void sessionCookieIsNotReadFromAQueryParameter() throws Exception {
        var cookie = loginAsBob();

        var response = get("/app?Cookie=" + URLEncoder.encode(cookie, StandardCharsets.UTF_8), null, null);

        assertEquals(302, response.statusCode(), "a request without a session cookie is sent to the login");
    }

    @Test
    void bearerRequestLeavesTheSessionUnchanged() throws Exception {
        var cookie = loginAsBob();

        var response = get("/api", cookie, "Bearer " + StubOAuth.ALICE_TOKEN);

        assertEquals(200, response.statusCode());
        assertEquals("api:alice", response.body());
        assertNull(setCookie(response));
        assertEquals("app:bob", get("/app", cookie, null).body());
    }

    /**
     * Runs the authorization code flow and returns the session cookie the browser holds afterwards.
     */
    private String loginAsBob() throws Exception {
        var start = get("/app", null, null);
        assertEquals(302, start.statusCode());
        var sessionCookie = setCookie(start);
        assertNotNull(sessionCookie);
        var cookie = sessionCookie.substring(0, sessionCookie.indexOf(';'));
        var location = start.headers().firstValue("Location").orElseThrow();
        var state = location.substring(location.indexOf("state=") + "state=".length());

        var callback = get("/auth?code=" + StubOAuth.BOB_CODE + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8),
                cookie, null);
        assertEquals(302, callback.statusCode());
        return cookie;
    }

    private HttpResponse<String> get(String pathAndQuery, String cookie, String authorization) throws Exception {
        var builder = request(pathAndQuery).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery));
    }

    private static String setCookie(HttpResponse<?> response) {
        return response.headers().firstValue("Set-Cookie").orElse(null);
    }

    private static String bearerSubject(Exchange exchange) {
        var profile = exchange.getProperty(OAuth.CAMEL_OAUTH_USER_PROFILE, UserProfile.class);
        return profile != null ? profile.subject() : "none";
    }

    private String sessionSubject(Exchange exchange) {
        return oauth.getSession(exchange)
                .flatMap(OAuthSession::getUserProfile)
                .map(UserProfile::subject)
                .orElse("none");
    }
}
