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

import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

/**
 * Represents one of built-in functions of the
 * <a href="http://camel.apache.org/simple.html">simple language</a>
 */
public class SimpleFunctionExpression extends LiteralExpression {

    // use caches to avoid re-parsing the same expressions over and over again
    private Map<String, Expression> cacheExpression;

    public SimpleFunctionExpression(SimpleToken token, Map<String, Expression> cacheExpression) {
        super(token);
        this.cacheExpression = cacheExpression;
    }

    /**
     * Creates a Camel {@link Expression} based on this model.
     *
     * @param expression not in use
     */
    @Override
    public Expression createExpression(String expression) {
        String function = text.toString();

        Expression answer = cacheExpression != null ? cacheExpression.get(function) : null;
        if (answer == null) {
            answer = createSimpleExpression(function, true);
            if (cacheExpression != null && answer != null) {
                cacheExpression.put(function, answer);
            }
        }
        return answer;
    }

    /**
     * Creates a Camel {@link Expression} based on this model.
     *
     * @param expression not in use
     * @param strict whether to throw exception if the expression was not a function,
     *          otherwise <tt>null</tt> is returned
     * @return the created {@link Expression}
     * @throws org.apache.camel.language.simple.types.SimpleParserException
     *          should be thrown if error parsing the model
     */
    public Expression createExpression(String expression, boolean strict) {
        String function = text.toString();

        Expression answer = cacheExpression != null ? cacheExpression.get(function) : null;
        if (answer == null) {
            answer = createSimpleExpression(function, strict);
            if (cacheExpression != null && answer != null) {
                cacheExpression.put(function, answer);
            }
        }
        return answer;
    }

    private Expression createSimpleExpression(String function, boolean strict) {
        // return the function directly if we can create function without analyzing the prefix
        Expression answer = createSimpleExpressionDirectly(function);
        if (answer != null) {
            return answer;
        }

        // body and headers first
        answer = createSimpleExpressionBodyOrHeader(function, strict);
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
            // remove leading character (dot or ?)
            if (remainder.startsWith(".") || remainder.startsWith("?")) {
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

        // file: prefix
        remainder = ifStartsWithReturnRemainder("file:", function);
        if (remainder != null) {
            Expression fileExpression = createSimpleFileExpression(remainder, strict);
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
                throw new SimpleParserException("Valid syntax: ${date-with-timezone:command:timezone:pattern} was: " + function, token.getIndex());
            }
            return SimpleExpressionBuilder.dateExpression(parts[0], parts[1], parts[2]);
        }

        // bean: prefix
        remainder = ifStartsWithReturnRemainder("bean:", function);
        if (remainder != null) {
            return ExpressionBuilder.beanExpression(remainder);
        }

        // properties: prefix
        remainder = ifStartsWithReturnRemainder("properties:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":");
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

        // const: prefix
        remainder = ifStartsWithReturnRemainder("type:", function);
        if (remainder != null) {
            Expression exp = SimpleExpressionBuilder.typeExpression(remainder);
            // we want to cache this expression so we wont re-evaluate it as the type/constant wont change
            return SimpleExpressionBuilder.cacheExpression(exp);
        }

        // miscellaneous functions
        Expression misc = createSimpleExpressionMisc(function);
        if (misc != null) {
            return misc;
        }

        if (strict) {
            throw new SimpleParserException("Unknown function: " + function, token.getIndex());
        } else {
            return null;
        }
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
                    throw new SimpleParserException("Valid syntax: ${mandatoryBodyAs(type).OGNL} was: " + function, token.getIndex());
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
            // OGNL must start with a . ? or [
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
        }

        // in header function
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

    private Expression createSimpleExpressionDirectly(String expression) {
        if (ObjectHelper.isEqualToAny(expression, "body", "in.body")) {
            return ExpressionBuilder.bodyExpression();
        } else if (ObjectHelper.equal(expression, "bodyOneLine")) {
            return ExpressionBuilder.bodyOneLine();
        } else if (ObjectHelper.equal(expression, "id")) {
            return ExpressionBuilder.messageIdExpression();
        } else if (ObjectHelper.equal(expression, "exchangeId")) {
            return ExpressionBuilder.exchangeIdExpression();
        } else if (ObjectHelper.equal(expression, "exchange")) {
            return ExpressionBuilder.exchangeExpression();
        } else if (ObjectHelper.equal(expression, "exception")) {
            return ExpressionBuilder.exchangeExceptionExpression();
        } else if (ObjectHelper.equal(expression, "exception.message")) {
            return ExpressionBuilder.exchangeExceptionMessageExpression();
        } else if (ObjectHelper.equal(expression, "exception.stacktrace")) {
            return ExpressionBuilder.exchangeExceptionStackTraceExpression();
        } else if (ObjectHelper.equal(expression, "threadName")) {
            return ExpressionBuilder.threadNameExpression();
        } else if (ObjectHelper.equal(expression, "hostname")) {
            return ExpressionBuilder.hostnameExpression();
        } else if (ObjectHelper.equal(expression, "camelId")) {
            return ExpressionBuilder.camelContextNameExpression();
        } else if (ObjectHelper.equal(expression, "routeId")) {
            return ExpressionBuilder.routeIdExpression();
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

        // random function
        remainder = ifStartsWithReturnRemainder("random(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${random(min,max)} or ${random(max)} was: " + function, token.getIndex());
            }
            if (values.contains(",")) {
                String[] tokens = values.split(",", -1);
                if (tokens.length > 2) {
                    throw new SimpleParserException("Valid syntax: ${random(min,max)} or ${random(max)} was: " + function, token.getIndex());
                }
                return SimpleExpressionBuilder.randomExpression(tokens[0].trim(), tokens[1].trim());
            } else {
                return SimpleExpressionBuilder.randomExpression("0", values.trim());
            }
        }

        // skip function
        remainder = ifStartsWithReturnRemainder("skip(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${skip(number)} was: " + function, token.getIndex());
            }
            String exp = "${body}";
            int num = Integer.parseInt(values.trim());
            return SimpleExpressionBuilder.skipExpression(exp, num);
        }

        // collate function
        remainder = ifStartsWithReturnRemainder("collate(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${collate(group)} was: " + function, token.getIndex());
            }
            String exp = "${body}";
            int num = Integer.parseInt(values.trim());
            return SimpleExpressionBuilder.collateExpression(exp, num);
        }

        // messageHistory function
        remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null) {
            boolean detailed;
            String values = StringHelper.between(remainder, "(", ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                detailed = true;
            } else {
                detailed = Boolean.valueOf(values);
            }
            return SimpleExpressionBuilder.messageHistoryExpression(detailed);
        } else if (ObjectHelper.equal(function, "messageHistory")) {
            return SimpleExpressionBuilder.messageHistoryExpression(true);
        }
        return null;
    }

    private String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (remainder.length() > 0) {
                return remainder;
            }
        }
        return null;
    }

}
