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

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAMEL-24703: the parser messages say what to write, for the mistakes people and AI agents make most.
 */
public class SimpleSyntaxHintsTest extends ExchangeTestSupport {

    private String predicateError(String text) {
        SimplePredicateParser parser = new SimplePredicateParser(context, text, true, null);
        return assertThrows(SimpleIllegalSyntaxException.class, parser::parsePredicate).getMessage();
    }

    private String expressionError(String text) {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, text, true, null);
        return assertThrows(SimpleIllegalSyntaxException.class, parser::parseExpression).getMessage();
    }

    @Test
    public void testFunctionWithoutDollarBraces() {
        assertThat(predicateError("body == 'Hello World'"))
                .contains("text outside ${...} is a literal")
                .contains("did you mean ${body} == 'Hello World'?");
        assertThat(predicateError("header.foo == 'bar'")).contains("did you mean ${header.foo} == 'bar'?");
    }

    @Test
    public void testOperatorInsideFunction() {
        assertThat(predicateError("${body == 'x'}")).contains("Operators go outside the function: ${body} == 'x'");
    }

    @Test
    public void testUnquotedValue() {
        assertThat(predicateError("${body} == x"))
                .contains("does not accept x on the right hand side")
                .contains("a quoted literal 'x'")
                .doesNotContain("token null");
        assertThat(predicateError("${body} contains x")).contains("does not accept x on the right hand side");
    }

    @Test
    public void testUnknownOperators() {
        assertThat(predicateError("${body} = 'x'")).contains("Unknown operator =: did you mean ==?");
        assertThat(predicateError("${body} == 'x' and ${header.y} == 1")).contains("use && for and");
        assertThat(predicateError("${body} == 'x' || ")).contains("needs a predicate on the right hand side");
    }

    @Test
    public void testQuotes() {
        assertThat(predicateError("${body} == 'it''s'")).contains("double quotes");
    }

    @Test
    public void testMissingClosingBrace() {
        assertThat(expressionError("${header.foo")).contains("missing } to close");
        assertThat(expressionError("${bodyAs(String)")).contains("missing } to close");
    }

    @Test
    public void testUnknownFunctionSuggestions() {
        assertThat(expressionError("${padding(3)}")).contains("Unknown function: padding(3)")
                .contains("did you mean ${pad(3)}?");
        assertThat(expressionError("${property.foo}")).contains("did you mean ${exchangeProperty.foo}?");
        assertThat(expressionError("${Body}")).contains("case sensitive: ${body}");
        assertThat(expressionError("${ body }")).contains("remove the spaces: ${body}");
        assertThat(expressionError("${bodyy}")).contains("did you mean ${body}?");
    }

    @Test
    public void testOperatorAfterOgnlMethod() {
        assertThat(predicateError("${body.length() > 3}")).contains("Operators go outside the function: ${body.length()} > 3");
    }

    @Test
    public void testOgnlRuntimeMessages() {
        exchange.getIn().setBody("hello");
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${body.lenght()}").evaluate(exchange, String.class));
        assertThat(e.getMessage())
                .contains("on the message body of type java.lang.String")
                .contains("did you mean length()?")
                .doesNotContain("on null");
    }

    @Test
    public void testOgnlDotOnAMapSaysToUseAKey() {
        exchange.getIn().setBody(new java.util.LinkedHashMap<>(java.util.Map.of("type", "order")));
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${body.type}").evaluate(exchange,
                        String.class));
        assertThat(e.getMessage()).contains("the value is a Map: a key is read with [type], as in ${body[type]}");
        assertEquals("order", context.resolveLanguage("simple").createExpression("${body[type]}").evaluate(exchange,
                String.class));
    }

    @Test
    public void testArithmeticInAFunctionSaysThereIsNone() {
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${exchangeCounter % 3}"));
        assertThat(e.getMessage()).contains("Unknown function: exchangeCounter % 3")
                .contains("simple has no arithmetic operators");
    }

    @Test
    public void testLanguageNameAsAFunctionIsNamed() {
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${simple}[${date:now:HH:mm}] size ${body.length}"));
        assertThat(e.getMessage()).contains("Unknown function: simple")
                .contains("simple is the language, not a function");
        Exception g = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${groovy(body.size())}"));
        assertThat(g.getMessage()).contains("groovy is a language, not a simple function").contains("groovy: \"...\"");
    }

    @Test
    public void testOgnlMethodWithoutParentheses() {
        exchange.getIn().setBody("hello");
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${body.toUpperCase}").evaluate(exchange,
                        String.class));
        assertThat(e.getMessage()).contains("a method call needs parentheses: toUpperCase()");
    }

    @Test
    public void testMissingLanguageNamesTheDependency() {
        // xquery is a built-in language whose jar (camel-saxon, not camel-xquery) is not on the classpath
        Exception e = assertThrows(Exception.class, () -> context.resolveLanguage("xquery"));
        assertThat(e.getMessage()).contains("No language could be found for: xquery")
                .contains("the xquery language is in camel-saxon; add camel-saxon to the classpath)");
    }

    @Test
    public void testUnknownLanguageSaysDidYouMean() {
        Exception e = assertThrows(Exception.class, () -> context.resolveLanguage("simpel"));
        assertThat(e.getMessage()).contains("No language could be found for: simpel")
                .contains("not a built-in Camel language; did you mean 'simple'?");

        e = assertThrows(Exception.class, () -> context.resolveLanguage("cheese"));
        assertThat(e.getMessage()).contains("No language could be found for: cheese")
                .contains("(not a built-in Camel language)").doesNotContain("did you mean");
    }

    @Test
    public void testFileFunctionDoesNotReadAFile() {
        assertThat(expressionError("${file:src/main/resources/input.xml}"))
                .contains("Unknown file language syntax")
                .contains("they do not read a file")
                .contains("poll EIP");
    }

    @Test
    public void testHelpers() {
        assertThat(SimpleSyntaxHints.wordAt("${body} == x", 12)).isEqualTo("x");
        assertThat(SimpleSyntaxHints.wordAt("${body} == 'x' and ${header.y} == 1", 15)).isEqualTo("and");
        assertThat(SimpleSyntaxHints.functionName("header.foo")).isEqualTo("header");
        assertThat(SimpleSyntaxHints.functionName("bodyAs(String)")).isEqualTo("bodyAs");
        assertThat(SimpleSyntaxHints.functionName("in.body")).isEqualTo("in.body");
        assertThat(SimpleSyntaxHints.operatorsOutside("iif(${body} == 'x', 'a', 'b')")).isNull();
        assertThat(SimpleSyntaxHints.operatorsOutside("body == 'x'")).isEqualTo("${body} == 'x'");
    }
}
