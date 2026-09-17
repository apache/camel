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

import java.util.List;

import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new StringFunctionFactory();
    }

    // --- replace ---

    @Test
    public void testReplace() {
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello Camel", evaluate("replace(World,Camel)", String.class));
    }

    @Test
    public void testReplaceWithExpression() {
        exchange.getIn().setHeader("msg", "foo bar");
        assertEquals("foo-bar", evaluate("replace( ,-,${header.msg})", String.class));
    }

    // --- substring ---

    @Test
    public void testSubstringRange1() {
        exchange.getIn().setBody("Hello World");
        assertEquals("World", evaluate("substring(6)", String.class));
    }

    @Test
    public void testSubstringRange2() {
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello", evaluate("substring(0,6)", String.class));
    }

    @Test
    public void testSubstringRange3() {
        exchange.getIn().setBody("Hello World");
        assertEquals("", evaluate("substring(6,5)", String.class));
    }

    @Test
    public void testSubstringRange4() {
        assertEquals("World", evaluate("substring(6,0,'Hello World')", String.class));
    }

    // --- substringBefore / substringAfter / substringBetween ---

    @Test
    public void testSubstringBefore() {
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello ", evaluate("substringBefore(World)", String.class));
    }

    @Test
    public void testSubstringAfter() {
        exchange.getIn().setBody("Hello World");
        assertEquals(" World", evaluate("substringAfter(Hello)", String.class));
    }

    @Test
    public void testSubstringBetween() {
        exchange.getIn().setBody("[Hello World]");
        assertEquals("Hello World", evaluate("substringBetween([,])", String.class));
    }

    // --- contains ---

    @Test
    public void testContains() {
        exchange.getIn().setBody("Hello World");
        assertEquals(true, evaluate("contains(World)", Boolean.class));
        assertEquals(false, evaluate("contains(Camel)", Boolean.class));
    }

    @Test
    public void testContainsWithExpression() {
        exchange.getIn().setHeader("greeting", "Hello World");
        assertEquals(true, evaluate("contains(${header.greeting}, World)", Boolean.class));
    }

    // --- trim ---

    @Test
    public void testTrim() {
        exchange.getIn().setBody("  hello  ");
        assertEquals("hello", evaluate("trim()", String.class));
    }

    // --- val ---

    @Test
    public void testVal() {
        exchange.getIn().setBody("hello");
        assertEquals("hello", evaluate("val(${body})", String.class));
    }

    // --- capitalize ---

    @Test
    public void testCapitalize() {
        exchange.getIn().setBody("hello world");
        assertEquals("Hello World", evaluate("capitalize()", String.class));
    }

    // --- pad ---

    @Test
    public void testPad() {
        exchange.getIn().setBody("hi");
        assertEquals("hi   ", evaluate("pad(${body}, 5)", String.class));
    }

    // --- concat ---

    @Test
    public void testConcat() {
        exchange.getIn().setBody("Hello");
        assertEquals("Hello World", evaluate("concat(${body}, World, ' ')", String.class));
    }

    // --- quote / safeQuote / unquote ---

    @Test
    public void testQuote() {
        exchange.getIn().setBody("hello");
        assertEquals("\"hello\"", evaluate("quote()", String.class));
    }

    @Test
    public void testSafeQuote() {
        exchange.getIn().setBody("hello");
        assertEquals("\"hello\"", evaluate("safeQuote()", String.class));
    }

    @Test
    public void testUnquote() {
        exchange.getIn().setBody("'hello'");
        assertEquals("hello", evaluate("unquote()", String.class));
    }

    // --- uppercase / lowercase ---

    @Test
    public void testUppercase() {
        exchange.getIn().setBody("hello");
        assertEquals("HELLO", evaluate("uppercase()", String.class));
    }

    @Test
    public void testLowercase() {
        exchange.getIn().setBody("HELLO");
        assertEquals("hello", evaluate("lowercase()", String.class));
    }

    // --- length / size ---

    @Test
    public void testLength() {
        exchange.getIn().setBody("Hello");
        assertEquals(5, evaluate("length()", Integer.class));
    }

    @Test
    public void testSize() {
        exchange.getIn().setBody(List.of("a", "b", "c"));
        assertEquals(3, evaluate("size()", Integer.class));
    }

    // --- normalizeWhitespace ---

    @Test
    public void testNormalizeWhitespace() {
        exchange.getIn().setBody("  hello   world  ");
        assertEquals("hello world", evaluate("normalizeWhitespace()", String.class));
    }

    // --- escape ---

    @Test
    public void testEscapeHtmlBody() {
        exchange.getIn().setBody("Monday & Tuesday <b>\"quoted\"</b>");
        assertEquals("Monday &amp; Tuesday &lt;b&gt;&quot;quoted&quot;&lt;/b&gt;", evaluate("escape(html)", String.class));
    }

    @Test
    public void testEscapeXmlHeader() {
        exchange.getIn().setHeader("title", "Tom & Jerry's <Show>");
        assertEquals("Tom &amp; Jerry&apos;s &lt;Show&gt;", evaluate("escape(xml, ${header.title})", String.class));
    }

    @Test
    public void testEscapeJson() {
        exchange.getIn().setBody("say \"hi\"\nbye");
        assertEquals("say \\\"hi\\\"\\nbye", evaluate("escape(json)", String.class));
    }

    @Test
    public void testEscapeJs() {
        exchange.getIn().setBody("it's </script>");
        assertEquals("it\\'s <\\/script>", evaluate("escape(js)", String.class));
        assertEquals("it\\'s <\\/script>", evaluate("escape(javascript)", String.class));
    }

    @Test
    public void testEscapeSql() {
        exchange.getIn().setHeader("name", "O'Reilly");
        assertEquals("O''Reilly", evaluate("escape(sql,${header.name})", String.class));
    }

    @Test
    public void testEscapeUrl() {
        assertEquals("Camel%20in%20Action%3F", evaluate("escape(url, 'Camel in Action?')", String.class));
        exchange.getIn().setBody("a&b=c");
        assertEquals("a%26b%3Dc", evaluate("escape(url)", String.class));
    }

    @Test
    public void testEscapeKindCaseInsensitive() {
        exchange.getIn().setBody("a & b");
        assertEquals("a &amp; b", evaluate("escape(HTML)", String.class));
    }

    @Test
    public void testEscapeNestedFunctionWithComma() {
        exchange.getIn().setBody("<a>");
        assertEquals("&lt;a&gt;-&lt;a&gt;", evaluate("escape(html, ${concat(${body},${body},-)})", String.class));
    }

    @Test
    public void testEscapeNonStringBody() {
        exchange.getIn().setBody(42);
        assertEquals("42", evaluate("escape(html)", String.class));
    }

    @Test
    public void testEscapeNullBody() {
        exchange.getIn().setBody(null);
        assertNull(evaluate("escape(html)", String.class));
    }

    @Test
    public void testEscapeUnknownKind() {
        SimpleParserException e = assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "escape(csv)", 0));
        assertTrue(e.getMessage().contains("Unknown escape kind: csv"));
    }

    @Test
    public void testEscapeMissingKind() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "escape()", 0));
    }
}
