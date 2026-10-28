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
package org.apache.camel.component.jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A request with an empty charset parameter in the Content-Type has no charset, so the exchange charset is not set to
 * an empty name and the body is read with the default charset (UTF-8).
 */
class JettyEmptyCharsetParameterTest extends BaseJettyTest {

    private static final String TEXT = "Grüße aus Köln";

    @Test
    void requestWithEmptyCharsetParameter() throws Exception {
        // Jetty itself rejects other empty forms such as charset="" (IllegalCharsetNameException in getContentType)
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + "/echo"))
                .header("Content-Type", "text/plain; charset=")
                .POST(HttpRequest.BodyPublishers.ofString(TEXT, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("Hello " + TEXT, response.body());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:{{port}}/echo")
                        .convertBodyTo(String.class)
                        .setBody(simple("Hello ${body}"));
            }
        };
    }
}
