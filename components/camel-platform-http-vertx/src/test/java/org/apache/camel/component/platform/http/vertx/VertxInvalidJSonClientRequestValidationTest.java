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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

public class VertxInvalidJSonClientRequestValidationTest {

    @Test
    public void testInvalidJSon() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    restConfiguration()
                            .bindingMode(RestBindingMode.json)
                            // turn on request validation
                            .clientRequestValidation(true);

                    // use the rest DSL to define the rest services
                    rest("/users/")
                            .post("{id}/update2")
                            .consumes("application/json")
                            .produces("application/json")
                            .to("direct:update");
                    from("direct:update").setBody(constant("{ \"status\": \"ok\"}"));
                }
            });

            context.start();

            given().when()
                    .contentType("application/json")
                    .body("{\"name\": \"Donald\"") // the body is invalid as the ending } is missing
                    .post("/users/123/update2")
                    .then()
                    .statusCode(400)
                    .body(equalTo("Invalid JSon payload."));
        } finally {
            context.stop();
        }
    }
}
