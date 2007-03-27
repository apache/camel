/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import static org.apache.camel.builder.ExpressionBuilder.*;
import static org.apache.camel.builder.PredicateBuilder.contains;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

import java.util.Arrays;

/**
 * @version $Revision$
 */
public class ExpressionBuilderTest extends TestSupport {
    protected Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    public void testRegexTokenize() throws Exception {
        Expression<Exchange> expression = regexTokenize(headerExpression("location"), ",");
        assertExpression(expression, exchange, Arrays.asList(new String[]{"Islington", "London", "UK"}));

        Predicate<Exchange> predicate = contains(regexTokenize(headerExpression("location"), ","), constantExpression("London"));
        assertPredicate(predicate, exchange, true);

        predicate = contains(regexTokenize(headerExpression("location"), ","), constantExpression("Manchester"));
        assertPredicate(predicate, exchange, false);
    }

    public void testRegexReplaceAll() throws Exception {
        Expression<Exchange> expression = regexReplaceAll(headerExpression("location"), "London", "Westminster");
        assertExpression(expression, exchange, "Islington,Westminster,UK");

        expression = regexReplaceAll(headerExpression("location"), "London", headerExpression("name"));
        assertExpression(expression, exchange, "Islington,James,UK");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Message in = exchange.getIn();
        in.setBody("Hello there!");
        in.setHeader("name", "James");
        in.setHeader("location", "Islington,London,UK");
    }
}
