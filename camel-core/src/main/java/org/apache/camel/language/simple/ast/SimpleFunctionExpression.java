/**
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

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

/**
 * Represents one of built-in functions of the
 * <a href="http://camel.apache.org/simple.html">simple language</a>
 */
public class SimpleFunctionExpression extends LiteralExpression {

    public SimpleFunctionExpression(SimpleToken token) {
        super(token);
    }

    @Override
    public Expression createExpression(String expression) {
        String function = text.toString();
        return createSimpleExpression(function, true);
    }

    /**
     * Creates a Camel {@link Expression} based on this model.
     *
     * @param expression the input string
     * @param strict whether to throw exception if the expression was not a function,
     *          otherwise <tt>null</tt> is returned
     * @return the created {@link Expression}
     * @throws org.apache.camel.language.simple.types.SimpleParserException
     *          should be thrown if error parsing the model
     */
    public Expression createExpression(String expression, boolean strict) {
        String function = text.toString();
        return createSimpleExpression(function, strict);
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
            return ExpressionBuilder.camelContextOgnlExpression(remainder);
        }

        // Exception OGNL
        remainder = ifStartsWithReturnRemainder("exception", function);
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${exception.OGNL} was: " + function, token.getIndex());
            }
            return ExpressionBuilder.exchangeExceptionOgnlExpression(remainder);
        }

        // property
        remainder = ifStartsWithReturnRemainder("property", function);
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
                throw new SimpleParserException("Valid syntax: ${property.OGNL} was: " + function, token.getIndex());
            }

            if (OgnlHelper.isValidOgnlExpression(remainder)) {
                // ognl based property
                return ExpressionBuilder.propertyOgnlExpression(remainder);
            } else {
                // regular property
                return ExpressionBuilder.propertyExpression(remainder);
            }
        }

        // system property
        remainder = ifStartsWithReturnRemainder("sys.", function);
        if (remainder != null) {
            return ExpressionBuilder.systemPropertyExpression(remainder);
        }
        remainder = ifStartsWithReturnRemainder("sysenv.", function);
        if (remainder != null) {
            return ExpressionBuilder.systemEnvironmentExpression(remainder);
        }

        // file: prefix
        remainder = ifStartsWithReturnRemainder("file:", function);
        if (remainder != null) {
            Expression fileExpression = createSimpleFileExpression(remainder);
            if (function != null) {
                return fileExpression;
            }
        }

        // date: prefix
        remainder = ifStartsWithReturnRemainder("date:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":");
            if (parts.length < 2) {
                throw new SimpleParserException("Valid syntax: ${date:command:pattern} was: " + function, token.getIndex());
            }
            String command = ObjectHelper.before(remainder, ":");
            String pattern = ObjectHelper.after(remainder, ":");
            return ExpressionBuilder.dateExpression(command, pattern);
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
                throw new SimpleParserException("Valid syntax: ${properties:[locations]:key} was: " + function, token.getIndex());
            }

            String locations = null;
            String key = remainder;
            if (parts.length == 2) {
                locations = ObjectHelper.before(remainder, ":");
                key = ObjectHelper.after(remainder, ":");
            }
            return ExpressionBuilder.propertiesComponentExpression(key, locations);
        }

        // ref: prefix
        remainder = ifStartsWithReturnRemainder("ref:", function);
        if (remainder != null) {
            return ExpressionBuilder.refExpression(remainder);
        }

        // const: prefix
        remainder = ifStartsWithReturnRemainder("type:", function);
        if (remainder != null) {
            Expression exp = ExpressionBuilder.typeExpression(remainder);
            // we want to cache this expression so we wont re-evaluate it as the type/constant wont change
            return ExpressionBuilder.cacheExpression(exp);
        }

        if (strict) {
            throw new SimpleParserException("Unknown function: " + function, token.getIndex());
        } else {
            return null;
        }
    }

    private Expression createSimpleExpressionBodyOrHeader(String function, boolean strict) {
        // bodyAs
        String remainder = ifStartsWithReturnRemainder("bodyAs", function);
        if (remainder != null) {
            String type = ObjectHelper.between(remainder, "(", ")");
            remainder = ObjectHelper.after(remainder, ")");
            if (type == null || ObjectHelper.isNotEmpty(remainder)) {
                throw new SimpleParserException("Valid syntax: ${bodyAs(type)} was: " + function, token.getIndex());
            }
            
            type = StringHelper.removeQuotes(type);
            return ExpressionBuilder.bodyExpression(type);
        }
        // mandatoryBodyAs
        remainder = ifStartsWithReturnRemainder("mandatoryBodyAs", function);
        if (remainder != null) {
            String type = ObjectHelper.between(remainder, "(", ")");
            remainder = ObjectHelper.after(remainder, ")");
            if (type == null || ObjectHelper.isNotEmpty(remainder)) {
                throw new SimpleParserException("Valid syntax: ${mandatoryBodyAs(type)} was: " + function, token.getIndex());
            }
            type = StringHelper.removeQuotes(type);
            return ExpressionBuilder.mandatoryBodyExpression(type);
        }

        // body OGNL
        remainder = ifStartsWithReturnRemainder("body", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.body", function);
        }
        if (remainder != null) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${body.OGNL} was: " + function, token.getIndex());
            }
            return ExpressionBuilder.bodyOgnlExpression(remainder);
        }

        // headerAs
        remainder = ifStartsWithReturnRemainder("headerAs", function);
        if (remainder != null) {
            String keyAndType = ObjectHelper.between(remainder, "(", ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, token.getIndex());
            }

            String key = ObjectHelper.before(keyAndType, ",");
            String type = ObjectHelper.after(keyAndType, ",");
            remainder = ObjectHelper.after(remainder, ")");
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

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${header.name[key]} was: " + function, token.getIndex());
            }

            if (OgnlHelper.isValidOgnlExpression(key)) {
                // ognl based header
                return ExpressionBuilder.headersOgnlExpression(key);
            } else {
                // regular header
                return ExpressionBuilder.headerExpression(key);
            }
        }

        // out header function
        remainder = ifStartsWithReturnRemainder("out.header.", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("out.headers.", function);
        }
        if (remainder != null) {
            return ExpressionBuilder.outHeaderExpression(remainder);
        }

        return null;
    }

    private Expression createSimpleExpressionDirectly(String expression) {
        if (ObjectHelper.isEqualToAny(expression, "body", "in.body")) {
            return ExpressionBuilder.bodyExpression();
        } else if (ObjectHelper.equal(expression, "out.body")) {
            return ExpressionBuilder.outBodyExpression();
        } else if (ObjectHelper.equal(expression, "id")) {
            return ExpressionBuilder.messageIdExpression();
        } else if (ObjectHelper.equal(expression, "exchangeId")) {
            return ExpressionBuilder.exchangeIdExpression();
        } else if (ObjectHelper.equal(expression, "exception")) {
            return ExpressionBuilder.exchangeExceptionExpression();
        } else if (ObjectHelper.equal(expression, "exception.message")) {
            return ExpressionBuilder.exchangeExceptionMessageExpression();
        } else if (ObjectHelper.equal(expression, "exception.stacktrace")) {
            return ExpressionBuilder.exchangeExceptionStackTraceExpression();
        } else if (ObjectHelper.equal(expression, "threadName")) {
            return ExpressionBuilder.threadNameExpression();
        } else if (ObjectHelper.equal(expression, "camelId")) {
            return ExpressionBuilder.camelContextNameExpression();
        } else if (ObjectHelper.equal(expression, "routeId")) {
            return ExpressionBuilder.routeIdExpression();
        } else if (ObjectHelper.equal(expression, "null")) {
            return ExpressionBuilder.nullExpression();
        }

        return null;
    }

    private Expression createSimpleFileExpression(String remainder) {
        if (ObjectHelper.equal(remainder, "name")) {
            return ExpressionBuilder.fileNameExpression();
        } else if (ObjectHelper.equal(remainder, "name.noext")) {
            return ExpressionBuilder.fileNameNoExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "name.ext")) {
            return ExpressionBuilder.fileExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname")) {
            return ExpressionBuilder.fileOnlyNameExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname.noext")) {
            return ExpressionBuilder.fileOnlyNameNoExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "ext")) {
            return ExpressionBuilder.fileExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "parent")) {
            return ExpressionBuilder.fileParentExpression();
        } else if (ObjectHelper.equal(remainder, "path")) {
            return ExpressionBuilder.filePathExpression();
        } else if (ObjectHelper.equal(remainder, "absolute")) {
            return ExpressionBuilder.fileAbsoluteExpression();
        } else if (ObjectHelper.equal(remainder, "absolute.path")) {
            return ExpressionBuilder.fileAbsolutePathExpression();
        } else if (ObjectHelper.equal(remainder, "length") || ObjectHelper.equal(remainder, "size")) {
            return ExpressionBuilder.fileSizeExpression();
        } else if (ObjectHelper.equal(remainder, "modified")) {
            return ExpressionBuilder.fileLastModifiedExpression();
        }
        throw new SimpleParserException("Unknown file language syntax: " + remainder, token.getIndex());
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
