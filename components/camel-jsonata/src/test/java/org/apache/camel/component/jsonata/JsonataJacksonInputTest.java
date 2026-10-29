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
package org.apache.camel.component.jsonata;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
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
 * The default inputType (Jackson) parses a body that is JSON text, bytes, a stream or a file.
 */
class JsonataJacksonInputTest extends CamelTestSupport {

    private static final String JSON = "{\"name\": \"Camel\"}";
    private static final String FULL_NAME = "{\"fullName\":\"Camel\"}";

    @TempDir
    Path tempDir;

    @Test
    void testJsonString() {
        assertEquals(FULL_NAME, template.requestBody("direct:fullName", JSON, String.class));
    }

    @Test
    void testJsonStringWithLeadingWhitespace() {
        assertEquals(FULL_NAME, template.requestBody("direct:fullName", " \n\t" + JSON, String.class));
    }

    @Test
    void testJsonStringBuilder() {
        assertEquals(FULL_NAME, template.requestBody("direct:fullName", new StringBuilder(JSON), String.class));
    }

    @Test
    void testJsonArrayString() {
        assertEquals("3", template.requestBody("direct:count", "[1, 2, 3]", String.class));
    }

    @Test
    void testBytes() {
        assertEquals(FULL_NAME,
                template.requestBody("direct:fullName", JSON.getBytes(StandardCharsets.UTF_8), String.class));
    }

    @Test
    void testBytesWithExchangeCharset() {
        String json = "{\"name\": \"København\"}";
        Object out = template.requestBodyAndHeader("direct:fullName", json.getBytes(StandardCharsets.ISO_8859_1),
                Exchange.CHARSET_NAME, "ISO-8859-1");
        assertEquals("{\"fullName\":\"København\"}", out);
    }

    @Test
    void testInputStream() {
        assertEquals(FULL_NAME, template.requestBody("direct:fullName",
                new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)), String.class));
    }

    @Test
    void testStreamCache() {
        assertEquals(FULL_NAME, template.requestBody("direct:fullName",
                new InputStreamCache(JSON.getBytes(StandardCharsets.UTF_8)), String.class));
    }

    @Test
    void testFileAndPath() throws Exception {
        Path path = tempDir.resolve("input.json");
        Files.writeString(path, JSON);
        assertEquals(FULL_NAME, template.requestBody("direct:fullName", path, String.class));
        File file = path.toFile();
        assertEquals(FULL_NAME, template.requestBody("direct:fullName", file, String.class));
    }

    @Test
    void testMap() {
        assertEquals(FULL_NAME, template.requestBody("direct:fullName", Map.of("name", "Camel"), String.class));
    }

    @Test
    void testTextThatIsNotJsonIsAJsonString() {
        assertEquals("\"HELLO\"", template.requestBody("direct:uppercase", "hello", String.class));
    }

    @Test
    void testInvalidJson() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:fullName", "{\"name\": "));
        assertInstanceOf(JsonParseException.class, e.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fullName")
                        .to("jsonata:org/apache/camel/component/jsonata/jacksonInput/fullName/expressions.spec?outputType=JsonString");
                from("direct:count")
                        .to("jsonata:org/apache/camel/component/jsonata/jacksonInput/count/expressions.spec?outputType=JsonString");
                from("direct:uppercase")
                        .to("jsonata:org/apache/camel/component/jsonata/jacksonInput/uppercase/expressions.spec?outputType=JsonString");
            }
        };
    }
}
