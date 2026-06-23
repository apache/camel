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
package org.apache.camel.language.simple.functions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OutputFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new OutputFunctionFactory();
    }

    // --- pretty( ---

    @Test
    public void testPretty() {
        assertEquals("Hello", evaluate("pretty('Hello')", String.class));

        // XmlPrettyPrinter only emits text content when indent > 1 (i.e., inside a nested element).
        // Text directly inside the root element is silently dropped, so "world!" does not appear.
        assertEquals("<hello id=\"m123\">\n</hello>", evaluate("pretty(${body})", String.class));

        exchange.getMessage().setBody("{\"name\": \"Jack\", \"id\": 123}");
        assertEquals("{\n\t\"name\": \"Jack\",\n\t\"id\": 123\n}\n", evaluate("pretty(${body})", String.class));
    }

    // --- toJson( / toPrettyJson( ---

    @Test
    public void testToJson() {
        // string body is returned as-is
        exchange.getMessage().setBody("Hello");
        assertEquals("Hello", evaluate("toJson(${body})", String.class));

        // map body is serialized to JSON
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Jack");
        map.put("id", 123);
        exchange.getMessage().setBody(map);
        assertEquals("{\"name\":\"Jack\",\"id\":123}", evaluate("toJson(${body})", String.class));
        // pretty mode
        String pretty = Jsoner.prettyPrint("{\"name\":\"Jack\",\"id\":123}");
        assertEquals(pretty, evaluate("toPrettyJson(${body})", String.class));

        // list body is serialized to JSON array
        exchange.getMessage().setBody(List.of("a", "b", "c"));
        assertEquals("[\"a\",\"b\",\"c\"]", evaluate("toJson(${body})", String.class));
        // pretty mode
        pretty = Jsoner.prettyPrint("[\"a\",\"b\",\"c\"]");
        assertEquals(pretty, evaluate("toPrettyJson(${body})", String.class));

        // null body
        exchange.getMessage().setBody(null);
        assertNull(evaluate("toJson(${body})", Object.class));
        // pretty mode
        assertNull(evaluate("toPrettyJson(${body})", Object.class));

        // toJson with a header expression
        exchange.getMessage().setHeader("myNum", 42);
        assertEquals("42", evaluate("toJson(${header.myNum})", String.class));
        // pretty mode
        assertEquals("42", evaluate("toPrettyJson(${header.myNum})", String.class));
    }
}
