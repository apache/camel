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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;

/**
 * Simulated reproducer for CAMEL-24594: when the REST DSL response binding (bindingMode json) fails to marshal the
 * response body, the failure must be surfaced to the caller as a server error (HTTP 500) - it must NOT be silently
 * returned as an HTTP 200 with an empty body.
 * <p/>
 * As {@code muteException} is enabled by default on platform-http, the response body must stay empty so no exception or
 * error message is leaked to the caller.
 * <p/>
 * The marshalling failure is simulated with a POJO whose getter throws, which makes Jackson fail during serialization
 * regardless of the configured {@code ObjectMapper} (no dependency on java.time / JavaTimeModule required).
 */
public class VertxRestBindingMarshalFailureTest {

    @Test
    public void testResponseMarshalFailureIsNotSilent200() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().bindingMode(RestBindingMode.json);

                    rest("/demo")
                            .get("/{id}").to("direct:demo");

                    from("direct:demo")
                            // the response POJO cannot be marshalled to json
                            .process(e -> e.getMessage().setBody(new BadPojo()));
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/demo/42")
                    .then()
                    // a serialization failure must surface as a server error, with no exception/message leaked
                    .statusCode(500)
                    .body(emptyString());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testFailureOriginatesInRestDslBindingMarshal() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().bindingMode(RestBindingMode.json)
                            // disable muting so the stacktrace is returned - only to PROVE where the failure happened
                            .endpointProperty("muteException", "false");

                    rest("/demo")
                            .get("/{id}").to("direct:demo");

                    from("direct:demo")
                            .process(e -> e.getMessage().setBody(new BadPojo()));
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/demo/42")
                    .then()
                    .statusCode(500)
                    // the thrown getter exception was surfaced
                    .body(containsString("Cannot serialize this POJO on purpose"))
                    // ...and it happened while the REST DSL binding marshalled the response body
                    .body(containsString("RestBindingAdvice"))
                    .body(containsString("MarshalProcessor"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testExplicitResponseCodeStillWinsOnMarshalFailure() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().bindingMode(RestBindingMode.json);

                    rest("/demo")
                            .get("/{id}").to("direct:demo");

                    from("direct:demo")
                            // an explicitly set response code must still win, even though the response marshalling
                            // fails afterwards (the failure is logged server-side, but does not override the code)
                            .process(e -> {
                                e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                                e.getMessage().setBody(new BadPojo());
                            });
                }
            });

            VertxPlatformHttpEngineTest.startCamelContext(context);

            given()
                    .when()
                    .get("/demo/42")
                    .then()
                    // the explicitly set 200 wins over the marshal failure, consistent with plain routes
                    .statusCode(200)
                    .body(emptyString());
        } finally {
            context.stop();
        }
    }

    /**
     * A POJO that always fails to marshal because its getter throws.
     */
    public static class BadPojo {
        public String getName() {
            throw new IllegalStateException("Cannot serialize this POJO on purpose");
        }
    }
}
