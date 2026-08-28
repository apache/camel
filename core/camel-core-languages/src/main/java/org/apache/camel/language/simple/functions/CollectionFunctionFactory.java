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

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.CollectionExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple collection functions: {@code ${setHeader}}, {@code ${setVariable}}, {@code ${range}},
 * {@code ${distinct}}, {@code ${reverse}}, {@code ${shuffle}}, {@code ${split}}, {@code ${sort}}, {@code ${forEach}},
 * {@code ${filter}}, {@code ${listAdd}}, {@code ${listRemove}}, {@code ${mapAdd}}, {@code ${mapRemove}},
 * {@code ${list}}, {@code ${map}}.
 */
public final class CollectionFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("setHeader(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
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
            type = StringHelper.removeQuotes(type);
            return CollectionExpressionBuilder.setHeaderExpression(name, type, exp);
        }

        remainder = ifStartsWithReturnRemainder("setVariable(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
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
            type = StringHelper.removeQuotes(type);
            return CollectionExpressionBuilder.setVariableExpression(name, type, exp);
        }

        remainder = ifStartsWithReturnRemainder("range(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${range(min,max)} or ${range(max)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = values.split(",", 3);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${range(min,max)} or ${range(max)} was: " + function, index);
                }
                return CollectionExpressionBuilder.rangeExpression(tokens[0].trim(), tokens[1].trim());
            } else {
                return CollectionExpressionBuilder.rangeExpression("1", values.trim());
            }
        }

        remainder = ifStartsWithReturnRemainder("distinct(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            } else {
                tokens = new String[] { "${body}" };
            }
            return CollectionExpressionBuilder.distinctExpression(tokens);
        }

        remainder = ifStartsWithReturnRemainder("reverse(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            } else {
                tokens = new String[] { "${body}" };
            }
            return CollectionExpressionBuilder.reverseExpression(tokens);
        }

        remainder = ifStartsWithReturnRemainder("shuffle(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            } else {
                tokens = new String[] { "${body}" };
            }
            return CollectionExpressionBuilder.shuffleExpression(tokens);
        }

        remainder = ifStartsWithReturnRemainder("split(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String exp = "${body}";
            String separator = ",";
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${split(separator)} or ${split(exp,separator)} was: " + function, index);
                }
                if (tokens.length == 2) {
                    exp = tokens[0];
                    separator = tokens[1];
                } else {
                    separator = tokens[0];
                }
            } else if ("\n".equals(values)) {
                separator = values;
            }
            return CollectionExpressionBuilder.splitStringExpression(exp, separator);
        }

        remainder = ifStartsWithReturnRemainder("sort(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String exp = "${body}";
            boolean reverse = false;
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${sort(reverse)} or ${sort(exp,reverse)} was: " + function, index);
                }
                if (tokens.length == 2) {
                    exp = tokens[0];
                    reverse = Boolean.parseBoolean(tokens[1]);
                } else {
                    reverse = Boolean.parseBoolean(tokens[0]);
                }
            }
            return CollectionExpressionBuilder.sortExpression(exp, reverse);
        }

        remainder = ifStartsWithReturnRemainder("forEach(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${forEach(exp,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${forEach(exp,exp)} was: " + function, index);
            }
            String exp1 = tokens[0];
            String exp2 = Arrays.stream(tokens).skip(1).collect(Collectors.joining(","));
            return CollectionExpressionBuilder.forEachExpression(exp1, exp2);
        }

        remainder = ifStartsWithReturnRemainder("filter(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${filter(exp,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${filter(exp,exp)} was: " + function, index);
            }
            String exp1 = tokens[0];
            String exp2 = Arrays.stream(tokens).skip(1).collect(Collectors.joining(","));
            return CollectionExpressionBuilder.filterExpression(exp1, exp2);
        }

        remainder = ifStartsWithReturnRemainder("listAdd(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${listAdd(exp)} or ${listAdd(exp,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            int skip = 0;
            String exp1 = "${body}";
            if (tokens.length > 1) {
                skip = 1;
                exp1 = tokens[0];
            }
            String exp2 = Arrays.stream(tokens).skip(skip).collect(Collectors.joining(","));
            return CollectionExpressionBuilder.listAddExpression(exp1, exp2);
        }

        remainder = ifStartsWithReturnRemainder("listRemove(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${listRemove(exp)} or ${listRemove(exp,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            int skip = 0;
            String exp1 = "${body}";
            if (tokens.length > 1) {
                skip = 1;
                exp1 = tokens[0];
            }
            String exp2 = Arrays.stream(tokens).skip(skip).collect(Collectors.joining(","));
            return CollectionExpressionBuilder.listRemoveExpression(exp1, exp2);
        }

        remainder = ifStartsWithReturnRemainder("mapAdd(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${mapAdd(key,exp)} or ${mapAdd(exp,key,exp)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            int skip;
            String exp1 = "${body}";
            String key;
            if (tokens.length > 2) {
                exp1 = tokens[0];
                key = tokens[1];
                skip = 2;
            } else if (tokens.length == 2) {
                key = tokens[0];
                skip = 1;
            } else {
                throw new SimpleParserException(
                        "Valid syntax: ${mapAdd(key,exp)} or ${mapAdd(exp,key,exp)} was: " + function, index);
            }
            String exp2 = Arrays.stream(tokens).skip(skip).collect(Collectors.joining(","));
            return CollectionExpressionBuilder.mapAddExpression(exp1, key, exp2);
        }

        remainder = ifStartsWithReturnRemainder("mapRemove(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${mapRemove(key)} or ${mapRemove(exp,key)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${mapRemove(key)} or ${mapRemove(exp,key)} was: " + function, index);
            }
            String key;
            String exp = "${body}";
            if (tokens.length == 2) {
                exp = tokens[0];
                key = tokens[1];
            } else {
                key = tokens[0];
            }
            return CollectionExpressionBuilder.mapRemoveExpression(exp, key);
        }

        remainder = ifStartsWithReturnRemainder("list(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            return CollectionExpressionBuilder.listExpression(tokens);
        }

        remainder = ifStartsWithReturnRemainder("map(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            if (tokens != null && tokens.length % 2 == 1) {
                throw new SimpleParserException(
                        "Map function must have an even number of values, was: " + tokens.length + " values.", index);
            }
            return CollectionExpressionBuilder.mapExpression(tokens);
        }

        return null;
    }
}
