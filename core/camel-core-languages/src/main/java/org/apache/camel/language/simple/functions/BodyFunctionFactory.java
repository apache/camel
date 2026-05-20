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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.appendClass;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ognlCodeMethods;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitOgnl;

/**
 * Built-in Simple functions for the message body: {@code ${body}}, {@code ${bodyAs(type)}},
 * {@code ${bodyAs(type).OGNL}}, {@code ${mandatoryBodyAs(type)}}, {@code ${bodyType}}, etc.
 */
public final class BodyFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        if (ObjectHelper.isEqualToAny(function, "body", "in.body")) {
            return ExpressionBuilder.bodyExpression();
        } else if (ObjectHelper.equal(function, "bodyType")) {
            return ExpressionBuilder.bodyTypeExpression();
        } else if (ObjectHelper.equal(function, "prettyBody")) {
            return ExpressionBuilder.prettyBodyExpression();
        } else if (ObjectHelper.equal(function, "toJsonBody")) {
            return ExpressionBuilder.toJsonExpression(ExpressionBuilder.bodyExpression(), false);
        } else if (ObjectHelper.equal(function, "toPrettyJsonBody")) {
            return ExpressionBuilder.toJsonExpression(ExpressionBuilder.bodyExpression(), true);
        } else if (ObjectHelper.equal(function, "bodyOneLine")) {
            return ExpressionBuilder.bodyOneLine();
        } else if (ObjectHelper.equal(function, "originalBody")) {
            return ExpressionBuilder.originalBodyExpression();
        }

        // bodyAs
        String remainder = ifStartsWithReturnRemainder("bodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${bodyAs(type)} was: " + function, index);
            }
            type = StringHelper.removeQuotes(type);
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException("Valid syntax: ${bodyAs(type).OGNL} was: " + function, index);
                }
                return SimpleExpressionBuilder.bodyOgnlExpression(type, remainder);
            } else {
                return ExpressionBuilder.bodyExpression(type);
            }
        }

        // mandatoryBodyAs
        remainder = ifStartsWithReturnRemainder("mandatoryBodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${mandatoryBodyAs(type)} was: " + function, index);
            }
            type = StringHelper.removeQuotes(type);
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${mandatoryBodyAs(type).OGNL} was: " + function, index);
                }
                return SimpleExpressionBuilder.mandatoryBodyOgnlExpression(type, remainder);
            } else {
                return SimpleExpressionBuilder.mandatoryBodyExpression(type);
            }
        }

        // body OGNL (must come after exact matches)
        remainder = ifStartsWithReturnRemainder("body", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.body", function);
        }
        if (remainder != null) {
            boolean ognlStart = remainder.startsWith(".") || remainder.startsWith("?") || remainder.startsWith("[");
            boolean invalid = !ognlStart || OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${body.OGNL} was: " + function, index);
            }
            return SimpleExpressionBuilder.bodyOgnlExpression(remainder);
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        if (ObjectHelper.isEqualToAny(function, "body", "in.body")) {
            return "body";
        } else if (ObjectHelper.equal(function, "bodyType")) {
            return "bodyType(exchange)";
        } else if (ObjectHelper.equal(function, "prettyBody")) {
            return "prettyBody(exchange)";
        } else if (ObjectHelper.equal(function, "toJsonBody")) {
            return "toJsonBody(exchange, false)";
        } else if (ObjectHelper.equal(function, "toPrettyJsonBody")) {
            return "toJsonBody(exchange, true)";
        } else if (ObjectHelper.equal(function, "bodyOneLine")) {
            return "bodyOneLine(exchange)";
        }

        // bodyAsIndex
        String remainder = ifStartsWithReturnRemainder("bodyAsIndex(", function);
        if (remainder != null) {
            String typeAndIndex = StringHelper.before(remainder, ")");
            if (typeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${bodyAsIndex(type, index).OGNL} was: " + function, index);
            }
            String type = StringHelper.before(typeAndIndex, ",");
            String idx = StringHelper.after(typeAndIndex, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(idx)) {
                throw new SimpleParserException(
                        "Valid syntax: ${bodyAsIndex(type, index).OGNL} was: " + function, index);
            }
            type = type.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            idx = StringHelper.removeQuotes(idx);
            idx = idx.trim();
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${bodyAsIndex(type, index).OGNL} was: " + function, index);
                }
                return "bodyAsIndex(message, " + type + ", \"" + idx + "\")" + ognlCodeMethods(remainder, type);
            } else {
                return "bodyAsIndex(message, " + type + ", \"" + idx + "\")";
            }
        }

        // bodyAs
        remainder = ifStartsWithReturnRemainder("bodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${bodyAs(type)} was: " + function, index);
            }
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException("Valid syntax: ${bodyAs(type).OGNL} was: " + function, index);
                }
                if (remainder.startsWith("[")) {
                    List<String> parts = splitOgnl(remainder);
                    if (!parts.isEmpty()) {
                        String func = "bodyAsIndex(" + type + ", \"" + parts.remove(0) + "\")";
                        String last = String.join("", parts);
                        if (!last.isEmpty()) {
                            func += "." + last;
                        }
                        return createCode(camelContext, func, index);
                    }
                }
                return "bodyAs(message, " + type + ")" + ognlCodeMethods(remainder, type);
            } else {
                return "bodyAs(message, " + type + ")";
            }
        }

        // mandatoryBodyAsIndex
        remainder = ifStartsWithReturnRemainder("mandatoryBodyAsIndex(", function);
        if (remainder != null) {
            String typeAndIndex = StringHelper.before(remainder, ")");
            if (typeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${mandatoryBodyAsIndex(type, index).OGNL} was: " + function, index);
            }
            String type = StringHelper.before(typeAndIndex, ",");
            String idx = StringHelper.after(typeAndIndex, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(idx)) {
                throw new SimpleParserException(
                        "Valid syntax: ${mandatoryBodyAsIndex(type, index).OGNL} was: " + function, index);
            }
            type = type.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            idx = StringHelper.removeQuotes(idx);
            idx = idx.trim();
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${mandatoryBodyAsIndex(type, index).OGNL} was: " + function, index);
                }
                return "mandatoryBodyAsIndex(message, " + type + ", \"" + idx + "\")" + ognlCodeMethods(remainder, type);
            } else {
                return "mandatoryBodyAsIndex(message, " + type + ", \"" + idx + "\")";
            }
        }

        // mandatoryBodyAs
        remainder = ifStartsWithReturnRemainder("mandatoryBodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${mandatoryBodyAs(type)} was: " + function, index);
            }
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${mandatoryBodyAs(type).OGNL} was: " + function, index);
                }
                if (remainder.startsWith("[")) {
                    List<String> parts = splitOgnl(remainder);
                    if (!parts.isEmpty()) {
                        String func = "mandatoryBodyAsIndex(" + type + ", \"" + parts.remove(0) + "\")";
                        String last = String.join("", parts);
                        if (!last.isEmpty()) {
                            func += "." + last;
                        }
                        return createCode(camelContext, func, index);
                    }
                }
                return "mandatoryBodyAs(message, " + type + ")" + ognlCodeMethods(remainder, type);
            } else {
                return "mandatoryBodyAs(message, " + type + ")";
            }
        }

        // body OGNL (must come after exact matches)
        remainder = ifStartsWithReturnRemainder("body", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.body", function);
        }
        if (remainder != null) {
            boolean ognlStart = remainder.startsWith(".") || remainder.startsWith("?") || remainder.startsWith("[");
            boolean invalid = !ognlStart || OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${body.OGNL} was: " + function, index);
            }
            if (remainder.startsWith("[")) {
                List<String> parts = splitOgnl(remainder);
                if (!parts.isEmpty()) {
                    String func = "bodyAsIndex(Object.class, \"" + parts.remove(0) + "\")";
                    String last = String.join("", parts);
                    if (!last.isEmpty()) {
                        func += "." + last;
                    }
                    return createCode(camelContext, func, index);
                }
            }
            return "body" + ognlCodeMethods(remainder, null);
        }

        return null;
    }
}
