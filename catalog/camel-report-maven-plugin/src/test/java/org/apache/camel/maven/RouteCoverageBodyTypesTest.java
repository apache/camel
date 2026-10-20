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
package org.apache.camel.maven;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.maven.model.RouteCoverageNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route coverage table with the body types each step has been reached with (CAMEL-25750).
 */
public class RouteCoverageBodyTypesTest {

    @Test
    public void testTheBodyTypesColumn() {
        String table = table(true);

        List<String> lines = table.lines().toList();
        assertTrue(lines.get(0).contains("Route") && lines.get(0).contains("Body types"), table);
        // the widest step is "  unmarshal", and the column starts four spaces after it
        assertTrue(table.contains("  unmarshal    GenericFile"), table);
        // a step reached with data by some messages and with a payload by others needs attention
        assertTrue(table.contains("    to" + " ".repeat(9) + "LinkedHashMap | String (data and payload)"), table);
        // the column lines up
        assertEquals(1, lines.stream().filter(l -> l.contains("GenericFile") || l.contains("LinkedHashMap"))
                .map(l -> l.indexOf(l.contains("GenericFile") ? "GenericFile" : "LinkedHashMap")).distinct().count(),
                table);
    }

    @Test
    public void testWithoutTheOption() {
        String table = table(false);

        assertFalse(table.contains("Body types"), table);
        assertFalse(table.contains("GenericFile"), table);
        assertTrue(table.contains("  unmarshal"), table);
    }

    @Test
    public void testTheStepsReachedWithBodiesOfMoreThanOneKind() {
        List<String> mixed = RouteCoverageMojo.mixedBodyKinds("routes/orders.xml", "orders", List.of(
                node("from", 3, 2, 0, List.of(), List.of()),
                // two types of one kind: dynamic, but not a failure
                node("unmarshal", 4, 2, 1, List.of("java.lang.String", "byte[]"), List.of("payload")),
                node("to", 7, 2, 1, List.of("java.util.LinkedHashMap", "java.lang.String"), List.of("data", "payload"))));

        assertEquals(List.of("routes/orders.xml:7 to (route orders): LinkedHashMap | String (data and payload)"), mixed);
        assertTrue(RouteCoverageMojo.mixedBodyKinds("routes/orders.xml", "orders",
                List.of(node("from", 3, 2, 0, List.of(), List.of()))).isEmpty());
    }

    @Test
    public void testBodyTypesText() {
        assertEquals("String | byte[] | String[]",
                RouteCoverageMojo.bodyTypesText(List.of("java.lang.String", "byte[]", "java.lang.String[]"),
                        List.of("payload")));
        assertEquals("null | LinkedHashMap (null and data)",
                RouteCoverageMojo.bodyTypesText(List.of("null", "java.util.LinkedHashMap"), List.of("null", "data")));
    }

    private static String table(boolean bodyTypes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream sw = new PrintStream(bos, true, StandardCharsets.UTF_8);
        RouteCoverageMojo.printTable(sw, List.of(
                node("from", 12, 2, 0, List.of(), List.of()),
                node("unmarshal", 13, 2, 1, List.of("org.apache.camel.component.file.GenericFile"), List.of("payload")),
                node("choice", 14, 2, 1, List.of(), List.of()),
                node("to", 16, 2, 2, List.of("java.util.LinkedHashMap", "java.lang.String"), List.of("data", "payload"))),
                bodyTypes);
        return bos.toString(StandardCharsets.UTF_8);
    }

    private static RouteCoverageNode node(
            String name, int line, int count, int level, List<String> bodyTypes, List<String> bodyKinds) {
        RouteCoverageNode node = new RouteCoverageNode();
        node.setName(name);
        node.setLineNumber(line);
        node.setCount(count);
        node.setLevel(level);
        node.setBodyTypes(bodyTypes);
        node.setBodyKinds(bodyKinds);
        return node;
    }
}
