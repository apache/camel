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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.language.python3.Python3LanguageSecurityTest.assertNameError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The script variables have the names {@code ExchangeHelper.populateVariableMap} gives Groovy: default mode binds only
 * the data names, trusted host access also the host objects. The older {@code properties} and {@code context} still
 * work as deprecated names.
 */
@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3BindingNamesTest extends CamelTestSupport {

    private Exchange sampleExchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("foo", "abc");
        exchange.setProperty("color", "red");
        exchange.setVariable("name", "Camel");
        return exchange;
    }

    @Test
    void defaultModeBindsGroovyDataNames() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = sampleExchange();

        assertThat(evaluate(language, exchange, "body")).isEqualTo("hello");
        assertThat(evaluate(language, exchange, "header['foo']")).isEqualTo("abc");
        assertThat(evaluate(language, exchange, "headers['foo']")).isEqualTo("abc");
        assertThat(evaluate(language, exchange, "exchangeProperty['color']")).isEqualTo("red");
        assertThat(evaluate(language, exchange, "exchangeProperties['color']")).isEqualTo("red");
        assertThat(evaluate(language, exchange, "variable['name']")).isEqualTo("Camel");
        assertThat(evaluate(language, exchange, "variables['name']")).isEqualTo("Camel");
        assertThat(evaluate(language, exchange, "exchangeId")).isEqualTo(exchange.getExchangeId());
    }

    @Test
    void defaultModeKeepsDeprecatedProperties() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = sampleExchange();

        assertThat(evaluate(language, exchange, "properties['color']")).isEqualTo("red");
    }

    @Test
    void defaultModeDoesNotBindHostObjects() {
        Language language = context.resolveLanguage("python3");
        Exchange exchange = sampleExchange();
        exchange.setException(new IllegalArgumentException("Forced"));

        assertNameError(language, exchange, "exchange");
        assertNameError(language, exchange, "camelContext");
        assertNameError(language, exchange, "message");
        assertNameError(language, exchange, "request");
        assertNameError(language, exchange, "exception");
        assertNameError(language, exchange, "context");
    }

    @Test
    void trustedModeBindsGroovyNames() {
        Python3Language trusted = Python3Language.createWithHostAccess();
        trusted.setCamelContext(context);
        trusted.start();
        try {
            Exchange exchange = sampleExchange();

            assertThat(evaluate(trusted, exchange, "exchange.getExchangeId()")).isEqualTo(exchange.getExchangeId());
            assertThat(evaluate(trusted, exchange, "camelContext.getName()")).isEqualTo(context.getName());
            assertThat(evaluate(trusted, exchange, "message.getHeader('foo')")).isEqualTo("abc");
            assertThat(evaluate(trusted, exchange, "request.getHeader('foo')")).isEqualTo("abc");
            assertThat(evaluate(trusted, exchange, "header['foo']")).isEqualTo("abc");
            assertThat(evaluate(trusted, exchange, "exchangeProperties['color']")).isEqualTo("red");
            assertThat(evaluate(trusted, exchange, "variables['name']")).isEqualTo("Camel");
            assertThat(trusted.createPredicate("exception is None").matches(exchange)).isTrue();

            exchange.setException(new IllegalArgumentException("Forced"));
            assertThat(evaluate(trusted, exchange, "exception.getMessage()")).isEqualTo("Forced");
        } finally {
            trusted.stop();
        }
    }

    @Test
    void trustedModeKeepsDeprecatedNames() {
        Python3Language trusted = Python3Language.createWithHostAccess();
        trusted.setCamelContext(context);
        trusted.start();
        try {
            Exchange exchange = sampleExchange();

            assertThat(evaluate(trusted, exchange, "context.getName()")).isEqualTo(context.getName());
            assertThat(evaluate(trusted, exchange, "properties['color']")).isEqualTo("red");
        } finally {
            trusted.stop();
        }
    }

    private static String evaluate(Language language, Exchange exchange, String script) {
        return language.createExpression(script).evaluate(exchange, String.class);
    }
}
