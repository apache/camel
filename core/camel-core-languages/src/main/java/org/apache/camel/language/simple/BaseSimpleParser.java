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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.ast.Block;
import org.apache.camel.language.simple.ast.BlockEnd;
import org.apache.camel.language.simple.ast.BlockStart;
import org.apache.camel.language.simple.ast.ChainExpression;
import org.apache.camel.language.simple.ast.OtherExpression;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.apache.camel.language.simple.ast.TernaryExpression;
import org.apache.camel.language.simple.ast.UnaryExpression;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.SimpleTokenType;
import org.apache.camel.language.simple.types.TokenType;

/**
 * Base class for Simple language parser.
 * <p/>
 * This parser is based on the principles of a <a href="http://en.wikipedia.org/wiki/Recursive_descent_parser">recursive
 * descent parser</a>.
 */
public abstract class BaseSimpleParser {

    /**
     * Marker used for start of generated code for csimple
     */
    public static final String CODE_START = "\"@@code[[";

    /**
     * Marker used for end of generated code for csimple
     */
    public static final String CODE_END = "]]code@@\"";

    protected final CamelContext camelContext;
    protected String expression;
    protected final SimpleTokenizer tokenizer;
    // regular simple expression
    protected final List<SimpleToken> tokens = new ArrayList<>();
    protected final List<SimpleNode> nodes = new ArrayList<>();
    // tokenizer state
    protected SimpleToken token;
    protected int previousIndex;
    protected int index;
    protected final boolean allowEscape;

    protected BaseSimpleParser(CamelContext camelContext, String expression, boolean allowEscape) {
        this(camelContext, expression, allowEscape, new SimpleTokenizer());
    }

    public BaseSimpleParser(CamelContext camelContext, String expression, boolean allowEscape, SimpleTokenizer tokenizer) {
        this.camelContext = camelContext;
        this.expression = expression;
        this.allowEscape = allowEscape;
        this.tokenizer = tokenizer;
    }

    /**
     * Advances the parser position to the next known {@link SimpleToken} in the input.
     */
    protected void nextToken() {
        if (index < expression.length()) {
            SimpleToken next = tokenizer.nextToken(expression, index, allowEscape);
            // add token
            tokens.add(next);
            token = next;
            // position index after the token
            previousIndex = index;
            index += next.getLength();
        } else {
            // end of tokens
            token = new SimpleToken(new SimpleTokenType(TokenType.eol, null), index);
        }
    }

    protected void skipToken() {
        if (index < expression.length()) {
            SimpleToken next = tokenizer.nextToken(expression, index, allowEscape);
            token = next;
            // position index after the token
            previousIndex = index;
            index += next.getLength();
        } else {
            // end of tokens
            token = new SimpleToken(new SimpleTokenType(TokenType.eol, null), index);
        }
    }

    /**
     * Advances the parser position to the next known {@link SimpleToken} in the input.
     *
     * @param filter filter for accepted token types
     */
    protected void nextToken(TokenType... filter) {
        if (index < expression.length()) {
            SimpleToken next = tokenizer.nextToken(expression, index, allowEscape, filter);
            // add token
            tokens.add(next);
            token = next;
            // position index after the token
            previousIndex = index;
            index += next.getLength();
        } else {
            // end of tokens
            token = new SimpleToken(new SimpleTokenType(TokenType.eol, null), index);
        }
    }

    /**
     * Clears the parser state, which means it can be used for parsing a new input.
     */
    protected void clear() {
        token = null;
        previousIndex = 0;
        index = 0;
        tokens.clear();
        nodes.clear();
    }

