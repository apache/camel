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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A gzip response of a body that is not a stream or byte array (a Map, List or number) is written as its String form,
 * as a response that is not gzip is.
 */
public class HttpGzipNonStreamBodyTest extends BaseJettyTest {

    @Test
    public void gzipMapResponse() {
        assertEquals("{a=1}", template.requestBody("http://localhost:{{port}}/map", "Hello", String.class));
    }

    @Test
    public void gzipMapResponseNotChunked() {
        assertEquals("{a=1}", template.requestBody("http://localhost:{{port}}/mapNotChunked", "Hello", String.class));
    }

    @Test
    public void gzipNumberResponse() {
        assertEquals("42", template.requestBody("http://localhost:{{port}}/number", "Hello", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // set the body on the HTTP message itself, as a processor or bean does (the setBody EIP replaces the
                // HTTP message with a plain message)
                from("jetty://http://localhost:{{port}}/map").process(e -> gzip(e, Map.of("a", 1)));
                from("jetty://http://localhost:{{port}}/mapNotChunked?chunked=false").process(e -> gzip(e, Map.of("a", 1)));
                from("jetty://http://localhost:{{port}}/number").process(e -> gzip(e, 42));
            }
        };
    }

    private static void gzip(Exchange exchange, Object body) {
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_ENCODING, "gzip");
    }
}
