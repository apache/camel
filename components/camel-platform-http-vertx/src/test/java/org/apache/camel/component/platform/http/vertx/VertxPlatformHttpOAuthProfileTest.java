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
package org.apache.camel.component.platform.http.vertx;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VertxPlatformHttpOAuthProfileTest {

    private final AtomicInteger routeInvocations = new AtomicInteger();

    @BeforeEach
    void reset() {
        VertxStubOAuthTokenValidationFactory.reset();
        routeInvocations.set(0);
    }

    @Test
    void rejectsMissingBearerTokenBeforeBodyHandler() throws Exception {
        CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        HttpClient client = null;
        try {
            addRoute(context);
            VertxPlatformHttpEngineTest.startCamelContext(context);

            VertxPlatformHttpServer server = context.hasService(VertxPlatformHttpServer.class);
            client = server.getVertx().createHttpClient();

            CompletableFuture<HttpClientResponse> responseFuture = new CompletableFuture<>();
            client.request(HttpMethod.POST, server.getPort(), "localhost", "/secure")
                    .onSuccess(request -> {
                        request.putHeader("Content-Length", "1024");
                        request.response()
                                .onSuccess(responseFuture::complete)
                                .onFailure(responseFuture::completeExceptionally);
                        request.sendHead().onFailure(responseFuture::completeExceptionally);
                    })
                    .onFailure(responseFuture::completeExceptionally);

            HttpClientResponse response = responseFuture.get(5, TimeUnit.SECONDS);
            CompletableFuture<String> bodyFuture = new CompletableFuture<>();
            response.body()
                    .onSuccess(buffer -> bodyFuture.complete(buffer.toString()))
                    .onFailure(bodyFuture::completeExceptionally);

            assertEquals(401, response.statusCode());
            assertEquals("Unauthorized", bodyFuture.get(5, TimeUnit.SECONDS));

            assertEquals(0, routeInvocations.get());
        } finally {
            if (client != null) {
                client.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            context.stop();
        }
    }

    @Test
    void validBearerTokenReachesRouteAfterBodyHandler() throws Exception {
        CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            addRoute(context);
            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .header("Authorization", "Bearer valid-token")
                    .body("hello")
                    .when()
                    .post("/secure")
                    .then()
                    .statusCode(200)
                    .body(equalTo("vertx-user:hello"));

            assertEquals(1, routeInvocations.get());
            assertEquals("myprofile", VertxStubOAuthTokenValidationFactory.lastProfileName);
            assertEquals("valid-token", VertxStubOAuthTokenValidationFactory.lastToken);
        } finally {
            context.stop();
        }
    }

    @Test
    void rejectsInvalidBearerTokenOverVertx() throws Exception {
        CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            addRoute(context);
            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .header("Authorization", "Bearer invalid-token")
                    .body("hello")
                    .when()
                    .post("/secure")
                    .then()
                    .statusCode(401)
                    .body(equalTo("Unauthorized"));

            assertEquals(0, routeInvocations.get());
            assertEquals("myprofile", VertxStubOAuthTokenValidationFactory.lastProfileName);
            assertEquals("invalid-token", VertxStubOAuthTokenValidationFactory.lastToken);
        } finally {
            context.stop();
        }
    }

    @Test
    void mapsInfrastructureErrorToServiceUnavailableOverVertx() throws Exception {
        CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            addRoute(context);
            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .header("Authorization", "Bearer error-token")
                    .body("hello")
                    .when()
                    .post("/secure")
                    .then()
                    .statusCode(503)
                    .body(equalTo("Service Unavailable"));

            assertEquals(0, routeInvocations.get());
            assertEquals("myprofile", VertxStubOAuthTokenValidationFactory.lastProfileName);
            assertEquals("error-token", VertxStubOAuthTokenValidationFactory.lastToken);
        } finally {
            context.stop();
        }
    }

    private void addRoute(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/secure?oauthProfile=myprofile")
                        .process(exchange -> {
                            routeInvocations.incrementAndGet();
                            OAuthTokenValidationResult result = exchange.getProperty(
                                    PlatformHttpConstants.OAUTH_TOKEN_VALIDATION_RESULT,
                                    OAuthTokenValidationResult.class);
                            exchange.getMessage().setBody(result.getSubject()
                                                          + ":" + exchange.getMessage().getBody(String.class));
                        });
            }
        });
    }
}
