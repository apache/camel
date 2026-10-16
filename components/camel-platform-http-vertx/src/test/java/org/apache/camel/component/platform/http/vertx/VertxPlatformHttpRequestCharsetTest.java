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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The charset parameter of the request Content-Type is read whatever the case of its name (RFC 9110, 5.6.6), and an
 * empty charset parameter counts as no charset (CAMEL-25355).
 */
class VertxPlatformHttpRequestCharsetTest {

    private static final String TEXT = "Grüße aus Köln";

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = VertxPlatformHttpEngineTest.createCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // decodes the request in its charset and answers in UTF-8
                from("platform-http:/decode")
                        .convertBodyTo(String.class)
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=UTF-8"));
            }
        });
        VertxPlatformHttpEngineTest.startCamelContext(context);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void testRequestCharsetParameterInUpperCase() throws Exception {
        HttpResponse<byte[]> response = post("text/plain; Charset=ISO-8859-1", TEXT.getBytes(ISO_8859_1));

        assertEquals(200, response.statusCode());
        assertArrayEquals(TEXT.getBytes(UTF_8), response.body());
    }

    @Test
    void testEmptyRequestCharsetParameter() throws Exception {
        // no charset: the body is decoded with the default charset (UTF-8) instead of failing on Charset.forName("")
        HttpResponse<byte[]> response = post("text/plain; charset=", TEXT.getBytes(UTF_8));

        assertEquals(200, response.statusCode());
        assertArrayEquals(TEXT.getBytes(UTF_8), response.body());
    }

    @Test
    void testEmptyQuotedRequestCharsetParameterFollowedByAnotherParameter() throws Exception {
        HttpResponse<byte[]> response = post("text/plain; charset=\"\"; format=flowed", TEXT.getBytes(UTF_8));

        assertEquals(200, response.statusCode());
        assertArrayEquals(TEXT.getBytes(UTF_8), response.body());
    }

    private HttpResponse<byte[]> post(String contentType, byte[] body) throws Exception {
        VertxPlatformHttpServer server = context.hasService(VertxPlatformHttpServer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/decode"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
