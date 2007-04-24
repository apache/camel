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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.processor.LoggingLevel;
import org.apache.camel.processor.SendProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for implementation inheritance for different clauses in the 
 * <a href="http://activemq.apache.org/camel/dsl.html">Java DSL</a>
 *
 * @version $Revision: $
 */
public abstract class BuilderSupport<E extends Exchange> {
    private CamelContext context;
    private ErrorHandlerBuilder<E> errorHandlerBuilder;
    private boolean inheritErrorHandler = true;

    protected BuilderSupport(CamelContext context) {
        this.context = context;
    }

    protected BuilderSupport(BuilderSupport<E> parent) {
        this.context = parent.getContext();
        this.inheritErrorHandler = parent.inheritErrorHandler;
        if (inheritErrorHandler && parent.errorHandlerBuilder != null) {
            this.errorHandlerBuilder = parent.errorHandlerBuilder.copy();
        }
    }

    // Builder methods
    //-------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header
     */
    @Fluent
    public ValueBuilder<E> header(@FluentArg("name")String name) {
        return Builder.<E>header(name);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    @Fluent
    public ValueBuilder<E> body() {
        return Builder.<E>body();
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a specific type
     */
    @Fluent
    public <T> ValueBuilder<E> bodyAs(@FluentArg("class")Class<T> type) {
        return Builder.<E, T>bodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an exchange
     */
    @Fluent
    public ValueBuilder<E> outBody() {
        return Builder.<E>outBody();
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a specific type
     */
    @Fluent
    public <T> ValueBuilder<E> outBody(@FluentArg("class")Class<T> type) {
        return Builder.<E, T>outBody(type);
    }

    /**
     * Returns a value builder for the given system property
     */
    @Fluent
    public ValueBuilder<E> systemProperty(@FluentArg("name")String name) {
        return Builder.<E>systemProperty(name);
    }

    /**
     * Returns a value builder for the given system property
     */
    @Fluent
    public ValueBuilder<E> systemProperty(
            @FluentArg("name")String name, @FluentArg("defaultValue")String defaultValue) {
        return Builder.<E>systemProperty(name, defaultValue);
    }

    /**
     * Resolves the given URI to an endpoint
     */
    @Fluent
    public Endpoint<E> endpoint(@FluentArg("uri")String uri) {
        return getContext().resolveEndpoint(uri);
    }

    /**
     * Resolves the list of URIs into a list of {@link Endpoint} instances
     */
    @Fluent
    public List<Endpoint<E>> endpoints(@FluentArg("uris")String... uris) {
        List<Endpoint<E>> endpoints = new ArrayList<Endpoint<E>>();
        for (String uri : uris) {
            endpoints.add(endpoint(uri));
        }
        return endpoints;
    }

    /**
     * Helper method to create a list of {@link Endpoint} instances
     */
    @Fluent
    public List<Endpoint<E>> endpoints(@FluentArg("endpoints")Endpoint<E>... endpoints) {
        List<Endpoint<E>> answer = new ArrayList<Endpoint<E>>();
        for (Endpoint<E> endpoint : endpoints) {
            answer.add(endpoint);
        }
        return answer;
    }

    /**
     * Creates a disabled error handler for removing the default error handler
     */
    @Fluent
    public NoErrorHandlerBuilder<E> noErrorHandler() {
        return new NoErrorHandlerBuilder<E>();
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler() {
        return new LoggingErrorHandlerBuilder<E>();
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler(@FluentArg("log")String log) {
        return loggingErrorHandler(LogFactory.getLog(log));
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler(@FluentArg("log")Log log) {
        return new LoggingErrorHandlerBuilder<E>(log);
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler(
            @FluentArg("log")Log log, @FluentArg("level")LoggingLevel level) {
        return new LoggingErrorHandlerBuilder<E>(log, level);
    }

    @Fluent
    public DeadLetterChannelBuilder<E> deadLetterChannel() {
        return new DeadLetterChannelBuilder<E>();
    }

    @Fluent
    public DeadLetterChannelBuilder<E> deadLetterChannel(@FluentArg("uri")String deadLetterUri) {
        return deadLetterChannel(endpoint(deadLetterUri));
    }

    @Fluent
    public DeadLetterChannelBuilder<E> deadLetterChannel(@FluentArg("endpoint")Endpoint<E> deadLetterEndpoint) {
        return new DeadLetterChannelBuilder<E>(new SendProcessor<E>(deadLetterEndpoint));
    }

    // Properties
    //-------------------------------------------------------------------------
    public CamelContext getContext() {
        return context;
    }

    public void setContext(CamelContext context) {
        this.context = context;
    }

    public ErrorHandlerBuilder<E> getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    protected ErrorHandlerBuilder<E> createErrorHandlerBuilder() {
        if (isInheritErrorHandler()) {
            return new DeadLetterChannelBuilder<E>();
        }
        else {
            return new NoErrorHandlerBuilder<E>();
        }
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder<E> errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    public boolean isInheritErrorHandler() {
        return inheritErrorHandler;
    }

    public void setInheritErrorHandler(boolean inheritErrorHandler) {
        this.inheritErrorHandler = inheritErrorHandler;
    }
}
