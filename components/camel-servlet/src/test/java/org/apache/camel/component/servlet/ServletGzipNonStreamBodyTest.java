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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A gzip response of a body that is not a stream or byte array (a Map, POJO or number) is written as its String form,
 * as a response that is not gzip is, and a String body is written in the charset of the content type.
 */
class ServletGzipNonStreamBodyTest extends ServletCamelRouterTestSupport {

    @Test
    void gzipMapResponse() throws Exception {
        assertGzipResponse("/map", "{a=1}", StandardCharsets.UTF_8);
    }

    @Test
    void gzipMapResponseNotChunked() throws Exception {
        assertGzipResponse("/mapNotChunked", "{a=1}", StandardCharsets.UTF_8);
    }

    @Test
    void gzipPojoResponse() throws Exception {
        assertGzipResponse("/pojo", "a pojo", StandardCharsets.UTF_8);
    }

    @Test
    void gzipNumberResponse() throws Exception {
        assertGzipResponse("/number", "42", StandardCharsets.UTF_8);
    }

    @Test
    void gzipStringResponseInContentTypeCharset() throws Exception {
        assertGzipResponse("/latin1", "Grüße", StandardCharsets.ISO_8859_1);
    }

    @Test
    void gzipMapResponseOfPostRequest() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/map", new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)),
                "text/plain");
        req.setHeaderField("Accept-Encoding", "gzip");
        WebResponse response = query(req, false);
        assertEquals(200, response.getResponseCode());
        assertEquals("gzip", response.getHeaderField("Content-Encoding"));
        assertEquals("{a=1}", gunzip(response.getInputStream(), StandardCharsets.UTF_8));
    }

    private void assertGzipResponse(String path, String expected, Charset charset) throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services" + path);
        req.setHeaderField("Accept-Encoding", "gzip");
        WebResponse response = query(req, false);
        assertEquals(200, response.getResponseCode());
        assertEquals("gzip", response.getHeaderField("Content-Encoding"));
        assertEquals(expected, gunzip(response.getInputStream(), charset));
    }

    private static String gunzip(InputStream is, Charset charset) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(is)) {
            return new String(gis.readAllBytes(), charset);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // set the body on the HTTP message itself, as a processor or bean does (the setBody EIP replaces the
                // HTTP message with a plain message)
                from("servlet:/map").process(e -> gzip(e, Map.of("a", 1)));
                from("servlet:/mapNotChunked?chunked=false").process(e -> gzip(e, Map.of("a", 1)));
                from("servlet:/pojo").process(e -> gzip(e, new MyPojo()));
                from("servlet:/number").process(e -> gzip(e, 42));
                from("servlet:/latin1").process(e -> {
                    gzip(e, "Grüße");
                    e.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain; charset=ISO-8859-1");
                });
            }
        };
    }

    private static void gzip(Exchange exchange, Object body) {
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_ENCODING, "gzip");
    }

    private static final class MyPojo {
        @Override
        public String toString() {
            return "a pojo";
        }
    }
}
