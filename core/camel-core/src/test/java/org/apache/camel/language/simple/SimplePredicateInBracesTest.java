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
package org.apache.camel.language.simple;

import java.util.List;
import java.util.Map;

import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-24921: {@code ${ }} may hold a predicate, the way the braces do in Jakarta EL, Groovy and a JavaScript
 * template, instead of refusing the shape.
 */
public class SimplePredicateInBracesTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testPredicateInsideTheBraces() {
        exchange.getIn().setBody(List.of(Map.of("sku", "CAMEL-MUG")));
        assertPredicate("${body != null && body.size() > 0}", true);
        assertExpression("${body != null && body.size() > 0}", "true");

        exchange.getIn().setBody(List.of());
        assertPredicate("${body != null && body.size() > 0}", false);
    }

    @Test
    public void testOneComparison() {
        exchange.getIn().setHeader("n", 5);
        assertPredicate("${header.n > 0}", true);
        assertPredicate("${header.n > 10}", false);
        assertPredicate("${header.n == 5}", true);
        exchange.getIn().setBody("Hello");
        assertPredicate("${body contains 'ell'}", true);
        assertPredicate("${body.length() > 3}", true);
    }

    @Test
    public void testBothFormsAgree() {
        exchange.getIn().setHeader("n", 5);
        assertPredicate("${header.n > 0 && header.n < 10}", true);
        assertPredicate("${header.n} > 0 && ${header.n} < 10", true);
        assertPredicate("${header.n > 0 || header.n > 100}", true);
    }

    @Test
    public void testAValueInTextIsStillATemplate() {
        exchange.getIn().setHeader("n", 5);
        // outside the braces the expression is a template, and that has not changed
        assertExpression("${header.n} > 0", "5 > 0");
        // inside the braces it is the answer of the predicate, which a template can now hold
        assertExpression("Count ${header.n} is over three: ${header.n > 3}", "Count 5 is over three: true");
    }

    @Test
    public void testANameIsNotArithmetic() {
        // an operator counts only when whitespace surrounds it, so these are names and patterns as before
        exchange.getIn().setHeader("Content-Length", 42);
        assertExpression("${header.Content-Length}", "42");
        exchange.getIn().setHeader("aws-s3-bucket", "orders");
        assertExpression("${header.aws-s3-bucket}", "orders");
        assertExpression("${date:now:yyyy-MM-dd}".substring(0, 10) + "}", null, true);
    }

    @Test
    public void testAnOperatorWordInAValueIsNotAPredicate() {
        // CAMEL-24963: the operator word is in a property default or a bracket key, which is text, not an operator
        assertExpression("${properties:msg:value is not set}", "value is not set");
        assertExpression("${properties:msg:a == b}", "a == b");
        exchange.getIn().setHeader("order in progress", "yes");
        assertExpression("${header[order in progress]}", "yes");
        // a closed bracket key can still be compared
        exchange.getIn().setHeader("n", 5);
        assertPredicate("${header[n] == 5}", true);
    }

    @Test
    public void testTheTernaryStillWins() {
        exchange.getIn().setHeader("n", 5);
        assertExpression("${header.n > 0 ? 'positive' : 'negative'}", "positive");
        assertExpression("${header.n > 0 && header.n < 10 ? 'in' : 'out'}", "in");
    }

    private void assertExpression(String expression, Object expected, boolean onlyParse) {
        if (onlyParse) {
            context.resolveLanguage("simple").createExpression(expression);
            return;
        }
        assertExpression(expression, expected);
    }
}
