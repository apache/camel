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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

public class PredicateBinaryCoerceTypeTest extends TestSupport {
    protected Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    @Test
    public void testIsNull() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("123");
        assertDoesNotMatch(PredicateBuilder.isNull(a));

        a = ExpressionBuilder.constantExpression(null);
        assertMatches(PredicateBuilder.isNull(a));
    }

    @Test
    public void testIsNotNull() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("123");
        assertMatches(PredicateBuilder.isNotNull(a));

        a = ExpressionBuilder.constantExpression(null);
        assertDoesNotMatch(PredicateBuilder.isNotNull(a));
    }

    @Test
    public void testEqual() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("123");
        Expression b = ExpressionBuilder.constantExpression(Integer.valueOf("123"));
        assertMatches(PredicateBuilder.isEqualTo(a, b));

        // reverse the type and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("123"));
        b = ExpressionBuilder.constantExpression("123");
        assertMatches(PredicateBuilder.isEqualTo(a, b));
    }

    @Test
    public void testEqualWithNull() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("123");
        Expression b = ExpressionBuilder.constantExpression(null);
        assertDoesNotMatch(PredicateBuilder.isEqualTo(a, b));

        // reverse the type and try again
        a = ExpressionBuilder.constantExpression(null);
        b = ExpressionBuilder.constantExpression("123");
        assertDoesNotMatch(PredicateBuilder.isEqualTo(a, b));

        // try two null values
        a = ExpressionBuilder.constantExpression(null);
        b = ExpressionBuilder.constantExpression(null);
        assertMatches(PredicateBuilder.isEqualTo(a, b));
    }

    @Test
    public void testNotEqual() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("123");
        Expression b = ExpressionBuilder.constantExpression(Integer.valueOf("123"));

        assertDoesNotMatch(PredicateBuilder.isNotEqualTo(a, b));

        a = ExpressionBuilder.constantExpression("333");
        assertMatches(PredicateBuilder.isNotEqualTo(a, b));
    }

    @Test
    public void testNotEqualWithNull() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("123");
        Expression b = ExpressionBuilder.constantExpression(null);
        assertMatches(PredicateBuilder.isNotEqualTo(a, b));

        // reverse the type and try again
        a = ExpressionBuilder.constantExpression(null);
        b = ExpressionBuilder.constantExpression("123");
        assertMatches(PredicateBuilder.isNotEqualTo(a, b));

        // try two null values
        a = ExpressionBuilder.constantExpression(null);
        b = ExpressionBuilder.constantExpression(null);
        assertDoesNotMatch(PredicateBuilder.isNotEqualTo(a, b));
    }

    @Test
    public void testGreatherThan() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("200");
        Expression b = ExpressionBuilder.constantExpression(Integer.valueOf("100"));

        assertMatches(PredicateBuilder.isGreaterThan(a, b));
        assertDoesNotMatch(PredicateBuilder.isGreaterThan(b, a));

        // reverse the types and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("100"));
        b = ExpressionBuilder.constantExpression("200");

        assertDoesNotMatch(PredicateBuilder.isGreaterThan(a, b));
        assertMatches(PredicateBuilder.isGreaterThan(b, a));
    }

    @Test
    public void testGreatherThanOrEqual() throws Exception {
        // greather than
        Expression a = ExpressionBuilder.constantExpression("200");
        Expression b = ExpressionBuilder.constantExpression(Integer.valueOf("100"));

        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(a, b));
        assertDoesNotMatch(PredicateBuilder.isGreaterThanOrEqualTo(b, a));

        // reverse the types and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("100"));
        b = ExpressionBuilder.constantExpression("200");

        assertDoesNotMatch(PredicateBuilder.isGreaterThanOrEqualTo(a, b));
        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(b, a));

        // equal
        a = ExpressionBuilder.constantExpression("100");
        b = ExpressionBuilder.constantExpression(Integer.valueOf("100"));

        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(a, b));
        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(b, a));

        // reverse the types and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("100"));
        b = ExpressionBuilder.constantExpression("100");

        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(a, b));
        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(b, a));
    }

    @Test
    public void testLessThan() throws Exception {
        Expression a = ExpressionBuilder.constantExpression("100");
        Expression b = ExpressionBuilder.constantExpression(Integer.valueOf("200"));

        assertMatches(PredicateBuilder.isLessThan(a, b));
        assertDoesNotMatch(PredicateBuilder.isLessThan(b, a));

        // reverse the types and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("100"));
        b = ExpressionBuilder.constantExpression("200");

        assertMatches(PredicateBuilder.isLessThan(a, b));
        assertDoesNotMatch(PredicateBuilder.isLessThan(b, a));
    }

    @Test
    public void testLessThanOrEqual() throws Exception {
        // less than
        Expression a = ExpressionBuilder.constantExpression("100");
        Expression b = ExpressionBuilder.constantExpression(Integer.valueOf("200"));

        assertMatches(PredicateBuilder.isLessThanOrEqualTo(a, b));
        assertDoesNotMatch(PredicateBuilder.isLessThanOrEqualTo(b, a));

        // reverse the types and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("200"));
        b = ExpressionBuilder.constantExpression("100");

        assertDoesNotMatch(PredicateBuilder.isLessThanOrEqualTo(a, b));
        assertMatches(PredicateBuilder.isLessThanOrEqualTo(b, a));

        // equal
        a = ExpressionBuilder.constantExpression("100");
        b = ExpressionBuilder.constantExpression(Integer.valueOf("100"));

        assertMatches(PredicateBuilder.isLessThanOrEqualTo(a, b));
        assertMatches(PredicateBuilder.isLessThanOrEqualTo(b, a));

        // reverse the types and try again
        a = ExpressionBuilder.constantExpression(Integer.valueOf("100"));
        b = ExpressionBuilder.constantExpression("100");

        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(a, b));
        assertMatches(PredicateBuilder.isGreaterThanOrEqualTo(b, a));
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Message in = exchange.getIn();
        in.setBody("Hello there!");
        in.setHeader("name", "James");
        in.setHeader("location", "Islington,London,UK");
        in.setHeader("size", 10);
    }

    protected void assertMatches(Predicate predicate) {
        assertPredicateMatches(predicate, exchange);
    }

    protected void assertDoesNotMatch(Predicate predicate) {
        assertPredicateDoesNotMatch(predicate, exchange);
    }
}
