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
package org.apache.camel.component.platform.http;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlatformHttpOAuthProfileHttpTest extends AbstractPlatformHttpTest {

    private static final AtomicInteger ROUTE_INVOCATIONS = new AtomicInteger();

    @BeforeEach
    void reset() {
        StubOAuthTokenValidationFactory.reset();
        ROUTE_INVOCATIONS.set(0);
    }

    @Test
    public void rejectsMissingBearerTokenOverHttp() {
        given()
                .port(port)
                .when()
                .get("/secure")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", is("Bearer"))
                .body(is("Unauthorized"));

        assertEquals(0, ROUTE_INVOCATIONS.get());
    }

    @Test
    public void acceptsValidBearerTokenOverHttp() {
        given()
                .port(port)
                .header("Authorization", "Bearer valid-token")
                .when()
                .get("/secure")
                .then()
                .statusCode(200)
                .body(is("camel-user:true:read"));

        assertEquals(1, ROUTE_INVOCATIONS.get());
    }

    @Test
    public void rejectsInvalidBearerTokenOverHttp() {
        given()
                .port(port)
                .header("Authorization", "Bearer invalid-token")
                .when()
                .get("/secure")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", is("Bearer"))
                .body(is("Unauthorized"));

        assertEquals(0, ROUTE_INVOCATIONS.get());
    }

    @Test
    public void rejectsMalformedBearerTokenOverHttp() {
        for (String authorization : List.of(
                "Basic valid-token", "Bearer", "Bearer   ", "Bearervalid-token", "Bearer valid token")) {
            given()
                    .port(port)
                    .header("Authorization", authorization)
                    .when()
                    .get("/secure")
                    .then()
                    .statusCode(401)
                    .header("WWW-Authenticate", is("Bearer"))
                    .body(is("Unauthorized"));
        }

        assertEquals(0, ROUTE_INVOCATIONS.get());
    }

    @Test
    public void rejectedBearerTokenIsNotEchoedWhenReturningRequestHeadersOverHttp() {
        given()
                .port(port)
                .header("Authorization", "Bearer invalid-token")
                .when()
                .get("/secure-echo")
                .then()
                .statusCode(401)
                .header("Authorization", emptyOrNullString())
                .header("WWW-Authenticate", is("Bearer"))
                .body(is("Unauthorized"));

        assertEquals(0, ROUTE_INVOCATIONS.get());
    }

    @Test
    public void mapsInfrastructureFailureToServiceUnavailableOverHttp() {
        given()
                .port(port)
                .header("Authorization", "Bearer error-token")
                .when()
                .get("/secure")
                .then()
                .statusCode(503)
                .header("WWW-Authenticate", emptyOrNullString())
                .body(is("Service Unavailable"));

        assertEquals(0, ROUTE_INVOCATIONS.get());
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/secure?oauthProfile=myprofile")
                        .process(exchange -> {
                            ROUTE_INVOCATIONS.incrementAndGet();
                            OAuthTokenValidationResult result = exchange.getProperty(
                                    PlatformHttpConstants.OAUTH_TOKEN_VALIDATION_RESULT,
                                    OAuthTokenValidationResult.class);
                            exchange.getMessage().setBody(result.getName()
                                                          + ":" + result.hasScope("read")
                                                          + ":" + result.getAttribute("scope", String.class));
                        });
                from("platform-http:/secure-echo?oauthProfile=myprofile&returnHttpRequestHeaders=true")
                        .process(exchange -> {
                            ROUTE_INVOCATIONS.incrementAndGet();
                            exchange.getMessage().setBody("secured");
                        });
            }
        };
    }
}
