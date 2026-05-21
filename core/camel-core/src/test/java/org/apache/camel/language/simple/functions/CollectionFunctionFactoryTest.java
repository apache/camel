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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CollectionFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new CollectionFunctionFactory();
    }

    // --- setHeader ---

    @Test
    public void testSetHeader() {
        exchange.getIn().setBody("hello");
        evaluate("setHeader(myKey,${body})");
        assertEquals("hello", exchange.getIn().getHeader("myKey"));
    }

    @Test
    public void testCreateCodeSetHeader() {
        assertEquals(
                "Object value = body;\n        return setHeader(exchange, \"myKey\", null, value);",
                createCode("setHeader(myKey,body)"));
    }

    // --- setVariable ---

    @Test
    public void testSetVariable() {
        exchange.getIn().setBody("world");
        evaluate("setVariable(myVar,${body})");
        assertEquals("world", exchange.getVariable("myVar"));
    }

    @Test
    public void testCreateCodeSetVariable() {
        assertEquals(
                "Object value = body;\n        return setVariable(exchange, \"myVar\", null, value);",
                createCode("setVariable(myVar,body)"));
    }

    // --- range ---

    @Test
    @SuppressWarnings("unchecked")
    public void testRangeMinMax() {
        // range is exclusive at the upper bound: range(1,5) -> [1,2,3,4]
        List<Integer> result = evaluate("range(1,5)", List.class);
        assertEquals(List.of(1, 2, 3, 4), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRangeMax() {
        // range(max) uses min=1, exclusive upper: range(3) -> [1,2]
        List<Integer> result = evaluate("range(3)", List.class);
        assertEquals(List.of(1, 2), result);
    }

    @Test
    public void testCreateCodeRangeMinMax() {
        assertEquals("rangeList(exchange, 1, 5)", createCode("range(1,5)"));
    }

    @Test
    public void testCreateCodeRangeMax() {
        assertEquals("rangeList(exchange, 0, 3)", createCode("range(3)"));
    }

    // --- distinct ---

    @Test
    @SuppressWarnings("unchecked")
    public void testDistinct() {
        exchange.getIn().setBody(List.of("a", "b", "a", "c", "b"));
        List<String> result = evaluate("distinct()", List.class);
        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of("a", "b", "c")));
    }

    @Test
    public void testCreateCodeDistinct() {
        assertEquals("distinct(exchange, body)", createCode("distinct()"));
    }

    // --- reverse ---

    @Test
    @SuppressWarnings("unchecked")
    public void testReverse() {
        exchange.getIn().setBody(List.of("a", "b", "c"));
        List<String> result = evaluate("reverse()", List.class);
        assertEquals(List.of("c", "b", "a"), result);
    }

    @Test
    public void testCreateCodeReverse() {
        assertEquals("reverse(exchange, body)", createCode("reverse()"));
    }

    // --- split ---

    @Test
    @SuppressWarnings("unchecked")
    public void testSplit() {
        exchange.getIn().setBody("a:b:c");
        List<String> result = evaluate("split(:)", List.class);
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSplitWithExp() {
        exchange.getIn().setHeader("csv", "x-y-z");
        List<String> result = evaluate("split(${header.csv},-)", List.class);
        assertEquals(List.of("x", "y", "z"), result);
    }

    @Test
    public void testCreateCodeSplit() {
        assertEquals(
                "Object value = body;\n        String separator = \":\";\n        return stringSplit(exchange, value, separator);",
                createCode("split(:)"));
    }

    // --- sort ---

    @Test
    @SuppressWarnings("unchecked")
    public void testSort() {
        exchange.getIn().setBody(List.of("b", "a", "c"));
        List<String> result = evaluate("sort()", List.class);
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSortReverse() {
        exchange.getIn().setBody(List.of("b", "a", "c"));
        List<String> result = evaluate("sort(true)", List.class);
        assertEquals(List.of("c", "b", "a"), result);
    }

    @Test
    public void testCreateCodeSortReturnsNull() {
        assertNull(createFactory().createCode(context, "sort()", 0));
    }

    // --- forEach ---

    @Test
    public void testCreateCodeForEachThrows() {
        assertThrows(UnsupportedOperationException.class,
                () -> createFactory().createCode(context, "forEach(${body},${body})", 0));
    }

    // --- filter ---

    @Test
    public void testCreateCodeFilterThrows() {
        assertThrows(UnsupportedOperationException.class,
                () -> createFactory().createCode(context, "filter(${body},${body})", 0));
    }

    // --- listAdd ---

    @Test
    public void testListAdd() {
        List<String> list = new ArrayList<>(List.of("a", "b"));
        exchange.getIn().setBody(list);
        evaluate("listAdd(c)");
        assertEquals(List.of("a", "b", "c"), exchange.getIn().getBody(List.class));
    }

    @Test
    public void testCreateCodeListAddReturnsNull() {
        assertNull(createFactory().createCode(context, "listAdd(c)", 0));
    }

    // --- listRemove ---

    @Test
    public void testListRemove() {
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        exchange.getIn().setBody(list);
        evaluate("listRemove(b)");
        assertEquals(List.of("a", "c"), exchange.getIn().getBody(List.class));
    }

    @Test
    public void testCreateCodeListRemoveReturnsNull() {
        assertNull(createFactory().createCode(context, "listRemove(b)", 0));
    }

    // --- mapAdd ---

    @Test
    public void testMapAdd() {
        Map<String, String> map = new HashMap<>();
        exchange.getIn().setBody(map);
        evaluate("mapAdd(key,val)");
        assertEquals("val", exchange.getIn().getBody(Map.class).get("key"));
    }

    @Test
    public void testCreateCodeMapAddReturnsNull() {
        assertNull(createFactory().createCode(context, "mapAdd(key,val)", 0));
    }

    // --- mapRemove ---

    @Test
    public void testMapRemove() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "val");
        exchange.getIn().setBody(map);
        evaluate("mapRemove(key)");
        assertTrue(exchange.getIn().getBody(Map.class).isEmpty());
    }

    @Test
    public void testCreateCodeMapRemoveReturnsNull() {
        assertNull(createFactory().createCode(context, "mapRemove(key)", 0));
    }

    // --- list ---

    @Test
    @SuppressWarnings("unchecked")
    public void testList() {
        List<String> result = evaluate("list('a','b','c')", List.class);
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    public void testCreateCodeList() {
        assertEquals("list(exchange, null)", createCode("list()"));
        assertEquals("list(exchange, \"a\", \"b\")", createCode("list('a','b')"));
    }

    // --- map ---

    @Test
    @SuppressWarnings("unchecked")
    public void testMap() {
        Map<String, String> result = evaluate("map('k1','v1','k2','v2')", Map.class);
        assertEquals(2, result.size());
        assertEquals("v1", result.get("k1"));
        assertEquals("v2", result.get("k2"));
    }

    @Test
    public void testCreateCodeMap() {
        assertEquals("map(exchange, null)", createCode("map()"));
        assertEquals("map(exchange, \"k\", \"v\")", createCode("map('k','v')"));
    }

}
