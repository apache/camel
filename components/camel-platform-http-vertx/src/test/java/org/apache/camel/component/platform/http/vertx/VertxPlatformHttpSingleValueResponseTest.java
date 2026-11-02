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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-26503: a number, a boolean, a character or an enum as the reply is written as its text, not in the binary form
 * of the type converter (an Integer as 4 bytes).
 */
public class VertxPlatformHttpSingleValueResponseTest {

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = VertxPlatformHttpEngineTest.createCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/integer")
                        .setBody(constant(42));

                from("platform-http:/long")
                        .setBody(constant(1234567890123L));

                from("platform-http:/double")
                        .setBody(constant(2.5d));

                from("platform-http:/boolean")
                        .setBody(constant(Boolean.TRUE));

                from("platform-http:/character")
                        .setBody(constant('x'));

                from("platform-http:/enum")
                        .setBody(constant(TimeUnit.SECONDS));

                from("platform-http:/latin1")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=ISO-8859-1"))
                        .setBody(constant('ü'));

                from("platform-http:/builder")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=ISO-8859-1"))
                        .process(exchange -> exchange.getMessage().setBody(new StringBuilder("Grüße")));
            }
        });
        VertxPlatformHttpEngineTest.startCamelContext(context);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void testIntegerReplyIsText() {
        assertEquals("42", get("/integer"));
    }

    @Test
    void testLongReplyIsText() {
        assertEquals("1234567890123", get("/long"));
    }

    @Test
    void testDoubleReplyIsText() {
        assertEquals("2.5", get("/double"));
    }

    @Test
    void testBooleanReplyIsText() {
        assertEquals("true", get("/boolean"));
    }

    @Test
    void testCharacterReplyIsText() {
        assertEquals("x", get("/character"));
    }

    @Test
    void testEnumReplyIsText() {
        assertEquals("SECONDS", get("/enum"));
    }

    @Test
    void testCharacterReplyInDeclaredCharset() {
        byte[] body = given()
                .get("/latin1")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertArrayEquals("ü".getBytes(StandardCharsets.ISO_8859_1), body);
    }

    @Test
    void testStringBuilderReplyInDeclaredCharset() {
        byte[] body = given()
                .get("/builder")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertArrayEquals("Grüße".getBytes(StandardCharsets.ISO_8859_1), body);
    }

    private static String get(String path) {
        byte[] body = given()
                .get(path)
                .then()
                .statusCode(200)
                .extract().asByteArray();
        return new String(body, StandardCharsets.UTF_8);
    }
}
