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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.language.python3.Python3LanguageSecurityTest.assertDenied;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exchange variables are bound as the data binding {@code variables}, in the same way as {@code headers}: default mode
 * may index the map but may not call Java methods on it.
 */
@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3VariablesTest extends CamelTestSupport {

    @Test
    void defaultModeBindsVariablesAsData() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("foo", "bar");
        exchange.setVariable("num", 5);

        assertThat(language.createExpression("variables['foo']").evaluate(exchange, String.class)).isEqualTo("bar");
        assertThat(language.createExpression("variables['num'] + 2").evaluate(exchange, Integer.class)).isEqualTo(7);
        assertThat(language.createExpression("'missing' in variables").evaluate(exchange, Boolean.class)).isFalse();
        assertThat(language.createPredicate("variables['foo'] == 'bar'").matches(exchange)).isTrue();
        assertThat(language.createPredicate("variables['foo'] == 'baz'").matches(exchange)).isFalse();
    }

    @Test
    void defaultModeWritesPropagateLikeHeaders() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("foo", "bar");

        assertThat(language.createExpression("variables['written'] = 'yes'\nvariables['written']")
                .evaluate(exchange, String.class)).isEqualTo("yes");
        assertThat(exchange.getVariable("written")).isEqualTo("yes");
    }

    @Test
    void defaultModeDeniesJavaMethodsOnVariables() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("foo", "bar");

        assertDenied(language, exchange, "variables.put('k', 'v')", "AttributeError");
        assertDenied(language, exchange, "variables.getClass()", "AttributeError");
    }

    @Test
    void trustedModeAlsoBindsVariables() {
        Python3Language trusted = Python3Language.createWithHostAccess();
        trusted.setCamelContext(context);
        trusted.start();
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.setVariable("foo", "bar");

            assertThat(trusted.createExpression("variables['foo']").evaluate(exchange, String.class)).isEqualTo("bar");
            assertThat(trusted.createExpression("exchange.getVariable('foo')").evaluate(exchange, String.class))
                    .isEqualTo("bar");
        } finally {
            trusted.stop();
        }
    }

    @Test
    void variableStoreIsOnlyCreatedForScriptsThatNameTheVariables() {
        Language language = context.resolveLanguage("python3");
        AtomicInteger calls = new AtomicInteger();
        Exchange exchange = countingGetVariables(new DefaultExchange(context), calls);
        exchange.getMessage().setBody("Hello");

        assertThat(language.createExpression("body + ' ' + str('foo' in headers)").evaluate(exchange, String.class))
                .isEqualTo("Hello False");
        assertThat(calls).as("a script without variables should not create the variable store").hasValue(0);

        assertThat(language.createPredicate("'foo' in variables").matches(exchange)).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test
    void variablesSetInRoute() throws Exception {
        getMockEndpoint("mock:camel").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:other").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Camel", "greeting", "Hello");
        template.sendBodyAndHeader("direct:start", "World", "greeting", "Hello");

        MockEndpoint.assertIsSatisfied(context);
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
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setVariable("name", body())
                        .setVariable("greeting", header("greeting"))
                        .setBody().python3("f\"{variables['greeting']} {variables['name']}\"")
                        .choice()
                        .when().python3("variables['name'] == 'Camel'").to("mock:camel")
                        .otherwise().to("mock:other");
            }
        };
    }
}
