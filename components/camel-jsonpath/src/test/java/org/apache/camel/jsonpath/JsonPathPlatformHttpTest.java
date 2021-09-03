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
package org.apache.camel.jsonpath;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonPathPlatformHttpTest extends CamelTestSupport {

    public static final String BODY = "{\"room\":{\"temperature\":30}}";
    public static final String JSON_PATH = "$.room[?(@.temperature > 20)]";
    public static final String RESULT = "HOT";

    private static final int PORT = AvailablePortFinder.getNextAvailable();

    @Test
    public void testWithPlatformHttp() {
        String result = RestAssured.given()
                .port(PORT)
                .contentType(ContentType.JSON)
                .body(BODY)
                .post("/getTemperature")
                .then()
                .statusCode(200)
                .extract().asString();

        assertEquals(RESULT, result);
    }

    @Test
    public void testWithoutPlatformHttp() {
        String result = template.requestBody("direct:getTemperature", BODY, String.class);

        assertEquals(RESULT, result);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(PORT);

        context.addService(new VertxPlatformHttpServer(conf));

        return context;

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                addRoute("platform-http:/getTemperature");

                addRoute("direct:getTemperature");
            }

            private ProcessorDefinition<?> addRoute(String from) {
                return from(from).choice().when().jsonpath(JSON_PATH).setBody(simple("HOT"))
                        .otherwise().setBody(constant("WARM")).end();
            }
        };
    }

}
