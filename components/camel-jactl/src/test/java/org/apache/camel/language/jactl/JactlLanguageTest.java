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
package org.apache.camel.language.jactl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.jactl.CompileError;
import io.jactl.runtime.RuntimeError;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.language.jactl.JactlLanguage.jactl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JactlLanguageTest {

    static final List<String> CONTEXT_MAP_ALL_VARIABLES = List.of("request", "response", "exception", "exchange",
            "exchangeProperty", "exchangeProperties", "camelContext");

    static CamelContext context;

    @BeforeAll
    static void startContext() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterAll
    static void stopContext() {
        context.stop();
    }

    static Exchange exchange(String name, int age) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("age", age);
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader("gold", "123");
        return exchange;
    }

    @Test
    void resolveJactlLanguage() {
        Language language = context.resolveLanguage("jactl");
        assertThat(language).isInstanceOf(JactlLanguage.class);
        assertThat(context.resolveLanguage("jactl")).isSameAs(language);
    }

    @Test
    void mapBodyPredicate() {
        Predicate predicate = context.resolveLanguage("jactl").createPredicate("body.name != null && body.age > 20");
        assertThat(predicate.matches(exchange("tony", 44))).isTrue();
        assertThat(predicate.matches(exchange("tony", 10))).isFalse();
    }

    @Test
    void headerPredicate() {
        Predicate predicate = context.resolveLanguage("jactl").createPredicate("headers.gold == '123'");
        assertThat(predicate.matches(exchange("tony", 44))).isTrue();
    }

    @Test
    void headerSingularExpression() {
        Expression expression = context.resolveLanguage("jactl").createExpression("header.gold");
        assertThat(expression.evaluate(exchange("tony", 44), String.class)).isEqualTo("123");
    }

    @Test
    void variableExpression() {
        Exchange exchange = exchange("tony", 44);
        exchange.setVariable("province", "ontario");
        Expression expression = context.resolveLanguage("jactl").createExpression("variable.province");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("ontario");
        Predicate predicate = context.resolveLanguage("jactl").createPredicate("variables.province == 'ontario'");
        assertThat(predicate.matches(exchange)).isTrue();
    }

    @Test
    void builtinMethodsAllowedInSandbox() {
        // Jactl built-in methods on Maps/Lists/Strings don't need host access
        Exchange exchange = exchange("tony", 44);
        assertThat(context.resolveLanguage("jactl").createExpression("headers.size()").evaluate(exchange, Integer.class))
                .isEqualTo(1);
        assertThat(context.resolveLanguage("jactl").createExpression("body.size()").evaluate(exchange, Integer.class))
                .isEqualTo(2);
    }

    @Test
    void valueExpression() {
        Expression expression = context.resolveLanguage("jactl").createExpression("'Hello ' + body.name");
        assertThat(expression.evaluate(exchange("tony", 44), String.class)).isEqualTo("Hello tony");
    }

    @Test
    void resultTypeConversion() {
        Expression expression = context.resolveLanguage("jactl").createExpression("body.age * 2");
        assertThat(expression.evaluate(exchange("tony", 44), String.class)).isEqualTo("88");
    }

    @Test
    void defaultDisallowContextMapAll() {
        Language language = context.resolveLanguage("jactl");
        for (String name : CONTEXT_MAP_ALL_VARIABLES) {
            assertThatThrownBy(() -> language.createExpression(name)).as(name).isInstanceOf(CompileError.class);
        }
    }

    @Test
    void allowContextMapAllExposesAllVariables() {
        JactlLanguage language = JactlLanguage.create().withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        exchange.setProperty("orderId", "42");
        exchange.setVariable("province", "ontario");
        assertThat(language.createExpression("body").evaluate(exchange, Object.class)).isSameAs(exchange.getIn().getBody());
        assertThat(language.createExpression("header.gold").evaluate(exchange, String.class)).isEqualTo("123");
        assertThat(language.createExpression("headers.gold").evaluate(exchange, String.class)).isEqualTo("123");
        assertThat(language.createExpression("variable.province").evaluate(exchange, String.class)).isEqualTo("ontario");
        assertThat(language.createExpression("variables.province").evaluate(exchange, String.class)).isEqualTo("ontario");
        assertThat(language.createExpression("exchangeProperty.orderId").evaluate(exchange, String.class)).isEqualTo("42");
        assertThat(language.createExpression("exchangeProperties.orderId").evaluate(exchange, String.class)).isEqualTo("42");
        // Host objects can be referenced and returned without host access - only method calls need it
        assertThat(language.createExpression("request").evaluate(exchange, Object.class)).isSameAs(exchange.getIn());
        assertThat(language.createExpression("exchange").evaluate(exchange, Object.class)).isSameAs(exchange);
        assertThat(language.createExpression("camelContext").evaluate(exchange, Object.class)).isSameAs(context);
        // response is only populated for out-capable (InOut) exchanges, exception only when one has been set
        assertThat(language.createExpression("response").evaluate(exchange, Object.class)).isNull();
        assertThat(language.createExpression("exception").evaluate(exchange, Object.class)).isNull();
    }

    @Test
    void responsePopulatedWhenOutCapable() {
        JactlLanguage language = JactlLanguage.create().withAllowContextMapAll(true);
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("hello");
        assertThat(language.createExpression("response").evaluate(exchange, Object.class)).isSameAs(exchange.getMessage());
    }

    @Test
    void sandboxBlocksMethodCallsOnHostVariables() {
        JactlLanguage language = JactlLanguage.create().withAllowContextMapAll(true);
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("hello");
        exchange.setException(new IllegalArgumentException("boom"));
        for (String script : List.of("request.getBody()",
                "response.getBody()",
                "exception.getMessage()",
                "exchange.getExchangeId()",
                "camelContext.getName()")) {
            Expression expression = language.createExpression(script);
            assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                    .as(script)
                    .isInstanceOf(RuntimeError.class)
                    .hasMessageContaining("Access to host classes not allowed");
        }
    }

    @Test
    void defaultLanguageIsSandboxed() {
        CamelContext custom = new DefaultCamelContext();
        try {
            JactlLanguage hostAccess = JactlLanguage.create().withAllowContextMapAll(true);
            custom.getRegistry().bind("jactl", hostAccess);
            custom.start();
            assertThat(custom.resolveLanguage("jactl")).isSameAs(hostAccess);
            Expression expression = custom.resolveLanguage("jactl").createExpression("camelContext.getName()");
            assertThatThrownBy(() -> expression.evaluate(new DefaultExchange(custom), String.class))
                    .isInstanceOf(RuntimeError.class);
        } finally {
            custom.stop();
        }
    }

    @Test
    void hostObjectMethodCall() {
        Expression expression = JactlLanguage.createWithHostAccess()
                .withAllowContextMapAll(true)
                .createExpression("camelContext.getName()");
        assertThat(expression.evaluate(exchange("tony", 44), String.class)).isEqualTo(context.getName());
    }

    @Test
    void hostObjectMessageAccess() {
        Exchange exchange = exchange("tony", 44);
        Expression expression = JactlLanguage.createWithHostAccess()
                .withAllowContextMapAll(true)
                .createExpression("request.getBody()");
        assertThat(expression.evaluate(exchange, Object.class)).isSameAs(exchange.getIn().getBody());
    }

    @Test
    void hostClassPredicate() {
        JactlLanguage language = JactlLanguage.createWithHostAccess(name -> name.startsWith("org.apache.camel.support."))
                .withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        // request is an org.apache.camel.support.DefaultMessage - allowed
        Expression allowed = language.createExpression("request.getBody()");
        assertThat(allowed.evaluate(exchange, Object.class)).isSameAs(exchange.getIn().getBody());
        // camelContext is an org.apache.camel.impl.DefaultCamelContext - not allowed
        Expression denied = language.createExpression("camelContext.getName()");
        assertThatThrownBy(() -> denied.evaluate(exchange, String.class)).isInstanceOf(RuntimeError.class);
    }

    @Test
    void hostAccessRequestMethodCall() {
        // The predicate is checked against both the runtime class of the object and the declaring
        // class of the resolved method: getBody() is implemented in the MessageSupport base class
        JactlLanguage language = JactlLanguage.createWithHostAccess(Set.of("org.apache.camel.support.DefaultMessage",
                "org.apache.camel.support.MessageSupport")::contains)
                .withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        assertThat(language.createExpression("request.getBody()").evaluate(exchange, Object.class))
                .isSameAs(exchange.getIn().getBody());
        // getHeader(String) is declared directly on DefaultMessage
        assertThat(language.createExpression("request.getHeader('gold')").evaluate(exchange, String.class)).isEqualTo("123");
    }

    @Test
    void hostAccessResponseMethodCall() {
        JactlLanguage language = JactlLanguage.createWithHostAccess(name -> name.startsWith("org.apache.camel.support."))
                .withAllowContextMapAll(true);
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        Map<String, Object> body = new HashMap<>();
        body.put("name", "tony");
        exchange.getIn().setBody(body);
        Expression expression = language.createExpression("response.setBody('Bye ' + body.name); response.getBody()");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("Bye tony");
        assertThat(exchange.getMessage().getBody()).isEqualTo("Bye tony");
    }

    @Test
    void hostAccessExchangeMethodCall() {
        // getExchangeId() is declared directly on DefaultExchange so the runtime class alone is enough
        JactlLanguage language = JactlLanguage.createWithHostAccess("org.apache.camel.support.DefaultExchange"::equals)
                .withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        assertThat(language.createExpression("exchange.getExchangeId()").evaluate(exchange, String.class))
                .isEqualTo(exchange.getExchangeId());
    }

    @Test
    void hostAccessExceptionMethodCall() {
        // getMessage() is declared in java.lang.Throwable so it must be allowed along with the exception class
        JactlLanguage language = JactlLanguage.createWithHostAccess(Set.of("java.lang.IllegalArgumentException",
                "java.lang.Throwable")::contains)
                .withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        exchange.setException(new IllegalArgumentException("boom"));
        assertThat(language.createExpression("exception.getMessage()").evaluate(exchange, String.class)).isEqualTo("boom");
    }

    @Test
    void hostAccessCamelContextMethodCall() {
        // getName() is implemented by AbstractCamelContext which lives in a different package
        // (org.apache.camel.impl.engine) to the runtime class org.apache.camel.impl.DefaultCamelContext
        JactlLanguage language = JactlLanguage.createWithHostAccess(Set.of("org.apache.camel.impl.DefaultCamelContext",
                "org.apache.camel.impl.engine.AbstractCamelContext")::contains)
                .withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        assertThat(language.createExpression("camelContext.getName()").evaluate(exchange, String.class))
                .isEqualTo(context.getName());
    }

    @Test
    void hostAccessRequiresMethodDeclaringClassToBeAllowed() {
        // Allowing the runtime class is not enough if the method resolves to a base class that is not allowed
        JactlLanguage language = JactlLanguage.createWithHostAccess("org.apache.camel.impl.DefaultCamelContext"::equals)
                .withAllowContextMapAll(true);
        Expression expression = language.createExpression("camelContext.getName()");
        assertThatThrownBy(() -> expression.evaluate(exchange("tony", 44), String.class))
                .isInstanceOf(RuntimeError.class)
                .hasMessageContaining("base class");
    }

    @Test
    void hostAccessChainedMethodCalls() {
        // Each object in the chain is checked: DefaultExchange for getIn(), then DefaultMessage/MessageSupport for getBody()
        JactlLanguage language = JactlLanguage.createWithHostAccess(name -> name.startsWith("org.apache.camel.support."))
                .withAllowContextMapAll(true);
        Exchange exchange = exchange("tony", 44);
        assertThat(language.createExpression("exchange.getIn().getBody()").evaluate(exchange, Object.class))
                .isSameAs(exchange.getIn().getBody());
    }

    @Test
    void registryOverrideEnablesHostAccess() throws Exception {
        CamelContext custom = new DefaultCamelContext();
        try {
            JactlLanguage hostAccess = JactlLanguage.createWithHostAccess().withAllowContextMapAll(true);
            custom.getRegistry().bind("jactl", hostAccess);
            custom.start();
            assertThat(custom.resolveLanguage("jactl")).isSameAs(hostAccess);
            Expression expression = custom.resolveLanguage("jactl").createExpression("camelContext.getName()");
            assertThat(expression.evaluate(new DefaultExchange(custom), String.class)).isEqualTo(custom.getName());
        } finally {
            custom.stop();
        }
    }

    @Test
    void scriptingLanguageEvaluate() {
        JactlLanguage language = (JactlLanguage) context.resolveLanguage("jactl");
        Map<String, Object> ints = new HashMap<>();
        ints.put("x", 20);
        ints.put("y", 22);
        assertThat(language.evaluate("x + y", ints, Integer.class)).isEqualTo(42);
        // same script text must also work with differently typed values, since
        // binding names are declared as untyped globals
        Map<String, Object> strings = new HashMap<>();
        strings.put("x", "foo");
        strings.put("y", "bar");
        assertThat(language.evaluate("x + y", strings, String.class)).isEqualTo("foobar");
    }

    @Test
    void filterRoute() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:filter")
                        .filter(jactl("body.age > 20"))
                        .process(e -> passed.incrementAndGet());
            }
        });
        ProducerTemplate template = context.createProducerTemplate();
        Map<String, Object> adult = new HashMap<>();
        adult.put("age", 44);
        Map<String, Object> child = new HashMap<>();
        child.put("age", 10);
        template.sendBody("direct:filter", adult);
        template.sendBody("direct:filter", child);
        assertThat(passed.get()).isEqualTo(1);
        template.stop();
    }
}
