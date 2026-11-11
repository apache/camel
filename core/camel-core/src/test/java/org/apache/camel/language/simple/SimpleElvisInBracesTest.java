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

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26401: the elvis operator may be written inside {@code ${ }}, where {@code ${a ?: b}} means {@code ${a} ?: b},
 * as a ternary and a predicate may since CAMEL-24921.
 */
public class SimpleElvisInBracesTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testBothFormsAgree() {
        assertExpression("${header.missing} ?: 10", "10");
        assertExpression("${header.missing ?: 10}", "10");
        exchange.getIn().setHeader("limit", 25);
        assertExpression("${header.limit} ?: 10", "25");
        assertExpression("${header.limit ?: 10}", "25");
        assertExpression("${exchangeProperty.foobar ?: 'none'}", "cba");
        assertExpression("${variable.missing ?: 'none'}", "none");
        assertExpression("${body?.length() ?: 0}", "31");
    }

    @Test
    public void testTheSameValuesFallBack() {
        // null, empty, false and 0 fall back, as with the operator after the braces
        exchange.getIn().setHeader("empty", "");
        exchange.getIn().setHeader("no", false);
        exchange.getIn().setHeader("zero", 0);
        exchange.getIn().setHeader("decimalZero", 0.0d);
        exchange.getIn().setHeader("yes", true);
        exchange.getIn().setHeader("five", 5);
        for (String header : new String[] { "missing", "empty", "no", "zero", "decimalZero", "yes", "five" }) {
            Object after = evaluateExpression("${header." + header + "} ?: 'fallback'", String.class);
            assertExpression("${header." + header + " ?: 'fallback'}", after);
        }
        assertExpression("${header.empty ?: 'fallback'}", "fallback");
        assertExpression("${header.five ?: 'fallback'}", "5");
    }

    @Test
    public void testNestedFunctionsAndChains() {
        exchange.getIn().setHeader("other", "o");
        assertExpression("${header.missing ?: ${header.other}}", "o");
        assertExpression("${header.missing ?: ${header.missing2} ?: 'c'}", "c");
        assertExpression("${header.missing ?: ${header.other} ?: 'c'}", "o");
        assertExpression("${header.missing ?: ${header.missing2 ?: 'deep'}}", "deep");
        assertExpression("${header.${header.missing ?: 'other'}}", "o");
        // the same chain after the braces
        assertExpression("${header.missing} ?: ${header.missing2} ?: 'c'", "c");
    }

    @Test
    public void testQuotedValues() {
        assertExpression("${header.missing ?: 'a b'}", "a b");
        assertExpression("${header.missing ?: \"it's\"}", "it's");
        assertExpression("${header.missing ?: 'a ?: b'}", "a ?: b");
        assertExpression("${header.missing ?: ''}", "");
    }

    @Test
    public void testInATemplate() {
        assertExpression("Limit: ${header.missing ?: 10}", "Limit: 10");
        assertExpression("${header.missing ?: 10} rows", "10 rows");
        assertExpression("${header.missing ?: 10 rows}", "10 rows");
        assertExpression("${header.missing} ?: 10 rows", "10 rows");
        exchange.getIn().setHeader("offset", 20);
        assertExpression("LIMIT ${header.offset ?: 0}, ${header.limit ?: 10}", "LIMIT 20, 10");
    }

    @Test
    public void testInAPredicate() {
        // the function inside a predicate reads it the same way
        assertPredicate("${header.missing ?: 10} == 10", true);
        assertPredicate("${header.bar ?: 10} == 123", true);
        // inside the braces the comparison takes the value the elvis falls back to
        exchange.getIn().setHeader("n", 5);
        assertPredicate("${header.n ?: 0 > 3}", true);
        assertPredicate("${header.missing ?: 0 > 3}", false);
    }

    @Test
    public void testTheTernaryIsNotConfusedWithIt() {
        exchange.getIn().setBody("x");
        assertExpression("${body == 'x' ? 'yes' : 'no'}", "yes");
        assertExpression("${body?.length()}", "1");
        exchange.getIn().setHeader("n", 5);
        assertExpression("${header.n == 5 ? header.missing ?: 'd' : 'no'}", "d");
        assertExpression("${header.n ?: 0 > 3 ? 'big' : 'small'}", "big");
        assertExpression("${header.missing ?: 0 > 3 ? 'big' : 'small'}", "small");
    }

    @Test
    public void testWithoutTheSpacesItIsText() {
        // an operator counts only with a space on both sides, outside the braces and inside them
        assertExpression("${header.missing} ?:10", " ?:10");
        assertExpression("${header.missing}?: 10", "?: 10");
        assertExpression("${header.missing ?:10}", null);
        assertExpression("${header.missing?: 10}", null);
        exchange.getIn().setHeader("missing ?:10", "a header of that name");
        assertExpression("${header.missing ?:10}", "a header of that name");
    }

    @Test
    public void testInQuotesBracketsOrADefaultItIsText() {
        assertExpression("${properties:msg:which ?: none}", "which ?: none");
        exchange.getIn().setHeader("a ?: b", "bracket");
        assertExpression("${header[a ?: b]}", "bracket");
    }

    @Test
    public void testTheRewrite() {
        assertThat(SimpleSyntaxHints.elvisOutside("header.a ?: 'x'")).isEqualTo("${header.a} ?: 'x'");
        assertThat(SimpleSyntaxHints.elvisOutside("body.replace('a ?: b', 'c')")).isNull();
        assertThat(SimpleSyntaxHints.elvisOutside("iif(header.a ?: false, 'x', 'y')")).isNull();
        assertThat(SimpleSyntaxHints.elvisOutside("header.a ?:10")).isNull();
        assertThat(SimpleSyntaxHints.elvisOutside("header.a ?: ")).isNull();
        assertThat(SimpleSyntaxHints.elvisOutside("header.a ?: ${header.b ?: 'x'}"))
                .isEqualTo("${header.a} ?: ${header.b ?: 'x'}");
    }

    @Test
    public void testAnErrorSaysWhatIsWrong() {
        assertThatThrownBy(() -> context.resolveLanguage("simple").createExpression("${header.missing ?: Guest}"))
                .isInstanceOf(SimpleIllegalSyntaxException.class)
                .hasMessageContaining("does not accept Guest on the right hand side")
                .hasMessageContaining("'Guest'");
    }
}
