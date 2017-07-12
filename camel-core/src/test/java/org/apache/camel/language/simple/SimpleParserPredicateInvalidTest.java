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
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;

/**
 *
 */
public class SimpleParserPredicateInvalidTest extends ExchangeTestSupport {

    public void testSimpleEqFunctionInvalid() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} == abc", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(19, e.getIndex());
        }
    }

    public void testSimpleInvalidSymbol() throws Exception {
        exchange.getIn().setBody("Hello");
        exchange.getIn().setHeader("high", true);

        SimplePredicateParser parser = new SimplePredicateParser("${header.high} = true", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(15, e.getIndex());
        }
    }

    public void testSimpleUnevenSingleQuote() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("${body} == 'foo", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(14, e.getIndex());
        }
    }

    public void testSimpleUnevenDoubleQuote() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("${body} == \"foo", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(14, e.getIndex());
        }
    }

    public void testSimpleTwoAnd() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("${body} == 'foo' && && ${header} == 123", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(20, e.getIndex());
        }
    }

    public void testSimpleTwoOr() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("${body} == 'foo' || || ${header} == 123", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(20, e.getIndex());
        }
    }

    public void testSimpleTwoEq() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("${body} == == 'foo'", true);
        try {
            parser.parsePredicate();
            fail("Should thrown exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(13, e.getIndex());
        }
    }

}
