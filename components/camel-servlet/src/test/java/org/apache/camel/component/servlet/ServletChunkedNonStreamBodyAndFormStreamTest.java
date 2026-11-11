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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chunked response of a body that is not a stream (a Map, List, POJO or number) is written as its String form, as a
 * non-chunked one is, and a form POST keeps its body when stream caching is disabled.
 */
class ServletChunkedNonStreamBodyAndFormStreamTest extends ServletCamelRouterTestSupport {

    @Test
    void chunkedMapResponse() throws Exception {
        assertResponse("/map", "{a=1}");
    }

    @Test
    void chunkedListResponse() throws Exception {
        assertResponse("/list", "[a, b]");
    }

    @Test
    void chunkedPojoResponse() throws Exception {
        assertResponse("/pojo", "a pojo");
    }

    @Test
    void chunkedNumberResponse() throws Exception {
        assertResponse("/number", "42");
    }

    @Test
    void chunkedMapResponseOfPostRequest() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/map", new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)),
                "text/plain");
        WebResponse response = query(req);
        assertEquals(200, response.getResponseCode());
        assertEquals("{a=1}", response.getText());
    }

    @Test
    void notChunkedMapResponse() throws Exception {
        assertResponse("/mapNotChunked", "{a=1}");
    }

    @Test
    void formPostWithStreamCacheDisabled() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/form", new ByteArrayInputStream("b1=x&b2=y".getBytes(StandardCharsets.UTF_8)),
                "application/x-www-form-urlencoded");
        WebResponse response = query(req);
        assertEquals(200, response.getResponseCode());
        assertEquals("b1=x&b2=y x y", response.getText());
    }

    private void assertResponse(String path, String expected) throws Exception {
        WebResponse response = query(new GetMethodWebRequest(contextUrl + "/services" + path));
        assertEquals(200, response.getResponseCode());
        assertEquals(expected, response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // set the body on the HTTP message itself, as a processor or bean does (the setBody EIP replaces the
                // HTTP message with a plain message)
                from("servlet:/map").process(e -> e.getMessage().setBody(Map.of("a", 1)));
                from("servlet:/list").process(e -> e.getMessage().setBody(List.of("a", "b")));
                from("servlet:/pojo").process(e -> e.getMessage().setBody(new MyPojo()));
                from("servlet:/number").process(e -> e.getMessage().setBody(42));
                from("servlet:/mapNotChunked?chunked=false").process(e -> e.getMessage().setBody(Map.of("a", 1)));
                from("servlet:/form?disableStreamCache=true")
                        .setBody(simple("${bodyAs(String)} ${header.b1} ${header.b2}"));
            }
        };
    }

    private static final class MyPojo {
        @Override
        public String toString() {
            return "a pojo";
        }
    }
}
