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
import org.apache.camel.component.platform.http.vertx.model.Pet;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

public class PlatformHttpRestOpenApiConsumerRestDslBindingTest {

    @Test
    public void testRestOpenApiOutType() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().bindingMode(RestBindingMode.json);

                    rest().openApi().specification("openapi-v3.json").missingOperation("ignore");

                    from("direct:getPetById").process(e -> {
                        // build response body as POJO
                        Pet pet = new Pet();
                        pet.setId(e.getMessage().getHeader("petId", long.class));
                        pet.setName("tony the tiger");
                        pet.setStatus(Pet.Status.AVAILABLE);
                        e.getMessage().setBody(pet);
                    });
                }
            });

            context.start();

            given().when()
                    .get("/api/v3/pet/123")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"id\":123,\"name\":\"tony the tiger\",\"status\":\"AVAILABLE\"}"));

        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiInType() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // turn on json binding and scan for POJO classes in the model package
                    restConfiguration()
                            .bindingMode(RestBindingMode.json)
                            .bindingPackageScan("org.apache.camel.component.platform.http.vertx.model");

                    rest().openApi().specification("openapi-v3.json").missingOperation("ignore");

                    from("direct:updatePet").process(e -> {
                        Pet pet = e.getMessage().getBody(Pet.class);
                        pet.setStatus(Pet.Status.PENDING);
                    });
                }
            });

            context.start();

            given().when()
                    .contentType("application/json")
                    .body("{\"id\":123,\"name\":\"tony the tiger\"}")
                    .put("/api/v3/pet")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"id\":123,\"name\":\"tony the tiger\",\"status\":\"PENDING\"}"));

        } finally {
            context.stop();
        }
    }
}
