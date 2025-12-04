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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BasicAuthenticationMainHttpServerTest {

    private static Main main;

    @BeforeAll
    static void init() {
        main = new Main();
        main.setPropertyPlaceholderLocations("basic-auth.properties");
        main.configure().addRoutesBuilder(new BasicAuthenticationMainHttpServerTest.PlatformHttpRouteBuilder());
        main.enableTrace();
        main.start();
    }

    @AfterAll
    static void tearDown() {
        main.stop();
    }

    @Test
    public void testBasicAuthWithAuthenticationPropertiesFile() {
        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        given().when().get("/main-http-test").then().statusCode(401).body(equalTo("Unauthorized"));

        given().auth()
                .basic("camel", "propertiesPass")
                .when()
                .get("/main-http-test")
                .then()
                .statusCode(200)
                .body(equalTo("main-http-auth-basic-test-response"));
    }

    private static class PlatformHttpRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("platform-http:/main-http-test")
                    .log("Received request with headers: ${headers}\nWith body: ${body}")
                    .setBody(simple("main-http-auth-basic-test-response"));
        }
    }
}
