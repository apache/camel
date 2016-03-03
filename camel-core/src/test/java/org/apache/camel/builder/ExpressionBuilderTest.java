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
import org.apache.camel.impl.DefaultExchange;

import static org.apache.camel.builder.ExpressionBuilder.*;
import static org.apache.camel.builder.PredicateBuilder.contains;

/**
 * @version 
 */
public class ExpressionBuilderTest extends TestSupport {
    protected CamelContext camelContext = new DefaultCamelContext();
    protected Exchange exchange = new DefaultExchange(camelContext);
    
    public void testRegexTokenize() throws Exception {
        Expression expression = regexTokenizeExpression(headerExpression("location"), ",");
        List<String> expected = new ArrayList<String>(Arrays.asList(new String[] {"Islington", "London", "UK"}));
        assertExpression(expression, exchange, expected);

        Predicate predicate = contains(regexTokenizeExpression(headerExpression("location"), ","),
                                                 constantExpression("London"));
        assertPredicate(predicate, exchange, true);

        predicate = contains(regexTokenizeExpression(headerExpression("location"), ","),
                             constantExpression("Manchester"));
        assertPredicate(predicate, exchange, false);
    }

    public void testRegexReplaceAll() throws Exception {
        Expression expression = regexReplaceAll(headerExpression("location"), "London",
                                                          "Westminster");
        assertExpression(expression, exchange, "Islington,Westminster,UK");

        expression = regexReplaceAll(headerExpression("location"), "London", headerExpression("name"));
        assertExpression(expression, exchange, "Islington,James,UK");
    }

    public void testTokenize() throws Exception {
        Expression expression = tokenizeExpression(headerExpression("location"), ",");

        List<String> expected = new ArrayList<String>(Arrays.asList(new String[] {"Islington", "London", "UK"}));
        assertExpression(expression, exchange, expected);

        Predicate predicate = contains(tokenizeExpression(headerExpression("location"), ","),
                                                 constantExpression("London"));
        assertPredicate(predicate, exchange, true);

        predicate = contains(tokenizeExpression(headerExpression("location"), ","),
                             constantExpression("Manchester"));
        assertPredicate(predicate, exchange, false);
    }

    public void testTokenizeLines() throws Exception {
        Expression expression = regexTokenizeExpression(bodyExpression(), "[\r|\n]");
        exchange.getIn().setBody("Hello World\nBye World\rSee you again");

        List<String> expected = new ArrayList<String>(Arrays.asList(new String[] {"Hello World", "Bye World", "See you again"}));
        assertExpression(expression, exchange, expected);
    }

    public void testSortLines() throws Exception {
        Expression expression = sortExpression(body().tokenize(",").getExpression(), new SortByName());
        exchange.getIn().setBody("Jonathan,Claus,James,Hadrian");

        List<String> expected = new ArrayList<String>(Arrays.asList(new String[] {"Claus", "Hadrian", "James", "Jonathan"}));
        assertExpression(expression, exchange, expected);
    }
    
    public void testCamelContextPropertiesExpression() throws Exception {
        camelContext.getProperties().put("CamelTestKey", "CamelTestValue");        
        Expression expression = camelContextPropertyExpression("CamelTestKey");
        assertExpression(expression, exchange, "CamelTestValue");        
        expression = camelContextPropertiesExpression();
        Map<?, ?> properties = expression.evaluate(exchange, Map.class);
        assertEquals("Get a wrong properties size", properties.size(), 1);
    }

    public void testParseSimpleOrFallbackToConstantExpression() throws Exception {
        assertEquals("world", parseSimpleOrFallbackToConstantExpression("world", camelContext).evaluate(exchange, String.class));
        assertEquals("Hello there!", parseSimpleOrFallbackToConstantExpression("${body}", camelContext).evaluate(exchange, String.class));
        assertEquals("Hello there!", parseSimpleOrFallbackToConstantExpression("$simple{body}", camelContext).evaluate(exchange, String.class));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Message in = exchange.getIn();
        in.setBody("Hello there!");
        in.setHeader("name", "James");
        in.setHeader("location", "Islington,London,UK");
    }

    private static class SortByName implements Comparator<String> {

        public int compare(java.lang.String o1, java.lang.String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    }

}
