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
import org.apache.camel.language.simple.ast.InitExpression;
import org.apache.camel.language.simple.ast.LiteralNode;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.apache.camel.language.simple.types.TokenType;
import org.apache.camel.util.StringHelper;

class SimpleInitBlockParser extends SimpleExpressionParser {

    private final Set<String> initKeys = new LinkedHashSet<>();

    public SimpleInitBlockParser(CamelContext camelContext, String expression, boolean allowEscape, boolean skipFileFunctions,
                                 Map<String, Expression> cacheExpression) {
        super(camelContext,
              StringHelper.between(expression, SimpleInitBlockTokenizer.INIT_START, SimpleInitBlockTokenizer.INIT_END),
              allowEscape, skipFileFunctions, cacheExpression, new SimpleInitBlockTokenizer());
    }

    public Set<String> getInitKeys() {
        return initKeys;
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

    protected List<SimpleNode> parseInitTokens() {
        clear();
        initKeys.clear();

        // parse the expression using the following grammar
        nextToken();
        while (!token.getType().isEol()) {
            // an expression supports just template (eg text), functions, unary, or other operator
            initText();
            templateText();
            functionText();
            unaryOperator();
            otherOperator();
            nextToken();
        }

        // now after parsing, we need a bit of work to do, to make it easier to turn the tokens
        // into an ast, and then from the ast, to Camel expression(s).
        // hence why there are a number of tasks going on below to accomplish this

        // remove any ignorable white space tokens
        removeIgnorableWhiteSpaceTokens();
        // prepare for any local variables to use $$ syntax in simple expression

        // turn the tokens into the ast model
        parseAndCreateAstModel();
        // compact and stack blocks (eg function start/end)
        prepareBlocks();
        // compact and stack init blocks
        prepareInitBlocks();
        // compact and stack unary operators
        prepareUnaryExpressions();
        // compact and stack other expressions
        prepareOtherExpressions();

        return nodes;
    }

    // special for init blocks that are only available in the top
    // $$name := <function>
    // $$name2 := <function>
    protected boolean initText() {
        // turn on init mode so the parser can find the beginning of the init variable
        getTokenizer().setAcceptInitTokens(true);
        while (!token.getType().isInitVariable() && !token.getType().isEol()) {
            // skip until we find init variable
            nextToken();
        }
        if (accept(TokenType.initVariable)) {
            while (!token.getType().isWhitespace() && !token.getType().isEol()) {
                nextToken();
            }
            expect(TokenType.whiteSpace);
            nextToken();
            expect(TokenType.initOperator);
            nextToken();
            expect(TokenType.whiteSpace);
            // turn off init mode so the parser does not detect init variables inside functions or literal text
            // because they may also use := or $$ symbols
            getTokenizer().setAcceptInitTokens(false);
            return true;
        }

        return false;
    }

    protected void prepareInitBlocks() {
        List<SimpleNode> answer = new ArrayList<>();
        for (int i = 1; i < nodes.size() - 2; i++) {
            SimpleNode token = nodes.get(i);
            if (token instanceof InitExpression ie) {
                SimpleNode prev = nodes.get(i - 1);
                SimpleNode next = nodes.get(i + 1);
                ie.acceptLeftNode(prev);
                ie.acceptRightNode(next);
                answer.add(ie);

                // remember which init variables we have created
                if (prev instanceof LiteralNode ln) {
                    String key = StringHelper.after(ln.getText(), "$$");
                    if (key != null) {
                        key = key.trim();
                        initKeys.add(key);
                    }
                }
            }
        }
        nodes.clear();
        nodes.addAll(answer);
    }

}
