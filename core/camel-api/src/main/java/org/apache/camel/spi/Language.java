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
package org.apache.camel.spi;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;

/**
 * SPI base for every Camel expression and predicate language.
 * <p/>
 * Implementations include {@code simple}, {@code groovy}, {@code jexl}, {@code mvel}, {@code xpath}, {@code jsonpath},
 * {@code spel}, and many others. They are resolved by name via
 * {@link org.apache.camel.CamelContext#resolveLanguage(String)}, which first checks the Camel registry and then falls
 * back to the classpath service loader at
 * {@link LanguageResolver#resolveLanguage(String, org.apache.camel.CamelContext)}. The two core factory methods —
 * {@link #createExpression(String)} and {@link #createPredicate(String)} — accept a text expression and return a
 * thread-safe, reusable instance. The overloaded {@code Object[]} variants allow passing pre-parsed language-specific
 * options (e.g., XPath namespace maps or tokenizer delimiters) to avoid repeated parsing overhead. All implementations
 * must be thread-safe because expressions and predicates are evaluated concurrently on multiple exchanges.
 * <p/>
 * See <a href="https://camel.apache.org/manual/languages.html">Languages</a> in the Camel user manual.
 *
 * @see LanguageResolver
 * @see org.apache.camel.Expression
 * @see org.apache.camel.Predicate
 */
public interface Language {

    /**
     * Creates a predicate based on <b>only</b> the given string input
     *
     * @param  expression the expression as a string input
     * @return            the created predicate
     */
    Predicate createPredicate(String expression);

    /**
     * Creates an expression based on <b>only</b> the given string input
     *
     * @param  expression the expression as a string input
     * @return            the created expression
     */
    Expression createExpression(String expression);

    /**
     * Creates an expression based on the input with properties
     * <p>
     * This is used for languages that have been configured with custom properties most noticeable for
     * xpath/xquery/tokenizer languages that have several options.
     *
     * @param  expression the expression
     * @param  properties configuration properties (optimized as object array with hardcoded positions for properties)
     * @return            the created predicate
     */
    default Predicate createPredicate(String expression, Object[] properties) {
        return createPredicate(expression);
    }

    /**
     * Creates an expression based on the input with properties
     * <p>
     * This is used for languages that have been configured with custom properties most noticeable for
     * xpath/xquery/tokenizer languages that have several options.
     *
     * @param  expression the expression
     * @param  properties configuration properties (optimized as object array with hardcoded positions for properties)
     * @return            the created expression
     */
    default Expression createExpression(String expression, Object[] properties) {
        return createExpression(expression);
    }
}
