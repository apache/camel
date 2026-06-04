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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;

/**
 * Expression builder for collection and list functions used by the simple language.
 */
public final class CollectionExpressionBuilder {

    private CollectionExpressionBuilder() {
    }

    /**
     * An expression that returns the distinct values from the expressions
     */
    public static Expression distinctExpression(String[] values) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[values != null ? values.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; values != null && i < values.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(values[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Set<Object> answer = new LinkedHashSet<>();
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        answer.add(i);
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "distinct(" + Arrays.toString(values) + ")";
            }
        };
    }

    /**
     * An expression that returns the elements in reverse order
     */
    public static Expression reverseExpression(String[] values) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[values != null ? values.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; values != null && i < values.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(values[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List<Object> answer = new ArrayList<>();
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        answer.add(i);
                    }
                }
                Collections.reverse(answer);
                return answer;
            }

            @Override
            public String toString() {
                return "reverse(" + Arrays.toString(values) + ")";
            }
        };
    }

    /**
     * An expression that returns the elements in random order
     */
    public static Expression shuffleExpression(String[] values) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[values != null ? values.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; values != null && i < values.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(values[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List<Object> answer = new ArrayList<>();
                for (Expression exp : exps) {
                    Object o = exp.evaluate(exchange, Object.class);
                    // this may be an object that we can iterate
                    Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                    for (Object i : it) {
                        answer.add(i);
                    }
                }
                Collections.shuffle(answer);
                return answer;
            }

            @Override
            public String toString() {
                return "shuffle(" + Arrays.toString(values) + ")";
            }
        };
    }

    /**
     * An expression that creates an ArrayList
     */
    public static Expression listExpression(String[] values) {
        return new ExpressionAdapter() {

            private final Expression[] exps = new Expression[values != null ? values.length : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0; values != null && i < values.length; i++) {
                    Expression exp = context.resolveLanguage("simple").createExpression(values[i]);
                    exp.init(context);
                    exps[i] = exp;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List<Object> answer = new ArrayList<>(values != null ? values.length : 0);
                for (Expression exp : exps) {
                    Object val = exp.evaluate(exchange, Object.class);
                    answer.add(val);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "list(" + Arrays.toString(values) + ")";
            }
        };
    }

    /**
     * An expression that creates an LinkedHashMap
     */
    public static Expression mapExpression(String[] pairs) {
        return new ExpressionAdapter() {

            private final Expression[] keys = new Expression[pairs != null ? pairs.length / 2 : 0];
            private final Expression[] values = new Expression[pairs != null ? pairs.length / 2 : 0];

            @Override
            public void init(CamelContext context) {
                for (int i = 0, j = 0; pairs != null && i < pairs.length - 1; j++) {
                    String key = pairs[i];
                    String value = pairs[i + 1];
                    Expression exp = context.resolveLanguage("simple").createExpression(key);
                    exp.init(context);
                    keys[j] = exp;
                    exp = context.resolveLanguage("simple").createExpression(value);
                    exp.init(context);
                    values[j] = exp;
                    i = i + 2;
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Map<String, Object> answer = new LinkedHashMap<>(keys.length);
                for (int i = 0; i < keys.length; i++) {
                    String key = keys[i].evaluate(exchange, String.class);
                    Object val = values[i].evaluate(exchange, Object.class);
                    answer.put(key, val);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "map(" + Arrays.toString(pairs) + ")";
            }
        };
    }

    /**
     * Joins together the values from the expression
     */
    public static Expression joinExpression(final String expression, final String separator, final String prefix) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                exp = ExpressionBuilder.joinExpression(exp, separator, prefix);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                if (prefix != null) {
                    return "join(" + expression + "," + separator + "," + prefix + ")";
                } else {
                    return "join(" + expression + "," + separator + ")";
                }
            }
        };
    }

    /**
     * Split the String values from the expression using the given separator
     */
    public static Expression splitStringExpression(final String expression, final String separator) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                if (text == null) {
                    return null;
                }
                return text.split(separator);
            }

            @Override
            public String toString() {
                return "split(" + expression + "," + separator + ")";
            }
        };
    }

    /**
     * Sorts the expression
     */
    public static Expression sortExpression(final String expression, final boolean reverse) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List answer = new ArrayList<>();
                Object o = exp.evaluate(exchange, Object.class);
                // this may be an object that we can iterate
                Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                for (Object i : it) {
                    answer.add(i);
                }
                Collections.sort(answer);
                if (reverse) {
                    Collections.reverse(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "sort(" + expression + ")";
            }
        };
    }

    /**
     * For each value in the source expression then apply the function and return a list of responses from each function
     */
    public static Expression forEachExpression(final String source, final String function) {
        return new ExpressionAdapter() {
            private CamelContext context;
            private Expression exp1;
            private Expression exp2;

            @Override
            public void init(CamelContext context) {
                this.context = context;
                exp1 = context.resolveLanguage("simple").createExpression(source);
                exp1.init(context);
                exp2 = context.resolveLanguage("simple").createExpression(function);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List<Object> answer = new ArrayList<>();
                Object o = exp1.evaluate(exchange, Object.class);
                Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                for (Object i : it) {
                    // use a dummy exchange as the input is to be the message body
                    Exchange dummy = ExchangeHelper.createCopy(exchange, true);
                    dummy.getMessage().setBody(i);
                    Object out = exp2.evaluate(dummy, Object.class);
                    if (out != null) {
                        answer.add(out);
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "forEach(" + source + ", " + function + ")";
            }
        };
    }

    /**
     * Adds the result of the function to the source list
     */
    public static Expression listAddExpression(final String source, final String function) {
        return new ExpressionAdapter() {
            private CamelContext context;
            private Expression exp1;
            private Expression exp2;

            @Override
            public void init(CamelContext context) {
                this.context = context;
                exp1 = context.resolveLanguage("simple").createExpression(source);
                exp1.init(context);
                exp2 = context.resolveLanguage("simple").createExpression(function);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Collection<Object> col = exp1.evaluate(exchange, Collection.class);
                if (col != null) {
                    Object value = exp2.evaluate(exchange, Object.class);
                    if (value != null) {
                        col.add(value);
                    }
                }
                return col;
            }

            @Override
            public String toString() {
                return "listAdd(" + source + ", " + function + ")";
            }
        };
    }

    /**
     * Removes the result of the function from the source list
     */
    public static Expression listRemoveExpression(final String source, final String function) {
        return new ExpressionAdapter() {
            private CamelContext context;
            private Expression exp1;
            private Expression exp2;

            @Override
            public void init(CamelContext context) {
                this.context = context;
                exp1 = context.resolveLanguage("simple").createExpression(source);
                exp1.init(context);
                exp2 = context.resolveLanguage("simple").createExpression(function);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List<Object> list = exp1.evaluate(exchange, List.class);
                if (list != null) {
                    Object value = exp2.evaluate(exchange, Object.class);
                    if (value != null) {
                        boolean removed = list.remove(value);
                        if (!removed) {
                            Integer pos;
                            // special name to remove last
                            if ("last".equals(value)) {
                                pos = list.size() - 1;
                            } else {
                                // this may be an integer
                                pos = context.getTypeConverter().tryConvertTo(int.class, exchange, value);
                            }
                            if (pos != null) {
                                if (pos >= 0 && pos < list.size()) {
                                    list.remove((int) pos);
                                }
                            }
                        }
                    }
                }
                return list;
            }

            @Override
            public String toString() {
                return "listRemove(" + source + ", " + function + ")";
            }
        };
    }

    /**
     * Adds the result of the function to the source map
     */
    public static Expression mapAddExpression(final String source, final String key, final String function) {
        return new ExpressionAdapter() {
            private CamelContext context;
            private Expression exp1;
            private Expression exp2;

            @Override
            public void init(CamelContext context) {
                this.context = context;
                exp1 = context.resolveLanguage("simple").createExpression(source);
                exp1.init(context);
                exp2 = context.resolveLanguage("simple").createExpression(function);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Map<String, Object> map = exp1.evaluate(exchange, Map.class);
                if (map != null) {
                    Object value = exp2.evaluate(exchange, Object.class);
                    if (value != null) {
                        map.put(key, value);
                    }
                }
                return map;
            }

            @Override
            public String toString() {
                return "mapAdd(" + source + ", " + key + ", " + function + ")";
            }
        };
    }

    /**
     * Removes the result of the function from the source map
     */
    public static Expression mapRemoveExpression(final String source, final String key) {
        return new ExpressionAdapter() {
            private CamelContext context;
            private Expression exp1;

            @Override
            public void init(CamelContext context) {
                this.context = context;
                exp1 = context.resolveLanguage("simple").createExpression(source);
                exp1.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Map<String, Object> map = exp1.evaluate(exchange, Map.class);
                if (map != null) {
                    map.remove(key);
                }
                return map;
            }

            @Override
            public String toString() {
                return "mapRemove(" + source + ", " + key + ")";
            }
        };
    }

    /**
     * Filters the values from the source that matches the predicate function
     */
    public static Expression filterExpression(final String source, final String function) {
        return new ExpressionAdapter() {
            private CamelContext context;
            private Expression exp1;
            private Predicate exp2;

            @Override
            public void init(CamelContext context) {
                this.context = context;
                exp1 = context.resolveLanguage("simple").createExpression(source);
                exp1.init(context);
                exp2 = context.resolveLanguage("simple").createPredicate(function);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                List<Object> answer = new ArrayList<>();
                Object o = exp1.evaluate(exchange, Object.class);
                Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(o);
                for (Object i : it) {
                    // use a dummy exchange as the input is to be the message body
                    Exchange dummy = ExchangeHelper.createCopy(exchange, true);
                    dummy.getMessage().setBody(i);
                    if (exp2.matches(dummy)) {
                        answer.add(i);
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "filter(" + source + ", " + function + ")";
            }
        };
    }

    /**
     * Sets the message header with the given expression value
     */
    public static Expression setHeaderExpression(final String name, final String type, final String expression) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Class<?> clazz = Object.class;
                if (type != null) {
                    try {
                        clazz = classResolver.resolveMandatoryClass(type);
                    } catch (ClassNotFoundException e) {
                        throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                    }
                }
                Object value = exp.evaluate(exchange, clazz);
                if (value != null) {
                    exchange.getMessage().setHeader(name, value);
                } else {
                    exchange.getMessage().removeHeader(name);
                }
                // does not return anything
                return null;
            }

            @Override
            public String toString() {
                return "setHeader(" + name + "," + expression + ")";
            }
        };
    }

    /**
     * Sets the variable with the given expression value
     */
    public static Expression setVariableExpression(final String name, final String type, final String expression) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Class<?> clazz = Object.class;
                if (type != null) {
                    try {
                        clazz = classResolver.resolveMandatoryClass(type);
                    } catch (ClassNotFoundException e) {
                        throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                    }
                }
                Object value = exp.evaluate(exchange, clazz);
                if (value != null) {
                    exchange.setVariable(name, value);
                } else {
                    exchange.removeVariable(name);
                }
                // does not return anything
                return null;
            }

            @Override
            public String toString() {
                return "setVariable(" + name + "," + expression + ")";
            }
        };
    }

    /**
     * Returns a range of number between min and max (exclusive)
     */
    public static Expression rangeExpression(final String min, final String max) {
        return new ExpressionAdapter() {
            private Expression exp1;
            private Expression exp2;

            @Override
            public Object evaluate(Exchange exchange) {
                int num1 = exp1.evaluate(exchange, Integer.class);
                int num2 = exp2.evaluate(exchange, Integer.class);
                if (num1 >= 0 && num1 <= num2 && num1 != num2) {
                    List<Integer> answer = new ArrayList<>();
                    for (int i = num1; i < num2; i++) {
                        answer.add(i);
                    }
                    return answer;
                }
                return null;
            }

            @Override
            public void init(CamelContext context) {
                exp1 = ExpressionBuilder.simpleExpression(min);
                exp1.init(context);
                exp2 = ExpressionBuilder.simpleExpression(max);
                exp2.init(context);
            }

            @Override
            public String toString() {
                return "range(" + min + "," + max + ")";
            }
        };
    }
}
