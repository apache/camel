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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.SimpleExpressionParser;
import org.apache.camel.language.simple.SimplePredicateParser;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.util.StringHelper;

/**
 * Starts a function
 */
public class SimpleFunctionStart extends BaseSimpleNode implements BlockStart {

    // use caches to avoid re-parsing the same expressions over and over again
    private final Map<String, Expression> cacheExpression;
    private final CompositeNodes block;
    private final boolean skipFileFunctions;

    public SimpleFunctionStart(SimpleToken token, Map<String, Expression> cacheExpression, boolean skipFileFunctions) {
        super(token);
        this.block = new CompositeNodes(token);
        this.cacheExpression = cacheExpression;
        this.skipFileFunctions = skipFileFunctions;
    }

    public CompositeNodes getBlock() {
        return block;
    }

    public boolean lazyEval(SimpleNode child) {
        String text = child.toString();
        // don't lazy evaluate nested type references as they are static
        return !text.startsWith("${type:");
    }

    @Override
    public String toString() {
        // output a nice toString, so it makes debugging easier, so we can see the entire block
        return "${" + block + "}";
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) {
        // Check if the block contains ternary expression nodes - if so, process them first
        if (containsTernaryExpressionNodes()) {
            return doCreateTernaryExpression(camelContext, expression);
        }

        // a function can either be a simple literal function, or contain nested functions
        if (block.getChildren().size() == 1 && block.getChildren().get(0) instanceof LiteralNode) {
            return doCreateLiteralExpression(camelContext, expression);
        } else {
            return doCreateCompositeExpression(camelContext, expression);
        }
    }

    /**
     * Check if the block contains TernaryExpression nodes (? or : operators)
     */
    private boolean containsTernaryExpressionNodes() {
        for (SimpleNode child : block.getChildren()) {
            if (child instanceof TernaryExpression) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create an expression from a block that contains ternary expression nodes. This handles the pattern: condition ?
     * trueValue : falseValue
     */
    private Expression doCreateTernaryExpression(CamelContext camelContext, String expression) {
        List<SimpleNode> children = block.getChildren();

        // Find the ? operator
        int questionIdx = -1;
        for (int i = 0; i < children.size(); i++) {
            SimpleNode child = children.get(i);
            if (child instanceof TernaryExpression && "?".equals(child.getToken().getText())) {
                questionIdx = i;
                break;
            }
        }

        if (questionIdx < 0) {
            // No ? found, fall back to composite expression
            return doCreateCompositeExpression(camelContext, expression);
        }

        // Find the : operator after the ?
        int colonIdx = -1;
        for (int i = questionIdx + 1; i < children.size(); i++) {
            SimpleNode child = children.get(i);
            if (child instanceof TernaryExpression && ":".equals(child.getToken().getText())) {
                colonIdx = i;
                break;
            }
        }

        if (colonIdx < 0) {
            throw new SimpleParserException(
                    "Ternary operator ? must be followed by :", children.get(questionIdx).getToken().getIndex());
        }

        // Extract condition, true value, and false value
        List<SimpleNode> conditionNodes = children.subList(0, questionIdx);
        List<SimpleNode> trueNodes = children.subList(questionIdx + 1, colonIdx);
        List<SimpleNode> falseNodes = children.subList(colonIdx + 1, children.size());

        // Build the condition text
        String conditionText = buildTextFromNodes(conditionNodes, camelContext);
        String trueText = buildTextFromNodes(trueNodes, camelContext);
        String falseText = buildTextFromNodes(falseNodes, camelContext);

        // Wrap the condition for predicate parsing
        String predicateText = wrapFunctionsInCondition(conditionText.trim());

        // Parse the condition as a predicate
        SimplePredicateParser predicateParser
                = new SimplePredicateParser(camelContext, predicateText, true, skipFileFunctions, null);
        final Predicate conditionPredicate = predicateParser.parsePredicate();

        // Parse the true and false values as expressions
        final Expression trueExp = parseValueExpression(camelContext, trueText.trim());
        final Expression falseExp = parseValueExpression(camelContext, falseText.trim());

        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                if (conditionPredicate.matches(exchange)) {
                    return trueExp.evaluate(exchange, type);
                } else {
                    return falseExp.evaluate(exchange, type);
                }
            }

            @Override
            public String toString() {
                return conditionText + " ? " + trueText + " : " + falseText;
            }
        };
    }

