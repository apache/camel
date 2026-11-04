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

    @Test
    public void testParenthesesOutsideANestedFunctionAreText() {
        // a parenthesis outside a nested ${...} is text, as before: only the commas of a nested function do not split
        exchange.getIn().setBody("order (42) done");
        assertEquals("42", evaluate("substringBetween(${body},(,))", String.class));
        exchange.getIn().setHeader("s", "abcdef");
        assertEquals("bc", evaluate("substringBetween(${header.s.substring(0,4)},a,d)", String.class));
        exchange.getIn().setBody("a(b");
        assertEquals("axb", evaluate("replace((,x)", String.class));
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

    @Test
    void testArgumentsWithASpaceAfterTheComma() {
        // a space after the comma is not part of the next argument, as in the collection functions (CAMEL-24969)
        exchange.getIn().setBody("Hello great big World");
        assertEquals("Hell0 great big W0rld", evaluate("replace('o', '0')", String.class));
        assertEquals("ello great big Worl", evaluate("substring(1, -1)", String.class));
        assertEquals("Hello", evaluate("substringBefore(${body}, ' ')", String.class));
        assertEquals("great big World", evaluate("substringAfter(${body}, 'Hello ')", String.class));
        assertEquals(" big ", evaluate("substringBetween(${body}, 'great', 'World')", String.class));
        assertEquals(true, evaluate("contains(${body}, 'hello')", Boolean.class));
        // the spaces inside the quotes are kept
        assertEquals("Hello GREAT big World", evaluate("replace(' great', ' GREAT')", String.class));
    }

    @Test
    void testQuoteInsideAnArgumentIsKept() {
        // only the quotes around a whole argument are removed: a'b'c is the text a'b'c (it was abc)
        exchange.getIn().setBody("a'b'c and abc");
        assertEquals("x and abc", evaluate("replace(a'b'c,x)", String.class));
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

    @Test
    void testValQuotedLiteral() {
        assertEquals("Hello", evaluate("val('Hello')", String.class));
        assertEquals("Hello World", evaluate("val(\"Hello World\")", String.class));
        assertEquals("", evaluate("val('')", String.class));
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

    @Test
    void testEmptyArgumentIsKept() {
        // an empty argument is kept, so the next argument does not take its place
        exchange.getIn().setBody("abc");
        exchange.getIn().setHeader("s", "abcdef");
        assertEquals("acdef", evaluate("replace(b,,${header.s})", String.class));
        assertEquals("ac", evaluate("replace(b,)", String.class));
        // an empty expression is the same as leaving it out: the body
        assertEquals("aXc", evaluate("replace(b,X,)", String.class));
        assertEquals("bc", evaluate("substring(1,)", String.class));
        assertEquals("a", evaluate("substringBefore(,b)", String.class));
        assertEquals("c", evaluate("substringAfter(,b)", String.class));
        assertEquals("b", evaluate("substringBetween(,a,c)", String.class));
        assertEquals(true, evaluate("contains(,bc)", Boolean.class));
    }

    @Test
    void testEmptyArgumentWithoutDefaultIsRefused() {
        exchange.getIn().setBody("abc");
        assertThrows(SimpleParserException.class, () -> createFactory().createFunction(context, "replace(,X)", 0));
        assertThrows(SimpleParserException.class, () -> createFactory().createFunction(context, "substring(,2)", 0));
    }

    @Test
    void testCommaInsideANestedFunction() {
        // a comma inside a nested function does not split the argument
        exchange.getIn().setBody("abc");
        exchange.getIn().setHeader("s", "abcdef");
        assertEquals("b", evaluate("substring(1,2,${header.s.substring(0,4)})", String.class));
        assertEquals("bc", evaluate("substringBefore(${header.s.substring(1,4)},d)", String.class));
        assertEquals("c", evaluate("substringAfter(${header.s.substring(1,3)},b)", String.class));
        assertEquals("cd", evaluate("substringBetween(${header.s.substring(1,5)},b,e)", String.class));
        assertEquals(true, evaluate("contains(${header.s.substring(0,2)},ab)", Boolean.class));
        // the from and to of replace are text, not expressions
        assertEquals("a${header.s.substring(1,3)}c", evaluate("replace(b,${header.s.substring(1,3)})", String.class));
        assertEquals("aXcdef", evaluate("replace(b,X,${header.s.substring(0,6)})", String.class));
    }

    @Test
    void testEmptyTextArgumentIsRefused() {
        // an empty argument that has no default is refused, so a trailing comma is not read as an empty text
        for (String function : new String[] {
                "contains(xyz,)", "substringBefore(cd,)", "substringAfter(cd,)", "substringBetween(x,,c)",
                "substringBetween(x,a,)", "replace(a,b,${body},)" }) {
            assertThrows(SimpleParserException.class, () -> createFactory().createFunction(context, function, 0),
                    function);
        }
    }

    @Test
    void testQuotedEmptyArgumentIsTheEmptyText() {
        // '' is the empty text, not an argument left out
        exchange.getIn().setBody("abc");
        assertEquals(true, evaluate("contains(${body},'')", Boolean.class));
        assertEquals("abc", evaluate("substringAfter(${body},'')", String.class));
        assertEquals("ac", evaluate("replace(b,'')", String.class));
        // only the quotes around a whole argument are removed, so a lone quote at the end is kept
        assertEquals("ax'c", evaluate("replace(b,x')", String.class));
    }

    @Test
    void testQuotedParenthesisIsText() {
        // a parenthesis in an argument is quoted, so it does not nest
        exchange.getIn().setBody("a(b");
        assertEquals("axb", evaluate("replace('(',x)", String.class));
    }
}
