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
package org.apache.camel;

import org.apache.camel.spi.Language;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A useful base class for testing the language plugins in Camel
 */
public abstract class LanguageTestSupport extends ExchangeTestSupport {

    protected abstract String getLanguageName();

    /**
     * Asserts that the given predicate expression evaluated on the current language and message exchange evaluates to
     * true
     */
    protected void assertPredicate(String expression) {
        assertPredicate(exchange, expression, true);
    }

    /**
     * Asserts that the given predicate expression evaluated on the current language and message exchange evaluates to
     * false
     */
    protected void assertPredicateFails(String expression) {
        assertPredicate(exchange, expression, false);
    }

    /**
     * Asserts that the given predicate expression evaluated on the current language and message exchange evaluates to
     * the expected value
     */
    protected void assertPredicate(String expression, boolean expected) {
        assertPredicate(exchange, expression, expected);
    }

    protected void assertPredicate(Exchange exchange, String expression, boolean expected) {
        assertPredicate(getLanguageName(), expression, exchange, expected);
    }

    /**
     * Asserts that this language expression evaluates to the given value on the given exchange
     */
    protected void assertExpression(Exchange exchange, String expressionText, Object expectedValue) {
        assertExpression(exchange, getLanguageName(), expressionText, expectedValue);
    }

    /**
     * Asserts that this language expression evaluates to the given value on the current exchange
     */
    protected void assertExpression(String expressionText, Object expectedValue) {
        assertExpression(exchange, expressionText, expectedValue);
    }

    /**
     * Asserts that the expression evaluates to one of the two given values
     */
    protected void assertExpression(String expressionText, String expectedValue, String orThisExpectedValue) {
        Object value = evaluateExpression(expressionText, expectedValue.getClass());

        assertTrue(expectedValue.equals(value) || orThisExpectedValue.equals(value),
                "Expression: " + expressionText + " on Exchange: " + exchange);
    }

    /**
     * Evaluates the expression
     */
    protected Object evaluateExpression(String expressionText, Class<?> expectedType) {
        Language language = assertResolveLanguage(getLanguageName());

        Expression expression = language.createExpression(expressionText);
        assertNotNull(expression, "No Expression could be created for text: " + expressionText + " language: " + language);

        Object value;
        if (expectedType != null) {
            value = expression.evaluate(exchange, expectedType);
        } else {
            value = expression.evaluate(exchange, Object.class);
        }
        log.debug("Evaluated expression: {} on exchange: {} result: {}", expression, exchange, value);

        return value;
    }

}