    /**
     * Build a text string from a list of nodes
     */
    private String buildTextFromNodes(List<SimpleNode> nodes, CamelContext camelContext) {
        StringBuilder sb = new StringBuilder();
        for (SimpleNode node : nodes) {
            if (node instanceof LiteralNode literal) {
                sb.append(literal.getText());
            } else if (node instanceof SingleQuoteStart || node instanceof DoubleQuoteStart) {
                sb.append(node.toString());
            } else if (node instanceof SimpleFunctionStart) {
                sb.append(node.toString());
            } else if (node instanceof TernaryExpression) {
                // Include the ternary operator (? or :) in the text
                sb.append(node.getToken().getText());
            }
        }
        return sb.toString();
    }

    private Expression doCreateLiteralExpression(CamelContext camelContext, String expression) {
        LiteralNode literal = (LiteralNode) block.getChildren().get(0);
        String text = literal.getText();

        // Check if this is a ternary expression
        Expression ternaryExp = tryParseTernaryExpression(camelContext, text);
        if (ternaryExp != null) {
            return ternaryExp;
        }

        SimpleFunctionExpression function = new SimpleFunctionExpression(this.getToken(), cacheExpression, skipFileFunctions);
        function.addText(text);
        return function.createExpression(camelContext, expression);
    }

    /**
     * Try to parse the text as a ternary expression. Returns null if the text is not a ternary expression.
     */
    private Expression tryParseTernaryExpression(CamelContext camelContext, String text) {
        // Find the ? operator (not inside quotes or nested ${})
        int questionIdx = findTernaryOperator(text, '?');
        if (questionIdx < 0) {
            return null;
        }

        // Find the : operator after the ?
        int colonIdx = findTernaryOperator(text.substring(questionIdx + 1), ':');
        if (colonIdx < 0) {
            return null;
        }
        colonIdx = questionIdx + 1 + colonIdx;

        // Extract the three parts
        String conditionText = text.substring(0, questionIdx).trim();
        String trueText = text.substring(questionIdx + 1, colonIdx).trim();
        String falseText = text.substring(colonIdx + 1).trim();

        if (conditionText.isEmpty() || trueText.isEmpty() || falseText.isEmpty()) {
            return null;
        }

        // The condition text is like "header.foo > 0" but the predicate parser expects
        // "${header.foo} > 0". We need to transform the condition to wrap function references
        // with ${}. A simple approach: if there's no ${} in the condition, wrap the left side.
        String predicateText = wrapFunctionsInCondition(conditionText);

        // Parse the condition as a predicate - use null for cache to avoid caching issues
        SimplePredicateParser predicateParser
                = new SimplePredicateParser(camelContext, predicateText, true, skipFileFunctions, null);
        final Predicate conditionPredicate = predicateParser.parsePredicate();

        // Parse the true and false values as expressions
        final Expression trueExp = parseValueExpression(camelContext, trueText);
        final Expression falseExp = parseValueExpression(camelContext, falseText);

        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                if (conditionPredicate.matches(exchange)) {
                    return trueExp.evaluate(exchange, type);
                } else {
                    return falseExp.evaluate(exchange, type);
                }
            }

