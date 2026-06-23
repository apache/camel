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
package org.apache.camel.component.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class ServletOAuthProfileTest extends ServletCamelRouterTestSupport {

    private final AtomicInteger routeInvocations = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        return context;
    }

    @Test
    public void validBearerTokenReachesRoute() throws Exception {
        assertOAuthEndpointInitialized();
        WebResponse response = request("valid-token");

        assertResponseCode(200, response);
        assertEquals("servlet-user:https://issuer.example:[camel-api]:[read]:acme:1234567890:null", response.getText());
        assertEquals(1, routeInvocations.get());
    }

    @Test
    public void rejectsMissingBearerToken() throws Exception {
        assertOAuthEndpointInitialized();
        WebResponse response = request(null);

        assertResponseCode(401, response);
        assertEquals("Bearer",
                response.getHeaderField("WWW-Authenticate"));
        assertEquals("Unauthorized", responseText(response));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsMalformedBearerHeader() throws Exception {
        assertOAuthEndpointInitialized();
        WebResponse response = requestAuthorization("Bearer valid token");

        assertResponseCode(400, response);
        assertEquals("Bearer error=\"invalid_request\"",
                response.getHeaderField("WWW-Authenticate"));
        assertEquals("Bad Request", responseText(response));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsDuplicateAuthorizationHeaders() throws Exception {
        assertOAuthEndpointInitialized();
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(contextUrl + "/services/secure"))
                .header("Authorization", "Bearer valid-token")
                .header("Authorization", "Bearer valid-token")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertEquals("Bearer error=\"invalid_request\"",
                response.headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals("Bad Request", response.body());
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void automaticOptionsBypassesOAuth() throws Exception {
        assertOAuthEndpointInitialized();
        WebResponse response = query(new OptionsMethodWebRequest(contextUrl + "/services/secure"), false);

        assertResponseCode(200, response);
        assertNotNull(response.getHeaderField("Allow"));
        assertNull(response.getHeaderField("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsInvalidBearerToken() throws Exception {
        assertOAuthEndpointInitialized();
        WebResponse response = request("invalid-token");

        assertResponseCode(401, response);
        assertEquals("Bearer error=\"invalid_token\"",
                response.getHeaderField("WWW-Authenticate"));
        assertEquals("Unauthorized", responseText(response));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void mapsInfrastructureErrorToServiceUnavailable() throws Exception {
        assertOAuthEndpointInitialized();
        WebResponse response = request("error-token");

        assertResponseCode(503, response);
        assertNull(response.getHeaderField("WWW-Authenticate"));
        assertEquals("Service Unavailable", responseText(response));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void invalidOauthProfileFailsStartup() {
        Exception exception = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:/invalid?oauthProfile=invalid-profile")
                        .to("mock:invalid");
            }
        }));
        assertUnknownOAuthProfile(exception);
    }

    private static void assertUnknownOAuthProfile(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IllegalArgumentException
                    && "Unknown OAuth profile: invalid-profile".equals(current.getMessage())) {
                return;
            }
            current = current.getCause();
        }
        fail("Expected cause chain to contain unknown OAuth profile failure");
    }

    private WebResponse request(String token) throws IOException {
        return requestAuthorization(token == null ? null : "Bearer " + token);
    }

    private WebResponse requestAuthorization(String authorization) throws IOException {
        WebRequest request = new GetMethodWebRequest(contextUrl + "/services/secure");
        if (authorization != null) {
            request.setHeaderField("Authorization", authorization);
        }
        return query(request, false);
    }

    private static String responseText(WebResponse response) throws IOException {
        InputStream stream = response.con.getErrorStream();
        if (stream == null) {
            try {
                stream = response.con.getInputStream();
            } catch (IOException e) {
                return e.getMessage();
            }
        }
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void assertResponseCode(int expected, WebResponse response) throws IOException {
        int actual = response.getResponseCode();
        if (actual != expected) {
            assertEquals(expected, actual, responseText(response));
        }
    }

    private void assertOAuthEndpointInitialized() {
        ServletEndpoint endpoint = context.getEndpoints().stream()
                .filter(ServletEndpoint.class::isInstance)
                .map(ServletEndpoint.class::cast)
                .filter(candidate -> candidate.getEndpointUri().contains("/secure"))
                .findFirst()
                .orElseThrow();
        assertEquals("myprofile", endpoint.getOauthProfile());
        assertNotNull(endpoint.getOauthHttpSecurity());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:/secure?oauthProfile=myprofile")
                        .process(exchange -> {
                            routeInvocations.incrementAndGet();
                            OAuthTokenValidationResult result = exchange.getProperty(
                                    OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT,
                                    OAuthTokenValidationResult.class);
                            exchange.getMessage().setBody(result.getSubject() + ":"
                                                          + result.getIssuer() + ":"
                                                          + result.getAudience() + ":"
                                                          + result.getScopes() + ":"
                                                          + result.getClaim("tenant") + ":"
                                                          + result.getExpiresAt() + ":"
                                                          + exchange.getMessage().getHeader(
                                                                  "Authorization"));
                        });
            }
        };
    }

    private static final class StubOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            return validate(token);
        }

        @Override
        public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
            assertEquals("myprofile", profileName);
            return validate(token);
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }

        @Override
        public void validateConfiguration(CamelContext context, String profileName) {
            if (!"myprofile".equals(profileName)) {
                throw new IllegalArgumentException("Unknown OAuth profile: " + profileName);
            }
        }

        private OAuthTokenValidationResult validate(String token) {
            if ("valid-token".equals(token)) {
                return OAuthTokenValidationResult.valid(
                        "servlet-user", "https://issuer.example", List.of("camel-api"), List.of("read"),
                        Map.of("tenant", "acme"), 1234567890);
            }
            if ("error-token".equals(token)) {
                throw new IllegalStateException("validator unavailable");
            }
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid token");
        }
    }
}
