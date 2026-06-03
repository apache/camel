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
import org.apache.camel.Expression;
import org.apache.camel.language.simple.BaseSimpleParser;
import org.apache.camel.language.simple.FileExpressionBuilder;
import org.apache.camel.language.simple.SimpleFunctionDispatcher;
import org.apache.camel.language.simple.SimpleFunctionHelper;
import org.apache.camel.language.simple.SimplePredicateParser;
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
        String cacheKey = FUNCTION_CACHE_KEY_PREFIX + function;

        Expression answer = cacheExpression != null ? cacheExpression.get(cacheKey) : null;
        if (answer == null) {
            answer = createSimpleExpression(camelContext, function, true);
            if (answer != null) {
                answer.init(camelContext);
            }
            if (cacheExpression != null && answer != null) {
                cacheExpression.put(cacheKey, answer);
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

    // Prefix that separates function-level cache entries from the top-level expression cache entries
    // written by SimpleLanguage (which uses "@SIMPLE@" + fullExpression). The two keyspaces share the
    // same LRUCache instance, so distinct prefixes are needed to prevent accidental collisions.
    private static final String FUNCTION_CACHE_KEY_PREFIX = "@FUNC@";

    private static final DirectFunctionFactory DIRECT_FACTORY = new DirectFunctionFactory();

    private Expression doCreateSimpleExpression(CamelContext camelContext, String function, boolean strict) {
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
                fileExpression = createSimpleFileExpression(remainder, strict);
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

        if (strict) {
            throw new SimpleParserException("Unknown function: " + function, token.getIndex());
        } else {
            return null;
        }
    }

    private Expression createSimpleFileExpression(String remainder, boolean strict) {
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

        // file: prefix
        String remainder = ifStartsWithReturnRemainder("file:", function);
        if (remainder != null) {
            return createCodeFileExpression(remainder);
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
