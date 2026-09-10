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
package org.apache.camel.language.js;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The GraalJS {@link org.graalvm.polyglot.Engine} is shared per language instance while every evaluation still gets its
 * own {@link org.graalvm.polyglot.Context}.
 */
@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class JavaScriptSharedEngineTest {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 25;

    @Test
    void concurrentEvaluationsShareTheEngine() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Expression expression = context.resolveLanguage("js").createExpression("'Hello ' + body + ' ' + headers.n");

            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            try {
                List<Future<List<String>>> futures = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    final int thread = t;
                    futures.add(pool.submit((Callable<List<String>>) () -> {
                        List<String> results = new ArrayList<>();
                        for (int i = 0; i < ITERATIONS; i++) {
                            Exchange exchange = new DefaultExchange(context);
                            exchange.getMessage().setBody("t" + thread);
                            exchange.getMessage().setHeader("n", i);
                            results.add(expression.evaluate(exchange, String.class));
                        }
                        return results;
                    }));
                }
                for (int t = 0; t < THREADS; t++) {
                    List<String> results = futures.get(t).get(60, TimeUnit.SECONDS);
                    assertThat(results).hasSize(ITERATIONS);
                    for (int i = 0; i < ITERATIONS; i++) {
                        assertThat(results.get(i)).isEqualTo("Hello t" + t + " " + i);
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void languageWorksAgainAfterCamelContextRestart() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Language language = context.resolveLanguage("js");
            Expression expression = language.createExpression("body + '!'");
            assertThat(evaluate(context, expression, "first")).isEqualTo("first!");

            context.stop();
            context.start();

            // the expression created before the restart still works (the engine is re-created lazily)
            assertThat(evaluate(context, expression, "second")).isEqualTo("second!");
            // and so does a freshly resolved language
            Expression fresh = context.resolveLanguage("js").createExpression("body + '?'");
            assertThat(evaluate(context, fresh, "third")).isEqualTo("third?");
        }
    }

    @Test
    void eachCamelContextHasItsOwnLanguageAndEngine() throws Exception {
        try (CamelContext one = new DefaultCamelContext(); CamelContext two = new DefaultCamelContext()) {
            one.start();
            two.start();
            Language languageOne = one.resolveLanguage("js");
            Language languageTwo = two.resolveLanguage("js");
            assertThat(languageOne).isNotSameAs(languageTwo);

            Expression expressionOne = languageOne.createExpression("body + ' one'");
            Expression expressionTwo = languageTwo.createExpression("body + ' two'");
            assertThat(evaluate(one, expressionOne, "a")).isEqualTo("a one");
            assertThat(evaluate(two, expressionTwo, "b")).isEqualTo("b two");

            // stopping one context closes only its engine; the other language keeps working
            one.stop();
            assertThat(evaluate(two, expressionTwo, "c")).isEqualTo("c two");
        }
    }

    private static String evaluate(CamelContext context, Expression expression, String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return expression.evaluate(exchange, String.class);
    }
}