    /**
     * Prepares blocks, such as functions, single or double-quoted texts.
     * <p/>
     * This process prepares the {@link Block}s in the AST. This is done by linking child {@link SimpleNode nodes} which
     * are within the start and end of the blocks, as child to the given block. This is done to have the AST graph
     * updated and prepared properly.
     * <p/>
     * So when the AST node is later used to create the {@link org.apache.camel.Predicate}s or
     * {@link org.apache.camel.Expression}s to be used by Camel then the AST graph has a linked and prepared graph of
     * nodes which represent the input expression.
     */
    protected void prepareBlocks(List<SimpleNode> nodes) {
        List<SimpleNode> answer = new ArrayList<>();
        Deque<Block> stack = new ArrayDeque<>();

        for (SimpleNode token : nodes) {
            if (token instanceof BlockStart blockStart) {
                // a new block is started, so push on the stack
                stack.push(blockStart);
            } else if (token instanceof BlockEnd) {
                // end block is just an abstract mode, so we should not add it
                if (stack.isEmpty()) {
                    throw new SimpleParserException(
                            token.getToken().getType().getType() + " has no matching start token", token.getToken().getIndex());
                }

                Block top = stack.pop();
                // if there is a block on the stack then it should accept the child token
                acceptOrAdd(answer, stack, top);
            } else {
                // if there is a block on the stack then it should accept the child token
                acceptOrAdd(answer, stack, token);
            }
        }

        // replace nodes from the stack
        nodes.clear();
        nodes.addAll(answer);
    }

    private static void acceptOrAdd(List<SimpleNode> answer, Deque<Block> stack, SimpleNode token) {
        Block block = stack.isEmpty() ? null : stack.peek();
        if (block != null) {
            if (!block.acceptAndAddNode(token)) {
                throw new SimpleParserException(
                        block.getToken().getType() + " cannot accept " + token.getToken().getType(),
                        token.getToken().getIndex());
            }
        } else {
            // no block, so add to answer
            answer.add(token);
        }
    }

    /**
     * Prepares unary expressions.
     * <p/>
     * This process prepares the unary expressions in the AST. This is done by linking the unary operator with the left
     * hand side node, to have the AST graph updated and prepared properly.
     * <p/>
     * So when the AST node is later used to create the {@link org.apache.camel.Predicate}s or
     * {@link org.apache.camel.Expression}s to be used by Camel then the AST graph has a linked and prepared graph of
     * nodes which represent the input expression.
     */
    protected void prepareUnaryExpressions(List<SimpleNode> nodes) {
        Deque<SimpleNode> stack = new ArrayDeque<>();

        for (SimpleNode node : nodes) {
            if (node instanceof UnaryExpression token) {
                // remember the logical operator
                String operator = token.getOperator().toString();

                SimpleNode previous = stack.isEmpty() ? null : stack.pop();
                if (previous == null) {
                    throw new SimpleParserException(
                            "Unary operator " + operator + " has no left hand side token", token.getToken().getIndex());
                } else {
                    token.acceptLeft(previous);
                }
            }
            stack.push(node);
        }

        // replace nodes from the stack
        nodes.clear();
        nodes.addAll(stack);
        // must reverse as it was added from a stack that is reverse
        Collections.reverse(nodes);
    }

