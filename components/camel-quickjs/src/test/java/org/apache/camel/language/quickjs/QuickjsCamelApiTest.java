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

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The controlled {@code camel} API (mutation through host functions), the {@code variables} / {@code exception}
 * bindings and the per-engine compiled script cache.
 */
class QuickjsCamelApiTest {

    private static CamelContext context;
    private static Language language;

    @BeforeAll
    static void startContext() {
        context = new DefaultCamelContext();
        context.start();
        language = context.resolveLanguage("quickjs");
    }

    @AfterAll
    static void stopContext() {
        context.stop();
    }

    private static Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @Test
    void setHeaderWritesThroughToTheExchange() {
        Exchange exchange = exchange("Hello");
        language.createExpression(
                "camel.setHeader('processed', true); camel.setHeader('count', 3); camel.log('info', 'set'); body")
                .evaluate(exchange, Object.class);
        assertThat(exchange.getMessage().getHeader("processed")).isEqualTo(true);
        assertThat(exchange.getMessage().getHeader("count")).isEqualTo(3);
    }

    @Test
    void getHeaderReadsTheLiveExchange() {
        Exchange exchange = exchange("Hello");
        exchange.getMessage().setHeader("foo", "bar");
        Object result = language.createExpression("camel.setHeader('foo', 'baz'); camel.getHeader('foo')")
                .evaluate(exchange, Object.class);
        // the headers binding is a snapshot taken before the script ran, the API sees the live value
        assertThat(result).isEqualTo("baz");
        Object snapshot = language.createExpression("camel.setHeader('foo', 'qux'); headers.foo")
                .evaluate(exchange, Object.class);
        assertThat(snapshot).isEqualTo("baz");
        assertThat(exchange.getMessage().getHeader("foo")).isEqualTo("qux");
    }

    @Test
    void removeHeaderPropertyAndVariable() {
        Exchange exchange = exchange("Hello");
        exchange.getMessage().setHeader("h", 1);
        exchange.setProperty("p", 2);
        exchange.setVariable("v", 3);
        Object removed = language.createExpression(
                "[camel.removeHeader('h'), camel.removeProperty('p'), camel.removeVariable('v')]")
                .evaluate(exchange, Object.class);
        assertThat(removed).isEqualTo(List.of(1, 2, 3));
        assertThat(exchange.getMessage().getHeader("h")).isNull();
        assertThat(exchange.getProperty("p")).isNull();
        assertThat(exchange.getVariable("v")).isNull();
    }

    @Test
    void setBodyAndSetPropertyWithStructuredValues() {
        Exchange exchange = exchange("Hello");
        language.createExpression("camel.setBody({ greeting: body, items: [1, 2] }); camel.setProperty('tag', 'x')")
                .evaluate(exchange, Object.class);
        assertThat(exchange.getMessage().getBody()).isEqualTo(Map.of("greeting", "Hello", "items", List.of(1, 2)));
        assertThat(exchange.getProperty("tag")).isEqualTo("x");
    }

    @Test
    void variablesAreBoundAndWritable() {
        Exchange exchange = exchange("Hello");
        exchange.setVariable("who", "World");
        Object result = language.createExpression("camel.setVariable('seen', variables.who); body + ' ' + variables.who")
                .evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("Hello World");
        assertThat(exchange.getVariable("seen")).isEqualTo("World");
        assertThat(language.createExpression("camel.getVariable('seen')").evaluate(exchange, String.class))
                .isEqualTo("World");
        // no variables at all binds an empty object
        assertThat(language.createExpression("Object.keys(variables).length").evaluate(exchange("x"), Integer.class))
                .isZero();
    }

