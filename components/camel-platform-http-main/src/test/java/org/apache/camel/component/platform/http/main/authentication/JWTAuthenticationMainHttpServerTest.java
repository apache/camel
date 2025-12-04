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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JWTAuthenticationMainHttpServerTest {

    private static Main main;

    private static JWTAuth jwtAuth;

    @BeforeAll
    static void init() {
        main = new Main();
        main.setPropertyPlaceholderLocations("jwt-auth.properties");
        main.configure().addRoutesBuilder(new PlatformHttpRouteBuilder());
        main.enableTrace();
        main.start();

        jwtAuth = JWTAuth.create(
                Vertx.vertx(),
                new JWTAuthOptions(new JsonObject()
                        .put(
                                "keyStore",
                                new JsonObject()
                                        .put("type", "jks")
                                        .put("path", "test-camel-main-auth-jwt.jks")
                                        .put("password", "changeme"))));
    }

    @AfterAll
    static void tearDown() {
        main.stop();
    }

    @Test
    void testJWTAuth() {
        String validToken = jwtAuth.generateToken(new JsonObject().put("admin", "camel"), new JWTOptions());
        String invalidToken = validToken.substring(0, (validToken.length() - 2));

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        given().when().get("/main-http-test").then().statusCode(401).body(equalTo("Unauthorized"));

        given().header("Authorization", "Bearer " + validToken)
                .when()
                .get("/main-http-test")
                .then()
                .statusCode(200)
                .body(equalTo("main-http-auth-jwt-test-response"));

        given().header("Authorization", "Bearer " + invalidToken)
                .when()
                .get("/main-http-test")
                .then()
                .statusCode(401)
                .body(equalTo("Unauthorized"));
    }

    private static class PlatformHttpRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("platform-http:/main-http-test")
                    .log("Received request with headers: ${headers}\nWith body: ${body}")
                    .setBody(simple("main-http-auth-jwt-test-response"));
        }
    }
}
