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

import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * A String response body must be written in the charset that the response Content-Type declares.
 */
public class VertxPlatformHttpResponseCharsetTest {

    private static final String TEXT = "Grüße aus Köln";

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = VertxPlatformHttpEngineTest.createCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/latin1")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=ISO-8859-1"))
                        .setBody(constant(TEXT));

                from("platform-http:/echo")
                        .convertBodyTo(String.class);

                from("platform-http:/default")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody(constant(TEXT));
            }
        });
        VertxPlatformHttpEngineTest.startCamelContext(context);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void testStringBodyWrittenInDeclaredCharset() {
        byte[] body = given()
                .get("/latin1")
                .then()
                .statusCode(200)
                .header("Content-Type", "text/plain; charset=ISO-8859-1")
                .extract().asByteArray();

        assertArrayEquals(TEXT.getBytes(StandardCharsets.ISO_8859_1), body);
    }

    @Test
    void testEchoInRequestCharset() {
        byte[] request = TEXT.getBytes(StandardCharsets.ISO_8859_1);
        byte[] body = given()
                .contentType("text/plain; charset=ISO-8859-1")
                .body(request)
                .post("/echo")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertArrayEquals(request, body);
    }

    @Test
    void testStringBodyWithoutCharsetIsUtf8() {
        byte[] body = given()
                .get("/default")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertArrayEquals(TEXT.getBytes(StandardCharsets.UTF_8), body);
    }
}
