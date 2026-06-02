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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.StringExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.codeSplitSafe;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple string functions: {@code ${replace}}, {@code ${substring}}, {@code ${substringBefore}},
 * {@code ${substringAfter}}, {@code ${substringBetween}}, {@code ${contains}}, {@code ${trim}}, {@code ${val}},
 * {@code ${capitalize}}, {@code ${pad}}, {@code ${concat}}, {@code ${quote}}, {@code ${safeQuote}}, {@code ${unquote}},
 * {@code ${uppercase}}, {@code ${lowercase}}, {@code ${length}}, {@code ${size}}, {@code ${normalizeWhitespace}}.
 */
public final class StringFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("replace(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to)} or ${replace(from,to,expression)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to,expression)} was: " + function, index);
            }
            String from = StringHelper.xmlDecode(tokens[0]);
            String to = StringHelper.xmlDecode(tokens[1]);
            if ("&empty;".equals(to)) {
                to = "";
            }
            String exp = "${body}";
            if (tokens.length == 3) {
                exp = tokens[2];
            }
            return StringExpressionBuilder.replaceExpression(exp, from, to);
        }

        remainder = ifStartsWithReturnRemainder("substring(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num)}, ${substring(num,num)}, or ${substring(num,num,expression)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num,num,expression)} was: " + function, index);
            }
            String num1 = tokens[0];
            String num2 = "0";
            if (tokens.length > 1) {
                num2 = tokens[1];
            }
            String exp = "${body}";
            if (tokens.length == 3) {
                exp = tokens[2];
            }
            return StringExpressionBuilder.substringExpression(exp, num1, num2);
        }

        remainder = ifStartsWithReturnRemainder("substringBefore(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: " + function, index);
            }
            String exp1 = "${body}";
            String before;
            if (tokens.length == 2) {
                exp1 = tokens[0];
                before = tokens[1];
            } else {
                before = tokens[0];
            }
            return StringExpressionBuilder.substringBeforeExpression(exp1, before);
        }

        remainder = ifStartsWithReturnRemainder("substringAfter(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: " + function, index);
            }
            String exp1 = "${body}";
            String after;
            if (tokens.length == 2) {
                exp1 = tokens[0];
                after = tokens[1];
            } else {
                after = tokens[0];
            }
            return StringExpressionBuilder.substringAfterExpression(exp1, after);
        }

        remainder = ifStartsWithReturnRemainder("substringBetween(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)} or ${substringAfter(exp,after,before)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)} or ${substringAfter(exp,after,before)} was: "
                                                + function,
                        index);
            }
            String exp1 = "${body}";
            String after;
            String before;
            if (tokens.length == 3) {
                exp1 = tokens[0];
                after = tokens[1];
                before = tokens[2];
            } else {
                after = tokens[0];
                before = tokens[1];
            }
            return StringExpressionBuilder.substringBetweenExpression(exp1, after, before);
        }

        remainder = ifStartsWithReturnRemainder("contains(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 1 || tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
            }
            String exp = "${body}";
            String pattern;
            if (tokens.length == 1) {
                pattern = tokens[0];
            } else {
                exp = tokens[0];
                pattern = tokens[1];
            }
            return StringExpressionBuilder.containsExpression(exp, pattern);
        }

        remainder = ifStartsWithReturnRemainder("trim(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.trimExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("val(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (value == null || ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException(
                        "Valid syntax: ${val(exp)} was: " + function, index);
            }
            return ExpressionBuilder.simpleExpression(value);
        }

        remainder = ifStartsWithReturnRemainder("capitalize(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.capitalizeExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("pad(", function);
        if (remainder != null) {
            String exp;
            String len;
            String separator = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(len)} or ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function, index);
            }
            exp = StringHelper.removeQuotes(tokens[0]);
            len = StringHelper.removeQuotes(tokens[1]);
            if (tokens.length == 3) {
                separator = StringHelper.removeQuotes(tokens[2]);
            }
            return StringExpressionBuilder.padExpression(exp, len, separator);
        }

        remainder = ifStartsWithReturnRemainder("concat(", function);
        if (remainder != null) {
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
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 3) {
                    throw new SimpleParserException(
                            "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                                    + function,
                            index);
                }
                exp1 = StringHelper.removeQuotes(tokens[0]);
                exp2 = StringHelper.removeQuotes(tokens[1]);
                if (tokens.length == 3) {
                    separator = StringHelper.removeQuotes(tokens[2]);
                }
            } else {
                exp2 = StringHelper.removeQuotes(values.trim());
            }
            return StringExpressionBuilder.concatExpression(exp1, exp2, separator);
        }

        remainder = ifStartsWithReturnRemainder("quote(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.quoteExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("safeQuote(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.safeQuoteExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("unquote(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.unquoteExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("uppercase(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.uppercaseExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("lowercase(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.lowercaseExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("length(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.lengthExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("size(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.sizeExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("normalizeWhitespace(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return StringExpressionBuilder.normalizeWhitespaceExpression(exp);
        }

        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("substring(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num)}, ${substring(num,num)} was: " + function, index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num,num)} was: " + function, index);
            }
            String num1 = tokens[0].trim();
            String num2 = tokens.length > 1 ? tokens[1].trim() : "0";
            return "substring(exchange, " + num1 + ", " + num2 + ")";
        }

        remainder = ifStartsWithReturnRemainder("substringBefore(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(before)}, ${substringBefore(exp,before)} was: " + function,
                        index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(before)}, ${substringBefore(exp,before)} was: " + function,
                        index);
            }
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = squoteToDouble(tokens[i]);
            }
            String body = tokens.length > 1 ? tokens[0] : "body";
            String before = tokens.length > 1 ? tokens[1] : tokens[0];
            return "Object value = " + body + ";\n        Object before = " + before
                   + ";\n        return substringBefore(exchange, value, before);";
        }

        remainder = ifStartsWithReturnRemainder("substringAfter(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(before)}, ${substringAfter(exp,before)} was: " + function,
                        index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(before)}, ${substringAfter(exp,before)} was: " + function,
                        index);
            }
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = squoteToDouble(tokens[i]);
            }
            String body = tokens.length > 1 ? tokens[0] : "body";
            String after = tokens.length > 1 ? tokens[1] : tokens[0];
            return "Object value = " + body + ";\n        Object after = " + after
                   + ";\n        return substringAfter(exchange, value, after);";
        }

        remainder = ifStartsWithReturnRemainder("substringBetween(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)}, ${substringBetween(exp,after,before)} was: "
                                                + function,
                        index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)}, ${substringBetween(exp,after,before)} was: "
                                                + function,
                        index);
            }
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = squoteToDouble(tokens[i]);
            }
            String body = tokens.length == 3 ? tokens[0] : "body";
            String after = tokens.length == 3 ? tokens[1] : tokens[0];
            String before = tokens.length == 3 ? tokens[2] : tokens[1];
            return "Object value = " + body + ";\n        Object after = " + after
                   + ";\n        Object before = " + before
                   + ";\n        return substringBetween(exchange, value, after, before);";
        }

        remainder = ifStartsWithReturnRemainder("contains(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 1 || tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${contains(text)} or ${contains(exp,text)} was: " + function, index);
            }
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = squoteToDouble(tokens[i]);
            }
            String exp = tokens.length == 1 ? "body" : tokens[0];
            String pattern = tokens.length == 1 ? tokens[0] : tokens[1];
            pattern = StringHelper.removeLeadingAndEndingQuotes(pattern);
            pattern = StringQuoteHelper.doubleQuote(pattern);
            return "Object value = " + exp + ";\n        return containsIgnoreCase(exchange, value, " + pattern + ");";
        }

        remainder = ifStartsWithReturnRemainder("replace(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to)} was: " + function, index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to)} was: " + function, index);
            }
            String from = StringHelper.xmlDecode(tokens[0]);
            String to = StringHelper.xmlDecode(tokens[1]);
            if ("&empty;".equals(to)) {
                to = "";
            }
            if ("\"".equals(from)) {
                from = "\\\"";
            }
            if ("\"".equals(to)) {
                to = "\\\"";
            }
            from = StringQuoteHelper.doubleQuote(from);
            to = StringQuoteHelper.doubleQuote(to);
            return "replace(exchange, " + from + ", " + to + ")";
        }

        remainder = ifStartsWithReturnRemainder("trim(", function);
        if (remainder != null) {
            return codeSimpleUnary("trim", remainder, function, index, "null");
        }

        remainder = ifStartsWithReturnRemainder("val(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${val(exp)} was: " + function, index);
            }
            String s = StringHelper.removeLeadingAndEndingQuotes(values);
            s = StringQuoteHelper.doubleQuote(s);
            return "Object o = " + s + ";\n        return o;";
        }

        remainder = ifStartsWithReturnRemainder("capitalize(", function);
        if (remainder != null) {
            return codeSimpleUnary("capitalize", remainder, function, index, "null");
        }

        remainder = ifStartsWithReturnRemainder("pad(", function);
        if (remainder != null) {
            String separator = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(len)} or ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function,
                        index);
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function, index);
            }
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = squoteToDouble(tokens[i]);
            }
            if (tokens.length == 3) {
                separator = tokens[2];
            }
            separator = StringHelper.removeLeadingAndEndingQuotes(separator);
            separator = separator != null ? StringQuoteHelper.doubleQuote(separator) : "null";
            return "Object value = " + tokens[0] + ";\n        " + "Object width = " + tokens[1]
                   + ";\n        String separator = " + separator
                   + ";\n        return pad(exchange, value, width, separator);";
        }

        remainder = ifStartsWithReturnRemainder("concat(", function);
        if (remainder != null) {
            String separator = "null";
            String exp1 = "body";
            String exp2;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                                + function,
                        index);
            }
            if (values.contains(",")) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length > 3) {
                    throw new SimpleParserException(
                            "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                                    + function,
                            index);
                }
                for (int i = 0; i < tokens.length; i++) {
                    tokens[i] = squoteToDouble(tokens[i]);
                }
                if (tokens.length == 1) {
                    exp2 = tokens[0];
                } else {
                    exp1 = tokens[0];
                    exp2 = tokens[1];
                }
                if (tokens.length == 3) {
                    separator = tokens[2];
                }
            } else {
                String s = values.trim();
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
                exp2 = s;
            }
            return "Object right = " + exp2 + ";\n        Object left = " + exp1 + ";\n        " + "Object separator = "
                   + separator + ";\n        return concat(exchange, left, right, separator);";
        }

        remainder = ifStartsWithReturnRemainder("quote(", function);
        if (remainder != null) {
            return codeSimpleUnary("quote", remainder, function, index, "null");
        }

        remainder = ifStartsWithReturnRemainder("safeQuote(", function);
        if (remainder != null) {
            return codeSimpleUnary("safeQuote", remainder, function, index, "body");
        }

        remainder = ifStartsWithReturnRemainder("unquote(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${unquote(exp)} was: " + function, index);
                }
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = s.replace("\"", "\\\"");
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "null";
            }
            return "Object o = " + exp + ";\n        return unquote(exchange, o);";
        }

        remainder = ifStartsWithReturnRemainder("uppercase(", function);
        if (remainder != null) {
            return codeSimpleUnary("uppercase", remainder, function, index, "null");
        }

        remainder = ifStartsWithReturnRemainder("lowercase(", function);
        if (remainder != null) {
            return codeSimpleUnary("lowercase", remainder, function, index, "null");
        }

        remainder = ifStartsWithReturnRemainder("length(", function);
        if (remainder != null) {
            return codeSimpleUnary("length", remainder, function, index, "body");
        }

        remainder = ifStartsWithReturnRemainder("size(", function);
        if (remainder != null) {
            return codeSimpleUnary("size", remainder, function, index, "body");
        }

        remainder = ifStartsWithReturnRemainder("normalizeWhitespace(", function);
        if (remainder != null) {
            return codeSimpleUnary("normalizeWhitespace", remainder, function, index, "null");
        }

        return null;
    }

    private static String codeSimpleUnary(String name, String remainder, String function, int index, String defaultExp) {
        String exp = null;
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(values)) {
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length != 1) {
                throw new SimpleParserException(
                        "Valid syntax: ${" + name + "(exp)} was: " + function, index);
            }
            String s = tokens[0];
            if (StringHelper.isSingleQuoted(s)) {
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
            }
            exp = s;
        }
        if (ObjectHelper.isEmpty(exp)) {
            exp = defaultExp;
        }
        return "Object o = " + exp + ";\n        return " + name + "(exchange, o);";
    }

    private static String squoteToDouble(String s) {
        if (StringHelper.isSingleQuoted(s)) {
            s = StringHelper.removeLeadingAndEndingQuotes(s);
            s = StringQuoteHelper.doubleQuote(s);
        }
        return s;
    }
}
