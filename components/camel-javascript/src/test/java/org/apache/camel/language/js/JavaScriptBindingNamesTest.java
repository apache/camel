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
package org.apache.camel.language.js;

import org.apache.camel.test.junit6.LanguageTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * The script variables have the names {@code ExchangeHelper.populateVariableMap} gives Groovy, and the older
 * {@code context} and {@code properties} still work as deprecated names.
 */
@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class JavaScriptBindingNamesTest extends LanguageTestSupport {

    @Test
    void groovyNamesAreBound() {
        exchange.setProperty("color", "red");
        exchange.setVariable("foo", "bar");

        assertExpression("exchange.getExchangeId() == exchangeId", true);
        assertExpression("camelContext.getName()", context.getName());
        assertExpression("message.getHeader('foo')", "abc");
        assertExpression("request.getHeader('foo')", "abc");
        assertExpression("body", "<hello id='m123'>world!</hello>");
        assertExpression("header.foo", "abc");
        assertExpression("headers.foo", "abc");
        assertExpression("exchangeProperty.color", "red");
        assertExpression("exchangeProperties.color", "red");
        assertExpression("variable.foo", "bar");
        assertExpression("variables.foo", "bar");
    }

    @Test
    void exceptionIsBound() {
        assertExpression("exception == null", true);

        exchange.setException(new IllegalArgumentException("Forced"));
        assertExpression("exception.getMessage()", "Forced");
    }

    @Test
    void deprecatedNamesStillWork() {
        exchange.setProperty("color", "red");

        assertExpression("context.getName()", context.getName());
        assertExpression("context === camelContext", true);
        assertExpression("properties.color", "red");
    }

    @Override
    protected String getLanguageName() {
        return "js";
    }
}
