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
package org.apache.camel.dsl.jbang.core.commands.transform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataWeaveConverterTest {

    private DataWeaveConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DataWeaveConverter();
    }

    // ── Header conversion ──

    @Test
    void testHeaderConversion() {
        String dw = """
                %dw 2.0
                output application/json
                ---
                { name: "test" }
                """;
        String result = converter.convert(dw);
        assertTrue(result.contains("/** DataSonnet"));
        assertTrue(result.contains("version=2.0"));
        assertTrue(result.contains("output application/json"));
        assertTrue(result.contains("*/"));
    }

    // ── Field access ──

    @Test
    void testPayloadToBody() {
        String result = converter.convertExpression("payload.name");
        assertEquals("body.name", result);
    }

    @Test
    void testNestedPayloadAccess() {
        String result = converter.convertExpression("payload.customer.email");
        assertEquals("body.customer.email", result);
    }

    @Test
    void testVarsConversion() {
        String result = converter.convertExpression("vars.myVar");
        assertEquals("cml.variable('myVar')", result);
    }

    @Test
    void testAttributesHeaders() {
        String result = converter.convertExpression("attributes.headers.contentType");
        assertEquals("cml.header('contentType')", result);
    }

    @Test
    void testAttributesQueryParams() {
        String result = converter.convertExpression("attributes.queryParams.page");
        assertEquals("cml.header('page')", result);
    }

    // ── Operators ──

    @Test
    void testStringConcat() {
        String result = converter.convertExpression("payload.first ++ \" \" ++ payload.last");
        assertEquals("body.first + \" \" + body.last", result);
    }

    @Test
    void testArithmetic() {
        String result = converter.convertExpression("payload.qty * payload.price");
        assertEquals("body.qty * body.price", result);
    }

    @Test
    void testComparison() {
        String result = converter.convertExpression("payload.age >= 18");
        assertEquals("body.age >= 18", result);
    }

    @Test
    void testLogicalOps() {
        String result = converter.convertExpression("payload.active and payload.verified");
        assertEquals("body.active && body.verified", result);
    }

    // ── Default operator ──

    @Test
    void testDefault() {
        String result = converter.convertExpression("payload.currency default \"USD\"");
        assertEquals("cml.defaultVal(body.currency, \"USD\")", result);
    }

    // ── Type coercion ──

    @Test
    void testAsNumber() {
        String result = converter.convertExpression("payload.count as Number");
        assertEquals("cml.toDecimal(body.count)", result);
    }

    @Test
    void testAsString() {
        String result = converter.convertExpression("payload.id as String");
        assertEquals("std.toString(body.id)", result);
    }

    @Test
    void testAsStringWithFormat() {
        String result = converter.convertExpression("payload.date as String {format: \"yyyy-MM-dd\"}");
        assertEquals("cml.formatDate(body.date, \"yyyy-MM-dd\")", result);
    }

    @Test
    void testAsBoolean() {
        String result = converter.convertExpression("payload.active as Boolean");
        assertEquals("cml.toBoolean(body.active)", result);
    }

    // ── Built-in functions ──

    @Test
    void testSizeOf() {
        String result = converter.convertExpression("sizeOf(payload.items)");
        assertEquals("std.length(body.items)", result);
    }

    @Test
    void testUpper() {
        String result = converter.convertExpression("upper(payload.name)");
        assertEquals("std.asciiUpper(body.name)", result);
    }

    @Test
    void testLower() {
        String result = converter.convertExpression("lower(payload.name)");
        assertEquals("std.asciiLower(body.name)", result);
    }

    @Test
    void testNow() {
        String result = converter.convertExpression("now()");
        assertEquals("cml.now()", result);
    }

    @Test
    void testNowWithFormat() {
        String result = converter.convertExpression("now() as String {format: \"yyyy-MM-dd\"}");
        assertEquals("cml.nowFmt(\"yyyy-MM-dd\")", result.trim());
    }

    @Test
    void testUuid() {
        String result = converter.convertExpression("uuid()");
        assertEquals("cml.uuid()", result);
    }

    @Test
    void testP() {
        String result = converter.convertExpression("p('config.key')");
        assertEquals("cml.properties(\"config.key\")", result);
    }

    @Test
    void testTrim() {
        String result = converter.convertExpression("trim(payload.name)");
        assertEquals("c.trim(body.name)", result);
        assertTrue(converter.needsCamelLib());
    }

    // ── String operations ──

    @Test
    void testContains() {
        String result = converter.convertExpression("payload.email contains \"@\"");
        assertEquals("c.contains(body.email, \"@\")", result);
        assertTrue(converter.needsCamelLib());
    }

    @Test
    void testSplitBy() {
        String result = converter.convertExpression("payload.tags splitBy \",\"");
        assertEquals("std.split(body.tags, \",\")", result);
    }

    @Test
    void testJoinBy() {
        String result = converter.convertExpression("payload.items joinBy \", \"");
        assertEquals("std.join(\", \", body.items)", result);
    }

    @Test
    void testReplace() {
        String result = converter.convertExpression("payload.text replace \"old\" with \"new\"");
        assertEquals("std.strReplace(body.text, \"old\", \"new\")", result);
    }

    // ── Collection operations ──

    @Test
    void testMap() {
        String result = converter.convertExpression(
                "payload.items map ((item) -> { name: item.name })");
        assertTrue(result.contains("std.map(function(item)"));
        assertTrue(result.contains("item.name"));
    }

    @Test
    void testFilter() {
        String result = converter.convertExpression(
                "payload.items filter ((item) -> item.active)");
        assertTrue(result.contains("std.filter(function(item)"));
        assertTrue(result.contains("item.active"));
    }

    @Test
    void testReduce() {
        String result = converter.convertExpression(
                "payload.items reduce ((item, acc = 0) -> acc + item.price)");
        assertTrue(result.contains("std.foldl(function(acc, item)"));
        assertTrue(result.contains("acc + item.price"));
        assertTrue(result.contains(", 0)"));
    }

    @Test
    void testReduceParamSwap() {
        // Verify that acc and item params are swapped for std.foldl
        String result = converter.convertExpression(
                "payload.items reduce ((item, acc = 0) -> acc + item.price)");
        // In std.foldl, it should be function(acc, item) not function(item, acc)
        assertTrue(result.contains("function(acc, item)"));
    }

    @Test
    void testFlatMap() {
        String result = converter.convertExpression(
                "payload.items flatMap ((item) -> item.tags)");
        assertTrue(result.contains("std.flatMap(function(item)"));
        assertTrue(result.contains("item.tags"));
    }

    // ── If/else ──

    @Test
    void testIfElse() {
        String result = converter.convertExpression(
                "if (payload.age >= 18) \"adult\" else \"minor\"");
        assertEquals("if body.age >= 18 then \"adult\" else \"minor\"", result);
    }

    // ── Object and array literals ──

    @Test
    void testObjectLiteral() {
        String result = converter.convertExpression("{ name: payload.name, age: payload.age }");
        assertTrue(result.contains("name: body.name"));
        assertTrue(result.contains("age: body.age"));
    }

    @Test
    void testArrayLiteral() {
        String result = converter.convertExpression("[1, 2, 3]");
        assertEquals("[1, 2, 3]", result);
    }

    // ── Full script tests ──

    @Test
    void testSimpleRenameScript() throws IOException {
        String dw = loadResource("dataweave/simple-rename.dwl");
        String result = converter.convert(dw);

        assertTrue(result.contains("/** DataSonnet"));
        assertTrue(result.contains("output application/json"));
        assertTrue(result.contains("body.order_id"));
        assertTrue(result.contains("body.customer.email"));
        assertTrue(result.contains("body.customer.first_name + \" \" + body.customer.last_name"));
        assertTrue(result.contains("cml.defaultVal(body.currency, \"USD\")"));
        assertTrue(result.contains("status: \"RECEIVED\""));
    }

    @Test
    void testCollectionMapScript() throws IOException {
        String dw = loadResource("dataweave/collection-map.dwl");
        String result = converter.convert(dw);

        assertTrue(result.contains("std.map(function(item)"));
        assertTrue(result.contains("item.product_sku"));
        assertTrue(result.contains("cml.toDecimal(item.qty)"));
        assertTrue(result.contains("std.foldl(function(acc, item)"));
    }

    @Test
    void testEventMessageScript() throws IOException {
        String dw = loadResource("dataweave/event-message.dwl");
        String result = converter.convert(dw);

        assertTrue(result.contains("\"ORDER_CREATED\""));
        assertTrue(result.contains("cml.uuid()"));
        assertTrue(result.contains("cml.variable('correlationId')"));
        assertTrue(result.contains("cml.variable('parsedOrder')"));
        assertTrue(result.contains("std.length("));
    }

    @Test
    void testTypeCoercionScript() throws IOException {
        String dw = loadResource("dataweave/type-coercion.dwl");
        String result = converter.convert(dw);

        assertTrue(result.contains("cml.toDecimal(body.count)"));
        assertTrue(result.contains("cml.toDecimal(body.total)"));
        assertTrue(result.contains("cml.toBoolean(body.active)"));
        assertTrue(result.contains("std.toString(body.id)"));
        assertTrue(result.contains("cml.formatDate(body.timestamp, \"yyyy-MM-dd\")"));
    }

    @Test
    void testNullHandlingScript() throws IOException {
        String dw = loadResource("dataweave/null-handling.dwl");
        String result = converter.convert(dw);

        assertTrue(result.contains("cml.defaultVal(body.name, \"Unknown\")"));
        assertTrue(result.contains("cml.defaultVal(body.address.city, \"N/A\")"));
        assertTrue(result.contains("cml.defaultVal(body.address.country, \"US\")"));
    }

    @Test
    void testStringOpsScript() throws IOException {
        String dw = loadResource("dataweave/string-ops.dwl");
        String result = converter.convert(dw);

        assertTrue(result.contains("std.asciiUpper(body.name)"));
        assertTrue(result.contains("std.asciiLower(body.name)"));
        assertTrue(result.contains("c.contains(body.email, \"@\")"));
        assertTrue(result.contains("std.split(body.tags, \",\")"));
        assertTrue(result.contains("std.join(\"; \", body.items)"));
        assertTrue(result.contains("std.strReplace(body.text, \"old\", \"new\")"));
    }

    @Test
    void testTodoCountForUnsupportedConstructs() {
        converter.convert("""
                %dw 2.0
                output application/json
                ---
                {
                    value: payload.x as Date
                }
                """);
        assertTrue(converter.getTodoCount() > 0, "Should have TODO count for unsupported 'as Date'");
    }

    @Test
    void testNoHeaderScript() {
        String result = converter.convert("{ name: payload.name }");
        assertTrue(result.contains("name: body.name"));
    }

    // ── startsWith / endsWith ──

    @Test
    void testStartsWith() {
        String result = converter.convertExpression("payload.name startsWith \"Dr\"");
        assertEquals("c.startsWith(body.name, \"Dr\")", result);
        assertTrue(converter.needsCamelLib());
    }

    @Test
    void testEndsWith() {
        String result = converter.convertExpression("payload.file endsWith \".csv\"");
        assertEquals("c.endsWith(body.file, \".csv\")", result);
        assertTrue(converter.needsCamelLib());
    }

    // ── Math functions ──

    @Test
    void testAbs() {
        String result = converter.convertExpression("abs(payload.value)");
        assertEquals("c.abs(body.value)", result);
        assertTrue(converter.needsCamelLib());
    }

    @Test
    void testRound() {
        String result = converter.convertExpression("round(payload.value)");
        assertEquals("c.round(body.value)", result);
        assertTrue(converter.needsCamelLib());
    }

    @Test
    void testSqrt() {
        String result = converter.convertExpression("sqrt(payload.value)");
        assertEquals("cml.sqrt(body.value)", result);
    }

    @Test
    void testAvg() {
        String result = converter.convertExpression("avg(payload.scores)");
        assertEquals("c.avg(body.scores)", result);
        assertTrue(converter.needsCamelLib());
    }

    // ── mapWithIndex parameter order ──

    @Test
    void testMapWithIndex() {
        String result = converter.convertExpression(
                "payload.items map ((item, idx) -> { index: idx, name: item.name })");
        // DataSonnet std.mapWithIndex uses function(index, item), so params must be swapped
        assertTrue(result.contains("std.mapWithIndex(function(idx, item)"));
    }

    // ── distinctBy ──

    @Test
    void testDistinctBy() {
        String result = converter.convertExpression(
                "payload.items distinctBy ((item) -> item.id)");
        assertTrue(result.contains("c.distinctBy("));
        assertTrue(result.contains("function(item) item.id"));
        assertTrue(converter.needsCamelLib());
    }

    // ── Lambda shorthand ──

    @Test
    void testLambdaShorthand() {
        String result = converter.convertExpression("payload.items map $.name");
        assertTrue(result.contains("function(x) x.name"));
    }

    // ── match expression ──

    @Test
    void testMatchExpressionUnsupported() {
        String result = converter.convertExpression(
                "payload.status match { case \"active\" -> true, case \"inactive\" -> false }");
        assertTrue(result.contains("TODO"));
        assertTrue(converter.getTodoCount() > 0);
    }

    // ── Multi-value selector ──

    @Test
    void testMultiValueSelector() {
        String result = converter.convertExpression("payload.items.*name");
        assertEquals("std.map(function(x) x.name, body.items)", result);
        assertEquals(0, converter.getTodoCount());
    }

    // ── Escape handling ──

    @Test
    void testStringEscapesPreserved() {
        String result = converter.convertExpression("payload.text ++ \"\\n\"");
        assertTrue(result.contains("\"\\n\""), "Newline escape should be preserved, got: " + result);
    }

    // ── Helpers ──

    private String loadResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(is, "Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
