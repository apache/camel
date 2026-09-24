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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RestOpenApiUnmatchedRequestHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests the {@code unmatchedRequestHandling} option of the rest-openapi consumer: when set to {@code camel} unmatched
 * requests (no operation in the OpenAPI specification matches) are routed to Camel and answered by the
 * {@code RestOpenApiUnmatchedRequestHandler}, otherwise they are answered by the HTTP layer (Vert.x).
 */
public class PlatformHttpRestOpenApiUnmatchedRequestHandlingTest {

    @Test
    public void testUnmatchedRequestAnsweredByPlatformByDefault() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        RecordingHandler handler = new RecordingHandler();
        // the handler must NOT be used as long as unmatchedRequestHandling defaults to platform
        context.getRegistry().bind("myHandler", handler);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore")
                            .to("mock:result");

                    from("direct:getPetById")
                            .setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            // matched requests are answered by Camel
            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"tony the tiger\"}"));
            mock.assertIsSatisfied();

            // unmatched requests are answered by Vert.x (the handler is not invoked)
            given()
                    .when()
                    .get("/api/v3/pet/123/unknown")
                    .then()
                    .statusCode(404);
            given()
                    .when()
                    .put("/api/v3/pet/123")
                    .then()
                    .statusCode(405);
            Assertions.assertEquals(0, handler.invocations.get());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testUnmatchedRequestAnsweredByCamelWithDefaultHandler() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore&unmatchedRequestHandling=camel")
                            .to("mock:result");

                    from("direct:getPetById")
                            .setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            // matched requests are still answered by Camel
            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"tony the tiger\"}"));
            mock.assertIsSatisfied();

            // unmatched requests reach Camel and are answered by the default unmatched request handler
            given()
                    .when()
                    .get("/api/v3/pet/123/unknown")
                    .then()
                    .statusCode(404)
                    .body(equalTo(""));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testUnmatchedRequestAnsweredByCustomHandlerWhenCamelHandling() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        RecordingHandler handler = new RecordingHandler();
        context.getRegistry().bind("myHandler", handler);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore&unmatchedRequestHandling=camel")
                            .to("mock:result");

                    from("direct:getPetById")
                            .setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            // matched requests are still answered by Camel
            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200);

            // unknown path is answered by the custom handler with 404
            given()
                    .when()
                    .get("/api/v3/does-not-exist")
                    .then()
                    .statusCode(404)
                    .body(equalTo("{\"error\":\"handled by camel\"}"));

            // known path with wrong method is answered by the custom handler with 405 and an Allow header
            given()
                    .when()
                    .put("/api/v3/pet/123")
                    .then()
                    .statusCode(405)
                    .header("Allow", equalTo("GET, POST, DELETE"))
                    .body(equalTo("{\"error\":\"handled by camel\"}"));

            Assertions.assertEquals(List.of(404, 405), handler.statusCodes.stream().toList());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testUnmatchedRequestHandlingViaRestDsl() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        RecordingHandler handler = new RecordingHandler();
        context.getRegistry().bind("myHandler", handler);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // same as setting unmatchedRequestHandling=camel on the rest-openapi endpoint uri
                    rest().openApi().specification("openapi-v3.json")
                            .missingOperation("ignore")
                            .unmatchedRequestHandling("camel");

                    from("direct:getPetById")
                            .setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200);

            // unmatched requests are routed to Camel and answered by the custom handler
            given()
                    .when()
                    .get("/api/v3/does-not-exist")
                    .then()
                    .statusCode(404)
                    .body(equalTo("{\"error\":\"handled by camel\"}"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCatchAllDoesNotShadowApiWithNestedBasePath() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        RecordingHandler handler = new RecordingHandler();
        context.getRegistry().bind("myHandler", handler);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // an API with a base path that is a prefix of the second API ("/api" of "/api/v3");
                    // started first, so its catch-all would shadow the second API without ordering it last
                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore&unmatchedRequestHandling=camel&basePath=/api")
                            .to("mock:resultBroad");

                    from("rest-openapi:classpath:openapi-v3.json?missingOperation=ignore&unmatchedRequestHandling=camel")
                            .to("mock:resultNested");

                    from("direct:getPetById")
                            .setBody().constant("{\"pet\": \"tony the tiger\"}");
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            // operations of the nested API are not swallowed by the catch-all of the broader API
            MockEndpoint nested = context.getEndpoint("mock:resultNested", MockEndpoint.class);
            nested.expectedMessageCount(1);
            given()
                    .when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"tony the tiger\"}"));
            nested.assertIsSatisfied();

            // unmatched requests are still routed to Camel and answered by the handler
            given()
                    .when()
                    .get("/api/v3/does-not-exist")
                    .then()
                    .statusCode(404)
                    .body(equalTo("{\"error\":\"handled by camel\"}"));
            given()
                    .when()
                    .get("/api/does-not-exist")
                    .then()
                    .statusCode(404)
                    .body(equalTo("{\"error\":\"handled by camel\"}"));

            // the broader API still answers its own operations
            MockEndpoint broad = context.getEndpoint("mock:resultBroad", MockEndpoint.class);
            broad.expectedMessageCount(1);
            given()
                    .when()
                    .get("/api/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"tony the tiger\"}"));
            broad.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    /**
     * Custom {@link RestOpenApiUnmatchedRequestHandler} that counts invocations and produces a fixed response body.
     */
    public static final class RecordingHandler implements RestOpenApiUnmatchedRequestHandler {

        final AtomicInteger invocations = new AtomicInteger();
        final List<Integer> statusCodes = new CopyOnWriteArrayList<>();

        @Override
        public void handle(Exchange exchange, int statusCode, List<String> allowedMethods) {
            statusCodes.add(statusCode);
            invocations.incrementAndGet();
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            if (!allowedMethods.isEmpty()) {
                exchange.getMessage().setHeader("Allow", String.join(", ", allowedMethods));
            }
            exchange.getMessage().setBody("{\"error\":\"handled by camel\"}");
        }
    }
}
