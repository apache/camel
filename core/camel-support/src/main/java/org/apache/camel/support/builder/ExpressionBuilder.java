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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.NoSuchPropertyException;
import org.apache.camel.NoSuchVariableException;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.ConstantExpressionAdapter;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.GroupIterator;
import org.apache.camel.support.GroupTokenIterator;
import org.apache.camel.support.LanguageHelper;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.util.InetAddressUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.Jsoner;
import org.apache.camel.util.xml.pretty.XmlPrettyPrinter;

/**
 * A helper class for working with <a href="http://camel.apache.org/expression.html">expressions</a>.
 */
public class ExpressionBuilder {

    /**
     * Returns an expression for the header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param  headerName the name of the header the expression will return
     * @return            an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName) {
        return headerExpression(simpleExpression(headerName));
    }

    /**
     * Returns an expression for the header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param  headerName the name of the header the expression will return
     * @param  mandatory  whether the header is mandatory and if not present an exception is thrown
     * @return            an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName, boolean mandatory) {
        return headerExpression(simpleExpression(headerName), mandatory);
    }

    /**
     * Returns an expression for the header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param  headerName the name of the header the expression will return
     * @return            an expression object which will return the header value
     */
    public static Expression headerExpression(final Expression headerName) {
        return headerExpression(headerName, false);
    }

    /**
     * Returns an expression for the header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param  headerName the name of the header the expression will return
     * @param  mandatory  whether the header is mandatory and if not present an exception is thrown
     * @return            an expression object which will return the header value
     */
    public static Expression headerExpression(final Expression headerName, final boolean mandatory) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String key = headerName.evaluate(exchange, String.class);
                Object answer = exchange.getIn().getHeader(key);
                if (answer == null) {
                    // fall back on a property
                    answer = exchange.getProperty(key);
                }
                if (mandatory && answer == null) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(new NoSuchHeaderException(exchange, key, null));
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                headerName.init(context);
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
     * @param  headerName the name of the header the expression will return
     * @param  type       the type to convert to
     * @return            an expression object which will return the header value
     */
    public static <T> Expression headerExpression(final String headerName, final Class<T> type) {
        return headerExpression(simpleExpression(headerName), constantExpression(type.getName()));
    }

    /**
     * Returns an expression for the header value with the given name converted to the given type
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param  headerName the name of the header the expression will return
     * @param  typeName   the type to convert to as a FQN class name
     * @return            an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName, final String typeName) {
        return headerExpression(simpleExpression(headerName), simpleExpression(typeName));
    }

    /**
     * Returns an expression for the header value with the given name converted to the given type
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param  headerName the name of the header the expression will return
     * @param  typeName   the type to convert to as a FQN class name
     * @return            an expression object which will return the header value
     */
    public static Expression headerExpression(final Expression headerName, final Expression typeName) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;

