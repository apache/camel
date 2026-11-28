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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.CollectionExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.expressionOrBody;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.joinArguments;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitArguments;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitArgumentsKeepQuotes;

/**
 * Built-in Simple collection functions: {@code ${setHeader}}, {@code ${setVariable}}, {@code ${range}},
 * {@code ${distinct}}, {@code ${reverse}}, {@code ${shuffle}}, {@code ${split}}, {@code ${sort}}, {@code ${forEach}},
 * {@code ${filter}}, {@code ${listAdd}}, {@code ${listRemove}}, {@code ${mapAdd}}, {@code ${mapRemove}},
 * {@code ${list}}, {@code ${map}}.
 */
public final class CollectionFunctionFactory implements SimpleLanguageFunctionFactory {

    @FunctionalInterface
    private interface FunctionParser {
        Expression parse(String remainder, String function, int index);
    }

    private static final Map<String, FunctionParser> PARSERS = new LinkedHashMap<>();

    static {
        PARSERS.put("setHeader(", CollectionFunctionFactory::parseSetHeader);
        PARSERS.put("setVariable(", CollectionFunctionFactory::parseSetVariable);
        PARSERS.put("range(", CollectionFunctionFactory::parseRange);
        PARSERS.put("distinct(", CollectionFunctionFactory::parseDistinct);
        PARSERS.put("reverse(", CollectionFunctionFactory::parseReverse);
        PARSERS.put("shuffle(", CollectionFunctionFactory::parseShuffle);
        PARSERS.put("split(", CollectionFunctionFactory::parseSplit);
        PARSERS.put("sort(", CollectionFunctionFactory::parseSort);
        PARSERS.put("forEach(", CollectionFunctionFactory::parseForEach);
        PARSERS.put("filter(", CollectionFunctionFactory::parseFilter);
        PARSERS.put("listAdd(", CollectionFunctionFactory::parseListAdd);
        PARSERS.put("listRemove(", CollectionFunctionFactory::parseListRemove);
        PARSERS.put("mapAdd(", CollectionFunctionFactory::parseMapAdd);
        PARSERS.put("mapRemove(", CollectionFunctionFactory::parseMapRemove);
        PARSERS.put("list(", CollectionFunctionFactory::parseList);
        PARSERS.put("map(", CollectionFunctionFactory::parseMap);
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

    private static Expression parseSetHeader(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length < 2 || tokens.length > 3) {
            throw new SimpleParserException(
                    "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function, index);
        }
        String name = tokens[0];
        String exp;
        String type = null;
        if (tokens.length == 3) {
            type = tokens[1];
            exp = tokens[2];
        } else {
            exp = tokens[1];
        }
        if (name == null || exp == null) {
            // an argument left out is refused where it has no default
            throw new SimpleParserException(
                    "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function, index);
        }
        type = StringHelper.removeQuotes(type);
        if (type != null && type.isEmpty()) {
            // an empty type is no type, as when it is left out
            type = null;
        }
        return CollectionExpressionBuilder.setHeaderExpression(name, type, exp);
    }

    private static Expression parseSetVariable(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length < 2 || tokens.length > 3) {
            throw new SimpleParserException(
                    "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function, index);
        }
        String name = tokens[0];
        String exp;
        String type = null;
        if (tokens.length == 3) {
            type = tokens[1];
            exp = tokens[2];
        } else {
            exp = tokens[1];
        }
        if (name == null || exp == null) {
            // an argument left out is refused where it has no default
            throw new SimpleParserException(
                    "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function, index);
        }
        type = StringHelper.removeQuotes(type);
        if (type != null && type.isEmpty()) {
            // an empty type is no type, as when it is left out
            type = null;
        }
        return CollectionExpressionBuilder.setVariableExpression(name, type, exp);
    }

    private static Expression parseRange(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${range(min,max)} or ${range(max)} was: " + function, index);
        }
        // a comma inside a nested function is part of the argument; a min left out is 0
        String[] tokens = splitArgumentsKeepQuotes(values);
        if (tokens.length > 2 || tokens[tokens.length - 1] == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${range(min,max)} or ${range(max)} was: " + function, index);
        }
        if (tokens.length == 2) {
            return CollectionExpressionBuilder.rangeExpression(tokens[0] != null ? tokens[0] : "0", tokens[1]);
        }
        return CollectionExpressionBuilder.rangeExpression("0", tokens[0]);
    }

    private static Expression parseDistinct(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String[] tokens;
        if (ObjectHelper.isNotEmpty(values)) {
            tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false, true);
        } else {
            tokens = new String[] { "${body}" };
        }
        return CollectionExpressionBuilder.distinctExpression(tokens);
    }

    private static Expression parseReverse(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String[] tokens;
        if (ObjectHelper.isNotEmpty(values)) {
            tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false, true);
        } else {
            tokens = new String[] { "${body}" };
        }
        return CollectionExpressionBuilder.reverseExpression(tokens);
    }

