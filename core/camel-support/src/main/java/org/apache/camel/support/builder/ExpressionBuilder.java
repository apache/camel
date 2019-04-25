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
package org.apache.camel.support.builder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.GroupIterator;
import org.apache.camel.support.GroupTokenIterator;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.Scanner;
import org.apache.camel.util.StringHelper;

/**
 * A helper class for working with <a href="http://camel.apache.org/expression.html">expressions</a>.
 */
//CHECKSTYLE:OFF
public class ExpressionBuilder {

    /**
     * Returns an expression for the inbound message attachments
     *
     * @return an expression object which will return the inbound message attachments
     */
    public static Expression attachmentObjectsExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getAttachmentObjects();
            }

            @Override
            public String toString() {
                return "attachmentObjects";
            }
        };
    }

    /**
     * Returns an expression for the inbound message attachments
     *
     * @return an expression object which will return the inbound message attachments
     */
    public static Expression attachmentObjectValuesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getAttachmentObjects().values();
            }

            @Override
            public String toString() {
                return "attachmentObjects";
            }
        };
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
     * Returns an expression for the inbound message attachments
     *
     * @return an expression object which will return the inbound message attachments
     */
    public static Expression attachmentValuesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getAttachments().values();
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
                String name = simpleExpression(headerName).evaluate(exchange, String.class);
                Object header = exchange.getIn().getHeader(name);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(name);
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
                String name = simpleExpression(headerName).evaluate(exchange, String.class);
                Object header = exchange.getIn().getHeader(name, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(name, type);
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
     * @param typeName the type to convert to as a FQN class name
     * @return an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName, final String typeName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    String text = simpleExpression(typeName).evaluate(exchange, String.class);
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }

                String text = simpleExpression(headerName).evaluate(exchange, String.class);
                Object header = exchange.getIn().getHeader(text, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(text, type);
                }
                return header;
            }

            @Override
            public String toString() {
                return "headerAs(" + headerName + ", " + typeName + ")";
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

                String text = simpleExpression(headerName).evaluate(exchange, String.class);
                Message out = exchange.getOut();
                Object header = out.getHeader(text);
                if (header == null) {
                    // let's try the exchange header
                    header = exchange.getProperty(text);
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
     * @return an expression object which will return the headers, will be <tt>null</tt> if the
     * exchange is not out capable.
     */
    public static Expression outHeadersExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // only get out headers if the MEP is out capable
                if (ExchangeHelper.isOutCapable(exchange)) {
                    return exchange.getOut().getHeaders();
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "outHeaders";
            }
        };
    }

    /**
     * Returns an expression for the exchange pattern
     *
     * @see Exchange#getPattern()
     * @return an expression object which will return the exchange pattern
     */
    public static Expression exchangePatternExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getPattern();
            }

            @Override
            public String toString() {
                return "exchangePattern";
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
     * Returns an expression for lookup a bean in the {@link org.apache.camel.spi.Registry}
     *
     * @return an expression object which will return the bean
     */
    public static Expression refExpression(final String ref) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = simpleExpression(ref).evaluate(exchange, String.class);
                return exchange.getContext().getRegistry().lookupByName(text);
            }

            @Override
            public String toString() {
                return "ref(" + ref + ")";
            }
        };
    }

    /**
     * Returns an expression for the {@link CamelContext}
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
     * Returns an expression for the {@link CamelContext} name
     *
     * @return an expression object which will return the camel context name
     */
    public static Expression camelContextNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getName();
            }

            @Override
            public String toString() {
                return "camelContextName";
            }
        };
    }

    /**
     * Returns an expression for an exception message set on the exchange
     *
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
                    IOHelper.close(pw, sw);
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
    public static Expression exchangePropertyExpression(final String propertyName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = simpleExpression(propertyName).evaluate(exchange, String.class);
                return exchange.getProperty(text);
            }

            @Override
            public String toString() {
                return "exchangeProperty(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for the exchange properties of exchange
     *
     * @return an expression object which will return the exchange properties
     */
    public static Expression exchangePropertiesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperties();
            }

            @Override
            public String toString() {
                return "exchangeProperties";
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
                return exchange.getContext().getGlobalOptions();
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
                String text = simpleExpression(propertyName).evaluate(exchange, String.class);
                return exchange.getContext().getGlobalOption(text);
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
                String text = simpleExpression(propertyName).evaluate(exchange, String.class);
                String text2 = simpleExpression(defaultValue).evaluate(exchange, String.class);
                return System.getProperty(text, text2);
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
                String text = simpleExpression(propertyName).evaluate(exchange, String.class);
                String answer = null;
                if (text != null) {
                    // lookup OS env with upper case key
                    text = text.toUpperCase();
                    answer = System.getenv(text);
                    // some OS do not support dashes in keys, so replace with underscore
                    if (answer == null) {
                        String noDashKey = text.replace('-', '_');
                        answer = System.getenv(noDashKey);
                    }
                }

                if (answer == null) {
                    answer = simpleExpression(defaultValue).evaluate(exchange, String.class);
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
     * Returns an expression for evaluating the expression/predicate using the given language
     *
     * @param expression  the expression or predicate
     * @return an expression object which will evaluate the expression/predicate using the given language
     */
    public static Expression languageExpression(final String language, final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Language lan = exchange.getContext().resolveLanguage(language);
                if (lan != null) {
                    return lan.createExpression(expression).evaluate(exchange, Object.class);
                } else {
                    throw new NoSuchLanguageException(language);
                }
            }

            @Override
            public boolean matches(Exchange exchange) {
                Language lan = exchange.getContext().resolveLanguage(language);
                if (lan != null) {
                    return lan.createPredicate(expression).matches(exchange);
                } else {
                    throw new NoSuchLanguageException(language);
                }
            }

            @Override
            public String toString() {
                return "language[" + language + ":" + expression + "]";
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
     * Returns a functional expression for the exchanges inbound message body
     */
    public static Expression bodyExpression(final Function<Object, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getIn().getBody()
                );
            }

            @Override
            public String toString() {
                return "bodyExpression";
            }
        };
    }

    /**
     * Returns a functional expression for the exchanges inbound message body and headers
     */
    public static Expression bodyExpression(final BiFunction<Object, Map<String, Object>, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getIn().getBody(),
                    exchange.getIn().getHeaders()
                );
            }

            @Override
            public String toString() {
                return "bodyExpression";
            }
        };
    }

    /**
     * Returns a functional expression for the exchanges inbound message body converted to a desired type
     */
    public static <T> Expression bodyExpression(final Class<T> bodyType, final Function<T, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getIn().getBody(bodyType)
                );
            }

            @Override
            public String toString() {
                return "bodyExpression (" + bodyType + ")";
            }
        };
    }

    /**
     * Returns a functional expression for the exchanges inbound message body converted to a desired type and headers
     */
    public static <T> Expression bodyExpression(final Class<T> bodyType, final BiFunction<T, Map<String, Object>, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getIn().getBody(bodyType),
                    exchange.getIn().getHeaders()
                );
            }

            @Override
            public String toString() {
                return "bodyExpression (" + bodyType + ")";
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
            public Object evaluate(Exchange exchange) {
                String text = simpleExpression(name).evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
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
     * Returns the expression for the current step id (if any)
     */
    public static Expression stepIdExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperty(Exchange.STEP_ID);
            }

            @Override
            public String toString() {
                return "stepId";
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
                }

                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
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
                Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                return msg.isFault() ? msg.getBody() : null;
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
                Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                return msg.isFault() ? msg.getBody(type) : null;
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
     * Returns a functional expression for the exchange
     */
    public static Expression exchangeExpression(final Function<Exchange, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange);
            }

            @Override
            public String toString() {
                return "exchangeExpression";
            }
        };
    }

    /**
     * Returns the expression for the IN message
     */
    public static Expression messageExpression() {
        return inMessageExpression();
    }

    /**
     * Returns a functional expression for the IN message
     */
    public static Expression messageExpression(final Function<Message, Object> function) {
        return inMessageExpression(function);
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
     * Returns a functional expression for the IN message
     */
    public static Expression inMessageExpression(final Function<Message, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn());
            }

            @Override
            public String toString() {
                return "inMessageExpression";
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
     * Returns a functional expression for the OUT message
     */
    public static Expression outMessageExpression(final Function<Message, Object> function) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getOut());
            }

            @Override
            public String toString() {
                return "outMessageExpression";
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type
     */
    public static Expression convertToExpression(final Expression expression, final Class<?> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (type != null) {
                    return expression.evaluate(exchange, type);
                } else {
                    return expression;
                }
            }

            @Override
            public String toString() {
                return "" + expression;
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
                Object result = type.evaluate(exchange, Object.class);
                if (result != null) {
                    return expression.evaluate(exchange, result.getClass());
                } else {
                    return expression;
                }
            }

            @Override
            public String toString() {
                return "" + expression;
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
                String text = simpleExpression(token).evaluate(exchange, String.class);
                Object value = expression.evaluate(exchange, Object.class);
                Scanner scanner = ExchangeHelper.getScanner(exchange, value, text);
                return scanner;
            }

            @Override
            public String toString() {
                return "tokenize(" + expression + ", " + token + ")";
            }
        };
    }

    /**
     * Returns an expression that skips the first element
     */
    public static Expression skipFirstExpression(final Expression expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                Iterator it = exchange.getContext().getTypeConverter().tryConvertTo(Iterator.class, exchange, value);
                if (it != null) {
                    // skip first
                    it.next();
                    return it;
                } else {
                    return value;
                }
            }

            @Override
            public String toString() {
                return "skipFirst(" + expression + ")";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the
     * given regex
     */
    public static Expression regexTokenizeExpression(final Expression expression,
                                                     final String regexTokenizer) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                Scanner scanner = ExchangeHelper.getScanner(exchange, value, regexTokenizer);
                return scanner;
            }

            @Override
            public String toString() {
                return "regexTokenize(" + expression + ", " + regexTokenizer + ")";
            }
        };
    }

    public static Expression groupXmlIteratorExpression(final Expression expression, final String group) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it, "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                // must use GroupTokenIterator in xml mode as we want to concat the xml parts into a single message
                // the group can be a simple expression so evaluate it as a number
                Integer parts = exchange.getContext().resolveLanguage("simple").createExpression(group).evaluate(exchange, Integer.class);
                if (parts == null) {
                    throw new RuntimeExchangeException("Group evaluated as null, must be evaluated as a positive Integer value from expression: " + group, exchange);
                } else if (parts <= 0) {
                    throw new RuntimeExchangeException("Group must be a positive number, was: " + parts, exchange);
                }
                return new GroupTokenIterator(exchange, it, null, parts, false);
            }

            @Override
            public String toString() {
                return "group " + expression + " " + group + " times";
            }
        };
    }

    public static Expression groupIteratorExpression(final Expression expression, final String token, final String group, final boolean skipFirst) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it, "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                // the group can be a simple expression so evaluate it as a number
                Integer parts = exchange.getContext().resolveLanguage("simple").createExpression(group).evaluate(exchange, Integer.class);
                if (parts == null) {
                    throw new RuntimeExchangeException("Group evaluated as null, must be evaluated as a positive Integer value from expression: " + group, exchange);
                } else if (parts <= 0) {
                    throw new RuntimeExchangeException("Group must be a positive number, was: " + parts, exchange);
                }
                if (token != null) {
                    return new GroupTokenIterator(exchange, it, token, parts, skipFirst);
                } else {
                    return new GroupIterator(exchange, it, parts, skipFirst);
                }
            }

            @Override
            public String toString() {
                return "group " + expression + " " + group + " times";
            }
        };
    }

    /**
     * Returns a sort expression which will sort the expression with the given comparator.
     * <p/>
     * The expression is evaluated as a {@link List} object to allow sorting.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Expression sortExpression(final Expression expression, final Comparator comparator) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                List<?> list = expression.evaluate(exchange, List.class);
                list.sort(comparator);
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
     * @param description the text description of the expression
     * @return an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions, final String description) {
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
                if (description != null) {
                    return description;
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

    /**
     * Returns an Expression for the route id
     */
    public static Expression routeIdExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = null;
                UnitOfWork uow = exchange.getUnitOfWork();
                RouteContext rc = uow != null ? uow.getRouteContext() : null;
                if (rc != null) {
                    answer = rc.getRouteId();
                }
                if (answer == null) {
                    // fallback and get from route id on the exchange
                    answer = exchange.getFromRouteId();
                }
                return answer;
            }

            @Override
            public String toString() {
                return "routeId";
            }
        };
    }

    public static Expression simpleExpression(final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (LanguageSupport.hasSimpleFunction(expression)) {
                    // resolve language using context to have a clear separation of packages
                    // must call evaluate to return the nested language evaluate when evaluating
                    // stacked expressions
                    Language language = exchange.getContext().resolveLanguage("simple");
                    return language.createExpression(expression).evaluate(exchange, Object.class);
                } else {
                    return expression;
                }
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
                // bean is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate expression as a string
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

    /**
     * Returns Simple expression or fallback to Constant expression if expression str is not Simple expression.
     */
    public static Expression parseSimpleOrFallbackToConstantExpression(String str, CamelContext camelContext) {
        if (StringHelper.hasStartToken(str, "simple")) {
            return camelContext.resolveLanguage("simple").createExpression(str);
        }
        return constantExpression(str);
    }

    public static Expression propertiesComponentExpression(final String key, final String locations, final String defaultValue) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = simpleExpression(key).evaluate(exchange, String.class);
                String text2 = simpleExpression(locations).evaluate(exchange, String.class);
                try {
                    if (text2 != null) {
                        // the properties component is optional as we got locations
                        // getComponent will create a new component if none already exists
                        PropertiesComponent pc = exchange.getContext().getPropertiesComponent(true);
                        // enclose key with {{ }} to force parsing
                        String[] paths = text2.split(",");
                        return pc.parseUri(pc.getPrefixToken() + text + pc.getSuffixToken(), paths);
                    } else {
                        // the properties component is mandatory if no locations provided
                        PropertiesComponent pc = exchange.getContext().getPropertiesComponent(false);
                        if (pc == null) {
                            throw new IllegalArgumentException("PropertiesComponent with name properties must be defined"
                                + " in CamelContext to support property placeholders in expressions");
                        }
                        // enclose key with {{ }} to force parsing
                        return pc.parseUri(pc.getPrefixToken() + text + pc.getSuffixToken());
                    }
                } catch (Exception e) {
                    // property with key not found, use default value if provided
                    if (defaultValue != null) {
                        return defaultValue;
                    }
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public String toString() {
                return "properties(" + key + ")";
            }
        };
    }

}
