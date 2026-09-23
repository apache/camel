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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.FileExpressionBuilder;
import org.apache.camel.language.simple.SimpleFunctionDispatcher;
import org.apache.camel.language.simple.SimpleFunctionHelper;
import org.apache.camel.language.simple.SimplePredicateParser;
import org.apache.camel.language.simple.SimpleSyntaxHints;
import org.apache.camel.language.simple.functions.DirectFunctionFactory;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

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
            answer = createSimpleExpression(camelContext, function);
            if (answer != null) {
                answer.init(camelContext);
            }
            // a custom function from an init block ($f ~:= ...) is bound to the definition of that block,
            // so it is not shared with another expression that defines a function with the same name
            if (cacheExpression != null && answer != null && !function.startsWith("function(")) {
                cacheExpression.put(function, answer);
            }
        }
        return answer;
    }

    private Expression createSimpleExpression(CamelContext camelContext, String function) {
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
        Expression exp = doCreateSimpleExpression(camelContext, function);
        if (type != null) {
            exp = ExpressionBuilder.convertToExpression(exp, type);
        }
        return exp;
    }

    private static final DirectFunctionFactory DIRECT_FACTORY = new DirectFunctionFactory();

    /**
     * A predicate written inside the braces, as an expression that answers whether it matches; null when the text is
     * not a predicate but a plain function (CAMEL-24921).
     * <p/>
     * An operator counts only when whitespace surrounds it outside quotes, so {@code ${header.Content-Length}} and
     * {@code ${date:now:yyyy-MM-dd}} are names, not arithmetic.
     */
    private Expression createPredicateExpression(CamelContext camelContext, String function) {
        if (SimpleSyntaxHints.operatorsOutside(function) == null) {
            return null;
        }
        String text = SimpleSyntaxHints.wrapFunctions(function);
        final Predicate predicate;
        try {
            predicate = new SimplePredicateParser(camelContext, text, true, skipFileFunctions, null).parsePredicate();
        } catch (SimpleParserException e) {
            // not a predicate after all: say what is wrong with it, at the place it went wrong
            throw new SimpleParserException(e.getMessage(), token.getIndex());
        }
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                boolean matches = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, exchange, matches);
            }

            @Override
            public String toString() {
                return text;
            }
        };
    }

    private Expression doCreateSimpleExpression(CamelContext camelContext, String function) {
        // ${body != null && body.size() > 0}: the braces hold a predicate, which is what they hold in EL,
        // Groovy and a JavaScript template, so read it as one (CAMEL-24921)
        Expression predicate = createPredicateExpression(camelContext, function);
        if (predicate != null) {
            return predicate;
        }
        // return the function directly if we can create function without analyzing the prefix
        Expression answer = DIRECT_FACTORY.createFunction(camelContext, function, token.getIndex());
        if (answer != null) {
            return answer;
        }

        // file: prefix
        String remainder = ifStartsWithReturnRemainder("file:", function);
        if (remainder != null) {
            Expression fileExpression;
            if (skipFileFunctions) {
                // do not create file expressions but keep the function as-is as a constant value
                fileExpression = ExpressionBuilder.constantExpression("${" + function + "}");
            } else {
                fileExpression = createSimpleFileExpression(remainder);
            }
            if (fileExpression != null) {
                return fileExpression;
            }
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

        // it may be a custom function registered without the function(...) wrapper
        String name = StringHelper.before(function, "(", function);
        if (PluginHelper.getSimpleFunctionRegistry(camelContext).getFunction(name) != null) {
            String after = StringHelper.after(function, "(");
            if (after == null || after.equals(")")) {
                function = "function(" + name + ")";
            } else {
                function = "function(" + name + "," + after;
            }
            Expression exp = SimpleFunctionDispatcher.tryCreateBuiltIn(camelContext, function, token.getIndex());
            if (exp != null) {
                return exp;
            }
        }

        String hint = SimpleSyntaxHints.unknownFunction(function);
        throw new SimpleParserException(
                "Unknown function: " + function + (hint != null ? " (" + hint + ")" : ""),
                token.getIndex());
    }

    private Expression createSimpleFileExpression(String remainder) {
        if (ObjectHelper.equal(remainder, "name")) {
            return FileExpressionBuilder.fileNameExpression();
        } else if (ObjectHelper.equal(remainder, "name.noext")) {
            return FileExpressionBuilder.fileNameNoExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "name.noext.single")) {
            return FileExpressionBuilder.fileNameNoExtensionSingleExpression();
        } else if (ObjectHelper.equal(remainder, "name.ext") || ObjectHelper.equal(remainder, "ext")) {
            return FileExpressionBuilder.fileExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "name.ext.single")) {
            return FileExpressionBuilder.fileExtensionSingleExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname")) {
            return FileExpressionBuilder.fileOnlyNameExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname.noext")) {
            return FileExpressionBuilder.fileOnlyNameNoExtensionExpression();
        } else if (ObjectHelper.equal(remainder, "onlyname.noext.single")) {
            return FileExpressionBuilder.fileOnlyNameNoExtensionSingleExpression();
        } else if (ObjectHelper.equal(remainder, "parent")) {
            return FileExpressionBuilder.fileParentExpression();
        } else if (ObjectHelper.equal(remainder, "path")) {
            return FileExpressionBuilder.filePathExpression();
        } else if (ObjectHelper.equal(remainder, "absolute")) {
            return FileExpressionBuilder.fileAbsoluteExpression();
        } else if (ObjectHelper.equal(remainder, "absolute.path")) {
            return FileExpressionBuilder.fileAbsolutePathExpression();
        } else if (ObjectHelper.equal(remainder, "length") || ObjectHelper.equal(remainder, "size")) {
            return FileExpressionBuilder.fileSizeExpression();
        } else if (ObjectHelper.equal(remainder, "modified")) {
            return FileExpressionBuilder.fileLastModifiedExpression();
        }
        throw new SimpleParserException(
                "Unknown file language syntax: " + remainder + " (the file: functions describe the file being consumed:"
                                        + " ${file:name}, ${file:size}, ${file:parent}, ${file:absolute.path};"
                                        + " they do not read a file. To read a file into the body use the poll"
                                        + " EIP with a file: endpoint)",
                token.getIndex());
    }

    @Deprecated(since = "4.21")
    public static String ifStartsWithReturnRemainder(String prefix, String text) {
        return SimpleFunctionHelper.ifStartsWithReturnRemainder(prefix, text);
    }

}
