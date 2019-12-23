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
package org.apache.camel.component.bean;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public final class CamelSimpleExpressionPerfTestRunner {
    private static final int MESSAGE_LOOP_COUNT = 1000;
    private static final int TEST_EXECUTION_COUNT = 5;

    private CamelSimpleExpressionPerfTestRunner() {
        // Utils class
    }

    public static void main(String[] args) throws Exception {
        long bodyOnly = executePerformanceTest("${body}");
        long bodyProperty = executePerformanceTest("${body[p]}");
        long bodyPropertyWithCache = executePerformanceTest("${body[p]}");

        System.out.printf("${body}: %dms%n", bodyOnly);
        System.out.printf("${body[p]} : %dms%n", bodyProperty);
        System.out.printf("${body[p]} with cache : %dms%n", bodyPropertyWithCache);
    }

    private static long executePerformanceTest(final String simpleExpression) throws Exception {
        CamelContext ctx = new DefaultCamelContext();

        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").loop(MESSAGE_LOOP_COUNT).setHeader("test").simple(simpleExpression).to("mock:plop");
            }
        });

        ctx.start();

        Map<String, String> body = new HashMap<>();
        body.put("p", "q");

        ProducerTemplate template = ctx.createProducerTemplate();
        // Initial one, it's a dry start, we don't care about this one.
        template.sendBody("direct:start", body);

        // Measure the duration of the executions in nanoseconds
        long totalNsDuration = 0;
        for (int i = 0; i < TEST_EXECUTION_COUNT; i++) {
            long tick = System.nanoTime();
            template.sendBody("direct:start", body);
            totalNsDuration += System.nanoTime() - tick;
        }

        ctx.stop();

        // Return the average duration in milliseconds
        return totalNsDuration / TEST_EXECUTION_COUNT / 1000 / 1000;
    }
}
