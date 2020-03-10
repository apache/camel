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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.types.BinaryOperatorType;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.support.builder.ValueBuilder;

/**
 * Represents a binary expression in the AST.
 */
public class BinaryExpression extends BaseSimpleNode {

    // this is special for the range operator where you define the range as from..to (where from and to are numbers)
    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(\\.\\.)(\\d+)$");

    private final BinaryOperatorType operator;
    private SimpleNode left;
    private SimpleNode right;

    public BinaryExpression(SimpleToken token) {
        super(token);
        operator = BinaryOperatorType.asOperator(token.getText());
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

    public BinaryOperatorType getOperator() {
        return operator;
    }

    @Override
    public Expression createExpression(String expression) {
        org.apache.camel.util.ObjectHelper.notNull(left, "left node", this);
        org.apache.camel.util.ObjectHelper.notNull(right, "right node", this);

        final Expression leftExp = left.createExpression(expression);
        final Expression rightExp = right.createExpression(expression);

        if (operator == BinaryOperatorType.EQ) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isEqualTo(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.EQ_IGNORE) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isEqualToIgnoreCase(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.GT) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isGreaterThan(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.GTE) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isGreaterThanOrEqualTo(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.LT) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isLessThan(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.LTE) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isLessThanOrEqualTo(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.NOT_EQ) {
            return createExpression(leftExp, rightExp, PredicateBuilder.isNotEqualTo(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.NOT_EQ_IGNORE) {
            return createExpression(leftExp, rightExp, PredicateBuilder.not(PredicateBuilder.isEqualToIgnoreCase(leftExp, rightExp)));
        } else if (operator == BinaryOperatorType.CONTAINS) {
            return createExpression(leftExp, rightExp, PredicateBuilder.contains(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.NOT_CONTAINS) {
            return createExpression(leftExp, rightExp, PredicateBuilder.not(PredicateBuilder.contains(leftExp, rightExp)));
        } else if (operator == BinaryOperatorType.CONTAINS_IGNORECASE) {
            return createExpression(leftExp, rightExp, PredicateBuilder.containsIgnoreCase(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.NOT_CONTAINS_IGNORECASE) {
            return createExpression(leftExp, rightExp, PredicateBuilder.not(PredicateBuilder.containsIgnoreCase(leftExp, rightExp)));
        } else if (operator == BinaryOperatorType.IS || operator == BinaryOperatorType.NOT_IS) {
            return createIsExpression(expression, leftExp, rightExp);
        } else if (operator == BinaryOperatorType.REGEX || operator == BinaryOperatorType.NOT_REGEX) {
            return createRegexExpression(leftExp, rightExp);
        } else if (operator == BinaryOperatorType.IN || operator == BinaryOperatorType.NOT_IN) {
            return createInExpression(leftExp, rightExp);
        } else if (operator == BinaryOperatorType.RANGE || operator == BinaryOperatorType.NOT_RANGE) {
            return createRangeExpression(expression, leftExp, rightExp);
        } else if (operator == BinaryOperatorType.STARTS_WITH) {
            return createExpression(leftExp, rightExp, PredicateBuilder.startsWith(leftExp, rightExp));
        } else if (operator == BinaryOperatorType.ENDS_WITH) {
            return createExpression(leftExp, rightExp, PredicateBuilder.endsWith(leftExp, rightExp));
        }

        throw new SimpleParserException("Unknown binary operator " + operator, token.getIndex());
    }

    private Expression createIsExpression(final String expression, final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Predicate predicate;
                String name = rightExp.evaluate(exchange, String.class);
                if (name == null || "null".equals(name)) {
                    throw new SimpleIllegalSyntaxException(expression, right.getToken().getIndex(), operator + " operator cannot accept null. A class type must be provided.");
                }
                Class<?> rightType = exchange.getContext().getClassResolver().resolveClass(name);
                if (rightType == null) {
                    throw new SimpleIllegalSyntaxException(expression, right.getToken().getIndex(), operator + " operator cannot find class with name: " + name);
                }

                predicate = PredicateBuilder.isInstanceOf(leftExp, rightType);
                if (operator == BinaryOperatorType.NOT_IS) {
                    predicate = PredicateBuilder.not(predicate);
                }
                boolean answer = predicate.matches(exchange);

                return exchange.getContext().getTypeConverter().convertTo(type, answer);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    private Expression createRegexExpression(final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                // reg ex should use String pattern, so we evaluate the right hand side as a String
                Predicate predicate = PredicateBuilder.regex(leftExp, rightExp.evaluate(exchange, String.class));
                if (operator == BinaryOperatorType.NOT_REGEX) {
                    predicate = PredicateBuilder.not(predicate);
                }
                boolean answer = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, answer);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    private Expression createInExpression(final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                // okay the in operator is a bit more complex as we need to build a list of values
                // from the right hand side expression.
                // each element on the right hand side must be separated by comma (default for create iterator)
                Iterator<?> it = ObjectHelper.createIterator(rightExp.evaluate(exchange, Object.class));
                List<Object> values = new ArrayList<>();
                while (it.hasNext()) {
                    values.add(it.next());
                }
                // then reuse value builder to create the in predicate with the list of values
                ValueBuilder vb = new ValueBuilder(leftExp);
                Predicate predicate = vb.in(values.toArray());
                if (operator == BinaryOperatorType.NOT_IN) {
                    predicate = PredicateBuilder.not(predicate);
                }
                boolean answer = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, answer);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    private Expression createRangeExpression(final String expression, final Expression leftExp, final Expression rightExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Predicate predicate;

                String range = rightExp.evaluate(exchange, String.class);
                Matcher matcher = RANGE_PATTERN.matcher(range);
                if (matcher.matches()) {
                    // wrap as constant expression for the from and to values
                    Expression from = ExpressionBuilder.constantExpression(matcher.group(1));
                    Expression to = ExpressionBuilder.constantExpression(matcher.group(3));

                    // build a compound predicate for the range
                    predicate = PredicateBuilder.isGreaterThanOrEqualTo(leftExp, from);
                    predicate = PredicateBuilder.and(predicate, PredicateBuilder.isLessThanOrEqualTo(leftExp, to));
                } else {
                    throw new SimpleIllegalSyntaxException(expression, right.getToken().getIndex(), operator + " operator is not valid. Valid syntax:'from..to' (where from and to are numbers).");
                }
                if (operator == BinaryOperatorType.NOT_RANGE) {
                    predicate = PredicateBuilder.not(predicate);
                }

                boolean answer = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, answer);
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    private Expression createExpression(final Expression left, final Expression right, final Predicate predicate) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
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
