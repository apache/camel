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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyHttpOAuthProfileTest extends BaseNettyTestSupport {

    private final AtomicInteger routeInvocations = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        return context;
    }

    @Test
    public void validBearerTokenReachesRoute() {
        Exchange out = request("valid-token");

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("netty-user:https://issuer.example:[camel-api]:[read]:acme:1234567890:null",
                out.getMessage().getBody(String.class));
        assertEquals(1, routeInvocations.get());
    }

    @Test
    public void rejectsMissingBearerToken() {
        Exchange out = request(null);

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Unauthorized", out.getMessage().getBody(String.class));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsMalformedBearerHeader() {
        Exchange out = requestAuthorization("Bearer valid token");

        assertEquals(400, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_request\"",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Bad Request", out.getMessage().getBody(String.class));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsDuplicateAuthorizationHeaders() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + "/secure"))
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
    public void automaticOptionsBypassesOAuth() {
        Exchange out = template.request("netty-http:http://localhost:{{port}}/secure?throwExceptionOnFailure=false",
                exchange -> {
                    exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
                });

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNotNull(out.getMessage().getHeader("Allow"));
        assertNull(out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void matchOnUriPrefixSubPathEnforcesOAuth() {
        Exchange unauthorized = template.request(
                "netty-http:http://localhost:{{port}}/prefix/sub/path?throwExceptionOnFailure=false",
                exchange -> exchange.getMessage().setBody("hello"));

        assertEquals(401, unauthorized.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer",
                unauthorized.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());

        Exchange authorized = template.request(
                "netty-http:http://localhost:{{port}}/prefix/sub/path?throwExceptionOnFailure=false",
                exchange -> {
                    exchange.getMessage().setBody("hello");
                    exchange.getMessage().setHeader("Authorization", "Bearer valid-token");
                });

        assertEquals(200, authorized.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("prefix-ok", authorized.getMessage().getBody(String.class));
        assertEquals(1, routeInvocations.get());
    }

    @Test
    public void rejectsInvalidBearerToken() {
        Exchange out = request("invalid-token");

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_token\"",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Unauthorized", out.getMessage().getBody(String.class));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void mapsInfrastructureErrorToServiceUnavailable() {
        Exchange out = request("error-token");

        assertEquals(503, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Service Unavailable", out.getMessage().getBody(String.class));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void oauthProfileRequiresExecutorService() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint(
                        "netty-http:http://0.0.0.0:{{port}}/eventloop?oauthProfile=myprofile&usingExecutorService=false")
                        .createConsumer(exchange -> {
                        }));

        assertEquals(
                "The netty-http oauthProfile option requires usingExecutorService=true so token validation "
                     + "does not run on a Netty event-loop thread",
                exception.getMessage());
    }

    @Test
    public void oauthProfileRejectsSharedHttpServer() {
        context.getRegistry().bind("sharedServer", new DefaultNettySharedHttpServer());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint(
                        "netty-http:http://0.0.0.0:{{port}}/shared?oauthProfile=myprofile&nettySharedHttpServer=#sharedServer")
                        .createConsumer(exchange -> {
                        }));

        assertEquals(
                "The netty-http oauthProfile option is not supported with nettySharedHttpServer because "
                     + "the shared pipeline does not use the endpoint executor service",
                exception.getMessage());
    }

    @Test
    public void oauthProfileRequiresSync() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint(
                        "netty-http:http://0.0.0.0:{{port}}/async?oauthProfile=myprofile&sync=false")
                        .createConsumer(exchange -> {
                        }));

        assertEquals(
                "The netty-http oauthProfile option requires sync=true so token validation rejection "
                     + "responses can be returned to the client",
                exception.getMessage());
    }

    @Test
    public void oauthProfileRejectsMixedExecutorServiceOnSharedAddress() throws Exception {
        int sharedPort = AvailablePortFinder.getNextAvailable();

        // the first consumer on an address decides the effective pipeline configuration for that address
        context.getEndpoint("netty-http:http://0.0.0.0:" + sharedPort + "/plain?usingExecutorService=false")
                .createConsumer(exchange -> {
                });

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint("netty-http:http://0.0.0.0:" + sharedPort + "/mixed?oauthProfile=myprofile")
                        .createConsumer(exchange -> {
                        }));

        assertTrue(exception.getMessage().contains("requires usingExecutorService=true on the shared server"),
                "Unexpected message: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("initialized with usingExecutorService=false by another endpoint"),
                "Unexpected message: " + exception.getMessage());
    }

    @Test
    public void oauthProfileIsConsumerOnly() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint("netty-http:http://localhost:{{port}}/out?oauthProfile=myprofile")
                        .createProducer());

        assertEquals("The netty-http oauthProfile option is only supported on consumers", exception.getMessage());
    }

    @Test
    public void invalidOauthProfileFailsStartup() {
        Exception exception = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/invalid?oauthProfile=invalid-profile")
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

    private Exchange request(String token) {
        return requestAuthorization(token == null ? null : "Bearer " + token);
    }

    private Exchange requestAuthorization(String authorization) {
        return template.request("netty-http:http://localhost:{{port}}/secure?throwExceptionOnFailure=false", exchange -> {
            exchange.getMessage().setBody("hello");
            if (authorization != null) {
                exchange.getMessage().setHeader("Authorization", authorization);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/secure?oauthProfile=myprofile")
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
                from("netty-http:http://0.0.0.0:{{port}}/prefix?matchOnUriPrefix=true&oauthProfile=myprofile")
                        .process(exchange -> {
                            routeInvocations.incrementAndGet();
                            exchange.getMessage().setBody("prefix-ok");
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
                        "netty-user", "https://issuer.example", List.of("camel-api"), List.of("read"),
                        Map.of("tenant", "acme"), 1234567890);
            }
            if ("error-token".equals(token)) {
                throw new IllegalStateException("validator unavailable");
            }
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid token");
        }
    }
}
