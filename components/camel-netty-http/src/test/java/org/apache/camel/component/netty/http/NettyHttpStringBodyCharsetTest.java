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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A String body is written in the charset that the Content-Type declares, and the charset parameter is recognized
 * whatever its case.
 */
public class NettyHttpStringBodyCharsetTest extends BaseNettyTestSupport {

    private static final String TEXT = "Grüße aus Köln";

    @Test
    public void testResponseInDeclaredCharset() throws Exception {
        HttpResponse<byte[]> response = send("/response", "text/plain", TEXT.getBytes(StandardCharsets.UTF_8));

        assertEquals("text/plain; charset=ISO-8859-1", response.headers().firstValue("Content-Type").orElse(null));
        assertArrayEquals(TEXT.getBytes(StandardCharsets.ISO_8859_1), response.body());
    }

    @Test
    public void testResponseWithoutCharsetIsUtf8() throws Exception {
        HttpResponse<byte[]> response = send("/plain", "text/plain", TEXT.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(TEXT.getBytes(StandardCharsets.UTF_8), response.body());
    }

    @Test
    public void testRequestCharsetParameterIgnoresCase() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:text");
        mock.expectedBodiesReceived(TEXT);

        send("/text", "text/plain; Charset=ISO-8859-1", TEXT.getBytes(StandardCharsets.ISO_8859_1));

        mock.assertIsSatisfied();
    }

    @Test
    public void testProducerRequestInDeclaredCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bytes");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("netty-http:http://localhost:{{port}}/bytes", TEXT, Exchange.CONTENT_TYPE,
                "text/plain; charset=ISO-8859-1");

        mock.assertIsSatisfied();
        assertArrayEquals(TEXT.getBytes(StandardCharsets.ISO_8859_1),
                mock.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
    }

    @Test
    public void testCharsetFromContentType() {
        assertEquals("ISO-8859-1", NettyHttpHelper.getCharsetFromContentType("text/plain; Charset=ISO-8859-1"));
        assertEquals("UTF-8", NettyHttpHelper.getCharsetFromContentType("text/plain;charset=\"UTF-8\""));
        assertEquals("utf-16", NettyHttpHelper.getCharsetFromContentType("text/plain; format=flowed; CHARSET=utf-16"));
        // no charset parameter: no charset (not a UTF-8 default), so the exchange charset is used as before
        assertNull(NettyHttpHelper.getCharsetFromContentType("text/plain"));
        assertNull(NettyHttpHelper.getCharsetFromContentType("multipart/form-data; boundary=charset"));
        assertNull(NettyHttpHelper.getCharsetFromContentType(null));
        // an empty charset parameter is no charset
        assertNull(NettyHttpHelper.getCharsetFromContentType("text/plain; charset="));
        assertNull(NettyHttpHelper.getCharsetFromContentType("text/plain; charset=\"\""));
    }

    @Test
    public void testRequestWithEmptyCharsetParameter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:text");
        mock.expectedBodiesReceived(TEXT);

        // an empty charset is ignored: no CamelCharsetName is set, and the body is read as before
        send("/text", "text/plain; charset=", TEXT.getBytes(StandardCharsets.UTF_8));

        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getProperty(Exchange.CHARSET_NAME));
    }

    private HttpResponse<byte[]> send(String path, String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/response")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=ISO-8859-1"))
                        .setBody(constant(TEXT));

                from("netty-http:http://localhost:{{port}}/plain")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody(constant(TEXT));

                from("netty-http:http://localhost:{{port}}/text")
                        .convertBodyTo(String.class)
                        .to("mock:text");

                from("netty-http:http://localhost:{{port}}/bytes")
                        .convertBodyTo(byte[].class)
                        .to("mock:bytes");
            }
        };
    }
}
