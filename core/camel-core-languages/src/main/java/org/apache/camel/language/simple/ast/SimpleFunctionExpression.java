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
package org.apache.camel.language.simple.ast;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.language.simple.BaseSimpleParser;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.language.simple.SimplePredicateParser;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;
import org.apache.camel.util.URISupport;

/**
 * Represents one of built-in functions of the <a href="http://camel.apache.org/simple.html">simple language</a>
 */
public class SimpleFunctionExpression extends LiteralExpression {

    // use caches to avoid re-parsing the same expressions over and over again
    private final Map<String, Expression> cacheExpression;
    private final boolean skipFileFunctions;

    public SimpleFunctionExpression(SimpleToken token, Map<String, Expression> cacheExpression, boolean skipFileFunctions) {
        super(token);
        this.cacheExpression = cacheExpression;
        this.skipFileFunctions = skipFileFunctions;
    }

    /**
     * Creates a Camel {@link Expression} based on this model.
     *
     * @param expression not in use
     */
    @Override
    public Expression createExpression(CamelContext camelContext, String expression) {
        String function = text.toString();

        Expression answer = cacheExpression != null ? cacheExpression.get(function) : null;
        if (answer == null) {
            answer = createSimpleExpression(camelContext, function, true);
            if (answer != null) {
                answer.init(camelContext);
            }
            if (cacheExpression != null && answer != null) {
                cacheExpression.put(function, answer);
            }
        }
        return answer;
    }

    private Expression createSimpleExpression(CamelContext camelContext, String function, boolean strict) {
        // return the function directly if we can create function without analyzing the prefix
        Expression answer = createSimpleExpressionDirectly(camelContext, function);
        if (answer != null) {
            return answer;
        }

        // message first
        answer = createSimpleExpressionMessage(camelContext, function, strict);
        if (answer != null) {
            return answer;
        }

        // body and headers first
        answer = createSimpleExpressionBodyOrHeader(function, strict);
        if (answer != null) {
            return answer;
        }
        // variables
        answer = createSimpleExpressionVariables(function, strict);
        if (answer != null) {
            return answer;
        }
        // custom languages
        answer = createSimpleCustomLanguage(function, strict);
        if (answer != null) {
            return answer;
        }

        // camelContext OGNL
        String remainder = ifStartsWithReturnRemainder("camelContext", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${camelContext.OGNL} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.camelContextOgnlExpression(remainder);
        }

        // Exception OGNL
        remainder = ifStartsWithReturnRemainder("exception", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exception.OGNL} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.exchangeExceptionOgnlExpression(remainder);
        }

