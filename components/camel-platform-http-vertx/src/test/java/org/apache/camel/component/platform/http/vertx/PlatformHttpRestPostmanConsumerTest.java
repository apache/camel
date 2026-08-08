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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

/**
 * Contract-first consumer driven by a Postman Collection.
 */
class PlatformHttpRestPostmanConsumerTest {

    @Test
    void shouldServeARequestOfTheCollection() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json?missingRequest=ignore")
                            .to("mock:result");

                    from("direct:getPetById")
                            .setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);

            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"tony the tiger\"}"));

            mock.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldMapPathParametersToHeaders() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json?missingRequest=ignore").stop();

                    from("direct:getPetById")
                            .setBody().simple("pet=${header.petId}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("pet=123"));
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldReturn404ForAPathTheCollectionDoesNotDescribe() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json?missingRequest=ignore").stop();

                    from("direct:getPetById").setBody().constant("ok");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/api/v3/unknown")
                    .then()
                    .statusCode(404);
        } finally {
            context.stop();
        }
    }

    /**
     * Note that for a path the collection does describe, the 405 is produced by the vert.x router itself, because the
     * path was registered with only the verbs the collection uses. The router does not populate {@code Allow}, so this
     * asserts the status only.
     */
    @Test
    void shouldReturn405ForAKnownPathOnTheWrongMethod() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json?missingRequest=ignore").stop();

                    from("direct:getPetById").setBody().constant("ok");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .delete("/api/v3/pet")
                    .then()
                    .statusCode(405);
        } finally {
            context.stop();
        }
    }

    /**
     * The default refuses to start rather than silently serving a collection whose requests go nowhere.
     */
    @Test
    void shouldFailToStartWhenARequestHasNoRoute() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json").stop();
                }
            });

            assertThatThrownBy(() -> VertxPlatformHttpEngineTest.startCamelContext(context))
                    .rootCause()
                    .hasMessageContaining("not mapped to a corresponding route")
                    .hasMessageContaining("direct:getPetById");
        } finally {
            context.stop();
        }
    }

    /**
     * Saved example responses in the collection are real recorded responses, so they make better mocks than anything
     * that could be generated.
     */
    @Test
    void shouldMockFromASavedExampleResponse() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json?missingRequest=mock").stop();
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"mocked tiger\"}"));
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldServeTheCollectionWithCredentialsRedacted() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json"
                         + "?missingRequest=ignore&apiContextPath=/collection").stop();

                    from("direct:getPetById").setBody().constant("ok");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            String document = given()
                    .when()
                    .get("/api/v3/collection")
                    .then()
                    .statusCode(200)
                    .extract().body().asString();

            assertThat(document).contains("Petstore").contains("Get Pet By Id");
            // the bearer token and the secret variable must not be published
            assertThat(document).doesNotContain("s3cr3t").doesNotContain("\"auth\"");
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldServeOnlyTheRequestsSelectedByTheFilter() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-postman:classpath:postman-petstore.json?requestFilter=getPetById").stop();

                    from("direct:getPetById").setBody().constant("ok");
                }
            });

            // starting proves validation passed even though addPet has no route, because it was filtered out
            VertxPlatformHttpEngineTest.startCamelContext(context);

            given().when().get("/api/v3/pet/123").then().statusCode(200).body(equalTo("ok"));
            given().when().post("/api/v3/pet").then().statusCode(404);
        } finally {
            context.stop();
        }
    }
}
