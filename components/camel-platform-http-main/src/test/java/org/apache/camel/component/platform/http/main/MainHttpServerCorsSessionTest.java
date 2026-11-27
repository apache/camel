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
package org.apache.camel.component.platform.http.main;

import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CORS and session settings of the embedded HTTP server are configured with camel.server.* keys.
 */
class MainHttpServerCorsSessionTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private Main main;

    @AfterEach
    void stopMain() {
        if (main != null) {
            main.stop();
        }
        RestAssured.reset();
    }

    private void startMain(Map<String, String> properties) {
        main = new Main();
        main.addOverrideProperty("camel.server.enabled", "true");
        main.addOverrideProperty("camel.server.port", Integer.toString(port.getPort()));
        properties.forEach(main::addOverrideProperty);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/hello").setBody(constant("Hello"));
            }
        });
        main.start();
        RestAssured.port = port.getPort();
    }

    @Test
    void corsPreflightIsAnsweredWithTheConfiguredHeaders() {
        startMain(Map.of(
                "camel.server.corsEnabled", "true",
                "camel.server.corsOrigins", "https://app.example.com, https://other.example.com",
                "camel.server.corsMethods", "GET,POST",
                "camel.server.corsHeaders", "X-Custom",
                "camel.server.corsExposedHeaders", "X-Total,X-Page",
                "camel.server.cors-access-control-max-age", "3600"));

        Response preflight = given()
                .header("Origin", "https://app.example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Custom,X-Other")
                .when()
                .options("/hello");

        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.header("Access-Control-Allow-Origin")).isEqualTo("https://app.example.com");
        assertThat(preflight.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(preflight.header("Access-Control-Allow-Methods")).isEqualTo("POST");
        assertThat(preflight.header("Access-Control-Allow-Headers")).isEqualTo("X-Custom");
        assertThat(preflight.header("Access-Control-Expose-Headers")).isEqualTo("X-Total,X-Page");
        assertThat(preflight.header("Access-Control-Max-Age")).isEqualTo("3600");

        Response foreign = given()
                .header("Origin", "https://evil.example.com")
                .when()
                .get("/hello");

        assertThat(foreign.statusCode()).isEqualTo(200);
        assertThat(foreign.header("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void corsIsNotHandledByDefault() {
        startMain(Map.of());

        Response response = given()
                .header("Origin", "https://app.example.com")
                .when()
                .get("/hello");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void sessionCookieHasTheConfiguredSettings() {
        startMain(Map.of(
                "camel.server.sessionEnabled", "true",
                "camel.server.sessionCookieName", "camel-session",
                "camel.server.sessionCookiePath", "/hello",
                "camel.server.sessionCookieSecure", "true",
                "camel.server.sessionCookieSameSite", "lax"));

        Response response = given().when().get("/hello");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().asString()).isEqualTo("Hello");
        String cookie = response.header("Set-Cookie");
        assertThat(cookie)
                .startsWith("camel-session=")
                .contains("Path=/hello")
                .contains("Secure")
                .contains("HTTPOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void noSessionCookieByDefault() {
        startMain(Map.of());

        Response response = given().when().get("/hello");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Set-Cookie")).isNull();
    }
}
