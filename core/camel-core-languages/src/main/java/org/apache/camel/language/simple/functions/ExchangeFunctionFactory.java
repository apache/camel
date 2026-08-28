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
import org.apache.camel.util.OgnlHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

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
}
