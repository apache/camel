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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for implementation inheritance for different clauses in the <a
 * href="http://camel.apache.org/dsl.html">Java DSL</a>
 *
 * @version
 */
public abstract class BuilderSupport {
    private ModelCamelContext context;
    private ErrorHandlerBuilder errorHandlerBuilder;

    protected BuilderSupport() {
    }

    protected BuilderSupport(CamelContext context) {
        this.context = context != null ? context.adapt(ModelCamelContext.class) : null;
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header
     */
    public ValueBuilder header(String name) {
        Expression exp = new HeaderExpression(name);
        return new ValueBuilder(exp);
    }

    /**
     *
     * Returns a value builder for the given exchange property
     * @deprecated use {@link #exchangeProperty(String)} instead
     */
    @Deprecated
    public ValueBuilder property(String name) {
        Expression exp = new ExchangePropertyExpression(name);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a value builder for the given exchange property
     */
    public ValueBuilder exchangeProperty(String name) {
        Expression exp = new ExchangePropertyExpression(name);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public ValueBuilder body() {
        return Builder.body();
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a
     * specific type
     *
     * @deprecated use {@link #bodyAs(Class)}
     */
    @Deprecated
    public <T> ValueBuilder body(Class<T> type) {
        return bodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a
     * specific type
     */
    public <T> ValueBuilder bodyAs(Class<T> type) {
        return Builder.bodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an
     * exchange
     *
     * @deprecated use {@link #body()}
     */
    @Deprecated
    public ValueBuilder outBody() {
        return Builder.outBody();
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a
     * specific type
     *
     * @deprecated use {@link #bodyAs(Class)}
     */
    @Deprecated
    public <T> ValueBuilder outBody(Class<T> type) {
        return Builder.outBodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the fault body on an
     * exchange
     */
    public ValueBuilder faultBody() {
        return Builder.faultBody();
    }

    /**
     * Returns a predicate and value builder for the fault message body as a
     * specific type
     *
     * @deprecated use {@link #bodyAs(Class)}
     */
    @Deprecated
    public <T> ValueBuilder faultBodyAs(Class<T> type) {
        return Builder.faultBodyAs(type);
    }

    /**
     * Returns a value builder for the given system property
     */
    public ValueBuilder systemProperty(String name) {
        return Builder.systemProperty(name);
    }

    /**
     * Returns a value builder for the given system property
     */
    public ValueBuilder systemProperty(String name, String defaultValue) {
        return Builder.systemProperty(name, defaultValue);
    }

    /**
     * Returns a constant expression value builder
     */
    public ValueBuilder constant(Object value) {
        return Builder.constant(value);
    }

    /**
     * Returns a language expression value builder
     */
    public ValueBuilder language(String language, String expression) {
        return Builder.language(language, expression);
    }

    /**
     * Returns a simple expression value builder
     */
    public SimpleBuilder simple(String value) {
        return SimpleBuilder.simple(value);
    }

    /**
     * Returns a simple expression value builder
     */
    public SimpleBuilder simple(String value, Class<?> resultType) {
        return SimpleBuilder.simple(value, resultType);
    }

    /**
     * Returns a simple expression value builder, using String.format style
     */
    public SimpleBuilder simpleF(String format, Object...values) {
        return SimpleBuilder.simpleF(format, values);
    }

    /**
     * Returns a simple expression value builder, using String.format style
     */
    public SimpleBuilder simpleF(String format, Class<?> resultType, Object...values) {
        return SimpleBuilder.simpleF(format, resultType, values);
    }

    /**
     * Returns a xpath expression value builder
     * @param value The XPath expression
     * @return A new XPathBuilder object
     */
    public XPathBuilder xpath(String value) {
        return XPathBuilder.xpath(value);
    }

    /**
     * Returns a xpath expression value builder
     * @param value The XPath expression
     * @param resultType The result type that the XPath expression will return.
     * @return A new XPathBuilder object
     */
    public static XPathBuilder xpath(String value, Class<?> resultType) {
        return XPathBuilder.xpath(value, resultType);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a reference to a bean (String).
     *
     * @param beanOrBeanRef  either an instanceof a bean or a reference to bean to lookup in the Registry
     * @return the builder
     * @deprecated use {@link #method(Object)} instead
     */
    @Deprecated
    public ValueBuilder bean(Object beanOrBeanRef) {
        return bean(beanOrBeanRef, null);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a reference to a bean (String).
     *
     * @param beanOrBeanRef  either an instanceof a bean or a reference to bean to lookup in the Registry
     * @param method   name of method to invoke
     * @return the builder
     * @deprecated use {@link #method(Object, String)} instead
     */
    @Deprecated
    public ValueBuilder bean(Object beanOrBeanRef, String method) {
        return Builder.bean(beanOrBeanRef, method);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     *
     * @param beanType the Class of the bean which we want to invoke
     * @return the builder
     * @deprecated use {@link #method(Class)} instead
     */
    @Deprecated
    public ValueBuilder bean(Class<?> beanType) {
        return Builder.bean(beanType);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     *
     * @param beanType the Class of the bean which we want to invoke
     * @param method   name of method to invoke
     * @return the builder
     * @deprecated use {@link #method(Class, String)} instead
     */
    @Deprecated
    public ValueBuilder bean(Class<?> beanType, String method) {
        return Builder.bean(beanType, method);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a reference to a bean (String).
     *
     * @param beanOrBeanRef  either an instanceof a bean or a reference to bean to lookup in the Registry
     * @return the builder
     */
    public ValueBuilder method(Object beanOrBeanRef) {
        return method(beanOrBeanRef, null);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a reference to a bean (String).
     *
     * @param beanOrBeanRef  either an instanceof a bean or a reference to bean to lookup in the Registry
     * @param method   name of method to invoke
     * @return the builder
     */
    public ValueBuilder method(Object beanOrBeanRef, String method) {
        return Builder.bean(beanOrBeanRef, method);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     *
     * @param beanType the Class of the bean which we want to invoke
     * @return the builder
     */
    public ValueBuilder method(Class<?> beanType) {
        return Builder.bean(beanType);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method call expression</a>
     * value builder
     *
     * @param beanType the Class of the bean which we want to invoke
     * @param method   name of method to invoke
     * @return the builder
     */
    public ValueBuilder method(Class<?> beanType, String method) {
        return Builder.bean(beanType, method);
    }

    /**
     * Returns an expression processing the exchange to the given endpoint uri
     *
     * @param uri endpoint uri to send the exchange to
     * @return the builder
     * @deprecated not in use, and not available in XML DSL
     */
    @Deprecated
    public ValueBuilder sendTo(String uri) {
        return Builder.sendTo(uri);
    }

    /**
     * Returns an expression value builder that replaces all occurrences of the
     * regular expression with the given replacement
     */
    public ValueBuilder regexReplaceAll(Expression content, String regex, String replacement) {
        return Builder.regexReplaceAll(content, regex, replacement);
    }

    /**
     * Returns an expression value builder that replaces all occurrences of the
     * regular expression with the given replacement
     */
    public ValueBuilder regexReplaceAll(Expression content, String regex, Expression replacement) {
        return Builder.regexReplaceAll(content, regex, replacement);
    }

    /**
     * Returns a exception expression value builder
     */
    public ValueBuilder exceptionMessage() {
        return Builder.exceptionMessage();
    }

    /**
     * Resolves the given URI to an endpoint
     *
     * @param uri  the uri to resolve
     * @throws NoSuchEndpointException if the endpoint URI could not be resolved
     * @return the endpoint
     */
    public Endpoint endpoint(String uri) throws NoSuchEndpointException {
        ObjectHelper.notNull(uri, "uri");
        Endpoint endpoint = getContext().getEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri);
        }
        return endpoint;
    }

    /**
     * Resolves the given URI to an endpoint of the specified type
     *
     * @param uri  the uri to resolve
     * @param type the excepted type of the endpoint
     * @throws NoSuchEndpointException if the endpoint URI could not be resolved
     * @return the endpoint
     */
    public <T extends Endpoint> T endpoint(String uri, Class<T> type) throws NoSuchEndpointException {
        ObjectHelper.notNull(uri, "uri");
        T endpoint = getContext().getEndpoint(uri, type);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri);
        }
        return endpoint;
    }

    /**
     * Resolves the list of URIs into a list of {@link Endpoint} instances
     *
     * @param uris  list of endpoints to resolve
     * @throws NoSuchEndpointException if an endpoint URI could not be resolved
     * @return list of endpoints
     */
    public List<Endpoint> endpoints(String... uris) throws NoSuchEndpointException {
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (String uri : uris) {
            endpoints.add(endpoint(uri));
        }
        return endpoints;
    }

    /**
     * Helper method to create a list of {@link Endpoint} instances
     *
     * @param endpoints  endpoints
     * @return list of the given endpoints
     */
    public List<Endpoint> endpoints(Endpoint... endpoints) {
        List<Endpoint> answer = new ArrayList<Endpoint>();
        answer.addAll(Arrays.asList(endpoints));
        return answer;
    }

    /**
     * Creates a default <a href="http://camel.apache.org/error-handler.html">error handler</a>.
     *
     * @return the builder
     */
    public DefaultErrorHandlerBuilder defaultErrorHandler() {
        return new DefaultErrorHandlerBuilder();
    }

    /**
     * Creates a disabled <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * for removing the default error handler
     *
     * @return the builder
     */
    public NoErrorHandlerBuilder noErrorHandler() {
        return new NoErrorHandlerBuilder();
    }

    /**
     * Creates an <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * which just logs errors
     *
     * @return the builder
     * @deprecated use dead letter channel with a log endpoint
     */
    @Deprecated
    public LoggingErrorHandlerBuilder loggingErrorHandler() {
        return new LoggingErrorHandlerBuilder();
    }

    /**
     * Creates an <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * which just logs errors
     *
     * @return the builder
     * @deprecated use dead letter channel with a log endpoint
     */
    @Deprecated
    public LoggingErrorHandlerBuilder loggingErrorHandler(String log) {
        return loggingErrorHandler(LoggerFactory.getLogger(log));
    }

    /**
     * Creates an <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * which just logs errors
     *
     * @return the builder
     * @deprecated use dead letter channel with a log endpoint
     */
    @Deprecated
    public LoggingErrorHandlerBuilder loggingErrorHandler(Logger log) {
        return new LoggingErrorHandlerBuilder(log);
    }

    /**
     * Creates an <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * which just logs errors
     *
     * @return the builder
     * @deprecated use dead letter channel with a log endpoint
     */
    @Deprecated
    public LoggingErrorHandlerBuilder loggingErrorHandler(Logger log, LoggingLevel level) {
        return new LoggingErrorHandlerBuilder(log, level);
    }

    /**
     * <a href="http://camel.apache.org/dead-letter-channel.html">Dead Letter Channel EIP:</a>
     * is a error handler for handling messages that could not be delivered to it's intended destination.
     *
     * @param deadLetterUri  uri to the dead letter endpoint storing dead messages
     * @return the builder
     */
    public DeadLetterChannelBuilder deadLetterChannel(String deadLetterUri) {
        return deadLetterChannel(endpoint(deadLetterUri));
    }

    /**
     * <a href="http://camel.apache.org/dead-letter-channel.html">Dead Letter Channel EIP:</a>
     * is a error handler for handling messages that could not be delivered to it's intended destination.
     *
     * @param deadLetterEndpoint  dead letter endpoint storing dead messages
     * @return the builder
     */
    public DeadLetterChannelBuilder deadLetterChannel(Endpoint deadLetterEndpoint) {
        return new DeadLetterChannelBuilder(deadLetterEndpoint);
    }

    // Properties
    // -------------------------------------------------------------------------

    public ModelCamelContext getContext() {
        return context;
    }

    public void setContext(CamelContext context) {
        ObjectHelper.notNull(context, "CamelContext", this);
        this.context = context.adapt(ModelCamelContext.class);
    }

    public void setContext(ModelCamelContext context) {
        ObjectHelper.notNull(context, "CamelContext", this);
        this.context = context;
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    protected ErrorHandlerBuilder createErrorHandlerBuilder() {
        return new DefaultErrorHandlerBuilder();
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

}
