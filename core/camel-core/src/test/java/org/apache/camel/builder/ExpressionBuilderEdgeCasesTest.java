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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchVariableException;
import org.apache.camel.Predicate;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.support.builder.ValueBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExpressionBuilderEdgeCasesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Object evaluate(Expression expression, Exchange exchange) {
        expression.init(context);
        return expression.evaluate(exchange, Object.class);
    }

    private boolean matches(Predicate predicate, Exchange exchange) {
        predicate.init(context);
        return predicate.matches(exchange);
    }

    @Test
    public void testLessThanWithBothNull() {
        Exchange exchange = new DefaultExchange(context);
        Predicate lessThan = PredicateBuilder.isLessThan(ExpressionBuilder.headerExpression("a"),
                ExpressionBuilder.headerExpression("b"));
        assertThat(matches(lessThan, exchange)).isFalse();
        assertThat(matches(context.resolveLanguage("simple").createPredicate("${header.a} < ${header.b}"), exchange))
                .isFalse();
        // null is equal to null
        assertThat(matches(context.resolveLanguage("simple").createPredicate("${header.a} <= ${header.b}"), exchange))
                .isTrue();
    }

    @Test
    public void testLanguagePredicateWithConcurrentExchanges() throws Exception {
        Predicate predicate = PredicateBuilder.language(ExpressionBuilder.headerExpression("value"), "simple",
                "${body} == 'A'");
        predicate.init(context);

        List<String> wrong = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 5000; i++) {
                String value = i % 2 == 0 ? "A" : "B";
                pool.submit(() -> {
                    Exchange exchange = new DefaultExchange(context);
                    exchange.getMessage().setHeader("value", value);
                    if (predicate.matches(exchange) != "A".equals(value)) {
                        wrong.add(value);
                    }
                });
            }
        } finally {
            pool.shutdown();
        }
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(wrong).isEmpty();
    }

    @Test
    public void testMissingVariableSourceIsMandatory() {
        Exchange exchange = new DefaultExchange(context);
        Expression source = ExpressionBuilder.singleInputExpression("variable:missing");
        assertThatThrownBy(() -> evaluate(source, exchange))
                .hasCauseInstanceOf(NoSuchVariableException.class);
    }

    @Test
    public void testInWithNullValue() {
        Exchange exchange = new DefaultExchange(context);
        Predicate in = new ValueBuilder(ExpressionBuilder.headerExpression("foo")).in("a", null);
        assertThat(matches(in, exchange)).isTrue();

        exchange.getMessage().setHeader("foo", "b");
        assertThat(matches(in, exchange)).isFalse();
    }

    @Test
    public void testJoinKeepsEmptyElements() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(Arrays.asList("", "", "c"));
        assertThat(evaluate(context.resolveLanguage("simple").createExpression("${join(',')}"), exchange))
                .isEqualTo(",,c");
    }

    @Test
    public void testHeaderAndVariableAsArrayType() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("h", new byte[] { 1, 2 });
        exchange.setVariable("v", new byte[] { 3 });
        assertThat(evaluate(ExpressionBuilder.headerExpression("h", byte[].class), exchange))
                .isEqualTo(new byte[] { 1, 2 });
        assertThat(evaluate(ExpressionBuilder.variableExpression("v", byte[].class), exchange))
                .isEqualTo(new byte[] { 3 });
    }

    @Test
    public void testConcatWithNullConstant() {
        Exchange exchange = new DefaultExchange(context);
        Expression concat = ExpressionBuilder.concatExpression(
                List.of(ExpressionBuilder.constantExpression(null), ExpressionBuilder.constantExpression("x")));
        assertThat(evaluate(concat, exchange)).isEqualTo("x");
    }

    @Test
    public void testLanguageExpressionInitsItsInput() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("foo", "Hello");
        Expression expression = ExpressionBuilder.languageExpression(
                ExpressionBuilder.simpleExpression("${header.foo} World"), "simple", "${body}", String.class);
        assertThat(evaluate(expression, exchange)).isEqualTo("Hello World");
    }
}
