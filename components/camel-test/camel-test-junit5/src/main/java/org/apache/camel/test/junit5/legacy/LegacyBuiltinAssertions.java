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

package org.apache.camel.test.junit5.legacy;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Language;
import org.apache.camel.test.junit5.TestSupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public interface LegacyBuiltinAssertions {
    CamelContext context();

    /**
     * Asserts that the given language name and expression evaluates to the given value on a specific exchange
     */
    @Deprecated(since = "4.7.0")
    default void assertExpression(Exchange exchange, String languageName, String expressionText, Object expectedValue) {
        TestSupport.assertExpression(context(), exchange, languageName, expressionText, expectedValue);
    }

    /**
     * Asserts that the given language name and predicate expression evaluates to the expected value on the message
     * exchange
     */
    @Deprecated(since = "4.7.0")
    default void assertPredicate(String languageName, String expressionText, Exchange exchange, boolean expected) {
        TestSupport.assertPredicate(context(), languageName, expressionText, exchange, expected);
    }

    /**
     * Asserts that the language name can be resolved
     */
    @Deprecated(since = "4.7.0")
    default Language assertResolveLanguage(String languageName) {
        return TestSupport.assertResolveLanguage(context(), languageName);
    }

    /**
     * Asserts the validity of the context
     *
     * @deprecated         Use JUnit's assertions if needed
     * @param      context
     */
    @Deprecated(since = "4.7.0")
    default void assertValidContext(CamelContext context) {
        assertNotNull(context(), "No context found!");
    }
}
