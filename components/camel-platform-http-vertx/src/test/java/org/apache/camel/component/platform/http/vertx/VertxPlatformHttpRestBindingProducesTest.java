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

import java.io.ByteArrayInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Under json binding, a response without a Content-Type takes it from the verb's produces.
 */
public class VertxPlatformHttpRestBindingProducesTest {

    private static final byte[] PAYLOAD = { 'P', 'K', 3, 4, 0, 1, 2 };

    private CamelContext context;

    @BeforeEach
    public void setUp() throws Exception {
        context = VertxPlatformHttpEngineTest.createCamelContext();
        // let a json Accept reach a text/plain verb, as a client that always sends it does
        context.getComponent("platform-http", PlatformHttpComponent.class).setServerRequestValidation(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().bindingMode(RestBindingMode.json);

                rest("/files")
                        .get("/bytes").produces("application/octet-stream").to("direct:bytes")
                        .get("/stream").produces("application/octet-stream").to("direct:stream");
                rest("/text")
                        .get("/plain").produces("text/plain").to("direct:plain")
                        .get("/multi").produces("text/plain,application/json").to("direct:multi")
                        .get("/none").to("direct:none");

                from("direct:bytes").setBody(constant(PAYLOAD));
                from("direct:stream").process(e -> e.getMessage().setBody(new ByteArrayInputStream(PAYLOAD)));
                from("direct:plain").setBody(constant("ok"));
                from("direct:multi").setBody(constant("ok"));
                from("direct:none").setBody(constant("ok"));
            }
        });
        VertxPlatformHttpEngineTest.startCamelContext(context);
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void testBinaryBodyIsNotJsonMarshalled() {
        for (String path : new String[] { "/files/bytes", "/files/stream" }) {
            byte[] body = given()
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200)
                    .header("Content-Type", equalTo("application/octet-stream"))
                    .extract().asByteArray();
            assertArrayEquals(PAYLOAD, body, path);
        }
    }

    @Test
    public void testPlainProducesWinsOverJsonAccept() {
        given()
                .accept("application/json")
                .when()
                .get("/text/plain")
                .then()
                .statusCode(200)
                .header("Content-Type", startsWith("text/plain"))
                .body(equalTo("ok"));
    }

    @Test
    public void testMultiValueProducesPicksJson() {
        given()
                .when()
                .get("/text/multi")
                .then()
                .statusCode(200)
                .header("Content-Type", startsWith("application/json"))
                .body(equalTo("\"ok\""));
    }

    @Test
    public void testNoProducesFallsBackToBindingMode() {
        given()
                .when()
                .get("/text/none")
                .then()
                .statusCode(200)
                .header("Content-Type", startsWith("application/json"))
                .body(equalTo("\"ok\""));
    }
}
