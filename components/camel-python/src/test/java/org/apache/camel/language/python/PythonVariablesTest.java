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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.LanguageTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exchange variables are bound as {@code variable} and {@code variables}, next to {@code headers} and
 * {@code properties}.
 */
class PythonVariablesTest extends LanguageTestSupport {

    @Test
    void variablesAreBound() {
        exchange.setVariable("foo", "bar");
        exchange.setVariable("num", 5);

        assertExpression("variables['foo']", "bar");
        assertExpression("variable['foo']", "bar");
        assertExpression("variables['num'] + 2", 7);
        assertExpression("variables.get('missing') is None", true);
        assertPredicate("variables['foo'] == 'bar'");
        assertPredicate("variables['foo'] == 'baz'", false);
    }

    @Test
    void variableStoreIsOnlyCreatedForScriptsThatNameTheVariables() {
        Language language = context.resolveLanguage("python");
        AtomicInteger calls = new AtomicInteger();
        Exchange exchange = countingGetVariables(new DefaultExchange(context), calls);
        exchange.getMessage().setBody("Hello");

        assertEquals("Hello 0", language.createExpression("body + ' ' + str(len(headers))").evaluate(exchange, String.class));
        assertEquals(0, calls.get(), "a script without variables should not create the variable store");

        assertEquals("0", language.createExpression("str(len(variables))").evaluate(exchange, String.class));
        assertEquals(1, calls.get());
    }

    @Test
    void variablesSetInRoute() throws Exception {
        getMockEndpoint("mock:camel").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:other").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Camel", "greeting", "Hello");
        template.sendBodyAndHeader("direct:start", "World", "greeting", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setVariable("name", body())
                        .setVariable("greeting", header("greeting"))
                        .setBody().python("variables['greeting'] + ' ' + variable['name']")
                        .choice()
                        .when().python("variables['name'] == 'Camel'").to("mock:camel")
                        .otherwise().to("mock:other");
            }
        };
    }

    /**
     * The exchange, counting the calls of {@link Exchange#getVariables()}, which creates the variable store of the
     * exchange.
     */
    private static Exchange countingGetVariables(Exchange exchange, AtomicInteger calls) {
        return (Exchange) Proxy.newProxyInstance(Exchange.class.getClassLoader(), new Class<?>[] { Exchange.class },
                (proxy, method, args) -> {
                    if ("getVariables".equals(method.getName())) {
                        calls.incrementAndGet();
                    }
                    try {
                        return method.invoke(exchange, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    protected String getLanguageName() {
        return "python";
    }
}
