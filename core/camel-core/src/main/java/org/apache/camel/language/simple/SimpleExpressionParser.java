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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.ast.LiteralExpression;
import org.apache.camel.language.simple.ast.LiteralNode;
import org.apache.camel.language.simple.ast.SimpleFunctionEnd;
import org.apache.camel.language.simple.ast.SimpleFunctionStart;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.apache.camel.language.simple.ast.UnaryExpression;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.TokenType;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * A parser to parse simple language as a Camel {@link Expression}
 */
public class SimpleExpressionParser extends BaseSimpleParser {

    // use caches to avoid re-parsing the same expressions over and over again
    private Map<String, Expression> cacheExpression;

    public SimpleExpressionParser(String expression, boolean allowEscape,
                                  Map<String, Expression> cacheExpression) {
        super(expression, allowEscape);
        this.cacheExpression = cacheExpression;
    }

    public Expression parseExpression() {
        clear();
        try {
            return doParseExpression();
        } catch (SimpleParserException e) {
            // catch parser exception and turn that into a syntax exceptions
            throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
        } catch (Exception e) {
            // include exception in rethrown exception
            throw new SimpleIllegalSyntaxException(expression, -1, e.getMessage(), e);
        }
    }

    protected Expression doParseExpression() {
        // parse the expression using the following grammar
        nextToken();
        while (!token.getType().isEol()) {
            // an expression supports just template (eg text), functions, or unary operator
            templateText();
            functionText();
            unaryOperator();
            nextToken();
        }

        // now after parsing we need a bit of work to do, to make it easier to turn the tokens
        // into an ast, and then from the ast, to Camel expression(s).
        // hence why there is a number of tasks going on below to accomplish this

        // turn the tokens into the ast model
        parseAndCreateAstModel();
        // compact and stack blocks (eg function start/end)
        prepareBlocks();
        // compact and stack unary operators
        prepareUnaryExpressions();

        // create and return as a Camel expression
        List<Expression> expressions = createExpressions();
        if (expressions.isEmpty()) {
            // return an empty string as response as there was nothing to parse
            return ExpressionBuilder.constantExpression("");
        } else if (expressions.size() == 1) {
            return expressions.get(0);
        } else {
            // concat expressions as evaluating an expression is like a template language
            return ExpressionBuilder.concatExpression(expressions, expression);
        }
    }

    protected void parseAndCreateAstModel() {
        // we loop the tokens and create a sequence of ast nodes

        // counter to keep track of number of functions in the tokens
        AtomicInteger functions = new AtomicInteger();

        LiteralNode imageToken = null;
        for (SimpleToken token : tokens) {
            // break if eol
            if (token.getType().isEol()) {
                break;
            }

            // create a node from the token
            SimpleNode node = createNode(token, functions);
            if (node != null) {
                // a new token was created so the current image token need to be added first
                if (imageToken != null) {
                    nodes.add(imageToken);
                    imageToken = null;
                }
                // and then add the created node
                nodes.add(node);
                // continue to next
                continue;
            }

            // if no token was created then its a character/whitespace/escaped symbol
            // which we need to add together in the same image
            if (imageToken == null) {
                imageToken = new LiteralExpression(token);
            }
            imageToken.addText(token.getText());
        }

        // append any leftover image tokens (when we reached eol)
        if (imageToken != null) {
            nodes.add(imageToken);
        }
    }

    private SimpleNode createNode(SimpleToken token, AtomicInteger functions) {
        // expression only support functions and unary operators
        if (token.getType().isFunctionStart()) {
            // starting a new function
            functions.incrementAndGet();
            return new SimpleFunctionStart(token, cacheExpression);
        } else if (functions.get() > 0 && token.getType().isFunctionEnd()) {
            // there must be a start function already, to let this be a end function
            functions.decrementAndGet();
            return new SimpleFunctionEnd(token);
        } else if (token.getType().isUnary()) {
            // there must be a end function as previous, to let this be a unary function
            if (!nodes.isEmpty() && nodes.get(nodes.size() - 1) instanceof SimpleFunctionEnd) {
                return new UnaryExpression(token);
            }
        }

        // by returning null, we will let the parser determine what to do
        return null;
    }

    private List<Expression> createExpressions() {
        List<Expression> answer = new ArrayList<>();
        for (SimpleNode token : nodes) {
            Expression exp = token.createExpression(expression);
            if (exp != null) {
                answer.add(exp);
            }
        }
        return answer;
    }

    // --------------------------------------------------------------
    // grammar
    // --------------------------------------------------------------

    // the expression parser only understands
    // - template = literal texts with can contain embedded functions
    // - function = simple functions such as ${body} etc
    // - unary operator = operator attached to the left hand side node

    protected void templateText() {
        // for template we accept anything but functions
        while (!token.getType().isFunctionStart() && !token.getType().isFunctionEnd() && !token.getType().isEol()) {
            nextToken();
        }
    }

    protected boolean functionText() {
        if (accept(TokenType.functionStart)) {
            nextToken();
            while (!token.getType().isFunctionEnd() && !token.getType().isEol()) {
                if (token.getType().isFunctionStart()) {
                    // embedded function
                    functionText();
                }
                // we need to loop until we find the ending function quote, an embedded function, or the eol
                nextToken();
            }
            // if its not an embedded function then we expect the end token
            if (!token.getType().isFunctionStart()) {
                expect(TokenType.functionEnd);
            }
            return true;
        }
        return false;
    }

    protected boolean unaryOperator() {
        if (accept(TokenType.unaryOperator)) {
            nextToken();
            // there should be a whitespace after the operator
            expect(TokenType.whiteSpace);
            return true;
        }
        return false;
    }
}
