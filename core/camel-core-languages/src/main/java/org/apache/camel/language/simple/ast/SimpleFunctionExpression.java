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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.language.simple.BaseSimpleParser;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.language.simple.SimpleFunctionDispatcher;
import org.apache.camel.language.simple.SimpleFunctionHelper;
import org.apache.camel.language.simple.SimplePredicateParser;
import org.apache.camel.language.simple.functions.DirectFunctionFactory;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.spi.Language;
import org.apache.camel.support.PluginHelper;
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
        Class<?> type = null;

        // is it a known result type (make it easy in simple to return the value as you need)
        if (function.startsWith("int:")) {
            type = int.class;
            function = function.substring(4);
        } else if (function.startsWith("integer:")) {
            type = int.class;
            function = function.substring(8);
        } else if (function.startsWith("long:")) {
            type = long.class;
            function = function.substring(5);
        } else if (function.startsWith("boolean:")) {
            type = boolean.class;
            function = function.substring(8);
        } else if (function.startsWith("string:")) {
            type = String.class;
            function = function.substring(7);
        }
        Expression exp = doCreateSimpleExpression(camelContext, function, strict);
        if (type != null) {
            exp = ExpressionBuilder.convertToExpression(exp, type);
        }
        return exp;
    }

    private static final DirectFunctionFactory DIRECT_FACTORY = new DirectFunctionFactory();

    private Expression doCreateSimpleExpression(CamelContext camelContext, String function, boolean strict) {
        // return the function directly if we can create function without analyzing the prefix
        Expression answer = DIRECT_FACTORY.createFunction(camelContext, function, token.getIndex());
        if (answer != null) {
            return answer;
        }

        // custom functions
        answer = createSimpleCustomFunction(camelContext, function, strict);
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

        // miscellaneous and other built-in functions
        Expression builtIn = SimpleFunctionDispatcher.tryCreateBuiltIn(camelContext, function, token.getIndex());
        if (builtIn != null) {
            return builtIn;
        }

        // functions from external components (attachments, base64, html, ...)
        Expression external = SimpleFunctionDispatcher.tryCreateExternal(camelContext, function, token.getIndex());
        if (external != null) {
            return external;
        }

        // it may be a custom function
        String name = StringHelper.before(function, "(", function);
        if (PluginHelper.getSimpleFunctionRegistry(camelContext).getFunction(name) != null) {
            String after = StringHelper.after(function, "(");
            if (after == null || after.equals(")")) {
                function = "function(" + name + ")";
            } else {
                function = "function(" + name + "," + after;
            }
            Expression exp = createSimpleCustomFunction(camelContext, function, strict);
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

    private Expression createSimpleCustomFunction(CamelContext camelContext, String function, boolean strict) {
        String remainder = ifStartsWithReturnRemainder("function(", function);
        if (remainder != null) {
            String key;
            String param = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${function(name)} or ${function(name,exp)} was: " + function,
                        token.getIndex());
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length < 1 || tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${function(name)} or ${function(name,exp)} was: " + function,
                        token.getIndex());
            }
            key = StringHelper.removeQuotes(tokens[0]);
            key = key.trim();
            if (tokens.length == 2) {
                param = tokens[1];
                param = StringHelper.removeLeadingAndEndingQuotes(param.trim());
            }
            if (param == null) {
                param = "${body}";
            }
            return SimpleExpressionBuilder.customFunction(key, param);
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
        // simple-jsonpath
        remainder = ifStartsWithReturnRemainder("simpleJsonpath(", function);
        if (remainder != null) {
            String exp = StringHelper.beforeLast(remainder, ")");
            if (exp == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${simpleJsonpath(exp)} was: " + function, token.getIndex());
            }
            String input = null;
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            if (exp.startsWith("header:") || exp.startsWith("property:") || exp.startsWith("exchangeProperty:")
                    || exp.startsWith("variable:")) {
                input = StringHelper.before(exp, ",");
                exp = StringHelper.after(exp, ",");
                if (input != null) {
                    input = input.trim();
                }
                if (exp != null) {
                    exp = exp.trim();
                }
            }
            return SimpleExpressionBuilder.simpleJsonPathExpression(input, exp);
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

    @Deprecated(since = "4.21")
    public static String ifStartsWithReturnRemainder(String prefix, String text) {
        return SimpleFunctionHelper.ifStartsWithReturnRemainder(prefix, text);
    }

    @Override
    public String createCode(CamelContext camelContext, String expression) throws SimpleParserException {
        return BaseSimpleParser.CODE_START + doCreateCode(camelContext, expression) + BaseSimpleParser.CODE_END;
    }

    private String doCreateCode(CamelContext camelContext, String expression) throws SimpleParserException {
        String function = getText();

        // return the function directly if we can create function without analyzing the prefix
        String answer = DIRECT_FACTORY.createCode(camelContext, function, token.getIndex());
        if (answer != null) {
            return answer;
        }

        // exchange property first
        answer = createCodeExchangeProperty(function);
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

        // miscellaneous and other built-in functions
        String builtIn = SimpleFunctionDispatcher.tryCreateCodeBuiltIn(camelContext, function, token.getIndex());
        if (builtIn != null) {
            return builtIn;
        }

        // not() code-gen requires skipFileFunctions from the parser context and cannot be in MiscFunctionFactory
        String notRemainder = ifStartsWithReturnRemainder("not(", function);
        if (notRemainder != null) {
            String exp = "body";
            String values = StringHelper.beforeLast(notRemainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException("Valid syntax: ${not(exp)} was: " + function, token.getIndex());
                }
                SimplePredicateParser predicateParser
                        = new SimplePredicateParser(camelContext, tokens[0], true, skipFileFunctions, null);
                exp = predicateParser.parseCode();
            }
            return "Object o = " + exp + ";\n        return isNot(exchange, o);";
        }

        // code from external components (attachments, base64, ...)
        String external = SimpleFunctionDispatcher.tryCreateCodeExternal(camelContext, function, token.getIndex());
        if (external != null) {
            return external;
        }

        throw new SimpleParserException("Unknown function: " + function, token.getIndex());
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

    @Deprecated(since = "4.21")
    public static String ognlCodeMethods(String remainder, String type) {
        return SimpleFunctionHelper.ognlCodeMethods(remainder, type);
    }

    @Deprecated(since = "4.21")
    public static String[] codeSplitSafe(String input, char separator, boolean trim, boolean keepQuotes) {
        return SimpleFunctionHelper.codeSplitSafe(input, separator, trim, keepQuotes);
    }

}