            @Override
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    String text = typeName.evaluate(exchange, String.class);
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                String text = headerName.evaluate(exchange, String.class);
                Object header = exchange.getIn().getHeader(text, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(text, type);
                }
                return header;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                headerName.init(context);
                typeName.init(context);
                classResolver = context.getClassResolver();
            }

            @Override
            public String toString() {
                return "headerAs(" + headerName + ", " + typeName + ")";
            }
        };
    }

    /**
     * Returns an expression for the variable with the given name
     *
     * @param  variableName the name of the variable the expression will return
     * @return              an expression object which will return the variable value
     */
    public static Expression variableExpression(final String variableName) {
        return variableExpression(simpleExpression(variableName));
    }

    /**
     * Returns an expression for the variable with the given name
     *
     * @param  variableName the name of the variable the expression will return
     * @param  mandatory    whether the variable is mandatory and if not present an exception is thrown
     * @return              an expression object which will return the variable value
     */
    public static Expression variableExpression(final String variableName, boolean mandatory) {
        return variableExpression(simpleExpression(variableName), mandatory);
    }

    /**
     * Returns an expression for the variable with the given name
     *
     * @param  variableName the name of the variable the expression will return
     * @return              an expression object which will return the variable value
     */
    public static Expression variableExpression(final Expression variableName) {
        return variableExpression(variableName, false);
    }

    /**
     * Returns an expression for the variable with the given name
     *
     * @param  variableName the name of the variable the expression will return
     * @param  mandatory    whether the variable is mandatory and if not present an exception is thrown
     * @return              an expression object which will return the variable value
     */
    public static Expression variableExpression(final Expression variableName, final boolean mandatory) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String key = variableName.evaluate(exchange, String.class);
                Object answer = ExchangeHelper.getVariable(exchange, key);
                if (mandatory && answer == null) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(new NoSuchVariableException(exchange, key));
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                variableName.init(context);
            }

            @Override
            public String toString() {
                return "variable(" + variableName + ")";
            }
        };
    }

    /**
     * Returns an expression for the variable with the given name converted to the given type
     *
     * @param  variableName the name of the variable the expression will return
     * @param  type         the type to convert to
     * @return              an expression object which will return the variable value
     */
    public static <T> Expression variableExpression(final String variableName, final Class<T> type) {
        return variableExpression(simpleExpression(variableName), constantExpression(type.getName()));
    }

    /**
     * Returns an expression for the variable with the given name converted to the given type
     *
     * @param  variableName the name of the variable the expression will return
     * @param  typeName     the type to convert to as a FQN class name
     * @return              an expression object which will return the header value
     */
    public static Expression variableExpression(final String variableName, final String typeName) {
        return variableExpression(simpleExpression(variableName), simpleExpression(typeName));
    }

    /**
     * Returns an expression for the variable with the given name converted to the given type
     *
     * @param  variableName the name of the variable the expression will return
     * @param  typeName     the type to convert to as a FQN class name
     * @return              an expression object which will return the header value
     */
    public static Expression variableExpression(final Expression variableName, final Expression typeName) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private TypeConverter converter;

            @Override
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    String text = typeName.evaluate(exchange, String.class);
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                String key = variableName.evaluate(exchange, String.class);
                Object value = ExchangeHelper.getVariable(exchange, key);
                if (value != null) {
                    value = converter.convertTo(type, value);
                }
                return value;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                variableName.init(context);
                typeName.init(context);
                classResolver = context.getClassResolver();
                converter = context.getTypeConverter();
            }

            @Override
            public String toString() {
                return "variableAs(" + variableName + ", " + typeName + ")";
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
            @Override
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
     * Returns an expression for the {@link Exchange} variables
     *
     * @return an expression object which will return the variables
     */
    public static Expression variablesExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return exchange.getVariables();
            }

            @Override
            public String toString() {
                return "variables";
            }
        };
    }

    /**
     * Returns an expression for the exchange pattern
     *
     * @see    Exchange#getPattern()
     * @return an expression object which will return the exchange pattern
     */
    public static Expression exchangePatternExpression() {
        return new ExpressionAdapter() {
            @Override
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
     * @see    Exchange#getException()
     * @return an expression object which will return the exception set on the exchange
     */
    public static Expression exchangeExceptionExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
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
     * Is used to get the caused exception that typically have been wrapped in some sort of Camel wrapper exception
     *
     * @param  type the exception type
     * @see         Exchange#getException(Class)
     * @return      an expression object which will return the exception set on the exchange
     */
    public static Expression exchangeExceptionExpression(final Class<Exception> type) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException(type);
                if (exception == null) {
                    exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
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
            private TypeConverter typeConverter;

            @Override
            public Object evaluate(Exchange exchange) {
                return typeConverter;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                typeConverter = context.getTypeConverter();
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
            private Registry registry;

            @Override
            public Object evaluate(Exchange exchange) {
                return registry;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                registry = context.getRegistry();
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
        if (LanguageSupport.hasSimpleFunction(ref)) {
            return refExpression(simpleExpression(ref));
        } else {
            return refExpression(constantExpression(ref));
        }
    }

    /**
     * Returns an expression for lookup a bean in the {@link org.apache.camel.spi.Registry}
     *
     * @return an expression object which will return the bean
     */
    public static Expression refExpression(final Expression ref) {
        return new ExpressionAdapter() {
            private Registry registry;

            @Override
            public Object evaluate(Exchange exchange) {
                String text = ref.evaluate(exchange, String.class);
                if (text != null) {
                    return registry.lookupByName(text);
                } else {
                    return null;
                }
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                ref.init(context);
                registry = context.getRegistry();
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
            private CamelContext context;

            @Override
            public Object evaluate(Exchange exchange) {
                return context;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                this.context = context;
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
        return new ConstantExpressionAdapter() {
            private String name;

            @Override
            public Object evaluate(Exchange exchange) {
                return name;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                name = context.getName();
                setValue(name);
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
            @Override
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
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
            @Override
            public Object evaluate(Exchange exchange) {
                return LanguageHelper.exceptionStacktrace(exchange);
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
     * @param  propertyName the name of the property the expression will return
     * @return              an expression object which will return the property value
     */
    public static Expression exchangePropertyExpression(final String propertyName) {
        return exchangePropertyExpression(simpleExpression(propertyName));
    }

    /**
     * Returns an expression for the property value of exchange with the given name
     *
     * @param  propertyName the name of the property the expression will return
     * @param  mandatory    whether the property is mandatory and if not present an exception is thrown
     * @return              an expression object which will return the property value
     */
    public static Expression exchangePropertyExpression(final String propertyName, boolean mandatory) {
        return exchangePropertyExpression(simpleExpression(propertyName), mandatory);
    }

    /**
     * Returns an expression for the property value of exchange with the given name
     *
     * @param  propertyName the name of the property the expression will return
     * @return              an expression object which will return the property value
     */
    public static Expression exchangePropertyExpression(final Expression propertyName) {
        return exchangePropertyExpression(propertyName, false);
    }

    /**
     * Returns an expression for the property value of exchange with the given name
     *
     * @param  propertyName the name of the property the expression will return
     * @param  mandatory    whether the property is mandatory and if not present an exception is thrown
     * @return              an expression object which will return the property value
     */
    public static Expression exchangePropertyExpression(final Expression propertyName, final boolean mandatory) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String key = propertyName.evaluate(exchange, String.class);
                Object answer = exchange.getProperty(key);
                if (mandatory && answer == null) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(new NoSuchPropertyException(exchange, key));
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                propertyName.init(context);
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
            @Override
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
            private Map<String, String> globalOptions;

            @Override
            public Object evaluate(Exchange exchange) {
                return globalOptions;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                globalOptions = context.getGlobalOptions();
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
     * @param  propertyName the name of the property the expression will return
     * @return              an expression object which will return the property value
     */
    public static Expression camelContextPropertyExpression(final String propertyName) {
        return camelContextPropertyExpression(simpleExpression(propertyName));
    }

    /**
     * Returns an expression for the property value of the camel context with the given name
     *
     * @param  propertyName the name of the property the expression will return
     * @return              an expression object which will return the property value
     */
    public static Expression camelContextPropertyExpression(final Expression propertyName) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String text = propertyName.evaluate(exchange, String.class);
                return exchange.getContext().getGlobalOption(text);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                propertyName.init(context);
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
     * @param  propertyName the name of the system property the expression will return
     * @return              an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(final String propertyName) {
        return systemPropertyExpression(propertyName, null);
    }

    /**
     * Returns an expression for a system property value with the given name
     *
     * @param  propertyName the name of the system property the expression will return
     * @param  defaultValue default value to return if no system property exists
     * @return              an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(
            final String propertyName,
            final String defaultValue) {
        Expression exprName = simpleExpression(propertyName);
        Expression exprDefault = simpleExpression(defaultValue);
        return systemPropertyExpression(exprName, exprDefault);
    }

    /**
     * Returns an expression for a system property value with the given name
     *
     * @param  exprName     the name of the system property the expression will return
     * @param  defaultValue default value to return if no system property exists
     * @return              an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(
            final Expression exprName,
            final Expression defaultValue) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String text = exprName.evaluate(exchange, String.class);
                String text2 = defaultValue.evaluate(exchange, String.class);
                return System.getProperty(text, text2);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                exprName.init(context);
                defaultValue.init(context);
            }

            @Override
            public String toString() {
                return "systemProperty(" + exprName + ")";
            }
        };
    }

    /**
     * Returns an expression for a system environment value with the given name
     *
     * @param  propertyName the name of the system environment the expression will return
     * @return              an expression object which will return the system property value
     */
    public static Expression systemEnvironmentExpression(final String propertyName) {
        return systemEnvironmentExpression(propertyName, null);
    }

    /**
     * Returns an expression for a system environment value with the given name
     *
     * @param  propertyName the name of the system environment the expression will return
     * @param  defaultValue default value to return if no system environment exists
     * @return              an expression object which will return the system environment value
     */
    public static Expression systemEnvironmentExpression(
            final String propertyName,
            final String defaultValue) {
        Expression exprName = simpleExpression(propertyName);
        Expression expDefault = simpleExpression(defaultValue);
        return systemEnvironmentExpression(exprName, expDefault);
    }

    /**
     * Returns an expression for a system environment value with the given name
     *
     * @param  propertyName the name of the system environment the expression will return
     * @param  defaultValue default value to return if no system environment exists
     * @return              an expression object which will return the system environment value
     */
    public static Expression systemEnvironmentExpression(
            final Expression propertyName,
            final Expression defaultValue) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String text = propertyName.evaluate(exchange, String.class);
                String answer = LanguageHelper.sysenv(text);

                if (answer == null) {
                    answer = defaultValue.evaluate(exchange, String.class);
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                propertyName.init(context);
                defaultValue.init(context);
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
     * @param  value the value the expression will return
     * @return       an expression object which will return the constant value
     */
    public static Expression constantExpression(final Object value) {
        return new ConstantExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return value;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                setValue(value);
            }

            @Override
            public String toString() {
                return String.valueOf(value);
            }
        };
    }

    /**
     * Returns an expression for evaluating the expression/predicate using the given language
     *
     * @param  languageName the language name
     * @param  language     the language
     * @param  expression   the expression or predicate
     * @return              an expression object which will evaluate the expression/predicate using the given language
     */
    public static Expression languageExpression(final String languageName, final Language language, final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                super.init(context);
                exp = language.createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return languageName + "(" + expression + ")";
            }
        };
    }

    /**
     * Returns an expression for evaluating the expression/predicate using the given language
     *
     * @param  expression the expression or predicate
     * @return            an expression object which will evaluate the expression/predicate using the given language
     */
    public static Expression languageExpression(final String language, final String expression) {
        return new ExpressionAdapter() {
            private Expression expr;
            private Predicate pred;

            @Override
            public Object evaluate(Exchange exchange) {
                return expr.evaluate(exchange, Object.class);
            }

            @Override
            public boolean matches(Exchange exchange) {
                return pred.matches(exchange);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                Language lan = context.resolveLanguage(language);
                if (lan != null) {
                    pred = lan.createPredicate(expression);
                    pred.init(context);
                    expr = lan.createExpression(expression);
                    expr.init(context);
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
     * Returns an expression for evaluating the expression/predicate using the given language
     *
     * @param  expression the expression or predicate
     * @param  source     Source to use, instead of message body. You can prefix with variable:, header:, or property:
     *                    to specify kind of source. Otherwise, the source is assumed to be a variable. Use empty or
     *                    null to use default source, which is the message body.
     * @return            an expression object which will evaluate the expression/predicate using the given language
     */
    public static Expression singleInputLanguageExpression(
            final String language, final String expression, final String source) {
        return new ExpressionAdapter() {
            private Expression expr;
            private Predicate pred;

            @Override
            public Object evaluate(Exchange exchange) {
                return expr.evaluate(exchange, Object.class);
            }

            @Override
            public boolean matches(Exchange exchange) {
                return pred.matches(exchange);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                Language lan = context.resolveLanguage(language);
                if (lan != null) {
                    if (source != null && lan instanceof SingleInputTypedLanguageSupport sil) {
                        Expression input = ExpressionBuilder.singleInputExpression(source);
                        expr = sil.createExpression(input, expression, null);
                        expr.init(context);
                        pred = PredicateBuilder.toPredicate(expr);
                        pred.init(context);
                    } else {
                        pred = lan.createPredicate(expression);
                        pred.init(context);
                        expr = lan.createExpression(expression);
                        expr.init(context);
                    }
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
            @Override
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
     * Returns the expression for the original incoming message body
     */
    public static Expression originalBodyExpression() {
        return new ExpressionAdapter() {

            private boolean enabled;

            @Override
            public Object evaluate(Exchange exchange) {
                if (enabled) {
                    UnitOfWork uow = exchange.getUnitOfWork();
                    if (uow != null) {
                        Message msg = uow.getOriginalInMessage();
                        if (msg != null) {
                            return msg.getBody();
                        }
                    }
                }
                return null;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                this.enabled = context.isAllowUseOriginalMessage();
            }

            @Override
            public String toString() {
                return "originalBody";
            }
        };
    }

    /**
     * Returns a functional expression for the exchanges inbound message body
     */
    public static Expression bodyExpression(final Function<Object, Object> function) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return function.apply(
                        exchange.getIn().getBody());
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
            @Override
            public Object evaluate(Exchange exchange) {
                return function.apply(
                        exchange.getIn().getBody(),
                        exchange.getIn().getHeaders());
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
            @Override
            public Object evaluate(Exchange exchange) {
                return function.apply(
                        exchange.getIn().getBody(bodyType));
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
    public static <
            T> Expression bodyExpression(final Class<T> bodyType, final BiFunction<T, Map<String, Object>, Object> function) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return function.apply(
                        exchange.getIn().getBody(bodyType),
                        exchange.getIn().getHeaders());
            }

            @Override
            public String toString() {
                return "bodyExpression (" + bodyType + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type
     */
    public static <T> Expression bodyExpression(final Class<T> type) {
        return new ExpressionAdapter() {
            @Override
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
     * Returns the expression for the exchanges inbound message body converted to the given type
     */
    public static Expression bodyExpression(final String name) {
        return bodyExpression(simpleExpression(name));
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type
     */
    public static Expression bodyExpression(final Expression name) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;

            @Override
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    String text = name.evaluate(exchange, String.class);
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                return exchange.getIn().getBody(type);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                name.init(context);
                classResolver = context.getClassResolver();
            }

            @Override
            public String toString() {
                return "bodyAs[" + name + "]";
            }
        };
    }

    /**
     * Creates a source {@link Expression} for languages that can accept input from other sources than the message body.
     *
     * @param  source Source to use, instead of message body. You can prefix with variable:, header:, or property: to
     *                specify kind of source. Otherwise, the source is assumed to be a variable. Use empty or null to
     *                use default source, which is the message body.
     * @return        a variable expression if {@code variableName} is not empty, a header expression if
     *                {@code headerName} is not empty, otherwise a property expression if {@code propertyName} is not
     *                empty or finally a body expression.
     */
    public static Expression singleInputExpression(String source) {
        final Expression exp;
        if (source == null || source.isEmpty()) {
            exp = bodyExpression();
        } else if (source.startsWith("header:")) {
            exp = headerExpression(source.substring(7), true);
        } else if (source.startsWith("property:")) {
            exp = exchangePropertyExpression(source.substring(9), true);
        } else {
            if (source.startsWith("variable:")) {
                source = source.substring(9);
            }
            exp = variableExpression(source);
        }
        return exp;
    }

    /**
     * Returns the expression for the current thread id
     */
    public static Expression threadIdExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return Thread.currentThread().getId();
            }

            @Override
            public String toString() {
                return "threadId";
            }
        };
    }

    /**
     * Returns the expression for the current thread name
     */
    public static Expression threadNameExpression() {
        return new ExpressionAdapter() {
            @Override
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
     * Returns the expression for the local hostname
     */
    public static Expression hostnameExpression() {
        return new ConstantExpressionAdapter() {
            private String hostname;

            @Override
            public Object evaluate(Exchange exchange) {
                return hostname;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                hostname = InetAddressUtil.getLocalHostNameSafe();
                setValue(hostname);
            }

            @Override
            public String toString() {
                return "hostname";
            }
        };
    }

    /**
     * Returns the expression for the current step id (if any)
     */
    public static Expression stepIdExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return exchange.getProperty(ExchangePropertyKey.STEP_ID);
            }

            @Override
            public String toString() {
                return "stepId";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type.
     * <p/>
     * Does <b>not</b> allow null bodies.
     */
    public static <T> Expression mandatoryBodyExpression(final Class<T> type) {
        return mandatoryBodyExpression(type, false);
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type
     *
     * @param type            the type
     * @param nullBodyAllowed whether null bodies is allowed and if so a null is returned, otherwise an exception is
     *                        thrown
     */
    public static <T> Expression mandatoryBodyExpression(final Class<T> type, final boolean nullBodyAllowed) {
        return new ExpressionAdapter() {
            @Override
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
            @Override
            public Object evaluate(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                return body != null ? body.getClass() : null;
            }

            @Override
            public String toString() {
                return "bodyType";
            }
        };
    }

    /**
     * Returns the expression for the exchange
     */
    public static Expression exchangeExpression() {
        return new ExpressionAdapter() {
            @Override
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
            @Override
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
     * Returns the expression for the message converted to the given type
     */
    public static Expression messageExpression(final String name) {
        return messageExpression(simpleExpression(name));
    }

    /**
     * Returns the expression for the message converted to the given type
     */
    public static Expression messageExpression(final Expression name) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;

            @Override
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    String text = name.evaluate(exchange, String.class);
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                return exchange.getMessage(type);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                name.init(context);
                classResolver = context.getClassResolver();
            }

            @Override
            public String toString() {
                return "messageAs[" + name + "]";
            }
        };
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
            @Override
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
            @Override
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
     * Returns an expression which converts the given expression to the given type
     */
    public static Expression convertToExpression(final Expression expression, final Class<?> type) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                if (type != null) {
                    return expression.evaluate(exchange, type);
                } else {
                    return expression;
                }
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
            }

            @Override
            public String toString() {
                return String.valueOf(expression);
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type the type expression is evaluated to
     */
    public static Expression convertToExpression(final Expression expression, final Expression type) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Object result = type.evaluate(exchange, Object.class);
                if (result != null) {
                    return expression.evaluate(exchange, result.getClass());
                } else {
                    return expression;
                }
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                type.init(context);
            }

            @Override
            public String toString() {
                return String.valueOf(expression);
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the given token
     */
    public static Expression tokenizeExpression(
            final Expression expression,
            final String token) {
        return tokenizeExpression(expression, simpleExpression(token));
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the given token
     */
    public static Expression tokenizeExpression(
            final Expression expression,
            final Expression token) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String text = token.evaluate(exchange, String.class);
                Object value = expression.evaluate(exchange, Object.class);
                return ExchangeHelper.getScanner(exchange, value, text);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                token.init(context);
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
            private TypeConverter typeConverter;

            @Override
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                Iterator<?> it = typeConverter.tryConvertTo(Iterator.class, exchange, value);
                if (it != null) {
                    // skip first
                    it.next();
                    return it;
                } else {
                    return value;
                }
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                typeConverter = context.getTypeConverter();
            }

            @Override
            public String toString() {
                return "skipFirst(" + expression + ")";
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the given regex
     */
    public static Expression regexTokenizeExpression(
            final Expression expression,
            final String regexTokenizer) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                return ExchangeHelper.getScanner(exchange, value, regexTokenizer);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
            }

            @Override
            public String toString() {
                return "regexTokenize(" + expression + ", " + regexTokenizer + ")";
            }
        };
    }

    public static Expression groupXmlIteratorExpression(final Expression expression, final String group) {
        return new ExpressionAdapter() {
            private Expression groupExp;

            @Override
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it,
                        "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                // must use GroupTokenIterator in xml mode as we want to concat the xml parts into a single message
                // the group can be a simple expression so evaluate it as a number
                Integer parts = groupExp.evaluate(exchange, Integer.class);
                if (parts == null) {
                    throw new RuntimeExchangeException(
                            "Group evaluated as null, must be evaluated as a positive Integer value from expression: " + group,
                            exchange);
                } else if (parts <= 0) {
                    throw new RuntimeExchangeException("Group must be a positive number, was: " + parts, exchange);
                }
                return new GroupTokenIterator(exchange, it, null, parts, false);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                groupExp = context.resolveLanguage("simple").createExpression(group);
                groupExp.init(context);
            }

            @Override
            public String toString() {
                return "group " + expression + " " + group + " times";
            }
        };
    }

    public static Expression groupIteratorExpression(
            final Expression expression, final String token, final String group, final boolean skipFirst) {
        return new ExpressionAdapter() {
            private Expression groupExp;

            @Override
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it,
                        "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                // the group can be a simple expression so evaluate it as a number
                Integer parts = groupExp.evaluate(exchange, Integer.class);
                if (parts == null) {
                    throw new RuntimeExchangeException(
                            "Group evaluated as null, must be evaluated as a positive Integer value from expression: " + group,
                            exchange);
                } else if (parts <= 0) {
                    throw new RuntimeExchangeException("Group must be a positive number, was: " + parts, exchange);
                }
                if (token != null) {
                    return new GroupTokenIterator(exchange, it, token, parts, skipFirst);
                } else {
                    return new GroupIterator(it, parts);
                }
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                groupExp = context.resolveLanguage("simple").createExpression(group);
                groupExp.init(context);
            }

            @Override
            public String toString() {
                return "group " + expression + " " + group + " times";
            }
        };
    }

    public static Expression joinExpression(final Expression expression, final String separator, final String prefix) {
        return new ExpressionAdapter() {
            private TypeConverter converter;

            @Override
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it,
                        "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");

                StringBuilder sb = new StringBuilder();
                while (it.hasNext()) {
                    Object o = it.next();
                    if (o != null) {
                        String s = converter.tryConvertTo(String.class, exchange, o);
                        if (s != null) {
                            if (!sb.isEmpty()) {
                                sb.append(separator);
                            }
                            if (prefix != null) {
                                sb.append(prefix);
                            }
                            sb.append(s);
                        }
                    }
                }

                return sb.toString();
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                converter = context.getTypeConverter();
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
     * Returns a sort expression which will sort the expression with the given comparator.
     * <p/>
     * The expression is evaluated as a {@link List} object to allow sorting.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Expression sortExpression(final Expression expression, final Comparator comparator) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                List<?> list = expression.evaluate(exchange, List.class);
                list.sort(comparator);
                return list;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
            }

            @Override
            public String toString() {
                return "sort(" + expression + " by: " + comparator + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex replaceAll to transform the String and return the
     * result
     */
    public static Expression regexReplaceAll(
            final Expression expression,
            final String regex, final String replacement) {
        final Pattern pattern = Pattern.compile(regex);
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String text = expression.evaluate(exchange, String.class);
                if (text == null) {
                    return null;
                }
                return pattern.matcher(text).replaceAll(replacement);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
            }

            @Override
            public String toString() {
                return "regexReplaceAll(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex replaceAll to transform the String and return the
     * result
     */
    public static Expression regexReplaceAll(
            final Expression expression,
            final String regex, final Expression replacementExpression) {

        final Pattern pattern = Pattern.compile(regex);
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String text = expression.evaluate(exchange, String.class);
                String replacement = replacementExpression.evaluate(exchange, String.class);
                if (text == null || replacement == null) {
                    return null;
                }
                return pattern.matcher(text).replaceAll(replacement);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                expression.init(context);
                replacementExpression.init(context);
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
            @Override
            public Object evaluate(Exchange exchange) {
                return left.evaluate(exchange, String.class) + right.evaluate(exchange, String.class);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                left.init(context);
                right.init(context);
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
            @Override
            public Object evaluate(Exchange exchange) {
                return right.evaluate(exchange, String.class) + left.evaluate(exchange, String.class);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                left.init(context);
                right.init(context);
            }

            @Override
            public String toString() {
                return "prepend(" + left + ", " + right + ")";
            }
        };
    }

    /**
     * Returns an expression which returns the string concatenation value of the various expressions
     *
     * @param  expressions the expression to be concatenated dynamically
     * @return             an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions) {
        return concatExpression(expressions, null);
    }

    /**
     * Returns an expression which returns the string concatenation value of the various expressions
     *
     * @param  expressions the expression to be concatenated dynamically
     * @param  description the text description of the expression
     * @return             an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions, final String description) {
        for (Expression expression : expressions) {
            if (expression instanceof ConstantExpressionAdapter) {
                return concatExpressionOptimized(expressions, description);
            }
        }
        return concatExpressionUnoptimized(expressions, description);
    }

    /**
     * Returns an expression which returns the string concatenation value of the various expressions
     *
     * @param  expressions the expression to be concatenated dynamically
     * @param  description the text description of the expression
     * @return             an expression which when evaluated will return the concatenated values
     */
    private static Expression concatExpressionUnoptimized(final Collection<Expression> expressions, final String description) {
        return new ExpressionAdapter() {

            @Override
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
            public void init(CamelContext context) {
                super.init(context);
                for (Expression expression : expressions) {
                    expression.init(context);
                }
            }

            @Override
            public String toString() {
                if (description != null) {
                    return description;
                } else {
                    return "concat(" + expressions + ")";
                }
            }
        };
    }

    /**
     * Returns an optimized expression which returns the string concatenation value of the various. expressions
     *
     * @param  expressions the expression to be concatenated dynamically
     * @param  description the text description of the expression
     * @return             an expression which when evaluated will return the concatenated values
     */
    private static Expression concatExpressionOptimized(final Collection<Expression> expressions, final String description) {
        return new ExpressionAdapter() {
            private Collection<Object> optimized;
            private String optimizedValue;

            @Override
            public Object evaluate(Exchange exchange) {
                if (optimizedValue != null) {
                    return optimizedValue;
                }
                StringBuilder buffer = new StringBuilder();
                Collection<?> col = optimized != null ? optimized : expressions;
                for (Object obj : col) {
                    if (obj instanceof Expression expression) {
                        String text = expression.evaluate(exchange, String.class);
                        if (text != null) {
                            buffer.append(text);
                        }
                    } else if (obj != null) {
                        buffer.append(obj);
                    }
                }
                return buffer.toString();
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                if (optimized == null) {
                    Collection<Object> preprocessedExpression = new ArrayList<>(expressions.size());
                    boolean constantsOnly = true;
                    for (Expression expression : expressions) {
                        expression.init(context);
                        if (expression instanceof ConstantExpressionAdapter) {
                            Object value = ((ConstantExpressionAdapter) expression).getValue();
                            preprocessedExpression.add(value.toString());
                        } else {
                            preprocessedExpression.add(expression);
                            constantsOnly = false;
                        }
                    }
                    if (constantsOnly) {
                        StringBuilder sb = new StringBuilder();
                        for (Object o : preprocessedExpression) {
                            sb.append(o);
                        }
                        optimizedValue = sb.toString();
                    } else {
                        optimized = preprocessedExpression;
                    }
                } else {
                    for (Expression expression : expressions) {
                        expression.init(context);
                    }
                }
            }

            @Override
            public String toString() {
                if (description != null) {
                    return description;
                } else {
                    return "concat(" + expressions + ")";
                }
            }
        };
    }

    /**
     * Returns an Expression for the inbound message id
     */
    public static Expression messageIdExpression() {
        return new ExpressionAdapter() {
            @Override
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
     * Returns an Expression for the inbound message timestamp
     */
    public static Expression messageTimestampExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getMessageTimestamp();
            }

            @Override
            public String toString() {
                return "messageTimestamp";
            }
        };
    }

    /**
     * Returns an Expression for the exchange id
     */
    public static Expression exchangeIdExpression() {
        return new ExpressionAdapter() {
            @Override
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
            @Override
            public Object evaluate(Exchange exchange) {
                return ExchangeHelper.getRouteId(exchange);
            }

            @Override
            public String toString() {
                return "routeId";
            }
        };
    }

    /**
     * Returns an Expression for the route group
     */
    public static Expression routeGroupExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return ExchangeHelper.getRouteGroup(exchange);
            }

            @Override
            public String toString() {
                return "routeGroup";
            }
        };
    }

    public static Expression simpleExpression(final String expression) {
        if (LanguageSupport.hasSimpleFunction(expression)) {
            return new ExpressionAdapter() {
                private Expression exp;

                @Override
                public Object evaluate(Exchange exchange) {
                    return exp.evaluate(exchange, Object.class);
                }

                @Override
                public void init(CamelContext context) {
                    super.init(context);
                    final Language language = context.resolveLanguage("simple");
                    this.exp = language.createExpression(expression);
                    this.exp.init(context);
                }

                @Override
                public String toString() {
                    return "simple(" + expression + ")";
                }
            };
        } else {
            return constantExpression(expression);
        }
    }

    public static Expression beanExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public Object evaluate(Exchange exchange) {
                // bean is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate expression as a string
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                final Language language = context.resolveLanguage("bean");
                this.exp = language.createExpression(expression);
                this.exp.init(context);
            }

            @Override
            public String toString() {
                return "bean(" + expression + ")";
            }
        };
    }

    public static Expression beanExpression(final Object bean, final String method) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                final Language language = context.resolveLanguage("bean");
                this.exp = language.createExpression(null, new Object[] { null, bean, method });
                this.exp.init(context);
            }

            public String toString() {
                return "bean(" + bean + ", " + method + ")";
            }
        };
    }

    public static Expression beanExpression(final Expression expression, final String method) {
        return new ExpressionAdapter() {
            private Language language;

            @Override
            public Object evaluate(Exchange exchange) {
                Object bean = expression.evaluate(exchange, Object.class);
                Expression exp = language.createExpression(null, new Object[] { null, bean, method });
                exp.init(exchange.getContext());
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                this.language = context.resolveLanguage("bean");
            }

            public String toString() {
                return "bean(" + expression + ", " + method + ")";
            }
        };
    }

    public static Expression propertiesComponentExpression(final String key, final String defaultValue) {
        return new ExpressionAdapter() {
            private Expression exp;
            private PropertiesComponent pc;

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                try {
                    // enclose key with {{ }} to force parsing as key can be a nested expression too
                    return pc.parseUri(PropertiesComponent.PREFIX_TOKEN + text + PropertiesComponent.SUFFIX_TOKEN);
                } catch (Exception e) {
                    // property with key not found, use default value if provided
                    if (defaultValue != null) {
                        return defaultValue;
                    }
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                exp = simpleExpression(key);
                exp.init(context);
                pc = context.getPropertiesComponent();
            }

            @Override
            public String toString() {
                return "properties(" + key + ")";
            }
        };
    }

    public static Expression propertiesComponentExist(final String key, final boolean negate) {
        return new ExpressionAdapter() {
            private PropertiesComponent pc;

            @Override
            public Object evaluate(Exchange exchange) {
                Optional<String> result = pc.resolveProperty(key);
                boolean answer = result.isPresent();
                if (negate) {
                    answer = !answer;
                }
                return answer;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                pc = context.getPropertiesComponent();
            }

            @Override
            public String toString() {
                return "propertiesExist(" + key + ")";
            }
        };
    }

    /**
     * Returns an {@link TokenPairExpressionIterator} expression
     */
    public static Expression tokenizePairExpression(String startToken, String endToken, boolean includeTokens) {
        return new TokenPairExpressionIterator(startToken, endToken, includeTokens);
    }

    /**
     * Returns an {@link TokenPairExpressionIterator} expression
     */
    public static Expression tokenizePairExpression(
            Expression source, String startToken, String endToken, boolean includeTokens) {
        return new TokenPairExpressionIterator(startToken, endToken, includeTokens);
    }

    /**
     * Returns an {@link TokenXMLExpressionIterator} expression
     */
    public static Expression tokenizeXMLExpression(String tagName, String inheritNamespaceTagName) {
        return new TokenXMLExpressionIterator(tagName, inheritNamespaceTagName);
    }

    public static Expression tokenizeXMLExpression(Expression source, String tagName, String inheritNamespaceTagName) {
        return new TokenXMLExpressionIterator(source, tagName, inheritNamespaceTagName);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode) {
        return tokenizeXMLAwareExpression(path, mode, 1, null);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode, int group) {
        return tokenizeXMLAwareExpression(path, mode, group, null);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode, int group, Namespaces namespaces) {
        StringHelper.notEmpty(path, "path");
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                final Language language = context.resolveLanguage("xtokenize");
                this.exp = language.createExpression(path,
                        new Object[] { null, null, mode, group, namespaces });
                this.exp.init(context);
            }

            @Override
            public String toString() {
                return "xtokenize(" + path + ")";
            }
        };
    }

    /**
     * Returns the expression for the message body as a one-line string
     */
    public static Expression bodyOneLine() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String body = exchange.getIn().getBody(String.class);
                if (body == null) {
                    return null;
                }
                body = body.replace(System.lineSeparator(), "");
                return body;
            }

            @Override
            public String toString() {
                return "bodyOneLine()";
            }
        };
    }

    /**
     * Returns the expression as pretty formatted string
     */
    public static Expression prettyExpression(final Expression expression) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String body = expression.evaluate(exchange, String.class);
                if (body == null) {
                    return null;
                } else if (body.startsWith("{") && body.endsWith("}") || body.startsWith("[") && body.endsWith("]")) {
                    return Jsoner.prettyPrint(body); //json
                } else if (body.startsWith("<") && body.endsWith(">")) {
                    return ExpressionBuilder.prettyXml(body); //xml
                }

                return body;
            }

            @Override
            public String toString() {
                return "pretty(" + expression + ")";
            }
        };
    }

    /**
     * Returns the expression for the message body as pretty formatted string
     */
    public static Expression prettyBodyExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                String body = exchange.getIn().getBody(String.class);

                if (body == null) {
                    return null;
                } else if (body.startsWith("{") && body.endsWith("}") || body.startsWith("[") && body.endsWith("]")) {
                    return Jsoner.prettyPrint(body); //json
                } else if (body.startsWith("<") && body.endsWith(">")) {
                    return ExpressionBuilder.prettyXml(body); //xml
                }

                return body;
            }

            @Override
            public String toString() {
                return "prettyBody()";
            }
        };
    }

    private static String prettyXml(String rawXml) {
        try {
            boolean includeDeclaration = rawXml.startsWith("<?xml");
            return XmlPrettyPrinter.pettyPrint(rawXml, 2, includeDeclaration);
        } catch (Exception e) {
            return rawXml;
        }
    }

}
