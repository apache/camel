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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class VertxPlatformServerRequestValidationTest {

    @Test
    void testServerRequestFalse() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    PlatformHttpComponent phc = context.getComponent("platform-http", PlatformHttpComponent.class);
                    phc.setServerRequestValidation(false);

                    restConfiguration().component("platform-http")
                            .contextPath("/rest");

                    rest().post("/test")
                            .consumes("application/json")
                            .produces("application/json")
                            .to("direct:rest");

                    from("direct:rest")
                            .setBody(simple("Hello"));
                }
            });

            context.start();

            given()
                    .body("<hello>World</hello>")
                    .contentType("application/xml")
                    .post("/rest/test")
                    .then()
                    .statusCode(200)
                    .body(is("Hello"));

            given()
                    .body("{ \"name\": \"jack\" }")
                    .contentType("application/json")
                    .accept("application/xml")
                    .post("/rest/test")
                    .then()
                    .statusCode(200)
                    .body(is("Hello"));
        } finally {
            context.stop();
        }
    }

    @Test
    void testServerRequestTrue() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    PlatformHttpComponent phc = context.getComponent("platform-http", PlatformHttpComponent.class);
                    phc.setServerRequestValidation(true);

                    restConfiguration().component("platform-http")
                            .contextPath("/rest");

                    rest().post("/test")
                            .consumes("application/json")
                            .produces("application/json")
                            .to("direct:rest");

                    from("direct:rest")
                            .setBody(simple("Hello"));
                }
            });

            context.start();

            given()
                    .body("<hello>World</hello>")
                    .contentType("application/xml")
                    .post("/rest/test")
                    .then()
                    .statusCode(415);

            given()
                    .body("{ \"name\": \"jack\" }")
                    .contentType("application/json")
                    .accept("application/xml")
                    .post("/rest/test")
                    .then()
                    .statusCode(406);
        } finally {
            context.stop();
        }
    }

}
