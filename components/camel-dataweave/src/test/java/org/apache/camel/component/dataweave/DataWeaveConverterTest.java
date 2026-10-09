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
package org.apache.camel.component.dataweave;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the DataSonnet produced by {@link DataWeaveConverter}. The produced DataSonnet is executed against the
 * output of DataWeave by the DataWeave corpus test of camel-datasonnet.
 */
class DataWeaveConverterTest {

    private static final String DW_IMPORT = "local dw = import 'camel-dataweave.libsonnet';\n";

    private DataWeaveConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DataWeaveConverter();
    }

    // Converts an expression that needs no library, or asserts the library import and returns what follows it
    private String expr(String dataWeave) {
        String result = converter.convertExpression(dataWeave);
        if (converter.needsDataWeaveLib()) {
            assertTrue(result.startsWith(DW_IMPORT), result);
            return result.substring(DW_IMPORT.length());
        }
        return result;
    }

    // -- Header

    @Test
    void testHeader() {
        String result = converter.convert("""
                %dw 2.0
                input payload application/xml
                output application/json
                ---
                { a: 1 }
                """);
        assertEquals("/** DataSonnet\nversion=2.0\noutput application/json\ninput payload application/xml\n*/\n{\n  a: 1\n}",
                result);
        assertFalse(converter.needsDataWeaveLib());
        assertEquals(0, converter.getTodoCount());
    }

    @Test
    void testOutputMediaTypes() {
        assertTrue(converter.convert("%dw 2.0\noutput application/java\n---\n1").contains("output application/x-java-object"));
        assertTrue(converter.convert("%dw 2.0\noutput json\n---\n1").contains("output application/json"));
    }

    @Test
    void testSkipNullOn() {
        String result = converter.convert("%dw 2.0\noutput application/json skipNullOn=\"everywhere\", indent=false\n---\n1");
        assertTrue(result.endsWith("dw.skipNulls(1, \"everywhere\")"), result);
        assertEquals(0, converter.getTodoCount());
    }

    @Test
    void testUnsupportedWriterPropertyAndModule() {
        String result = converter.convert("%dw 2.0\nimport modules::MyLib\noutput application/csv header=false\n---\npayload");
        assertEquals(2, converter.getTodoCount());
        assertTrue(result.contains("/* TODO: manual conversion needed -- import of module: modules::MyLib */\n"), result);
        assertTrue(result.contains("/* TODO: manual conversion needed -- writer property: header=false */\nbody"), result);
    }

    @Test
    void testKnownModuleImport() {
        converter.convert("%dw 2.0\nimport * from dw::core::Strings\n---\ncamelize(payload.a)");
        assertEquals(0, converter.getTodoCount());
    }

    // -- Selectors

    @Test
    void testSelectors() {
        assertEquals("body", expr("payload"));
        assertEquals("dw.sel(body, \"name\")", expr("payload.name"));
        assertEquals("dw.path(body, [\"customer\", \"name\"])", expr("payload.customer.name"));
        assertEquals("dw.sel(body, \"first-name\")", expr("payload.'first-name'"));
        assertEquals("dw.sel(dw.idx(dw.selRaw(body, \"items\"), 0), \"sku\")", expr("payload.items[0].sku"));
        assertEquals("dw.idx(body, \"customer\")", expr("payload[\"customer\"]"));
        assertEquals("dw.slice(dw.selRaw(body, \"items\"), -1, 0)", expr("payload.items[-1 to 0]"));
        assertEquals("dw.attr(dw.selRaw(body, \"order\"), \"id\")", expr("payload.order.@id"));
        assertEquals("dw.multi(body, \"item\")", expr("payload.*item"));
        assertEquals("dw.desc(body, \"sku\")", expr("payload..sku"));
        assertEquals("dw.has(body, \"a\")", expr("payload.a?"));
        assertEquals("dw.filter(dw.sel(body, \"items\"), function(item, index) dw.sel(item, \"qty\") > 1)",
                expr("payload.items[?($.qty > 1)]"));
        assertEquals(0, converter.getTodoCount());
    }

    @Test
    void testVariablesAndAttributes() {
        assertEquals("cml.variable(\"orderId\")", expr("vars.orderId"));
        assertEquals("cml.variable(\"orderId\")", expr("vars[\"orderId\"]"));
        assertEquals("dw.sel(cml.variable(\"cfg\"), \"x\")", expr("vars.cfg.x"));
        assertEquals("cml.header(\"x-id\")", expr("attributes.headers.'x-id'"));
        assertEquals("cml.header(\"x-id\")", expr("attributes.headers[\"x-id\"]"));
        assertEquals("cml.header(\"page\")", expr("attributes.queryParams.page"));
        assertEquals("cml.header(\"id\")", expr("attributes.uriParams.id"));
        assertEquals("cml.header(\"CamelHttpMethod\")", expr("attributes.method"));
        assertEquals(0, converter.getTodoCount());
    }

    @ParameterizedTest
    @ValueSource(strings = { "vars", "attributes.headers", "attributes.foo", "foo", "bar(1)", "upper(1, 2)", "$" })
    void testUnknownIsTodo(String dataWeave) {
        String result = converter.convertExpression(dataWeave);
        assertEquals(1, converter.getTodoCount(), result);
        assertTrue(result.contains("TODO"), result);
    }

    @Test
    void testDatePart() {
        assertEquals("cml.datePart(cml.parseDateTime(dw.sel(body, \"date\"), null, \"Date\"), \"year\")",
                expr("(payload.date as Date).year"));
    }

    // -- Operators

    @Test
    void testOperators() {
        assertEquals("\"a\" + dw.sel(body, \"b\")", expr("\"a\" ++ payload.b"));
        assertEquals("1 + 2 * 3", expr("1 + 2 * 3"));
        assertEquals("1 - (2 - 3)", expr("1 - (2 - 3)"));
        assertEquals("\"a\" + 1 + 2", expr("\"a\" ++ 1 ++ 2"));
        assertEquals("(1 + 2) * 3", expr("(1 + 2) * 3"));
        assertEquals("true && (false || true)", expr("true and (false or true)"));
        assertEquals("!true", expr("not true"));
        assertEquals("dw.default(dw.sel(body, \"a\"), \"x\")", expr("payload.a default \"x\""));
        assertEquals("dw.similar(\"1\", 1)", expr("\"1\" ~= 1"));
        assertEquals("dw.minus(body, \"password\")", expr("payload - \"password\""));
        assertEquals("dw.removeAll(body, [\"a\", \"b\"])", expr("payload -- [\"a\", \"b\"]"));
        assertEquals("dw.range(1, 3)", expr("1 to 3"));
        assertEquals("if true then 1 else 2", expr("if (true) 1 else 2"));
        assertEquals("if true then 2 else 1", expr("unless (true) 1 else 2"));
        assertEquals("if true then 2 else 1", expr("unless (true) 1 otherwise 2"));
    }

    @Test
    void testDateArithmetic() {
        assertEquals("cml.dateAdd(\"2020-01-31\", \"P1D\")", expr("|2020-01-31| + |P1D|"));
        assertEquals("cml.dateAdd(\"2020-01-31\", \"-P1M\")", expr("|2020-01-31| - |P1M|"));
    }

    @Test
    void testCoercions() {
        assertEquals("cml.toDecimal(\"1\")", expr("\"1\" as Number"));
        assertEquals("dw.toString(1)", expr("1 as String"));
        assertEquals("cml.formatNumber(1, \"#.00\")", expr("1 as String {format: \"#.00\"}"));
        assertEquals("cml.formatNumberLocale(1, \"#.00\", \"de\")", expr("1 as String {format: \"#.00\", locale: \"de\"}"));
        // a date coerced with a format is written in that format, and is an ISO-8601 date otherwise
        assertEquals("cml.formatDate(cml.parseDateTime(\"01/31/2020\", \"MM/dd/yyyy\", \"Date\"), \"MM/dd/yyyy\")",
                expr("\"01/31/2020\" as Date {format: \"MM/dd/yyyy\"}"));
        assertEquals("cml.formatDate(cml.parseDateTime(\"01/31/2020\", \"MM/dd/yyyy\", \"Date\"), \"yyyy-MM-dd\")",
                expr("\"01/31/2020\" as Date {format: \"MM/dd/yyyy\"} as String {format: \"yyyy-MM-dd\"}"));
        assertEquals("cml.toBoolean(\"true\")", expr("\"true\" as Boolean"));
        assertEquals("std.isString(1)", expr("1 is String"));
        assertEquals(0, converter.getTodoCount());
        expr("1 as Foo");
        assertEquals(1, converter.getTodoCount());
    }

    // -- Strings

    @Test
    void testStrings() {
        assertEquals("\"say \\\"hi\\\"\"", expr("\"say \\\"hi\\\"\""));
        assertEquals("\"it's \\\"x\\\"\"", expr("'it\\'s \"x\"'"));
        assertEquals("(\"Hello \" + dw.str(dw.sel(body, \"name\")) + \"!\")", expr("\"Hello $(payload.name)!\""));
        assertEquals("\"$ 5\"", expr("\"\\$ 5\""));
        assertEquals("dw.map([1, 2], function(item, index) (\"v\" + dw.str(item)))", expr("[1, 2] map \"v$\""));
    }

    @Test
    void testRegularExpressions() {
        assertEquals("ds.replace(\"a  b\", \"\\\\s+\", \"_\")", expr("\"a  b\" replace /\\s+/ with \"_\""));
        assertEquals("dw.replace(\"a.b\", \".\", \"_\")", expr("\"a.b\" replace \".\" with \"_\""));
        assertEquals("dw.containsMatch(\"abc\", ds.scan, \"b+\")", expr("\"abc\" contains /b+/"));
        assertEquals("dw.splitByMatch(\"a1b\", ds.splitBy, \"[0-9]\")", expr("\"a1b\" splitBy /[0-9]/"));
        assertEquals("ds.matches(\"abc\", \"a.c\")", expr("\"abc\" matches /a.c/"));
    }

    // -- Functions

    @Test
    void testCoreFunctions() {
        assertEquals("dw.upper(dw.sel(body, \"a\"))", expr("upper(payload.a)"));
        assertEquals("dw.sizeOf(body)", expr("sizeOf(payload)"));
        assertEquals("dw.nullSafe(ds.strings.camelize, \"a_b\")", expr("camelize(\"a_b\")"));
        assertEquals("dw.nullSafe(ds.strings.capitalize, \"a_b\")", expr("Strings::capitalize(\"a_b\")"));
        assertEquals("ds.strings.leftPad(\"a\", 3, \" \")", expr("leftPad(\"a\", 3, \" \")"));
        assertEquals("std.mod(10, 3)", expr("10 mod 3"));
        assertEquals("cml.uuid()", expr("uuid()"));
        assertEquals("ds.write(body, \"application/xml\")", expr("write(payload, \"application/xml\")"));
        assertEquals("error \"bad\"", expr("fail(\"bad\")"));
        assertEquals("body", expr("log(payload)"));
        assertEquals(0, converter.getTodoCount());
    }

    @Test
    void testLambdas() {
        // DataWeave passes the index too: a lambda is given the parameters it does not declare
        assertEquals("dw.map(body, function(item, _1) dw.sel(item, \"name\"))", expr("payload map (item) -> item.name"));
        assertEquals("dw.map(body, function(i, idx) idx)", expr("payload map ((i, idx) -> idx)"));
        assertEquals("dw.mapObject(body, function(v, k, _2) {\n  [dw.str(k)]: v\n})",
                expr("payload mapObject (v, k) -> { (k): v }"));
        assertEquals("dw.map(body, function(item, index) {\n  line: index,\n  name: dw.sel(item, \"name\")\n})",
                expr("payload map { line: $$, name: $.name }"));
        assertEquals("dw.orderBy(body, function(item, index) -dw.sel(item, \"price\"))", expr("payload orderBy -$.price"));
        assertEquals("dw.pluck(body, function(value, key, index) key)", expr("payload pluck $$"));
        assertEquals("dw.pipe(body, function(value) dw.sizeOf(value))", expr("payload then sizeOf($)"));
        assertEquals(0, converter.getTodoCount());
    }

    @Test
    void testReduce() {
        assertEquals("dw.reduce(body, function(item, acc) acc + item, 0)",
                expr("payload reduce ((item, acc = 0) -> acc + item)"));
        assertEquals("dw.reduce1(body, function(item, acc) acc + item)", expr("payload reduce ((item, acc) -> acc + item)"));
        assertEquals("dw.reduce1(body, function(item, acc) (acc + item))", expr("payload reduce ($$ + $)"));
    }

    @Test
    void testImplicitLambdaDoesNotHideVariables() {
        // the inner $ is the line and the outer item stays visible
        assertEquals("dw.map(body, function(item, _1) dw.map(dw.sel(item, \"lines\"), "
                     + "function(item2, index) (dw.sel(item2, \"n\") + dw.sel(item, \"id\"))))",
                expr("payload map (item) -> item.lines map ($.n ++ item.id)"));
    }

    @Test
    void testFunctionReferenceArguments() {
        String result = expr("do {\n  fun name(x) = x.name\n  ---\n  payload map name\n}");
        assertTrue(result.endsWith("dw.map(body, function(item, index) name(item))"), result);
        assertEquals("function(f) dw.map(body, dw.fn(f))", expr("(f) -> payload map f"));
    }

    @Test
    void testLambdaWithTooManyParametersIsTodo() {
        expr("payload map (a, b, c) -> a");
        assertEquals(1, converter.getTodoCount());
    }

    // -- Objects

    @Test
    void testObjects() {
        assertEquals("{\n  a: 1,\n  \"first-name\": 2,\n  \"if\": 3\n}", expr("{ a: 1, 'first-name': 2, if: 3 }"));
        assertEquals("{\n  [dw.str(dw.sel(body, \"k\"))]: 1\n}", expr("{ (payload.k): 1 }"));
        assertEquals("{\n  [if dw.sel(body, \"flag\") then \"b\" else null]: 2\n}", expr("{ (b: 2) if payload.flag }"));
        assertEquals("({\n  a: 1\n} + dw.toObject(dw.sel(body, \"extra\")))", expr("{ a: 1, (payload.extra) }"));
        assertEquals("{}", expr("{}"));
    }

    // -- Match

    @Test
    void testMatch() {
        assertEquals(
                """
                        (local match = dw.sel(body, "status");
                        if match == "A" then 1
                        else if std.isNumber(match) then 2
                        else if std.isString(match) && ds.matches(match, "x(.)") then (local m = ds.scan(match, "x(.)")[0]; dw.idx(m, 1))
                        else if (local n = match; n > 3) then (local n = match; n)
                        else 5)""",
                expr("""
                        payload.status match {
                          case "A" -> 1
                          case is Number -> 2
                          case m matches /x(.)/ -> m[1]
                          case n if n > 3 -> n
                          else -> 5
                        }"""));
        assertTrue(expr("1 match { case 1 -> 2 }").endsWith("else error 'No case of the match expression matched: ' + "
                                                            + "std.toString(match))"));
    }

    // -- Declarations

    @Test
    void testDeclarations() {
        String result = converter.convert("""
                %dw 2.0
                var rate = 0.5
                fun total(a, b = 1) = a * b * rate
                fun fact(n) = if (n <= 1) 1 else n * fact(n - 1)
                ---
                total(fact(3))
                """);
        assertTrue(result.endsWith("""
                local rate = 0.5,
                      total(a, b = 1) = a * b * rate,
                      fact(n) = if n <= 1 then 1 else n * fact(n - 1);
                total(fact(3))"""), result);
        assertEquals(0, converter.getTodoCount());
    }

    @Test
    void testDoBlock() {
        assertEquals("local x = 1;\nx + 1", expr("do {\n  var x = 1\n  ---\n  x + 1\n}"));
    }

    @Test
    void testReservedNames() {
        assertEquals("local local_ = 1;\nlocal_", expr("do {\n  var local = 1\n  ---\n  local\n}"));
    }

    @Test
    void testOverloadedFunctionIsTodo() {
        converter.convert("%dw 2.0\nfun f(x: String) = x\nfun f(x: Number) = x\n---\nf(1)");
        assertEquals(1, converter.getTodoCount());
    }

    // -- TODO comments

    @Test
    void testTodoWithoutComments() {
        converter.setIncludeComments(false);
        assertEquals("null", converter.convertExpression("foo"));
        assertEquals(1, converter.getTodoCount());
    }

    @Test
    void testTodoCommentCannotBeClosedEarly() {
        String result = converter.convertExpression("payload update { case .a -> \"*/\" }");
        assertTrue(result.contains("* /"), result);
        assertFalse(result.contains("\"*/\""), result);
    }

    @Test
    void testUnsupportedConstructs() {
        assertTrue(converter.convertExpression("payload update { case .a -> 1 }").contains("update operator"));
        assertEquals(1, converter.getTodoCount());
        converter.convertExpression("{ a @(id: 1): 2 }");
        assertEquals(1, converter.getTodoCount());
    }

    @Test
    void testParseErrors() {
        assertThrows(DataWeaveConversionException.class, () -> converter.convertExpression("payload.a )"));
        assertThrows(DataWeaveConversionException.class, () -> converter.convert("%dw 2.0\n---\n{ a: 1 } )"));
        assertThrows(DataWeaveConversionException.class, () -> converter.convertExpression("var x = 1\nx"));
    }

    // -- Scripts

    @ParameterizedTest
    @ValueSource(strings = {
            "simple-rename.dwl", "collection-map.dwl", "event-message.dwl", "null-handling.dwl", "string-ops.dwl",
            "type-coercion.dwl" })
    void testScripts(String name) throws IOException {
        String result = converter.convert(loadResource("dataweave/" + name));
        assertEquals(0, converter.getTodoCount(), result);
        assertTrue(result.startsWith("/** DataSonnet"), result);
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream is = DataWeaveConverterTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