        // exchange property
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

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exchangeProperty.OGNL} was: " + function, token.getIndex());
            }

            if (OgnlHelper.isValidOgnlExpression(remainder)) {
                // ognl based property
                return SimpleExpressionBuilder.propertyOgnlExpression(remainder);
            } else {
                // regular property
                return ExpressionBuilder.exchangePropertyExpression(remainder);
            }
        }

        // system property
        remainder = ifStartsWithReturnRemainder("sys.", function);
        if (remainder != null) {
            return ExpressionBuilder.systemPropertyExpression(remainder);
        }
        remainder = ifStartsWithReturnRemainder("sysenv.", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("sysenv:", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env.", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env:", function);
        }
        if (remainder != null) {
            return ExpressionBuilder.systemEnvironmentExpression(remainder);
        }

        // exchange OGNL
        remainder = ifStartsWithReturnRemainder("exchange", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exchange.OGNL} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.exchangeOgnlExpression(remainder);
        }

        // pretty
        remainder = ifStartsWithReturnRemainder("pretty(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${pretty(exp)} was: " + function, token.getIndex());
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            Expression inlined = camelContext.resolveLanguage("simple").createExpression(exp);
            return ExpressionBuilder.prettyExpression(inlined);
        }

        // file: prefix
        remainder = ifStartsWithReturnRemainder("file:", function);
        if (remainder != null) {
            Expression fileExpression;
            if (skipFileFunctions) {
                // do not create file expressions but keep the function as-is as a constant value
                fileExpression = ExpressionBuilder.constantExpression("${" + function + "}");
            } else {
                fileExpression = createSimpleFileExpression(remainder, strict);
            }
            if (fileExpression != null) {
                return fileExpression;
            }
        }

        // date: prefix
        remainder = ifStartsWithReturnRemainder("date:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length == 1) {
                return SimpleExpressionBuilder.dateExpression(parts[0]);
            } else if (parts.length == 2) {
                return SimpleExpressionBuilder.dateExpression(parts[0], parts[1]);
            }
        }

        // date-with-timezone: prefix
        remainder = ifStartsWithReturnRemainder("date-with-timezone:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 3);
            if (parts.length < 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${date-with-timezone:command:timezone:pattern} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.dateExpression(parts[0], parts[1], parts[2]);
        }

        // bean: prefix
        remainder = ifStartsWithReturnRemainder("bean:", function);
        if (remainder != null) {
            Language bean = camelContext.resolveLanguage("bean");
            String ref = remainder;
            Object method = null;
            Object scope = null;

            // we support different syntax for bean function
            if (remainder.contains("?method=") || remainder.contains("?scope=")) {
                ref = StringHelper.before(remainder, "?");
                String query = StringHelper.after(remainder, "?");
                try {
                    Map<String, Object> map = URISupport.parseQuery(query);
                    method = map.get("method");
                    scope = map.get("scope");
                } catch (URISyntaxException e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            } else {
                //first check case :: because of my.own.Bean::method
                int doubleColonIndex = remainder.indexOf("::");
                //need to check that not inside params
                int beginOfParameterDeclaration = remainder.indexOf('(');
                if (doubleColonIndex > 0 && (!remainder.contains("(") || doubleColonIndex < beginOfParameterDeclaration)) {
                    ref = remainder.substring(0, doubleColonIndex);
                    method = remainder.substring(doubleColonIndex + 2);
                } else {
                    int idx = remainder.indexOf('.');
                    if (idx > 0) {
                        ref = remainder.substring(0, idx);
                        method = remainder.substring(idx + 1);
                    }
                }
            }

            Class<?> type = null;
            if (ref != null && ref.startsWith("type:")) {
                try {
                    type = camelContext.getClassResolver().resolveMandatoryClass(ref.substring(5));
                    ref = null;
                } catch (ClassNotFoundException e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            }

            // there are parameters then map them into properties
            Object[] properties = new Object[7];
            properties[3] = type;
            properties[4] = ref;
            properties[2] = method;
            properties[5] = scope;
            return bean.createExpression(null, properties);
        }

        // properties-exist: prefix
        remainder = ifStartsWithReturnRemainder("propertiesExist:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length > 2) {
                throw new SimpleParserException("Valid syntax: ${propertiesExist:key} was: " + function, token.getIndex());
            }
            String key = parts[0];
            boolean negate = key != null && key.startsWith("!");
            if (negate) {
                key = key.substring(1);
            }
            return ExpressionBuilder.propertiesComponentExist(key, negate);
        }

        // properties: prefix
        remainder = ifStartsWithReturnRemainder("properties:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length > 2) {
                throw new SimpleParserException("Valid syntax: ${properties:key[:default]} was: " + function, token.getIndex());
            }
            String defaultValue = null;
            if (parts.length >= 2) {
                defaultValue = parts[1];
            }
            String key = parts[0];
            return ExpressionBuilder.propertiesComponentExpression(key, defaultValue);
        }

        // ref: prefix
        remainder = ifStartsWithReturnRemainder("ref:", function);
        if (remainder != null) {
            return ExpressionBuilder.refExpression(remainder);
        }

        // type: prefix
        remainder = ifStartsWithReturnRemainder("type:", function);
        if (remainder != null) {
            Expression exp = SimpleExpressionBuilder.typeExpression(remainder);
            exp.init(camelContext);
            // we want to cache this expression, so we won't re-evaluate it as the type/constant won't change
            return SimpleExpressionBuilder.cacheExpression(exp);
        }

        // miscellaneous functions
        Expression misc = createSimpleExpressionMisc(function);
        if (misc != null) {
            return misc;
        }
        // math functions
        Expression math = createSimpleExpressionMath(function);
        if (math != null) {
            return math;
        }

        // attachments
        if ("attachments".equals(function) || ifStartsWithReturnRemainder("attachment", function) != null) {
            Expression exp = createSimpleAttachments(camelContext, function);
            if (exp != null) {
                return exp;
            }
        }
        // base64
        if ("base64Encode".equals(function) || "base64Decode".equals(function)
                || ifStartsWithReturnRemainder("base64Encode", function) != null
                || ifStartsWithReturnRemainder("base64Decode", function) != null) {
            Expression exp = createSimpleBase64(camelContext, function);
            if (exp != null) {
                return exp;
            }
        }

        if (strict) {
            throw new SimpleParserException("Unknown function: " + function, token.getIndex());
        } else {
            return null;
        }
    }

    private Expression createSimpleAttachments(CamelContext camelContext, String function) {
        Optional<SimpleLanguageFunctionFactory> factory = ResolverHelper.resolveService(
                camelContext,
                camelContext.getCamelContextExtension().getBootstrapFactoryFinder(),
                SimpleLanguageFunctionFactory.FACTORY + "/camel-attachments",
                SimpleLanguageFunctionFactory.class);

        if (factory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot find SimpleLanguageFunctionFactory on classpath. Add camel-attachments to classpath.");
        }
        return factory.get().createFunction(camelContext, function, token.getIndex());
    }

    private Expression createSimpleBase64(CamelContext camelContext, String function) {
        Optional<SimpleLanguageFunctionFactory> factory = ResolverHelper.resolveService(
                camelContext,
                camelContext.getCamelContextExtension().getBootstrapFactoryFinder(),
                SimpleLanguageFunctionFactory.FACTORY + "/camel-base64",
                SimpleLanguageFunctionFactory.class);

        if (factory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot find SimpleLanguageFunctionFactory on classpath. Add camel-base64 to classpath.");
        }
        return factory.get().createFunction(camelContext, function, token.getIndex());
    }

    private Expression createSimpleExpressionMessage(CamelContext camelContext, String function, boolean strict) {
        // messageAs
        String remainder = ifStartsWithReturnRemainder("messageAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${messageAs(type)} was: " + function, token.getIndex());
            }
            type = StringHelper.removeQuotes(type);
            remainder = StringHelper.after(remainder, ")");

            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException("Valid syntax: ${messageAs(type).OGNL} was: " + function, token.getIndex());
                }
                return SimpleExpressionBuilder.messageOgnlExpression(type, remainder);
            } else {
                return ExpressionBuilder.messageExpression(type);
            }
        }

        return null;
    }

    private Expression createSimpleExpressionBodyOrHeader(String function, boolean strict) {
        // bodyAs
        String remainder = ifStartsWithReturnRemainder("bodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${bodyAs(type)} was: " + function, token.getIndex());
            }
            type = StringHelper.removeQuotes(type);
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException("Valid syntax: ${bodyAs(type).OGNL} was: " + function, token.getIndex());
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
                throw new SimpleParserException("Valid syntax: ${mandatoryBodyAs(type)} was: " + function, token.getIndex());
            }
            type = StringHelper.removeQuotes(type);
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${mandatoryBodyAs(type).OGNL} was: " + function, token.getIndex());
                }
                return SimpleExpressionBuilder.mandatoryBodyOgnlExpression(type, remainder);
            } else {
                return SimpleExpressionBuilder.mandatoryBodyExpression(type);
            }
        }

        // body OGNL
        remainder = ifStartsWithReturnRemainder("body", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.body", function);
        }
        if (remainder != null) {
            // OGNL must start with a ".", "?" or "[".
            boolean ognlStart = remainder.startsWith(".") || remainder.startsWith("?") || remainder.startsWith("[");
            boolean invalid = !ognlStart || OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${body.OGNL} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.bodyOgnlExpression(remainder);
        }

        // headerAs
        remainder = ifStartsWithReturnRemainder("headerAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, token.getIndex());
            }

            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isNotEmpty(remainder)) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            type = StringHelper.removeQuotes(type);
            return ExpressionBuilder.headerExpression(key, type);
        }

        // headers function
        if ("in.headers".equals(function) || "headers".equals(function)) {
            return ExpressionBuilder.headersExpression();
        } else if ("headers.size".equals(function) || "headers.size()".equals(function)
                || "headers.length".equals(function) || "headers.length()".equals(function)) {
            return ExpressionBuilder.headersSizeExpression();
        }

        // in header function
        remainder = parseInHeader(function);
        if (remainder != null) {
            // remove leading character (dot, colon or ?)
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            // remove quotes from key
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${header.name[key]} was: " + function, token.getIndex());
            }

            if (OgnlHelper.isValidOgnlExpression(key)) {
                // ognl based header
                return SimpleExpressionBuilder.headersOgnlExpression(key);
            } else {
                // regular header
                return ExpressionBuilder.headerExpression(key);
            }
        }

        return null;
    }

    private Expression createSimpleExpressionVariables(String function, boolean strict) {
        // variableAs
        String remainder = ifStartsWithReturnRemainder("variableAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, token.getIndex());
            }

            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isNotEmpty(remainder)) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            type = StringHelper.removeQuotes(type);
            return ExpressionBuilder.variableExpression(key, type);
        }

        // variables function
        if ("variables".equals(function)) {
            return ExpressionBuilder.variablesExpression();
        } else if ("variables.size".equals(function) || "variables.size()".equals(function)
                || "variables.length".equals(function) || "variables.length()".equals(function)) {
            return ExpressionBuilder.variablesSizeExpression();
        }

        // variable function
        remainder = parseVariable(function);
        if (remainder != null) {
            // remove leading character (dot, colon or ?)
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            // remove quotes from key
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${variable.name[key]} was: " + function, token.getIndex());
            }

            if (OgnlHelper.isValidOgnlExpression(key)) {
                // ognl based variable
                return SimpleExpressionBuilder.variablesOgnlExpression(key);
            } else {
                // regular variable
                return ExpressionBuilder.variableExpression(key);
            }
        }

        return null;
    }

    private Expression createSimpleCustomLanguage(String function, boolean strict) {
        // jq
        String remainder = ifStartsWithReturnRemainder("jq(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${jq(exp)} was: " + function, token.getIndex());
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            if (exp.startsWith("header:") || exp.startsWith("property:") || exp.startsWith("exchangeProperty:")
                    || exp.startsWith("variable:")) {
                String input = StringHelper.before(exp, ",");
                exp = StringHelper.after(exp, ",");
                if (input != null) {
                    input = input.trim();
                }
                if (exp != null) {
                    exp = exp.trim();
                }
                return ExpressionBuilder.singleInputLanguageExpression("jq", exp, input);
            }
            return ExpressionBuilder.languageExpression("jq", exp);
        }
        // jsonpath
        remainder = ifStartsWithReturnRemainder("jsonpath(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${jsonpath(exp)} was: " + function, token.getIndex());
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            if (exp.startsWith("header:") || exp.startsWith("property:") || exp.startsWith("exchangeProperty:")
                    || exp.startsWith("variable:")) {
                String input = StringHelper.before(exp, ",");
                exp = StringHelper.after(exp, ",");
                if (input != null) {
                    input = input.trim();
                }
                if (exp != null) {
                    exp = exp.trim();
                }
                return ExpressionBuilder.singleInputLanguageExpression("jsonpath", exp, input);
            }
            return ExpressionBuilder.languageExpression("jsonpath", exp);
        }
        remainder = ifStartsWithReturnRemainder("xpath(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException("Valid syntax: ${xpath(exp)} was: " + function, token.getIndex());
            }
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            if (exp.startsWith("header:") || exp.startsWith("property:") || exp.startsWith("exchangeProperty:")
                    || exp.startsWith("variable:")) {
                String input = StringHelper.before(exp, ",");
                exp = StringHelper.after(exp, ",");
                if (input != null) {
                    input = input.trim();
                }
                if (exp != null) {
                    exp = exp.trim();
                }
                return ExpressionBuilder.singleInputLanguageExpression("xpath", exp, input);
            }
            return ExpressionBuilder.languageExpression("xpath", exp);
        }

        return null;
    }

    private Expression createSimpleExpressionDirectly(CamelContext camelContext, String expression) {
        if (ObjectHelper.isEqualToAny(expression, "body", "in.body")) {
            return ExpressionBuilder.bodyExpression();
        } else if (ObjectHelper.equal(expression, "bodyType")) {
            return ExpressionBuilder.bodyTypeExpression();
        } else if (ObjectHelper.equal(expression, "prettyBody")) {
            return ExpressionBuilder.prettyBodyExpression();
        } else if (ObjectHelper.equal(expression, "bodyOneLine")) {
            return ExpressionBuilder.bodyOneLine();
        } else if (ObjectHelper.equal(expression, "originalBody")) {
            return ExpressionBuilder.originalBodyExpression();
        } else if (ObjectHelper.equal(expression, "id")) {
            return ExpressionBuilder.messageIdExpression();
        } else if (ObjectHelper.equal(expression, "messageTimestamp")) {
            return ExpressionBuilder.messageTimestampExpression();
        } else if (ObjectHelper.equal(expression, "exchangeId")) {
            return ExpressionBuilder.exchangeIdExpression();
        } else if (ObjectHelper.equal(expression, "exchange")) {
            return ExpressionBuilder.exchangeExpression();
        } else if (ObjectHelper.equal(expression, "logExchange")) {
            return ExpressionBuilder.logExchange();
        } else if (ObjectHelper.equal(expression, "exception")) {
            return ExpressionBuilder.exchangeExceptionExpression();
        } else if (ObjectHelper.equal(expression, "exception.message")) {
            return ExpressionBuilder.exchangeExceptionMessageExpression();
        } else if (ObjectHelper.equal(expression, "exception.stacktrace")) {
            return ExpressionBuilder.exchangeExceptionStackTraceExpression();
        } else if (ObjectHelper.equal(expression, "threadId")) {
            return ExpressionBuilder.threadIdExpression();
        } else if (ObjectHelper.equal(expression, "threadName")) {
            return ExpressionBuilder.threadNameExpression();
        } else if (ObjectHelper.equal(expression, "hostname")) {
            return ExpressionBuilder.hostnameExpression();
        } else if (ObjectHelper.equal(expression, "camelId")) {
            return ExpressionBuilder.camelContextNameExpression();
        } else if (ObjectHelper.equal(expression, "routeId")) {
            return ExpressionBuilder.routeIdExpression();
        } else if (ObjectHelper.equal(expression, "fromRouteId")) {
            return ExpressionBuilder.fromRouteIdExpression();
        } else if (ObjectHelper.equal(expression, "routeGroup")) {
            return ExpressionBuilder.routeGroupExpression();
        } else if (ObjectHelper.equal(expression, "stepId")) {
            return ExpressionBuilder.stepIdExpression();
        } else if (ObjectHelper.equal(expression, "null")) {
            return SimpleExpressionBuilder.nullExpression();
        }

        return null;
    }

    private Expression createSimpleFileExpression(String remainder, boolean strict) {
        if (ObjectHelper.equal(remainder, "name")) {
            return SimpleExpressionBuilder.fileNameExpression();
        } else if (ObjectHelper.equal(remainder, "name.noext")) {
            return SimpleExpressionBuilder.fileNameNoExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "name.noext.single")) {
            return SimpleExpressionBuilder.fileNameNoExtensionSingleExpression();
        } else if (ObjectHelper.equal(remainder, "name.ext") || ObjectHelper.equal(remainder, "ext")) {
            return SimpleExpressionBuilder.fileExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "name.ext.single")) {
            return SimpleExpressionBuilder.fileExtensionSingleExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname")) {
            return SimpleExpressionBuilder.fileOnlyNameExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname.noext")) {
            return SimpleExpressionBuilder.fileOnlyNameNoExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname.noext.single")) {
            return SimpleExpressionBuilder.fileOnlyNameNoExtensionSingleExpression();
        } else if (ObjectHelper.equal(remainder, "parent")) {
            return SimpleExpressionBuilder.fileParentExpression();
        } else if (ObjectHelper.equal(remainder, "path")) {
            return SimpleExpressionBuilder.filePathExpression();
        } else if (ObjectHelper.equal(remainder, "absolute")) {
            return SimpleExpressionBuilder.fileAbsoluteExpression();
        } else if (ObjectHelper.equal(remainder, "absolute.path")) {
            return SimpleExpressionBuilder.fileAbsolutePathExpression();
        } else if (ObjectHelper.equal(remainder, "length") || ObjectHelper.equal(remainder, "size")) {
            return SimpleExpressionBuilder.fileSizeExpression();
        } else if (ObjectHelper.equal(remainder, "modified")) {
            return SimpleExpressionBuilder.fileLastModifiedExpression();
        }
        if (strict) {
            throw new SimpleParserException("Unknown file language syntax: " + remainder, token.getIndex());
        }
        return null;
    }

    private Expression createSimpleExpressionMisc(String function) {
        String remainder;

        // setHeader function
        remainder = ifStartsWithReturnRemainder("setHeader(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function,
                        token.getIndex());
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
            return SimpleExpressionBuilder.setHeaderExpression(name, type, exp);
        }
        // setVariable function
        remainder = ifStartsWithReturnRemainder("setVariable(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function,
                        token.getIndex());
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
            return SimpleExpressionBuilder.setVariableExpression(name, type, exp);
        }

        // replace function
        remainder = ifStartsWithReturnRemainder("replace(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to)} or ${replace(from,to,expression)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to,expression)} was: " + function, token.getIndex());
            }
            String from = StringHelper.xmlDecode(tokens[0]);
            String to = StringHelper.xmlDecode(tokens[1]);
            // special to make it easy to replace to an empty value (ie remove)
            if ("&empty;".equals(to)) {
                to = "";
            }
            String exp = "${body}";
            if (tokens.length == 3) {
                exp = tokens[2];
            }
            return SimpleExpressionBuilder.replaceExpression(exp, from, to);
        }

        // substring function
        remainder = ifStartsWithReturnRemainder("substring(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num)}, ${substring(num,num)}, or ${substring(num,num,expression)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num,num,expression)} was: " + function, token.getIndex());
            }
            String num1 = tokens[0];
            String num2 = "0";
            if (tokens.length > 1) {
                num2 = tokens[1];
            }
            String exp = "${body}";
            if (tokens.length == 3) {
                exp = tokens[2];
            }
            return SimpleExpressionBuilder.substringExpression(exp, num1, num2);
        }
        remainder = ifStartsWithReturnRemainder("substringBefore(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(exp)} or ${substringBefore(exp,exp)} was: "
                                                + function,
                        token.getIndex());
            }
            String exp1 = "${body}";
            String before;
            if (tokens.length == 2) {
                exp1 = tokens[0];
                before = tokens[1];
            } else {
                before = tokens[0];
            }
            return SimpleExpressionBuilder.substringBeforeExpression(exp1, before);
        }
        remainder = ifStartsWithReturnRemainder("substringAfter(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(exp)} or ${substringAfter(exp,exp)} was: "
                                                + function,
                        token.getIndex());
            }
            String exp1 = "${body}";
            String after;
            if (tokens.length == 2) {
                exp1 = tokens[0];
                after = tokens[1];
            } else {
                after = tokens[0];
            }
            return SimpleExpressionBuilder.substringAfterExpression(exp1, after);
        }
        remainder = ifStartsWithReturnRemainder("substringBetween(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)} or ${substringAfter(exp,after,before)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)} or ${substringAfter(exp,after,before)} was: "
                                                + function,
                        token.getIndex());
            }
            String exp1 = "${body}";
            String after;
            String before;
            if (tokens.length == 3) {
                exp1 = tokens[0];
                after = tokens[1];
                before = tokens[2];
            } else {
                after = tokens[0];
                before = tokens[1];
            }
            return SimpleExpressionBuilder.substringBetweenExpression(exp1, after, before);
        }

        // random function
        remainder = ifStartsWithReturnRemainder("random(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${random(min,max)} or ${random(max)} was: " + function, token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = values.split(",", 3);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${random(min,max)} or ${random(max)} was: " + function, token.getIndex());
                }
                return SimpleExpressionBuilder.randomExpression(tokens[0].trim(), tokens[1].trim());
            } else {
                return SimpleExpressionBuilder.randomExpression("0", values.trim());
            }
        }

        // distinct function
        remainder = ifStartsWithReturnRemainder("distinct(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            } else {
                tokens = new String[] { "${body}" };
            }
            return SimpleExpressionBuilder.distinctExpression(tokens);
        }
        // reverse function
        remainder = ifStartsWithReturnRemainder("reverse(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            } else {
                tokens = new String[] { "${body}" };
            }
            return SimpleExpressionBuilder.reverseExpression(tokens);
        }
        // shuffle function
        remainder = ifStartsWithReturnRemainder("shuffle(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            } else {
                tokens = new String[] { "${body}" };
            }
            return SimpleExpressionBuilder.shuffleExpression(tokens);
        }
        // skip function
        remainder = ifStartsWithReturnRemainder("skip(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${skip(number)} was: " + function, token.getIndex());
            }
            String exp = "${body}";
            return SimpleExpressionBuilder.skipExpression(exp, values.trim());
        }

        // collate function
        remainder = ifStartsWithReturnRemainder("collate(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${collate(group)} was: " + function, token.getIndex());
            }
            String exp = "${body}";
            return SimpleExpressionBuilder.collateExpression(exp, values.trim());
        }

        // join function
        remainder = ifStartsWithReturnRemainder("join(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String separator = ",";
            String prefix = null;
            String exp = "${body}";
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
                if (tokens.length > 3) {
                    throw new SimpleParserException(
                            "Valid syntax: ${join(separator,prefix,expression)} was: " + function, token.getIndex());
                }
                if (tokens.length == 3) {
                    separator = tokens[0];
                    prefix = tokens[1];
                    exp = tokens[2];
                } else if (tokens.length == 2) {
                    separator = tokens[0];
                    prefix = tokens[1];
                } else {
                    separator = tokens[0];
                }
            }
            return SimpleExpressionBuilder.joinExpression(exp, separator, prefix);
        }
        // split function
        remainder = ifStartsWithReturnRemainder("split(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String exp = "${body}";
            String separator = ",";
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${split(separator)} or ${split(exp,separator)} was: " + function, token.getIndex());
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
            return SimpleExpressionBuilder.splitStringExpression(exp, separator);
        }
        // foreach function
        remainder = ifStartsWithReturnRemainder("forEach(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${forEach(exp,exp)} was: " + function, token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length < 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${forEach(exp,exp)} was: " + function, token.getIndex());
            }
            String exp1 = tokens[0];
            // the function takes the remainder of the tokens
            String exp2 = Arrays.stream(tokens).skip(1).collect(Collectors.joining(","));
            return SimpleExpressionBuilder.forEachExpression(exp1, exp2);
        }

        // isEmpty function
        remainder = ifStartsWithReturnRemainder("isEmpty(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.isEmptyExpression(exp);
        }

        // isAlpha function
        remainder = ifStartsWithReturnRemainder("isAlpha(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.isAlphaExpression(exp);
        }
        remainder = ifStartsWithReturnRemainder("isAlphaNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.isAlphaNumericExpression(exp);
        }
        // isNumeric function
        remainder = ifStartsWithReturnRemainder("isNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.isNumericExpression(exp);
        }
        // not function
        remainder = ifStartsWithReturnRemainder("not(", function);
        if (remainder != null) {
            String exp = "${body}";
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = value;
            }
            return SimpleExpressionBuilder.isNotPredicate(exp);
        }

        // trim function
        remainder = ifStartsWithReturnRemainder("trim(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.trimExpression(exp);
        }

        // capitalize
        remainder = ifStartsWithReturnRemainder("capitalize(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.capitalizeExpression(exp);
        }

        // pad function
        remainder = ifStartsWithReturnRemainder("pad(", function);
        if (remainder != null) {
            String exp;
            String len;
            String separator = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(len)} or ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function,
                        token.getIndex());
            }
            exp = StringHelper.removeQuotes(tokens[0]);
            len = StringHelper.removeQuotes(tokens[1]);
            if (tokens.length == 3) {
                separator = StringHelper.removeQuotes(tokens[2]);
            }
            return SimpleExpressionBuilder.padExpression(exp, len, separator);
        }

        // concat function
        remainder = ifStartsWithReturnRemainder("concat(", function);
        if (remainder != null) {
            String separator = null;
            String exp1 = "${body}";
            String exp2;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: " + function,
                        token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 3) {
                    throw new SimpleParserException(
                            "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                                    + function,
                            token.getIndex());
                }
                exp1 = StringHelper.removeQuotes(tokens[0]);
                exp2 = StringHelper.removeQuotes(tokens[1]);
                if (tokens.length == 3) {
                    separator = StringHelper.removeQuotes(tokens[2]);
                }
            } else {
                exp2 = StringHelper.removeQuotes(values.trim());
            }
            return SimpleExpressionBuilder.concatExpression(exp1, exp2, separator);
        }

        // throwException function
        remainder = ifStartsWithReturnRemainder("throwException(", function);
        if (remainder != null) {
            String msg;
            String type = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${throwException(msg)} or ${throwException(type,sg)} was: " + function,
                        token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${throwException(msg)} or ${throwException(type,msg)} was: " + function,
                            token.getIndex());
                }
                msg = StringHelper.removeQuotes(tokens[0]);
                type = StringHelper.removeQuotes(tokens[1]);
            } else {
                msg = StringHelper.removeQuotes(values.trim());
            }
            return SimpleExpressionBuilder.throwExceptionExpression(msg, type);
        }

        // convertTo function
        remainder = ifStartsWithReturnRemainder("convertTo(", function);
        if (remainder != null) {
            String exp = "${body}";
            String type;
            // do not use beforeLast as this supports OGNL
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function,
                        token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function,
                            token.getIndex());
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
                            "Valid syntax: ${convertTo(type).OGNL} or ${convertTo(exp,type).OGNL} was: " + function,
                            token.getIndex());
                }
                return SimpleExpressionBuilder.convertToOgnlExpression(exp, type, remainder);
            } else {
                return SimpleExpressionBuilder.convertToExpression(exp, type);
            }
        }

        // uppercase function
        remainder = ifStartsWithReturnRemainder("uppercase(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.uppercaseExpression(exp);
        }
        // lowercase function
        remainder = ifStartsWithReturnRemainder("lowercase(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.lowercaseExpression(exp);
        }

        // length function
        remainder = ifStartsWithReturnRemainder("length(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.lengthExpression(exp);
        }
        // size function
        remainder = ifStartsWithReturnRemainder("size(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.sizeExpression(exp);
        }
        // normalizeWhitespace function
        remainder = ifStartsWithReturnRemainder("normalizeWhitespace(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.normalizeWhitespaceExpression(exp);
        }

        // messageHistory function
        remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null) {
            boolean detailed;
            String values = StringHelper.between(remainder, "(", ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                detailed = true;
            } else {
                detailed = Boolean.parseBoolean(values);
            }
            return SimpleExpressionBuilder.messageHistoryExpression(detailed);
        } else if (ObjectHelper.equal(function, "messageHistory")) {
            return SimpleExpressionBuilder.messageHistoryExpression(true);
        }

        // uuid function
        remainder = ifStartsWithReturnRemainder("uuid", function);
        if (remainder != null) {
            String values = StringHelper.between(remainder, "(", ")");
            return SimpleExpressionBuilder.uuidExpression(values);
        } else if (ObjectHelper.equal(function, "uuid")) {
            return SimpleExpressionBuilder.uuidExpression(null);
        }

        // hash function
        remainder = ifStartsWithReturnRemainder("hash(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = values.split(",", 2);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, token.getIndex());
                }
                return SimpleExpressionBuilder.hashExpression(tokens[0].trim(), tokens[1].trim());
            } else {
                return SimpleExpressionBuilder.hashExpression(values.trim(), "SHA-256");
            }
        }

        // empty function
        remainder = ifStartsWithReturnRemainder("empty(", function);
        if (remainder != null) {
            String value = StringHelper.before(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException(
                        "Valid syntax: ${empty(<type>)} but was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.newEmptyExpression(value);
        }
        // newEmpty function
        remainder = ifStartsWithReturnRemainder("newEmpty(", function);
        if (remainder != null) {
            String value = StringHelper.before(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException(
                        "Valid syntax: ${newEmpty(<type>)} but was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.newEmptyExpression(value);
        }
        // iif function
        remainder = ifStartsWithReturnRemainder("iif(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.iifExpression(tokens[0].trim(), tokens[1].trim(), tokens[2].trim());
        }

        // list function
        remainder = ifStartsWithReturnRemainder("list(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            return SimpleExpressionBuilder.listExpression(tokens);
        }
        // map function
        remainder = ifStartsWithReturnRemainder("map(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            // there must be an even number of tokens as each map element is a pair
            if (tokens != null && tokens.length % 2 == 1) {
                throw new SimpleParserException(
                        "Map function must have an even number of values, was: " + tokens.length + " values.",
                        token.getIndex());
            }
            return SimpleExpressionBuilder.mapExpression(tokens);
        }

        return null;
    }

    private Expression createSimpleExpressionMath(String function) {
        String remainder;

        // abs function
        remainder = ifStartsWithReturnRemainder("abs(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.absExpression(exp);
        }
        // floor function
        remainder = ifStartsWithReturnRemainder("floor(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.floorExpression(exp);
        }
        // ceil function
        remainder = ifStartsWithReturnRemainder("ceil(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return SimpleExpressionBuilder.ceilExpression(exp);
        }
        // sum function
        remainder = ifStartsWithReturnRemainder("sum(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            return SimpleExpressionBuilder.sumExpression(tokens);
        }
        // max function
        remainder = ifStartsWithReturnRemainder("max(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            return SimpleExpressionBuilder.maxExpression(tokens);
        }
        // min function
        remainder = ifStartsWithReturnRemainder("min(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            return SimpleExpressionBuilder.minExpression(tokens);
        }
        // average function
        remainder = ifStartsWithReturnRemainder("average(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, false);
            }
            return SimpleExpressionBuilder.averageExpression(tokens);
        }

        return null;
    }

    public static String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (!remainder.isEmpty()) {
                return remainder;
            }
        }
        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String expression) throws SimpleParserException {
        return BaseSimpleParser.CODE_START + doCreateCode(camelContext, expression) + BaseSimpleParser.CODE_END;
    }

    private String doCreateCode(CamelContext camelContext, String expression) throws SimpleParserException {
        String function = getText();

        // return the function directly if we can create function without analyzing the prefix
        String answer = createCodeDirectly(function);
        if (answer != null) {
            return answer;
        }

        // body, headers and exchange property first
        answer = createCodeBody(function);
        if (answer != null) {
            return answer;
        }
        answer = createCodeHeader(function);
        if (answer != null) {
            return answer;
        }
        answer = createCodeExchangeProperty(function);
        if (answer != null) {
            return answer;
        }
        answer = createCodeVariables(function);
        if (answer != null) {
            return answer;
        }

        // camelContext OGNL
        String remainder = ifStartsWithReturnRemainder("camelContext", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${camelContext.OGNL} was: " + function, token.getIndex());
            }
            return "context" + ognlCodeMethods(remainder, null);
        }

        // ExceptionAs OGNL
        remainder = ifStartsWithReturnRemainder("exceptionAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            remainder = StringHelper.after(remainder, ")");
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (type.isEmpty() || invalid) {
                throw new SimpleParserException("Valid syntax: ${exceptionAs(type).OGNL} was: " + function, token.getIndex());
            }
            return "exceptionAs(exchange, " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // Exception OGNL
        remainder = ifStartsWithReturnRemainder("exception", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exceptionAs(type).OGNL} was: " + function, token.getIndex());
            }
            return "exception(exchange)" + ognlCodeMethods(remainder, null);
        }

        // system property
        remainder = ifStartsWithReturnRemainder("sys.", function);
        if (remainder != null) {
            return "sys(\"" + remainder + "\")";
        }
        remainder = ifStartsWithReturnRemainder("sysenv.", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("sysenv:", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env.", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env:", function);
        }
        if (remainder != null) {
            return "sysenv(\"" + remainder + "\")";
        }

        // exchange OGNL
        remainder = ifStartsWithReturnRemainder("exchange", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exchange.OGNL} was: " + function, token.getIndex());
            }
            return "exchange" + ognlCodeMethods(remainder, null);
        }

        // file: prefix
        remainder = ifStartsWithReturnRemainder("file:", function);
        if (remainder != null) {
            return createCodeFileExpression(remainder);
        }

        if ("date:millis".equals(function)) {
            return "System.currentTimeMillis()";
        }
        // date: prefix
        remainder = ifStartsWithReturnRemainder("date:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length == 1) {
                return "date(exchange, \"" + parts[0] + "\")";
            } else if (parts.length == 2) {
                return "date(exchange, \"" + parts[0] + "\", null, \"" + parts[1] + "\")";
            }
        }

        // date-with-timezone: prefix
        remainder = ifStartsWithReturnRemainder("date-with-timezone:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 3);
            if (parts.length < 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${date-with-timezone:command:timezone:pattern} was: " + function, token.getIndex());
            }
            return "date(exchange, \"" + parts[0] + "\", \"" + parts[1] + "\", \"" + parts[2] + "\")";
        }

        // bean: prefix
        remainder = ifStartsWithReturnRemainder("bean:", function);
        if (remainder != null) {
            String ref = remainder;
            Object method = null;
            Object scope = null;

            // we support different syntax for bean function
            if (remainder.contains("?method=") || remainder.contains("?scope=")) {
                ref = StringHelper.before(remainder, "?");
                String query = StringHelper.after(remainder, "?");
                try {
                    Map<String, Object> map = URISupport.parseQuery(query);
                    method = map.get("method");
                    scope = map.get("scope");
                } catch (URISyntaxException e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            } else {
                //first check case :: because of my.own.Bean::method
                int doubleColonIndex = remainder.indexOf("::");
                //need to check that not inside params
                int beginOfParameterDeclaration = remainder.indexOf('(');
                if (doubleColonIndex > 0 && (!remainder.contains("(") || doubleColonIndex < beginOfParameterDeclaration)) {
                    ref = remainder.substring(0, doubleColonIndex);
                    method = remainder.substring(doubleColonIndex + 2);
                } else {
                    int idx = remainder.indexOf('.');
                    if (idx > 0) {
                        ref = remainder.substring(0, idx);
                        method = remainder.substring(idx + 1);
                    }
                }
            }
            ref = ref.trim();
            if (method != null && scope != null) {
                return "bean(exchange, bean, \"" + ref + "\", \"" + method + "\", \"" + scope + "\")";
            } else if (method != null) {
                return "bean(exchange, bean, \"" + ref + "\", \"" + method + "\", null)";
            } else {
                return "bean(exchange, bean, \"" + ref + "\", null, null)";
            }
        }

        // properties: prefix
        remainder = ifStartsWithReturnRemainder("properties:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length > 2) {
                throw new SimpleParserException("Valid syntax: ${properties:key[:default]} was: " + function, token.getIndex());
            }
            String defaultValue = null;
            if (parts.length >= 2) {
                defaultValue = parts[1];
            }
            String key = parts[0];
            key = key.trim();
            if (defaultValue != null) {
                return "properties(exchange, \"" + key + "\", \"" + defaultValue.trim() + "\")";
            } else {
                return "properties(exchange, \"" + key + "\")";
            }
        }

        // ref: prefix
        remainder = ifStartsWithReturnRemainder("ref:", function);
        if (remainder != null) {
            return "ref(exchange, \"" + remainder + "\")";
        }

        // type: prefix
        remainder = ifStartsWithReturnRemainder("type:", function);
        if (remainder != null) {
            int pos = remainder.lastIndexOf('.');
            String type = pos != -1 ? remainder.substring(0, pos) : remainder;
            String field = pos != -1 ? remainder.substring(pos + 1) : null;
            if (!type.endsWith(".class")) {
                type += ".class";
            }
            type = type.replace('$', '.');
            if (field != null) {
                return "type(exchange, " + type + ", \"" + field + "\")";
            } else {
                return "type(exchange, " + type + ")";
            }
        }

        // miscellaneous functions
        String misc = createCodeExpressionMisc(camelContext, function);
        if (misc != null) {
            return misc;
        }
        // math functions
        String math = createCodeExpressionMath(function);
        if (math != null) {
            return math;
        }

        // attachments
        if ("attachments".equals(function) || ifStartsWithReturnRemainder("attachment", function) != null) {
            String code = createCodeAttachments(camelContext, function);
            if (code != null) {
                return code;
            }
        }
        // base64
        if ("base64Encode".equals(function) || "base64Decode".equals(function)
                || ifStartsWithReturnRemainder("base64Encode", function) != null
                || ifStartsWithReturnRemainder("base64Decode", function) != null) {
            String code = createCodeBase64(camelContext, function);
            if (code != null) {
                return code;
            }
        }

        throw new SimpleParserException("Unknown function: " + function, token.getIndex());
    }

    public String createCodeDirectly(String expression) throws SimpleParserException {
        if (ObjectHelper.isEqualToAny(expression, "body", "in.body")) {
            return "body";
        } else if (ObjectHelper.equal(expression, "bodyType")) {
            return "bodyType(exchange)";
        } else if (ObjectHelper.equal(expression, "prettyBody")) {
            return "prettyBody(exchange)";
        } else if (ObjectHelper.equal(expression, "bodyOneLine")) {
            return "bodyOneLine(exchange)";
        } else if (ObjectHelper.equal(expression, "id")) {
            return "message.getMessageId()";
        } else if (ObjectHelper.equal(expression, "messageTimestamp")) {
            return "message.getMessageTimestamp()";
        } else if (ObjectHelper.equal(expression, "exchangeId")) {
            return "exchange.getExchangeId()";
        } else if (ObjectHelper.equal(expression, "exchange")) {
            return "exchange";
        } else if (ObjectHelper.equal(expression, "logExchange")) {
            return "logExchange(exchange)";
        } else if (ObjectHelper.equal(expression, "exception")) {
            return "exception(exchange)";
        } else if (ObjectHelper.equal(expression, "exception.message")) {
            return "exceptionMessage(exchange)";
        } else if (ObjectHelper.equal(expression, "exception.stacktrace")) {
            return "exceptionStacktrace(exchange)";
        } else if (ObjectHelper.equal(expression, "threadId")) {
            return "threadId()";
        } else if (ObjectHelper.equal(expression, "threadName")) {
            return "threadName()";
        } else if (ObjectHelper.equal(expression, "hostname")) {
            return "hostName()";
        } else if (ObjectHelper.equal(expression, "camelId")) {
            return "context.getName()";
        } else if (ObjectHelper.equal(expression, "fromRouteId")) {
            return "fromRouteId(exchange)";
        } else if (ObjectHelper.equal(expression, "routeId")) {
            return "routeId(exchange)";
        } else if (ObjectHelper.equal(expression, "stepId")) {
            return "stepId(exchange)";
        } else if (ObjectHelper.equal(expression, "null")) {
            return "null";
        }

        return null;
    }

    private String createCodeBody(final String function) {
        // bodyAsIndex
        String remainder = ifStartsWithReturnRemainder("bodyAsIndex(", function);
        if (remainder != null) {
            String typeAndIndex = StringHelper.before(remainder, ")");
            if (typeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${bodyAsIndex(type, index).OGNL} was: " + function, token.getIndex());
            }

            String type = StringHelper.before(typeAndIndex, ",");
            String index = StringHelper.after(typeAndIndex, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(index)) {
                throw new SimpleParserException(
                        "Valid syntax: ${bodyAsIndex(type, index).OGNL} was: " + function, token.getIndex());
            }
            type = type.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            index = StringHelper.removeQuotes(index);
            index = index.trim();
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${bodyAsIndex(type, index).OGNL} was: " + function, token.getIndex());
                }
                return "bodyAsIndex(message, " + type + ", \"" + index + "\")" + ognlCodeMethods(remainder, type);
            } else {
                return "bodyAsIndex(message, " + type + ", \"" + index + "\")";
            }
        }

        // bodyAs
        remainder = ifStartsWithReturnRemainder("bodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${bodyAs(type)} was: " + function, token.getIndex());
            }
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException("Valid syntax: ${bodyAs(type).OGNL} was: " + function, token.getIndex());
                }
                if (remainder.startsWith("[")) {
                    // is there any index, then we should use bodyAsIndex function instead
                    // (use splitOgnl which assembles multiple indexes into a single part)
                    List<String> parts = splitOgnl(remainder);
                    if (!parts.isEmpty()) {
                        String func = "bodyAsIndex(" + type + ", \"" + parts.remove(0) + "\")";
                        String last = String.join("", parts);
                        if (!last.isEmpty()) {
                            func += "." + last;
                        }
                        return createCodeBody(func);
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
                        "Valid syntax: ${mandatoryBodyAsIndex(type, index).OGNL} was: " + function, token.getIndex());
            }

            String type = StringHelper.before(typeAndIndex, ",");
            String index = StringHelper.after(typeAndIndex, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(index)) {
                throw new SimpleParserException(
                        "Valid syntax: ${mandatoryBodyAsIndex(type, index).OGNL} was: " + function, token.getIndex());
            }
            type = type.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            index = StringHelper.removeQuotes(index);
            index = index.trim();
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${mandatoryBodyAsIndex(type, index).OGNL} was: " + function, token.getIndex());
                }
                return "mandatoryBodyAsIndex(message, " + type + ", \"" + index + "\")" + ognlCodeMethods(remainder, type);
            } else {
                return "mandatoryBodyAsIndex(message, " + type + ", \"" + index + "\")";
            }
        }

        // mandatoryBodyAs
        remainder = ifStartsWithReturnRemainder("mandatoryBodyAs(", function);
        if (remainder != null) {
            String type = StringHelper.before(remainder, ")");
            if (type == null) {
                throw new SimpleParserException("Valid syntax: ${mandatoryBodyAs(type)} was: " + function, token.getIndex());
            }
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${mandatoryBodyAs(type).OGNL} was: " + function, token.getIndex());
                }
                if (remainder.startsWith("[")) {
                    // is there any index, then we should use mandatoryBodyAsIndex function instead
                    // (use splitOgnl which assembles multiple indexes into a single part)
                    List<String> parts = splitOgnl(remainder);
                    if (!parts.isEmpty()) {
                        String func = "mandatoryBodyAsIndex(" + type + ", \"" + parts.remove(0) + "\")";
                        String last = String.join("", parts);
                        if (!last.isEmpty()) {
                            func += "." + last;
                        }
                        return createCodeBody(func);
                    }
                }
                return "mandatoryBodyAs(message, " + type + ")" + ognlCodeMethods(remainder, type);
            } else {
                return "mandatoryBodyAs(message, " + type + ")";
            }
        }

        // body OGNL
        remainder = ifStartsWithReturnRemainder("body", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.body", function);
        }
        if (remainder != null) {
            // OGNL must start with a . ? or [
            boolean ognlStart = remainder.startsWith(".") || remainder.startsWith("?") || remainder.startsWith("[");
            boolean invalid = !ognlStart || OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${body.OGNL} was: " + function, token.getIndex());
            }
            if (remainder.startsWith("[")) {
                // is there any index, then we should use bodyAsIndex function instead
                // (use splitOgnl which assembles multiple indexes into a single part)
                List<String> parts = splitOgnl(remainder);
                if (!parts.isEmpty()) {
                    String func = "bodyAsIndex(Object.class, \"" + parts.remove(0) + "\")";
                    String last = String.join("", parts);
                    if (!last.isEmpty()) {
                        func += "." + last;
                    }
                    return createCodeBody(func);
                }
            }
            return "body" + ognlCodeMethods(remainder, null);
        }

        return null;
    }

    private String createCodeHeader(final String function) {
        // headerAsIndex
        String remainder = ifStartsWithReturnRemainder("headerAsIndex(", function);
        if (remainder != null) {
            String keyTypeAndIndex = StringHelper.before(remainder, ")");
            if (keyTypeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${headerAsIndex(key, type, index)} was: " + function, token.getIndex());
            }
            String[] parts = keyTypeAndIndex.split(",");
            if (parts.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${headerAsIndex(key, type, index)} was: " + function, token.getIndex());
            }
            String key = parts[0];
            String type = parts[1];
            String index = parts[2];
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(index)) {
                throw new SimpleParserException(
                        "Valid syntax: ${headerAsIndex(key, type, index)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            index = StringHelper.removeQuotes(index);
            index = index.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${headerAsIndex(key, type, index).OGNL} was: " + function, token.getIndex());
                }
                return "headerAsIndex(message, " + type + ", \"" + key + "\", \"" + index + "\")"
                       + ognlCodeMethods(remainder, type);
            } else {
                return "headerAsIndex(message, " + type + ", \"" + key + "\", \"" + index + "\")";
            }
        }

        // headerAs
        remainder = ifStartsWithReturnRemainder("headerAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, token.getIndex());
            }

            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type)) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "headerAs(message, \"" + key + "\", " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // headers function
        if ("in.headers".equals(function) || "headers".equals(function)) {
            return "message.getHeaders()";
        } else if ("headers.size".equals(function) || "headers.size()".equals(function)
                || "headers.length".equals(function) || "headers.length()".equals(function)) {
            return "message.getHeaders().size()";
        }

        // in header function
        remainder = parseInHeader(function);
        if (remainder != null) {
            // remove leading character (dot, colon or ?)
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            // remove quotes from key
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);
            key = key.trim();

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${header.name[key]} was: " + function, token.getIndex());
            }

            // the key can contain index as it may be a map header.foo[0]
            // and the key can also be OGNL (e.g., if there is a dot)
            boolean index = false;
            List<String> parts = splitOgnl(key);
            if (!parts.isEmpty()) {
                String s = parts.get(0);
                int pos = s.indexOf('[');
                if (pos != -1) {
                    index = true;
                    // split key into name and index
                    String before = s.substring(0, pos);
                    String after = s.substring(pos);
                    parts.set(0, before);
                    parts.add(1, after);
                }
            }
            if (index) {
                // is there any index, then we should use headerAsIndex function instead
                // (use splitOgnl which assembles multiple indexes into a single part?)
                String func = "headerAsIndex(\"" + parts.get(0) + "\", Object.class, \"" + parts.get(1) + "\")";
                if (parts.size() > 2) {
                    String last = String.join("", parts.subList(2, parts.size()));
                    if (!last.isEmpty()) {
                        func += "." + last;
                    }
                }
                return createCodeHeader(func);
            } else if (OgnlHelper.isValidOgnlExpression(key)) {
                // ognl based header must be typed
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type).OGNL} was: " + function, token.getIndex());
            } else {
                // regular header
                return "header(message, \"" + key + "\")";
            }
        }

        return null;
    }

    private String parseInHeader(String function) {
        String remainder;
        remainder = ifStartsWithReturnRemainder("in.headers", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.header", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("headers", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("header", function);
        }
        return remainder;
    }

    private String parseVariable(String function) {
        String remainder;
        remainder = ifStartsWithReturnRemainder("variables", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("variable", function);
        }
        return remainder;
    }

    private String createCodeVariables(final String function) {
        // variableAs
        String remainder = ifStartsWithReturnRemainder("variableAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, token.getIndex());
            }
            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type)) {
                throw new SimpleParserException(
                        "Valid syntax: ${variableAs(key, type)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "variableAs(exchange, \"" + key + "\", " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // variables function
        if ("variables".equals(function)) {
            return "variables(exchange)";
        } else if ("variables.size".equals(function) || "variables.size()".equals(function)
                || "variables.length".equals(function) || "variables.length()".equals(function)) {
            return "variablesSize(exchange)";
        }

        // variable
        remainder = ifStartsWithReturnRemainder("variable", function);
        if (remainder != null) {
            // remove leading character (dot or ?)
            if (remainder.startsWith(".") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            // remove quotes from key
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);
            key = key.trim();

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException(
                        "Valid syntax: ${variable.name[key]} was: " + function, token.getIndex());
            }

            // it is an index?
            String index = null;
            if (key.endsWith("]")) {
                index = StringHelper.between(key, "[", "]");
                if (index != null) {
                    key = StringHelper.before(key, "[");
                }
            }
            if (index != null) {
                index = StringHelper.removeLeadingAndEndingQuotes(index);
                return "variableAsIndex(exchange, Object.class, \"" + key + "\", \"" + index + "\")";
            } else if (OgnlHelper.isValidOgnlExpression(remainder)) {
                // ognl based exchange property must be typed
                throw new SimpleParserException(
                        "Valid syntax: ${variableAs(key, type)} was: " + function, token.getIndex());
            } else {
                // regular property
                return "variable(exchange, \"" + key + "\")";
            }
        }

        return null;
    }

    private String createCodeExchangeProperty(final String function) {
        // exchangePropertyAsIndex
        String remainder = ifStartsWithReturnRemainder("exchangePropertyAsIndex(", function);
        if (remainder != null) {
            String keyTypeAndIndex = StringHelper.before(remainder, ")");
            if (keyTypeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAsIndex(key, type, index)} was: " + function, token.getIndex());
            }
            String[] parts = keyTypeAndIndex.split(",");
            if (parts.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAsIndex(key, type, index)} was: " + function, token.getIndex());
            }
            String key = parts[0];
            String type = parts[1];
            String index = parts[2];
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(index)) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAsIndex(key, type, index)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            index = StringHelper.removeQuotes(index);
            index = index.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${exchangePropertyAsIndex(key, type, index).OGNL} was: " + function,
                            token.getIndex());
                }
                return "exchangePropertyAsIndex(exchange, " + type + ", \"" + key + "\", \"" + index + "\")"
                       + ognlCodeMethods(remainder, type);
            } else {
                return "exchangePropertyAsIndex(exchange, " + type + ", \"" + key + "\", \"" + index + "\")";
            }
        }

        // exchangePropertyAs
        remainder = ifStartsWithReturnRemainder("exchangePropertyAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAs(key, type)} was: " + function, token.getIndex());
            }

            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type)) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAs(key, type)} was: " + function, token.getIndex());
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "exchangePropertyAs(exchange, \"" + key + "\", " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // exchange property
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
            // remove quotes from key
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);
            key = key.trim();

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException(
                        "Valid syntax: ${exchangeProperty.name[key]} was: " + function, token.getIndex());
            }

            // it is an index?
            String index = null;
            if (key.endsWith("]")) {
                index = StringHelper.between(key, "[", "]");
                if (index != null) {
                    key = StringHelper.before(key, "[");
                }
            }
            if (index != null) {
                index = StringHelper.removeLeadingAndEndingQuotes(index);
                return "exchangePropertyAsIndex(exchange, Object.class, \"" + key + "\", \"" + index + "\")";
            } else if (OgnlHelper.isValidOgnlExpression(remainder)) {
                // ognl based exchange property must be typed
                throw new SimpleParserException(
                        "Valid syntax: ${exchangePropertyAs(key, type)} was: " + function, token.getIndex());
            } else {
                // regular property
                return "exchangeProperty(exchange, \"" + key + "\")";
            }
        }

        return null;
    }

    private static String appendClass(String type) {
        type = StringHelper.removeQuotes(type);
        if (!type.endsWith(".class")) {
            type = type + ".class";
        }
        return type;
    }

    private String createCodeFileExpression(String remainder) {
        if (ObjectHelper.equal(remainder, "name")) {
            return "fileName(message)";
        } else if (ObjectHelper.equal(remainder, "name.noext")) {
            return "fileNameNoExt(message)";
        } else if (ObjectHelper.equal(remainder, "name.noext.single")) {
            return "fileNameNoExtSingle(message)";
        } else if (ObjectHelper.equal(remainder, "name.ext") || ObjectHelper.equal(remainder, "ext")) {
            return "fileNameExt(message)";
        } else if (ObjectHelper.equal(remainder, "name.ext.single")) {
            return "fileNameExtSingle(message)";
        } else if (ObjectHelper.equal(remainder, "onlyname")) {
            return "fileOnlyName(message)";
        } else if (ObjectHelper.equal(remainder, "onlyname.noext")) {
            return "fileOnlyNameNoExt(message)";
        } else if (ObjectHelper.equal(remainder, "onlyname.noext.single")) {
            return "fileOnlyNameNoExtSingle(message)";
        } else if (ObjectHelper.equal(remainder, "parent")) {
            return "fileParent(message)";
        } else if (ObjectHelper.equal(remainder, "path")) {
            return "filePath(message)";
        } else if (ObjectHelper.equal(remainder, "absolute")) {
            return "fileAbsolute(message)";
        } else if (ObjectHelper.equal(remainder, "absolute.path")) {
            return "fileAbsolutePath(message)";
        } else if (ObjectHelper.equal(remainder, "length") || ObjectHelper.equal(remainder, "size")) {
            return "fileSize(message)";
        } else if (ObjectHelper.equal(remainder, "modified")) {
            return "fileModified(message)";
        }
        throw new SimpleParserException("Unknown file language syntax: " + remainder, token.getIndex());
    }

    private String createCodeAttachments(CamelContext camelContext, String function) {
        Optional<SimpleLanguageFunctionFactory> factory = ResolverHelper.resolveService(
                camelContext,
                camelContext.getCamelContextExtension().getBootstrapFactoryFinder(),
                SimpleLanguageFunctionFactory.FACTORY + "/camel-attachments",
                SimpleLanguageFunctionFactory.class);

        if (factory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot find SimpleLanguageFunctionFactory on classpath. Add camel-attachments to classpath.");
        }
        return factory.get().createCode(camelContext, function, token.getIndex());
    }

    private String createCodeBase64(CamelContext camelContext, String function) {
        Optional<SimpleLanguageFunctionFactory> factory = ResolverHelper.resolveService(
                camelContext,
                camelContext.getCamelContextExtension().getBootstrapFactoryFinder(),
                SimpleLanguageFunctionFactory.FACTORY + "/camel-base64",
                SimpleLanguageFunctionFactory.class);

        if (factory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot find SimpleLanguageFunctionFactory on classpath. Add camel-base64 to classpath.");
        }
        return factory.get().createCode(camelContext, function, token.getIndex());
    }

    private String createCodeExpressionMisc(CamelContext camelContext, String function) {
        String remainder;

        // setHeader function
        remainder = ifStartsWithReturnRemainder("setHeader(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${setHeader(name,exp)} or ${setHeader(name,type,exp)} was: " + function,
                        token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 1; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            // name must be in double quote as its a string value
            String name = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
            name = StringQuoteHelper.doubleQuote(name);
            String exp;
            String type = null;
            if (tokens.length == 3) {
                type = tokens[1];
                exp = tokens[2];
            } else {
                exp = tokens[1];
            }
            if (type != null) {
                type = StringHelper.removeQuotes(type);
                type = type.trim();
                type = appendClass(type);
                type = type.replace('$', '.');
            } else {
                type = "null";
            }
            return "Object value = " + exp + ";\n        return setHeader(exchange, " + name + ", " + type + ", value);";
        }
        // setVariable function
        remainder = ifStartsWithReturnRemainder("setVariable(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${setVariable(name,exp)} or ${setVariable(name,type,exp)} was: " + function,
                        token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 1; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            // name must be in double quote as its a string value
            String name = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
            name = StringQuoteHelper.doubleQuote(name);
            String exp;
            String type = null;
            if (tokens.length == 3) {
                type = tokens[1];
                exp = tokens[2];
            } else {
                exp = tokens[1];
            }
            if (type != null) {
                type = StringHelper.removeQuotes(type);
                type = type.trim();
                type = appendClass(type);
                type = type.replace('$', '.');
            } else {
                type = "null";
            }
            return "Object value = " + exp + ";\n        return setVariable(exchange, " + name + ", " + type + ", value);";
        }

        // substring function
        remainder = ifStartsWithReturnRemainder("substring(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num)}, ${substring(num,num)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substring(num,num)} was: " + function, token.getIndex());
            }
            String num1 = tokens[0];
            String num2 = "0";
            if (tokens.length > 1) {
                num2 = tokens[1];
            }
            num1 = num1.trim();
            num2 = num2.trim();
            return "substring(exchange, " + num1 + ", " + num2 + ")";
        }
        remainder = ifStartsWithReturnRemainder("substringBefore(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(before)}, ${substringBefore(exp,before)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBefore(before)}, ${substringBefore(exp,before)} was: "
                                                + function,
                        token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 0; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            String body = "body";
            String before;
            if (tokens.length > 1) {
                body = tokens[0];
                before = tokens[1];
            } else {
                before = tokens[0];
            }
            return "Object value = " + body + ";\n        Object before = " + before
                   + ";\n        return substringBefore(exchange, value, before);";
        }
        remainder = ifStartsWithReturnRemainder("substringAfter(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(before)}, ${substringAfter(exp,before)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringAfter(before)}, ${substringAfter(exp,before)} was: "
                                                + function,
                        token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 0; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            String body = "body";
            String before;
            if (tokens.length > 1) {
                body = tokens[0];
                before = tokens[1];
            } else {
                before = tokens[0];
            }
            return "Object value = " + body + ";\n        Object after = " + before
                   + ";\n        return substringAfter(exchange, value, after);";
        }
        remainder = ifStartsWithReturnRemainder("substringBetween(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)}, ${substringBetween(exp,after,before)} was: "
                                                + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${substringBetween(after,before)}, ${substringBetween(exp,after,before)} was: "
                                                + function,
                        token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 0; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            String body = "body";
            String after;
            String before;
            if (tokens.length == 3) {
                body = tokens[0];
                after = tokens[1];
                before = tokens[2];
            } else {
                after = tokens[0];
                before = tokens[1];
            }
            return "Object value = " + body + ";\n        Object after = " + after
                   + ";\n        Object before = " + before
                   + ";\n        return substringBetween(exchange, value, after, before);";
        }
        // split function
        remainder = ifStartsWithReturnRemainder("split(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String exp = "body";
            String separator = ",";
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${split(separator)} or ${split(exp,separator)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                for (int i = 0; i < tokens.length; i++) {
                    String s = tokens[i];
                    if (StringHelper.isSingleQuoted(s)) {
                        s = StringHelper.removeLeadingAndEndingQuotes(s);
                        s = StringQuoteHelper.doubleQuote(s);
                        tokens[i] = s;
                    }
                }
                if (tokens.length == 2) {
                    exp = tokens[0];
                    separator = tokens[1];
                } else {
                    separator = tokens[0];
                }
            }
            // separator must be in double quotes
            separator = StringHelper.removeLeadingAndEndingQuotes(separator);
            separator = StringQuoteHelper.doubleQuote(separator);
            return "Object value = " + exp + ";\n        String separator = " + separator
                   + ";\n        return stringSplit(exchange, value, separator);";
        }
        // foreach function
        remainder = ifStartsWithReturnRemainder("forEach(", function);
        if (remainder != null) {
            throw new UnsupportedOperationException("forEach is not supported in csimple language");
        }

        // random function
        remainder = ifStartsWithReturnRemainder("random(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${random(min,max)} or ${random(max)} was: " + function, token.getIndex());
            }
            if (values.contains(",")) {
                String before = StringHelper.before(remainder, ",");
                before = before.trim();
                String after = StringHelper.after(remainder, ",");
                after = after.trim();
                if (after.endsWith(")")) {
                    after = after.substring(0, after.length() - 1);
                }
                return "random(exchange, " + before + ", " + after + ")";
            } else {
                return "random(exchange, 0, " + values.trim() + ")";
            }
        }

        // replace function
        remainder = ifStartsWithReturnRemainder("replace(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${replace(from,to)} was: " + function, token.getIndex());
            }
            String from = StringHelper.xmlDecode(tokens[0]);
            String to = StringHelper.xmlDecode(tokens[1]);
            // special to make it easy to replace to an empty value (ie remove)
            if ("&empty;".equals(to)) {
                to = "";
            }
            if ("\"".equals(from)) {
                from = "\\\"";
            }
            if ("\"".equals(to)) {
                to = "\\\"";
            }
            from = StringQuoteHelper.doubleQuote(from);
            to = StringQuoteHelper.doubleQuote(to);
            return "replace(exchange, " + from + ", " + to + ")";
        }

        // distinct function
        remainder = ifStartsWithReturnRemainder("distinct(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "body";
            return "distinct(exchange, " + p + ")";
        }
        // reverse function
        remainder = ifStartsWithReturnRemainder("reverse(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "body";
            return "reverse(exchange, " + p + ")";
        }
        // shuffle function
        remainder = ifStartsWithReturnRemainder("shuffle(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "body";
            return "shuffle(exchange, " + p + ")";
        }

        // skip function
        remainder = ifStartsWithReturnRemainder("skip(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${skip(number)} was: " + function, token.getIndex());
            }
            return "skip(exchange, " + values.trim() + ")";
        }

        // pad function
        remainder = ifStartsWithReturnRemainder("pad(", function);
        if (remainder != null) {
            String separator = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(len)} or ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length < 2 || tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${pad(exp,len)} or ${pad(exp,len,separator)} was: " + function,
                        token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 0; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            if (tokens.length == 3) {
                separator = tokens[2];
            }
            // separator must be in double quotes
            separator = StringHelper.removeLeadingAndEndingQuotes(separator);
            separator = separator != null ? StringQuoteHelper.doubleQuote(separator) : "null";
            return "Object value = " + tokens[0] + ";\n        " + "Object width = " + tokens[1]
                   + ";\n        String separator = " + separator
                   + ";\n        return pad(exchange, value, width, separator);";
        }

        // trim function
        remainder = ifStartsWithReturnRemainder("trim(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${trim(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return trim(exchange, o);";
        }

        // isEmpty function
        remainder = ifStartsWithReturnRemainder("isEmpty(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${isEmpty(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "body";
            }
            return "Object o = " + exp + ";\n        return isEmpty(exchange, o);";
        }

        // isNumeric function
        remainder = ifStartsWithReturnRemainder("isAlpha(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${isAlpha(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "body";
            }
            return "Object o = " + exp + ";\n        return isAlpha(exchange, o);";
        }
        // isAlphaNumeric function
        remainder = ifStartsWithReturnRemainder("isAlphaNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${isAlphaNumeric(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "body";
            }
            return "Object o = " + exp + ";\n        return isAlphaNumeric(exchange, o);";
        }
        // isNumeric function
        remainder = ifStartsWithReturnRemainder("isNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${isNumeric(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "body";
            }
            return "Object o = " + exp + ";\n        return isNumeric(exchange, o);";
        }
        // not function
        remainder = ifStartsWithReturnRemainder("not(", function);
        if (remainder != null) {
            String exp = "body";
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${not(exp)} was: " + function, token.getIndex());
                }

                // Parse the condition as a predicate and generate code
                SimplePredicateParser predicateParser
                        = new SimplePredicateParser(camelContext, tokens[0], true, skipFileFunctions, null);
                exp = predicateParser.parseCode();
            }
            return "Object o = " + exp + ";\n        return isNot(exchange, o);";
        }

        // capitalize function
        remainder = ifStartsWithReturnRemainder("capitalize(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${capitalize(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return capitalize(exchange, o);";
        }

        // concat function
        remainder = ifStartsWithReturnRemainder("concat(", function);
        if (remainder != null) {
            String separator = "null";
            String exp1 = "body";
            String exp2;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: " + function,
                        token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length > 3) {
                    throw new SimpleParserException(
                            "Valid syntax: ${concat(exp)} or ${concat(exp,exp)} or ${concat(exp,exp,separator)} was: "
                                                    + function,
                            token.getIndex());
                }
                // single quotes should be double quotes
                for (int i = 0; i < tokens.length; i++) {
                    String s = tokens[i];
                    if (StringHelper.isSingleQuoted(s)) {
                        s = StringHelper.removeLeadingAndEndingQuotes(s);
                        s = StringQuoteHelper.doubleQuote(s);
                        tokens[i] = s;
                    }
                }
                if (tokens.length == 1) {
                    exp2 = tokens[0];
                } else {
                    exp1 = tokens[0];
                    exp2 = tokens[1];
                }
                if (tokens.length == 3) {
                    separator = tokens[2];
                }
            } else {
                String s = values.trim();
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
                exp2 = s;
            }
            return "Object right = " + exp2 + ";\n        Object left = " + exp1 + ";\n        " + "Object separator = "
                   + separator + ";\n        return concat(exchange, left, right, separator);";
        }

        // convertTo function
        remainder = ifStartsWithReturnRemainder("convertTo(", function);
        if (remainder != null) {
            String ognl = null;
            String exp = "body";
            String type;
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function,
                        token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function,
                            token.getIndex());
                }
                String s = tokens[0].trim();
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
                exp = s;
                type = tokens[1];
            } else {
                type = values.trim();
            }
            type = appendClass(type);
            type = type.replace('$', '.');
            if (ObjectHelper.isEmpty(exp)) {
                exp = "null";
            }

            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${convertTo(type).OGNL} or ${convertTo(exp,type).OGNL} was: " + function,
                            token.getIndex());
                }
                ognl = ognlCodeMethods(remainder, type);
            }

            String code = "Object value = " + exp + ";\n        return convertTo(exchange, " + type + ", value)";
            if (ognl != null) {
                code += ognl;
            }
            code += ";";
            return code;
        }
        // throwException function
        remainder = ifStartsWithReturnRemainder("throwException(", function);
        if (remainder != null) {
            String msg;
            String type = "IllegalArgumentException";
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${throwException(msg)} or ${throwException(msg,type)} was: " + function,
                        token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${throwException(msg)} or ${throwException(msg,type)} was: " + function,
                            token.getIndex());
                }
                msg = tokens[0];
                type = tokens[1];
            } else {
                msg = values.trim();
            }
            msg = StringHelper.removeLeadingAndEndingQuotes(msg);
            msg = StringQuoteHelper.doubleQuote(msg);
            type = appendClass(type);
            type = type.replace('$', '.');
            return "return throwException(exchange, " + msg + ", " + type + ");";
        }

        // uppercase function
        remainder = ifStartsWithReturnRemainder("uppercase(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${uppercase(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return uppercase(exchange, o);";
        }
        // lowercase
        remainder = ifStartsWithReturnRemainder("lowercase(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${lowercase(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return lowercase(exchange, o);";
        }

        // length function
        remainder = ifStartsWithReturnRemainder("length(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${length(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "body";
            }
            return "Object o = " + exp + ";\n        return length(exchange, o);";
        }
        // size function
        remainder = ifStartsWithReturnRemainder("size(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${size(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "body";
            }
            return "Object o = " + exp + ";\n        return size(exchange, o);";
        }
        // normalizeWhitespace function
        remainder = ifStartsWithReturnRemainder("normalizeWhitespace(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${normalizeWhitespace(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return normalizeWhitespace(exchange, o);";
        }

        // collate function
        remainder = ifStartsWithReturnRemainder("collate(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${collate(group)} was: " + function, token.getIndex());
            }
            return "collate(exchange, " + values.trim() + ")";
        }

        // messageHistory function
        remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null) {
            boolean detailed;
            String values = StringHelper.between(remainder, "(", ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                detailed = true;
            } else {
                detailed = Boolean.parseBoolean(values);
            }
            return "messageHistory(exchange, " + (detailed ? "true" : "false") + ")";
        } else if (ObjectHelper.equal(function, "messageHistory")) {
            return "messageHistory(exchange, true)";
        }

        // join function
        remainder = ifStartsWithReturnRemainder("join(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String separator = "\",\"";
            String prefix = null;
            String exp = "body";
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length > 3) {
                    throw new SimpleParserException(
                            "Valid syntax: ${join(separator,prefix,expression)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
                for (int i = 0; i < tokens.length; i++) {
                    String s = tokens[i];
                    if (StringHelper.isSingleQuoted(s)) {
                        s = StringHelper.removeLeadingAndEndingQuotes(s);
                        s = StringQuoteHelper.doubleQuote(s);
                        tokens[i] = s;
                    } else if (i < 2 && !StringHelper.isDoubleQuoted(s)) {
                        s = StringQuoteHelper.doubleQuote(s);
                        tokens[i] = s;
                    }
                }
                if (tokens.length == 3) {
                    separator = tokens[0];
                    prefix = tokens[1];
                    exp = tokens[2];
                } else if (tokens.length == 2) {
                    separator = tokens[0];
                    prefix = tokens[1];
                } else {
                    separator = tokens[0];
                }
            }
            return "var val = " + exp + ";\n        return join(exchange, val, " + separator + ", " + prefix + ");";
        }

        // empty function
        remainder = ifStartsWithReturnRemainder("empty(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException(
                        "Valid syntax: ${empty(<type>)} but was: " + function, token.getIndex());
            }
            value = StringQuoteHelper.doubleQuote(value);
            return "newEmpty(exchange, " + value + ")";
        }
        // newEmpty
        remainder = ifStartsWithReturnRemainder("newEmpty(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException(
                        "Valid syntax: ${newEmpty(<type>)} but was: " + function, token.getIndex());
            }
            value = StringQuoteHelper.doubleQuote(value);
            return "newEmpty(exchange, " + value + ")";
        }

        // list function
        remainder = ifStartsWithReturnRemainder("list(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "null";
            return "list(exchange, " + p + ")";
        }
        // distinct function
        remainder = ifStartsWithReturnRemainder("distinct(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "body";
            return "distinct(exchange, " + p + ")";
        }

        // map function
        remainder = ifStartsWithReturnRemainder("map(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "null";
            return "map(exchange, " + p + ")";
        }

        // hash function
        remainder = ifStartsWithReturnRemainder("hash(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 0; i < tokens.length; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }
            String algo = "\"SHA-256\"";
            if (tokens.length == 2) {
                algo = tokens[1];
                if (!StringHelper.isQuoted(algo)) {
                    algo = StringQuoteHelper.doubleQuote(algo);
                }
            }
            return "var val = " + tokens[0] + ";\n        return hash(exchange, val, " + algo + ");";
        }

        // uuid function
        remainder = ifStartsWithReturnRemainder("uuid", function);
        if (remainder == null && "uuid".equals(function)) {
            remainder = "(default)";
        }
        if (remainder != null) {
            String generator = StringHelper.between(remainder, "(", ")");
            if (generator == null) {
                generator = "default";
            }
            StringBuilder sb = new StringBuilder(128);
            if ("classic".equals(generator)) {
                sb.append("    UuidGenerator uuid = new org.apache.camel.support.ClassicUuidGenerator();\n");
                sb.append("return uuid.generateUuid();");
            } else if ("short".equals(generator)) {
                sb.append("    UuidGenerator uuid = new org.apache.camel.support.ShortUuidGenerator();\n");
                sb.append("return uuid.generateUuid();");
            } else if ("simple".equals(generator)) {
                sb.append("    UuidGenerator uuid = new org.apache.camel.support.SimpleUuidGenerator();\n");
                sb.append("return uuid.generateUuid();");
            } else if ("default".equals(generator)) {
                sb.append("    UuidGenerator uuid = new org.apache.camel.support.DefaultUuidGenerator();\n");
                sb.append("return uuid.generateUuid();");
            } else if ("random".equals(generator)) {
                sb.append("    UuidGenerator uuid = new org.apache.camel.support.RandomUuidGenerator();\n");
                sb.append("return uuid.generateUuid();");
            } else {
                generator = StringQuoteHelper.doubleQuote(generator);
                sb.append("if (uuid == null) uuid = customUuidGenerator(exchange, ").append(generator)
                        .append("); return uuid.generateUuid();");
            }
            return sb.toString();
        }

        // iif function
        remainder = ifStartsWithReturnRemainder("iif(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, token.getIndex());
            }
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, token.getIndex());
            }
            // single quotes should be double quotes
            for (int i = 0; i < 3; i++) {
                String s = tokens[i];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                    tokens[i] = s;
                }
            }

            return "Object o = " + tokens[0]
                   + ";\n        boolean b = convertTo(exchange, boolean.class, o);\n        return b ? "
                   + tokens[1] + " : " + tokens[2];
        }

        return null;
    }

    private static List<String> splitOgnl(String remainder) {
        List<String> methods = OgnlHelper.splitOgnl(remainder);
        // if its a double index [foo][0] then we want them combined into a single element
        List<String> answer = new ArrayList<>();
        for (String m : methods) {
            if (m.startsWith(".")) {
                m = m.substring(1);
            }
            boolean index = m.startsWith("[") && m.endsWith("]");
            if (index) {
                String last = answer.isEmpty() ? null : answer.get(answer.size() - 1);
                boolean lastIndex = last != null && last.startsWith("[") && last.endsWith("]");
                if (lastIndex) {
                    String line = last + m;
                    answer.set(answer.size() - 1, line);
                } else {
                    answer.add(m);
                }
            } else {
                answer.add(m);
            }
        }

        return answer;
    }

    private String createCodeExpressionMath(String function) {
        String remainder;

        // sum function
        remainder = ifStartsWithReturnRemainder("sum(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "null";
            return "sum(exchange, " + p + ")";
        }
        // abs function
        remainder = ifStartsWithReturnRemainder("abs(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${abs(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return abs(exchange, o);";
        }
        // floor function
        remainder = ifStartsWithReturnRemainder("floor(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${floor(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return floor(exchange, o);";
        }
        remainder = ifStartsWithReturnRemainder("ceil(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${ceil(exp)} was: " + function, token.getIndex());
                }
                // single quotes should be double quotes
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
            return "Object o = " + exp + ";\n        return ceil(exchange, o);";
        }

        // max function
        remainder = ifStartsWithReturnRemainder("max(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "null";
            return "max(exchange, " + p + ")";
        }
        // min function
        remainder = ifStartsWithReturnRemainder("min(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "null";
            return "min(exchange, " + p + ")";
        }
        // average function
        remainder = ifStartsWithReturnRemainder("average(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            String[] tokens = null;
            if (ObjectHelper.isNotEmpty(values)) {
                tokens = codeSplitSafe(values, ',', true, true);
            }
            StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; tokens != null && i < tokens.length; i++) {
                String s = tokens[i];
                // single quotes should be double quotes
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                sj.add(s);
            }
            String p = sj.length() > 0 ? sj.toString() : "null";
            return "average(exchange, " + p + ")";
        }

        return null;
    }

    public static String ognlCodeMethods(String remainder, String type) {
        StringBuilder sb = new StringBuilder(256);

        if (remainder != null) {
            List<String> methods = splitOgnl(remainder);
            for (int i = 0; i < methods.size(); i++) {
                String m = methods.get(i);
                if (m.startsWith("(")) {
                    // its parameters for the function so add as-is and continue
                    sb.append(m);
                    continue;
                }

                // clip index
                String index = StringHelper.betweenOuterPair(m, '[', ']');
                if (index != null) {
                    m = StringHelper.before(m, "[");
                }

                // special for length on arrays
                if (m != null && m.equals("length")) {
                    if (type != null && type.contains("[]")) {
                        sb.append(".length");
                        continue;
                    }
                }

                // single quotes for string literals should be replaced as double quotes
                if (m != null) {
                    m = OgnlHelper.methodAsDoubleQuotes(m);
                }

                // shorthand getter syntax: .name -> .getName()
                if (m != null && !m.isEmpty()) {
                    // a method so append with a dot
                    sb.append(".");
                    char ch = m.charAt(m.length() - 1);
                    if (Character.isAlphabetic(ch)) {
                        if (!m.startsWith("get")) {
                            sb.append("get");
                            sb.append(Character.toUpperCase(m.charAt(0)));
                            sb.append(m.substring(1));
                        } else {
                            sb.append(m);
                        }
                        sb.append("()");
                    } else {
                        sb.append(m);
                    }
                }

                // append index via a get method - eg get for a list, or get for a map (array not supported)
                if (index != null) {
                    sb.append(".get(");
                    try {
                        long lon = Long.parseLong(index);
                        sb.append(lon);
                        if (lon > Integer.MAX_VALUE) {
                            sb.append("l");
                        }
                    } catch (Exception e) {
                        // its text based
                        index = StringHelper.removeLeadingAndEndingQuotes(index);
                        sb.append("\"");
                        sb.append(index);
                        sb.append("\"");
                    }
                    sb.append(")");
                }
            }
        }

        if (!sb.isEmpty()) {
            return sb.toString();
        } else {
            return remainder;
        }
    }

    public static String[] codeSplitSafe(String input, char separator, boolean trim, boolean keepQuotes) {
        if (input == null) {
            return null;
        }

        if (input.indexOf(separator) == -1) {
            if (input.length() > 1) {
                char ch = input.charAt(0);
                char ch2 = input.charAt(input.length() - 1);
                boolean singleQuoted = ch == '\'' && ch2 == '\'';
                boolean doubleQuoted = ch == '"' && ch2 == '"';
                if (!keepQuotes && (singleQuoted || doubleQuoted)) {
                    input = input.substring(1, input.length() - 1);
                    // do not trim quoted text
                } else if (trim) {
                    input = input.trim();
                }
            }
            // no separator in data, so return single string with input as is
            return new String[] { input };
        }

        List<String> answer = new ArrayList<>();
        StringBuilder sb = new StringBuilder(256);

        int codeLevel = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean separating = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            char prev = i > 0 ? input.charAt(i - 1) : 0;
            boolean isQuoting = singleQuoted || doubleQuoted;
            boolean last = i == input.length() - 1;

            // do not split inside code blocks
            if (input.indexOf(BaseSimpleParser.CODE_START, i) == i) {
                codeLevel++;
                sb.append(BaseSimpleParser.CODE_START);
                i = i + BaseSimpleParser.CODE_START.length() - 1;
                continue;
            } else if (input.indexOf(BaseSimpleParser.CODE_END, i) == i) {
                codeLevel--;
                sb.append(BaseSimpleParser.CODE_END);
                i = i + BaseSimpleParser.CODE_END.length() - 1;
                continue;
            }
            if (codeLevel > 0) {
                sb.append(ch);
                continue;
            }

            if (!doubleQuoted && ch == '\'') {
                if (singleQuoted && prev == ch && sb.isEmpty()) {
                    // its an empty quote so add empty text
                    if (keepQuotes) {
                        answer.add("''");
                    } else {
                        answer.add("");
                    }
                }
                // special logic needed if this quote is the end
                if (last) {
                    if (singleQuoted && !sb.isEmpty()) {
                        String text = sb.toString();
                        // do not trim a quoted string
                        if (keepQuotes) {
                            answer.add(text + "'"); // append ending quote
                        } else {
                            answer.add(text);
                        }
                        sb.setLength(0);
                    }
                    break; // break out as we are finished
                }
                singleQuoted = !singleQuoted;
                if (keepQuotes) {
                    sb.append(ch);
                }
                continue;
            } else if (!singleQuoted && ch == '"') {
                if (doubleQuoted && prev == ch && sb.isEmpty()) {
                    // its an empty quote so add empty text
                    if (keepQuotes) {
                        answer.add("\""); // append ending quote
                    } else {
                        answer.add("");
                    }
                }
                // special logic needed if this quote is the end
                if (last) {
                    if (doubleQuoted && !sb.isEmpty()) {
                        String text = sb.toString();
                        // do not trim a quoted string
                        if (keepQuotes) {
                            answer.add(text + "\"");
                        } else {
                            answer.add(text);
                        }
                        sb.setLength(0);
                    }
                    break; // break out as we are finished
                }
                doubleQuoted = !doubleQuoted;
                if (keepQuotes) {
                    sb.append(ch);
                }
                continue;
            } else if (!isQuoting && ch == separator) {
                separating = true;
                // add as answer if we are not in a quote
                if (!sb.isEmpty()) {
                    String text = sb.toString();
                    if (trim) {
                        text = text.trim();
                    }
                    answer.add(text);
                    sb.setLength(0);
                }
                // we should avoid adding the separator
                continue;
            }

            if (trim && !isQuoting && separating && separator != ' ' && ch == ' ') {
                continue;
            }
            separating = false;

            // append char
            sb.append(ch);
        }

        // any leftover
        if (!sb.isEmpty()) {
            String text = sb.toString();
            if (trim) {
                text = text.trim();
            }
            answer.add(text);
        }

        return answer.toArray(new String[0]);
    }

}
