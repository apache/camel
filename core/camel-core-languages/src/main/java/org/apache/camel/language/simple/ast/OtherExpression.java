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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.BaseSimpleParser;
import org.apache.camel.language.simple.types.OtherOperatorType;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

/**
 * Represents other operator expression in the AST.
 */
public class OtherExpression extends BaseSimpleNode {

    private final OtherOperatorType operator;
    private SimpleNode left;
    private SimpleNode right;

    public OtherExpression(SimpleToken token) {
        super(token);
        operator = OtherOperatorType.asOperator(token.getText());
    }

    @Override
    public String toString() {
        return left + " " + token.getText() + " " + right;
    }

    public boolean acceptLeftNode(SimpleNode lef) {
        this.left = lef;
        return true;
    }

    public boolean acceptRightNode(SimpleNode right) {
        this.right = right;
        return true;
    }

    public OtherOperatorType getOperator() {
        return operator;
    }

    public SimpleNode getLeft() {
        return left;
    }

    public SimpleNode getRight() {
        return right;
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) {
        org.apache.camel.util.ObjectHelper.notNull(left, "left node", this);
        org.apache.camel.util.ObjectHelper.notNull(right, "right node", this);

        // the expression parser does not parse literal text into single/double quote tokens
        // so we need to manually remove leading quotes from the literal text when using the other operators
        final Expression leftExp = left.createExpression(camelContext, expression);
        if (right instanceof LiteralExpression le) {
            String text = le.getText();
            String changed = StringHelper.removeLeadingAndEndingQuotes(text);
            if (!changed.equals(text)) {
                le.replaceText(changed);
            }
        }
        final Expression rightExp = right.createExpression(camelContext, expression);

        if (operator == OtherOperatorType.ELVIS) {
            return createElvisExpression(camelContext, leftExp, rightExp);
        } else if (operator == OtherOperatorType.CHAIN || operator == OtherOperatorType.CHAIN_NULL_SAFE) {
            boolean nullSafe = operator == OtherOperatorType.CHAIN_NULL_SAFE;
            return createChainExpression(camelContext, leftExp, rightExp, nullSafe);
        }

        throw new SimpleParserException("Unknown other operator " + operator, token.getIndex());
    }

    private Expression createElvisExpression(
            final CamelContext camelContext, final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Object value = leftExp.evaluate(exchange, Object.class);
                if (value == null || Boolean.FALSE == value || ObjectHelper.isEmpty(value) || ObjectHelper.equal(0, value)) {
                    return rightExp.evaluate(exchange, type);
                } else {
                    return leftExp.evaluate(exchange, type);
                }
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    private Expression createChainExpression(
            final CamelContext camelContext, final Expression leftExp, final Expression rightExp, boolean nullSafe) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                // left is input to right
                Object value = leftExp.evaluate(exchange, Object.class);
                if (value == null && nullSafe) {
                    return null; // break out
                }
                exchange.getMessage().setBody(value);
                return rightExp.evaluate(exchange, type);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    @Override
    public String createCode(CamelContext camelContext, String expression) throws SimpleParserException {
        return BaseSimpleParser.CODE_START + doCreateCode(camelContext, expression) + BaseSimpleParser.CODE_END;
    }

    private String doCreateCode(CamelContext camelContext, String expression) throws SimpleParserException {
        org.apache.camel.util.ObjectHelper.notNull(left, "left node", this);
        org.apache.camel.util.ObjectHelper.notNull(right, "right node", this);

        // the expression parser does not parse literal text into single/double quote tokens
        // so we need to manually remove leading quotes from the literal text when using the other operators
        final String leftExp = left.createCode(camelContext, expression);
        if (right instanceof LiteralExpression le) {
            String text = le.getText();
            // must be in double quotes to be a String type
            String changed = StringHelper.removeLeadingAndEndingQuotes(text);
            changed = StringQuoteHelper.doubleQuote(changed);
            if (!changed.equals(text)) {
                le.replaceText(changed);
            }
        }
        final String rightExp = right.createCode(camelContext, expression);

        if (operator == OtherOperatorType.ELVIS) {
            return "elvis(exchange, " + leftExp + ", " + rightExp + ")";
        } else if (operator == OtherOperatorType.CHAIN) {
            throw new SimpleParserException("Chain operator " + operator + " not supported in csimple", token.getIndex());
        }

        throw new SimpleParserException("Unknown other operator " + operator, token.getIndex());
    }

}
