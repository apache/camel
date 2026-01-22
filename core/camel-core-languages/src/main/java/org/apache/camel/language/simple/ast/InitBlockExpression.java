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
import org.apache.camel.language.simple.types.InitOperatorType;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Represents other init block expression in the AST.
 */
public class InitBlockExpression extends BaseSimpleNode {

    private final InitOperatorType operator;
    private SimpleNode left;
    private SimpleNode right;

    public InitBlockExpression(SimpleToken token) {
        super(token);
        operator = InitOperatorType.asOperator(token.getText());
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

    public InitOperatorType getOperator() {
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
        ObjectHelper.notNull(left, "left node", this);
        ObjectHelper.notNull(right, "right node", this);

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

        if (operator == InitOperatorType.ASSIGNMENT) {
            return createAssignmentExpression(camelContext, leftExp, rightExp);
        }

        throw new SimpleParserException("Unknown other operator " + operator, token.getIndex());
    }

    private Expression createAssignmentExpression(
            final CamelContext camelContext, final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                String name = leftExp.evaluate(exchange, String.class);
                name = name.trim();
                name = StringHelper.after(name, "$$", name);
                Object value = rightExp.evaluate(exchange, Object.class);
                exchange.setVariable(name, value);
                return null;
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
        throw new SimpleParserException("Using init blocks with csimple is not supported", token.getIndex());
    }

}
