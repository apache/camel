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

import java.io.InputStream;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.StreamCache;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

/**
 * Expression builder for string-manipulation functions used by the simple language.
 */
public final class StringExpressionBuilder {

    private StringExpressionBuilder() {
    }

    /**
     * Safe quotes the given expressions (uses message body if expression is null) if necessary.
     */
    public static Expression safeQuoteExpression(final String expression) {
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
                Object value;
                if (exp != null) {
                    value = exp.evaluate(exchange, Object.class);
                } else {
                    value = exchange.getMessage().getBody(Object.class);
                }
                if (value != null) {
                    String type = kindOfType(value);
                    if ("string".equals(type) || "array".equals(type) || "object".equals(type)) {
                        String body = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, value);
                        body = StringHelper.removeLeadingAndEndingQuotes(body);
                        value = StringQuoteHelper.doubleQuote(body);
                    }
                }
                return value;
            }

            private String kindOfType(Object value) {
                Class<?> type = value.getClass();
                if (ObjectHelper.isNumericType(type)) {
                    return "number";
                } else if (boolean.class == type || Boolean.class == type) {
                    return "boolean";
                } else if (value instanceof CharSequence) {
                    return "string";
                } else if (ObjectHelper.isPrimitiveArrayType(type) || value instanceof Collection
                        || value instanceof Map<?, ?>) {
                    return "array";
                } else {
                    return "object";
                }
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "safeQuote(" + expression + ")";
                } else {
                    return "safeQuote()";
                }
            }
        };
    }

    /**
     * Double quotes the given expressions (uses message body if expression is null)
     */
    public static Expression quoteExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null && !StringHelper.isDoubleQuoted(value)) {
                    value = StringHelper.removeLeadingAndEndingQuotes(value);
                    value = StringQuoteHelper.doubleQuote(value);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "quote(" + expression + ")";
                } else {
                    return "quote()";
                }
            }
        };
    }

    /**
     * Un quotes the given expressions (uses message body if expression is null)
     */
    public static Expression unquoteExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    value = StringHelper.removeLeadingAndEndingQuotes(value);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "unquote(" + expression + ")";
                } else {
                    return "unquote()";
                }
            }
        };
    }

    /**
     * Trims the given expressions (uses message body if expression is null)
     */
    public static Expression trimExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    value = value.trim();
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "trim(" + expression + ")";
                } else {
                    return "trim()";
                }
            }
        };
    }

    /**
     * Capitalizes the given expressions (uses message body if expression is null)
     */
    public static Expression capitalizeExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    value = StringHelper.capitalizeAll(value);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "capitalize(" + expression + ")";
                } else {
                    return "capitalize()";
                }
            }
        };
    }

    /**
     * Pad the expression
     */
    public static Expression padExpression(final String expression, final String length, final String separator) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression len;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                len = context.resolveLanguage("simple").createExpression(length);
                len.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String answer = exp.evaluate(exchange, String.class);
                Integer width = len.evaluate(exchange, Integer.class);
                String sep = separator;
                if (sep == null || sep.isEmpty()) {
                    sep = " ";
                }

                int max = Math.abs(width);
                while (max > answer.length()) {
                    if (width > 0) {
                        answer = answer + sep;
                    } else {
                        answer = sep + answer;
                    }
                }
                return answer;
            }

            @Override
            public String toString() {
                return "pad(" + exp + "," + length + ")";
            }
        };
    }

    /**
     * String concats the two expressions.
     */
    public static Expression concatExpression(final String right, final String left, String separator) {
        return new ExpressionAdapter() {
            private Expression exp1;
            private Expression exp2;

            @Override
            public void init(CamelContext context) {
                exp1 = context.resolveLanguage("simple").createExpression(right);
                exp1.init(context);
                exp2 = context.resolveLanguage("simple").createExpression(left);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String value1 = exp1.evaluate(exchange, String.class);
                String value2 = exp2.evaluate(exchange, String.class);
                if (value1 != null && value2 != null) {
                    return value1 + (separator != null ? separator : "") + value2;
                } else {
                    return value1 != null ? value1 : value2;
                }
            }

            @Override
            public String toString() {
                return "concat(" + right + "," + left + ")";
            }
        };
    }

    /**
     * Uppercases the given expressions (uses message body if expression is null)
     */
    public static Expression uppercaseExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    value = value.toUpperCase(Locale.ENGLISH);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "uppercase(" + expression + ")";
                } else {
                    return "uppercase()";
                }
            }
        };
    }

    /**
     * Lowercases the given expressions (uses message body if expression is null)
     */
    public static Expression lowercaseExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    value = value.toLowerCase(Locale.ENGLISH);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "lowercase(" + expression + ")";
                } else {
                    return "lowercase()";
                }
            }
        };
    }

    /**
     * Returns the size of the expression (number of elements in collection/map; otherwise 1)
     */
    public static Expression sizeExpression(final String expression) {
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
                Object body;
                if (exp != null) {
                    body = exp.evaluate(exchange, Object.class);
                } else {
                    body = exchange.getMessage().getBody();
                }
                if (body != null) {
                    if (body instanceof byte[] arr) {
                        return arr.length;
                    } else if (body instanceof char[] arr) {
                        return arr.length;
                    } else if (body instanceof int[] arr) {
                        return arr.length;
                    } else if (body instanceof long[] arr) {
                        return arr.length;
                    } else if (body instanceof double[] arr) {
                        return arr.length;
                    } else if (body instanceof String[] arr) {
                        return arr.length;
                    } else if (body instanceof Collection<?> c) {
                        return c.size();
                    } else if (body instanceof Map<?, ?> m) {
                        return m.size();
                    } else {
                        return 1;
                    }
                }
                return 0;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "size(" + expression + ")";
                } else {
                    return "size()";
                }
            }
        };
    }

    /**
     * Returns the length of the expression (length of payload in bytes)
     */
    public static Expression lengthExpression(final String expression) {
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
                Object body;
                if (exp != null) {
                    body = exp.evaluate(exchange, Object.class);
                } else {
                    body = exchange.getMessage().getBody();
                }
                try {
                    if (body instanceof byte[] arr) {
                        return arr.length;
                    } else if (body instanceof char[] arr) {
                        return arr.length;
                    } else if (body instanceof int[] arr) {
                        return arr.length;
                    } else if (body instanceof long[] arr) {
                        return arr.length;
                    } else if (body instanceof double[] arr) {
                        return arr.length;
                    } else if (body instanceof String[] arr) {
                        return arr.length;
                    } else if (body instanceof StreamCache sc) {
                        return (int) sc.length();
                    } else {
                        // first read as stream
                        InputStream is = null;
                        try {
                            is = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);
                            int len = 0;
                            while (is.read() != -1) {
                                len++;
                            }
                            return len;
                        } catch (Exception e) {
                            // ignore
                        } finally {
                            IOHelper.close(is);
                        }
                        // fallback to use string based
                        String data = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, body);
                        if (data != null) {
                            return data.length();
                        }
                    }
                } finally {
                    if (body instanceof StreamCache streamCache) {
                        streamCache.reset();
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "length(" + expression + ")";
                } else {
                    return "length()";
                }
            }
        };
    }

    /**
     * Normalizes the whitespaces in the given expressions (uses message body if expression is null)
     */
    public static Expression normalizeWhitespaceExpression(final String expression) {
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
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    value = StringHelper.normalizeWhitespace(value);
                }
                return value;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "normalizeWhitespace(" + expression + ")";
                } else {
                    return "normalizeWhitespace()";
                }
            }
        };
    }

    /**
     * Replaces string values from the expression
     */
    public static Expression replaceExpression(final String expression, final String from, final String to) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                exp = ExpressionBuilder.replaceAll(exp, from, to);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "replace(" + expression + "," + from + "," + to + ")";
            }
        };
    }

    /**
     * Substring string values from the expression
     */
    public static Expression substringExpression(final String expression, final String head, final String tail) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression exp1;
            private Expression exp2;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                exp1 = ExpressionBuilder.simpleExpression(head);
                exp1.init(context);
                exp2 = ExpressionBuilder.simpleExpression(tail);
                exp2.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                int num1 = exp1.evaluate(exchange, Integer.class);
                int num2 = exp2.evaluate(exchange, Integer.class);
                if (num1 < 0 && num2 == 0) {
                    // if there is only one value and its negative then we want to clip from tail
                    num2 = num1;
                    num1 = 0;
                }
                num1 = Math.abs(num1);
                num2 = Math.abs(num2);
                return ExpressionBuilder.substring(exp, num1, num2).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "substring(" + expression + "," + head + "," + tail + ")";
            }
        };
    }

    /**
     * Returns the substring from the given expression that comes before
     */
    public static Expression substringBeforeExpression(final String expression, final String before) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression expBefore;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                expBefore = ExpressionBuilder.simpleExpression(before);
                expBefore.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String body = exp.evaluate(exchange, String.class);
                if (body == null) {
                    return null;
                }
                String bef = expBefore.evaluate(exchange, String.class);
                return StringHelper.before(body, bef);
            }

            @Override
            public String toString() {
                return "substringBefore(" + expression + "," + before + ")";
            }
        };
    }

    /**
     * Returns the substring from the given expression that comes after
     */
    public static Expression substringAfterExpression(final String expression, final String after) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression expAfter;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                expAfter = ExpressionBuilder.simpleExpression(after);
                expAfter.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String body = exp.evaluate(exchange, String.class);
                if (body == null) {
                    return null;
                }
                String aft = expAfter.evaluate(exchange, String.class);
                return StringHelper.after(body, aft);
            }

            @Override
            public String toString() {
                return "substringAfter(" + expression + "," + after + ")";
            }
        };
    }

    /**
     * Returns the substring from the given expression that are between after and before
     */
    public static Expression substringBetweenExpression(
            final String expression, final String after,
            final String before) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression expAfter;
            private Expression expBefore;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                expAfter = ExpressionBuilder.simpleExpression(after);
                expAfter.init(context);
                expBefore = ExpressionBuilder.simpleExpression(before);
                expBefore.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String body = exp.evaluate(exchange, String.class);
                if (body == null) {
                    return null;
                }
                String aft = expAfter.evaluate(exchange, String.class);
                String bef = expBefore.evaluate(exchange, String.class);
                return StringHelper.between(body, aft, bef);
            }

            @Override
            public String toString() {
                return "substringBetween(" + expression + "," + after + "," + before + ")";
            }
        };
    }

    /**
     * Whether the expression matches the pattern
     */
    public static Expression containsExpression(final String expression, final String pattern) {
        return new ExpressionAdapter() {
            private Expression right;
            private Expression left;

            @Override
            public void init(CamelContext context) {
                left = context.resolveLanguage("simple").createExpression(expression);
                left.init(context);
                right = context.resolveLanguage("simple").createExpression(pattern);
                right.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return PredicateBuilder.containsIgnoreCase(left, right).matches(exchange);
            }

            @Override
            public String toString() {
                return "contains(" + expression + "," + pattern + ")";
            }
        };
    }
}
