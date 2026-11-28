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
package org.apache.camel.language.simple.functions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.StringExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.EscapeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.expressionOrBody;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitArguments;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitArgumentsKeepQuotes;

/**
 * Built-in Simple string functions: {@code ${replace}}, {@code ${substring}}, {@code ${substringBefore}},
 * {@code ${substringAfter}}, {@code ${substringBetween}}, {@code ${contains}}, {@code ${trim}}, {@code ${val}},
 * {@code ${capitalize}}, {@code ${pad}}, {@code ${concat}}, {@code ${quote}}, {@code ${safeQuote}}, {@code ${unquote}},
 * {@code ${uppercase}}, {@code ${lowercase}}, {@code ${length}}, {@code ${size}}, {@code ${normalizeWhitespace}},
 * {@code ${escape}}, {@code ${unescape}}.
 */
public final class StringFunctionFactory implements SimpleLanguageFunctionFactory {

    @FunctionalInterface
    private interface FunctionParser {
        Expression parse(String remainder, String function, int index);
    }

    private static final Map<String, FunctionParser> PARSERS = new LinkedHashMap<>();

    static {
        PARSERS.put("replace(", StringFunctionFactory::parseReplace);
        PARSERS.put("substring(", StringFunctionFactory::parseSubstring);
        PARSERS.put("substringBefore(", StringFunctionFactory::parseSubstringBefore);
        PARSERS.put("substringAfter(", StringFunctionFactory::parseSubstringAfter);
        PARSERS.put("substringBetween(", StringFunctionFactory::parseSubstringBetween);
        PARSERS.put("contains(", StringFunctionFactory::parseContains);
        PARSERS.put("trim(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::trimExpression));
        PARSERS.put("val(", StringFunctionFactory::parseVal);
        PARSERS.put("capitalize(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::capitalizeExpression));
        PARSERS.put("pad(", StringFunctionFactory::parsePad);
        PARSERS.put("concat(", StringFunctionFactory::parseConcat);
        PARSERS.put("quote(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::quoteExpression));
        PARSERS.put("safeQuote(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::safeQuoteExpression));
        PARSERS.put("unquote(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::unquoteExpression));
        PARSERS.put("uppercase(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::uppercaseExpression));
        PARSERS.put("lowercase(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::lowercaseExpression));
        PARSERS.put("length(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::lengthExpression));
        PARSERS.put("size(", (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::sizeExpression));
        PARSERS.put("escape(", StringFunctionFactory::parseEscape);
        PARSERS.put("unescape(", StringFunctionFactory::parseUnescape);
        PARSERS.put("normalizeWhitespace(",
                (r, f, i) -> simpleOptExpression(r, StringExpressionBuilder::normalizeWhitespaceExpression));
    }

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        for (Map.Entry<String, FunctionParser> entry : PARSERS.entrySet()) {
            String remainder = ifStartsWithReturnRemainder(entry.getKey(), function);
            if (remainder != null) {
                return entry.getValue().parse(remainder, function, index);
            }
        }
        return null;
    }

    private static Expression parseReplace(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${replace(from,to)} or ${replace(from,to,expression)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        // the from cannot be left out; a to left out is the empty text
        if (tokens.length < 2 || tokens.length > 3 || tokens[0] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${replace(from,to)} or ${replace(from,to,expression)} was: " + function, index);
        }
        String from = StringHelper.xmlDecode(tokens[0]);
        String to = tokens[1] == null ? "" : StringHelper.xmlDecode(tokens[1]);
        if ("&empty;".equals(to)) {
            to = "";
        }
        String exp = "${body}";
        if (tokens.length == 3) {
            exp = expressionOrBody(tokens[2]);
        }
        return StringExpressionBuilder.replaceExpression(exp, from, to);
    }

    private static Expression parseSubstring(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${substring(num)}, ${substring(num,num)}, or ${substring(num,num,expression)} was: "
                                            + function,
                    index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length > 3) {
            throw new SimpleParserException(
                    "Valid syntax: ${substring(num,num,expression)} was: " + function, index);
        }
        if (tokens[0] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${substring(num)}, ${substring(num,num)}, or ${substring(num,num,expression)} was: "
                                            + function,
                    index);
        }
        String num1 = tokens[0];
        String num2 = "0";
        if (tokens.length > 1 && tokens[1] != null) {
            num2 = tokens[1];
        }
        String exp = "${body}";
        if (tokens.length == 3) {
            exp = expressionOrBody(tokens[2]);
        }
        return StringExpressionBuilder.substringExpression(exp, num1, num2);
    }

    private static Expression parseSubstringBefore(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length > 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: " + function, index);
        }
        String exp1 = "${body}";
        String before;
        if (tokens.length == 2) {
            exp1 = expressionOrBody(tokens[0]);
            before = tokens[1];
        } else {
            before = tokens[0];
        }
        if (before == null) {
            // an argument left out is refused where it has no default, so ${substringBefore(x,)} is not read as
            // x before nothing
            throw new SimpleParserException(
                    "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: " + function, index);
        }
        return StringExpressionBuilder.substringBeforeExpression(exp1, before);
    }

    private static Expression parseSubstringAfter(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length > 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: " + function, index);
        }
        String exp1 = "${body}";
        String after;
        if (tokens.length == 2) {
            exp1 = expressionOrBody(tokens[0]);
            after = tokens[1];
        } else {
            after = tokens[0];
        }
        if (after == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: " + function, index);
        }
        return StringExpressionBuilder.substringAfterExpression(exp1, after);
    }

    private static Expression parseSubstringBetween(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringBetween(after,before)} or ${substringBetween(exp,after,before)} was: "
                                            + function,
                    index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length < 2 || tokens.length > 3) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringBetween(after,before)} or ${substringBetween(exp,after,before)} was: "
                                            + function,
                    index);
        }
        String exp1 = "${body}";
        String after;
        String before;
        if (tokens.length == 3) {
            exp1 = expressionOrBody(tokens[0]);
            after = tokens[1];
            before = tokens[2];
        } else {
            after = tokens[0];
            before = tokens[1];
        }
        if (after == null || before == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${substringBetween(after,before)} or ${substringBetween(exp,after,before)} was: "
                                            + function,
                    index);
        }
        return StringExpressionBuilder.substringBetweenExpression(exp1, after, before);
    }

    private static Expression parseContains(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length < 1 || tokens.length > 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
        }
        String exp = "${body}";
        String pattern;
        if (tokens.length == 1) {
            pattern = tokens[0];
        } else {
            exp = expressionOrBody(tokens[0]);
            pattern = tokens[1];
        }
        if (pattern == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
        }
        return StringExpressionBuilder.containsExpression(exp, pattern);
    }

    private static Expression parseVal(String remainder, String function, int index) {
        String value = StringHelper.beforeLast(remainder, ")");
        if (value == null || ObjectHelper.isEmpty(value)) {
            throw new SimpleParserException(
                    "Valid syntax: ${val(exp)} was: " + function, index);
        }
        // a quoted literal is the text inside the quotes
        return ExpressionBuilder.simpleExpression(StringHelper.removeLeadingAndEndingQuotes(value));
    }

    private static Expression parsePad(String remainder, String function, int index) {
        String exp;
        String len;
        String separator = null;
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function, index);
        }
        // a comma inside quotes or a nested function is part of the argument
        String[] tokens = splitArgumentsKeepQuotes(values);
        if (tokens.length < 2 || tokens.length > 3 || tokens[0] == null || tokens[1] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function, index);
        }
        exp = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
        len = StringHelper.removeLeadingAndEndingQuotes(tokens[1]);
        // a separator left out is a space
        if (tokens.length == 3 && tokens[2] != null) {
            separator = StringHelper.removeLeadingAndEndingQuotes(tokens[2]);
        }
        return StringExpressionBuilder.padExpression(exp, len, separator);
    }

    private static Expression parseConcat(String remainder, String function, int index) {
        String separator = null;
        String exp1 = "${body}";
        String exp2;
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                            + function,
                    index);
        }
        // a comma inside quotes or a nested function is part of the value, such as ${concat('Hello, ')}
        String[] tokens = splitArgumentsKeepQuotes(values);
        // the expression that must be given: the only one, or the second one when there are more
        int expIdx = tokens.length == 1 ? 0 : 1;
        if (tokens.length > 3 || tokens[expIdx] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                            + function,
                    index);
        }
        if (tokens.length >= 2) {
            // a first expression left out is the message body, a separator left out is none
            if (tokens[0] != null) {
                exp1 = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
            }
            exp2 = StringHelper.removeLeadingAndEndingQuotes(tokens[1]);
            if (tokens.length == 3 && tokens[2] != null) {
                separator = StringHelper.removeLeadingAndEndingQuotes(tokens[2]);
            }
        } else {
            exp2 = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
        }
        return StringExpressionBuilder.concatExpression(exp1, exp2, separator);
    }

    private static Expression parseEscape(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${escape(kind)} or ${escape(kind,expression)} was: " + function, index);
        }
        // only split on the first comma so the expression may contain nested functions with commas
        String kindName = StringHelper.before(values, ",", values).trim();
        String exp = StringHelper.after(values, ",");
        EscapeHelper.Kind kind = EscapeHelper.Kind.fromName(kindName);
        if (kind == null) {
            throw new SimpleParserException(
                    "Unknown escape kind: " + kindName + " (valid kinds: html, xml, json, js, sql, url) was: "
                                            + function,
                    index);
        }
        if (ObjectHelper.isNotEmpty(exp)) {
            exp = StringHelper.removeLeadingAndEndingQuotes(exp.trim());
        } else {
            exp = null;
        }
        return StringExpressionBuilder.escapeExpression(kind, exp);
    }

    private static Expression parseUnescape(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${unescape(kind)} or ${unescape(kind,expression)} was: " + function, index);
        }
        // only split on the first comma so the expression may contain nested functions with commas
        String kindName = StringHelper.before(values, ",", values).trim();
        String exp = StringHelper.after(values, ",");
        EscapeHelper.Kind kind = EscapeHelper.Kind.fromName(kindName);
        if (kind == null) {
            throw new SimpleParserException(
                    "Unknown unescape kind: " + kindName + " (valid kinds: html, xml, json, js, sql, url) was: "
                                            + function,
                    index);
        }
        if (ObjectHelper.isNotEmpty(exp)) {
            exp = StringHelper.removeLeadingAndEndingQuotes(exp.trim());
        } else {
            exp = null;
        }
        return StringExpressionBuilder.unescapeExpression(kind, exp);
    }

    private static Expression simpleOptExpression(String remainder, Function<String, Expression> builder) {
        String exp = null;
        String value = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(value)) {
            exp = StringHelper.removeLeadingAndEndingQuotes(value);
        }
        return builder.apply(exp);
    }
}