    @Test
    void exceptionBindingIsNullOrTypeAndMessage() {
        Exchange exchange = exchange("Hello");
        assertThat(language.createExpression("exception === null").evaluate(exchange, Boolean.class)).isTrue();
        exchange.setException(new IllegalStateException("boom"));
        assertThat(language.createExpression("exception.type + ': ' + exception.message").evaluate(exchange,
                String.class)).isEqualTo("java.lang.IllegalStateException: boom");
        Exchange caught = exchange("Hello");
        caught.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("caught"));
        assertThat(language.createExpression("exception.message").evaluate(caught, String.class)).isEqualTo("caught");
    }

    @Test
    void camelApiIsNotAvailableWithoutAnExchange() {
        QuickjsLanguage quickjs = (QuickjsLanguage) language;
        Throwable thrown = catchThrowable(() -> quickjs.evaluate("camel.getBody()", Map.of(), Object.class));
        assertThat(thrown).isInstanceOf(ExpressionEvaluationException.class);
        assertThat(thrown.getCause()).hasMessageContaining("only available while evaluating a route expression");
    }

    @Test
    void camelFacadeCannotReachTheRawHostBridge() {
        Exchange exchange = exchange("x");
        assertThat(language.createExpression("typeof camel.getHeader").evaluate(exchange, String.class))
                .isEqualTo("function");
        assertThat(language.createExpression("typeof globalThis.camel.setBody").evaluate(exchange, String.class))
                .isEqualTo("function");
        assertThat(language.createExpression("Object.isFrozen(camel)").evaluate(exchange, Boolean.class)).isTrue();
        // the facade closes over the real java_invoke; nothing on it or on the script's scope leaks it
        assertThat(language.createExpression("typeof java_invoke").evaluate(exchange, String.class))
                .isEqualTo("function");
        Throwable thrown = catchThrowable(
                () -> language.createExpression("java_invoke('camel', 'getBody', '[]')").evaluate(exchange,
                        Object.class));
        assertThat(thrown).isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("java_invoke is not available");
        // a JavaScript exception leaves the engine usable
        assertThat(language.createExpression("camel.getBody()").evaluate(exchange, String.class)).isEqualTo("x");
        assertThat(language.createExpression("Object.keys(camel).includes('invoke')").evaluate(exchange,
                Boolean.class)).isFalse();
    }

    @Test
    void guestExceptionKeepsTheEngine() {
        QuickjsLanguage quickjs = (QuickjsLanguage) language;
        Exchange exchange = exchange("x");
        language.createExpression("body").evaluate(exchange, String.class);
        assertThat(quickjs.trackedEngineCount()).isEqualTo(1);
        long memory = quickjs.engineMemory();
        for (String script : new String[] { "throw new Error('boom')", "body.nope.deeper", "var a = 1; throw a" }) {
            Throwable thrown = catchThrowable(() -> language.createExpression(script).evaluate(exchange, Object.class));
            assertThat(thrown).isInstanceOf(ExpressionEvaluationException.class);
            // same engine: not discarded, and it still compiles and runs new scripts
            assertThat(quickjs.trackedEngineCount()).isEqualTo(1);
            assertThat(quickjs.engineMemory()).isGreaterThanOrEqualTo(memory);
            assertThat(language.createExpression("body + '/' + '" + script.length() + "'").evaluate(exchange, String.class))
                    .isEqualTo("x/" + script.length());
        }
    }

    @Test
    void hostTrapDiscardsTheEngineAndTheNextEvaluationRecovers() {
        QuickjsLanguage quickjs = (QuickjsLanguage) language;
        Exchange exchange = exchange("x");
        language.createExpression("body").evaluate(exchange, String.class);
        assertThat(quickjs.trackedEngineCount()).isEqualTo(1);
        // a host function that throws is a trap inside the runtime: the engine is dropped
        Throwable thrown = catchThrowable(
                () -> language.createExpression("camel.getHeader(null)").evaluate(exchange, Object.class));
        assertThat(thrown).isInstanceOf(ExpressionEvaluationException.class)
                .isNotInstanceOf(ExpressionIllegalSyntaxException.class);
        assertThat(quickjs.trackedEngineCount()).isZero();
        // and a valid script is neither blamed for the runtime state nor rejected
        assertThat(language.createExpression("body + 103").evaluate(exchange, String.class)).isEqualTo("x103");
        assertThat(quickjs.trackedEngineCount()).isEqualTo(1);
    }

    @Test
    void streamingBodyIsAnErrorThroughTheApiAsWell() {
        // the body binding rejects a streaming body (QuickjsSerializationTest); camel.getBody() must agree
        Exchange exchange = exchange(new ByteArrayInputStream(new byte[] { 1 }));
        Throwable thrown = catchThrowable(
                () -> language.createExpression("camel.getBody()").evaluate(exchange, Object.class));
        assertThat(thrown).isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("cannot be exposed to camel-quickjs without consuming the message body");
    }

    @Test
    void expressionAndStatementFormsKeepTheirValue() {
        Exchange exchange = exchange(20);
        // single expression: compiled as return (...)
        assertThat(language.createExpression("body * 2 + 1").evaluate(exchange, Integer.class)).isEqualTo(41);
        // object literal is an expression, not a block, when compiled as an expression
        assertThat(language.createExpression("{ a: body }").evaluate(exchange, Object.class))
                .isEqualTo(Map.of("a", 20));
        // statements: evaluated for their completion value
        assertThat(language.createExpression("var x = body; x = x + 1; x").evaluate(exchange, Integer.class))
                .isEqualTo(21);
        assertThat(language.createExpression("body + 1;").evaluate(exchange, Integer.class)).isEqualTo(21);
        assertThat(language.createExpression("let y = 2; body * y").evaluate(exchange, Integer.class)).isEqualTo(40);
    }

    @Test
    void compiledScriptsAreCachedPerEngineAndBounded() {
        QuickjsLanguage quickjs = (QuickjsLanguage) language;
        Exchange exchange = exchange(1);
        String script = "body + 1000";
        for (int i = 0; i < 3; i++) {
            assertThat(language.createExpression(script).evaluate(exchange, Integer.class)).isEqualTo(1001);
        }
        assertThat(quickjs.compiledScriptCount()).isPositive();
        int before = quickjs.compiledScriptCount();
        language.createExpression(script).evaluate(exchange, Integer.class);
        assertThat(quickjs.compiledScriptCount()).isEqualTo(before);
        for (int i = 0; i < QuickjsLanguage.COMPILED_SCRIPTS_PER_ENGINE + 50; i++) {
            language.createExpression("body + " + i).evaluate(exchange, Integer.class);
        }
        assertThat(quickjs.compiledScriptCount()).isEqualTo(QuickjsLanguage.COMPILED_SCRIPTS_PER_ENGINE);
    }
}
