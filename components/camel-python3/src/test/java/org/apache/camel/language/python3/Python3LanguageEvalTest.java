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
package org.apache.camel.language.python3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.language.python3.Python3Language.python3;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3LanguageEvalTest {

    static CamelContext context;

    @BeforeAll
    static void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterAll
    static void stopContext() {
        context.stop();
    }

    static Exchange exchangeWithBody(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    void resolvePython3Language() {
        Language language = context.resolveLanguage("python3");
        assertThat(language).isInstanceOf(Python3Language.class);
        assertThat(context.resolveLanguage("python3")).isSameAs(language);
        assertThat(language).isInstanceOf(ScriptingLanguage.class);
    }

    @Test
    void python3Syntax() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = exchangeWithBody("World");
        assertThat(language.createExpression("f'Hello {body}'").evaluate(exchange, String.class)).isEqualTo("Hello World");
        assertThat(language.createExpression("7 // 2").evaluate(exchange, Integer.class)).isEqualTo(3);
        assertThat(language.createExpression("(x := 4) * 2").evaluate(exchange, Integer.class)).isEqualTo(8);
    }

    @Test
    void bodyHeadersPropertiesAndExchangeId() {
        Exchange exchange = exchangeWithBody(7);
        exchange.getIn().setHeader("foo", "bar");
        exchange.setProperty("color", "red");
        Language language = context.resolveLanguage("python3");
        assertThat(language.createExpression("2 + body").evaluate(exchange, Integer.class)).isEqualTo(9);
        assertThat(language.createExpression("headers['foo']").evaluate(exchange, String.class)).isEqualTo("bar");
        assertThat(language.createExpression("properties['color']").evaluate(exchange, String.class)).isEqualTo("red");
        assertThat(language.createExpression("exchangeId").evaluate(exchange, String.class))
                .isEqualTo(exchange.getExchangeId());
        assertThat(language.createPredicate("body == 7").matches(exchange)).isTrue();
        assertThat(language.createPredicate("body == 8").matches(exchange)).isFalse();
    }

    @Test
    void resultIsNotStringified() {
        Expression expression = context.resolveLanguage("python3").createExpression("40 + 2");
        Object result = expression.evaluate(exchangeWithBody(null), Object.class);
        assertThat(result).isInstanceOf(Integer.class).isEqualTo(42);
        assertThat(expression.evaluate(exchangeWithBody(null), String.class)).isEqualTo("42");
    }

    @Test
    void noneAndStatementOnlyProduceJavaNull() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = exchangeWithBody(null);
        assertThat(language.createExpression("None").evaluate(exchange, Object.class)).isNull();
        assertThat(language.createExpression("pass").evaluate(exchange, Object.class)).isNull();
        assertThat(language.createExpression("x = 1").evaluate(exchange, Object.class)).isNull();
        assertThat(language.createExpression("None").evaluate(exchange, String.class)).isNull();
        assertThat(language.createExpression("None").evaluate(exchange, Integer.class)).isNull();
    }

    @Test
    void primitiveConversionRemainsUnchanged() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = exchangeWithBody(null);
        assertThat(language.createExpression("'hello'").evaluate(exchange, Object.class)).isEqualTo("hello");
        assertThat(language.createExpression("42").evaluate(exchange, Object.class)).isInstanceOf(Integer.class).isEqualTo(42);
        assertThat(language.createExpression("42").evaluate(exchange, Integer.class)).isEqualTo(42);
        assertThat(language.createExpression("42").evaluate(exchange, Long.class)).isEqualTo(42L);
        assertThat(language.createExpression("42").evaluate(exchange, String.class)).isEqualTo("42");
        assertThat(language.createExpression("1.5").evaluate(exchange, Object.class)).isInstanceOf(Double.class)
                .isEqualTo(1.5d);
        assertThat(language.createExpression("True").evaluate(exchange, Object.class)).isEqualTo(Boolean.TRUE);
        assertThat(language.createExpression("True").evaluate(exchange, Boolean.class)).isTrue();
        assertThat(language.createExpression("False").evaluate(exchange, Object.class)).isEqualTo(Boolean.FALSE);
        assertThat(language.createExpression("0").evaluate(exchange, Object.class)).isEqualTo(0);
    }

    @Test
    void listResultSurvivesContextClose() {
        Language language = context.resolveLanguage("python3");
        Object result = language.createExpression("[1, 'a', True]").evaluate(exchangeWithBody(null), Object.class);
        assertThat(result.getClass().getName()).doesNotContain("polyglot");
        assertThat(result).isInstanceOf(List.class);
        List<Object> list = castList(result);
        assertThat(list).containsExactly(1, "a", true);
        assertThat(list.get(0)).isInstanceOf(Integer.class);
        assertThat(list.get(2)).isInstanceOf(Boolean.class);
    }

    @Test
    void dictResultSurvivesContextClose() {
        Language language = context.resolveLanguage("python3");
        Object result = language.createExpression("{'a': 1, 'b': 'x'}").evaluate(exchangeWithBody(null), Object.class);
        assertThat(result.getClass().getName()).doesNotContain("polyglot");
        assertThat(result).isInstanceOf(Map.class);
        assertThat(castMap(result)).containsEntry("a", 1).containsEntry("b", "x");
    }

    @Test
    void setResultSurvivesContextClose() {
        Language language = context.resolveLanguage("python3");
        Object result = language.createExpression("{1, 2}").evaluate(exchangeWithBody(null), Object.class);
        assertThat(result.getClass().getName()).doesNotContain("polyglot");
        assertThat(result).isInstanceOf(Set.class);
        assertThat(castSet(result)).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void tupleResultSurvivesContextClose() {
        Language language = context.resolveLanguage("python3");
        Object result = language.createExpression("(1, 2)").evaluate(exchangeWithBody(null), Object.class);
        assertThat(result.getClass().getName()).doesNotContain("polyglot");
        assertThat(result).isInstanceOf(List.class);
        assertThat(castList(result)).containsExactly(1, 2);
    }

    @Test
    void nestedSetAndTupleSurviveContextClose() {
        Language language = context.resolveLanguage("python3");
        Object result = language.createExpression("{'s': {1, 2}, 't': (3, 4)}").evaluate(exchangeWithBody(null), Object.class);
        assertThat(result.getClass().getName()).doesNotContain("polyglot");
        Map<Object, Object> map = castMap(result);
        assertThat(castSet(map.get("s"))).containsExactlyInAnyOrder(1, 2);
        assertThat(castList(map.get("t"))).containsExactly(3, 4);
        assertThat(map.get("s").getClass().getName()).doesNotContain("polyglot");
        assertThat(map.get("t").getClass().getName()).doesNotContain("polyglot");
    }

    @Test
    void nestedListAndDictSurviveContextClose() {
        Language language = context.resolveLanguage("python3");
        Object result = language.createExpression("{'n': [1, {'k': 2}]}").evaluate(exchangeWithBody(null), Object.class);
        assertThat(result.getClass().getName()).doesNotContain("polyglot");
        Map<Object, Object> map = castMap(result);
        List<Object> nested = castList(map.get("n"));
        assertThat(nested.get(0)).isEqualTo(1);
        Map<Object, Object> inner = castMap(nested.get(1));
        assertThat(inner.getClass().getName()).doesNotContain("polyglot");
        assertThat(inner).containsEntry("k", 2);
    }

    @Test
    void resultTypeListAndMap() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = exchangeWithBody(7);
        List<Object> list = language.createExpression("[body, 1]").evaluate(exchange, List.class);
        assertThat(list.getClass().getName()).doesNotContain("polyglot");
        assertThat(list).containsExactly(7, 1);

        Map<Object, Object> map = language.createExpression("{'a': body}").evaluate(exchange, Map.class);
        assertThat(map.getClass().getName()).doesNotContain("polyglot");
        assertThat(map).containsEntry("a", 7);

        ScriptingLanguage sl = (ScriptingLanguage) language;
        assertThat(castList(sl.evaluate("[1, 2]", null, List.class))).containsExactly(1, 2);
        assertThat(castMap(sl.evaluate("{'a': 1}", null, Map.class))).containsEntry("a", 1);
    }

    @Test
    void concurrentEvaluationIsThreadSafe() throws Exception {
        Expression expression = context.resolveLanguage("python3").createExpression("body + 1");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                int value = i;
                futures.add(pool.submit(() -> expression.evaluate(exchangeWithBody(value), Integer.class)));
            }
            for (int i = 0; i < futures.size(); i++) {
                assertThat(futures.get(i).get(20, TimeUnit.SECONDS)).isEqualTo(i + 1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void scriptingLanguageEvaluate() {
        ScriptingLanguage language = (ScriptingLanguage) context.resolveLanguage("python3");
        assertThat(language.evaluate("2 * 3", null, Integer.class)).isEqualTo(6);
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("body", 3);
        assertThat(language.evaluate("resource:classpath:mypython3.py", bindings, String.class))
                .isEqualTo("The result is 6");
    }

    @Test
    void repeatableEvaluation() {
        Predicate predicate = context.resolveLanguage("python3").createPredicate("body == 5");
        assertThat(predicate.matches(exchangeWithBody(5))).isTrue();
        assertThat(predicate.matches(exchangeWithBody(6))).isFalse();
    }

    @Test
    void filterRoute() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:python3-filter")
                        .filter(python3("body > 20"))
                        .process(e -> passed.incrementAndGet());
            }
        });
        ProducerTemplate template = context.createProducerTemplate();
        try {
            template.sendBody("direct:python3-filter", 44);
            template.sendBody("direct:python3-filter", 10);
            assertThat(passed.get()).isEqualTo(1);
        } finally {
            template.stop();
        }
    }

    @SuppressWarnings("unchecked")
    static List<Object> castList(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    static Map<Object, Object> castMap(Object value) {
        return (Map<Object, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static Set<Object> castSet(Object value) {
        return (Set<Object>) value;
    }
}
