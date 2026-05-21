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
package org.apache.camel.yaml.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlPrinterTest {

    @Test
    void simpleRoute() {
        var route = orderedMap(
                "id", "myRoute",
                "from", orderedMap(
                        "uri", "timer:yaml",
                        "parameters", orderedMap(
                                "period", 1234,
                                "includeMetadata", true),
                        "steps", List.of(
                                Map.of("log", orderedMap("message", "${body}")))));

        var roots = List.of(Map.of("route", route));
        String yaml = YamlPrinter.print(roots);

        String expected = "- route:\n"
                          + "    id: myRoute\n"
                          + "    from:\n"
                          + "      uri: timer:yaml\n"
                          + "      parameters:\n"
                          + "        period: 1234\n"
                          + "        includeMetadata: true\n"
                          + "      steps:\n"
                          + "        - log:\n"
                          + "            message: \"${body}\"\n";
        assertEquals(expected, yaml);
    }

    @Test
    void choiceWithWhenAndOtherwise() {
        var when1 = orderedMap(
                "expression", orderedMap(
                        "simple", orderedMap("expression", "${header.age} < 21")),
                "steps", List.of(
                        Map.of("to", orderedMap("uri", "mock:young"))));
        var when2 = orderedMap(
                "expression", orderedMap(
                        "simple", orderedMap("expression", "${header.age} > 70")),
                "steps", List.of(
                        Map.of("to", orderedMap("uri", "mock:senior"))));
        var otherwise = orderedMap(
                "steps", List.of(
                        Map.of("to", orderedMap("uri", "mock:work"))));

        var choice = orderedMap(
                "when", List.of(when1, when2),
                "otherwise", otherwise);

        var route = orderedMap(
                "from", orderedMap(
                        "uri", "direct:start",
                        "steps", List.of(Map.of("choice", choice))));

        String yaml = YamlPrinter.print(List.of(Map.of("route", route)));

        assertTrue(yaml.contains("- choice:"));
        assertTrue(yaml.contains("    when:"));
        assertTrue(yaml.contains("    otherwise:"));
        assertTrue(yaml.contains("        - to:"));
    }

    @Test
    void emptyMapping() {
        var roots = List.of(
                Map.of("route", orderedMap(
                        "from", orderedMap(
                                "uri", "timer:foo",
                                "steps", List.of(
                                        Map.of("marshal", orderedMap("csv", Map.of())),
                                        Map.of("log", orderedMap("message", "${body}")))))));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("csv: {}"), "Empty mapping should be inline {}");
    }

    @Test
    void multiLineString() {
        var roots = List.of(
                Map.of("route", orderedMap(
                        "from", orderedMap(
                                "uri", "direct:start",
                                "steps", List.of(
                                        Map.of("setBody", orderedMap(
                                                "expression", orderedMap(
                                                        "constant", orderedMap(
                                                                "expression", "{\n key: '123'\n}")))))))));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("|-"), "Multi-line without trailing newline should use |-");
        assertTrue(yaml.contains("  key: '123'"));
    }

    @Test
    void multiLineStringWithTrailingNewline() {
        var roots = List.of(
                Map.of("test", orderedMap("value", "line1\nline2\n")));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("|\n"), "Multi-line with trailing newline should use |");
    }

    @Test
    void quotingRules() {
        assertTrue(YamlPrinter.needsQuoting(""), "empty string");
        assertTrue(YamlPrinter.needsQuoting("true"), "boolean true");
        assertTrue(YamlPrinter.needsQuoting("false"), "boolean false");
        assertTrue(YamlPrinter.needsQuoting("TRUE"), "boolean TRUE");
        assertTrue(YamlPrinter.needsQuoting("yes"), "boolean yes");
        assertTrue(YamlPrinter.needsQuoting("no"), "boolean no");
        assertTrue(YamlPrinter.needsQuoting("on"), "boolean on");
        assertTrue(YamlPrinter.needsQuoting("off"), "boolean off");
        assertTrue(YamlPrinter.needsQuoting("null"), "null");
        assertTrue(YamlPrinter.needsQuoting("~"), "tilde");
        assertTrue(YamlPrinter.needsQuoting("123"), "integer");
        assertTrue(YamlPrinter.needsQuoting("3.14"), "float");
        assertTrue(YamlPrinter.needsQuoting("-42"), "negative number");
        assertTrue(YamlPrinter.needsQuoting("foo: bar"), "contains colon-space");
        assertTrue(YamlPrinter.needsQuoting("foo #comment"), "contains space-hash");
        assertTrue(YamlPrinter.needsQuoting("- item"), "starts with dash-space");
        assertTrue(YamlPrinter.needsQuoting("*ref"), "starts with star");
        assertTrue(YamlPrinter.needsQuoting("&anchor"), "starts with ampersand");
        assertTrue(YamlPrinter.needsQuoting("{flow}"), "starts with brace");
        assertTrue(YamlPrinter.needsQuoting("[flow]"), "starts with bracket");

        assertFalse(YamlPrinter.needsQuoting("hello"), "simple word");
        assertFalse(YamlPrinter.needsQuoting("Hello World"), "simple phrase");
        assertFalse(YamlPrinter.needsQuoting("timer:yaml"), "URI");
        assertFalse(YamlPrinter.needsQuoting("mock:result"), "simple URI");
        assertTrue(YamlPrinter.needsQuoting("${body}"), "starts with $");
        assertFalse(YamlPrinter.needsQuoting("foo.bar"), "dotted name");
        assertFalse(YamlPrinter.needsQuoting("my-route-id"), "dashed name");
    }

    @Test
    void booleanAndNumberValues() {
        var roots = List.of(Map.of("config", orderedMap(
                "enabled", true,
                "count", 42,
                "name", "test")));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("count: 42"));
        assertTrue(yaml.contains("name: test"));
    }

    @Test
    void multipleRoots() {
        var route1 = orderedMap("id", "r1", "from", orderedMap("uri", "direct:a"));
        var route2 = orderedMap("id", "r2", "from", orderedMap("uri", "direct:b"));

        String yaml = YamlPrinter.print(List.of(
                Map.of("route", route1),
                Map.of("route", route2)));

        long routeCount = yaml.lines().filter(l -> l.equals("- route:")).count();
        assertEquals(2, routeCount);
    }

    @Test
    void restWithEmptySteps() {
        var roots = List.of(
                Map.of("rest", orderedMap(
                        "path", "/api",
                        "steps", List.of(
                                Map.of("get", orderedMap("path", "/hello"))))));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("- rest:"));
        assertTrue(yaml.contains("    path: /api"));
        assertTrue(yaml.contains("      - get:"));
    }

    @Test
    void stringStartingWithDollarQuoted() {
        var roots = List.of(Map.of("test", orderedMap(
                "expr", "${header.age} < 21")));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("expr: \"${header.age} < 21\""),
                "Starts with $ — should be quoted: " + yaml);
    }

    @Test
    void stringWithColonSpaceQuoted() {
        var roots = List.of(Map.of("test", orderedMap(
                "value", "key: value")));

        String yaml = YamlPrinter.print(roots);
        assertTrue(yaml.contains("value: \"key: value\""),
                "Colon-space must be quoted: " + yaml);
    }

    private static Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
