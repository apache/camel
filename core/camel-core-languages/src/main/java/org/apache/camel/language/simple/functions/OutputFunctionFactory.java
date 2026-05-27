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
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple functions for formatted output: {@code ${pretty(exp)}}, {@code ${toJson(exp)}},
 * {@code ${toPrettyJson(exp)}}.
 */
public final class OutputFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("pretty(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${pretty(exp)} was: " + function, index);
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            Expression inlined = camelContext.resolveLanguage("simple").createExpression(exp);
            return ExpressionBuilder.prettyExpression(inlined);
        }

        remainder = ifStartsWithReturnRemainder("toPrettyJson(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${toPrettyJson(exp)} was: " + function, index);
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            Expression inlined = camelContext.resolveLanguage("simple").createExpression(exp);
            return ExpressionBuilder.toJsonExpression(inlined, true);
        }

        remainder = ifStartsWithReturnRemainder("toJson(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${toJson(exp)} was: " + function, index);
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            Expression inlined = camelContext.resolveLanguage("simple").createExpression(exp);
            return ExpressionBuilder.toJsonExpression(inlined, false);
        }

        return null;
    }
}
