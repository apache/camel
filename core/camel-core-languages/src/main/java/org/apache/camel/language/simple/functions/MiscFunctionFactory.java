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
import org.apache.camel.language.simple.MiscExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple miscellaneous functions: {@code ${isEmpty}}, {@code ${isAlpha}}, {@code ${isAlphaNumeric}},
 * {@code ${isNumeric}}, {@code ${not}}, {@code ${kindOfType}}, {@code ${throwException}}, {@code ${assert}},
 * {@code ${convertTo}}, {@code ${messageHistory}}, {@code ${uuid}}, {@code ${hash}}, {@code ${empty}},
 * {@code ${newEmpty}}, {@code ${iif}}, {@code ${load}}.
 *
 * <p>
 */
public final class MiscFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("isEmpty(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isEmptyExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("isAlpha(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isAlphaExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("isAlphaNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isAlphaNumericExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("isNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isNumericExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("not(", function);
        if (remainder != null) {
            String exp = "${body}";
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = value;
            }
            return MiscExpressionBuilder.isNotPredicate(exp);
        }

        remainder = ifStartsWithReturnRemainder("kindOfType(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.kindOfTypeExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("throwException(", function);
        if (remainder != null) {
            String msg;
            String type = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${throwException(msg)} or ${throwException(type,msg)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${throwException(msg)} or ${throwException(type,msg)} was: " + function, index);
                }
                msg = StringHelper.removeQuotes(tokens[0]);
                type = StringHelper.removeQuotes(tokens[1]);
            } else {
                msg = StringHelper.removeQuotes(values.trim());
            }
            return MiscExpressionBuilder.throwExceptionExpression(msg, type);
        }

        remainder = ifStartsWithReturnRemainder("assert(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${assert(exp,msg)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length != 2) {
                throw new SimpleParserException("Valid syntax: ${assert(exp,msg)} was: " + function, index);
            }
            return MiscExpressionBuilder.assertExpression(tokens[0], StringHelper.removeQuotes(tokens[1]));
        }

        remainder = ifStartsWithReturnRemainder("convertTo(", function);
        if (remainder != null) {
            String exp = "${body}";
            String type;
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
                }
                exp = StringHelper.removeQuotes(tokens[0]);
                type = StringHelper.removeQuotes(tokens[1]);
            } else {
                type = StringHelper.removeQuotes(values.trim());
            }
            remainder = StringHelper.after(remainder, ")");
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

        remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null) {
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

        remainder = ifStartsWithReturnRemainder("uuid", function);
        if (remainder != null) {
            String values = StringHelper.between(remainder, "(", ")");
            return MiscExpressionBuilder.uuidExpression(values);
        } else if (ObjectHelper.equal(function, "uuid")) {
            return MiscExpressionBuilder.uuidExpression(null);
        }

        remainder = ifStartsWithReturnRemainder("hash(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = values.split(",", 2);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
                }
                return MiscExpressionBuilder.hashExpression(tokens[0].trim(), tokens[1].trim());
            } else {
                return MiscExpressionBuilder.hashExpression(values.trim(), "SHA-256");
            }
        }

        remainder = ifStartsWithReturnRemainder("empty(", function);
        if (remainder != null) {
            String value = StringHelper.before(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${empty(<type>)} but was: " + function, index);
            }
            return MiscExpressionBuilder.newEmptyExpression(value);
        }

        remainder = ifStartsWithReturnRemainder("newEmpty(", function);
        if (remainder != null) {
            String value = StringHelper.before(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${newEmpty(<type>)} but was: " + function, index);
            }
            return MiscExpressionBuilder.newEmptyExpression(value);
        }

        remainder = ifStartsWithReturnRemainder("iif(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
            }
            return MiscExpressionBuilder.iifExpression(tokens[0].trim(), tokens[1].trim(), tokens[2].trim());
        }

        remainder = ifStartsWithReturnRemainder("load(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${load(name)} but was: " + function, index);
            }
            return MiscExpressionBuilder.loadExpression(StringHelper.removeQuotes(value));
        }

        return null;
    }
}
