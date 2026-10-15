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
package org.apache.camel.component.chunk;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that message data (body, headers, variables) that contains Chunk tags is rendered as literal text and is not
 * re-parsed and expanded as Chunk template code, while tag-free data keeps its normal behaviour (including Chunk's
 * string filters). Only the route author's template should be parsed as template code.
 */
class ChunkValueExpansionTest extends CamelTestSupport {

    @Test
    void bodyValueIsPrintedLiterally() {
        // The body itself contains Chunk markup; it must be printed as-is, not expanded.
        Exchange out = template.request("direct:body", e -> {
            e.getIn().setBody("{$headers.name}");
            e.getIn().setHeader("name", "World");
        });
        assertEquals("This is the body {$headers.name}\n", out.getMessage().getBody(String.class));
    }

    @Test
    void headerValueIsPrintedLiterally() {
        // A header value containing Chunk markup must be printed as-is, not expanded.
        Exchange out = template.request("direct:header", e -> {
            e.getIn().setHeader("value", "{$headers.name}");
            e.getIn().setHeader("name", "World");
        });
        assertEquals("Header is {$headers.name}\n", out.getMessage().getBody(String.class));
    }

    @Test
    void variableValueIsPrintedLiterally() {
        // A variable value containing Chunk markup must be printed as-is, not expanded.
        Exchange out = template.request("direct:var", e -> {
            e.setVariable("v", "{$headers.name}");
            e.getIn().setHeader("name", "World");
        });
        assertEquals("Var is {$headers.name}\n", out.getMessage().getBody(String.class));
    }

    @Test
    void mapBodyValueIsPrintedLiterally() {
        // A nested value inside a Map body containing Chunk markup must be printed as-is, not expanded.
        Exchange out = template.request("direct:bodykey", e -> {
            e.getIn().setBody(Map.of("key", "{$headers.name}"));
            e.getIn().setHeader("name", "World");
        });
        assertEquals("Body key is {$headers.name}\n", out.getMessage().getBody(String.class));
    }

    @Test
    void stringBuilderBodyIsPrintedLiterally() {
        // A non-String CharSequence body containing Chunk markup must be printed as-is, not expanded.
        Exchange out = template.request("direct:body", e -> {
            e.getIn().setBody(new StringBuilder("{$headers.name}"));
            e.getIn().setHeader("name", "World");
        });
        assertEquals("This is the body {$headers.name}\n", out.getMessage().getBody(String.class));
    }

    @Test
    void listOfMapsValueIsPrintedLiterally() {
        // A nested value inside a List of Maps (e.g. unmarshalled JSON) containing Chunk markup must be literal.
        Exchange out = template.request("direct:loopmaps", e -> {
            e.getIn().setBody(List.of(Map.of("k", "{$headers.name}")));
            e.getIn().setHeader("name", "World");
        });
        assertEquals("[{$headers.name}]", out.getMessage().getBody(String.class));
    }

    @Test
    void stringFiltersStillWorkOnPlainData() {
        // Tag-free data is left as a String, so Chunk's string filters behave as before.
        Exchange out = template.request("direct:slice", e -> e.getIn().setBody("Hello World"));
        assertEquals("Hello\n", out.getMessage().getBody(String.class));
    }

    @Test
    void normalRenderingStillWorks() {
        // Tags in the template are still resolved against the data as usual.
        Exchange out = template.request("direct:hello", e -> e.getIn().setHeader("name", "Andrew"));
        assertEquals("Earth to Andrew. Come in, Andrew.\n", out.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:body").to("chunk://nested");
                from("direct:header").to("chunk://headervalue");
                from("direct:var").to("chunk://varvalue");
                from("direct:bodykey").to("chunk://bodykey");
                from("direct:loopmaps").to("chunk://loopmaps");
                from("direct:slice").to("chunk://slicebody");
                from("direct:hello").to("chunk://hello");
            }
        };
    }
}
