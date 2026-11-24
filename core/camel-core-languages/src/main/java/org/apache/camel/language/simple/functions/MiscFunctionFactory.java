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
import org.apache.camel.language.simple.MiscExpressionBuilder;
import org.apache.camel.language.simple.SimpleFunctionHelper;
import org.apache.camel.language.simple.XmlJsonExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitArgumentsKeepQuotes;

/**
 * Built-in Simple miscellaneous functions: {@code ${isEmpty}}, {@code ${isAlpha}}, {@code ${isAlphaNumeric}},
 * {@code ${isNumeric}}, {@code ${isValidXml}}, {@code ${isValidJson}}, {@code ${not}}, {@code ${kindOfType}},
 * {@code ${throwException}}, {@code ${assert}}, {@code ${convertTo}}, {@code ${messageHistory}}, {@code ${uuid}},
 * {@code ${hash}}, {@code ${empty}}, {@code ${newEmpty}}, {@code ${iif}}, {@code ${load}}.
 */
public final class MiscFunctionFactory implements SimpleLanguageFunctionFactory {

    @FunctionalInterface
    private interface FunctionParser {
        Expression parse(String remainder, String function, int index);
    }

    private static final Map<String, FunctionParser> PARSERS = new LinkedHashMap<>();

    static {
        PARSERS.put("isEmpty(", (r, f, i) -> simpleOptExpression(r, MiscExpressionBuilder::isEmptyExpression));
        PARSERS.put("isAlpha(", (r, f, i) -> simpleOptExpression(r, MiscExpressionBuilder::isAlphaExpression));
        PARSERS.put("isAlphaNumeric(",
                (r, f, i) -> simpleOptExpression(r, MiscExpressionBuilder::isAlphaNumericExpression));
        PARSERS.put("isValidXml(", (r, f, i) -> simpleOptExpression(r, XmlJsonExpressionBuilder::isValidXmlExpression));
        PARSERS.put("isValidJson(",
                (r, f, i) -> simpleOptExpression(r, XmlJsonExpressionBuilder::isValidJsonExpression));
        PARSERS.put("isNumeric(", (r, f, i) -> simpleOptExpression(r, MiscExpressionBuilder::isNumericExpression));
        PARSERS.put("not(", MiscFunctionFactory::parseNot);
        PARSERS.put("kindOfType(", (r, f, i) -> simpleOptExpression(r, MiscExpressionBuilder::kindOfTypeExpression));
        PARSERS.put("throwException(", MiscFunctionFactory::parseThrowException);
        PARSERS.put("assert(", MiscFunctionFactory::parseAssert);
        PARSERS.put("convertTo(", MiscFunctionFactory::parseConvertTo);
        PARSERS.put("hash(", MiscFunctionFactory::parseHash);
        PARSERS.put("empty(", MiscFunctionFactory::parseEmpty);
        PARSERS.put("newEmpty(", MiscFunctionFactory::parseNewEmpty);
        PARSERS.put("iif(", MiscFunctionFactory::parseIif);
        PARSERS.put("load(", MiscFunctionFactory::parseLoad);
    }

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        for (Map.Entry<String, FunctionParser> entry : PARSERS.entrySet()) {
            String remainder = ifStartsWithReturnRemainder(entry.getKey(), function);
            if (remainder != null) {
                return entry.getValue().parse(remainder, function, index);
            }
        }

