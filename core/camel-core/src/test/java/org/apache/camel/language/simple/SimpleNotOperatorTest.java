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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code !} negates a function in a predicate, the way every other language writes it (CAMEL-24984). It is a predicate
 * operator only: in an expression a {@code !} is text, as in {@code Hello ${body}!}.
 */
public class SimpleNotOperatorTest extends ExchangeTestSupport {

    private boolean predicate(String text) {
        Predicate p = context.resolveLanguage("simple").createPredicate(text);
        p.init(context);
        return p.matches(exchange);
    }

    private Object expression(String text) {
        Expression e = context.resolveLanguage("simple").createExpression(text);
        e.init(context);
        return e.evaluate(exchange, Object.class);
    }

    @Test
    public void testNegatesAFunction() {
        exchange.getMessage().setBody(new LinkedHashMap<>(Map.of("a", 1)));
        assertTrue(predicate("!${body.isEmpty()}"));
        assertFalse(predicate("!${body.containsKey('a')}"));
    }

    @Test
    public void testWithLogicalOperators() {
        exchange.getMessage().setBody(new LinkedHashMap<>(Map.of("a", 1)));
        // on the left, on the right, and the form that reaches it through the operators inside ${ } (CAMEL-24921)
        assertTrue(predicate("!${body.isEmpty()} && ${body} != null"));
        assertTrue(predicate("${body} == null || !${body.isEmpty()}"));
        assertTrue(predicate("${body != null && !${body.isEmpty()}}"));
    }

    @Test
    public void testEmptyBodyIsNegatedToFalse() {
        exchange.getMessage().setBody(new LinkedHashMap<>());
        assertFalse(predicate("!${body.isEmpty()}"));
    }

    @Test
    public void testTheNegatedOperatorsAreUnaffected() {
        exchange.getMessage().setBody("Hello");
        assertTrue(predicate("${body} != 'x'"));
        assertTrue(predicate("${body} !contains 'zz'"));
        assertTrue(predicate("${body} !startsWith 'zz'"));
        assertTrue(predicate("${body} !endsWith 'zz'"));
        assertTrue(predicate("${body} !in 'a,b'"));
    }

    @Test
    public void testAnExclamationMarkInTextIsNotAnOperator() {
        exchange.getMessage().setBody("World");
        // an expression never has operators, so every one of these is text
        assertEquals("Hello World! how are you", expression("Hello ${body}! how are you"));
        assertEquals("!aaa! is a weird text", expression("!aaa! is a weird text"));
        assertEquals("Alert: !World", expression("Alert: !${body}"));
        assertEquals("Order World!!!", expression("Order ${body}!!!"));
        // and in a predicate a quoted one is text too
        exchange.getMessage().setBody("Hello!");
        assertTrue(predicate("${body} == 'Hello!'"));
        assertTrue(predicate("${body} contains '!'"));
    }
}
