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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class PredicateBuilderConcurrentTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testPredicateBuilderConcurrent() throws Exception {
        context.start();

        List<Future<Boolean>> futures = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 1000; i++) {
            final Integer num = i;
            Future<Boolean> future = pool.submit(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    Expression left = ExpressionBuilder.headerExpression("foo");
                    Expression right;
                    if (num % 2 == 0) {
                        right = ExpressionBuilder.constantExpression("ABC");
                    } else {
                        right = ExpressionBuilder.constantExpression("DEF");
                    }
                    Predicate predicate = PredicateBuilder.isEqualTo(left, right);

                    Exchange exchange = new DefaultExchange(context);
                    exchange.getIn().setBody("Hello World");
                    exchange.getIn().setHeader("foo", "ABC");

                    return predicate.matches(exchange);
                }
            });

            futures.add(future);
        }

        for (int i = 0; i < 1000; i++) {
            Boolean result = futures.get(i).get(10, TimeUnit.SECONDS);
            if (i % 2 == 0) {
                assertEquals("Should be true for #" + i, true, result.booleanValue());
            } else {
                assertEquals("Should be false for #" + i, false, result.booleanValue());
            }
        }

        pool.shutdownNow();
    }

}
