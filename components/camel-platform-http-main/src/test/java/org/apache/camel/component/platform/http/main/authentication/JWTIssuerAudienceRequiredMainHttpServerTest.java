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
package org.apache.camel.component.platform.http.main.authentication;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.HttpManagementServerConfigurationProperties;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A JWT authenticator built without an issuer or an audience only verifies the token signature and the exp/nbf claims,
 * so the embedded server refuses to start in that configuration unless the opt-out is set.
 */
class JWTIssuerAudienceRequiredMainHttpServerTest {

    @RegisterExtension
    static AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Test
    void serverFailsToStartWithoutIssuerOrAudience() {
        Main main = MainHttpServerAuthenticationTestSupport.createMain(
                "jwt-auth-no-issuer-audience.properties", port, new PlatformHttpRouteBuilder());
        try {
            assertThatThrownBy(main::start)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("camel.server.jwtIssuer")
                    .hasMessageContaining("camel.server.jwtAudience")
                    .hasMessageContaining("camel.server.jwtAllowMissingIssuerAndAudience");
        } finally {
            MainHttpServerAuthenticationTestSupport.stopMain(main);
        }
    }

    @Test
    void optOutStartsAndValidatesSignatureAndExpiryOnly() {
        // jwt-auth.properties sets jwtAllowMissingIssuerAndAudience=true
        Main main = MainHttpServerAuthenticationTestSupport.createMain(
                "jwt-auth.properties", port, new PlatformHttpRouteBuilder());
        try {
            main.start();

            JWTAuth jwtAuth = JWTAuth.create(Vertx.vertx(), new JWTAuthOptions(
                    new JsonObject().put("keyStore", new JsonObject()
                            .put("type", "jks")
                            .put("path", "test-camel-main-auth-jwt.jks")
                            .put("password", "changeme"))));

            String token = jwtAuth.generateToken(new JsonObject().put("admin", "camel"), new JWTOptions());

            given()
                    .header("Authorization", "Bearer " + token)
                    .when()
                    .get("/main-http-test")
                    .then()
                    .statusCode(200);
        } finally {
            MainHttpServerAuthenticationTestSupport.stopMain(main);
        }
    }

    @Test
    void configuredIssuerAndAudienceRejectAForeignToken() {
        Main main = MainHttpServerAuthenticationTestSupport.createMain(
                "jwt-issuer-audience-auth.properties", port, new PlatformHttpRouteBuilder());
        try {
            main.start();

            JWTAuth jwtAuth = JWTAuth.create(Vertx.vertx(), new JWTAuthOptions(
                    new JsonObject().put("keyStore", new JsonObject()
                            .put("type", "jks")
                            .put("path", "test-camel-main-auth-jwt.jks")
                            .put("password", "changeme"))));

            // signed by a trusted key, but minted for a different issuer and audience
            String foreignToken = jwtAuth.generateToken(
                    new JsonObject().put("admin", "camel"),
                    new JWTOptions()
                            .setIssuer("https://another.example")
                            .addAudience("another-application"));

            given()
                    .header("Authorization", "Bearer " + foreignToken)
                    .when()
                    .get("/main-http-test")
                    .then()
                    .statusCode(401);
        } finally {
            MainHttpServerAuthenticationTestSupport.stopMain(main);
        }
    }

    @Test
    void optOutIsNotSetByDefault() {
        assertThat(new HttpServerConfigurationProperties(null).isJwtAllowMissingIssuerAndAudience()).isFalse();
        assertThat(new HttpManagementServerConfigurationProperties(null).isJwtAllowMissingIssuerAndAudience()).isFalse();
    }

    private static class PlatformHttpRouteBuilder extends RouteBuilder {

        @Override
        public void configure() {
            from("platform-http:/main-http-test")
                    .setBody(simple("main-http-auth-jwt-test-response"));
        }
    }
}
