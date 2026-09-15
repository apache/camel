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
package org.apache.camel.language.python;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrent evaluations of one python expression must not see each other's bindings.
 */
public class PythonExpressionConcurrentTest extends CamelTestSupport {

    @Test
    public void testConcurrentEvaluationsDoNotShareBindings() throws Exception {
        Expression expression = context.resolveLanguage("python").createExpression("body + '-' + str(headers['n'])");
        int threads = 8;
        int rounds = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> failures = new CopyOnWriteArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            tasks.add(() -> {
                try {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        Exchange exchange = new DefaultExchange(context);
                        exchange.getMessage().setBody("t" + id);
                        exchange.getMessage().setHeader("n", i);
                        String expected = "t" + id + "-" + i;
                        String actual = expression.evaluate(exchange, String.class);
                        if (!expected.equals(actual)) {
                            failures.add(expected + " != " + actual);
                        }
                    }
                } catch (Exception e) {
                    failures.add(e.toString());
                }
            });
        }
        tasks.forEach(pool::submit);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(2, TimeUnit.MINUTES));
        assertEquals(List.of(), failures);
    }
}
