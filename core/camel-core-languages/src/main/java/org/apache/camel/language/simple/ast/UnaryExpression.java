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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.UnaryOperatorType;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an unary expression in the AST
 */
public class UnaryExpression extends BaseSimpleNode {

    private UnaryOperatorType operator;
    private SimpleNode left;

    public UnaryExpression(SimpleToken token) {
        super(token);
        operator = UnaryOperatorType.asOperator(token.getText());
    }

    @Override
    public String toString() {
        if (left != null) {
            return left + token.getText();
        } else {
            return token.getText();
        }
    }

    /**
     * Accepts the left node to this operator
     *
     * @param left  the left node to accept
     */
    public void acceptLeft(SimpleNode left) {
        this.left = left;
    }

    public UnaryOperatorType getOperator() {
        return operator;
    }

    @Override
    public Expression createExpression(String expression) {
        ObjectHelper.notNull(left, "left node", this);

        final Expression leftExp = left.createExpression(expression);

        if (operator == UnaryOperatorType.INC) {
            return createIncExpression(leftExp);
        } else if (operator == UnaryOperatorType.DEC) {
            return createDecExpression(leftExp);
        }

        throw new SimpleParserException("Unknown unary operator " + operator, token.getIndex());
    }

    private Expression createIncExpression(final Expression leftExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Number num = leftExp.evaluate(exchange, Number.class);
                if (num != null) {
                    long val = num.longValue();
                    val++;

                    // convert value back to same type as input as we want to preserve type
                    Object left = leftExp.evaluate(exchange, Object.class);
                    try {
                        left = exchange.getContext().getTypeConverter().mandatoryConvertTo(left.getClass(), exchange, val);
                    } catch (NoTypeConversionAvailableException e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }

                    // and return the result
                    return exchange.getContext().getTypeConverter().convertTo(type, left);
                }
                // cannot convert the expression as a number
                Exception cause = new CamelExchangeException("Cannot evaluate " + leftExp + " as a number", exchange);
                throw RuntimeCamelException.wrapRuntimeCamelException(cause);
            }

            @Override
            public String toString() {
                return left + operator.toString();
            }
        };
    }

    private Expression createDecExpression(final Expression leftExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Number num = leftExp.evaluate(exchange, Number.class);
                if (num != null) {
                    long val = num.longValue();
                    val--;

                    // convert value back to same type as input as we want to preserve type
                    Object left = leftExp.evaluate(exchange, Object.class);
                    try {
                        left = exchange.getContext().getTypeConverter().mandatoryConvertTo(left.getClass(), exchange, val);
                    } catch (NoTypeConversionAvailableException e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }

                    // and return the result
                    return exchange.getContext().getTypeConverter().convertTo(type, left);
                }
                // cannot convert the expression as a number
                Exception cause = new CamelExchangeException("Cannot evaluate " + leftExp + " as a number", exchange);
                throw RuntimeCamelException.wrapRuntimeCamelException(cause);
            }

            @Override
            public String toString() {
                return left + operator.toString();
            }
        };
    }

}