            @Override
            public String toString() {
                return conditionText + " ? " + trueText + " : " + falseText;
            }
        };
    }

    /**
     * Parse a value as an expression. Handles quoted literals, functions, ternary expressions, and null.
     */
    private Expression parseValueExpression(CamelContext camelContext, String text) {
        // Handle quoted strings
        if ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\""))) {
            final String value = text.substring(1, text.length() - 1);
            return new Expression() {
                @Override
                public <T> T evaluate(Exchange exchange, Class<T> type) {
                    return exchange.getContext().getTypeConverter().convertTo(type, value);
                }

                @Override
                public String toString() {
                    return value;
                }
            };
        }

        // Handle null
        if ("null".equals(text) || "${null}".equals(text)) {
            return new Expression() {
                @Override
                public <T> T evaluate(Exchange exchange, Class<T> type) {
                    return null;
                }

                @Override
                public String toString() {
                    return "null";
                }
            };
        }

        // Check if this is a nested ternary expression (contains ? and :)
        Expression ternaryExp = tryParseTernaryExpression(camelContext, text);
        if (ternaryExp != null) {
            return ternaryExp;
        }

        // Handle function expressions (may or may not have ${})
        String expText = text;
        if (!text.startsWith("${")) {
            expText = "${" + text + "}";
        }
        // use null for cache to avoid caching issues with ternary expressions
        SimpleExpressionParser parser
                = new SimpleExpressionParser(camelContext, expText, true, skipFileFunctions, null);
        return parser.parseExpression();
    }

    /**
     * Wrap function references in the condition text with ${}. For example: "header.foo > 0" becomes "${header.foo} >
     * 0"
     */
    private String wrapFunctionsInCondition(String conditionText) {
        // If the condition already has ${}, assume it's properly formatted
        if (conditionText.contains("${")) {
            return conditionText;
        }

        // Find the operator in the condition
        String[] operators = {
                " >= ", " <= ", " > ", " < ", " == ", " != ", " =~ ", " !=~ ",
                " contains ", " !contains ", " ~~ ", " !~~ ", " regex ", " !regex ",
                " in ", " !in ", " is ", " !is ", " range ", " !range ",
                " startsWith ", " !startsWith ", " endsWith ", " !endsWith " };

        for (String op : operators) {
            int opIdx = conditionText.indexOf(op);
            if (opIdx > 0) {
                String leftSide = conditionText.substring(0, opIdx).trim();
                String rightSide = conditionText.substring(opIdx + op.length()).trim();

                // Wrap the left side with ${} if it looks like a function reference
                if (!leftSide.startsWith("${") && !leftSide.startsWith("'") && !leftSide.startsWith("\"")
                        && !isNumeric(leftSide) && !"true".equalsIgnoreCase(leftSide)
                        && !"false".equalsIgnoreCase(leftSide) && !"null".equalsIgnoreCase(leftSide)) {
                    leftSide = "${" + leftSide + "}";
                }

                return leftSide + op + rightSide;
            }
        }

        // No operator found, return as-is
        return conditionText;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Find the index of the ternary operator character, skipping nested ${}, quotes, etc.
     */
    private int findTernaryOperator(String text, char operator) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    depth++;
                    i++; // skip the {
                    continue;
                }
                if (c == '}' && depth > 0) {
                    depth--;
                    continue;
                }
                if (c == '\'' && depth == 0) {
                    inSingleQuote = true;
                    continue;
                }
                if (c == '"' && depth == 0) {
                    inDoubleQuote = true;
                    continue;
                }
                if (c == operator && depth == 0) {
                    return i;
                }
            } else if (inSingleQuote && c == '\'') {
                inSingleQuote = false;
            } else if (inDoubleQuote && c == '"') {
                inDoubleQuote = false;
            }
        }
        return -1;
    }

    private Expression doCreateCompositeExpression(CamelContext camelContext, String expression) {
        final SimpleToken token = getToken();
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                StringBuilder sb = new StringBuilder(256);
                boolean quoteEmbeddedFunctions = false;

                // we need to concat the block so we have the expression
                for (SimpleNode child : block.getChildren()) {
                    // whether a nested function should be lazy evaluated or not
                    boolean lazy = true;
                    if (child instanceof SimpleFunctionStart simpleFunctionStart) {
                        lazy = simpleFunctionStart.lazyEval(child);
                    }
                    if (child instanceof LiteralNode literal) {
                        String text = literal.getText();
                        sb.append(text);
                        quoteEmbeddedFunctions |= literal.quoteEmbeddedNodes();
                        // if its quoted literal then embed that as text
                    } else if (!lazy || child instanceof SingleQuoteStart || child instanceof DoubleQuoteStart) {
                        try {
                            // pass in null when we evaluate the nested expressions
                            Expression nested = child.createExpression(camelContext, null);
                            String text = nested.evaluate(exchange, String.class);
                            if (text != null) {
                                if (quoteEmbeddedFunctions && !StringHelper.isQuoted(text)) {
                                    sb.append("'").append(text).append("'");
                                } else {
                                    sb.append(text);
                                }
                            }
                        } catch (SimpleParserException e) {
                            // must rethrow parser exception as illegal syntax with details about the location
                            throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
                        }
                        // if its an inlined function then embed that function as text so it can be evaluated lazy
                    } else if (child instanceof SimpleFunctionStart) {
                        sb.append(child);
                    }
                }

                // we have now concat the block as a String which contains the function expression
                // which we then need to evaluate as a function
                String exp = sb.toString();

                // Check if this is a ternary expression
                Expression ternaryExp = tryParseTernaryExpression(camelContext, exp);
                if (ternaryExp != null) {
                    return ternaryExp.evaluate(exchange, type);
                }

                SimpleFunctionExpression function = new SimpleFunctionExpression(token, cacheExpression, skipFileFunctions);
                function.addText(exp);
                try {
                    return function.createExpression(camelContext, exp).evaluate(exchange, type);
                } catch (SimpleParserException e) {
                    // must rethrow parser exception as illegal syntax with details about the location
                    throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
                }
            }

            @Override
            public String toString() {
                return expression;
            }
        };
    }

    @Override
    public boolean acceptAndAddNode(SimpleNode node) {
        // only accept literals, quotes, ternary expressions, or embedded functions
        if (node instanceof LiteralNode || node instanceof SimpleFunctionStart
                || node instanceof SingleQuoteStart || node instanceof DoubleQuoteStart
                || node instanceof TernaryExpression) {
            block.addChild(node);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String createCode(CamelContext camelContext, String expression) throws SimpleParserException {
        String answer;
        // a function can either be a simple literal function or contain nested functions
        if (block.getChildren().size() == 1 && block.getChildren().get(0) instanceof LiteralNode) {
            answer = doCreateLiteralCode(camelContext, expression);
        } else {
            answer = doCreateCompositeCode(camelContext, expression);
        }
        return answer;
    }

    private String doCreateLiteralCode(CamelContext camelContext, String expression) {
        SimpleFunctionExpression function = new SimpleFunctionExpression(this.getToken(), cacheExpression, skipFileFunctions);
        LiteralNode literal = (LiteralNode) block.getChildren().get(0);
        function.addText(literal.getText());
        return function.createCode(camelContext, expression);
    }

    private String doCreateCompositeCode(CamelContext camelContext, String expression) {
        StringBuilder sb = new StringBuilder(256);
        boolean quoteEmbeddedFunctions = false;

        // we need to concat the block, so we have the expression
        for (SimpleNode child : block.getChildren()) {
            if (child instanceof LiteralNode literal) {
                String text = literal.getText();
                sb.append(text);
                quoteEmbeddedFunctions |= literal.quoteEmbeddedNodes();
                // if its quoted literal then embed that as text
            } else if (child instanceof SingleQuoteStart || child instanceof DoubleQuoteStart) {
                try {
                    // pass in null when we evaluate the nested expressions
                    String text = child.createCode(camelContext, null);
                    if (text != null) {
                        if (quoteEmbeddedFunctions && !StringHelper.isQuoted(text)) {
                            sb.append("'").append(text).append("'");
                        } else {
                            sb.append(text);
                        }
                    }
                } catch (SimpleParserException e) {
                    // must rethrow parser exception as illegal syntax with details about the location
                    throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
                }
            } else if (child instanceof SimpleFunctionStart) {
                // inlined function
                String inlined = child.createCode(camelContext, expression);
                sb.append(inlined);
            }
        }

        // we have now concat the block as a String which contains inlined functions parsed
        // so now we should reparse as a single function
        String exp = sb.toString();
        SimpleFunctionExpression function = new SimpleFunctionExpression(token, cacheExpression, skipFileFunctions);
        function.addText(exp);
        try {
            return function.createCode(camelContext, exp);
        } catch (SimpleParserException e) {
            // must rethrow parser exception as illegal syntax with details about the location
            throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
        }
    }

}
