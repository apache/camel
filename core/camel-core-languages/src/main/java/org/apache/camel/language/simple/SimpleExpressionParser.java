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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.ast.ChainExpression;
import org.apache.camel.language.simple.ast.InitBlockExpression;
import org.apache.camel.language.simple.ast.LiteralExpression;
import org.apache.camel.language.simple.ast.LiteralNode;
import org.apache.camel.language.simple.ast.OtherExpression;
import org.apache.camel.language.simple.ast.SimpleFunctionEnd;
import org.apache.camel.language.simple.ast.SimpleFunctionStart;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.apache.camel.language.simple.ast.UnaryExpression;
import org.apache.camel.language.simple.types.ChainOperatorType;
import org.apache.camel.language.simple.types.OtherOperatorType;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.TokenType;
import org.apache.camel.support.LanguageHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.StringHelper;

/**
 * A parser to parse simple language as a Camel {@link Expression}
 */
public class SimpleExpressionParser extends BaseSimpleParser {

    // use caches to avoid re-parsing the same expressions over and over again
    private final Map<String, Expression> cacheExpression;
    private boolean skipFileFunctions;

    public SimpleExpressionParser(CamelContext camelContext, String expression,
                                  boolean allowEscape,
                                  Map<String, Expression> cacheExpression) {
        this(camelContext, expression, allowEscape, false, cacheExpression);
    }

    public SimpleExpressionParser(CamelContext camelContext, String expression,
                                  boolean allowEscape, boolean skipFileFunctions,
                                  Map<String, Expression> cacheExpression) {
        super(camelContext, expression, allowEscape);
        this.cacheExpression = cacheExpression;
        this.skipFileFunctions = skipFileFunctions;
    }

    public SimpleExpressionParser(CamelContext camelContext, String expression,
                                  boolean allowEscape, boolean skipFileFunctions,
                                  Map<String, Expression> cacheExpression, SimpleTokenizer tokenizer) {
        super(camelContext, expression, allowEscape, tokenizer);
        this.cacheExpression = cacheExpression;
        this.skipFileFunctions = skipFileFunctions;
    }

    public Expression parseExpression() {
        try {
            Expression init = null;
            // are there init block then parse this part only, and change the expression to clip out the init block afterwards
            if (SimpleInitBlockTokenizer.hasInitBlock(expression)) {
                SimpleInitBlockParser initParser
                        = new SimpleInitBlockParser(camelContext, expression, allowEscape, skipFileFunctions, cacheExpression);
                // the init block should be parsed in predicate mode as that is needed to fully parse with all the operators and functions
                init = initParser.parseExpression();
                String part = StringHelper.after(expression, SimpleInitBlockTokenizer.INIT_END);
                if (part.startsWith("\n")) {
                    // skip newline after ending init block
                    part = part.substring(1);
                }
                this.expression = part;
                // use $$key as local variable in the expression afterwards
                for (String key : initParser.getInitKeys()) {
                    this.expression = this.expression.replace("$" + key, "${variable." + key + "}");
                }
                // use $$key() as local function in the expression afterwards
                for (String key : initParser.getInitFunctions()) {
                    // no-arg functions
                    this.expression = this.expression.replace("$" + key + "()", "${function(" + key + ")}");
                    // arg functions
                    this.expression = this.expression.replace("${" + key + "(", "${function(" + key + ",");
                }
            }

            // parse simple expression
            parseTokens();
            Expression exp = doParseExpression();
            // include init block in expression
            if (init != null) {
                exp = ExpressionBuilder.concatExpression(List.of(init, exp));
            }
            return exp;
        } catch (SimpleParserException e) {
            // catch parser exception and turn that into a syntax exceptions
            throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
        } catch (Exception e) {
            // include exception in rethrown exception
            throw new SimpleIllegalSyntaxException(expression, -1, e.getMessage(), e);
        }
    }

    public String parseCode() {
        try {
            parseTokens();
            return doParseCode();
        } catch (SimpleParserException e) {
            // catch parser exception and turn that into a syntax exceptions
            throw new SimpleIllegalSyntaxException(expression, e.getIndex(), e.getMessage(), e);
        } catch (Exception e) {
            // include exception in rethrown exception
            throw new SimpleIllegalSyntaxException(expression, -1, e.getMessage(), e);
        }
    }

