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
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple functions that delegate evaluation to another Camel language: {@code ${jq(exp)}},
 * {@code ${jsonpath(exp)}}, {@code ${xpath(exp)}}, {@code ${simpleJsonpath(exp)}}.
 * <p>
 * All four functions support an optional input-source qualifier so the language operates on a header, exchange
 * property, or variable rather than the body: e.g. {@code ${jq(header:myHeader,.name)}}.
 * <p>
 * CSimple code generation is not supported for these functions (returns {@code null}).
 */
public final class QueryLanguageFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("jq(", function);
        if (remainder != null) {
            return delegateLanguageExpression("jq", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("simpleJsonpath(", function);
        if (remainder != null) {
            return createSimpleJsonpathExpression(remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("jsonpath(", function);
        if (remainder != null) {
            return delegateLanguageExpression("jsonpath", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("xpath(", function);
        if (remainder != null) {
            return delegateLanguageExpression("xpath", remainder, function, index);
        }

        return null;
    }

    private static Expression delegateLanguageExpression(String name, String remainder, String function, int index) {
        String exp = StringHelper.beforeLast(remainder, ")");
        if (exp == null) {
            throw new SimpleParserException("Valid syntax: ${" + name + "(exp)} was: " + function, index);
        }
        exp = StringHelper.removeLeadingAndEndingQuotes(exp);
        if (hasInputSource(exp)) {
            String input = StringHelper.before(exp, ",");
            exp = StringHelper.after(exp, ",");
            if (input != null) {
                input = input.trim();
            }
            if (exp != null) {
                exp = exp.trim();
            }
            return ExpressionBuilder.singleInputLanguageExpression(name, exp, input);
        }
        return ExpressionBuilder.languageExpression(name, exp);
    }

    private static Expression createSimpleJsonpathExpression(String remainder, String function, int index) {
        String exp = StringHelper.beforeLast(remainder, ")");
        if (exp == null) {
            throw new SimpleParserException("Valid syntax: ${simpleJsonpath(exp)} was: " + function, index);
        }
        String input = null;
        exp = StringHelper.removeLeadingAndEndingQuotes(exp);
        if (hasInputSource(exp)) {
            input = StringHelper.before(exp, ",");
            exp = StringHelper.after(exp, ",");
            if (input != null) {
                input = input.trim();
            }
            if (exp != null) {
                exp = exp.trim();
            }
        }
        return MiscExpressionBuilder.simpleJsonPathExpression(input, exp);
    }

    private static boolean hasInputSource(String exp) {
        return exp.startsWith("header:") || exp.startsWith("property:")
                || exp.startsWith("exchangeProperty:") || exp.startsWith("variable:");
    }
}
