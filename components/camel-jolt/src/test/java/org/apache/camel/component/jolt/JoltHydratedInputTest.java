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
package org.apache.camel.component.jolt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.joltcommunity.jolt.exception.JsonUnmarshalException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The default inputType (Hydrated) parses a body that is JSON text, bytes, a stream or a file.
 */
public class JoltHydratedInputTest extends CamelTestSupport {

    private static final String JSON = "{\"name\": \"Camel\"}";

    @TempDir
    Path tempDir;

    @Test
    public void testJsonString() {
        assertFullName("Camel", template.requestBody("direct:shiftr", JSON));
    }

    @Test
    public void testJsonStringWithLeadingWhitespace() {
        assertFullName("Camel", template.requestBody("direct:shiftr", " \n\t" + JSON));
    }

    @Test
    public void testJsonStringBuilder() {
        assertFullName("Camel", template.requestBody("direct:shiftr", new StringBuilder(JSON)));
    }

    @Test
    public void testJsonArrayString() {
        Object out = template.requestBody("direct:sortr", "[\"b\", \"a\"]");
        assertEquals(List.of("b", "a"), assertInstanceOf(List.class, out));
    }

    @Test
    public void testBytes() {
        assertFullName("Camel", template.requestBody("direct:shiftr", JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testBytesWithExchangeCharset() {
        String json = "{\"name\": \"København\"}";
        Object out = template.requestBodyAndHeader("direct:shiftr", json.getBytes(StandardCharsets.ISO_8859_1),
                Exchange.CHARSET_NAME, "ISO-8859-1");
        assertFullName("København", out);
    }

    @Test
    public void testInputStream() {
        assertFullName("Camel",
                template.requestBody("direct:shiftr", new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void testStreamCache() {
        assertFullName("Camel",
                template.requestBody("direct:shiftr", new InputStreamCache(JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void testFileAndPath() throws Exception {
        Path path = tempDir.resolve("input.json");
        Files.writeString(path, JSON);
        assertFullName("Camel", template.requestBody("direct:shiftr", path));
        File file = path.toFile();
        assertFullName("Camel", template.requestBody("direct:shiftr", file));
    }

    @Test
    public void testMap() {
        assertFullName("Camel", template.requestBody("direct:shiftr", Map.of("name", "Camel")));
    }

    @Test
    public void testTextThatIsNotJsonIsKept() {
        assertEquals("hello", template.requestBody("direct:sortr", "hello"));
    }

    @Test
    public void testInvalidJson() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:shiftr", "{\"name\": "));
        assertInstanceOf(JsonUnmarshalException.class, e.getCause());
    }

    private static void assertFullName(String expected, Object out) {
        Map<?, ?> map = assertInstanceOf(Map.class, out);
        assertEquals(expected, map.get("fullName"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:shiftr")
                        .to("jolt:org/apache/camel/component/jolt/shiftr.json?transformDsl=Shiftr");
                from("direct:sortr")
                        .to("jolt:dummy?transformDsl=Sortr");
            }
        };
    }

}
