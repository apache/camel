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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.camel.CamelContext;
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

    private final UnaryOperatorType operator;
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
     * @param left the left node to accept
     */
    public void acceptLeft(SimpleNode left) {
        this.left = left;
    }

    public UnaryOperatorType getOperator() {
        return operator;
    }

    public SimpleNode getLeft() {
        return left;
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) {
        ObjectHelper.notNull(left, "left node", this);

        final Expression leftExp = left.createExpression(camelContext, expression);

        if (operator == UnaryOperatorType.INC) {
            return createIncDecExpression(camelContext, leftExp, 1);
        } else if (operator == UnaryOperatorType.DEC) {
            return createIncDecExpression(camelContext, leftExp, -1);
        }

        throw new SimpleParserException("Unknown unary operator " + operator, token.getIndex());
    }

    private Expression createIncDecExpression(CamelContext camelContext, final Expression leftExp, final int delta) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                // evaluate only once as the left hand side may have side effects
                Object value = leftExp.evaluate(exchange, Object.class);
                Number num = value instanceof Number n
                        ? n : camelContext.getTypeConverter().convertTo(Number.class, exchange, value);
                if (num != null) {
                    // keep decimals such as 1.5++ is 2.5
                    Number result;
                    if (num instanceof BigDecimal bd) {
                        result = bd.add(BigDecimal.valueOf(delta));
                    } else if (num instanceof BigInteger bi) {
                        result = bi.add(BigInteger.valueOf(delta));
                    } else if (num instanceof Double || num instanceof Float) {
                        result = num.doubleValue() + delta;
                    } else {
                        result = num.longValue() + delta;
                    }

                    // convert value back to same type as input as we want to preserve type
                    Object answer;
                    try {
                        answer = camelContext.getTypeConverter().mandatoryConvertTo(value.getClass(), exchange, result);
                    } catch (NoTypeConversionAvailableException e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }

                    // and return the result
                    return camelContext.getTypeConverter().convertTo(type, answer);
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
