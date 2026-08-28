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

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
