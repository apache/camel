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
import org.apache.camel.Predicate;

/**
 *
 */
public class SimpleParserPredicateTest extends ExchangeTestSupport {

    public void testSimpleEq() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("${body} == 'foo'", true);
        Predicate pre = parser.parsePredicate();

        assertTrue(pre.matches(exchange));
    }

    public void testSimpleEqNumeric() throws Exception {
        exchange.getIn().setBody(123);

        SimplePredicateParser parser = new SimplePredicateParser("${body} == 123", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleEqFunctionFunction() throws Exception {
        exchange.getIn().setBody(122);
        exchange.getIn().setHeader("val", 122);

        SimplePredicateParser parser = new SimplePredicateParser("${body} == ${header.val}", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleEqFunctionNumeric() throws Exception {
        exchange.getIn().setBody(122);

        SimplePredicateParser parser = new SimplePredicateParser("${body} == 122", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleGtFunctionNumeric() throws Exception {
        exchange.getIn().setBody(122);

        SimplePredicateParser parser = new SimplePredicateParser("${body} > 120", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleUnaryInc() throws Exception {
        exchange.getIn().setBody(122);

        SimplePredicateParser parser = new SimplePredicateParser("${body}++ == 123", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleUnaryDec() throws Exception {
        exchange.getIn().setBody(122);

        SimplePredicateParser parser = new SimplePredicateParser("${body}-- == 121", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleEqFunctionBoolean() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == true", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleEqFunctionBooleanSpaces() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high}   ==     true", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleLogicalAnd() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);
        exchange.getIn().setHeader("foo", 123);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == true && ${header.foo} == 123", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleLogicalOr() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);
        exchange.getIn().setHeader("foo", 123);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == false || ${header.foo} == 123", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleLogicalAndAnd() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);
        exchange.getIn().setHeader("foo", 123);
        exchange.getIn().setHeader("bar", "beer");

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == true && ${header.foo} == 123 && ${header.bar} == 'beer'", true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleManyAndLogical() throws Exception {
        exchange.getIn().setBody("Hello");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            exchange.getIn().setHeader("foo" + i, i);
            sb.append("${header.foo").append(i).append("} == ").append(i);
            if (i < 9) {
                sb.append(" && ");
            }
        }

        SimplePredicateParser parser = new SimplePredicateParser(sb.toString(), true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }

    public void testSimpleManyOrLogical() throws Exception {
        exchange.getIn().setBody("Hello");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("${header.foo").append(i).append("} == ").append(i);
            if (i < 9) {
                sb.append(" || ");
            }
        }
        sb.append(" || ${body} == 'Hello'");

        SimplePredicateParser parser = new SimplePredicateParser(sb.toString(), true);
        Predicate pre = parser.parsePredicate();

        assertTrue("Should match", pre.matches(exchange));
    }
    
    public void testSimpleExpressionPredicate() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("number", "1234");
        SimplePredicateParser parser = new SimplePredicateParser("${in.header.number} regex '\\d{4}'", true);
        Predicate pre = parser.parsePredicate();
        assertTrue("Should match", pre.matches(exchange));
    }

}
