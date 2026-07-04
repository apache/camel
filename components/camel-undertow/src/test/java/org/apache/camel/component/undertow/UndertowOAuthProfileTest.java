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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class UndertowOAuthProfileTest extends BaseUndertowTest {

    private final AtomicInteger routeInvocations = new AtomicInteger();
    private final AtomicReference<String> customHandlerAuthorization = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        context.getRegistry().bind("recordingHandler", new RecordingHandler(customHandlerAuthorization));
        return context;
    }

    @Test
    public void validBearerTokenReachesRoute() {
        Exchange out = request("valid-token");

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("undertow-user:https://issuer.example:[camel-api]:[read]:acme:1234567890:null",
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
    public void rejectsInvalidBearerToken() {
        Exchange out = request("invalid-token");

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_token\"",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Unauthorized", out.getMessage().getBody(String.class));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsMalformedBearerHeader() {
        Exchange out = requestAuthorization("/secure", "Bearer valid token");

        assertEquals(400, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_request\"",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals("Bad Request", out.getMessage().getBody(String.class));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsDuplicateAuthorizationHeaders() throws Exception {
        HttpResponse<String> response = requestDuplicateAuthorization();

        assertEquals(400, response.statusCode());
        assertEquals("Bearer error=\"invalid_request\"",
                response.headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals("Bad Request", response.body());
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
    public void customHandlerSeesAuthorizationBeforeOAuthHandler() {
        Exchange out = request("/handler", "valid-token");

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer valid-token", customHandlerAuthorization.get());
        assertEquals(1, routeInvocations.get());
    }

    @Test
    public void validBearerTokenRemovesAuthorizationQueryParameterFromCamelHeaders() {
        Exchange out = template.request(
                "undertow:http://localhost:{{port}}/secure?throwExceptionOnFailure=false",
                exchange -> {
                    exchange.getMessage().setBody("hello");
                    exchange.getMessage().setHeader(Exchange.HTTP_QUERY, "Authorization=spoof");
                    exchange.getMessage().setHeader("Authorization", "Bearer valid-token");
                });

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("undertow-user:https://issuer.example:[camel-api]:[read]:acme:1234567890:null",
                out.getMessage().getBody(String.class));
        assertEquals(1, routeInvocations.get());
    }

    @Test
    public void automaticOptionsBypassesOAuth() {
        Exchange out = template.request("undertow:http://localhost:{{port}}/secure?throwExceptionOnFailure=false", exchange -> {
            exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
        });

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void optionsRequiresAuthWhenOptionsEnabled() {
        Exchange out
                = template.request("undertow:http://localhost:{{port}}/options?throwExceptionOnFailure=false", exchange -> {
                    exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
                });

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void suspendedConsumerReturnsServiceUnavailableBeforeOAuth() throws Exception {
        context.getRouteController().suspendRoute("secureRoute");

        Exchange out = request(null);

        assertEquals(503, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void oauthProfileIsConsumerOnly() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint("undertow:http://localhost:{{port}}/out?oauthProfile=myprofile")
                        .createProducer());

        assertEquals("The undertow oauthProfile option is only supported on consumers", exception.getMessage());
    }

    @Test
    public void invalidOauthProfileFailsStartup() {
        Exception exception = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://0.0.0.0:{{port}}/invalid?oauthProfile=invalid-profile")
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

    private HttpResponse<String> requestDuplicateAuthorization() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + "/secure"))
                .header("Authorization", "Bearer valid-token")
                .header("Authorization", "Bearer valid-token")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private Exchange request(String token) {
        return request("/secure", token);
    }

    private Exchange request(String path, String token) {
        return requestAuthorization(path, token == null ? null : "Bearer " + token);
    }

    private Exchange requestAuthorization(String path, String authorization) {
        return template.request("undertow:http://localhost:{{port}}" + path + "?throwExceptionOnFailure=false", exchange -> {
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
                from("undertow:http://0.0.0.0:{{port}}/secure?oauthProfile=myprofile")
                        .routeId("secureRoute")
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
                from("undertow:http://0.0.0.0:{{port}}/handler?oauthProfile=myprofile&handlers=#recordingHandler")
                        .process(exchange -> {
                            routeInvocations.incrementAndGet();
                            exchange.getMessage().setBody("ok");
                        });
                from("undertow:http://0.0.0.0:{{port}}/options?oauthProfile=myprofile&optionsEnabled=true")
                        .process(exchange -> {
                            routeInvocations.incrementAndGet();
                            exchange.getMessage().setBody("ok");
                        });
            }
        };
    }

    private static final class RecordingHandler implements CamelUndertowHttpHandler {

        private final AtomicReference<String> authorization;
        private HttpHandler next;

        private RecordingHandler(AtomicReference<String> authorization) {
            this.authorization = authorization;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            authorization.set(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION));
            next.handleRequest(exchange);
        }

        @Override
        public void setNext(HttpHandler nextHandler) {
            this.next = nextHandler;
        }
    }
}
