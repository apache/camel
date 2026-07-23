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
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for prepareLogicalExpressions in SimplePredicateParser. Covers the bug where the right-hand token
 * was reported as the left-hand token in the "does not support right hand side token" error message.
 */
public class SimplePredicateParserLogicalTest extends ExchangeTestSupport {

    @Test
    public void testAndWithFunctionRightHandSide() {
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("active", true);

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} == 'hello' && ${header.active} == true", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testAndWithLiteralRightHandSide() {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} == 'foo' && ${body} != 'bar'", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testOrWithFunctionRightHandSide() {
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("score", 5);

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} == 'world' || ${header.score} > 3", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testOrAllFalse() {
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("score", 1);

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} == 'world' || ${header.score} > 3", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertFalse(predicate.matches(exchange));
    }

    @Test
    public void testAndWithNumericRightHandSide() {
        exchange.getIn().setBody(42);

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} > 10 && ${body} < 100", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testAndWithNullRightHandSide() {
        exchange.getIn().setBody("present");
        exchange.getIn().setHeader("tag", "x");

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} != null && ${header.tag} != null", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testChainedAndOrLogicalOperators() {
        exchange.getIn().setBody("alpha");
        exchange.getIn().setHeader("flag", true);

        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} == 'alpha' && ${header.flag} == true || ${body} == 'beta'", true, null);
        Predicate predicate = parser.parsePredicate();
        predicate.init(context);

        assertTrue(predicate.matches(exchange));
    }

    @Test
    public void testInvalidRightHandSideReportsRightToken() {
        // "&&" followed by a bare numeric (not a binary expression) is invalid syntax.
        // The error message must say "right hand side token 42" (the actual offending token),
        // not "right hand side token ==" (which would indicate the left-hand node was reported).
        SimplePredicateParser parser = new SimplePredicateParser(
                context, "${body} == 'foo' && 42", true, null);
        SimpleIllegalSyntaxException ex = assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate);
        assertTrue(ex.getMessage().contains("right hand side token 42"),
                "Error message should say 'right hand side token 42', but was: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("right hand side token =="),
                "Error message must not say 'right hand side token ==', but was: " + ex.getMessage());
    }
}
