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
import org.apache.camel.language.simple.XmlJsonExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitArguments;

/**
 * Built-in Simple functions for formatted output: {@code ${pretty(exp)}}, {@code ${compact(exp)}},
 * {@code ${toJson(exp)}}, {@code ${toPrettyJson(exp)}}, {@code ${stripXmlDeclaration(exp)}},
 * {@code ${stripXmlComments(exp)}}, {@code ${stripXmlNamespaces(exp)}}, {@code ${canonicalXml(exp,mode)}}.
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

        remainder = ifStartsWithReturnRemainder("compact(", function);
        if (remainder != null) {
            return XmlJsonExpressionBuilder.compactExpression(optionalExpression(remainder));
        }

        remainder = ifStartsWithReturnRemainder("stripXmlDeclaration(", function);
        if (remainder != null) {
            return XmlJsonExpressionBuilder.stripXmlDeclarationExpression(optionalExpression(remainder));
        }

        remainder = ifStartsWithReturnRemainder("stripXmlComments(", function);
        if (remainder != null) {
            return XmlJsonExpressionBuilder.stripXmlCommentsExpression(optionalExpression(remainder));
        }

        remainder = ifStartsWithReturnRemainder("stripXmlNamespaces(", function);
        if (remainder != null) {
            return XmlJsonExpressionBuilder.stripXmlNamespacesExpression(optionalExpression(remainder));
        }

        remainder = ifStartsWithReturnRemainder("canonicalXml(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String exp = null;
            boolean exclusive = true;
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = splitArguments(values);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${canonicalXml(exp)} or ${canonicalXml(exp,mode)} was: " + function, index);
                }
                Boolean mode = canonicalMode(tokens[tokens.length - 1]);
                if (tokens.length == 2) {
                    if (mode == null) {
                        throw new SimpleParserException(
                                "Unknown canonicalXml mode: " + tokens[1] + " (valid modes: exclusive, inclusive) was: "
                                                        + function,
                                index);
                    }
                    exp = tokens[0];
                    exclusive = mode;
                } else if (mode != null) {
                    // ${canonicalXml(inclusive)}: the only argument is the mode
                    exclusive = mode;
                } else {
                    exp = tokens[0];
                }
            }
            return XmlJsonExpressionBuilder.canonicalXmlExpression(exp, exclusive);
        }

        return null;
    }

    /**
     * The expression argument of a function with a single optional argument, or {@code null} for the message body.
     */
    private static String optionalExpression(String remainder) {
        String value = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(value)) {
            return StringHelper.removeLeadingAndEndingQuotes(value);
        }
        return null;
    }

    /**
     * Whether the canonicalXml mode is exclusive ({@code true}) or inclusive ({@code false}), or {@code null} when the
     * argument is not a mode.
     */
    private static Boolean canonicalMode(String token) {
        if ("exclusive".equalsIgnoreCase(token)) {
            return Boolean.TRUE;
        } else if ("inclusive".equalsIgnoreCase(token)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
