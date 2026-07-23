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
import org.apache.camel.language.simple.OgnlExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.appendClass;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ognlCodeMethods;

/**
 * Built-in Simple function for exchange-related OGNL navigation: {@code ${camelContext.OGNL}},
 * {@code ${exception.OGNL}}, {@code ${exceptionAs(type).OGNL}}, {@code ${exchangeProperty.name}},
 * {@code ${exchangePropertyAs(key,type)}}, {@code ${exchangePropertyAsIndex(key,type,index)}},
 * {@code ${exchange.OGNL}}.
 */
public final class ExchangeFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        // camelContext OGNL
        String remainder = ifStartsWithReturnRemainder("camelContext", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${camelContext.OGNL} was: " + function, index);
            }
            return OgnlExpressionBuilder.camelContextOgnlExpression(remainder);
        }

        // Exception OGNL — exchangeProperty/exchange checked separately, no prefix clash here
        remainder = ifStartsWithReturnRemainder("exception", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exception.OGNL} was: " + function, index);
            }
            return OgnlExpressionBuilder.exchangeExceptionOgnlExpression(remainder);
        }

        // exchangeProperty must be checked before exchange to avoid prefix clash
        remainder = ifStartsWithReturnRemainder("exchangeProperty", function);
        if (remainder != null) {
            // remove leading character (dot, colon or ?)
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }

            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exchangeProperty.OGNL} was: " + function, index);
            }

            if (OgnlHelper.isValidOgnlExpression(remainder)) {
                return OgnlExpressionBuilder.propertyOgnlExpression(remainder);
            } else {
                return ExpressionBuilder.exchangePropertyExpression(remainder);
            }
        }

        // exchange OGNL
        remainder = ifStartsWithReturnRemainder("exchange", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exchange.OGNL} was: " + function, index);
            }
            return OgnlExpressionBuilder.exchangeOgnlExpression(remainder);
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        // exchange property variants first to avoid prefix clash with exchange OGNL
        String answer = createCodeExchangeProperty(function, index);
        if (answer != null) {
            return answer;
        }

        // camelContext OGNL
        String remainder = ifStartsWithReturnRemainder("camelContext", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${camelContext.OGNL} was: " + function, index);
            }
            return "context" + ognlCodeMethods(remainder, null);
        }

        // exceptionAs must be checked before exception to avoid prefix clash
        remainder = ifStartsWithReturnRemainder("exceptionAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            remainder = StringHelper.after(remainder, ")");
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (type.isEmpty() || invalid) {
                throw new SimpleParserException("Valid syntax: ${exceptionAs(type).OGNL} was: " + function, index);
            }
            return "exceptionAs(exchange, " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // Exception OGNL
        remainder = ifStartsWithReturnRemainder("exception", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exception.OGNL} was: " + function, index);
            }
            return "exception(exchange)" + ognlCodeMethods(remainder, null);
        }

        // exchange OGNL
        remainder = ifStartsWithReturnRemainder("exchange", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exchange.OGNL} was: " + function, index);
            }
            return "exchange" + ognlCodeMethods(remainder, null);
        }

        return null;
    }

    private static String createCodeExchangeProperty(String function, int index) {
        // exchangePropertyAsIndex must be checked before exchangePropertyAs and exchangeProperty
        String remainder = ifStartsWithReturnRemainder("exchangePropertyAsIndex(", function);
        if (remainder != null) {
            String keyTypeAndIndex = StringHelper.before(remainder, ")");
            if (keyTypeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAsIndex(key, type, index)} was: " + function, index);
            }
            String[] parts = keyTypeAndIndex.split(",");
            if (parts.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAsIndex(key, type, index)} was: " + function, index);
            }
            String key = parts[0];
            String type = parts[1];
            String idx = parts[2];
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(idx)) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAsIndex(key, type, index)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            idx = StringHelper.removeQuotes(idx);
            idx = idx.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${exchangePropertyAsIndex(key, type, index).OGNL} was: " + function, index);
                }
                return "exchangePropertyAsIndex(exchange, " + type + ", \"" + key + "\", \"" + idx + "\")"
                       + ognlCodeMethods(remainder, type);
            } else {
                return "exchangePropertyAsIndex(exchange, " + type + ", \"" + key + "\", \"" + idx + "\")";
            }
        }

        // exchangePropertyAs must be checked before exchangeProperty
        remainder = ifStartsWithReturnRemainder("exchangePropertyAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAs(key, type)} was: " + function, index);
            }
            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type)) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAs(key, type)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "exchangePropertyAs(exchange, \"" + key + "\", " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // plain exchangeProperty
        remainder = ifStartsWithReturnRemainder("exchangeProperty", function);
        if (remainder != null) {
            // remove leading character (dot or ?)
            if (remainder.startsWith(".") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);
            key = key.trim();

            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangeProperty.name[key]} was: " + function, index);
            }

            String idx = null;
            if (key.endsWith("]")) {
                idx = StringHelper.between(key, "[", "]");
                if (idx != null) {
                    key = StringHelper.before(key, "[");
                }
            }
            if (idx != null) {
                idx = StringHelper.removeLeadingAndEndingQuotes(idx);
                return "exchangePropertyAsIndex(exchange, Object.class, \"" + key + "\", \"" + idx + "\")";
            } else if (OgnlHelper.isValidOgnlExpression(remainder)) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAs(key, type)} was: " + function, index);
            } else {
                return "exchangeProperty(exchange, \"" + key + "\")";
            }
        }

        return null;
    }
}
