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
package org.apache.camel.component.rest.openapi.validator.client;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Integration test that verifies the OpenAPI validator works end-to-end with real HTTP requests through Camel REST DSL
 * routes. The {@code camel-openapi-validator} is auto-discovered from the classpath and used instead of the default
 * validator, providing full OpenAPI schema validation via the Atlassian Swagger Request Validator library.
 */
public class OpenApiValidatorIntegrationTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Test
    public void testValidRequestSucceeds() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    rest().clientRequestValidation(true)
                            .openApi().specification("petstore-v3.json").missingOperation("ignore");

                    from("direct:updatePet")
                            .setBody().constant("{\"name\": \"tiger\", \"photoUrls\": [\"img.jpg\"]}");
                }
            });
            context.start();

            given().port(port.getPort())
                    .contentType("application/json")
                    .body("{\"name\": \"tiger\", \"photoUrls\": [\"img.jpg\"]}")
                    .when()
                    .put("/api/v3/pet")
                    .then()
                    .statusCode(200);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testMissingRequiredBodyReturns400() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    rest().clientRequestValidation(true)
                            .openApi().specification("petstore-v3.json").missingOperation("ignore");

                    from("direct:updatePet")
                            .setBody().constant("should not reach here");
                }
            });
            context.start();

            given().port(port.getPort())
                    .contentType("application/json")
                    .when()
                    .put("/api/v3/pet")
                    .then()
                    .statusCode(400)
                    .body(containsString("A request body is required but none found"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testInvalidBodySchemaReturns400() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    rest().clientRequestValidation(true)
                            .openApi().specification("petstore-v3.json").missingOperation("ignore");

                    from("direct:updatePet")
                            .setBody().constant("should not reach here");
                }
            });
            context.start();

            given().port(port.getPort())
                    .contentType("application/json")
                    .body("{\"name\": \"tiger\"}")
                    .when()
                    .put("/api/v3/pet")
                    .then()
                    .statusCode(400)
                    .body(containsString("photoUrls"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testMissingRequiredQueryParamReturns400() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    rest().clientRequestValidation(true)
                            .openApi().specification("petstore-v3.json").missingOperation("ignore");

                    from("direct:findPetsByStatus")
                            .setBody().constant("[]");
                }
            });
            context.start();

            given().port(port.getPort())
                    .when()
                    .get("/api/v3/pet/findByStatus")
                    .then()
                    .statusCode(400)
                    .body(containsString("status"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testMissingRequiredHeaderReturns400() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    rest().clientRequestValidation(true)
                            .openApi().specification("petstore-v3.json").missingOperation("ignore");

                    from("direct:findPetsByTags")
                            .setBody().constant("[]");
                }
            });
            context.start();

            given().port(port.getPort())
                    .when()
                    .get("/api/v3/pet/findByTags")
                    .then()
                    .statusCode(400)
                    .body(containsString("tags"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testValidationLevelOverride() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration()
                            .clientRequestValidation(true)
                            .validationLevelProperty("validation.request.body.schema.required", "INFO");

                    rest().openApi().specification("petstore-v3.json").missingOperation("ignore");

                    from("direct:updatePet")
                            .setBody().constant("{\"name\": \"tiger\"}");
                }
            });
            context.start();

            // Body missing required "photoUrls" field, but the validation level for
            // schema.required is downgraded to INFO, so the request passes.
            given().port(port.getPort())
                    .contentType("application/json")
                    .body("{\"name\": \"tiger\"}")
                    .when()
                    .put("/api/v3/pet")
                    .then()
                    .statusCode(200);
        } finally {
            context.stop();
        }
    }

    private CamelContext createCamelContext() throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port.getPort());
        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }
}
