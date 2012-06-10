/**
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

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;

/**
 *
 */
public class SimpleParserExpressionTest extends ExchangeTestSupport {

    public void testSimpleParserEol() throws Exception {
        SimpleExpressionParser parser = new SimpleExpressionParser("Hello", true);
        Expression exp = parser.parseExpression();

        assertEquals("Hello", exp.evaluate(exchange, String.class));
    }

    public void testSimpleSingleQuote() throws Exception {
        SimpleExpressionParser parser = new SimpleExpressionParser("'Hello'", true);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello'", exp.evaluate(exchange, String.class));
    }

    public void testSimpleSingleQuoteWithFunction() throws Exception {
        exchange.getIn().setBody("World");
        SimpleExpressionParser parser = new SimpleExpressionParser("'Hello ${body} how are you?'", true);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello World how are you?'", exp.evaluate(exchange, String.class));
    }

    public void testSimpleSingleQuoteWithFunctionBodyAs() throws Exception {
        exchange.getIn().setBody("World");
        SimpleExpressionParser parser = new SimpleExpressionParser("'Hello ${bodyAs(String)} how are you?'", true);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello World how are you?'", exp.evaluate(exchange, String.class));
    }

    public void testSimpleSingleQuoteEol() throws Exception {
        SimpleExpressionParser parser = new SimpleExpressionParser("'Hello' World", true);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello' World", exp.evaluate(exchange, String.class));
    }

    public void testSimpleFunction() throws Exception {
        exchange.getIn().setBody("World");
        SimpleExpressionParser parser = new SimpleExpressionParser("${body}", true);
        Expression exp = parser.parseExpression();

        assertEquals("World", exp.evaluate(exchange, String.class));
    }

    public void testSimpleSingleQuoteDollar() throws Exception {
        SimpleExpressionParser parser = new SimpleExpressionParser("Pay 200$ today", true);
        Expression exp = parser.parseExpression();

        assertEquals("Pay 200$ today", exp.evaluate(exchange, String.class));
    }

    public void testSimpleSingleQuoteDollarEnd() throws Exception {
        SimpleExpressionParser parser = new SimpleExpressionParser("Pay 200$", true);
        Expression exp = parser.parseExpression();

        assertEquals("Pay 200$", exp.evaluate(exchange, String.class));
    }

    public void testSimpleUnaryInc() throws Exception {
        exchange.getIn().setBody("122");
        SimpleExpressionParser parser = new SimpleExpressionParser("${body}++", true);
        Expression exp = parser.parseExpression();

        assertEquals("123", exp.evaluate(exchange, String.class));
    }

    public void testSimpleUnaryDec() throws Exception {
        exchange.getIn().setBody("122");
        SimpleExpressionParser parser = new SimpleExpressionParser("${body}--", true);
        Expression exp = parser.parseExpression();

        assertEquals("121", exp.evaluate(exchange, String.class));
    }

    public void testSimpleUnaryIncInt() throws Exception {
        exchange.getIn().setBody(122);
        SimpleExpressionParser parser = new SimpleExpressionParser("${body}++", true);
        Expression exp = parser.parseExpression();

        assertEquals(Integer.valueOf(123), exp.evaluate(exchange, Integer.class));
    }

    public void testSimpleUnaryDecInt() throws Exception {
        exchange.getIn().setBody(122);
        SimpleExpressionParser parser = new SimpleExpressionParser("${body}--", true);
        Expression exp = parser.parseExpression();

        assertEquals(Integer.valueOf(121), exp.evaluate(exchange, Integer.class));
    }

    public void testHeaderNestedFunction() throws Exception {
        exchange.getIn().setBody("foo");
        exchange.getIn().setHeader("foo", "abc");
        SimpleExpressionParser parser = new SimpleExpressionParser("${header.${body}}", true);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        assertEquals("abc", obj);
    }

    public void testBodyAsNestedFunction() throws Exception {
        exchange.getIn().setBody("123");
        exchange.getIn().setHeader("foo", "Integer");
        SimpleExpressionParser parser = new SimpleExpressionParser("${bodyAs(${header.foo})}", true);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        Integer num = assertIsInstanceOf(Integer.class, obj);
        assertEquals(123, num.intValue());
    }

    public void testThreeNestedFunctions() throws Exception {
        exchange.getIn().setBody("123");
        exchange.getIn().setHeader("foo", "Int");
        exchange.getIn().setHeader("bar", "e");
        exchange.getIn().setHeader("baz", "ger");
        SimpleExpressionParser parser = new SimpleExpressionParser("${bodyAs(${header.foo}${header.bar}${header.baz})}", true);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        Integer num = assertIsInstanceOf(Integer.class, obj);
        assertEquals(123, num.intValue());
    }

    public void testNestedNestedFunctions() throws Exception {
        exchange.getIn().setBody("123");
        exchange.getIn().setHeader("foo", "Integer");
        exchange.getIn().setHeader("bar", "foo");
        SimpleExpressionParser parser = new SimpleExpressionParser("${bodyAs(${header.${header.bar}})}", true);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        Integer num = assertIsInstanceOf(Integer.class, obj);
        assertEquals(123, num.intValue());
    }

}
