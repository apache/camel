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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.ExpressionBuilder.*;
import static org.apache.camel.builder.PredicateBuilder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionBuilderTest extends TestSupport {
    protected CamelContext camelContext = new DefaultCamelContext();
    protected Exchange exchange = new DefaultExchange(camelContext);

    @Test
    public void testRegexTokenize() throws Exception {
        Expression expression = regexTokenizeExpression(headerExpression("location"), ",");
        List<String> expected = new ArrayList<>(Arrays.asList("Islington", "London", "UK"));
        assertExpression(expression, exchange, expected);

        Predicate predicate
                = contains(regexTokenizeExpression(headerExpression("location"), ","), constantExpression("London"));
        assertPredicate(predicate, exchange, true);

        predicate = contains(regexTokenizeExpression(headerExpression("location"), ","), constantExpression("Manchester"));
        assertPredicate(predicate, exchange, false);
    }

    @Test
    public void testRegexReplaceAll() throws Exception {
        Expression expression = regexReplaceAll(headerExpression("location"), "London", "Westminster");
        assertExpression(expression, exchange, "Islington,Westminster,UK");

        expression = regexReplaceAll(headerExpression("location"), "London", headerExpression("name"));
        assertExpression(expression, exchange, "Islington,James,UK");
    }

    @Test
    public void testTokenize() throws Exception {
        Expression expression = tokenizeExpression(headerExpression("location"), ",");

        List<String> expected = new ArrayList<>(Arrays.asList("Islington", "London", "UK"));
        assertExpression(expression, exchange, expected);

        Predicate predicate = contains(tokenizeExpression(headerExpression("location"), ","), constantExpression("London"));
        assertPredicate(predicate, exchange, true);

        predicate = contains(tokenizeExpression(headerExpression("location"), ","), constantExpression("Manchester"));
        assertPredicate(predicate, exchange, false);
    }

    @Test
    public void testTokenizeLines() throws Exception {
        Expression expression = regexTokenizeExpression(bodyExpression(), "[\r|\n]");
        exchange.getIn().setBody("Hello World\nBye World\rSee you again");

        List<String> expected = new ArrayList<>(Arrays.asList("Hello World", "Bye World", "See you again"));
        assertExpression(expression, exchange, expected);
    }

    @Test
    public void testSortLines() throws Exception {
        Expression expression = sortExpression(body().tokenize(",").getExpression(), new SortByName());
        exchange.getIn().setBody("Jonathan,Claus,James,Hadrian");

        List<String> expected = new ArrayList<>(Arrays.asList("Claus", "Hadrian", "James", "Jonathan"));
        assertExpression(expression, exchange, expected);
    }

    @Test
    public void testCamelContextPropertiesExpression() throws Exception {
        camelContext.getGlobalOptions().put("CamelTestKey", "CamelTestValue");
        Expression expression = camelContextPropertyExpression("CamelTestKey");
        expression.init(camelContext);
        assertExpression(expression, exchange, "CamelTestValue");
        expression = camelContextPropertiesExpression();
        expression.init(camelContext);
        Map<?, ?> properties = expression.evaluate(exchange, Map.class);
        assertEquals(1, properties.size(), "Get a wrong properties size");
    }

    @Test
    public void testParseSimpleOrFallbackToConstantExpression() throws Exception {
        Expression exp = simpleExpression("world");
        exp.init(camelContext);
        assertEquals("world", exp.evaluate(exchange, String.class));

        exp = simpleExpression("${body}");
        exp.init(camelContext);
        assertEquals("Hello there!", exp.evaluate(exchange, String.class));

        exp = simpleExpression("$simple{body}");
        exp.init(camelContext);
        assertEquals("Hello there!", exp.evaluate(exchange, String.class));
    }

    @Test
    public void testFunction() throws Exception {
        assertExpression(messageExpression(m -> m.getExchange().getIn().getHeader("name")), exchange, "James");
        assertExpression(messageExpression(m -> m.getHeader("name")), exchange, "James");
    }

    @Test
    public void testHeaderExpression() throws Exception {
        assertExpression(headerExpression("name", String.class), exchange, "James");
    }

    @Test
    public void testConstantsOnly() throws Exception {
        Expression expression = concatExpression(
                List.of(constantExpression("Hello"), constantExpression(" big "), constantExpression("World")));
        assertExpression(expression, exchange, "Hello big World");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Message in = exchange.getIn();
        in.setBody("Hello there!");
        in.setHeader("name", "James");
        in.setHeader("location", "Islington,London,UK");
    }

    private static class SortByName implements Comparator<String> {

        @Override
        public int compare(java.lang.String o1, java.lang.String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    }

}
