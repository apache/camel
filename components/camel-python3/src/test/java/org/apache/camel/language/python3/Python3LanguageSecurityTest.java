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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3LanguageSecurityTest {

    static CamelContext context;
    static Python3Language trusted;

    @BeforeAll
    static void startContext() {
        context = new DefaultCamelContext();
        context.start();
        trusted = Python3Language.createWithHostAccess();
        trusted.setCamelContext(context);
        trusted.start();
    }

    @AfterAll
    static void stopContext() {
        if (trusted != null) {
            trusted.stop();
        }
        context.stop();
    }

    static Language defaultLanguage() {
        return context.resolveLanguage("python3");
    }

    static Exchange sampleExchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Person("Ada", 36));
        exchange.getIn().setHeader("foo", "bar");
        exchange.setProperty("color", "red");
        return exchange;
    }

    @Test
    void defaultAllowsDataBindings() {
        Language language = defaultLanguage();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("foo", "bar");
        exchange.setProperty("color", "red");
        assertThat(language.createExpression("body").evaluate(exchange, String.class)).isEqualTo("hello");
        assertThat(language.createExpression("headers['foo']").evaluate(exchange, String.class)).isEqualTo("bar");
        assertThat(language.createExpression("headers['written'] = 'yes'\nheaders['written']").evaluate(exchange,
                String.class)).isEqualTo("yes");
        assertThat(exchange.getIn().getHeader("written")).isEqualTo("yes");
        assertThat(language.createExpression("properties['color']").evaluate(exchange, String.class)).isEqualTo("red");
        assertThat(language.createExpression("exchangeId").evaluate(exchange, String.class))
                .isEqualTo(exchange.getExchangeId());
    }

    @Test
    void defaultDoesNotBindExchangeMessageOrContext() {
        Language language = defaultLanguage();
        Exchange exchange = sampleExchange();
        assertNameError(language, exchange, "exchange");
        assertNameError(language, exchange, "message");
        assertNameError(language, exchange, "context");
    }

    @Test
    void defaultDeniesJavaMethodInvocation() {
        Language language = defaultLanguage();
        Exchange exchange = sampleExchange();
        assertDenied(language, exchange, "body.getAge()", "AttributeError");
        assertDenied(language, exchange, "headers.put('k', 'v')", "AttributeError");
    }

    @Test
    void defaultDeniesJavaTypeAndClassLookup() {
        Language language = defaultLanguage();
        Exchange exchange = sampleExchange();
        assertDenied(language, exchange, "java.type('java.lang.String')", "NameError");
        assertDenied(language, exchange, "from java.lang import Runtime", "host lookup is not allowed");
    }

    @Test
    void defaultDeniesPythonIoAndProcessCreation() {
        Language language = defaultLanguage();
        Exchange exchange = sampleExchange();
        assertDenied(language, exchange, "open('/etc/passwd')", "PermissionError");
        assertDenied(language, exchange, "import os\nos.system('true')", "Process creation is not allowed");
    }

    @Test
    void trustedAllowsCamelHostMethodsAndPojoAccess() {
        Exchange exchange = sampleExchange();
        assertThat(trusted.createExpression("exchange.getExchangeId()").evaluate(exchange, String.class))
                .isEqualTo(exchange.getExchangeId());
        assertThat(trusted.createExpression("message.getBody()").evaluate(exchange, Object.class))
                .isInstanceOf(Person.class);
        assertThat(trusted.createExpression("context.getName()").evaluate(exchange, String.class))
                .isEqualTo(context.getName());
        assertThat(trusted.createExpression("body.getAge()").evaluate(exchange, Integer.class)).isEqualTo(36);
        assertThat(trusted.createExpression("body.name").evaluate(exchange, String.class)).isEqualTo("Ada");
    }

    @Test
    void trustedStillDeniesClassLookupIoAndProcessCreation() {
        Exchange exchange = sampleExchange();
        assertDenied(trusted, exchange, "java.type('java.lang.Runtime')", "NameError");
        assertDenied(trusted, exchange, "from java.lang import Runtime", "host lookup is not allowed");
        assertDenied(trusted, exchange, "open('/etc/passwd')", "PermissionError");
        assertDenied(trusted, exchange, "import os\nos.system('true')", "Process creation is not allowed");
    }

    static void assertNameError(Language language, Exchange exchange, String name) {
        assertDenied(language, exchange, name, "NameError");
    }

    static void assertDenied(Language language, Exchange exchange, String script, String messageFragment) {
        Throwable thrown = catchThrowable(() -> language.createExpression(script).evaluate(exchange, Object.class));
        assertThat(thrown).as("expected %s to fail", script).isNotNull();
        assertThat(messageContains(thrown, messageFragment))
                .as("%s should mention \"%s\" but was: %s", script, messageFragment, thrown)
                .isTrue();
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
