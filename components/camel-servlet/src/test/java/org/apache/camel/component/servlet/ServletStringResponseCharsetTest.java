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
package org.apache.camel.component.servlet;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A String response body must be written in the charset that its Content-Type declares, so a client that decodes the
 * response with that charset gets the text back.
 */
class ServletStringResponseCharsetTest extends ServletCamelRouterTestSupport {

    private static final String TEXT = "Grüße aus Köln";

    @Test
    void chunkedTextResponseInDeclaredCharset() throws Exception {
        assertEquals(TEXT, queryAndDecode("/chunked"));
    }

    @Test
    void jsonResponseInDeclaredCharset() throws Exception {
        assertEquals("{\"greeting\":\"" + TEXT + "\"}", queryAndDecode("/json"));
    }

    @Test
    void notChunkedTextResponseInDeclaredCharset() throws Exception {
        assertResponseInCharset("/notChunked", StandardCharsets.ISO_8859_1);
    }

    @Test
    void notChunkedTextResponseWithoutDeclaredCharset() throws Exception {
        // no charset declared: the charset of the exchange, UTF-8 for a GET request
        assertResponseInCharset("/notChunkedNoCharset", StandardCharsets.UTF_8);
    }

    private void assertResponseInCharset(String path, Charset expected) throws Exception {
        WebResponse response = query(new GetMethodWebRequest(contextUrl + "/services" + path));
        String contentType = response.getHeaderField("Content-Type");
        String charset = IOHelper.getCharsetNameFromContentType(contentType);
        assertEquals(expected, Charset.forName(charset), "charset of the response Content-Type " + contentType);
        byte[] expectedBytes = TEXT.getBytes(expected);
        assertEquals(String.valueOf(expectedBytes.length), response.getHeaderField("Content-Length"));
        try (InputStream is = response.getInputStream()) {
            assertArrayEquals(expectedBytes, is.readAllBytes());
        }
    }

    private String queryAndDecode(String path) throws Exception {
        WebResponse response = query(new GetMethodWebRequest(contextUrl + "/services" + path));
        String contentType = response.getHeaderField("Content-Type");
        String charset = IOHelper.getCharsetNameFromContentType(contentType);
        assertNotNull(charset, "charset of the response Content-Type " + contentType);
        try (InputStream is = response.getInputStream()) {
            return new String(is.readAllBytes(), Charset.forName(charset));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:/chunked")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=ISO-8859-1"))
                        .setBody(constant(TEXT));

                from("servlet:/json?chunked=false")
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json; charset=ISO-8859-1"))
                        .setBody(constant("{\"greeting\":\"" + TEXT + "\"}"));

                from("servlet:/notChunked?chunked=false")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain; charset=ISO-8859-1"))
                        .setBody(constant(TEXT));

                from("servlet:/notChunkedNoCharset?chunked=false")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody(constant(TEXT));
            }
        };
    }
}
