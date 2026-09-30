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
package org.apache.camel.language.simple;

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24967: how the function names are matched and the arguments are split.
 */
public class SimpleFunctionArgumentsTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testQuotesInsideANestedFunctionAreKept() {
        exchange.getMessage().setBody("a,b,c");
        assertExpression("${size(${body.split(',')})}", 3);
    }

    @Test
    public void testConvertToWithParenthesesInTheExpression() {
        exchange.getMessage().setHeader("foo", " 42 ");
        assertExpression("${convertTo(${header.foo.trim()},Integer)}", 42);
    }

    @Test
    public void testCommaInsideQuotes() {
        exchange.getMessage().setBody("World");
        // concat(exp) appends to the message body
        assertExpression("${concat('Hello, ')}", "WorldHello, ");
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${throwException('Order failed, retry later')}")
                        .evaluate(exchange, Object.class));
        assertEquals("Order failed, retry later", e.getMessage());
        String hash = context.resolveLanguage("simple").createExpression("${hash('a,b')}").evaluate(exchange, String.class);
        exchange.getMessage().setBody("a,b");
        assertEquals(context.resolveLanguage("simple").createExpression("${hash(${body})}").evaluate(exchange, String.class),
                hash);
    }

    @Test
    public void testTooFewArguments() {
        exchange.getMessage().setHeader("foo", 1);
        // with a nested function the arguments are parsed when evaluated
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${iif(${header.foo} > 0,'yes')}")
                        .evaluate(exchange, Object.class));
        assertTrue(e.getMessage().contains("Valid syntax: ${iif(predicate,trueExpression,falseExpression)}"), e.getMessage());
        e = assertThrows(SimpleIllegalSyntaxException.class,
                () -> context.resolveLanguage("simple").createExpression("${replace(a)}"));
        assertTrue(e.getMessage().contains("Valid syntax: ${replace(from,to)}"), e.getMessage());
    }

    @Test
    public void testBeanTypeWithPackage() {
        assertExpression("${bean:type:java.lang.System.lineSeparator}", System.lineSeparator());
        assertExpression("${bean:type:org.apache.camel.language.simple.SimpleFunctionArgumentsTest$MyStatic.hello}", "Hi");
    }

    @Test
    public void testQuotedKeyIsNotOgnl() {
        exchange.getMessage().setHeader("a", "xyz");
        exchange.getMessage().setHeader("a.b", "yes");
        assertExpression("${header['a.b']}", "yes");
        exchange.getMessage().removeHeader("a.b");
        assertExpression("${header['a.b']}", null);
        exchange.setVariable("x.y", "var");
        assertExpression("${variable['x.y']}", "var");
        exchange.setProperty("p.q", "prop");
        assertExpression("${exchangeProperty['p.q']}", "prop");
    }

    @Test
    public void testNamesMustNotBeGluedToAPrefix() {
        exchange.getMessage().setHeader("foo", "bar");
        assertThrows(SimpleIllegalSyntaxException.class,
                () -> context.resolveLanguage("simple").createExpression("${headerfoo}"));
        assertThrows(SimpleIllegalSyntaxException.class,
                () -> context.resolveLanguage("simple").createExpression("${uuidv7}"));
        assertThrows(SimpleIllegalSyntaxException.class,
                () -> context.resolveLanguage("simple").createExpression("${exceptionInfo}"));
        // the proper forms still work
        assertExpression("${header.foo}", "bar");
        assertExpression("${header:foo}", "bar");
        assertExpression("${header[foo]}", "bar");
        assertExpression("${headers.foo}", "bar");
    }

    public static class MyStatic {
        public static String hello() {
            return "Hi";
        }
    }
}
