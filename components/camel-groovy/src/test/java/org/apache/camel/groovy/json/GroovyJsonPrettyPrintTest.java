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
package org.apache.camel.groovy.json;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import groovy.json.JsonOutput;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The single pass pretty printer renders exactly what {@link JsonOutput#prettyPrint(String)} renders.
 */
public class GroovyJsonPrettyPrintTest {

    static Stream<Arguments> documents() {
        return Stream.of(
                Arguments.of("empty map", map()),
                Arguments.of("empty list", list()),
                Arguments.of("nested", map("a", 1, "b", map(), "c", list(), "d", list(1, 2.5, "x\"y", null, true),
                        "e", map("f", map("g", list(map("h", "ü€")))))),
                Arguments.of("list of maps", list(map("a", 1), map("b", list()), map())),
                Arguments.of("strings", map("s", "line\nbreak\ttab/slash\\back \"quoted\" 'single'", "empty", "",
                        "unicode", "é中😀", "k\"ey", "v", "ü", "€")),
                Arguments.of("numbers", map("i", 1, "l", Long.MAX_VALUE, "d", 1.0d, "f", 1.5f, "big", 1.2345678901234567E19,
                        "neg", -1.5e-7, "bd", new BigDecimal("1.10"), "zero", 0, "nd", -0.0d)),
                Arguments.of("scalars", map("n", null, "t", true, "f", false, "c", 'c', "date", new Date(0),
                        "uuid", UUID.fromString("11111111-2222-3333-4444-555555555555"), "en", Thread.State.NEW)),
                Arguments.of("arrays and sets", map("arr", new String[] { "a", "b" }, "empty", new Object[0],
                        "ints", new int[] { 1, 2 }, "set", new LinkedHashSet<>(list(1, 2)),
                        "sorted", new TreeMap<>(map("z", 1, "a", 2)))),
                Arguments.of("deep nesting", map("nested", list(list(list()), list(map()), map("x", list(list(1)))))),
                Arguments.of("top level list", list(1, "two", map("three", 3), list(4))),
                Arguments.of("top level string", "top"),
                Arguments.of("top level number", 42),
                Arguments.of("top level null", null),
                Arguments.of("pojo", map("pojo", new Book("Dune", 1965), "pojos", list(new Book("Emma", 1815)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documents")
    public void testSameOutputAsGroovyPrettyPrint(String name, Object document) {
        String expected = JsonOutput.prettyPrint(JsonOutput.toJson(document));
        assertEquals(expected, GroovyJSonlDataFormat.toPrettyJson(document));
    }

    @Test
    public void testNullKey() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put(null, "x");
        assertThrows(IllegalArgumentException.class, () -> JsonOutput.toJson(doc));
        assertThrows(IllegalArgumentException.class, () -> GroovyJSonlDataFormat.toPrettyJson(doc));
    }

    @Test
    public void testMarshalUsesExchangeCharset() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            GroovyJSonlDataFormat df = new GroovyJSonlDataFormat();
            Map<String, Object> doc = map("name", "Jürgen");

            Exchange exchange = new DefaultExchange(context);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            df.marshal(exchange, doc, bos);
            assertEquals(JsonOutput.prettyPrint(JsonOutput.toJson(doc)), bos.toString(StandardCharsets.UTF_8));

            df.setPrettyPrint(false);
            exchange.setProperty(Exchange.CHARSET_NAME, "UTF-16BE");
            bos = new ByteArrayOutputStream();
            df.marshal(exchange, doc, bos);
            assertEquals(JsonOutput.toJson(doc), bos.toString(StandardCharsets.UTF_16BE));
        }
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static List<Object> list(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    public static class Book {
        private final String title;
        private final int year;

        Book(String title, int year) {
            this.title = title;
            this.year = year;
        }

        public String getTitle() {
            return title;
        }

        public int getYear() {
            return year;
        }
    }
}
