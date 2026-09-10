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
package org.apache.camel.language.groovy;

import java.util.Map;

import groovy.lang.GroovyShell;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The binding of a groovy expression exposes the exchange variables, and the compiled script of an expression is reused
 * until the language cache is cleared.
 */
public class GroovyExpressionBindingTest {

    private CamelContext context;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.start();
        exchange = new DefaultExchange(context);
        exchange.getIn().setBody("World");
        exchange.getIn().setHeader("name", "James");
        exchange.setProperty("myProperty", "myValue");
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    private Object evaluate(String script) {
        Expression expression = context.resolveLanguage("groovy").createExpression(script);
        expression.init(context);
        return expression.evaluate(exchange, Object.class);
    }

    @Test
    public void testExchangeVariables() {
        assertEquals("World", evaluate("body"));
        assertEquals("James", evaluate("header.name"));
        assertEquals("James", evaluate("headers.name"));
        assertEquals("myValue", evaluate("exchangeProperty.myProperty"));
        assertEquals("myValue", evaluate("exchangeProperties.myProperty"));
        assertSame(exchange, evaluate("exchange"));
        assertSame(exchange.getIn(), evaluate("request"));
        assertSame(context, evaluate("camelContext"));
        assertEquals(Boolean.TRUE, evaluate("attachments.isEmpty()"));
        assertEquals(Boolean.TRUE, evaluate("log != null"));
    }

    @Test
    public void testOutOnlyWhenOutCapable() {
        assertEquals(Boolean.FALSE, evaluate("binding.hasVariable('response')"));
        exchange.setPattern(ExchangePattern.InOut);
        assertEquals(Boolean.TRUE, evaluate("binding.hasVariable('response')"));
        assertEquals("World", evaluate("response.body"));
    }

    @Test
    public void testBodyIsReadWhenTheBindingIsCreated() {
        assertEquals("World", evaluate("exchange.in.body = 'Changed'; body"));
        assertEquals("Changed", exchange.getIn().getBody());
    }

    @Test
    public void testScriptVariables() {
        assertEquals(10, evaluate("x = 5; x * 2"));
        assertEquals("Bye", evaluate("body = 'Bye'; body"));
        assertEquals("World", exchange.getIn().getBody());
    }

    @Test
    public void testBindingVariables() {
        assertEquals(Boolean.TRUE, evaluate(
                "binding.variables.keySet().containsAll(['body', 'header', 'headers', 'variable', 'variables', 'exception',"
                                            + " 'in', 'request', 'exchange', 'exchangeProperty', 'exchangeProperties',"
                                            + " 'camelContext', 'attachments', 'log'])"));
        assertEquals("World", evaluate("binding.variables.body"));
        assertEquals("Bye", evaluate("body = 'Bye'; binding.variables.body"));
    }

    @Test
    public void testUnknownVariable() {
        Exception e = assertThrows(Exception.class, () -> evaluate("doesNotExist"));
        assertTrue(e.getMessage().contains("doesNotExist"), e.getMessage());
    }

    @Test
    public void testShellFactoryVariables() {
        context.getRegistry().bind("shellFactory", new GroovyShellFactory() {
            @Override
            public GroovyShell createGroovyShell(Exchange exchange) {
                return new GroovyShell();
            }

            @Override
            public Map<String, Object> getVariables(Exchange exchange) {
                return Map.of("greeting", "Hello", "body", "not used");
            }
        });
        // exchange variables take precedence over global variables with the same name
        assertEquals("Hello World", evaluate("greeting + ' ' + body"));
    }

    @Test
    public void testCompiledScriptIsReusedUntilTheCacheIsCleared() {
        GroovyLanguage language = (GroovyLanguage) context.resolveLanguage("groovy");
        Expression expression = language.createExpression("getClass()");
        expression.init(context);
        Class<?> first = expression.evaluate(exchange, Class.class);

        // more distinct scripts than the language cache holds
        for (int i = 0; i < 1100; i++) {
            language.createExpression("getClass() // " + i).evaluate(exchange, Class.class);
        }
        assertSame(first, expression.evaluate(exchange, Class.class));

        // the cache is cleared when the language stops (and on reload in dev profile)
        language.stop();
        assertNotSame(first, expression.evaluate(exchange, Class.class));
    }
}
