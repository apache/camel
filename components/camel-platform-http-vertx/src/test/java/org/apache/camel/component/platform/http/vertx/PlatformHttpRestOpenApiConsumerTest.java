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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformHttpRestOpenApiConsumerTest {

    @Test
    public void testRestOpenApi() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore")
                            .to("mock:result");

                    from("direct:getPetById").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();

            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);

            given().when().get("/api/v3/pet/123").then().statusCode(200).body(equalTo("{\"pet\": \"tony the tiger\"}"));

            mock.assertIsSatisfied();

        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiDevMode() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        // run in developer mode
        context.getCamelContextExtension().setProfile("dev");

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json")
                            .stop(); // use stop if you dont need to do anything after rest-dsl

                    from("direct:getPetById").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();

            given().when().get("/api/v3/pet/123").then().statusCode(200).body(equalTo("{\"pet\": \"tony the tiger\"}"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiMock() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=mock")
                            .log("dummy");

                    from("direct:getPetById").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();

            given().when().get("/api/v3/pet/123").then().statusCode(200).body(equalTo("{\"pet\": \"tony the tiger\"}"));

            // mocked gives empty response
            given().when().get("/api/v3/pet/findByTags").then().statusCode(204).body(equalTo(""));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiMissingOperation() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=fail")
                            .log("dummy");

                    from("direct:getPetById").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();
            fail();
        } catch (Exception e) {
            Assertions.assertTrue(e.getCause()
                    .getMessage()
                    .startsWith("OpenAPI specification has 18 unmapped operations to corresponding routes"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiNotFound() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore")
                            .log("dummy");

                    from("direct:getPetById").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();

            given().when().get("/api/v3/pet/123/unknown").then().statusCode(404);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiNotAllowed() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore")
                            .log("dummy");

                    from("direct:getPetById").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();

            given().when().put("/api/v3/pet/123").then().statusCode(405);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiValidate() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore&clientRequestValidation=true")
                            .log("dummy");

                    from("direct:updatePet").setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            context.start();

            given().when().put("/api/v3/pet").then().statusCode(400); // no request body
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiMockData() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=mock")
                            .log("dummy");
                }
            });

            context.start();

            given().when()
                    .contentType("application/json")
                    .get("/api/v3/pet/444")
                    .then()
                    .statusCode(200)
                    .body(
                            equalToCompressingWhiteSpace(
                                    """
                                    {
                                      "pet": "donald the dock"
                                    }"""));
        } finally {
            context.stop();
        }
    }
}
