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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.junit.jupiter.api.Test;

public class SimpleParserExpressionTest extends ExchangeTestSupport {

    @Test
    public void testSimpleParserEol() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Hello", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("Hello", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleSingleQuote() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "'Hello'", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello'", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleStringList() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "\"Hello\" \"World\"", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("\"Hello\" \"World\"", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleSingleQuoteWithFunction() {
        exchange.getIn().setBody("World");
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "'Hello ${body} how are you?'", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello World how are you?'", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleSingleQuoteWithFunctionBodyAs() {
        exchange.getIn().setBody("World");
        SimpleExpressionParser parser =
                new SimpleExpressionParser(context, "'Hello ${bodyAs(String)} how are you?'", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello World how are you?'", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleSingleQuoteEol() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "'Hello' World", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("'Hello' World", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleFunction() {
        exchange.getIn().setBody("World");
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("World", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleSingleQuoteDollar() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Pay 200$ today", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("Pay 200$ today", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleSingleQuoteDollarEnd() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Pay 200$", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("Pay 200$", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleUnaryInc() {
        exchange.getIn().setBody("122");
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}++", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("123", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleUnaryDec() {
        exchange.getIn().setBody("122");
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}--", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("121", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testSimpleUnaryIncInt() {
        exchange.getIn().setBody(122);
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}++", true, null);
        Expression exp = parser.parseExpression();

        assertEquals(Integer.valueOf(123), exp.evaluate(exchange, Integer.class));
    }

    @Test
    public void testSimpleUnaryDecInt() {
        exchange.getIn().setBody(122);
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}--", true, null);
        Expression exp = parser.parseExpression();

        assertEquals(Integer.valueOf(121), exp.evaluate(exchange, Integer.class));
    }

    @Test
    public void testHeaderNestedFunction() {
        exchange.getIn().setBody("foo");
        exchange.getIn().setHeader("foo", "abc");
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${header.${body}}", true, null);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        assertEquals("abc", obj);
    }

    @Test
    public void testBodyAsNestedFunction() {
        exchange.getIn().setBody("123");
        exchange.getIn().setHeader("foo", "Integer");
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${bodyAs(${header.foo})}", true, null);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        Integer num = assertIsInstanceOf(Integer.class, obj);
        assertEquals(123, num.intValue());
    }

    @Test
    public void testThreeNestedFunctions() {
        exchange.getIn().setBody("123");
        exchange.getIn().setHeader("foo", "Int");
        exchange.getIn().setHeader("bar", "e");
        exchange.getIn().setHeader("baz", "ger");
        SimpleExpressionParser parser =
                new SimpleExpressionParser(context, "${bodyAs(${header.foo}${header.bar}${header.baz})}", true, null);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        Integer num = assertIsInstanceOf(Integer.class, obj);
        assertEquals(123, num.intValue());
    }

    @Test
    public void testNestedNestedFunctions() {
        exchange.getIn().setBody("123");
        exchange.getIn().setHeader("foo", "Integer");
        exchange.getIn().setHeader("bar", "foo");
        SimpleExpressionParser parser =
                new SimpleExpressionParser(context, "${bodyAs(${header.${header.bar}})}", true, null);
        Expression exp = parser.parseExpression();

        Object obj = exp.evaluate(exchange, Object.class);
        assertNotNull(obj);
        Integer num = assertIsInstanceOf(Integer.class, obj);
        assertEquals(123, num.intValue());
    }

    @Test
    public void testSimpleMap() {
        Map<String, String> map = new HashMap<>();
        map.put("foo", "123");
        map.put("foo bar", "456");

        exchange.getIn().setBody(map);

        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body[foo]}", true, null);
        Expression exp = parser.parseExpression();
        assertEquals("123", exp.evaluate(exchange, Object.class));

        parser = new SimpleExpressionParser(context, "${body['foo bar']}", true, null);
        exp = parser.parseExpression();
        assertEquals("456", exp.evaluate(exchange, Object.class));
    }

    @Test
    public void testUnaryLenient() {
        exchange.getIn().setHeader("JMSMessageID", "JMSMessageID-123");
        exchange.getIn().setBody("THE MSG ID ${header.JMSMessageID} isA --");

        SimpleExpressionParser parser =
                new SimpleExpressionParser(context, "THE MSG ID ${header.JMSMessageID} isA --", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("THE MSG ID JMSMessageID-123 isA --", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testUnaryLenient2() {
        exchange.getIn().setHeader("JMSMessageID", "JMSMessageID-123");
        exchange.getIn().setBody("------------THE MSG ID ${header.JMSMessageID}------------");

        SimpleExpressionParser parser = new SimpleExpressionParser(
                context, "------------THE MSG ID ${header.JMSMessageID}------------", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("------------THE MSG ID JMSMessageID-123------------", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testUnaryLenient3() {
        exchange.getIn().setHeader("JMSMessageID", "JMSMessageID-123");
        exchange.getIn().setBody("------------ THE MSG ID ${header.JMSMessageID} ------------");

        SimpleExpressionParser parser = new SimpleExpressionParser(
                context, "------------ THE MSG ID ${header.JMSMessageID} ------------", true, null);
        Expression exp = parser.parseExpression();

        assertEquals("------------ THE MSG ID JMSMessageID-123 ------------", exp.evaluate(exchange, String.class));
    }
}
