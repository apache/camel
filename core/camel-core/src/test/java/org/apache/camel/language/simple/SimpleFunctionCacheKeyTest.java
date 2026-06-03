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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the function cache key prefix bug. SimpleFunctionExpression shares a cache map with the
 * expression-level cache. Without an "@FUNC@" prefix, a function key like "body" would collide with any other cache
 * entry stored under the same string, causing the wrong Expression to be returned.
 */
public class SimpleFunctionCacheKeyTest extends ExchangeTestSupport {

    /**
     * The cache must store function entries under the "@FUNC@" prefix, not under the raw function name. A poison entry
     * stored under the raw key "body" must not be picked up when evaluating ${body}. Without the prefix fix, the
     * function would find the poison entry and return "WRONG" instead of the actual body value.
     */
    @Test
    public void testFunctionCacheKeyIsPrefixed() {
        Map<String, Expression> sharedCache = new HashMap<>();
        sharedCache.put("body", ExpressionBuilder.constantExpression("WRONG")); // raw key: collision target without the fix

        exchange.getIn().setBody("correct");

        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}", true, sharedCache);
        Expression expression = parser.parseExpression();
        expression.init(context);

        assertEquals("correct", expression.evaluate(exchange, String.class),
                "With @FUNC@ prefix the poison 'body' entry must be ignored; without the prefix 'WRONG' would be returned");
    }

    /**
     * After evaluating a function, the cache must contain the prefixed key "@FUNC@body" and must NOT contain a raw
     * "body" entry. This directly verifies the keyspace separation contract.
     */
    @Test
    public void testFunctionCacheEntryStoredUnderPrefixedKey() {
        Map<String, Expression> sharedCache = new HashMap<>();
        exchange.getIn().setBody("hello");

        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body}", true, sharedCache);
        Expression expression = parser.parseExpression();
        expression.init(context);
        expression.evaluate(exchange, String.class);

        assertTrue(sharedCache.containsKey("@FUNC@body"), "Cache must store the entry under '@FUNC@body'");
        assertFalse(sharedCache.containsKey("body"), "Cache must not store a raw 'body' entry");
    }

    /**
     * A shared cache is reused correctly across two expressions referencing the same function.
     */
    @Test
    public void testFunctionCacheReusedAcrossExpressions() {
        Map<String, Expression> sharedCache = new HashMap<>();
        exchange.getIn().setBody("world");

        SimpleExpressionParser p1 = new SimpleExpressionParser(context, "Hello ${body}!", true, sharedCache);
        Expression e1 = p1.parseExpression();
        e1.init(context);

        SimpleExpressionParser p2 = new SimpleExpressionParser(context, "Bye ${body}.", true, sharedCache);
        Expression e2 = p2.parseExpression();
        e2.init(context);

        assertEquals("Hello world!", e1.evaluate(exchange, String.class));
        assertEquals("Bye world.", e2.evaluate(exchange, String.class));
        assertTrue(sharedCache.containsKey("@FUNC@body"), "Shared cache must hold the prefixed body entry");
    }
}
