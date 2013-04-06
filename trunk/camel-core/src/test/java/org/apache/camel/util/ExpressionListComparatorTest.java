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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class ExpressionListComparatorTest extends ContextTestSupport {

    private static class MyFooExpression implements Expression {

        @SuppressWarnings("unchecked")
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            return (T) "foo";
        }
    }

    private static class MyBarExpression implements Expression {

        @SuppressWarnings("unchecked")
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            return (T) "bar";
        }
    }

    public void testExpressionListComparator() {
        List<Expression> list = new ArrayList<Expression>();
        list.add(new MyFooExpression());
        list.add(new MyBarExpression());

        ExpressionListComparator comp = new ExpressionListComparator(list);

        Exchange e1 = new DefaultExchange(context);
        Exchange e2 = new DefaultExchange(context);
        int out = comp.compare(e1, e2);

        assertEquals(0, out);
    }
}
