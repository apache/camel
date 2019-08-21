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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for implementation inheritance for different clauses in the
 * <a href="http://camel.apache.org/dsl.html">Java DSL</a>
 */
public abstract class BuilderSupport {
    private CamelContext context;
    private ErrorHandlerBuilder errorHandlerBuilder;

    protected BuilderSupport() {
    }

    protected BuilderSupport(CamelContext context) {
        this.context = context;
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
     */
    public <T> ValueBuilder bodyAs(Class<T> type) {
        return Builder.bodyAs(type);
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
     * Returns a JSonPath expression value builder
     */
    public ValueBuilder jsonpath(String value) {
        JsonPathExpression exp = new JsonPathExpression(value);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a JSonPath expression value builder
     *
     * @param value The JSonPath expression
     * @param resultType The result type that the JSonPath expression will
     *            return.
     */
    public ValueBuilder jsonpath(String value, Class<?> resultType) {
        JsonPathExpression exp = new JsonPathExpression(value);
        exp.setResultType(resultType);
        return new ValueBuilder(exp);
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
    public SimpleBuilder simpleF(String format, Object... values) {
        return SimpleBuilder.simpleF(format, values);
    }

    /**
     * Returns a simple expression value builder, using String.format style
     */
    public SimpleBuilder simpleF(String format, Class<?> resultType, Object... values) {
        return SimpleBuilder.simpleF(format, resultType, values);
    }

    /**
     * Returns a xpath expression value builder
     *
     * @param value the XPath expression
     * @return the builder
     */
    public ValueBuilder xpath(String value) {
        return xpath(value, null, null);
    }

    /**
     * Returns a xpath expression value builder
     *
     * @param value the XPath expression
     * @param resultType the result type that the XPath expression will return.
     * @return the builder
     */
    public ValueBuilder xpath(String value, Class<?> resultType) {
        return xpath(value, resultType, null);
    }

    /**
     * Returns a xpath expression value builder
     *
     * @param value the XPath expression
     * @param namespaces namespace mappings
     * @return the builder
     */
    public ValueBuilder xpath(String value, Namespaces namespaces) {
        return xpath(value, null, namespaces);
    }

    /**
     * Returns a xpath expression value builder
     *
     * @param value the XPath expression
     * @param resultType the result type that the XPath expression will return.
     * @param namespaces namespace mappings
     * @return the builder
     */
    public ValueBuilder xpath(String value, Class<?> resultType, Namespaces namespaces) {
        // the value may contain property placeholders as it may be used
        // directly from Java DSL
        try {
            value = getContext().resolvePropertyPlaceholders(value);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        XPathExpression exp = new XPathExpression(value);
        exp.setResultType(resultType);
        if (namespaces != null) {
            exp.setNamespaces(namespaces.getNamespaces());
        }
        return new ValueBuilder(exp);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method
     * call expression</a> value builder
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a
     * reference to a bean (String).
     *
     * @param beanOrBeanRef either an instanceof a bean or a reference to bean
     *            to lookup in the Registry
     * @return the builder
     */
    public ValueBuilder method(Object beanOrBeanRef) {
        return method(beanOrBeanRef, null);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method
     * call expression</a> value builder
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a
     * reference to a bean (String).
     *
     * @param beanOrBeanRef either an instanceof a bean or a reference to bean
     *            to lookup in the Registry
     * @param method name of method to invoke
     * @return the builder
     */
    public ValueBuilder method(Object beanOrBeanRef, String method) {
        return Builder.bean(beanOrBeanRef, method);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method
     * call expression</a> value builder
     *
     * @param beanType the Class of the bean which we want to invoke
     * @return the builder
     */
    public ValueBuilder method(Class<?> beanType) {
        return Builder.bean(beanType);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">method
     * call expression</a> value builder
     *
     * @param beanType the Class of the bean which we want to invoke
     * @param method name of method to invoke
     * @return the builder
     */
    public ValueBuilder method(Class<?> beanType, String method) {
        return Builder.bean(beanType, method);
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
     * @param uri the uri to resolve
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
     * @param uri the uri to resolve
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
     * @param uris list of endpoints to resolve
     * @throws NoSuchEndpointException if an endpoint URI could not be resolved
     * @return list of endpoints
     */
    public List<Endpoint> endpoints(String... uris) throws NoSuchEndpointException {
        List<Endpoint> endpoints = new ArrayList<>();
        for (String uri : uris) {
            endpoints.add(endpoint(uri));
        }
        return endpoints;
    }

    /**
     * Helper method to create a list of {@link Endpoint} instances
     *
     * @param endpoints endpoints
     * @return list of the given endpoints
     */
    public List<Endpoint> endpoints(Endpoint... endpoints) {
        List<Endpoint> answer = new ArrayList<>();
        answer.addAll(Arrays.asList(endpoints));
        return answer;
    }

    /**
     * Creates a default
     * <a href="http://camel.apache.org/error-handler.html">error handler</a>.
     *
     * @return the builder
     */
    public DefaultErrorHandlerBuilder defaultErrorHandler() {
        return new DefaultErrorHandlerBuilder();
    }

    /**
     * Creates a disabled
     * <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * for removing the default error handler
     *
     * @return the builder
     */
    public NoErrorHandlerBuilder noErrorHandler() {
        return new NoErrorHandlerBuilder();
    }

    /**
     * <a href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
     * Channel EIP:</a> is a error handler for handling messages that could not
     * be delivered to it's intended destination.
     *
     * @param deadLetterUri uri to the dead letter endpoint storing dead
     *            messages
     * @return the builder
     */
    public DeadLetterChannelBuilder deadLetterChannel(String deadLetterUri) {
        return deadLetterChannel(endpoint(deadLetterUri));
    }

    /**
     * <a href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
     * Channel EIP:</a> is a error handler for handling messages that could not
     * be delivered to it's intended destination.
     *
     * @param deadLetterEndpoint dead letter endpoint storing dead messages
     * @return the builder
     */
    public DeadLetterChannelBuilder deadLetterChannel(Endpoint deadLetterEndpoint) {
        return new DeadLetterChannelBuilder(deadLetterEndpoint);
    }

    // Properties
    // -------------------------------------------------------------------------

    public CamelContext getContext() {
        return context;
    }

    public void setContext(CamelContext context) {
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
