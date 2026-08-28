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
package org.apache.camel.language.quickjs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuickjsLanguageEvalTest {

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

    static Language language() {
        return context.resolveLanguage("quickjs");
    }

    static Exchange exchangeWithBody(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @Test
    void resolveQuickjsLanguage() {
        Language language = language();
        assertThat(language).isInstanceOf(QuickjsLanguage.class);
        assertThat(context.resolveLanguage("quickjs")).isSameAs(language);
        assertThat(language).isInstanceOf(ScriptingLanguage.class);
    }

    @Test
    void evaluatesArithmetic() {
        assertThat(language().createExpression("1 + 2").evaluate(exchangeWithBody(null), Integer.class)).isEqualTo(3);
    }

    @Test
    void bodyHeadersPropertiesAndExchangeId() {
        Exchange exchange = exchangeWithBody("hello");
        exchange.getMessage().setHeader("MyHeader", "foo");
        exchange.setProperty("foo", "bar");
        Language language = language();
        assertThat(language.createExpression("body.toUpperCase()").evaluate(exchange, String.class)).isEqualTo("HELLO");
        assertThat(language.createExpression("headers.MyHeader").evaluate(exchange, String.class)).isEqualTo("foo");
        assertThat(language.createExpression("properties.foo").evaluate(exchange, String.class)).isEqualTo("bar");
        assertThat(language.createExpression("exchangeId").evaluate(exchange, String.class))
                .isEqualTo(exchange.getExchangeId());
        assertThat(language.createPredicate("headers.MyHeader == 'foo'").matches(exchange)).isTrue();
        assertThat(language.createPredicate("headers.MyHeader == 'bar'").matches(exchange)).isFalse();
    }

    @Test
    void jsonDataTypes() {
        Language language = language();
        Exchange exchange = exchangeWithBody(null);
        assertThat(language.createExpression("'hello'").evaluate(exchange, Object.class)).isEqualTo("hello");
        assertThat(language.createExpression("true").evaluate(exchange, Object.class)).isEqualTo(Boolean.TRUE);
        assertThat(language.createExpression("false").evaluate(exchange, Boolean.class)).isFalse();
        assertThat(language.createExpression("42").evaluate(exchange, Object.class)).isInstanceOf(Number.class);
        assertThat(language.createExpression("null").evaluate(exchange, Object.class)).isNull();

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Ada");
        body.put("ok", true);
        List<Object> items = new ArrayList<>();
        items.add("a");
        items.add(1);
        body.put("items", items);
        Exchange mapExchange = exchangeWithBody(body);
        assertThat(language.createExpression("body.name").evaluate(mapExchange, String.class)).isEqualTo("Ada");
        assertThat(language.createExpression("body.ok").evaluate(mapExchange, Boolean.class)).isTrue();
        assertThat(language.createExpression("body.items[0]").evaluate(mapExchange, String.class)).isEqualTo("a");
        assertThat(language.createExpression("body.items.length").evaluate(mapExchange, Integer.class)).isEqualTo(2);
    }

    @Test
    void headerMutationDoesNotWriteThroughToCamel() {
        Exchange exchange = exchangeWithBody("x");
        exchange.getMessage().setHeader("MyHeader", "foo");
        language().createExpression("headers.MyHeader = 'changed'; headers.MyHeader").evaluate(exchange, String.class);
        assertThat(exchange.getMessage().getHeader("MyHeader")).isEqualTo("foo");
    }

    @Test
    void sequentialExchangesDoNotLeakBody() {
        Expression expression = language().createExpression("body");
        assertThat(expression.evaluate(exchangeWithBody("one"), String.class)).isEqualTo("one");
        assertThat(expression.evaluate(exchangeWithBody("two"), String.class)).isEqualTo("two");
        assertThat(expression.evaluate(exchangeWithBody(null), Object.class)).isNull();
    }

    @Test
    void varDeclarationsDoNotLeakBetweenExchanges() {
        Expression leak = language().createExpression("var leaked = body; leaked");
        Expression probe = language().createExpression("typeof leaked");
        assertThat(leak.evaluate(exchangeWithBody("secret"), String.class)).isEqualTo("secret");
        assertThat(probe.evaluate(exchangeWithBody("other"), String.class)).isEqualTo("undefined");
    }

    @Test
    void repeatablePredicate() {
        Predicate predicate = language().createPredicate("body == 5");
        assertThat(predicate.matches(exchangeWithBody(5))).isTrue();
        assertThat(predicate.matches(exchangeWithBody(6))).isFalse();
    }

    @Test
    void concurrentEvaluationIsThreadSafe() throws Exception {
        Expression expression = language().createExpression("body + 1");
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
        ScriptingLanguage language = (ScriptingLanguage) language();
        assertThat(language.evaluate("2 * 3", null, Integer.class)).isEqualTo(6);
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("body", 3);
        assertThat(language.evaluate("resource:classpath:myquickjs.js", bindings, String.class))
                .isEqualTo("The result is 6");
    }

    @Test
    void scriptingLanguageEvaluateAcceptsValidBindingNames() {
        ScriptingLanguage language = (ScriptingLanguage) language();
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("foo_bar", 2);
        bindings.put("$val", 3);
        assertThat(language.evaluate("foo_bar + $val", bindings, Integer.class)).isEqualTo(5);
    }

    @Test
    void scriptingLanguageEvaluateRejectsInvalidBindingNames() {
        assertRejectedBinding("foo-bar");
        assertRejectedBinding("123foo");
        assertRejectedBinding("for");
        assertRejectedBinding("");
    }

    void assertRejectedBinding(String name) {
        ScriptingLanguage language = (ScriptingLanguage) language();
        Map<String, Object> bindings = new HashMap<>();
        bindings.put(name, 1);
        assertThatThrownBy(() -> language.evaluate("1", bindings, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .isNotInstanceOf(ExpressionIllegalSyntaxException.class)
                .hasMessageContaining("identifier: " + name);
    }
}