    /**
     * Prepares chain expressions.
     * <p/>
     * This process prepares the other expressions in the AST. This is done by linking the other operator with both the
     * right and left hand side nodes, to have the AST graph updated and prepared properly.
     * <p/>
     * So when the AST node is later used to create the {@link Predicate}s to be used by Camel then the AST graph has a
     * linked and prepared graph of nodes which represent the input expression.
     */
    protected void prepareChainExpression(List<SimpleNode> nodes) {
        Deque<SimpleNode> stack = new ArrayDeque<>();

        SimpleNode left = null;
        for (int i = 0; i < nodes.size(); i++) {
            if (left == null) {
                left = i > 0 ? nodes.get(i - 1) : null;
            }
            SimpleNode token = nodes.get(i);
            SimpleNode right = i < nodes.size() - 1 ? nodes.get(i + 1) : null;

            if (token instanceof ChainExpression chain) {
                // remember the chain operator
                String operator = chain.getOperator().toString();

                if (left == null) {
                    throw new SimpleParserException(
                            "Chain operator " + operator + " has no left hand side token", token.getToken().getIndex());
                }
                if (right == null) {
                    throw new SimpleParserException(
                            "Chain operator " + operator + " has no right hand side token", token.getToken().getIndex());
                }

                if (left instanceof ChainExpression chainLeft) {
                    // append to existing chain on right hand side
                    chainLeft.acceptRightNode(right);
                } else {
                    if (!chain.acceptLeftNode(left)) {
                        throw new SimpleParserException(
                                "Chain operator " + operator + " does not support left hand side token " + left.getToken(),
                                token.getToken().getIndex());
                    }
                    if (!chain.acceptRightNode(right)) {
                        throw new SimpleParserException(
                                "Chain operator " + operator + " does not support right hand side token " + right.getToken(),
                                token.getToken().getIndex());
                    }

                    // pop previous as we need to replace it with this other operator
                    stack.pop();
                    stack.push(token);
                    // this token is now the left for the next loop
                    left = token;
                }

                // advantage after the right hand side
                i++;
            } else {
                // clear left
                left = null;
                stack.push(token);
            }
        }

        nodes.clear();
        nodes.addAll(stack);
        // must reverse as it was added from a stack that is reverse
        Collections.reverse(nodes);
    }

    protected void prepareOtherExpressions(List<SimpleNode> nodes) {
        Deque<SimpleNode> stack = new ArrayDeque<>();

        SimpleNode left = null;
        for (int i = 0; i < nodes.size(); i++) {
            if (left == null) {
                left = i > 0 ? nodes.get(i - 1) : null;
            }
            SimpleNode token = nodes.get(i);
            SimpleNode right = i < nodes.size() - 1 ? nodes.get(i + 1) : null;

            if (token instanceof OtherExpression other) {
                // remember the other operator
                String operator = other.getOperator().toString();

                if (left == null) {
                    throw new SimpleParserException(
                            "Other operator " + operator + " has no left hand side token", token.getToken().getIndex());
                }
                if (!other.acceptLeftNode(left)) {
                    throw new SimpleParserException(
                            "Other operator " + operator + " does not support left hand side token " + left.getToken(),
                            token.getToken().getIndex());
                }
                if (right == null) {
                    throw new SimpleParserException(
                            "Other operator " + operator + " has no right hand side token", token.getToken().getIndex());
                }
                if (!other.acceptRightNode(right)) {
                    throw new SimpleParserException(
                            "Other operator " + operator + " does not support right hand side token " + right.getToken(),
                            token.getToken().getIndex());
                }

                // pop previous as we need to replace it with this other operator
                stack.pop();
                stack.push(token);
                // advantage after the right hand side
                i++;
                // this token is now the left for the next loop
                left = token;
            } else {
                // clear left
                left = null;
                stack.push(token);
            }
        }

        nodes.clear();
        nodes.addAll(stack);
        // must reverse as it was added from a stack that is reverse
        Collections.reverse(nodes);
    }

