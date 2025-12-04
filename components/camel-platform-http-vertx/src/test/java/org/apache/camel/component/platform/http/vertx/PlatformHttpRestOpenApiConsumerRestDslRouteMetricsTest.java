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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformHttpRestOpenApiConsumerRestDslRouteMetricsTest {

    @Test
    public void testRouteMetrics() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    rest().openApi()
                            .specification("openapi-v3.json")
                            .missingOperation("ignore")
                            .routeId("myRest");

                    from("direct:getPetById")
                            .routeId("getPetById")
                            .process(e -> {
                                assertEquals("123", e.getMessage().getHeader("petId"));
                            })
                            .setBody()
                            .constant("{\"pet\": \"tony the tiger\"}");

                    from("direct:findPetsByStatus")
                            .routeId("findPetsByStatus")
                            .process(e -> {
                                assertEquals("sold", e.getMessage().getHeader("status"));
                            })
                            .setBody()
                            .constant("{\"pet\": \"jack the lion\"}");
                }
            });

            context.start();

            ManagedRouteMBean mr = context.getCamelContextExtension()
                    .getContextPlugin(ManagedCamelContext.class)
                    .getManagedRoute("myRest");
            assertNotNull(mr);
            ManagedRouteMBean mr2 = context.getCamelContextExtension()
                    .getContextPlugin(ManagedCamelContext.class)
                    .getManagedRoute("getPetById");
            assertNotNull(mr2);
            ManagedRouteMBean mr3 = context.getCamelContextExtension()
                    .getContextPlugin(ManagedCamelContext.class)
                    .getManagedRoute("findPetsByStatus");
            assertNotNull(mr3);

            Assertions.assertEquals(0, mr.getExchangesTotal());
            Assertions.assertEquals(0, mr2.getExchangesTotal());
            Assertions.assertEquals(0, mr3.getExchangesTotal());

            given().when().get("/api/v3/pet/123").then().statusCode(200).body(equalTo("{\"pet\": \"tony the tiger\"}"));

            Assertions.assertEquals(1, mr.getExchangesTotal());
            Assertions.assertEquals(1, mr2.getExchangesTotal());
            Assertions.assertEquals(0, mr3.getExchangesTotal());

            given().when()
                    .get("/api/v3/pet/findByStatus?status=sold")
                    .then()
                    .statusCode(200)
                    .body(equalTo("{\"pet\": \"jack the lion\"}"));

            Assertions.assertEquals(2, mr.getExchangesTotal());
            Assertions.assertEquals(1, mr2.getExchangesTotal());
            Assertions.assertEquals(1, mr3.getExchangesTotal());

        } finally {
            context.stop();
        }
    }
}
