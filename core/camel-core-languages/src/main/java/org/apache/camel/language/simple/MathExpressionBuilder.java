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

import java.util.Arrays;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.ObjectHelper;

/**
 * Expression builder for numeric and math functions used by the simple language.
 */
public final class MathExpressionBuilder {

    private MathExpressionBuilder() {
    }

    /**
     * Converts the given expressions to a number and return the absolute value (uses message body if expression is
     * null)
     */
    public static Expression absExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Long value;
                if (exp != null) {
                    value = exp.evaluate(exchange, Long.class);
                } else {
                    value = exchange.getMessage().getBody(Long.class);
                }
                if (value != null) {
                    value = Math.abs(value);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "abs(" + expression + ")";
                } else {
                    return "abs()";
                }
            }
        };
    }

    /**
     * Converts the given expressions to a floating number and return the floor value (uses message body if expression
     * is null)
     */
    public static Expression floorExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Double value;
                if (exp != null) {
                    value = exp.evaluate(exchange, Double.class);
                } else {
                    value = exchange.getMessage().getBody(Double.class);
                }
                if (value != null) {
                    double d = Math.floor(value);
                    return (long) d;
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "floor(" + expression + ")";
                } else {
                    return "floor()";
                }
            }
        };
    }

    /**
     * Converts the given expressions to a floating number and return the ceil value (uses message body if expression is
     * null)
     */
    public static Expression ceilExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Double value;
                if (exp != null) {
                    value = exp.evaluate(exchange, Double.class);
                } else {
                    value = exchange.getMessage().getBody(Double.class);
                }
                if (value != null) {
                    double d = Math.ceil(value);
                    return (long) d;
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "ceil(" + expression + ")";
                } else {
                    return "ceil()";
                }
            }
        };
    }

    /**
     * An expression that converts the expressions to number and sum the values
     */
    public static Expression sumExpression(String[] numbers) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[numbers != null ? numbers.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; numbers != null && i < numbers.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(numbers[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Long answer = null;
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        Long val = exchange.getContext().getTypeConverter().tryConvertTo(Long.class, exchange, i);
                        if (val != null) {
                            if (answer == null) {
                                answer = 0L;
                            }
                            answer += val;
                        }
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "sum(" + Arrays.toString(numbers) + ")";
            }
        };
    }

    /**
     * An expression that converts the expressions to number and returns the maximum number
     */
    public static Expression maxExpression(String[] numbers) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[numbers != null ? numbers.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; numbers != null && i < numbers.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(numbers[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Long answer = null;
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        Long val = exchange.getContext().getTypeConverter().tryConvertTo(Long.class, exchange, i);
                        if (val != null) {
                            if (answer == null) {
                                answer = val;
                            }
                            answer = Math.max(answer, val);
                        }
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "max(" + Arrays.toString(numbers) + ")";
            }
        };
    }

    /**
     * An expression that converts the expressions to number and returns the minimum number
     */
    public static Expression minExpression(String[] numbers) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[numbers != null ? numbers.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; numbers != null && i < numbers.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(numbers[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Long answer = null;
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        Long val = exchange.getContext().getTypeConverter().tryConvertTo(Long.class, exchange, i);
                        if (val != null) {
                            if (answer == null) {
                                answer = val;
                            }
                            answer = Math.min(answer, val);
                        }
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "min(" + Arrays.toString(numbers) + ")";
            }
        };
    }

    /**
     * An expression that converts the expressions to number and returns the average number
     */
    public static Expression averageExpression(String[] numbers) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[numbers != null ? numbers.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; numbers != null && i < numbers.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(numbers[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Long answer = null;
                int counter = 0;
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        Long val = exchange.getContext().getTypeConverter().tryConvertTo(Long.class, exchange, i);
                        if (val != null) {
                            if (answer == null) {
                                answer = 0L;
                            }
                            answer += val;
                            counter++;
                        }
                    }
                }
                return answer != null ? answer / counter : null;
            }

            @Override
            public String toString() {
                return "average(" + Arrays.toString(numbers) + ")";
            }
        };
    }
}