    /**
     * First step parsing into a list of nodes.
     *
     * This is used as SPI for camel-csimple to do AST transformation and parse into java source code.
     */
    protected List<SimpleNode> parseTokens() {
        clear();

        // parse the expression using the following grammar
        nextToken();
        while (!token.getType().isEol()) {
            // an expression supports just template (eg text), functions, unary, or other operator
            templateText();
            functionText();
            unaryOperator();
            chainOperator();
            otherOperator();
            nextToken();
        }

        // now after parsing, we need a bit of work to do, to make it easier to turn the tokens
        // into an ast, and then from the ast, to Camel expression(s).
        // hence why there are a number of tasks going on below to accomplish this

        // remove any ignorable white space tokens
        removeIgnorableWhiteSpaceTokens();
        // turn the tokens into the ast model
        parseAndCreateAstModel();
        // compact and stack blocks (eg function start/end)
        prepareBlocks(nodes);
        // compact and stack unary operators
        prepareUnaryExpressions(nodes);
        // compact and stack chain expressions
        prepareChainExpression(nodes);
        // compact and stack other expressions
        prepareOtherExpressions(nodes);

        return nodes;
    }

    /**
     * Second step parsing into an expression
     */
    protected Expression doParseExpression() {
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

    /**
     * Second step parsing into an expression
     */
    protected Expression doParseInitExpression() {
        // create and return as a Camel expression
        List<Expression> expressions = createExpressions();
        if (expressions.isEmpty()) {
            return null;
        } else if (expressions.size() == 1) {
            return expressions.get(0);
        } else {
            return ExpressionBuilder.eval(expressions);
        }
    }

    /**
     * Removes any ignorable whitespace tokens before and after other operators.
     * <p/>
     * During the initial parsing (input -> tokens), then there may be excessive whitespace tokens, which can safely be
     * removed, which makes the succeeding parsing easier.
     */
    protected void removeIgnorableWhiteSpaceTokens() {
        // remove all ignored
        tokens.removeIf(t -> t.getType().isIgnore());

        // white space should be removed before and after the chain/other operator
        List<SimpleToken> toRemove = new ArrayList<>();
        for (int i = 1; i < tokens.size() - 1; i++) {
            SimpleToken prev = tokens.get(i - 1);
            SimpleToken cur = tokens.get(i);
            SimpleToken next = tokens.get(i + 1);
            if (cur.getType().isOther() || cur.getType().isChain() || cur.getType().isInit()) {
                if (prev.getType().isWhitespace()) {
                    toRemove.add(prev);
                }
                if (next.getType().isWhitespace()) {
                    toRemove.add(next);
                }
            }
            if (cur.getType().isInitVariable()) {
                if (prev.getType().isWhitespace() || " ".equals(prev.getText())) {
                    toRemove.add(prev);
                }
            }
        }

        if (!toRemove.isEmpty()) {
            tokens.removeAll(toRemove);
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

            if (token.getType().isInitVariable()) {
                // we start a new init variable so the current image token need to be added first
                if (imageToken != null) {
                    nodes.add(imageToken);
                    imageToken = null;
                }
            }

            // if no token was created, then it's a character/whitespace/escaped symbol
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
        // expression only support functions, unary operators, operators, and other operators
        if (token.getType().isFunctionStart()) {
            // starting a new function
            functions.incrementAndGet();
            return new SimpleFunctionStart(token, cacheExpression, skipFileFunctions);
        } else if (functions.get() > 0 && token.getType().isFunctionEnd()) {
            // there must be a start function already, to let this be an end function
            functions.decrementAndGet();
            return new SimpleFunctionEnd(token);
        } else if (token.getType().isUnary()) {
            // there must be an end function as previous, to let this be a unary function
            if (!nodes.isEmpty() && nodes.get(nodes.size() - 1) instanceof SimpleFunctionEnd) {
                return new UnaryExpression(token);
            }
        } else if (token.getType().isChain()) {
            return new ChainExpression(token);
        } else if (token.getType().isOther()) {
            return new OtherExpression(token);
        } else if (token.getType().isInit()) {
            return new InitBlockExpression(token);
        }

        // by returning null, we will let the parser determine what to do
        return null;
    }

    private List<Expression> createExpressions() {
        List<Expression> answer = new ArrayList<>();
        for (SimpleNode token : nodes) {
            Expression exp = token.createExpression(camelContext, expression);
            if (exp != null) {
                answer.add(exp);
            }
        }
        return answer;
    }

    /**
     * Second step parsing into code
     */
    protected String doParseCode() {
        StringBuilder sb = new StringBuilder(256);
        boolean firstIsLiteral = false;
        for (SimpleNode node : nodes) {
            String exp = node.createCode(camelContext, expression);
            if (exp != null) {
                if (sb.isEmpty() && node instanceof LiteralNode) {
                    firstIsLiteral = true;
                }
                if (!sb.isEmpty()) {
                    // okay we append together and this requires that the first node to be literal
                    if (!firstIsLiteral) {
                        // then insert an empty string + to force type into string so the compiler
                        // can compile with the + function
                        sb.insert(0, "\"\" + ");
                    }
                    sb.append(" + ");
                }
                parseLiteralNode(sb, node, exp);
            }
        }

        String code = sb.toString();
        code = code.replace(BaseSimpleParser.CODE_START, "");
        code = code.replace(BaseSimpleParser.CODE_END, "");
        return code;
    }

    static void parseLiteralNode(StringBuilder sb, SimpleNode node, String exp) {
        if (node instanceof LiteralNode) {
            exp = StringHelper.removeLeadingAndEndingQuotes(exp);
            sb.append("\"");
            // " should be escaped to \"
            exp = LanguageHelper.escapeQuotes(exp);
            // \n \t \r should be escaped
            exp = exp.replaceAll("\n", "\\\\n");
            exp = exp.replaceAll("\t", "\\\\t");
            exp = exp.replaceAll("\r", "\\\\r");
            if (exp.endsWith("\\") && !exp.endsWith("\\\\")) {
                // there is a single trailing slash which we need to escape
                exp += "\\";
            }
            sb.append(exp);
            sb.append("\"");
        } else {
            sb.append(exp);
        }
    }

    // --------------------------------------------------------------
    // grammar
    // --------------------------------------------------------------

    // the expression parser only understands
    // - template = literal texts with can contain embedded functions
    // - function = simple functions such as ${body} etc.
    // - unary operator = operator attached to the left-hand side node
    // - other operator = operator attached to both the left and right hand side nodes

    protected void templateText() {
        // for template, we accept anything but functions / other operator
        while (!token.getType().isFunctionStart() && !token.getType().isFunctionEnd() && !token.getType().isEol()
                && !token.getType().isOther() && !token.getType().isChain()) {
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

    protected boolean otherOperator() {
        if (accept(TokenType.otherOperator)) {
            // remember the other operator
            OtherOperatorType operatorType = OtherOperatorType.asOperator(token.getText());

            nextToken();
            // there should be at least one whitespace after the operator
            expectAndAcceptMore(TokenType.whiteSpace);

            // then we expect either some quoted text, another function, or a numeric, boolean or null value
            if (singleQuotedLiteralWithFunctionsText()
                    || doubleQuotedLiteralWithFunctionsText()
                    || functionText()
                    || numericValue()
                    || booleanValue()
                    || nullValue()) {
                // then after the right hand side value, there should be a whitespace if there is more tokens
                nextToken();
                if (!token.getType().isEol()) {
                    expect(TokenType.whiteSpace);
                }
            } else {
                throw new SimpleParserException(
                        "Other operator " + operatorType + " does not support token " + token, token.getIndex());
            }
            return true;
        }
        return false;
    }

    protected boolean chainOperator() {
        if (accept(TokenType.chainOperator)) {
            // remember the chain operator
            ChainOperatorType operatorType = ChainOperatorType.asOperator(token.getText());

            nextToken();
            // there should be at least one whitespace after the operator
            expectAndAcceptMore(TokenType.whiteSpace);

            // then we expect either some quoted text, another function, or a numeric, boolean or null value
            if (singleQuotedLiteralWithFunctionsText()
                    || doubleQuotedLiteralWithFunctionsText()
                    || functionText()
                    || numericValue()
                    || booleanValue()
                    || nullValue()) {
                // then after the right hand side value, there should be a whitespace if there is more tokens
                nextToken();
                if (!token.getType().isEol()) {
                    expectAndAcceptMore(TokenType.whiteSpace);
                }
            } else {
                throw new SimpleParserException(
                        "Chain operator " + operatorType + " does not support token " + token, token.getIndex());
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

    protected boolean singleQuotedLiteralWithFunctionsText() {
        if (accept(TokenType.singleQuote)) {
            nextToken(TokenType.singleQuote, TokenType.eol, TokenType.functionStart, TokenType.functionEnd);
            while (!token.getType().isSingleQuote() && !token.getType().isEol()) {
                // we need to loop until we find the ending single quote, or the eol
                nextToken(TokenType.singleQuote, TokenType.eol, TokenType.functionStart, TokenType.functionEnd);
            }
            expect(TokenType.singleQuote);
            return true;
        }
        return false;
    }

    protected boolean doubleQuotedLiteralWithFunctionsText() {
        if (accept(TokenType.doubleQuote)) {
            nextToken(TokenType.doubleQuote, TokenType.eol, TokenType.functionStart, TokenType.functionEnd);
            while (!token.getType().isDoubleQuote() && !token.getType().isEol()) {
                // we need to loop until we find the ending double quote, or the eol
                nextToken(TokenType.doubleQuote, TokenType.eol, TokenType.functionStart, TokenType.functionEnd);
            }
            expect(TokenType.doubleQuote);
            return true;
        }
        return false;
    }

    protected boolean numericValue() {
        return accept(TokenType.numericValue);
        // no other tokens to check so do not use nextToken
    }

    protected boolean booleanValue() {
        return accept(TokenType.booleanValue);
        // no other tokens to check so do not use nextToken
    }

    protected boolean nullValue() {
        return accept(TokenType.nullValue);
        // no other tokens to check so do not use nextToken
    }

}
