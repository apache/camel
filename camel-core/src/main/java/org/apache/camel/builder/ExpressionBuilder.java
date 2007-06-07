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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * @version $Revision: $
 */
public class ExpressionBuilder {


    /**
     * Returns an expression for the header value with the given name
     *
     * @param headerName the name of the header the expression will return
     * @return an expression object which will return the header value
     */
    public static <E extends Exchange> Expression<E> headerExpression(final String headerName) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                Object header = exchange.getIn().getHeader(headerName);
                if (header == null) {
                    // lets try the exchange header
                    header = exchange.getProperty(headerName);
                }
                return header;
            }
            

            @Override
            public String toString() {
                return "header(" + headerName + ")";
            }
        };
    }

    /**
     * Returns an expression for the property value with the given name
     *
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     */
    public static <E extends Exchange> Expression<E> propertyExpression(final String propertyName) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getProperty(propertyName);
            }

            @Override
            public String toString() {
                return "property(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for the contant value
     *
     * @param value the value the expression will return
     * @return an expression object which will return the constant value
     */
    public static <E extends Exchange> Expression<E> constantExpression(final Object value) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return value;
            }

            @Override
            public String toString() {
                return "" + value;
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body
     */
    public static <E extends Exchange> Expression<E> bodyExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getBody();
            }

            @Override
            public String toString() {
                return "body";
            }
        };
    }


    /**
     * Returns the expression for the exchanges inbound message body converted to the given type
     */
    public static <E extends Exchange, T> Expression<E> bodyExpression(final Class<T> type) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getBody(type);
            }

            @Override
            public String toString() {
                return "bodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the out messages body
     */
    public static <E extends Exchange> Expression<E> outBodyExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getOut().getBody();
            }

            @Override
            public String toString() {
                return "outBody";
            }
        };
    }

    /**
     * Returns the expression for the exchange
     */
    public static <E extends Exchange> Expression<E> exchangeExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange;
            }

            @Override
            public String toString() {
                return "exchange";
            }
        };
    }

    /**
     * Returns the expression for the IN message
     */
    public static <E extends Exchange> Expression<E> inMessageExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn();
            }

            @Override
            public String toString() {
                return "inMessage";
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type
     */
    public static <E extends Exchange> Expression<E> convertTo(final Expression expression, final Class type) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                Object value = expression.evaluate(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, value);
            }

            @Override
            public String toString() {
                return "convertTo(" + expression + ", " + type + ")";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the given token
     */
    public static <E extends Exchange> Expression<E> tokenizeExpression(final Expression<E> expression, final String token) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                String text = evaluateStringExpression(expression, exchange);
                if (text == null) {
                    return null;
                }
                StringTokenizer iter = new StringTokenizer(text, token);
                List<String> answer = new ArrayList<String>();
                while (iter.hasMoreTokens()) {
                    answer.add(iter.nextToken());
                }
                return answer;
            }

            @Override
            public String toString() {
                return "tokenize(" + expression + ", " + token + ")";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the given regex
     */
    public static <E extends Exchange> Expression<E> regexTokenize(final Expression<E> expression, String regexTokenizer) {
        final Pattern pattern = Pattern.compile(regexTokenizer);
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                String text = evaluateStringExpression(expression, exchange);
                if (text == null) {
                    return null;
                }
                return Arrays.asList(pattern.split(text));
            }

            @Override
            public String toString() {
                return "regexTokenize(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex replaceAll to transform the String and return the result
     */
    public static <E extends Exchange> Expression<E> regexReplaceAll(final Expression<E> expression, String regex, final String replacement) {
        final Pattern pattern = Pattern.compile(regex);
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                String text = evaluateStringExpression(expression, exchange);
                if (text == null) {
                    return null;
                }
                return pattern.matcher(text).replaceAll(replacement);
            }

            @Override
            public String toString() {
                return "regexReplaceAll(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex replaceAll to transform the String and return the result
     */
    public static <E extends Exchange> Expression<E> regexReplaceAll(final Expression<E> expression, String regex, final Expression<E> replacementExpression) {
        final Pattern pattern = Pattern.compile(regex);
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                String text = evaluateStringExpression(expression, exchange);
                String replacement = evaluateStringExpression(replacementExpression, exchange);;
                if (text == null || replacement == null) {
                    return null;
                }
                return pattern.matcher(text).replaceAll(replacement);
            }

            @Override
            public String toString() {
                return "regexReplaceAll(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }


    /**
     * Appends the String evaluations of the two expressions together
     */
    public static <E extends Exchange> Expression<E> append(final Expression<E> left, final Expression<E> right) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return evaluateStringExpression(left, exchange) + evaluateStringExpression(right, exchange);
            }

            @Override
            public String toString() {
                return "append(" + left + ", " + right + ")";
            }
        };
    }

    /**
     * Evaluates the expression on the given exchange and returns the String representation
     *
     * @param expression the expression to evaluate
     * @param exchange the exchange to use to evaluate the expression
     * @return the String representation of the expression or null if it could not be evaluated
     */
    public static <E extends Exchange> String evaluateStringExpression(Expression<E> expression, E exchange) {
        Object value = expression.evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(String.class, value);
    }

    /**
     * Returns an expression for the given system property
     */
    public static <E extends Exchange> Expression<E> systemProperty(final String name) {
        return systemProperty(name, null);
    }

    /**
     * Returns an expression for the given system property
     */
    public static <E extends Exchange> Expression<E> systemProperty(final String name, final String defaultValue) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return System.getProperty(name, defaultValue);
            }
        };
    }
}
