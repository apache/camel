/**
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.language.bean.BeanLanguage;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.processor.DeadLetterChannel;

/**
 * A helper class for working with <a href="http://activemq.apache.org/camel/expression.html">expressions</a>.
 *
 * @version $Revision$
 */
public final class ExpressionBuilder {

    /**
     * Utility classes should not have a public constructor.
     */
    private ExpressionBuilder() {
    }

    /**
     * Returns an expression for the header value with the given name
     *
     * @param headerName the name of the header the expression will return
     * @return an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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
     * Returns an expression for the inbound message headers
     *
     * @see Message#getHeaders()
     * @return an expression object which will return the inbound headers
     */
    public static Expression headersExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeaders();
            }

            @Override
            public String toString() {
                return "headers";
            }
        };
    }

    /**
     * Returns an expression for the out header value with the given name
     *
     * @param headerName the name of the header the expression will return
     * @return an expression object which will return the header value
     */
    public static Expression outHeaderExpression(final String headerName) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Message out = exchange.getOut(false);
                if (out == null) {
                    return null;
                }
                Object header = out.getHeader(headerName);
                if (header == null) {
                    // lets try the exchange header
                    header = exchange.getProperty(headerName);
                }
                return header;
            }

            @Override
            public String toString() {
                return "outHeader(" + headerName + ")";
            }
        };
    }

    /**
     * Returns an expression for the outbound message headers
     *
     * @see Message#getHeaders()
     * @return an expression object which will return the headers
     */
    public static Expression outHeadersExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getOut().getHeaders();
            }

            @Override
            public String toString() {
                return "outHeaders";
            }
        };
    }

    /**
     * Returns an expression for an exception set on the exchange
     *
     * @see Exchange#getException()
     * @return an expression object which will return the exception set on the exchange
     */
    public static Expression exchangeExceptionExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Throwable exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(DeadLetterChannel.EXCEPTION_CAUSE_PROPERTY, Throwable.class);
                }
                return exception;
            }

            @Override
            public String toString() {
                return "exchangeException";
            }
        };
    }   
    
    /**
     * Returns an expression for an exception message set on the exchange
     *
     * @see <tt>Exchange.getException().getMessage()</tt>
     * @return an expression object which will return the exception message set on the exchange
     */
    public static Expression exchangeExceptionMessageExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Throwable exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(DeadLetterChannel.EXCEPTION_CAUSE_PROPERTY, Throwable.class);
                }
                return exception != null ? exception.getMessage() : null;
            }

            @Override
            public String toString() {
                return "exchangeExceptionMessage";
            }
        };
    }

    /**
     * Returns an expression for the property value with the given name
     *
     * @see Exchange#getProperty(String)
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     */
    public static Expression propertyExpression(final String propertyName) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperty(propertyName);
            }

            @Override
            public String toString() {
                return "property(" + propertyName + ")";
            }
        };
    }


    /**
     * Returns an expression for the property value with the given name
     *
     * @see Exchange#getProperties()
     * @return an expression object which will return the properties
     */
    public static Expression propertiesExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperties();
            }

            @Override
            public String toString() {
                return "properties";
            }
        };
    }

    /**
     * Returns an expression for a system property value with the given name
     *
     * @param propertyName the name of the system property the expression will return
     * @return an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(final String propertyName) {
        return systemPropertyExpression(propertyName, null);
    }

    /**
     * Returns an expression for a system property value with the given name
     *
     * @param propertyName the name of the system property the expression will return
     * @return an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(final String propertyName,
                                                      final String defaultValue) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return System.getProperty(propertyName, defaultValue);
            }

            @Override
            public String toString() {
                return "systemProperty(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for the constant value
     *
     * @param value the value the expression will return
     * @return an expression object which will return the constant value
     */
    public static Expression constantExpression(final Object value) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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
    public static Expression bodyExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getBody();
            }

            @Override
            public String toString() {
                return "body";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     */
    public static <T> Expression bodyExpression(final Class<T> type) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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
    public static Expression outBodyExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Message out = exchange.getOut(false);
                if (out == null) {
                    return null;
                }
                return out.getBody();
            }

            @Override
            public String toString() {
                return "outBody";
            }
        };
    }

    /**
     * Returns the expression for the exchanges outbound message body converted
     * to the given type
     */
    public static <T> Expression outBodyExpression(final Class<T> type) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Message out = exchange.getOut(false);
                if (out == null) {
                    return null;
                }
                return out.getBody(type);
            }

            @Override
            public String toString() {
                return "outBodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the fault messages body
     */
    public static Expression faultBodyExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getFault().getBody();
            }

            @Override
            public String toString() {
                return "faultBody";
            }
        };
    }

    /**
     * Returns the expression for the exchanges fault message body converted
     * to the given type
     */
    public static <T> Expression faultBodyExpression(final Class<T> type) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getFault().getBody(type);
            }

            @Override
            public String toString() {
                return "faultBodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchange
     */
    public static Expression exchangeExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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
    public static Expression inMessageExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn();
            }

            @Override
            public String toString() {
                return "inMessage";
            }
        };
    }

    /**
     * Returns the expression for the OUT message
     */
    public static Expression outMessageExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getOut();
            }

            @Override
            public String toString() {
                return "outMessage";
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type
     */
    public static Expression convertTo(final Expression expression, final Class type) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, exchange, value);
            }

            @Override
            public String toString() {
                return "" + expression + ".convertTo(" + type.getName() + ".class)";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the
     * given token
     */
    public static Expression tokenizeExpression(final Expression expression,
                                                final String token) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange);
                Scanner scanner = getScanner(exchange, value);
                scanner.useDelimiter(token);
                return scanner;
            }

            @Override
            public String toString() {
                return "tokenize(" + expression + ", " + token + ")";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the
     * given regex
     */
    public static Expression regexTokenize(final Expression expression,
                                           final String regexTokenizer) {
        final Pattern pattern = Pattern.compile(regexTokenizer);
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange);
                Scanner scanner = getScanner(exchange, value);
                scanner.useDelimiter(regexTokenizer);
                return scanner;
            }

            @Override
            public String toString() {
                return "regexTokenize(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    private static Scanner getScanner(Exchange exchange, Object value) {
        String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);

        Scanner scanner = null;
        if (value instanceof Readable) {
            scanner = new Scanner((Readable)value);
        } else if (value instanceof InputStream) {
            scanner = charset == null ? new Scanner((InputStream)value)
                : new Scanner((InputStream)value, charset);
        } else if (value instanceof File) {
            try {
                scanner = charset == null ? new Scanner((File)value) : new Scanner((File)value, charset);
            } catch (FileNotFoundException e) {
                throw new RuntimeCamelException(e);
            }
        } else if (value instanceof String) {
            scanner = new Scanner((String)value);
        } else if (value instanceof ReadableByteChannel) {
            scanner = charset == null ? new Scanner((ReadableByteChannel)value)
                : new Scanner((ReadableByteChannel)value, charset);
        }

        if (scanner == null) {
            // value is not a suitable type, try to convert value to a string
            String text = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
            if (text != null) {
                scanner = new Scanner(text);
            }
        }
        
        if (scanner == null) {
            scanner = new Scanner("");
        }
        return scanner;
    }

    /**
     * Transforms the expression into a String then performs the regex
     * replaceAll to transform the String and return the result
     */
    public static Expression regexReplaceAll(final Expression expression,
                                             final String regex, final String replacement) {
        final Pattern pattern = Pattern.compile(regex);
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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
     * Transforms the expression into a String then performs the regex
     * replaceAll to transform the String and return the result
     */
    public static Expression regexReplaceAll(final Expression expression,
                                             final String regex, final Expression replacementExpression) {

        final Pattern pattern = Pattern.compile(regex);
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                String text = evaluateStringExpression(expression, exchange);
                String replacement = evaluateStringExpression(replacementExpression, exchange);
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
    public static Expression append(final Expression left, final Expression right) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return evaluateStringExpression(left, exchange) + evaluateStringExpression(right, exchange);
            }

            @Override
            public String toString() {
                return "append(" + left + ", " + right + ")";
            }
        };
    }

    /**
     * Evaluates the expression on the given exchange and returns the String
     * representation
     *
     * @param expression the expression to evaluate
     * @param exchange the exchange to use to evaluate the expression
     * @return the String representation of the expression or null if it could
     *         not be evaluated
     */
    public static String evaluateStringExpression(Expression expression, Exchange exchange) {
        Object value = expression.evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
    }

    /**
     * Returns an expression for the given system property
     */
    public static Expression systemProperty(final String name) {
        return systemProperty(name, null);
    }

    /**
     * Returns an expression for the given system property
     */
    public static Expression systemProperty(final String name, final String defaultValue) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return System.getProperty(name, defaultValue);
            }
        };
    }

    /**
     * Returns an expression which returns the string concatenation value of the various
     * expressions
     *
     * @param expressions the expression to be concatenated dynamically
     * @return an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions) {
        return concatExpression(expressions, null);
    }

    /**
     * Returns an expression which returns the string concatenation value of the various
     * expressions
     *
     * @param expressions the expression to be concatenated dynamically
     * @param expression the text description of the expression
     * @return an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions, final String expression) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                StringBuffer buffer = new StringBuffer();
                for (Expression expression : expressions) {
                    String text = evaluateStringExpression(expression, exchange);
                    if (text != null) {
                        buffer.append(text);
                    }
                }
                return buffer.toString();
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return expression;
                } else {
                    return "concat" + expressions;
                }
            }
        };
    }

    /**
     * Returns an Expression for the inbound message id
     */
    public static Expression messageIdExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getMessageId();
            }

            @Override
            public String toString() {
                return "messageId";
            }
        };
    }

    public static Expression dateExpression(final String command, final String pattern) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                Date date;
                if ("now".equals(command)) {
                    date = new Date();
                } else if (command.startsWith("header.") || command.startsWith("in.header.")) {
                    String key = command.substring(command.lastIndexOf(".") + 1);
                    date = exchange.getIn().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Could not find java.util.Date object at " + command);
                    }
                } else if (command.startsWith("out.header.")) {
                    String key = command.substring(command.lastIndexOf(".") + 1);
                    date = exchange.getOut().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Could not find java.util.Date object at " + command);
                    }
                } else {
                    throw new IllegalArgumentException("Command not supported for dateExpression: " + command);
                }

                SimpleDateFormat df = new SimpleDateFormat(pattern);
                return df.format(date);
            }

            @Override
            public String toString() {
                return "date(" + command + ":" + pattern + ")";
            }
        };
    }

    public static Expression simpleExpression(final String simple) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                // must call evalute to return the nested langauge evaluate when evaluating
                // stacked expressions
                return SimpleLanguage.simple(simple).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "simple(" + simple + ")";
            }
        };
    }

    public static Expression beanExpression(final String expression) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                // must call evalute to return the nested langauge evaluate when evaluating
                // stacked expressions
                return BeanLanguage.bean(expression).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "bean(" + expression + ")";
            }
        };
    }

    public static Expression beanExpression(final String beanRef, final String methodName) {
        String expression = methodName != null ? beanRef + "." + methodName : beanRef;
        return beanExpression(expression);
    }

}
