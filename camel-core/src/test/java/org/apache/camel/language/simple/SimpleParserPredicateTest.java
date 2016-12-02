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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Predicate;
import org.apache.camel.impl.JndiRegistry;

/**
 *
 */
public class SimpleParserPredicateTest extends ExchangeTestSupport {

    public void testSimpleBooleanValue() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser("true", true);
        Predicate pre = parser.parsePredicate();
        assertTrue(pre.matches(exchange));

        parser = new SimplePredicateParser("false", true);
        pre = parser.parsePredicate();
        assertFalse(pre.matches(exchange));
    }

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

    public void testSimpleMap() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("foo", "123");
        map.put("foo bar", "456");

        exchange.getIn().setBody(map);

        SimplePredicateParser parser = new SimplePredicateParser("${body[foo]} == 123", true);
        Predicate pre = parser.parsePredicate();
        assertTrue("Should match", pre.matches(exchange));

        parser = new SimplePredicateParser("${body['foo bar']} == 456", true);
        pre = parser.parsePredicate();
        assertTrue("Should match", pre.matches(exchange));

        // the predicate has whitespace in the function
        parser = new SimplePredicateParser("${body[foo bar]} == 456", true);
        pre = parser.parsePredicate();
        assertTrue("Should match", pre.matches(exchange));

        // no header with that name
        parser = new SimplePredicateParser("${body[unknown]} == 456", true);
        pre = parser.parsePredicate();
        assertFalse("Should not match", pre.matches(exchange));
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        List<String> list = new ArrayList<String>();
        list.add("foo");
        list.add("bar");

        jndi.bind("myList", list);
        return jndi;
    }

    public void testSimpleIn() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("key", "foo");
        map.put("key2", "bar");
        map.put("key3", "none");
        exchange.getIn().setBody(map);

        SimplePredicateParser parser = new SimplePredicateParser("${body[key]} in ${ref:myList}", true);
        Predicate pre = parser.parsePredicate();
        assertTrue("Should match", pre.matches(exchange));

        parser = new SimplePredicateParser("${body[key2]} in ${ref:myList}", true);
        pre = parser.parsePredicate();
        assertTrue("Should match", pre.matches(exchange));

        parser = new SimplePredicateParser("${body[key3]} in ${ref:myList}", true);
        pre = parser.parsePredicate();
        assertFalse("Should not match", pre.matches(exchange));
    }

    public void testSimpleInEmpty() throws Exception {
        SimplePredicateParser parser = new SimplePredicateParser("${body} in ',,gold,silver'", true);
        Predicate pre = parser.parsePredicate();

        exchange.getIn().setBody("gold");
        assertTrue("Should match gold", pre.matches(exchange));

        exchange.getIn().setBody("silver");
        assertTrue("Should match silver", pre.matches(exchange));

        exchange.getIn().setBody("");
        assertTrue("Should match empty", pre.matches(exchange));

        exchange.getIn().setBody("bronze");
        assertFalse("Should not match bronze", pre.matches(exchange));
    }
}
