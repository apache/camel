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
package org.apache.camel.language.simple;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.ast.CompositeNodes;
import org.apache.camel.language.simple.ast.InitBlockExpression;
import org.apache.camel.language.simple.ast.LiteralExpression;
import org.apache.camel.language.simple.ast.LiteralNode;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.apache.camel.language.simple.types.InitOperatorType;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.TokenType;
import org.apache.camel.util.StringHelper;

class SimpleInitBlockParser extends SimpleExpressionParser {

    private final Set<String> initKeys = new LinkedHashSet<>();
    private final Set<String> initFunctions = new LinkedHashSet<>();

    public SimpleInitBlockParser(CamelContext camelContext, String expression, boolean allowEscape, boolean skipFileFunctions,
                                 Map<String, Expression> cacheExpression) {
        super(camelContext,
              StringHelper.between(expression, SimpleInitBlockTokenizer.INIT_START, SimpleInitBlockTokenizer.INIT_END),
              allowEscape, skipFileFunctions, cacheExpression, new SimpleInitBlockTokenizer());
    }

    public Set<String> getInitKeys() {
        return initKeys;
    }

    public Set<String> getInitFunctions() {
        return initFunctions;
    }

    protected SimpleInitBlockTokenizer getTokenizer() {
        return (SimpleInitBlockTokenizer) tokenizer;
    }

    @Override
    public Expression parseExpression() {
        // parse init block
        parseInitTokens();
        return doParseInitExpression();
    }

    @Override
    public String parseCode() {
        throw new UnsupportedOperationException("Using init blocks with csimple is not supported");
    }

    /**
     * Second step parsing into code
     */
    @Override
    protected String doParseCode() {
        StringBuilder sb = new StringBuilder(256);
        for (SimpleNode node : nodes) {
            String exp = node.createCode(camelContext, expression);
            if (exp != null) {
                parseLiteralNode(sb, node, exp);
            }
        }

        String code = sb.toString();
        code = code.replace(BaseSimpleParser.CODE_START, "");
        code = code.replace(BaseSimpleParser.CODE_END, "");
        return code;
    }

    protected List<SimpleNode> parseInitTokens() {
        clear();
        initKeys.clear();
        initFunctions.clear();

        // parse the expression using the following grammar
        // init statements are variables assigned to functions/operators
        nextToken();
        while (!token.getType().isEol()) {
            initText();
        }

        // now after parsing, we need a bit of work to do, to make it easier to turn the tokens
        // into an ast, and then from the ast, to Camel expression(s).
        // hence why there are a number of tasks going on below to accomplish this

        // remove any ignore and ignorable white space tokens
        removeIgnorableWhiteSpaceTokens();

        // turn the tokens into the ast model
        parseAndCreateAstModel();
        // compact and stack blocks (eg function start/end)
        prepareBlocks(nodes);
        // compact and stack init blocks
        prePrepareInitBlocks(nodes);

        return nodes;
    }

    // special for init blocks that are only available in the top
    // $$name := <function>
    // $$name2 := <function>
    protected boolean initText() {
        // skip until we find a new init variable
        while (!token.getType().isInitVariable() && !token.getType().isEol()) {
            skipToken();
        }
        if (accept(TokenType.initVariable)) {
            tokens.add(token);
            while (!token.getType().isWhitespace() && !token.getType().isEol()) {
                nextToken();
            }
            expect(TokenType.whiteSpace);
            nextToken();
            expect(TokenType.initOperator);
            nextToken();
            expectAndAcceptMore(TokenType.whiteSpace);
            // must either be a function, string literal, a number, or boolean
            expect(TokenType.functionStart, TokenType.singleQuote, TokenType.doubleQuote, TokenType.numericValue,
                    TokenType.booleanValue);

            // accept until we find init function end
            SimpleToken prev = token;
            while (!token.getType().isEol() && !prev.getType().isInitFunctionEnd()) {
                // an init variable supports functions, chain, unary and also whitespace as chain uses that between functions
                nextToken(TokenType.functionStart, TokenType.functionEnd, TokenType.unaryOperator, TokenType.chainOperator,
                        TokenType.otherOperator, TokenType.whiteSpace,
                        TokenType.numericValue, TokenType.booleanValue,
                        TokenType.singleQuote, TokenType.doubleQuote,
                        TokenType.initFunctionEnd, TokenType.eol);
                prev = token;
            }
            if (prev.getType().isInitFunctionEnd()) {
                tokens.remove(prev);
            }
            return true;
        }

        return false;
    }

    protected void prePrepareInitBlocks(List<SimpleNode> nodes) {
        List<SimpleNode> answer = new ArrayList<>();

        for (int i = 1; i < nodes.size() - 1; i++) {
            SimpleNode token = nodes.get(i);
            if (token instanceof InitBlockExpression ie) {
                answer.add(ie);

                SimpleNode prev = nodes.get(i - 1);
                ie.acceptLeftNode(prev);
                // remember which init variables we have created
                if (prev instanceof LiteralNode ln) {
                    String key = StringHelper.after(ln.getText(), "$");
                    if (key != null) {
                        key = key.trim();
                        if (ie.getOperator().equals(InitOperatorType.CHAIN_ASSIGNMENT)) {
                            initFunctions.add(key);
                        } else {
                            initKeys.add(key);
                        }
                    }
                }

                CompositeNodes cn = new CompositeNodes(ie.getToken());
                ie.acceptRightNode(cn);
                int j = i + 1;
                while (j < nodes.size()) {
                    SimpleNode next = nodes.get(j);
                    if (next instanceof InitBlockExpression || next.getToken().getType().isInitVariable()) {
                        break;
                    } else if (!next.getToken().getType().isInitFunctionEnd()) {
                        if (next instanceof LiteralExpression le) {
                            String text = unquote(le.getText());
                            le.replaceText(text);
                        }
                        cn.addChild(next);
                    }
                    j++;
                }

                // prepare the children
                prepareChainExpression(cn.getChildren());
                prepareUnaryExpressions(cn.getChildren());
                prepareOtherExpressions(cn.getChildren());
                // if there are only 1 child then flatten and add directly to right hand side
                if (cn.getChildren().size() == 1) {
                    ie.acceptRightNode(cn.getChildren().get(0));
                }
                i = j;
            }
        }
        nodes.clear();
        nodes.addAll(answer);
    }

    private static String unquote(String text) {
        if (text.startsWith("\"") || text.startsWith("'")) {
            text = text.substring(1);
        }
        if (text.endsWith("\"") || text.endsWith("'")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    @Deprecated
    protected void prepareInitBlocks() {
        List<SimpleNode> answer = new ArrayList<>();
        for (int i = 1; i < nodes.size() - 1; i++) {
            SimpleNode token = nodes.get(i);
            if (token instanceof InitBlockExpression ie) {
                SimpleNode prev = nodes.get(i - 1);
                SimpleNode next = nodes.get(i + 1);
                ie.acceptLeftNode(prev);
                ie.acceptRightNode(next);
                answer.add(ie);

                // remember which init variables we have created
                if (prev instanceof LiteralNode ln) {
                    String key = StringHelper.after(ln.getText(), "$");
                    if (key != null) {
                        key = key.trim();
                        if (ie.getOperator().equals(InitOperatorType.CHAIN_ASSIGNMENT)) {
                            initFunctions.add(key);
                        } else {
                            initKeys.add(key);
                        }
                    }
                }
            }
        }
        nodes.clear();
        nodes.addAll(answer);
    }

}
