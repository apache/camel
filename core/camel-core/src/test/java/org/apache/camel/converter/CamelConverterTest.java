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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.ExpressionBuilder.headerExpression;

public class CamelConverterTest extends ContextTestSupport {

    @Test
    public void testToProcessorExpression() throws Exception {
        Expression exp = ExpressionBuilder.headerExpression("foo");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("foo", "bar");
        exchange.getIn().setBody("Hello World");

        Processor pro = CamelConverter.toProcessor(exp);

        pro.process(exchange);

        assertEquals("bar", exchange.getMessage().getBody());
    }

    @Test
    public void testToProcessorPredicate() throws Exception {
        Predicate pred = PredicateBuilder.isEqualTo(headerExpression("foo"), constant("bar"));

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("foo", "bar");
        exchange.getIn().setBody("Hello World");

        Processor pro = CamelConverter.toProcessor(pred);

        pro.process(exchange);

        assertEquals(true, exchange.getMessage().getBody());
    }
}
