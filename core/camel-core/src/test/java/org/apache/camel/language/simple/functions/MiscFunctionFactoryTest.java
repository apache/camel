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
import java.util.Map;

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MiscFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new MiscFunctionFactory();
    }

    // --- isEmpty ---

    @Test
    public void testIsEmptyTrue() {
        exchange.getIn().setBody("");
        assertTrue((Boolean) evaluate("isEmpty(${body})"));
    }

    @Test
    public void testIsEmptyFalse() {
        exchange.getIn().setBody("hello");
        assertFalse((Boolean) evaluate("isEmpty(${body})"));
    }

    // --- isAlpha ---

    @Test
    public void testIsAlphaTrue() {
        exchange.getIn().setBody("hello");
        assertTrue((Boolean) evaluate("isAlpha(${body})"));
    }

    @Test
    public void testIsAlphaFalse() {
        exchange.getIn().setBody("hello123");
        assertFalse((Boolean) evaluate("isAlpha(${body})"));
    }

    // --- isAlphaNumeric ---

    @Test
    public void testIsAlphaNumericTrue() {
        exchange.getIn().setBody("hello123");
        assertTrue((Boolean) evaluate("isAlphaNumeric(${body})"));
    }

    @Test
    public void testIsAlphaNumericFalse() {
        exchange.getIn().setBody("hello 123");
        assertFalse((Boolean) evaluate("isAlphaNumeric(${body})"));
    }

    // --- isNumeric ---

    @Test
    public void testIsNumericTrue() {
        exchange.getIn().setBody("123");
        assertTrue((Boolean) evaluate("isNumeric(${body})"));
    }

    @Test
    public void testIsNumericFalse() {
        exchange.getIn().setBody("abc");
        assertFalse((Boolean) evaluate("isNumeric(${body})"));
    }

    // --- not ---

    @Test
    public void testNotEmpty() {
        exchange.getIn().setBody("");
        assertTrue((Boolean) evaluate("not(${body})"));
    }

    @Test
    public void testNotNonEmpty() {
        exchange.getIn().setBody("hello");
        assertFalse((Boolean) evaluate("not(${body})"));
    }

    // --- convertTo ---

    @Test
    public void testConvertToString() {
        exchange.getIn().setBody(42);
        assertEquals("42", evaluate("convertTo(java.lang.String)", String.class));
    }

    @Test
    public void testConvertToInteger() {
        exchange.getIn().setBody("42");
        assertEquals(42, evaluate("convertTo(java.lang.Integer)", Integer.class));
    }

    // --- uuid ---

    @Test
    public void testUuid() {
        assertNotNull(evaluate("uuid", String.class));
    }

    // --- hash ---

    @Test
    public void testHash() {
        exchange.getIn().setBody("hello");
        String result = evaluate("hash(${body})", String.class);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // --- empty / newEmpty ---

    @Test
    public void testEmpty() {
        Object result = evaluate("empty(List)");
        assertInstanceOf(List.class, result);
        assertTrue(((List<?>) result).isEmpty());
    }

    @Test
    public void testNewEmpty() {
        Object result = evaluate("newEmpty(Map)");
        assertInstanceOf(Map.class, result);
        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    // --- throwException ---

    @Test
    public void testThrowExceptionDefaultTypeIsIllegalArgumentException() {
        // pre-fix: catch(Exception e) around the entire body caught the RuntimeException that was
        // just thrown and re-wrapped it in new RuntimeException(e), producing
        // RuntimeException(IllegalArgumentException) instead of a bare IllegalArgumentException.
        // onException(IllegalArgumentException.class) handlers would never match.
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> evaluate("throwException('boom')"));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    public void testThrowExceptionCustomRuntimeTypeIsNotDoubleWrapped() {
        // A custom RuntimeException subclass must arrive unwrapped so that typed onException
        // handlers can catch it. Pre-fix: it arrived wrapped in RuntimeException(CustomEx).
        exchange.setProperty("type", "java.lang.IllegalStateException");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> evaluate("throwException('state failure',java.lang.IllegalStateException)"));
        assertEquals("state failure", ex.getMessage());
    }

    // --- pad ---

    @Test
    public void testPadNullBodyReturnsNull() {
        // pre-fix: answer.length() threw NullPointerException when the body was null
        exchange.getIn().setBody(null);
        assertNull(evaluate("pad(${body},5)"));
    }

    @Test
    public void testPadNullWidthThrowsIllegalArgument() {
        // pre-fix: int width = null unboxed to NullPointerException with no useful message
        exchange.getIn().setBody("hi");
        // header absent -> width expression evaluates to null
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluate("pad(${body},${header.missingWidth})"));
    }

    // --- iif ---

    @Test
    public void testIifTrue() {
        exchange.getIn().setBody(true);
        assertEquals("'yes'", evaluate("iif(${body},'yes','no')", String.class));
    }

    @Test
    public void testIifFalse() {
        exchange.getIn().setBody(false);
        assertEquals("'no'", evaluate("iif(${body},'yes','no')", String.class));
    }
}