    /**
     * Prepares ternary expressions.
     * <p/>
     * This process prepares the ternary expressions in the AST. This is done by linking the ternary operator (condition
     * ? trueValue : falseValue) with all three parts: condition, trueValue, and falseValue.
     * <p/>
     * The ternary operator consists of two tokens: ? and : We need to find the pattern: condition ? trueValue :
     * falseValue
     */
    protected void prepareTernaryExpressions(List<SimpleNode> nodes) {
        List<SimpleNode> answer = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            SimpleNode token = nodes.get(i);

            if (token instanceof TernaryExpression ternary && "?".equals(token.getToken().getText())) {
                // We found the ? operator
                // Get the condition (left side)
                if (answer.isEmpty()) {
                    throw new SimpleParserException(
                            "Ternary operator ? has no condition", token.getToken().getIndex());
                }
                SimpleNode condition = answer.remove(answer.size() - 1);

                // Get the true value (right side of ?)
                if (i >= nodes.size() - 1) {
                    throw new SimpleParserException(
                            "Ternary operator ? has no true value", token.getToken().getIndex());
                }
                SimpleNode trueValue = nodes.get(++i);

                // Find the : operator
                if (i >= nodes.size() - 1) {
                    throw new SimpleParserException(
                            "Ternary operator ? has no : operator", token.getToken().getIndex());
                }
                SimpleNode colonToken = nodes.get(++i);
                if (!(colonToken instanceof TernaryExpression) || !":".equals(colonToken.getToken().getText())) {
                    throw new SimpleParserException(
                            "Ternary operator ? must be followed by :", token.getToken().getIndex());
                }

                // Get the false value (right side of :)
                if (i >= nodes.size() - 1) {
                    throw new SimpleParserException(
                            "Ternary operator : has no false value", token.getToken().getIndex());
                }
                SimpleNode falseValue = nodes.get(++i);

                // Link all parts to the ternary expression
                if (!ternary.acceptCondition(condition)) {
                    throw new SimpleParserException(
                            "Ternary operator does not support condition token " + condition.getToken(),
                            token.getToken().getIndex());
                }
                if (!ternary.acceptTrueValue(trueValue)) {
                    throw new SimpleParserException(
                            "Ternary operator does not support true value token " + trueValue.getToken(),
                            token.getToken().getIndex());
                }
                if (!ternary.acceptFalseValue(falseValue)) {
                    throw new SimpleParserException(
                            "Ternary operator does not support false value token " + falseValue.getToken(),
                            token.getToken().getIndex());
                }

                answer.add(ternary);
            } else {
                answer.add(token);
            }
        }

        nodes.clear();
        nodes.addAll(answer);
    }

    // --------------------------------------------------------------
    // grammar
    // --------------------------------------------------------------

    /**
     * Accept the given token.
     * <p/>
     * This is to be used by the grammar to accept tokens and then continue parsing using the grammar, such as a
     * function grammar.
     *
     * @param  accept the token
     * @return        <tt>true</tt> if accepted, <tt>false</tt> otherwise.
     */
    protected boolean accept(TokenType accept) {
        return token == null || token.getType().getType() == accept;
    }

    /**
     * Expect any of the given token(s)
     *
     * @param  expect                the token(s) to expect
     * @throws SimpleParserException is thrown if the token is not as expected
     */
    protected void expect(TokenType... expect) throws SimpleParserException {
        if (token == null) {
            throw new SimpleParserException("expected any symbol " + Arrays.asList(expect) + " but reached eol", previousIndex);
        }
        for (TokenType target : expect) {
            if (token.getType().getType() == target) {
                return;
            }
        }
        // use the previous index as that is where the problem is
        throw new SimpleParserException(
                "expected any symbol " + Arrays.asList(expect) + " but was " + token.getType().getType(), previousIndex);
    }

    /**
     * Expect a given token
     *
     * @param  expect                the token to expect
     * @throws SimpleParserException is thrown if the token is not as expected
     */
    protected void expect(TokenType expect) throws SimpleParserException {
        if (token != null && token.getType().getType() == expect) {
            return;
        } else if (token == null) {
            // use the previous index as that is where the problem is
            throw new SimpleParserException("expected symbol " + expect + " but reached eol", previousIndex);
        } else {
            // use the previous index as that is where the problem is
            throw new SimpleParserException(
                    "expected symbol " + expect + " but was " + token.getType().getType(), previousIndex);
        }
    }

    /**
     * Expect and accept a given number of tokens in sequence.
     * <p/>
     * This is used to accept whitespace or string literals.
     *
     * @param expect the token to accept
     */
    protected void expectAndAcceptMore(TokenType expect) {
        expect(expect);

        while (!token.getType().isEol() && token.getType().getType() == expect) {
            nextToken();
        }
    }

}
