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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class SimpleParserPredicateInvalidTest extends ExchangeTestSupport {

    @Test
    public void testSimpleEqFunctionInvalid() {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);

        SimplePredicateParser parser = new SimplePredicateParser(context, "${header.high} == abc", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(19, e.getIndex());
    }

    @Test
    public void testSimpleInvalidSymbol() {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);

        SimplePredicateParser parser = new SimplePredicateParser(context, "${header.high} = true", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(15, e.getIndex());
    }

    @Test
    public void testSimpleUnevenSingleQuote() {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser(context, "${body} == 'foo", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(14, e.getIndex());
    }

    @Test
    public void testSimpleUnevenDoubleQuote() {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser(context, "${body} == \"foo", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(14, e.getIndex());
    }

    @Test
    public void testSimpleTwoAnd() {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser =
                new SimplePredicateParser(context, "${body} == 'foo' && && ${header} == 123", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(20, e.getIndex());
    }

    @Test
    public void testSimpleTwoOr() {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser =
                new SimplePredicateParser(context, "${body} == 'foo' || || ${header} == 123", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(20, e.getIndex());
    }

    @Test
    public void testSimpleTwoEq() {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser(context, "${body} == == 'foo'", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate, "Should thrown exception");

        assertEquals(13, e.getIndex());
    }
}