        // messageHistory and uuid have non-standard prefix matching (no opening parenthesis required)
        Expression result = parseMessageHistory(function);
        if (result != null) {
            return result;
        }
        return parseUuid(function);
    }

    private static Expression parseMessageHistory(String function) {
        String remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null && remainder.startsWith("(")) {
            boolean detailed;
            String values = StringHelper.between(remainder, "(", ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                detailed = true;
            } else {
                detailed = Boolean.parseBoolean(values);
            }
            return MiscExpressionBuilder.messageHistoryExpression(detailed);
        } else if (ObjectHelper.equal(function, "messageHistory")) {
            return MiscExpressionBuilder.messageHistoryExpression(true);
        }
        return null;
    }

    private static Expression parseUuid(String function) {
        String remainder = ifStartsWithReturnRemainder("uuid", function);
        // ${uuid(kind)}, but ${uuidv7} is not the uuid function
        if (remainder != null && remainder.startsWith("(")) {
            String values = StringHelper.between(remainder, "(", ")");
            return MiscExpressionBuilder.uuidExpression(values);
        } else if (ObjectHelper.equal(function, "uuid")) {
            return MiscExpressionBuilder.uuidExpression(null);
        }
        return null;
    }

    private static Expression parseNot(String remainder, String function, int index) {
        String exp = "${body}";
        String value = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(value)) {
            exp = value;
        }
        return MiscExpressionBuilder.isNotPredicate(exp);
    }

    private static Expression parseThrowException(String remainder, String function, int index) {
        String msg;
        String type = null;
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${throwException(msg)} or ${throwException(msg,type)} was: " + function, index);
        }
        // a comma inside a quoted message or a nested function is part of the message
        String[] tokens = splitArgumentsKeepQuotes(values);
        if (tokens.length > 2 || tokens[0] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${throwException(msg)} or ${throwException(msg,type)} was: " + function, index);
        }
        msg = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
        if (tokens.length == 2 && tokens[1] != null) {
            // a type left out is the default type
            type = StringHelper.removeLeadingAndEndingQuotes(tokens[1]);
        }
        return MiscExpressionBuilder.throwExceptionExpression(msg, type);
    }

    private static Expression parseAssert(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException("Valid syntax: ${assert(exp,msg)} was: " + function, index);
        }
        String[] tokens = splitArgumentsKeepQuotes(values);
        if (tokens.length != 2 || tokens[0] == null || tokens[1] == null) {
            throw new SimpleParserException("Valid syntax: ${assert(exp,msg)} was: " + function, index);
        }
        return MiscExpressionBuilder.assertExpression(tokens[0], StringHelper.removeLeadingAndEndingQuotes(tokens[1]));
    }

    private static Expression parseConvertTo(String remainder, String function, int index) {
        String exp = "${body}";
        String type;
        int end = SimpleFunctionHelper.indexOfClosingParenthesis(remainder);
        String values = end >= 0 ? remainder.substring(0, end) : null;
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
        }
        String[] tokens = splitArgumentsKeepQuotes(values);
        if (tokens.length > 2 || tokens[tokens.length - 1] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
        }
        if (tokens.length == 2) {
            // an expression left out is the message body
            if (tokens[0] != null) {
                exp = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
            }
            type = StringHelper.removeLeadingAndEndingQuotes(tokens[1]);
        } else {
            type = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
        }
        remainder = remainder.substring(end + 1);
        if (ObjectHelper.isNotEmpty(remainder)) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type).OGNL} or ${convertTo(exp,type).OGNL} was: " + function, index);
            }
            return MiscExpressionBuilder.convertToOgnlExpression(exp, type, remainder);
        } else {
            return MiscExpressionBuilder.convertToExpression(exp, type);
        }
    }

    private static Expression parseHash(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
        }
        // ${hash()}: the message body with SHA-256, as documented; a comma inside quotes or a nested function is
        // part of the value
        String[] tokens = ObjectHelper.isEmpty(values) ? new String[] { null } : splitArgumentsKeepQuotes(values);
        if (tokens.length > 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
        }
        // a value left out is the message body, an algorithm left out is SHA-256
        String value = tokens[0] != null ? StringHelper.removeLeadingAndEndingQuotes(tokens[0]) : "${body}";
        if (tokens.length == 2 && tokens[1] != null) {
            return MiscExpressionBuilder.hashExpression(value, StringHelper.removeLeadingAndEndingQuotes(tokens[1]));
        } else {
            return MiscExpressionBuilder.hashExpression(value, "SHA-256");
        }
    }

    private static Expression parseEmpty(String remainder, String function, int index) {
        String value = StringHelper.before(remainder, ")");
        if (ObjectHelper.isEmpty(value)) {
            throw new SimpleParserException("Valid syntax: ${empty(<type>)} but was: " + function, index);
        }
        return MiscExpressionBuilder.newEmptyExpression(value);
    }

    private static Expression parseNewEmpty(String remainder, String function, int index) {
        String value = StringHelper.before(remainder, ")");
        if (ObjectHelper.isEmpty(value)) {
            throw new SimpleParserException("Valid syntax: ${newEmpty(<type>)} but was: " + function, index);
        }
        return MiscExpressionBuilder.newEmptyExpression(value);
    }

    private static Expression parseIif(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
        }
        String[] tokens = splitArgumentsKeepQuotes(values);
        if (tokens.length != 3 || tokens[0] == null || tokens[1] == null || tokens[2] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
        }
        // the predicate keeps its quotes (CAMEL-22204); a quoted value loses its quotes, as with the ternary
        // operator, but unlike the ternary the text inside the quotes is still evaluated as Simple
        // ('Hello ${body}' gives Hello World)
        return MiscExpressionBuilder.iifExpression(tokens[0],
                StringHelper.removeLeadingAndEndingQuotes(tokens[1]),
                StringHelper.removeLeadingAndEndingQuotes(tokens[2]));
    }

    private static Expression parseLoad(String remainder, String function, int index) {
        String value = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(value)) {
            throw new SimpleParserException("Valid syntax: ${load(name)} but was: " + function, index);
        }
        return MiscExpressionBuilder.loadExpression(StringHelper.removeLeadingAndEndingQuotes(value));
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
