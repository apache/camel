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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.ExpressionAdapter;
import org.apache.camel.language.bean.BeanLanguage;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;

/**
 * A helper class for working with <a href="http://camel.apache.org/expression.html">expressions</a>.
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object header = exchange.getIn().getHeader(headerName);
                if (header == null) {
                    // fall back on a property
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
     * @return an expression object which will return the inbound headers
     */
    public static Expression headersExpression() {
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (!exchange.hasOut()) {
                    return null;
                }

                Message out = exchange.getOut();
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
     * @return an expression object which will return the headers
     */
    public static Expression outHeadersExpression() {
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
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
     * Returns an expression for an exception set on the exchange
     * <p/>
     * Is used to get the caused exception that typically have been wrapped in some sort
     * of Camel wrapper exception
     * @param type the exception type
     * @see Exchange#getException(Class)
     * @return an expression object which will return the exception set on the exchange
     */
    public static Expression exchangeExceptionExpression(final Class<Exception> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException(type);
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    // must use exception iterator to walk it and find the type we are looking for
                    Iterator<Throwable> it = ObjectHelper.createExceptionIterator(exception);
                    while (it.hasNext()) {
                        Throwable e = it.next();
                        if (type.isInstance(e)) {
                            return type.cast(e);
                        }
                    }
                    // not found
                    return null;

                }
                return exception;
            }

            @Override
            public String toString() {
                return "exchangeException[" + type + "]";
            }
        };
    }

    /**
     * Returns an expression for the type converter
     *
     * @return an expression object which will return the type converter
     */
    public static Expression typeConverterExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getTypeConverter();
            }

            @Override
            public String toString() {
                return "typeConverter";
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
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
     * Returns an expression for the property value of exchange with the given name
     *
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     */
    public static Expression propertyExpression(final String propertyName) {
        return new ExpressionAdapter() {
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
     * Returns an expression for the properties of exchange
     *
     * @return an expression object which will return the properties
     */
    public static Expression propertiesExpression() {
        return new ExpressionAdapter() {
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
     * Returns an expression for the properties of the camel context
     *
     * @return an expression object which will return the properties
     */
    public static Expression camelContextPropertiesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getProperties();
            }

            @Override
            public String toString() {
                return "camelContextProperties";
            }
        };
    }
    
    /**
     * Returns an expression for the property value of the camel context with the given name
     *
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     */
    public static Expression camelContextPropertyExpression(final String propertyName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getProperties().get(propertyName);
            }

            @Override
            public String toString() {
                return "camelContextProperty(" + propertyName + ")";
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
     * @param defaultValue default value to return if no system property exists
     * @return an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(final String propertyName,
                                                      final String defaultValue) {
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
     * Returns the expression for the exchanges inbound message body type
     */
    public static Expression bodyTypeExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getBody().getClass();
            }

            @Override
            public String toString() {
                return "bodyType";
            }
        };
    }

    /**
     * Returns the expression for the out messages body
     */
    public static Expression outBodyExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (exchange.hasOut()) {
                    return exchange.getOut().getBody();
                } else {
                    return null;
                }
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (exchange.hasOut()) {
                    return exchange.getOut().getBody(type);
                } else {
                    return null;
                }
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
    @SuppressWarnings("unchecked")
    public static Expression convertToExpression(final Expression expression, final Class type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return expression.evaluate(exchange, type);
            }

            @Override
            public String toString() {
                return "" + expression + ".convertTo(" + type.getCanonicalName() + ".class)";
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type the type
     * expression is evaluted to
     */
    public static Expression convertToExpression(final Expression expression, final Expression type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return expression.evaluate(exchange, type.evaluate(exchange, Object.class).getClass());
            }

            @Override
            public String toString() {
                return "" + expression + ".convertToEvaluatedType(" + type + ")";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the
     * given token
     */
    public static Expression tokenizeExpression(final Expression expression,
                                                final String token) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
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
    public static Expression regexTokenizeExpression(final Expression expression,
                                                     final String regexTokenizer) {
        final Pattern pattern = Pattern.compile(regexTokenizer);
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
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

    /**
     * Returns a sort expression which will sort the expression with the given comparator.
     * <p/>
     * The expression is evaluted as a {@link List} object to allow sorting.
     */
    @SuppressWarnings("unchecked")
    public static Expression sortExpression(final Expression expression, final Comparator comparator) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                List list = expression.evaluate(exchange, List.class);
                Collections.sort(list, comparator);
                return list;
            }

            @Override
            public String toString() {
                return "sort(" + expression + " by: " + comparator + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex
     * replaceAll to transform the String and return the result
     */
    public static Expression regexReplaceAll(final Expression expression,
                                             final String regex, final String replacement) {
        final Pattern pattern = Pattern.compile(regex);
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = expression.evaluate(exchange, String.class);
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = expression.evaluate(exchange, String.class);
                String replacement = replacementExpression.evaluate(exchange, String.class);
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return left.evaluate(exchange, String.class) + right.evaluate(exchange, String.class);
            }

            @Override
            public String toString() {
                return "append(" + left + ", " + right + ")";
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                StringBuffer buffer = new StringBuffer();
                for (Expression expression : expressions) {
                    String text = expression.evaluate(exchange, String.class);
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Date date;
                if ("now".equals(command)) {
                    date = new Date();
                } else if (command.startsWith("header.") || command.startsWith("in.header.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    date = exchange.getIn().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot find java.util.Date object at " + command);
                    }
                } else if (command.startsWith("out.header.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    date = exchange.getOut().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot find java.util.Date object at " + command);
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

    public static Expression simpleExpression(final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // resolve language using context to have a clear separation of packages
                // must call evalute to return the nested langauge evaluate when evaluating
                // stacked expressions
                Language language = exchange.getContext().resolveLanguage("simple");
                return language.createExpression(expression).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "simple(" + expression + ")";
            }
        };
    }

    public static Expression beanExpression(final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // resolve language using context to have a clear separation of packages
                // must call evaluate to return the nested language evaluate when evaluating
                // stacked expressions
                Language language = exchange.getContext().resolveLanguage("bean");
                return language.createExpression(expression).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "bean(" + expression + ")";
            }
        };
    }
    
    public static Expression beanExpression(final Class beanType, final String methodName) {
        return BeanLanguage.bean(beanType, methodName);        
    }

    public static Expression beanExpression(final String beanRef, final String methodName) {
        String expression = methodName != null ? beanRef + "." + methodName : beanRef;
        return beanExpression(expression);
    }

    private static Scanner getScanner(Exchange exchange, Object value) {
        String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);

        Scanner scanner = null;
        if (value instanceof Readable) {
            scanner = new Scanner((Readable)value);
        } else if (value instanceof InputStream) {
            scanner = charset == null ? new Scanner((InputStream)value) : new Scanner((InputStream)value, charset);
        } else if (value instanceof File) {
            try {
                scanner = charset == null ? new Scanner((File)value) : new Scanner((File)value, charset);
            } catch (FileNotFoundException e) {
                throw new RuntimeCamelException(e);
            }
        } else if (value instanceof String) {
            scanner = new Scanner((String)value);
        } else if (value instanceof ReadableByteChannel) {
            scanner = charset == null ? new Scanner((ReadableByteChannel)value) : new Scanner((ReadableByteChannel)value, charset);
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

}
