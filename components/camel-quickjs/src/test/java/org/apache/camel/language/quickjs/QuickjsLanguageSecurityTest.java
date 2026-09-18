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

import java.util.Arrays;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class QuickjsLanguageSecurityTest {

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

    static Exchange sampleExchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new Person("Ada", 36));
        exchange.getMessage().setHeader("foo", "bar");
        exchange.setProperty("color", "red");
        return exchange;
    }

    @Test
    void pojoBodyIsJsonSnapshotNotLiveJavaObject() {
        Language language = language();
        Exchange exchange = sampleExchange();
        assertThat(language.createExpression("body.name").evaluate(exchange, String.class)).isEqualTo("Ada");
        assertThat(language.createExpression("body.age").evaluate(exchange, Integer.class)).isEqualTo(36);
        assertDenied(language, exchange, "body.getAge()", "not a function", "TypeError");
    }

    @Test
    void doesNotBindExchangeMessageOrContext() {
        Language language = language();
        Exchange exchange = sampleExchange();
        assertDenied(language, exchange, "exchange", "is not defined", "ReferenceError");
        assertDenied(language, exchange, "message", "is not defined", "ReferenceError");
        assertDenied(language, exchange, "context", "is not defined", "ReferenceError");
        assertDenied(language, exchange, "exchange.getMessage()", "is not defined", "ReferenceError");
    }

    @Test
    void cannotLookupJavaClasses() {
        Language language = language();
        Exchange exchange = sampleExchange();
        assertDenied(language, exchange, "Java.type('java.lang.Runtime')", "is not defined", "ReferenceError");
        assertDenied(language, exchange, "java.lang.System", "is not defined", "ReferenceError");
    }

    @Test
    void javaInvokeHostPlumbingIsNotInScriptScope() {
        Language language = language();
        Exchange exchange = sampleExchange();

        assertThat(language.createExpression("typeof java_invoke").evaluate(exchange, String.class))
                .isEqualTo("function");
        assertThat(language.createExpression("typeof quickjs4j_engine").evaluate(exchange, String.class))
                .isEqualTo("undefined");
        assertDenied(language, exchange, "java_invoke('org.apache.camel.Exchange', 'getMessage', '[]')",
                "not available", "TypeError");

        assertThat(language.createExpression("typeof globalThis.java_invoke").evaluate(exchange, String.class))
                .isEqualTo("function");
        assertThat(language.createExpression("typeof globalThis[\"java_invoke\"]").evaluate(exchange, String.class))
                .isEqualTo("function");
        assertDenied(language, exchange, "globalThis.java_invoke('quickjs4j_engine', 'module_name', '[]')",
                "not available", "TypeError");
        assertDenied(language, exchange, "globalThis[\"java_invoke\"]('quickjs4j_engine', 'module_name', '[]')",
                "not available", "TypeError");

        assertThat(language.createExpression("typeof globalThis.quickjs4j_engine").evaluate(exchange, String.class))
                .isEqualTo("undefined");
        assertThat(language.createExpression("typeof globalThis[\"quickjs4j_engine\"]").evaluate(exchange, String.class))
                .isEqualTo("undefined");

        assertThat(language.createExpression("typeof globalThis.camelQuickjs").evaluate(exchange, String.class))
                .isEqualTo("undefined");
        assertDenied(language, exchange, "globalThis.camelQuickjs.camelEval_set_result", "undefined", "TypeError");

        assertThat(language.createExpression("(0, eval)('typeof java_invoke')").evaluate(exchange, String.class))
                .isEqualTo("function");
        assertThat(language.createExpression("(0, eval)('typeof quickjs4j_engine')").evaluate(exchange, String.class))
                .isEqualTo("undefined");
        assertDenied(language, exchange, "(0, eval)(\"java_invoke('quickjs4j_engine', 'module_name', '[]')\")",
                "not available", "TypeError");
        assertThat(language.createExpression("new Function('return typeof java_invoke')()").evaluate(exchange,
                String.class)).isEqualTo("function");
        assertThat(language.createExpression("new Function('return typeof quickjs4j_engine')()").evaluate(exchange,
                String.class)).isEqualTo("undefined");
        assertDenied(language, exchange, "new Function('return java_invoke')()('quickjs4j_engine', 'module_name', '[]')",
                "not available", "TypeError");

        assertThat(language.createExpression("1 + 1").evaluate(exchange, Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsLiveCamelObjectsAsBody() {
        Language language = language();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(exchange);
        Throwable thrown = catchThrowable(() -> language.createExpression("body").evaluate(exchange, Object.class));
        assertThat(thrown).isInstanceOf(ExpressionEvaluationException.class);
        assertThat(messageContains(thrown, "cannot be exposed to camel-quickjs")).isTrue();
    }

    static void assertDenied(Language language, Exchange exchange, String script, String... messageFragments) {
        Throwable thrown = catchThrowable(() -> language.createExpression(script).evaluate(exchange, Object.class));
        assertThat(thrown).as("expected %s to fail", script).isInstanceOf(ExpressionEvaluationException.class);
        assertThat(messageContainsAny(thrown, messageFragments))
                .as("%s should mention one of %s but was: %s", script, Arrays.toString(messageFragments), thrown)
                .isTrue();
    }

    static boolean messageContainsAny(Throwable thrown, String... messageFragments) {
        for (String fragment : messageFragments) {
            if (messageContains(thrown, fragment)) {
                return true;
            }
        }
        return false;
    }

    static boolean messageContains(Throwable thrown, String messageFragment) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(messageFragment)) {
                return true;
            }
        }
        return false;
    }

    public static class Person {
        public String name;
        private final int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public int getAge() {
            return age;
        }
    }
}
