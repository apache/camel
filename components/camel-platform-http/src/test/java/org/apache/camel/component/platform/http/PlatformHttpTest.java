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

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class PlatformHttpTest {
    private static JettyServerTest server;
    private static CamelContext ctx;
    private static int port;

    @BeforeAll
    public static void init() throws Exception {

        ctx = new DefaultCamelContext();
        ctx.getRegistry().bind(PlatformHttpConstants.PLATFORM_HTTP_ENGINE_FACTORY, new JettyCustomPlatformHttpEngine());

        port = AvailablePortFinder.getNextAvailable();
        server = new JettyServerTest(port);

        ctx.getRegistry().bind(JettyServerTest.JETTY_SERVER_NAME, server);
        server.start();

        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("platform-http:/get")
                        .setBody().constant("get");
                from("platform-http:/post")
                        .transform().body(String.class, b -> b.toUpperCase());
            }
        });
        ctx.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        ctx.stop();
        server.stop();
    }

    @Test
    public void testGet() throws Exception {
        given()
                .header("Accept", "application/json")
                .port(port)
                .expect()
                .statusCode(200)
                .when()
                .get("/get");
    }

    @Test
    public void testPost() {
        RestAssured.baseURI = "http://localhost:" + port;
        RequestSpecification request = RestAssured.given();
        request.body("test");
        Response response = request.get("/post");

        int statusCode = response.getStatusCode();
        assertEquals(200, statusCode);
        assertEquals("TEST", response.body().asString().trim());
    }

}
