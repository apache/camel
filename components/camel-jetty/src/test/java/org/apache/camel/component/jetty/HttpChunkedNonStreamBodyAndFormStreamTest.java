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

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chunked response of a body that is not a stream (a Map, List or number) is written as its String form, as a
 * non-chunked one is, and a form POST keeps its body when stream caching is disabled.
 */
public class HttpChunkedNonStreamBodyAndFormStreamTest extends BaseJettyTest {

    @Test
    public void chunkedMapResponse() {
        assertEquals("{a=1}", template.requestBody("http://localhost:{{port}}/map", "Hello", String.class));
    }

    @Test
    public void chunkedListResponse() {
        assertEquals("[a, b]", template.requestBody("http://localhost:{{port}}/list", "Hello", String.class));
    }

    @Test
    public void chunkedNumberResponse() {
        assertEquals("42", template.requestBody("http://localhost:{{port}}/number", "Hello", String.class));
    }

    @Test
    public void chunkedMapResponseOfGetRequest() {
        assertEquals("{a=1}", template.requestBodyAndHeader("http://localhost:{{port}}/map", null,
                Exchange.HTTP_METHOD, HttpMethods.GET, String.class));
    }

    @Test
    public void notChunkedMapResponse() {
        assertEquals("{a=1}", template.requestBody("http://localhost:{{port}}/mapNotChunked", "Hello", String.class));
    }

    @Test
    public void formPostWithStreamCacheDisabled() {
        String out = template.requestBodyAndHeaders("http://localhost:{{port}}/form", "b1=x&b2=y",
                Map.of(Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded", Exchange.HTTP_METHOD, HttpMethods.POST),
                String.class);
        assertEquals("b1=x&b2=y x y", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // set the body on the HTTP message itself, as a processor or bean does (the setBody EIP replaces the
                // HTTP message with a plain message)
                from("jetty://http://localhost:{{port}}/map").process(e -> e.getMessage().setBody(Map.of("a", 1)));
                from("jetty://http://localhost:{{port}}/list").process(e -> e.getMessage().setBody(List.of("a", "b")));
                from("jetty://http://localhost:{{port}}/number").process(e -> e.getMessage().setBody(42));
                from("jetty://http://localhost:{{port}}/mapNotChunked?chunked=false")
                        .process(e -> e.getMessage().setBody(Map.of("a", 1)));
                from("jetty://http://localhost:{{port}}/form?disableStreamCache=true")
                        .setBody(simple("${bodyAs(String)} ${header.b1} ${header.b2}"));
            }
        };
    }
}
