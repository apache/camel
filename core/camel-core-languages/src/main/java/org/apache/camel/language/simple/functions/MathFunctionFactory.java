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

import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.MathExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.codeSplitSafe;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple math functions: {@code ${abs}}, {@code ${floor}}, {@code ${ceil}}, {@code ${sum}}, {@code ${max}},
 * {@code ${min}}, {@code ${average}}.
 */
public final class MathFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("abs(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            return MathExpressionBuilder.absExpression(
                    ObjectHelper.isNotEmpty(value) ? StringHelper.removeQuotes(value) : null);
        }
        remainder = ifStartsWithReturnRemainder("floor(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            return MathExpressionBuilder.floorExpression(
                    ObjectHelper.isNotEmpty(value) ? StringHelper.removeQuotes(value) : null);
        }
        remainder = ifStartsWithReturnRemainder("ceil(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            return MathExpressionBuilder.ceilExpression(
                    ObjectHelper.isNotEmpty(value) ? StringHelper.removeQuotes(value) : null);
        }
        remainder = ifStartsWithReturnRemainder("sum(", function);
        if (remainder != null) {
            return MathExpressionBuilder.sumExpression(parseVariadicTokens(remainder));
        }
        remainder = ifStartsWithReturnRemainder("max(", function);
        if (remainder != null) {
            return MathExpressionBuilder.maxExpression(parseVariadicTokens(remainder));
        }
        remainder = ifStartsWithReturnRemainder("min(", function);
        if (remainder != null) {
            return MathExpressionBuilder.minExpression(parseVariadicTokens(remainder));
        }
        remainder = ifStartsWithReturnRemainder("average(", function);
        if (remainder != null) {
            return MathExpressionBuilder.averageExpression(parseVariadicTokens(remainder));
        }

        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("sum(", function);
        if (remainder != null) {
            return codeVariadic("sum", remainder);
        }
        remainder = ifStartsWithReturnRemainder("abs(", function);
        if (remainder != null) {
            return codeUnary("abs", remainder, function, index);
        }
        remainder = ifStartsWithReturnRemainder("floor(", function);
        if (remainder != null) {
            return codeUnary("floor", remainder, function, index);
        }
        remainder = ifStartsWithReturnRemainder("ceil(", function);
        if (remainder != null) {
            return codeUnary("ceil", remainder, function, index);
        }
        remainder = ifStartsWithReturnRemainder("max(", function);
        if (remainder != null) {
            return codeVariadic("max", remainder);
        }
        remainder = ifStartsWithReturnRemainder("min(", function);
        if (remainder != null) {
            return codeVariadic("min", remainder);
        }
        remainder = ifStartsWithReturnRemainder("average(", function);
        if (remainder != null) {
            return codeVariadic("average", remainder);
        }

        return null;
    }

    private static String[] parseVariadicTokens(String remainder) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(values)) {
            return StringQuoteHelper.splitSafeQuote(values, ',', true, false);
        }
        return null;
    }

    private static String codeUnary(String name, String remainder, String function, int index) {
        String exp = null;
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(values)) {
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length != 1) {
                throw new SimpleParserException("Valid syntax: ${" + name + "(exp)} was: " + function, index);
            }
            String s = tokens[0];
            if (StringHelper.isSingleQuoted(s)) {
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
            }
            exp = s;
        }
        if (ObjectHelper.isEmpty(exp)) {
            exp = "null";
        }
        return "Object o = " + exp + ";\n        return " + name + "(exchange, o);";
    }

    private static String codeVariadic(String name, String remainder) {
        String values = StringHelper.beforeLast(remainder, ")");
        String[] tokens = null;
        if (ObjectHelper.isNotEmpty(values)) {
            tokens = codeSplitSafe(values, ',', true, true);
        }
        StringJoiner sj = new StringJoiner(", ");
        for (int i = 0; tokens != null && i < tokens.length; i++) {
            String s = tokens[i];
            if (StringHelper.isSingleQuoted(s)) {
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
            }
            sj.add(s);
        }
        String p = sj.length() > 0 ? sj.toString() : "null";
        return name + "(exchange, " + p + ")";
    }
}
