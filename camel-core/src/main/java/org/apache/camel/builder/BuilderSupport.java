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
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.model.LoggingLevel;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for implementation inheritance for different clauses in the <a
 * href="http://activemq.apache.org/camel/dsl.html">Java DSL</a>
 *
 * @version $Revision$
 */
public abstract class BuilderSupport {
    private CamelContext context;
    private ErrorHandlerBuilder errorHandlerBuilder;
    private boolean inheritErrorHandler = true;

    protected BuilderSupport(CamelContext context) {
        this.context = context;
    }

    protected BuilderSupport(BuilderSupport parent) {
        this.context = parent.getContext();
        this.inheritErrorHandler = parent.inheritErrorHandler;
        if (inheritErrorHandler && parent.errorHandlerBuilder != null) {
            this.errorHandlerBuilder = parent.errorHandlerBuilder.copy();
        }
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header
     */
    public ValueBuilder header(String name) {
        return Builder.header(name);
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
    public <T> ValueBuilder body(Class<T> type) {
        return Builder.bodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an
     * exchange
     */
    public ValueBuilder outBody() {
        return Builder.outBody();
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a
     * specific type
     */
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
     */
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
     * Resolves the given URI to an endpoint
     *
     * @throws NoSuchEndpointException if the endpoint URI could not be resolved
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
     * @throws NoSuchEndpointException if the endpoint URI could not be resolved
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
     * @throws NoSuchEndpointException if an endpoint URI could not be resolved
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
     */
    public List<Endpoint> endpoints(Endpoint... endpoints) {
        List<Endpoint> answer = new ArrayList<Endpoint>();
        answer.addAll(Arrays.asList(endpoints));
        return answer;
    }

    /**
     * Creates a disabled error handler for removing the default error handler
     */
    public NoErrorHandlerBuilder noErrorHandler() {
        return new NoErrorHandlerBuilder();
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder loggingErrorHandler() {
        return new LoggingErrorHandlerBuilder();
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder loggingErrorHandler(String log) {
        return loggingErrorHandler(LogFactory.getLog(log));
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder loggingErrorHandler(Log log) {
        return new LoggingErrorHandlerBuilder(log);
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder loggingErrorHandler(Log log, LoggingLevel level) {
        return new LoggingErrorHandlerBuilder(log, level);
    }

    public DeadLetterChannelBuilder deadLetterChannel() {
        return new DeadLetterChannelBuilder();
    }

    public DeadLetterChannelBuilder deadLetterChannel(String deadLetterUri) {
        return deadLetterChannel(endpoint(deadLetterUri));
    }

    public DeadLetterChannelBuilder deadLetterChannel(Endpoint deadLetterEndpoint) {
        return new DeadLetterChannelBuilder(new SendProcessor(deadLetterEndpoint));
    }

    // Properties
    // -------------------------------------------------------------------------
    public CamelContext getContext() {
        return context;
    }

    public void setContext(CamelContext context) {
        this.context = context;
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    protected ErrorHandlerBuilder createErrorHandlerBuilder() {
        if (isInheritErrorHandler()) {
            return new DeadLetterChannelBuilder();
        } else {
            return new NoErrorHandlerBuilder();
        }
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    public boolean isInheritErrorHandler() {
        return inheritErrorHandler;
    }

    public void setInheritErrorHandler(boolean inheritErrorHandler) {
        this.inheritErrorHandler = inheritErrorHandler;
    }
}