    private static Expression parseShuffle(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String[] tokens;
        if (ObjectHelper.isNotEmpty(values)) {
            tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false, true);
        } else {
            tokens = new String[] { "${body}" };
        }
        return CollectionExpressionBuilder.shuffleExpression(tokens);
    }

    private static Expression parseSplit(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String exp = "${body}";
        String separator = ",";
        if (ObjectHelper.isNotEmpty(values)) {
            String[] tokens = splitArguments(values);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${split(separator)} or ${split(exp,separator)} was: " + function, index);
            }
            if (tokens.length == 2) {
                exp = expressionOrBody(tokens[0]);
                separator = tokens[1];
            } else {
                separator = tokens[0];
            }
            if (separator == null) {
                // a separator left out is the default
                separator = ",";
            }
        } else if ("\n".equals(values)) {
            separator = values;
        }
        return CollectionExpressionBuilder.splitStringExpression(exp, separator);
    }

    private static Expression parseSort(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String exp = "${body}";
        boolean reverse = false;
        if (ObjectHelper.isNotEmpty(values)) {
            String[] tokens = splitArguments(values);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${sort(reverse)} or ${sort(exp,reverse)} was: " + function, index);
            }
            if (tokens.length == 2) {
                exp = expressionOrBody(tokens[0]);
                reverse = Boolean.parseBoolean(tokens[1]);
            } else if ("true".equalsIgnoreCase(tokens[0]) || "false".equalsIgnoreCase(tokens[0])) {
                reverse = Boolean.parseBoolean(tokens[0]);
            } else {
                // ${sort(${header.list})}: the only argument is what to sort
                exp = tokens[0];
            }
        }
        return CollectionExpressionBuilder.sortExpression(exp, reverse);
    }

    private static Expression parseForEach(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${forEach(exp,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length < 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${forEach(exp,exp)} was: " + function, index);
        }
        String exp1 = tokens[0];
        String exp2 = joinArguments(tokens, 1);
        if (exp1 == null || exp2 == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${forEach(exp,exp)} was: " + function, index);
        }
        return CollectionExpressionBuilder.forEachExpression(exp1, exp2);
    }

    private static Expression parseFilter(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${filter(exp,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length < 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${filter(exp,exp)} was: " + function, index);
        }
        String exp1 = tokens[0];
        String exp2 = joinArguments(tokens, 1);
        if (exp1 == null || exp2 == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${filter(exp,exp)} was: " + function, index);
        }
        return CollectionExpressionBuilder.filterExpression(exp1, exp2);
    }

    private static Expression parseListAdd(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${listAdd(exp)} or ${listAdd(exp,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        int skip = 0;
        String exp1 = "${body}";
        if (tokens.length > 1) {
            skip = 1;
            exp1 = expressionOrBody(tokens[0]);
        }
        String exp2 = joinArguments(tokens, skip);
        if (exp2 == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${listAdd(exp)} or ${listAdd(exp,exp)} was: " + function, index);
        }
        return CollectionExpressionBuilder.listAddExpression(exp1, exp2);
    }

    private static Expression parseListRemove(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${listRemove(exp)} or ${listRemove(exp,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        int skip = 0;
        String exp1 = "${body}";
        if (tokens.length > 1) {
            skip = 1;
            exp1 = expressionOrBody(tokens[0]);
        }
        String exp2 = joinArguments(tokens, skip);
        if (exp2 == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${listRemove(exp)} or ${listRemove(exp,exp)} was: " + function, index);
        }
        return CollectionExpressionBuilder.listRemoveExpression(exp1, exp2);
    }

    private static Expression parseMapAdd(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${mapAdd(key,exp)} or ${mapAdd(exp,key,exp)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        int skip;
        String exp1 = "${body}";
        String key;
        if (tokens.length > 2) {
            exp1 = expressionOrBody(tokens[0]);
            key = tokens[1];
            skip = 2;
        } else if (tokens.length == 2) {
            key = tokens[0];
            skip = 1;
        } else {
            throw new SimpleParserException(
                    "Valid syntax: ${mapAdd(key,exp)} or ${mapAdd(exp,key,exp)} was: " + function, index);
        }
        String exp2 = joinArguments(tokens, skip);
        if (key == null || exp2 == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${mapAdd(key,exp)} or ${mapAdd(exp,key,exp)} was: " + function, index);
        }
        return CollectionExpressionBuilder.mapAddExpression(exp1, key, exp2);
    }

    private static Expression parseMapRemove(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${mapRemove(key)} or ${mapRemove(exp,key)} was: " + function, index);
        }
        String[] tokens = splitArguments(values);
        if (tokens.length > 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${mapRemove(key)} or ${mapRemove(exp,key)} was: " + function, index);
        }
        String key;
        String exp = "${body}";
        if (tokens.length == 2) {
            exp = expressionOrBody(tokens[0]);
            key = tokens[1];
        } else {
            key = tokens[0];
        }
        if (key == null) {
            throw new SimpleParserException(
                    "Valid syntax: ${mapRemove(key)} or ${mapRemove(exp,key)} was: " + function, index);
        }
        return CollectionExpressionBuilder.mapRemoveExpression(exp, key);
    }

    private static Expression parseList(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String[] tokens = null;
        if (ObjectHelper.isNotEmpty(values)) {
            tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false, true);
        }
        return CollectionExpressionBuilder.listExpression(tokens);
    }

    private static Expression parseMap(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        String[] tokens = null;
        if (ObjectHelper.isNotEmpty(values)) {
            tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false, true);
        }
        if (tokens != null && tokens.length % 2 == 1) {
            throw new SimpleParserException(
                    "Map function must have an even number of values, was: " + tokens.length + " values.", index);
        }
        return CollectionExpressionBuilder.mapExpression(tokens);
    }
}
