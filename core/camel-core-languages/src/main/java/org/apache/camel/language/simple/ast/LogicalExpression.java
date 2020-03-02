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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.types.LogicalOperatorType;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a logical expression in the AST
 */
public class LogicalExpression extends BaseSimpleNode {

    private LogicalOperatorType operator;
    private SimpleNode left;
    private SimpleNode right;

    public LogicalExpression(SimpleToken token) {
        super(token);
        operator = LogicalOperatorType.asOperator(token.getText());
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

    public LogicalOperatorType getOperator() {
        return operator;
    }

    @Override
    public Expression createExpression(String expression) {
        ObjectHelper.notNull(left, "left node", this);
        ObjectHelper.notNull(right, "right node", this);

        final Expression leftExp = left.createExpression(expression);
        final Expression rightExp = right.createExpression(expression);

        if (operator == LogicalOperatorType.AND) {
            return createAndExpression(leftExp, rightExp);
        } else if (operator == LogicalOperatorType.OR) {
            return createOrExpression(leftExp, rightExp);
        }

        throw new SimpleParserException("Unknown logical operator " + operator, token.getIndex());
    }

    private Expression createAndExpression(final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Predicate predicate = ExpressionToPredicateAdapter.toPredicate(leftExp);
                predicate = PredicateBuilder.and(predicate, ExpressionToPredicateAdapter.toPredicate(rightExp));

                boolean answer = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, answer);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    private Expression createOrExpression(final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Predicate predicate = ExpressionToPredicateAdapter.toPredicate(leftExp);
                predicate = PredicateBuilder.or(predicate, ExpressionToPredicateAdapter.toPredicate(rightExp));

                boolean answer = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, answer);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

}
