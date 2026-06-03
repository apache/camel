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

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the mutable state holders (boolean[] / int[]) used in place of AtomicBoolean / AtomicInteger in
 * the predicate and expression parsers. Exercises balanced-quote and function tracking logic.
 */
public class SimpleParserMutableStateTest extends ExchangeTestSupport {

    // --- predicate parser (boolean[] startSingle / startDouble / startFunction) ---

    @Test
    public void testPredicateSingleQuoteBalance() {
        exchange.getIn().setBody("hello");

        SimplePredicateParser parser = new SimplePredicateParser(context, "${body} == 'hello'", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testPredicateDoubleQuoteBalance() {
        exchange.getIn().setBody("world");

        SimplePredicateParser parser = new SimplePredicateParser(context, "${body} == \"world\"", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testPredicateUnclosedSingleQuoteThrows() {
        SimplePredicateParser parser = new SimplePredicateParser(context, "${body} == 'unclosed", true, null);
        assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate);
    }

    @Test
    public void testPredicateUnclosedFunctionThrows() {
        SimplePredicateParser parser = new SimplePredicateParser(context, "${body", true, null);
        assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate);
    }

    @Test
    public void testPredicateMultipleFunctionBalance() {
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("key", "body");

        SimplePredicateParser parser
                = new SimplePredicateParser(context, "${body} == 'hello' && ${header.key} != null", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    // --- expression parser (int[] functions counter) ---

    @Test
    public void testExpressionFunctionCounter() {
        exchange.getIn().setBody("world");

        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Hello ${body}!", true, null);
        Expression expression = parser.parseExpression();
        expression.init(context);

        assertEquals("Hello world!", expression.evaluate(exchange, String.class));
    }

    @Test
    public void testExpressionMultipleFunctions() {
        exchange.getIn().setBody("Camel");
        exchange.getIn().setHeader("version", "4.x");

        SimpleExpressionParser parser
                = new SimpleExpressionParser(context, "${body} ${header.version}", true, null);
        Expression expression = parser.parseExpression();
        expression.init(context);

        assertEquals("Camel 4.x", expression.evaluate(exchange, String.class));
    }

    @Test
    public void testExpressionUnclosedFunctionThrows() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Hello ${body", true, null);
        assertThrows(SimpleIllegalSyntaxException.class, parser::parseExpression);
    }
}
