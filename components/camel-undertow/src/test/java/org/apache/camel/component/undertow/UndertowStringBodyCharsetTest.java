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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A String body is written in the charset that the Content-Type declares, otherwise as UTF-8.
 */
public class UndertowStringBodyCharsetTest extends BaseUndertowTest {

    private static final String TEXT = "Grüße aus Köln";
    private static final String LATIN1 = "text/plain; charset=ISO-8859-1";
    // parameter names are case-insensitive (RFC 9110)
    private static final String LATIN1_UPPER_CASE = "text/plain; Charset=ISO-8859-1";

    @Test
    public void testResponseUsesCharsetOfContentType() throws Exception {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("latin1")).GET().build());

        assertEquals(LATIN1, response.headers().firstValue("Content-Type").orElse(null));
        assertArrayEquals(TEXT.getBytes(ISO_8859_1), response.body());
    }

    @Test
    public void testEchoUsesCharsetOfRequest() throws Exception {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("echo"))
                .header("Content-Type", LATIN1)
                .POST(HttpRequest.BodyPublishers.ofByteArray(TEXT.getBytes(ISO_8859_1))).build());

        assertArrayEquals(TEXT.getBytes(ISO_8859_1), response.body());
    }

    @Test
    public void testResponseWithoutCharsetIsUtf8() throws Exception {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("utf8")).GET().build());

        assertArrayEquals(TEXT.getBytes(UTF_8), response.body());
    }

    @Test
    public void testResponseWithoutCharsetToLatin1RequestIsUtf8() throws Exception {
        // the request charset (CamelCharsetName) must not be used for a response that does not declare it
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("noCharset"))
                .header("Content-Type", LATIN1)
                .POST(HttpRequest.BodyPublishers.ofByteArray(TEXT.getBytes(ISO_8859_1))).build());

        assertEquals("text/plain", response.headers().firstValue("Content-Type").orElse(null));
        assertArrayEquals(TEXT.getBytes(UTF_8), response.body());
    }

    @Test
    public void testProducerUsesCharsetOfContentType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("undertow:http://localhost:{{port}}/received", TEXT, Exchange.CONTENT_TYPE, LATIN1);

        MockEndpoint.assertIsSatisfied(context);
        assertArrayEquals(TEXT.getBytes(ISO_8859_1), mock.getExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void testResponseUsesCharsetParameterInAnyCase() throws Exception {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("latin1UpperCase")).GET().build());

        assertEquals(LATIN1_UPPER_CASE, response.headers().firstValue("Content-Type").orElse(null));
        assertArrayEquals(TEXT.getBytes(ISO_8859_1), response.body());
    }

    @Test
    public void testRequestUsesCharsetParameterInAnyCase() throws Exception {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("decode"))
                .header("Content-Type", LATIN1_UPPER_CASE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(TEXT.getBytes(ISO_8859_1))).build());

        assertArrayEquals(TEXT.getBytes(UTF_8), response.body());
    }

    @Test
    public void testProducerUsesCharsetParameterInAnyCase() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("undertow:http://localhost:{{port}}/received", TEXT, Exchange.CONTENT_TYPE,
                LATIN1_UPPER_CASE);

        MockEndpoint.assertIsSatisfied(context);
        assertArrayEquals(TEXT.getBytes(ISO_8859_1), mock.getExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    public void testRequestWithoutCharsetDoesNotSetCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedMessageCount(1);

        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("received"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofByteArray(TEXT.getBytes(UTF_8))).build());

        // only a charset that the request declares is set, otherwise Camel uses its default
        assertEquals(200, response.statusCode());
        MockEndpoint.assertIsSatisfied(context);
        Exchange received = mock.getExchanges().get(0);
        assertNull(received.getProperty(ExchangePropertyKey.CHARSET_NAME));
        assertNull(received.getIn().getHeader(UndertowConstants.HTTP_CHARACTER_ENCODING));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + getPort() + "/" + path);
    }

    private static HttpResponse<byte[]> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/latin1")
                        .setHeader(Exchange.CONTENT_TYPE, constant(LATIN1))
                        .setBody(constant(TEXT));

                from("undertow:http://localhost:{{port}}/latin1UpperCase")
                        .setHeader(Exchange.CONTENT_TYPE, constant(LATIN1_UPPER_CASE))
                        .setBody(constant(TEXT));

                // decodes the request in its charset and answers in UTF-8
                from("undertow:http://localhost:{{port}}/decode")
                        .convertBodyTo(String.class)
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=UTF-8"));

                from("undertow:http://localhost:{{port}}/echo")
                        .convertBodyTo(String.class);

                from("undertow:http://localhost:{{port}}/noCharset")
                        .convertBodyTo(String.class)
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"));

                from("undertow:http://localhost:{{port}}/utf8")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody(constant(TEXT));

                from("undertow:http://localhost:{{port}}/received")
                        .convertBodyTo(byte[].class)
                        .to("mock:received")
                        .setBody(constant("OK"));
            }
        };
    }
}
