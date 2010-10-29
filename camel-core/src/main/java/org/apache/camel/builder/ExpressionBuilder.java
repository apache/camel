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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Producer;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.ExpressionAdapter;
import org.apache.camel.language.bean.BeanLanguage;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;

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
     * Returns an expression for the inbound message attachments
     *
     * @return an expression object which will return the inbound message attachments
     */
    public static Expression attachmentsExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getAttachments();
            }

            @Override
            public String toString() {
                return "attachments";
            }
        };
    }

    /**
     * Returns an expression for the header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
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
     * Returns an expression for the header value with the given name converted to the given type
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param headerName the name of the header the expression will return
     * @param type the type to convert to
     * @return an expression object which will return the header value
     */
    public static <T> Expression headerExpression(final String headerName, final Class<T> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object header = exchange.getIn().getHeader(headerName, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(headerName, type);
                }
                return header;
            }

            @Override
            public String toString() {
                return "headerAs(" + headerName + ", " + type + ")";
            }
        };
    }

    /**
     * Returns an expression for the header value with the given name converted to the given type
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param headerName the name of the header the expression will return
     * @param name the type to convert to as a FQN class name
     * @return an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName, final String name) {
        return new ExpressionAdapter() {
            @SuppressWarnings("unchecked")
            public Object evaluate(Exchange exchange) {
                Class type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(name);
                } catch (ClassNotFoundException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }

                Object header = exchange.getIn().getHeader(headerName, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(headerName, type);
                }
                return header;
            }

            @Override
            public String toString() {
                return "headerAs(" + headerName + ", " + name + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message header invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the header in a simple OGNL syntax
     */
    public static Expression headersOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // try with full name first
                Object header = exchange.getIn().getHeader(ognl);
                if (header != null) {
                    return header;
                }

                // split into first name
                List<String> methods = OgnlHelper.splitOgnl(ognl);
                // remove any OGNL operators so we got the pure key name
                String key = OgnlHelper.removeOperators(methods.get(0));

                header = exchange.getIn().getHeader(key);
                if (header == null) {
                    return null;
                }
                // the remainder is the rest of the ognl without the key
                String remainder = ObjectHelper.after(ognl, key);
                return new MethodCallExpression(header, remainder).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "headerOgnl(" + ognl + ")";
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
     * <p/>
     * Will fallback and look in properties if not found in headers.
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
                    return ObjectHelper.getException(type, exception);
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
     * Returns the expression for the exchanges exception invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression exchangeExceptionOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }
                
                if (exception == null) {
                    return null;
                }
                return new MethodCallExpression(exception, ognl).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "exchangeExceptionOgnl(" + ognl + ")";
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
     * Returns an expression for the {@link org.apache.camel.spi.Registry}
     *
     * @return an expression object which will return the registry
     */
    public static Expression registryExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getRegistry();
            }

            @Override
            public String toString() {
                return "registry";
            }
        };
    }

    /**
     * Returns an expression for the {@link org.apache.camel.CamelContext}
     *
     * @return an expression object which will return the camel context
     */
    public static Expression camelContextExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext();
            }

            @Override
            public String toString() {
                return "camelContext";
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
     * Returns an expression for an exception stacktrace set on the exchange
     *
     * @return an expression object which will return the exception stacktrace set on the exchange
     */
    public static Expression exchangeExceptionStackTraceExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }
                if (exception != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    exception.printStackTrace(pw);
                    IOHelper.close(pw);
                    IOHelper.close(sw);
                    return sw.toString();
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "exchangeExceptionStackTrace";
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
     * Returns an expression for a system environment value with the given name
     *
     * @param propertyName the name of the system environment the expression will return
     * @return an expression object which will return the system property value
     */
    public static Expression systemEnvironmentExpression(final String propertyName) {
        return systemEnvironmentExpression(propertyName, null);
    }

    /**
     * Returns an expression for a system environment value with the given name
     *
     * @param propertyName the name of the system environment the expression will return
     * @param defaultValue default value to return if no system environment exists
     * @return an expression object which will return the system environment value
     */
    public static Expression systemEnvironmentExpression(final String propertyName,
                                                         final String defaultValue) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = System.getenv(propertyName);
                if (answer == null) {
                    answer = defaultValue;
                }
                return answer;
            }

            @Override
            public String toString() {
                return "systemEnvironment(" + propertyName + ")";
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
     * Returns the expression for the exchanges inbound message body invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression bodyOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                if (body == null) {
                    return null;
                }
                return new MethodCallExpression(body, ognl).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "bodyOgnl(" + ognl + ")";
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
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     */
    public static Expression bodyExpression(final String name) {
        return new ExpressionAdapter() {
            @SuppressWarnings("unchecked")
            public Object evaluate(Exchange exchange) {
                Class type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(name);
                } catch (ClassNotFoundException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
                return exchange.getIn().getBody(type);
            }

            @Override
            public String toString() {
                return "bodyAs[" + name + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     */
    public static Expression mandatoryBodyExpression(final String name) {
        return new ExpressionAdapter() {
            @SuppressWarnings("unchecked")
            public Object evaluate(Exchange exchange) {
                Class type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(name);
                } catch (ClassNotFoundException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + name + "]";
            }
        };
    }

    /**
     * Returns the expression for the current thread name
     */
    public static Expression threadNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return Thread.currentThread().getName();
            }

            @Override
            public String toString() {
                return "threadName";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type.
     * <p/>
     * Does <b>not</b> allow null bodies.
     */
    public static <T> Expression mandatoryBodyExpression(final Class<T> type) {
        return mandatoryBodyExpression(type, false);
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     *
     * @param type the type
     * @param nullBodyAllowed whether null bodies is allowed and if so a null is returned,
     *                        otherwise an exception is thrown
     */
    public static <T> Expression mandatoryBodyExpression(final Class<T> type, final boolean nullBodyAllowed) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (nullBodyAllowed) {
                    if (exchange.getIn().getBody() == null) {
                        return null;
                    }

                    // if its a bean invocation then if it has no arguments then it should be threaded as null body allowed
                    BeanInvocation bi = exchange.getIn().getBody(BeanInvocation.class);
                    if (bi != null && (bi.getArgs() == null || bi.getArgs().length == 0 || bi.getArgs()[0] == null)) {
                        return null;
                    }
                }

                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + type.getName() + "]";
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
                return exchange.getOut().isFault() ? exchange.getOut().getBody() : null;
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
                return exchange.getOut().isFault() ? exchange.getOut().getBody(type) : null;
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
     * expression is evaluated to
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
                Scanner scanner = ObjectHelper.getScanner(exchange, value);
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
                Scanner scanner = ObjectHelper.getScanner(exchange, value);
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
     * Prepends the String evaluations of the two expressions together
     */
    public static Expression prepend(final Expression left, final Expression right) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return right.evaluate(exchange, String.class) + left.evaluate(exchange, String.class);
            }

            @Override
            public String toString() {
                return "prepend(" + left + ", " + right + ")";
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
                StringBuilder buffer = new StringBuilder();
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

    /**
     * Returns an Expression for the exchange id
     */
    public static Expression exchangeIdExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getExchangeId();
            }

            @Override
            public String toString() {
                return "exchangeId";
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
                        throw new IllegalArgumentException("Cannot find java.util.Date object at command: " + command);
                    }
                } else if (command.startsWith("out.header.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    date = exchange.getOut().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot find java.util.Date object at command: " + command);
                    }
                } else if ("file".equals(command)) {
                    date = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot find " + Exchange.FILE_LAST_MODIFIED + " header at command: " + command);
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
                // must call evaluate to return the nested language evaluate when evaluating
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
    
    public static Expression beanExpression(final Class<?> beanType, final String methodName) {
        return BeanLanguage.bean(beanType, methodName);        
    }

    public static Expression beanExpression(final Object bean, final String methodName) {
        return BeanLanguage.bean(bean, methodName);        
    }

    public static Expression beanExpression(final String beanRef, final String methodName) {
        String expression = methodName != null ? beanRef + "." + methodName : beanRef;
        return beanExpression(expression);
    }

    /**
     * Returns an expression processing the exchange to the given endpoint uri
     *
     * @param uri endpoint uri to send the exchange to
     * @return an expression object which will return the OUT body
     */
    public static Expression toExpression(final String uri) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Endpoint endpoint = exchange.getContext().getEndpoint(uri);
                if (endpoint == null) {
                    throw new NoSuchEndpointException(uri);
                }

                Producer producer;
                try {
                    producer = endpoint.createProducer();
                    producer.start();
                    producer.process(exchange);
                    producer.stop();
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }

                // return the OUT body, but check for exchange pattern
                if (ExchangeHelper.isOutCapable(exchange)) {
                    return exchange.getOut().getBody();
                } else {
                    return exchange.getIn().getBody();
                }
            }

            @Override
            public String toString() {
                return "to(" + uri + ")";
            }
        };
    }

    public static Expression fileNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            }

            @Override
            public String toString() {
                return "file:name";
            }
        };
    }

    public static Expression fileOnlyNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY, String.class);
                if (answer == null) {
                    answer = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                    answer = FileUtil.stripPath(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "file:onlyname";
            }
        };
    }

    public static Expression fileNameNoExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.stripExt(name);
            }

            @Override
            public String toString() {
                return "file:name.noext";
            }
        };
    }

    public static Expression fileOnlyNameNoExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = fileOnlyNameExpression().evaluate(exchange, String.class);
                return FileUtil.stripExt(name);
            }

            @Override
            public String toString() {
                return "file:onlyname.noext";
            }
        };
    }

    public static Expression fileExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                if (name != null) {
                    return name.substring(name.lastIndexOf('.') + 1);
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "file:ext";
            }
        };
    }

    public static Expression fileParentExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileParent", String.class);
            }

            @Override
            public String toString() {
                return "file:parent";
            }
        };
    }

    public static Expression filePathExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFilePath", String.class);
            }

            @Override
            public String toString() {
                return "file:path";
            }
        };
    }

    public static Expression fileAbsolutePathExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
            }

            @Override
            public String toString() {
                return "file:absolute.path";
            }
        };
    }

    public static Expression fileAbsoluteExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolute", Boolean.class);
            }

            @Override
            public String toString() {
                return "file:absolute";
            }
        };
    }

    public static Expression fileSizeExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileLength", Long.class);
            }

            @Override
            public String toString() {
                return "file:length";
            }
        };
    }

    public static Expression fileLastModifiedExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Date.class);
            }

            @Override
            public String toString() {
                return "file:modified";
            }
        };
    }

    public static Expression propertiesComponentExpression(final String key, final String locations) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                try {
                    if (locations != null) {
                        // the properties component is optional as we got locations
                        // getComponent will create a new component if none already exists
                        Component component = exchange.getContext().getComponent("properties");
                        PropertiesComponent pc = exchange.getContext().getTypeConverter()
                                .mandatoryConvertTo(PropertiesComponent.class, component);
                        // enclose key with {{ }} to force parsing
                        String[] paths = locations.split(",");
                        return pc.parseUri(PropertiesComponent.PREFIX_TOKEN + key + PropertiesComponent.SUFFIX_TOKEN, paths);
                    } else {
                        // the properties component is mandatory if no locations provided
                        Component component = exchange.getContext().hasComponent("properties");
                        if (component == null) {
                            throw new IllegalArgumentException("PropertiesComponent with name properties must be defined"
                                    + " in CamelContext to support property placeholders in expressions");
                        }
                        PropertiesComponent pc = exchange.getContext().getTypeConverter()
                                .mandatoryConvertTo(PropertiesComponent.class, component);
                        // enclose key with {{ }} to force parsing
                        return pc.parseUri(PropertiesComponent.PREFIX_TOKEN + key + PropertiesComponent.SUFFIX_TOKEN);
                    }
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public String toString() {
                return "properties(" + key + ")";
            }
        };
    }

}
