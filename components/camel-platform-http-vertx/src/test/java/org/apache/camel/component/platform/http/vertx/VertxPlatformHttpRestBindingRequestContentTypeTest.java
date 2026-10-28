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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * CAMEL-25769: the Content-Type of a request describes its body, not the answer. Under json binding, a GET sent with
 * Content-Type text/plain gets its object answer as JSON, as a GET without one does.
 */
public class VertxPlatformHttpRestBindingRequestContentTypeTest {

    private CamelContext context;

    @BeforeEach
    public void startCamel() throws Exception {
        context = VertxPlatformHttpEngineTest.createCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().bindingMode(RestBindingMode.json);
                rest("/orders")
                        .get("/map").to("direct:map")
                        .get("/text").to("direct:text")
                        .get("/csv").to("direct:csv");
                from("direct:map").setBody(constant(Map.of("orderId", "ORD-1002", "customer", "C-207")));
                from("direct:text").setBody(constant("plain answer"));
                from("direct:csv").setHeader(Exchange.CONTENT_TYPE, constant("text/csv")).setBody(constant("ORD-1002;C-207"));
            }
        });
        VertxPlatformHttpEngineTest.startCamelContext(context);
    }

    @AfterEach
    public void stopCamel() {
        context.stop();
    }

    @Test
    public void testAnObjectAnswerIsJsonWhateverTheContentTypeOfTheRequest() {
        // was a 500: the Map could not be written as text/plain
        given().contentType("text/plain;charset=UTF-8")
                .when().get("/orders/map")
                .then().statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("orderId", equalTo("ORD-1002"));
        given().contentType("application/json")
                .when().get("/orders/map")
                .then().statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("orderId", equalTo("ORD-1002"));
        given()
                .when().get("/orders/map")
                .then().statusCode(200)
                .header("Content-Type", containsString("application/json"))
                .body("orderId", equalTo("ORD-1002"));
    }

    @Test
    public void testATextAnswerToATextRequestStaysText() {
        given().contentType("text/plain;charset=UTF-8")
                .when().get("/orders/text")
                .then().statusCode(200)
                .header("Content-Type", containsString("text/plain"))
                .body(equalTo("plain answer"));
    }

    @Test
    public void testTheContentTypeTheRouteSetsWins() {
        given().contentType("text/plain;charset=UTF-8")
                .when().get("/orders/csv")
                .then().statusCode(200)
                .header("Content-Type", containsString("text/csv"))
                .body(equalTo("ORD-1002;C-207"));
    }
}
