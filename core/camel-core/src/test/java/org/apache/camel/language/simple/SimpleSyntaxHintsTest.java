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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // CAMEL-24921: the braces may hold a predicate, which is then what they answer
        exchange.getIn().setBody("x");
        assertEquals(true, context.resolveLanguage("simple").createPredicate("${body == 'x'}").matches(exchange));
        exchange.getIn().setBody("y");
        assertEquals(false, context.resolveLanguage("simple").createPredicate("${body == 'x'}").matches(exchange));
        // and what is inside must still be a predicate the parser understands, reported against the wrapped text
        assertThat(predicateError("${body == }"))
                .contains("Unexpected token ==")
                .contains("${body} ==");
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
        // CAMEL-24970: the suggestions name the simple functions
        assertThat(expressionError("${upper}")).contains("did you mean ${uppercase()}?");
        assertThat(expressionError("${avg(1,2)}")).contains("did you mean ${average(1,2)}?");
        assertThat(expressionError("${uppercse()}")).contains("did you mean ${uppercase()}?");
        assertThat(expressionError("${count}")).contains("did you mean ${size()}?");
    }

    @Test
    public void testColonArgumentOnAParenthesisFunction() {
        // CAMEL-24845: jsonpath, jq, xpath and simpleJsonpath take their argument in parentheses. The suggestion
        // must say so, not echo back the text that was just rejected, or the reader writes it again
        assertThat(expressionError("${jsonpath:$.status}"))
                .contains("Unknown function: jsonpath:$.status")
                .contains("the argument goes in parentheses: did you mean ${jsonpath($.status)}?");
        assertThat(expressionError("${jq:.name}"))
                .contains("the argument goes in parentheses: did you mean ${jq(.name)}?");
        assertThat(expressionError("${xpath:/order/@id}"))
                .contains("the argument goes in parentheses: did you mean ${xpath(/order/@id)}?");
        assertThat(expressionError("${simpleJsonpath:$.status}"))
                .contains("the argument goes in parentheses: did you mean ${simpleJsonpath($.status)}?");
        // the json alias resolves to jsonpath, so its argument has to move into parentheses as well
        assertThat(expressionError("${json:$.status}"))
                .contains("the argument goes in parentheses: did you mean ${jsonpath($.status)}?");
    }

    @Test
    public void testBareQueryFunctionIsNotCalledANestedLanguage() {
        // CAMEL-24845: QueryLanguageFunctionFactory made these real simple functions, so the message must point at
        // the parenthesis form instead of claiming another language cannot be nested inside ${...}
        assertThat(expressionError("${jsonpath}"))
                .contains("the argument goes in parentheses: did you mean ${jsonpath(exp)}?")
                .doesNotContain("is a language");
        assertThat(expressionError("${jq}"))
                .contains("the argument goes in parentheses: did you mean ${jq(exp)}?");
    }

    @Test
    public void testASuggestionNeverRepeatsTheRejectedText() {
        // CAMEL-24845: bean and date do take a colon, so there is no parenthesis form to suggest for them, and the
        // did-you-mean must not degenerate into the input
        assertThat(SimpleSyntaxHints.unknownFunction("bean:myBean")).doesNotContain("${bean:myBean}");
        assertThat(SimpleSyntaxHints.unknownFunction("date:now:HH:mm")).doesNotContain("${date:now:HH:mm}");
        assertThat(SimpleSyntaxHints.unknownFunction("jsonpath:$.status")).doesNotContain("${jsonpath:$.status}");
    }

    @Test
    public void testOperatorAfterOgnlMethod() {
        // CAMEL-24921: an OGNL call on the left of the operator is wrapped as the function it is
        exchange.getIn().setBody("hello");
        assertEquals(true, context.resolveLanguage("simple").createPredicate("${body.length() > 3}").matches(exchange));
        exchange.getIn().setBody("hi");
        assertEquals(false, context.resolveLanguage("simple").createPredicate("${body.length() > 3}").matches(exchange));
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
    public void testOgnlDotOnAMapReadsTheKey() {
        // CAMEL-24916: a map has no method type, so the key is what the dot can mean
        exchange.getIn().setBody(new java.util.LinkedHashMap<>(java.util.Map.of("type", "order")));
        assertEquals("order", context.resolveLanguage("simple").createExpression("${body.type}").evaluate(exchange,
                String.class));
        assertEquals("order", context.resolveLanguage("simple").createExpression("${body[type]}").evaluate(exchange,
                String.class));
    }

    @Test
    public void testOgnlDotOnAMapWithoutThatKeySaysToUseAKey() {
        exchange.getIn().setBody(new java.util.LinkedHashMap<>(java.util.Map.of("type", "order")));
        Exception e = assertThrows(Exception.class,
                () -> context.resolveLanguage("simple").createExpression("${body.typo}").evaluate(exchange,
                        String.class));
        assertThat(e.getMessage()).contains("the value is a Map: a key is read with [typo], as in ${body[typo]}");
    }

    @Test
    public void testOgnlDotOnAMapWithANullValueAnswersNull() {
        // a key that is there and holds null is a value, not a missing key
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("sku", null);
        exchange.getIn().setBody(body);
        assertNull(context.resolveLanguage("simple").createExpression("${body.sku}").evaluate(exchange, Object.class),
                "a null value is a map entry: the expression answers null rather than throwing");
    }

    @Test
    public void testAMethodOfAMapStillWins() {
        exchange.getIn().setBody(new java.util.LinkedHashMap<>(java.util.Map.of("size", "not the size")));
        assertEquals("1", context.resolveLanguage("simple").createExpression("${body.size}").evaluate(exchange,
                String.class), "size() is a method of Map, so it still answers before the key");
    }

    @Test
    public void testOgnlDotOnANestedMapReadsTheKey() {
        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("sku", "CAMEL-MUG");
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("item", item);
        exchange.getIn().setBody(body);
        assertEquals("CAMEL-MUG", context.resolveLanguage("simple").createExpression("${body.item.sku}")
                .evaluate(exchange, String.class));
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
