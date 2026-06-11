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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that when an explicit authenticationPath is configured (e.g. /secure/*), only matching subpaths require
 * authentication while other subpaths remain accessible without credentials.
 */
public class BasicAuthenticationSelectivePathTest {

    private static Main main;

    @BeforeAll
    static void init() {
        main = new Main();
        main.setPropertyPlaceholderLocations("basic-auth-nonroot-path-selective.properties");
        main.configure().addRoutesBuilder(new PlatformHttpRouteBuilder());
        main.start();
    }

    @AfterAll
    static void tearDown() {
        main.stop();
    }

    @Test
    public void testUnauthenticatedRequestToSecurePathShouldReturn401() {
        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        // /secure/data is covered by authenticationPath=/secure/*, must require credentials
        given()
                .when()
                .get("/api/secure/data")
                .then()
                .statusCode(401)
                .body(equalTo("Unauthorized"));
    }

    @Test
    public void testAuthenticatedRequestToSecurePathShouldReturn200() {
        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        given()
                .auth().basic("camel", "propertiesPass")
                .when()
                .get("/api/secure/data")
                .then()
                .statusCode(200)
                .body(equalTo("secure-data-response"));
    }

    @Test
    public void testUnauthenticatedRequestToPublicPathShouldReturn200() {
        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        // /public is NOT covered by authenticationPath=/secure/*, so it should be accessible
        given()
                .when()
                .get("/api/public")
                .then()
                .statusCode(200)
                .body(equalTo("public-response"));
    }

    private static class PlatformHttpRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("platform-http:/secure/data?httpMethodRestrict=GET")
                    .setBody(constant("secure-data-response"));

            from("platform-http:/public?httpMethodRestrict=GET")
                    .setBody(constant("public-response"));
        }
    }
}
